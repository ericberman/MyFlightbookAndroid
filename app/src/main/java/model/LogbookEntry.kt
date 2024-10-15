/*
	MyFlightbook for Android - provides native access to MyFlightbook
	pilot's logbook
    Copyright (C) 2017-2024 MyFlightbook, LLC

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package model

import android.content.ContentValues
import android.database.Cursor
import android.util.Log
import com.myflightbook.android.ActRecentsWS
import com.myflightbook.android.MFBMain
import com.myflightbook.android.webservices.AircraftSvc
import com.myflightbook.android.webservices.AuthToken
import com.myflightbook.android.webservices.CustomPropertyTypesSvc.Companion.cachedPropertyTypes
import com.myflightbook.android.webservices.UTCDate.getNullDate
import com.myflightbook.android.webservices.UTCDate.isNullDate
import model.Aircraft.Companion.getAircraftById
import model.Airport.Companion.maxDistanceForRoute
import model.Airport.Companion.splitCodes
import model.FlightProperty.Companion.crossProduct
import model.FlightProperty.Companion.distillList
import model.FlightProperty.Companion.fromDB
import model.FlightProperty.Companion.rewritePropertiesForFlight
import model.LazyThumbnailLoader.ThumbnailedItem
import model.MFBImageInfo.PictureDestination
import model.MFBLocation.AutoFillOptions
import org.kobjects.isodate.IsoDate
import org.ksoap2.serialization.KvmSerializable
import org.ksoap2.serialization.PropertyInfo
import org.ksoap2.serialization.SoapObject
import java.io.Serializable
import java.text.NumberFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern
import kotlin.math.roundToInt

open class LogbookEntry : SoapableObject, KvmSerializable, Serializable, ThumbnailedItem {
    private enum class FlightProp {
        PIDFlightId, PIDUser, PIDFlightDate, PIDCatClassOverride, PIDAircraft, PIDcApproaches, PIDcAppPrecision, PIDcAppNP, PIDcLandings, PIDcFSNight, PIDcFSDay, PIDdXC, PIDdNight, PIDdIMC, PIDdSimulatedIFR, PIDdGrnd, PIDdDual, PIDdPIC, PIDdCFI, PIDdSIC, PIDdTotal, PIDfHold, PIDszCommets, PIDszRoute, PIDfPublic, PIDszErr, PIDdtFStart, PIDdtFEnd, PIDdtEStart, PIDdtEEnd, PIDdHobbsStart, PIDdHobbsEnd, PIDszModelDisplay, PIDszTailDisplay, PIDszCatClassDisplay, PIDfHasData, PIDszData, PIDProperties, PIDExistingImages, PIDSend, PIDShare, PIDPendingID, PIDFlightColor
    }

    enum class SigStatus {
        None, Valid, Invalid
    }

    @JvmField
    var idLocalDB: Long = -1
    @JvmField
    var idFlight = ID_NEW_FLIGHT
    private var szUser = ""
    @JvmField
    var dtFlight = Date()
    private var idCatClassOverride = 0
    @JvmField
    var idAircraft = -1
    @JvmField
    var cApproaches = 0
    private var cApproachPrecision = 0
    private var cApproachNonPrecision = 0
    @JvmField
    var cLandings = 0
    @JvmField
    var cNightLandings = 0
    @JvmField
    var cFullStopLandings = 0
    @JvmField
    var decXC = 0.0
    @JvmField
    var decNight = 0.0
    @JvmField
    var decIMC = 0.0
    @JvmField
    var decSimulatedIFR = 0.0
    @JvmField
    var decGrndSim = 0.0
    @JvmField
    var decDual = 0.0
    @JvmField
    var decPIC = 0.0
    @JvmField
    var decCFI = 0.0
    @JvmField
    var decSIC = 0.0
    @JvmField
    var decTotal = 0.0
    @JvmField
    var fHold = false
    @JvmField
    var szComments = ""
    @JvmField
    var szRoute = ""
    @JvmField
    var fPublic = false
    @JvmField
    var dtFlightStart = getNullDate()
    @JvmField
    var dtFlightEnd = getNullDate()
    @JvmField
    var dtEngineStart = getNullDate()
    @JvmField
    var dtEngineEnd = getNullDate()
    @JvmField
    var hobbsStart = 0.0
    @JvmField
    var hobbsEnd = 0.0
    @JvmField
    var szFlightData = ""
    @JvmField
    var rgCustomProperties: Array<FlightProperty> = arrayOf()
    @JvmField
    var rgFlightImages // Only used on existing flights.
            : Array<MFBImageInfo>? = null
    @JvmField
    var shareLink = ""
    @JvmField
    var sendLink = ""

    // non-persisted values.
    @JvmField
    var szError = ""
    private var szModelDisplay = ""
    @JvmField
    var szTailNumDisplay = ""
    private var szCatClassDisplay = ""
    private var fHasDataStream = false
    @JvmField
    var signatureStatus = SigStatus.None
    @JvmField
    var signatureComments = ""
    @JvmField
    var signatureDate: Date? = null
    @JvmField
    var signatureCFICert = ""
    @JvmField
    var signatureCFIExpiration: Date? = null
    @JvmField
    var signatureCFIName = ""
    @JvmField
    var signatureHasDigitizedSig = false
    @JvmField
    var fForcePending = false // Indicate if any save operation should force to be a pending flight.
    @JvmField
    protected var mPendingID = "" // error to use in LogbookEntry; used for PendingFlight serialization
    @JvmField
    var mFlightColorHex: String? = null

    private fun init() {
        szUser = AuthToken.m_szEmail
        cNightLandings = 0
        cLandings = 0
        cFullStopLandings = 0
        cApproachPrecision = 0
        cApproachNonPrecision = 0
        cApproaches = 0
        hobbsStart = 0.0
        hobbsEnd = 0.0
        decDual = 0.0
        decGrndSim = 0.0
        decXC = 0.0
        decTotal = 0.0
        decSimulatedIFR = 0.0
        decIMC = 0.0
        decSIC = 0.0
        decPIC = 0.0
        decNight = 0.0
        decCFI = 0.0
        dtFlightStart = getNullDate()
        dtFlightEnd = dtFlightStart
        dtEngineStart = dtFlightEnd
        dtEngineEnd = dtEngineStart
        dtFlight = Date()
        fPublic = false
        fHold = false
        fHasDataStream = false
        idAircraft = -1
        idCatClassOverride = 0
        idFlight = ID_NEW_FLIGHT
        szTailNumDisplay = ""
        szRoute = szTailNumDisplay
        szModelDisplay = szRoute
        szFlightData = szModelDisplay
        szError = szFlightData
        szComments = szError
        szCatClassDisplay = szComments
        rgFlightImages = arrayOf()
    }

    // region constructors/initialization
    // Initialize a flight from the specified ID,
    // else save a copy in the database and return that id
    private fun initFromId(id: Long) {
        init()
        if (id < 0) toDB() else {
            idLocalDB = id
            fromDB()
            // if this failed, save it and return the new dbID.
            if (idLocalDB < 0) toDB()
        }
    }

    constructor() : super() {
        init()
    }

    constructor(id: Long) : super() {
        initFromId(id)
    }

    constructor(idAircraft: Int, isPublic: Boolean) : super() {
        init()
        this.idAircraft = idAircraft
        fPublic = isPublic
    }

    constructor(so: SoapObject) : super() {
        init()
        initFromProps(so)
    }

    // endregion
    fun clone(): LogbookEntry? {
        if (idFlight < 0) return null
        val leNew = MFBUtil.clone<LogbookEntry>(this) ?: return null
        leNew.sendLink = ""
        leNew.shareLink = leNew.sendLink
        leNew.idFlight = ID_NEW_FLIGHT
        for (fp in leNew.rgCustomProperties) {
            fp.idFlight = ID_NEW_FLIGHT
            fp.idProp = FlightProperty.ID_PROP_NEW
        }
        leNew.rgFlightImages = arrayOf()
        leNew.dtFlight = Date()
        leNew.dtEngineStart = getNullDate()
        leNew.dtEngineEnd = getNullDate()
        leNew.dtFlightStart = getNullDate()
        leNew.dtFlightEnd = getNullDate()
        leNew.hobbsStart = 0.0
        leNew.hobbsEnd = leNew.hobbsStart
        leNew.szFlightData = ""
        leNew.rgFlightImages = arrayOf()
        for (fp in leNew.rgCustomProperties) {
            fp.idFlight = ID_NEW_FLIGHT
            fp.idProp = FlightProperty.ID_PROP_NEW
        }
        return leNew
    }

    fun cloneAndReverse(): LogbookEntry? {
        val leNew = clone()
        if (leNew == null)
            return leNew
        val airports = splitCodes(leNew.szRoute)
        val sb = StringBuilder()
        for (i in airports.indices.reversed()) {
            sb.append(airports[i]).append(" ")
        }
        leNew.szRoute = sb.toString()
        return leNew
    }

    fun addImageForflight(mfbii: MFBImageInfo) {
        // save it to the DB for uploading...
        mfbii.targetID = idLocalDB
        mfbii.toDB()
        Log.w(
            MFBConstants.LOG_TAG,
            String.format("Adding image %d for flight %d", mfbii.id, idLocalDB)
        )

        val rgmfbNew : MutableList<MFBImageInfo> = ArrayList()
        if (rgFlightImages != null)
            rgmfbNew.addAll(rgFlightImages!!)
        rgmfbNew.add(mfbii)
        rgFlightImages = rgmfbNew.toTypedArray()
    }

    // don't update once this has been initialized - we can overwrite downloaded images.
    val imagesForFlight: Unit
        get() {
            // don't update once this has been initialized - we can overwrite downloaded images.
            if (rgFlightImages == null || rgFlightImages!!.isEmpty())
                rgFlightImages = MFBImageInfo.getLocalImagesForId(idLocalDB, PictureDestination.FlightImage)
        }

    fun deletePendingImagesForFlight() {
        val mfbii = MFBImageInfo(PictureDestination.FlightImage)
        mfbii.deletePendingImages(idLocalDB)
        rgFlightImages = emptyArray() // since it's invalid; we'll need to reload the flight from recents now.
    }

    fun hasImages(): Boolean {
        return rgFlightImages != null && rgFlightImages!!.isNotEmpty()
    }

    fun isNewFlight(): Boolean {
        return idFlight == ID_NEW_FLIGHT
    }

    fun isAwaitingUpload(): Boolean {
        return idFlight <= ID_UNSUBMITTED_FLIGHT
    }

    fun isQueuedFlight(): Boolean {
        return idFlight == ID_QUEUED_FLIGHT_UNSUBMITTED
    }

    fun isExistingFlight(): Boolean {
        return idFlight > 0
    }

    val isKnownEngineStart: Boolean
        get() = !isNullDate(dtEngineStart)
    val isKnownEngineEnd: Boolean
        get() = !isNullDate(dtEngineEnd)
    val isKnownFlightStart: Boolean
        get() = !isNullDate(dtFlightStart)
    val isKnownFlightEnd: Boolean
        get() = !isNullDate(dtFlightEnd)

    val isKnownBlockOut: Boolean
        get() = !isNullDate(propDateForID(CustomPropertyType.idPropTypeBlockOut))

    val isKnownBlockIn: Boolean
        get() = !isNullDate(propDateForID(CustomPropertyType.idPropTypeBlockIn))

    private val isKnownEngineTime: Boolean
        get() = isKnownEngineStart && isKnownEngineEnd

    fun flightInProgress(): Boolean {
        // Issue #293: support block, not just engine
        // Flight could be in progress if ANY OF: Engine Start, Flight Start, Block Out
        // AND NONE of: Engine End, Block In
        val fHasStart = isKnownEngineStart || isKnownFlightStart || isKnownBlockOut
        val fHasEnd = isKnownEngineEnd || isKnownBlockIn
        return fHasStart && !fHasEnd
    }

    private val isKnownFlightTime: Boolean
        get() = isKnownFlightStart && isKnownFlightEnd
    val isEmptyFlight: Boolean
        get() {
            val fHasAtMostTachStart =
                rgCustomProperties.isEmpty() || rgCustomProperties.size == 1 && rgCustomProperties[0].idPropType == CustomPropertyType.idPropTypeTachStart
            return idFlight == ID_NEW_FLIGHT && szComments.isEmpty() && szRoute.isEmpty() && cApproaches == 0 && cLandings == 0 && cFullStopLandings == 0 && cNightLandings == 0 &&
                    !fHold && hobbsEnd == 0.0 && decNight == 0.0 && decSimulatedIFR == 0.0 && decIMC == 0.0 && decXC == 0.0 && decDual == 0.0 && decGrndSim == 0.0 && decCFI == 0.0 && decSIC == 0.0 && decPIC == 0.0 && decTotal == 0.0 &&
                    fHasAtMostTachStart
        }

    // Custom Properties
    fun syncProperties() {
        rgCustomProperties = fromDB(idLocalDB)
        // In memory, the properties need to refer to the same ID as the calling flight
        // whereas in the DB, they refer to the LOCAL id of the flight.
        // Note that this is OK because it is ALWAYS a one-way process: from the DB into memory; we never
        // persist from memory back to the DB.
        for (fp in rgCustomProperties) fp.idFlight = idFlight
    }

    fun addOrSetPropertyDouble(idPropType: Int, decValue: Double) : FlightProperty? {
        // expand the list of all properties, even ones that aren't currently set
        val rgfpAll = crossProduct(fromDB(idLocalDB), cachedPropertyTypes)

        for (fp in rgfpAll) {
            if (fp.idPropType == idPropType) {
                // set it, distill the properties, and save 'em to the db.
                fp.decValue = decValue
                val rgfpUpdated = distillList(rgfpAll)
                rewritePropertiesForFlight(idLocalDB, rgfpUpdated)
                syncProperties()
                return fp
            }
        }
        return null
    }

    fun addOrSetPropertyDate(idPropType: Int, dateValue: Date) : FlightProperty? {
        // expand the list of all properties, even ones that aren't currently set
        val rgfpAll = crossProduct(fromDB(idLocalDB), cachedPropertyTypes)

        for (fp in rgfpAll) {
            if (fp.idPropType == idPropType) {
                // set it, distill the properties, and save 'em to the db.
                fp.dateValue = dateValue
                val rgfpUpdated = distillList(rgfpAll)
                rewritePropertiesForFlight(idLocalDB, rgfpUpdated)
                syncProperties()
                return fp
            }
        }

        return null
    }

    fun addNightTakeoff() {
        // expand the list of all properties, even ones that aren't currently set
        val rgfpAll = crossProduct(fromDB(idLocalDB), cachedPropertyTypes)

        // find the nighttime takeoff property
        for (fp in rgfpAll) {
            if (fp.idPropType == CustomPropertyType.idPropTypeNightTakeOff) {
                // increment it, distill the properties, and save 'em to the db.
                fp.intValue++
                val rgfpUpdated = distillList(rgfpAll)
                rewritePropertiesForFlight(idLocalDB, rgfpUpdated)
                syncProperties()
                return
            }
        }
    }

    fun propertyWithID(id: Int): FlightProperty? {
        for (fp in rgCustomProperties) {
            if (fp.idPropType == id) return fp
        }
        return null
    }

    fun propDoubleForID(id: Int): Double {
        return propertyWithID(id)?.decValue ?: 0.0
    }

    fun propDateForID(id: Int): Date? {
        return propertyWithID(id)?.dateValue
    }

    fun removePropertyWithID(idPropType: Int) {
        val fp = propertyWithID(idPropType)
        if (fp != null) {
            fp.intValue = 0
            val rgProps = crossProduct(rgCustomProperties, cachedPropertyTypes)
            val rgfpUpdated = distillList(rgProps)
            rewritePropertiesForFlight(idLocalDB, rgfpUpdated)
        }
    }

    fun addApproachDescription(szApproachDesc: String) {
        // expand the list of all properties, even ones that aren't currently set
        val rgfpAll = crossProduct(fromDB(idLocalDB), cachedPropertyTypes)

        // find the nighttime takeoff property
        for (fp in rgfpAll) {
            if (fp.idPropType == CustomPropertyType.idPropTypeApproachDesc) {
                fp.stringValue = (fp.stringValue + " " + szApproachDesc).trim { it <= ' ' }
                val rgfpUpdated = distillList(rgfpAll)
                rewritePropertiesForFlight(idLocalDB, rgfpUpdated)
                syncProperties()
                return
            }
        }
    }

    // serialization methods
    override fun getPropertyCount(): Int {
        return FlightProp.values().size
    }

    override fun getProperty(i: Int): Any {
        return when (FlightProp.values()[i]) {
            FlightProp.PIDAircraft -> idAircraft
            FlightProp.PIDcAppNP -> cApproachNonPrecision
            FlightProp.PIDcAppPrecision -> cApproachPrecision
            FlightProp.PIDcApproaches -> cApproaches
            FlightProp.PIDCatClassOverride -> idCatClassOverride
            FlightProp.PIDcFSDay -> cFullStopLandings
            FlightProp.PIDcFSNight -> cNightLandings
            FlightProp.PIDcLandings -> cLandings
            FlightProp.PIDdCFI -> decCFI
            FlightProp.PIDdDual -> decDual
            FlightProp.PIDdGrnd -> decGrndSim
            FlightProp.PIDdHobbsEnd -> hobbsEnd
            FlightProp.PIDdHobbsStart -> hobbsStart
            FlightProp.PIDdIMC -> decIMC
            FlightProp.PIDdNight -> decNight
            FlightProp.PIDdPIC -> decPIC
            FlightProp.PIDdSIC -> decSIC
            FlightProp.PIDdSimulatedIFR -> decSimulatedIFR
            FlightProp.PIDdTotal -> decTotal
            FlightProp.PIDdtEEnd -> dtEngineEnd
            FlightProp.PIDdtEStart -> dtEngineStart
            FlightProp.PIDdtFEnd -> dtFlightEnd
            FlightProp.PIDdtFStart -> dtFlightStart
            FlightProp.PIDdXC -> decXC
            FlightProp.PIDfHasData -> fHasDataStream
            FlightProp.PIDfHold -> fHold
            FlightProp.PIDFlightDate -> dtFlight
            FlightProp.PIDFlightId -> idFlight
            FlightProp.PIDfPublic -> fPublic
            FlightProp.PIDszCatClassDisplay -> szCatClassDisplay
            FlightProp.PIDszCommets -> szComments
            FlightProp.PIDszData -> szFlightData
            FlightProp.PIDszErr -> szError
            FlightProp.PIDszModelDisplay -> szModelDisplay
            FlightProp.PIDszRoute -> szRoute
            FlightProp.PIDszTailDisplay -> szTailNumDisplay
            FlightProp.PIDUser -> szUser
            FlightProp.PIDFlightColor -> mFlightColorHex ?: ""
            FlightProp.PIDProperties ->                 // return this.rgCustomProperties;
                Vector(listOf(*rgCustomProperties))
            FlightProp.PIDExistingImages -> if (rgFlightImages == null) Vector() else Vector(rgFlightImages!!.asList())
            FlightProp.PIDSend -> sendLink
            FlightProp.PIDShare -> shareLink
            FlightProp.PIDPendingID -> mPendingID
        }
    }

    override fun setProperty(i: Int, value: Any) {
        val fp = FlightProp.values()[i]
        val sz = value.toString()
        when (fp) {
            FlightProp.PIDAircraft -> idAircraft = sz.toInt()
            FlightProp.PIDcAppNP -> cApproachNonPrecision = sz.toInt()
            FlightProp.PIDcAppPrecision -> cApproachPrecision = sz.toInt()
            FlightProp.PIDcApproaches -> cApproaches = sz.toInt()
            FlightProp.PIDCatClassOverride -> idCatClassOverride = sz.toInt()
            FlightProp.PIDcFSDay -> cFullStopLandings = sz.toInt()
            FlightProp.PIDcFSNight -> cNightLandings = sz.toInt()
            FlightProp.PIDcLandings -> cLandings = sz.toInt()
            FlightProp.PIDdCFI -> decCFI = sz.toDouble()
            FlightProp.PIDdDual -> decDual = sz.toDouble()
            FlightProp.PIDdGrnd -> decGrndSim = sz.toDouble()
            FlightProp.PIDdHobbsEnd -> hobbsEnd = sz.toDouble()
            FlightProp.PIDdHobbsStart -> hobbsStart = sz.toDouble()
            FlightProp.PIDdIMC -> decIMC = sz.toDouble()
            FlightProp.PIDdNight -> decNight = sz.toDouble()
            FlightProp.PIDdPIC -> decPIC = sz.toDouble()
            FlightProp.PIDdSIC -> decSIC = sz.toDouble()
            FlightProp.PIDdSimulatedIFR -> decSimulatedIFR = sz.toDouble()
            FlightProp.PIDdTotal -> decTotal = sz.toDouble()
            FlightProp.PIDdtEEnd -> dtEngineEnd = IsoDate.stringToDate(sz, IsoDate.DATE_TIME)
            FlightProp.PIDdtEStart -> dtEngineStart = IsoDate.stringToDate(sz, IsoDate.DATE_TIME)
            FlightProp.PIDdtFEnd -> dtFlightEnd = IsoDate.stringToDate(sz, IsoDate.DATE_TIME)
            FlightProp.PIDdtFStart -> dtFlightStart = IsoDate.stringToDate(sz, IsoDate.DATE_TIME)
            FlightProp.PIDdXC -> decXC = sz.toDouble()
            FlightProp.PIDfHasData -> fHasDataStream = java.lang.Boolean.parseBoolean(sz)
            FlightProp.PIDfHold -> fHold = java.lang.Boolean.parseBoolean(sz)
            FlightProp.PIDFlightDate -> dtFlight = IsoDate.stringToDate(sz, IsoDate.DATE)
            FlightProp.PIDFlightId -> idFlight = sz.toInt()
            FlightProp.PIDfPublic -> fPublic = java.lang.Boolean.parseBoolean(sz)
            FlightProp.PIDszCatClassDisplay -> szCatClassDisplay = sz
            FlightProp.PIDszCommets -> szComments = sz
            FlightProp.PIDszData -> szFlightData = sz
            FlightProp.PIDszErr -> szError = sz
            FlightProp.PIDszModelDisplay -> szModelDisplay = sz
            FlightProp.PIDszRoute -> szRoute = sz
            FlightProp.PIDszTailDisplay -> szTailNumDisplay = sz
            FlightProp.PIDUser -> szUser = sz
            FlightProp.PIDSend -> sendLink = sz
            FlightProp.PIDShare -> shareLink = sz
            FlightProp.PIDFlightColor -> mFlightColorHex = sz
            FlightProp.PIDProperties, FlightProp.PIDExistingImages -> {}
            else -> {}
        }
    }

    override fun getPropertyInfo(i: Int, h: Hashtable<*, *>?, pi: PropertyInfo) {
        when (FlightProp.values()[i]) {
            FlightProp.PIDAircraft -> {
                pi.type = PropertyInfo.INTEGER_CLASS
                pi.name = "AircraftID"
            }
            FlightProp.PIDcAppNP -> {
                pi.type = PropertyInfo.INTEGER_CLASS
                pi.name = "NonPrecisionApproaches"
            }
            FlightProp.PIDcAppPrecision -> {
                pi.type = PropertyInfo.INTEGER_CLASS
                pi.name = "PrecisionApproaches"
            }
            FlightProp.PIDcApproaches -> {
                pi.type = PropertyInfo.INTEGER_CLASS
                pi.name = "Approaches"
            }
            FlightProp.PIDCatClassOverride -> {
                pi.type = PropertyInfo.INTEGER_CLASS
                pi.name = "CatClassOverride"
            }
            FlightProp.PIDcFSDay -> {
                pi.type = PropertyInfo.INTEGER_CLASS
                pi.name = "FullStopLandings"
            }
            FlightProp.PIDcFSNight -> {
                pi.type = PropertyInfo.INTEGER_CLASS
                pi.name = "NightLandings"
            }
            FlightProp.PIDcLandings -> {
                pi.type = PropertyInfo.INTEGER_CLASS
                pi.name = "Landings"
            }
            FlightProp.PIDdCFI -> {
                pi.type = PropertyInfo.OBJECT_CLASS
                pi.name = "CFI"
            }
            FlightProp.PIDdDual -> {
                pi.type = PropertyInfo.OBJECT_CLASS
                pi.name = "Dual"
            }
            FlightProp.PIDdGrnd -> {
                pi.type = PropertyInfo.OBJECT_CLASS
                pi.name = "GroundSim"
            }
            FlightProp.PIDdHobbsEnd -> {
                pi.type = PropertyInfo.OBJECT_CLASS
                pi.name = "HobbsEnd"
            }
            FlightProp.PIDdHobbsStart -> {
                pi.type = PropertyInfo.OBJECT_CLASS
                pi.name = "HobbsStart"
            }
            FlightProp.PIDdIMC -> {
                pi.type = PropertyInfo.OBJECT_CLASS
                pi.name = "IMC"
            }
            FlightProp.PIDdNight -> {
                pi.type = PropertyInfo.OBJECT_CLASS
                pi.name = "Nighttime"
            }
            FlightProp.PIDdPIC -> {
                pi.type = PropertyInfo.OBJECT_CLASS
                pi.name = "PIC"
            }
            FlightProp.PIDdSIC -> {
                pi.type = PropertyInfo.OBJECT_CLASS
                pi.name = "SIC"
            }
            FlightProp.PIDdSimulatedIFR -> {
                pi.type = PropertyInfo.OBJECT_CLASS
                pi.name = "SimulatedIFR"
            }
            FlightProp.PIDdTotal -> {
                pi.type = PropertyInfo.OBJECT_CLASS
                pi.name = "TotalFlightTime"
            }
            FlightProp.PIDdtEEnd -> {
                pi.type = PropertyInfo.OBJECT_CLASS
                pi.name = "EngineEnd"
            }
            FlightProp.PIDdtEStart -> {
                pi.type = PropertyInfo.OBJECT_CLASS
                pi.name = "EngineStart"
            }
            FlightProp.PIDdtFEnd -> {
                pi.type = PropertyInfo.OBJECT_CLASS
                pi.name = "FlightEnd"
            }
            FlightProp.PIDdtFStart -> {
                pi.type = PropertyInfo.OBJECT_CLASS
                pi.name = "FlightStart"
            }
            FlightProp.PIDdXC -> {
                pi.type = PropertyInfo.OBJECT_CLASS
                pi.name = "CrossCountry"
            }
            FlightProp.PIDfHasData -> {
                pi.type = PropertyInfo.BOOLEAN_CLASS
                pi.name = "FHasData"
            }
            FlightProp.PIDfHold -> {
                pi.type = PropertyInfo.BOOLEAN_CLASS
                pi.name = "fHoldingProcedures"
            }
            FlightProp.PIDFlightDate -> {
                pi.type = PropertyInfo.OBJECT_CLASS
                pi.name = "Date"
            }
            FlightProp.PIDFlightId -> {
                pi.type = PropertyInfo.INTEGER_CLASS
                pi.name = "FlightID"
            }
            FlightProp.PIDfPublic -> {
                pi.type = PropertyInfo.BOOLEAN_CLASS
                pi.name = "fIsPublic"
            }
            FlightProp.PIDszCatClassDisplay -> {
                pi.type = PropertyInfo.STRING_CLASS
                pi.name = "CatClassDisplay"
            }
            FlightProp.PIDszCommets -> {
                pi.type = PropertyInfo.STRING_CLASS
                pi.name = "Comment"
            }
            FlightProp.PIDszData -> {
                pi.type = PropertyInfo.STRING_CLASS
                pi.name = "FlightData"
            }
            FlightProp.PIDszErr -> {
                pi.type = PropertyInfo.STRING_CLASS
                pi.name = "Error"
            }
            FlightProp.PIDszModelDisplay -> {
                pi.type = PropertyInfo.STRING_CLASS
                pi.name = "ModelDisplay"
            }
            FlightProp.PIDszRoute -> {
                pi.type = PropertyInfo.STRING_CLASS
                pi.name = "Route"
            }
            FlightProp.PIDszTailDisplay -> {
                pi.type = PropertyInfo.STRING_CLASS
                pi.name = "TailNumDisplay"
            }
            FlightProp.PIDUser -> {
                pi.type = PropertyInfo.STRING_CLASS
                pi.name = "User"
            }
            FlightProp.PIDFlightColor -> {
                pi.type = PropertyInfo.STRING_CLASS
                pi.name = "FlightColorHex"
            }
            FlightProp.PIDProperties -> {
                pi.type = PropertyInfo.VECTOR_CLASS
                pi.name = "CustomProperties"
                pi.elementType = PropertyInfo()
                pi.elementType.type = PropertyInfo.OBJECT_CLASS
                pi.elementType.name = "CustomFlightProperty"
            }
            FlightProp.PIDExistingImages -> {
                pi.type = PropertyInfo.VECTOR_CLASS
                pi.name = "FlightImages"
                pi.elementType = PropertyInfo()
                pi.elementType.type = PropertyInfo.OBJECT_CLASS
                pi.elementType.name = "MFBImageInfo"
            }
            FlightProp.PIDSend -> {
                pi.type = PropertyInfo.STRING_CLASS
                pi.name = "SendFlightLink"
            }
            FlightProp.PIDShare -> {
                pi.type = PropertyInfo.STRING_CLASS
                pi.name = "SocialMediaLink"
            }
            FlightProp.PIDPendingID -> {
                pi.type = PropertyInfo.STRING_CLASS
                pi.name = "PendingID"
            }
        }
    }

    override fun toProperties(so: SoapObject) {
        so.addProperty("User", szUser)
        so.addProperty("AircraftID", idAircraft)
        so.addProperty("CatClassOverride", idCatClassOverride)
        so.addProperty("NightLandings", cNightLandings)
        so.addProperty("FullStopLandings", cFullStopLandings)
        so.addProperty("Landings", cLandings)
        so.addProperty("Approaches", cApproaches)
        so.addProperty("cApproachPrecision", cApproachPrecision)
        so.addProperty("cApproachNonPrecision", cApproachNonPrecision)
        addDouble(so, "CrossCountry", decXC)
        addDouble(so, "Nighttime", decNight)
        addDouble(so, "IMC", decIMC)
        addDouble(so, "SimulatedIFR", decSimulatedIFR)
        addDouble(so, "GroundSim", decGrndSim)
        addDouble(so, "Dual", decDual)
        addDouble(so, "CFI", decCFI)
        addDouble(so, "PIC", decPIC)
        addDouble(so, "SIC", decSIC)
        addDouble(so, "TotalFlightTime", decTotal)
        so.addProperty("fHoldingProcedures", fHold)
        so.addProperty("Route", szRoute)
        so.addProperty("Comment", szComments)
        so.addProperty("fIsPublic", fPublic)
        so.addProperty("Date", IsoDate.dateToString(dtFlight, IsoDate.DATE))
        so.addProperty("FlightID", idFlight)
        addDouble(so, "HobbsStart", hobbsStart)
        addDouble(so, "HobbsEnd", hobbsEnd)
        addNullableDate(so, "FlightStart", dtFlightStart)
        addNullableDate(so, "FlightEnd", dtFlightEnd)
        addNullableDate(so, "EngineStart", dtEngineStart)
        addNullableDate(so, "EngineEnd", dtEngineEnd)
        so.addProperty("ModelDisplay", szModelDisplay)
        so.addProperty("TailNumDisplay", szTailNumDisplay)
        so.addProperty("CatClassDisplay", szCatClassDisplay)
        so.addProperty("FlightData", szFlightData)
        so.addProperty("CustomProperties", rgCustomProperties)
        // we don't need to write back existing image properties or send/share properties
    }

    private fun initFromProps(so: SoapObject) {
        szError = ""
        try {
            idFlight = so.getProperty("FlightID").toString().toInt()
        } catch (ignored: Exception) {
        }
        szUser = so.getProperty("User").toString()
        idAircraft = so.getProperty("AircraftID").toString().toInt()
        idCatClassOverride = so
            .getProperty("CatClassOverride").toString().toInt()
        cNightLandings = so.getProperty("NightLandings")
            .toString().toInt()
        cFullStopLandings = so.getProperty("FullStopLandings")
            .toString().toInt()
        cLandings = so.getProperty("Landings").toString().toInt()
        cApproaches = so.getProperty("Approaches").toString().toInt()
        val szPrecApproaches = so.getPropertySafelyAsString("PrecisionApproaches")
        val szNonPrecApproaches = so.getPropertySafelyAsString("PrecisionApproaches")
        cApproachPrecision = if (szPrecApproaches.isNotEmpty()) szPrecApproaches.toInt() else 0
        cApproachNonPrecision =
            if (szNonPrecApproaches.isNotEmpty()) szNonPrecApproaches.toInt() else 0
        decXC = so.getProperty("CrossCountry").toString().toDouble()
        decNight = so.getProperty("Nighttime").toString().toDouble()
        decIMC = so.getProperty("IMC").toString().toDouble()
        decSimulatedIFR = so.getProperty("SimulatedIFR")
            .toString().toDouble()
        decGrndSim = so.getProperty("GroundSim").toString().toDouble()
        decDual = so.getProperty("Dual").toString().toDouble()
        decCFI = so.getProperty("CFI").toString().toDouble()
        decPIC = so.getProperty("PIC").toString().toDouble()
        decSIC = so.getProperty("SIC").toString().toDouble()
        decTotal = so.getProperty("TotalFlightTime")
            .toString().toDouble()
        fHold = java.lang.Boolean.parseBoolean(
            so.getProperty("fHoldingProcedures")
                .toString()
        )
        szRoute = readNullableString(so, "Route")
        szComments = readNullableString(so, "Comment")
        fPublic = java.lang.Boolean.parseBoolean(so.getProperty("fIsPublic").toString())
        dtFlight = IsoDate.stringToDate(so.getProperty("Date").toString(), IsoDate.DATE)
        dtFlightStart = readNullableDate(so, "FlightStart")!!
        dtFlightEnd = readNullableDate(so, "FlightEnd")!!
        dtEngineStart = readNullableDate(so, "EngineStart")!!
        dtEngineEnd = readNullableDate(so, "EngineEnd")!!
        hobbsStart = so.getProperty("HobbsStart").toString().toDouble()
        hobbsEnd = so.getProperty("HobbsEnd").toString().toDouble()
        szModelDisplay = readNullableString(so, "ModelDisplay")
        szTailNumDisplay = readNullableString(so, "TailNumDisplay")
        szCatClassDisplay = readNullableString(so, "CatClassDisplay")
        sendLink = so.getPropertySafelyAsString("SendFlightLink")
        shareLink = so.getPropertySafelyAsString("SocialMediaLink")
        mFlightColorHex = so.getPropertySafelyAsString(("FlightColorHex"))

        // FlightData is not always present.
        try {
            szFlightData = readNullableString(so, "FlightData")
        } catch (ignored: Exception) {
        }
        val szSigState = so.getPropertySafelyAsString("CFISignatureState")
        signatureStatus =
            if (szSigState.isNotEmpty()) SigStatus.valueOf(szSigState) else SigStatus.None
        // Remaining fields not always present.
        try {
            signatureComments = so.getPropertySafelyAsString("CFIComments")
            signatureDate = readNullableDate(so, "CFISignatureDate")
            signatureCFICert = so.getPropertySafelyAsString("CFICertificate")
            signatureCFIExpiration = readNullableDate(so, "CFIExpiration")
            signatureCFIName = so.getPropertySafelyAsString("CFIName")
            signatureHasDigitizedSig =
                java.lang.Boolean.parseBoolean(so.getProperty("HasDigitizedSig").toString())
        } catch (ignored: Exception) {
        }
        val props = so.getPropertySafely("CustomProperties") as SoapObject
        val cProps = props.propertyCount

        val rgProps = ArrayList<FlightProperty>()
        for (i in 0 until cProps) {
            val fp = FlightProperty()
            fp.fromProperties((props.getProperty(i) as SoapObject))
            rgProps.add(fp)
        }
        rgCustomProperties = rgProps.toTypedArray()

        val images = so.getPropertySafely("FlightImages") as SoapObject
        val cImages = images.propertyCount
        val rgImages = ArrayList<MFBImageInfo>()
        for (i in 0 until cImages) {
            val mfbii = MFBImageInfo()
            mfbii.fromProperties((images.getProperty(i) as SoapObject))
            rgImages.add(mfbii)
        }
        rgFlightImages = rgImages.toTypedArray()
    }

    public override fun fromProperties(so: SoapObject) {
        initFromProps(so)
    }

    fun deleteUnsubmittedFlightFromLocalDB() {
        if (idLocalDB <= 0) return

        // kill the images for the flight while we still have referential integrity in the localID
        deletePendingImagesForFlight()
        val db = MFBMain.mDBHelper!!.writableDatabase
        try {
            val rgIdArg = arrayOf(String.format(Locale.US, "%d", idLocalDB))
            // delete the flight itself
            db.delete("Flights", "_id = ?", rgIdArg)

            // delete flightproperties for this flight.
            db.delete(FlightProperty.TABLENAME, "idFlight = ?", rgIdArg)
            idLocalDB = -1
        } catch (e: Exception) {
            Log.v(MFBConstants.LOG_TAG, "Error deleting unsubmitted flight - " + e.message)
        }
    }

    fun toDB(): Boolean {
        var fResult = true
        val df = SimpleDateFormat(MFBConstants.TIMESTAMP, Locale.US)
        df.timeZone = TimeZone.getTimeZone("UTC")
        val cv = ContentValues()
        cv.put("idFlight", idFlight)
        cv.put("szUser", szUser)
        cv.put("dtFlight", df.format(dtFlight))
        cv.put("idCatClassOverride", idCatClassOverride)
        cv.put("idAircraft", idAircraft)
        cv.put("cApproaches", cApproaches)
        cv.put("cApproachPrecision", cApproachPrecision)
        cv.put("cApproachNonPrecision", cApproachNonPrecision)
        cv.put("cLandings", cLandings)
        cv.put("cNightLandings", cNightLandings)
        cv.put("cFullStopLandings", cFullStopLandings)
        cv.put("decXC", decXC)
        cv.put("decIMC", decIMC)
        cv.put("decSimulatedIFR", decSimulatedIFR)
        cv.put("decGrndSim", decGrndSim)
        cv.put("decDual", decDual)
        cv.put("decNight", decNight)
        cv.put("decPIC", decPIC)
        cv.put("decCFI", decCFI)
        cv.put("decSIC", decSIC)
        cv.put("decTotal", decTotal)
        cv.put("fHold", fHold.toString())
        cv.put("szComments", szComments)
        cv.put("szRoute", szRoute)
        cv.put("fPublic", fPublic.toString())
        cv.put("dtFlightStart", df.format(dtFlightStart))
        cv.put("dtFlightEnd", df.format(dtFlightEnd))
        cv.put("dtEngineStart", df.format(dtEngineStart))
        cv.put("dtEngineEnd", df.format(dtEngineEnd))
        cv.put("hobbsStart", hobbsStart)
        cv.put("hobbsEnd", hobbsEnd)
        cv.put("szFlightData", szFlightData)
        cv.put("szError", szError)
        cv.put("forcePending", fForcePending.toString())
        cv.put("PendingID", mPendingID)
        val db = MFBMain.mDBHelper!!.writableDatabase
        try {
            if (idLocalDB <= 0) idLocalDB = db.insert("Flights", null, cv) else db.update(
                "Flights", cv, "_id = ?", arrayOf(
                    String.format(Locale.US, "%d", idLocalDB)
                )
            )
        } catch (e: Exception) {
            fResult = false
            szError = e.message!!
            Log.e(MFBConstants.LOG_TAG, "Error persisting logbook entry to db - $szError")
        }
        return fResult
    }

    private fun fromCursor(c: Cursor) {
        val df = SimpleDateFormat(MFBConstants.TIMESTAMP, Locale.US)
        df.timeZone = TimeZone.getTimeZone("UTC")
        try {
            idFlight = c.getInt(c.getColumnIndexOrThrow("idFlight"))
            szUser = c.getString(c.getColumnIndexOrThrow("szUser"))
            dtFlight = df.parse(c.getString(c.getColumnIndexOrThrow("dtFlight"))) ?: Date(0)
            idCatClassOverride = c.getInt(c.getColumnIndexOrThrow("idCatClassOverride"))
            idAircraft = c.getInt(c.getColumnIndexOrThrow("idAircraft"))
            cApproaches = c.getInt(c.getColumnIndexOrThrow("cApproaches"))
            cApproachPrecision = c.getInt(c.getColumnIndexOrThrow("cApproachPrecision"))
            cApproachNonPrecision = c.getInt(c.getColumnIndexOrThrow("cApproachNonPrecision"))
            cLandings = c.getInt(c.getColumnIndexOrThrow("cLandings"))
            cNightLandings = c.getInt(c.getColumnIndexOrThrow("cNightLandings"))
            cFullStopLandings = c.getInt(c.getColumnIndexOrThrow("cFullStopLandings"))
            decXC = c.getDouble(c.getColumnIndexOrThrow("decXC"))
            decIMC = c.getDouble(c.getColumnIndexOrThrow("decIMC"))
            decSimulatedIFR = c.getDouble(c.getColumnIndexOrThrow("decSimulatedIFR"))
            decGrndSim = c.getDouble(c.getColumnIndexOrThrow("decGrndSim"))
            decDual = c.getDouble(c.getColumnIndexOrThrow("decDual"))
            decNight = c.getDouble(c.getColumnIndexOrThrow("decNight"))
            decPIC = c.getDouble(c.getColumnIndexOrThrow("decPIC"))
            decCFI = c.getDouble(c.getColumnIndexOrThrow("decCFI"))
            decSIC = c.getDouble(c.getColumnIndexOrThrow("decSIC"))
            decTotal = c.getDouble(c.getColumnIndexOrThrow("decTotal"))
            fHold = java.lang.Boolean.parseBoolean(c.getString(c.getColumnIndexOrThrow("fHold")))
            szComments = c.getString(c.getColumnIndexOrThrow("szComments"))
            szRoute = c.getString(c.getColumnIndexOrThrow("szRoute"))
            fPublic =
                java.lang.Boolean.parseBoolean(c.getString(c.getColumnIndexOrThrow("fPublic")))
            dtFlightStart = df.parse(c.getString(c.getColumnIndexOrThrow("dtFlightStart"))) ?: Date(0)
            dtFlightEnd = df.parse(c.getString(c.getColumnIndexOrThrow("dtFlightEnd"))) ?: Date(0)
            dtEngineStart = df.parse(c.getString(c.getColumnIndexOrThrow("dtEngineStart"))) ?: Date(0)
            dtEngineEnd = df.parse(c.getString(c.getColumnIndexOrThrow("dtEngineEnd"))) ?: Date(0)
            hobbsStart = c.getDouble(c.getColumnIndexOrThrow("hobbsStart"))
            hobbsEnd = c.getDouble(c.getColumnIndexOrThrow("hobbsEnd"))
            szFlightData = c.getString(c.getColumnIndexOrThrow("szFlightData"))
            szError = c.getString(c.getColumnIndexOrThrow("szError"))
            mPendingID = c.getString(c.getColumnIndexOrThrow("PendingID"))
            fForcePending =
                java.lang.Boolean.parseBoolean(c.getString(c.getColumnIndexOrThrow("forcePending")))
        } catch (e: Exception) {
            Log.e(MFBConstants.LOG_TAG, "FromCursor failed: " + e.localizedMessage)
            idLocalDB = -1
        }
    }

    private fun fromDB() {
        if (idLocalDB > 0) {
            val db = MFBMain.mDBHelper!!.writableDatabase
            try {
                db.query(
                    "Flights",
                    null,
                    "_id = ?",
                    arrayOf(String.format(Locale.US, "%d", idLocalDB)),
                    null,
                    null,
                    null
                ).use { c ->
                    if (c != null && c.count == 1) {
                        c.moveToFirst()
                        fromCursor(c)
                    } else throw Exception("Query for flight from db failed!")
                }
            } catch (e: Exception) {
                Log.e(MFBConstants.LOG_TAG, "Requested stored flight failed to load - resetting")
                idLocalDB = -1
            }
        }
    }

    override val defaultImage: MFBImageInfo?
        get() {
            if (hasImages()) return rgFlightImages!![0] else {
                if (ActRecentsWS.m_rgac == null) ActRecentsWS.m_rgac = AircraftSvc().cachedAircraft
                val ac = getAircraftById(idAircraft, ActRecentsWS.m_rgac)
                if (ac != null && ac.hasImage()) return ac.aircraftImages!![0]
            }
            return null
        }

    // Autofill utilities
    fun autoFillHobbs(totalTimePaused: Long): Double {
        var dtHobbs: Long = 0
        var dtFlight: Long = 0
        var dtEngine: Long = 0

        // compute the flight time, in ms, if known
        if (isKnownFlightTime) dtFlight = dtFlightEnd.time - dtFlightStart.time

        // and engine time, if known.
        if (isKnownEngineTime) dtEngine = dtEngineEnd.time - dtEngineStart.time
        if (hobbsStart > 0) {
            when (MFBLocation.fPrefAutoFillHobbs) {
                AutoFillOptions.EngineTime -> dtHobbs = dtEngine
                AutoFillOptions.FlightTime -> dtHobbs = dtFlight
                else -> {}
            }
            dtHobbs -= totalTimePaused
            if (dtHobbs > 0) {
                hobbsEnd = hobbsStart + dtHobbs / MFBConstants.MS_PER_HOUR
                if (MFBLocation.fPrefRoundNearestTenth) hobbsEnd =
                    (hobbsEnd * 10.0).roundToInt() / 10.0
            }
        }
        return dtHobbs.toDouble()
    }

    fun autoFillTotal(ac: Aircraft?, totalTimePaused: Long): Double {
        var dtTotal = 0.0
        when (MFBLocation.fPrefAutoFillTime) {
            AutoFillOptions.EngineTime -> if (isKnownEngineTime) dtTotal =
                (dtEngineEnd.time - dtEngineStart.time - totalTimePaused) / MFBConstants.MS_PER_HOUR
            AutoFillOptions.FlightTime -> if (isKnownFlightTime) dtTotal =
                (dtFlightEnd.time - dtFlightStart.time - totalTimePaused) / MFBConstants.MS_PER_HOUR
            AutoFillOptions.HobbsTime ->                 // NOTE: we do NOT subtract totalTimePaused here because hobbs should already have subtracted pause time,
                // whether from being entered by user (hobbs on airplane pauses on ground or with engine stopped)
                // or from this being called by autohobbs (which has already subtracted it)
                if (hobbsStart > 0 && hobbsEnd > hobbsStart) dtTotal =
                    hobbsEnd - hobbsStart // hobbs is already in hours
            AutoFillOptions.BlockTime -> {
                var blockOut: Long = 0
                var blockIn: Long = 0
                for (fp in rgCustomProperties) {
                    if (fp.idPropType == CustomPropertyType.idPropTypeBlockIn) blockIn =
                        MFBUtil.removeSeconds(
                            fp.dateValue!!
                        ).time
                    if (fp.idPropType == CustomPropertyType.idPropTypeBlockOut) blockOut =
                        MFBUtil.removeSeconds(
                            fp.dateValue!!
                        ).time
                }
                if (blockIn > 0 && blockOut > 0) dtTotal =
                    (blockIn - blockOut - totalTimePaused) / MFBConstants.MS_PER_HOUR
            }
            AutoFillOptions.FlightStartToEngineEnd -> if (isKnownFlightStart && isKnownEngineEnd) dtTotal =
                (dtEngineEnd.time - dtFlightStart.time - totalTimePaused) / MFBConstants.MS_PER_HOUR
            else -> {}
        }
        if (dtTotal > 0) {
            if (MFBLocation.fPrefRoundNearestTenth) dtTotal = (dtTotal * 10.0).roundToInt() / 10.0

            // update totals and XC if this is a real aircraft, else ground sim
            if (ac != null && ac.instanceTypeID == 1) {
                decTotal = dtTotal
                decXC =
                    if (maxDistanceForRoute(szRoute) > MFBConstants.NM_FOR_CROSS_COUNTRY) dtTotal else 0.0
            } else decGrndSim = dtTotal
        }
        return dtTotal
    }

    private fun autoFillCostOfFlight() {
        // Fill in cost of flight.
        val ac = getAircraftById(idAircraft, AircraftSvc().cachedAircraft) ?: return
        val p = Pattern.compile("#PPH:(\\d+(?:[.,]\\d+)?)#", Pattern.CASE_INSENSITIVE)
        val m = p.matcher(ac.privateNotes.uppercase(Locale.getDefault()))
        if (!m.find() || m.group().isEmpty()) return
        val rate: Double
        val nf = NumberFormat.getInstance(Locale.getDefault())
        rate = try {
            Objects.requireNonNull(nf.parse(Objects.requireNonNull(m.group(1)))).toDouble()
        } catch (e: ParseException) {
            return
        }
        if (rate == 0.0) return
        val fpTachStart = propertyWithID(CustomPropertyType.idPropTypeTachStart)
        val fpTachEnd = propertyWithID(CustomPropertyType.idPropTypeTachEnd)
        val tachStart: Double = fpTachStart?.decValue ?: 0.0
        val tachEnd: Double = fpTachEnd?.decValue ?: 0.0
        val time =
            if (hobbsEnd > hobbsStart && hobbsStart > 0) hobbsEnd - hobbsStart else if (tachEnd > tachStart && tachStart > 0) tachEnd - tachStart else decTotal
        val cost = rate * time
        if (cost > 0) addOrSetPropertyDouble(CustomPropertyType.idPropTypeFlightCost, cost)
    }

    private fun autoFillFuel() {
        val fpFuelAtStart = propertyWithID(CustomPropertyType.idPropTypeFuelAtStart)
        val fpFuelAtEnd = propertyWithID(CustomPropertyType.idPropTypeFuelAtEnd)
        val fuelConsumed = (fpFuelAtStart?.decValue ?: 0.0) - (fpFuelAtEnd?.decValue ?: 0.0)
        if (fuelConsumed > 0) {
            addOrSetPropertyDouble(CustomPropertyType.idPropTypeFuelConsumed, fuelConsumed)
            if (decTotal > 0)
                addOrSetPropertyDouble(CustomPropertyType.idPropTypeFuelBurnRate, fuelConsumed / decTotal)
        }
    }

    private fun autoFillInstruction() {
        // Check for ground instruction given or received
        val dual = decDual
        val cfi = decCFI
        if (dual > 0 && cfi == 0.0 || cfi > 0 && dual == 0.0) {
            val fpLessonStart = propertyWithID(CustomPropertyType.idPropTypeLessonStart)
            val fpLessonEnd = propertyWithID(CustomPropertyType.idPropTypeLessonEnd)
            if (fpLessonEnd == null || fpLessonStart == null || fpLessonEnd.dateValue!! <= fpLessonStart.dateValue
            ) return
            val tsLesson =
                (fpLessonEnd.dateValue!!.time - fpLessonStart.dateValue!!.time).toDouble()

            // pull out flight or engine time, whichever is greater
            val tsFlight =
                if (isKnownFlightEnd && isKnownFlightStart && dtFlightEnd > dtFlightStart) (dtFlightEnd.time - dtFlightStart.time).toDouble() else 0.toDouble()
            val tsEngine =
                if (isKnownEngineEnd && isKnownEngineStart && dtEngineEnd > dtEngineStart) (dtEngineEnd.time - dtEngineStart.time).toDouble() else 0.toDouble()
            val tsNonGround = tsFlight.coerceAtLeast(tsEngine).coerceAtLeast(0.0)
            val groundHours = (tsLesson - tsNonGround) / MFBConstants.MS_PER_HOUR
            val idPropTarget =
                if (dual > 0) CustomPropertyType.idPropTypeGroundInstructionReceived else CustomPropertyType.idPropTypeGroundInstructionGiven
            if (groundHours > 0) addOrSetPropertyDouble(idPropTarget, groundHours)
        }
    }

    fun autoFillFinish() {
        autoFillCostOfFlight()
        autoFillFuel()
        autoFillInstruction()
    }

    fun xfillValueForProperty(cpt : CustomPropertyType) :Double {
        if (cpt.idPropType == CustomPropertyType.idPropTypeTachStart)
            return Aircraft.getHighWaterTachForAircraft(idAircraft)
        if (cpt.idPropType == CustomPropertyType.idPropTypeFlightMeterStart)
            return Aircraft.getHighWaterMeterForAircraft(idAircraft)
        if (cpt.isTime())
            return decTotal
        if (cpt.isLanding())
            return cLandings.toDouble()
        if (cpt.isApproach())
            return cApproaches.toDouble()
        return 0.0
    }

    companion object {
        const val ID_NEW_FLIGHT = -1
        const val ID_UNSUBMITTED_FLIGHT = -2
        const val ID_QUEUED_FLIGHT_UNSUBMITTED = -3
        private fun getFlightsWithIdFlight(idFlight: Int): Array<LogbookEntry> {
            val rgleLocal: ArrayList<LogbookEntry> = ArrayList()
            val db = MFBMain.mDBHelper!!.writableDatabase
            try {
                db.query(
                    "Flights",
                    null,
                    "idFlight = ?",
                    arrayOf(String.format(Locale.US, "%d", idFlight)),
                    null,
                    null,
                    null
                ).use { c ->
                    if (c != null) {
                        while (c.moveToNext()) {
                            // Check for a pending flight
                            val szPending = c.getString(c.getColumnIndexOrThrow("PendingID"))
                            val le =
                                if (szPending != null && szPending.isNotEmpty()) PendingFlight() else LogbookEntry()
                            rgleLocal.add(le)
                            le.fromCursor(c)
                            le.idLocalDB = c.getLong(c.getColumnIndexOrThrow("_id"))
                            le.rgFlightImages = MFBImageInfo.getLocalImagesForId(
                                le.idLocalDB,
                                PictureDestination.FlightImage
                            )
                        }
                    } else throw Exception("Query for flight from db failed!")
                }
            } catch (e: Exception) {
                Log.e("LogbookEntry", "Error retrieving local flights: " + e.localizedMessage)
            }
            return rgleLocal.toTypedArray()
        }

        @JvmStatic
        val unsubmittedFlights: Array<LogbookEntry>
            get() = getFlightsWithIdFlight(ID_UNSUBMITTED_FLIGHT)
        private val queuedFlights: Array<LogbookEntry>
            get() = getFlightsWithIdFlight(ID_QUEUED_FLIGHT_UNSUBMITTED)
        @JvmStatic
        val queuedAndUnsubmittedFlights: Array<LogbookEntry>?
            get() = mergeFlightLists(unsubmittedFlights, queuedFlights)
        @JvmStatic
        val newFlights: Array<LogbookEntry>
            get() = getFlightsWithIdFlight(ID_NEW_FLIGHT)

        @JvmStatic
        fun mergeFlightLists(
            rgle1: Array<LogbookEntry>?,
            rgle2: Array<LogbookEntry>?
        ): Array<LogbookEntry>? {
            if (rgle1 == null && rgle2 == null) return arrayOf()
            if (rgle1 == null) return rgle2
            if (rgle2 == null) return rgle1

            val rgleReturn : ArrayList<LogbookEntry> = ArrayList()
            rgleReturn.addAll(rgle1)
            rgleReturn.addAll(rgle2)
            return rgleReturn.toTypedArray()
        }
    }
}