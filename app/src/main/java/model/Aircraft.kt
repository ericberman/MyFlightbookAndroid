/*
	MyFlightbook for Android - provides native access to MyFlightbook
	pilot's logbook
    Copyright (C) 2017-2025 MyFlightbook, LLC

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

import android.content.Context
import android.text.format.DateFormat.getDateFormat
import model.CountryCode.Companion.bestGuessPrefixForTail
import org.ksoap2.serialization.KvmSerializable
import model.LazyThumbnailLoader.ThumbnailedItem
import java.io.Serializable
import org.ksoap2.serialization.SoapObject
import java.util.*
import com.myflightbook.android.R
import com.myflightbook.android.webservices.UTCDate.isNullDate
import org.kobjects.isodate.IsoDate
import org.ksoap2.serialization.PropertyInfo
import kotlin.math.max

class Aircraft : SoapableObject, KvmSerializable, Serializable, ThumbnailedItem {
    private enum class AircraftProp {
        PIDTailNumber, PIDAircratID, PIDModelID, PIDInstanctTypeID, PIDVersion, PIDICAO, PIDAvionicsTechnologyUpgrade, PIDIsGlass, PIDGlassUpgradeDate, PIDLastVOR, PIDLastAltimeter, PIDLastTransponder, PIDLastELT, PIDLastStatic, PIDLastAnnual, PIDRegistrationDue, PIDLast100, PIDLastOil,
        PIDLastEngine, PIDMaintNotes, PIDPublicNotes, PIDPrivateNotes, PIDDefaultImage, PIDDefaultTemplates, PIDHideFromSelection,
        PIDPilotRole, PIDCopyPICName, PIDRevision
    }

    @JvmField
    var tailNumber = ""
    @JvmField
    var aircraftID = -1
    @JvmField
    var modelID = -1
    @JvmField
    var instanceTypeID = 1
    var modelCommonName = ""
    @JvmField
    var modelDescription = ""
    var modelICAO = ""
    var version = 0
    @JvmField
    var aircraftImages: Array<MFBImageInfo>? = arrayOf()
    var mDefaultImage = ""
    @JvmField
    val defaultTemplates = HashSet<Int>()
    var revision = 0

    // Maintenance fields
    var lastVOR: Date? = null
    var lastAltimeter: Date? = null
    var lastTransponder: Date? = null
    var lastELT: Date? = null
    var lastStatic: Date? = null
    var lastAnnual: Date? = null
    var registrationDue: Date? = null
    var last100 = 0.0
    var lastOil = 0.0
    var lastEngine = 0.0
    var maintenanceNote = ""

    // Glass
    enum class AvionicsTechnologyType {
        None, Glass, TAA
    }

    var avionicsTechnologyUpgrade = AvionicsTechnologyType.None
    var isGlass: Boolean = false
    var glassUpgradeDate: Date? = null

    // Notes
    var publicNotes: String = ""
    @JvmField
    var privateNotes: String = ""

    // User preferences
    enum class PilotRole {
        None, PIC, SIC, CFI
    }

    @JvmField
    var hideFromSelection: Boolean = false
    var copyPICNameWithCrossfill: Boolean = false
    var roleForPilot: PilotRole = PilotRole.None
    @JvmField
    var errorString = ""

    constructor() : super()

    override fun toString(): String {
        return if (isAnonymous()) displayTailNumber() else String.format(
            "%s (%s)",
            tailNumber,
            modelDescription
        )
    }

    constructor(so: SoapObject) : super() {
        fromProperties(so)
    }

    fun hasImage(): Boolean {
        return aircraftImages != null && aircraftImages!!.isNotEmpty()
    }

    fun isReal(): Boolean {
        return instanceTypeID == 1
    }

    fun isAnonymous(): Boolean {
        return tailNumber.startsWith(PREFIX_ANON)
    }

    fun displayTailNumber(): String {
        return if (isAnonymous()) String.format("(%s)", modelDescription) else tailNumber
    }

    fun anonTailNumber(): String {
        return String.format(Locale.US, "%s%05d", PREFIX_ANON, modelID)
    }

    fun isValid(c: Context): Boolean {
        // check to see that the tailnumber begins with a country code
        val fStartsWithSim = tailNumber.uppercase(Locale.getDefault()).startsWith(PREFIX_SIM)
        if (isAnonymous()) {
            // nothing to check - by definition, the way we know we are anonymous is that we begin with PREFIX_ANON
            return true
        } else if (isReal()) {
            // Real aircraft - MUST NOT begin with SIM prefix
            if (fStartsWithSim) {
                errorString = c.getString(R.string.errRealAircraftCantUseSIM)
                return false
            }
            val cc = bestGuessPrefixForTail(tailNumber)
            if (cc == null) {
                errorString = c.getString(R.string.errInvalidCountryPrefix)
                return false
            }
        } else {
            // A sim MUST begin with SIM prefix
            if (!fStartsWithSim) {
                errorString = c.getString(R.string.errSimsMustStartWithSIM)
                return false
            }
        }
        return true
    }

    override fun toProperties(so: SoapObject) {
        so.addProperty("Tailnumber", tailNumber)
        so.addProperty("AircraftID", aircraftID)
        so.addProperty("ModelID", modelID)
        so.addProperty("Version", version)
        so.addProperty("ICAO", modelICAO)
        so.addProperty("InstanceTypeID", instanceTypeID)
        so.addProperty("ModelCommonName", modelCommonName)
        so.addProperty("ModelDescription", modelDescription)
        so.addProperty("LastVOR", lastVOR)
        so.addProperty("LastAltimeter", lastAltimeter)
        so.addProperty("LastTransponder", lastTransponder)
        so.addProperty("LastELT", lastELT)
        so.addProperty("LastStatic", lastStatic)
        so.addProperty("LastAnnual", lastAnnual)
        so.addProperty("RegistrationDue", registrationDue)
        so.addProperty("Last100", last100)
        so.addProperty("LastOilChange", lastOil)
        so.addProperty("LastNewEngine", lastEngine)
        so.addProperty("MaintenanceNote", maintenanceNote)
        so.addProperty("RoleForPilot", roleForPilot)
        so.addProperty("CopyPICNameWithCrossfill", copyPICNameWithCrossfill)
        so.addProperty("HideFromSelection", hideFromSelection)
        so.addProperty("IsGlass", isGlass)
        so.addProperty("GlassUpgradeDate", glassUpgradeDate)
        so.addProperty("AvionicsTechnologyUpgrade", avionicsTechnologyUpgrade)
        so.addProperty("PublicNotes", publicNotes)
        so.addProperty("PrivateNotes", privateNotes)
        so.addProperty("DefaultImage", mDefaultImage)
        so.addProperty("Revision", revision)
    }

    public override fun fromProperties(so: SoapObject) {
        tailNumber = so.getProperty("TailNumber").toString()
        aircraftID = so.getProperty("AircraftID").toString().toInt()
        modelID = so.getProperty("ModelID").toString().toInt()
        instanceTypeID = so.getProperty("InstanceTypeID").toString().toInt()
        modelCommonName = so.getProperty("ModelCommonName").toString()
        modelDescription = so.getProperty("ModelDescription").toString()
        modelICAO = so.getProperty("ICAO").toString()
        version = so.getProperty("Version").toString().toInt()
        val szRevision = so.getPrimitivePropertySafelyAsString("Revision")
        revision = if (szRevision.isEmpty()) 0 else szRevision.toInt()
        lastVOR = readNullableDate(so, "LastVOR")
        lastAltimeter = readNullableDate(so, "LastAltimeter")
        lastTransponder = readNullableDate(so, "LastTransponder")
        lastELT = readNullableDate(so, "LastELT")
        lastStatic = readNullableDate(so, "LastStatic")
        lastAnnual = readNullableDate(so, "LastAnnual")
        registrationDue = readNullableDate(so, "RegistrationDue")
        last100 = so.getProperty("Last100").toString().toDouble()
        lastOil = so.getProperty("LastOilChange").toString().toDouble()
        lastEngine = so.getProperty("LastNewEngine").toString().toDouble()
        maintenanceNote = readNullableString(so, "MaintenanceNote")
        hideFromSelection =
            java.lang.Boolean.parseBoolean(so.getProperty("HideFromSelection").toString())
        copyPICNameWithCrossfill =
            java.lang.Boolean.parseBoolean(so.getProperty("CopyPICNameWithCrossfill").toString())
        roleForPilot = PilotRole.valueOf(so.getProperty("RoleForPilot").toString())
        isGlass = java.lang.Boolean.parseBoolean(so.getProperty("IsGlass").toString())
        glassUpgradeDate = readNullableDate(so, "GlassUpgradeDate")
        avionicsTechnologyUpgrade =
            AvionicsTechnologyType.valueOf(so.getProperty("AvionicsTechnologyUpgrade").toString())
        publicNotes = readNullableString(so, "PublicNotes")
        privateNotes = readNullableString(so, "PrivateNotes")
        val images = so.getProperty("AircraftImages") as SoapObject
        val cImages = images.propertyCount

        val rgImages : MutableList<MFBImageInfo> = mutableListOf()
        for (i in 0 until cImages) {
            val mfbii = MFBImageInfo(MFBImageInfo.PictureDestination.AircraftImage)
            mfbii.targetID = aircraftID.toLong()
            mfbii.fromProperties((images.getProperty(i) as SoapObject))
            rgImages.add(mfbii)
        }
        aircraftImages = rgImages.toTypedArray()

        mDefaultImage = readNullableString(so, "DefaultImage")
        val templates = so.getProperty("DefaultTemplates") as SoapObject
        val cVals = templates.propertyCount
        for (i in 0 until cVals) defaultTemplates.add(templates.getPropertyAsString(i).toInt())
    }

    // serialization methods
    override fun getPropertyCount(): Int {
        return AircraftProp.entries.size
    }

    override fun getProperty(i: Int): Any? {
        return when (AircraftProp.entries[i]) {
            AircraftProp.PIDTailNumber -> tailNumber
            AircraftProp.PIDAircratID -> aircraftID
            AircraftProp.PIDModelID -> modelID
            AircraftProp.PIDVersion -> version
            AircraftProp.PIDRevision -> revision
            AircraftProp.PIDICAO -> modelICAO
            AircraftProp.PIDInstanctTypeID -> instanceTypeID
            AircraftProp.PIDLastVOR -> lastVOR
            AircraftProp.PIDLastAltimeter -> lastAltimeter
            AircraftProp.PIDLastTransponder -> lastTransponder
            AircraftProp.PIDLastELT -> lastELT
            AircraftProp.PIDLastStatic -> lastStatic
            AircraftProp.PIDLastAnnual -> lastAnnual
            AircraftProp.PIDRegistrationDue -> registrationDue
            AircraftProp.PIDLast100 -> last100
            AircraftProp.PIDLastOil -> lastOil
            AircraftProp.PIDLastEngine -> lastEngine
            AircraftProp.PIDMaintNotes -> maintenanceNote
            AircraftProp.PIDHideFromSelection -> hideFromSelection
            AircraftProp.PIDCopyPICName -> copyPICNameWithCrossfill
            AircraftProp.PIDPilotRole -> roleForPilot.toString()
            AircraftProp.PIDPublicNotes -> publicNotes
            AircraftProp.PIDPrivateNotes -> privateNotes
            AircraftProp.PIDDefaultImage -> mDefaultImage
            AircraftProp.PIDDefaultTemplates -> Vector(listOf<Any>(*defaultTemplates.toTypedArray()))
            AircraftProp.PIDIsGlass -> isGlass
            AircraftProp.PIDGlassUpgradeDate -> glassUpgradeDate
            AircraftProp.PIDAvionicsTechnologyUpgrade -> avionicsTechnologyUpgrade.toString()
        }
    }

    override fun setProperty(i: Int, value: Any) {
        val ap = AircraftProp.entries[i]
        val sz = value.toString()
        when (ap) {
            AircraftProp.PIDTailNumber -> tailNumber = sz
            AircraftProp.PIDAircratID -> aircraftID = sz.toInt()
            AircraftProp.PIDModelID -> modelID = sz.toInt()
            AircraftProp.PIDICAO -> modelICAO = sz
            AircraftProp.PIDVersion -> version = sz.toInt()
            AircraftProp.PIDRevision -> revision = sz.toInt()
            AircraftProp.PIDInstanctTypeID -> instanceTypeID = sz.toInt()
            AircraftProp.PIDLastVOR -> lastVOR = IsoDate.stringToDate(sz, IsoDate.DATE)
            AircraftProp.PIDLastAltimeter -> lastAltimeter = IsoDate.stringToDate(sz, IsoDate.DATE)
            AircraftProp.PIDLastTransponder -> lastTransponder =
                IsoDate.stringToDate(sz, IsoDate.DATE)
            AircraftProp.PIDLastELT -> lastELT = IsoDate.stringToDate(sz, IsoDate.DATE)
            AircraftProp.PIDLastStatic -> lastStatic = IsoDate.stringToDate(sz, IsoDate.DATE)
            AircraftProp.PIDLastAnnual -> lastAnnual = IsoDate.stringToDate(sz, IsoDate.DATE)
            AircraftProp.PIDRegistrationDue -> registrationDue =
                IsoDate.stringToDate(sz, IsoDate.DATE)
            AircraftProp.PIDLast100 -> last100 = sz.toDouble()
            AircraftProp.PIDLastOil -> lastOil = sz.toDouble()
            AircraftProp.PIDLastEngine -> lastEngine = sz.toDouble()
            AircraftProp.PIDMaintNotes -> maintenanceNote = sz
            AircraftProp.PIDHideFromSelection -> hideFromSelection =
                java.lang.Boolean.parseBoolean(sz)
            AircraftProp.PIDCopyPICName -> copyPICNameWithCrossfill =
                java.lang.Boolean.parseBoolean(sz)
            AircraftProp.PIDPilotRole -> roleForPilot = PilotRole.valueOf(sz)
            AircraftProp.PIDPublicNotes -> publicNotes = sz
            AircraftProp.PIDPrivateNotes -> privateNotes = sz
            AircraftProp.PIDDefaultImage -> mDefaultImage = sz
            AircraftProp.PIDDefaultTemplates -> {
                defaultTemplates.clear()
                @Suppress("UNCHECKED_CAST")
                val rgVals = value as Array<Int>
                defaultTemplates.addAll(listOf(*rgVals))
            }
            AircraftProp.PIDIsGlass -> isGlass = java.lang.Boolean.parseBoolean(sz)
            AircraftProp.PIDGlassUpgradeDate -> glassUpgradeDate =
                IsoDate.stringToDate(sz, IsoDate.DATE)
            AircraftProp.PIDAvionicsTechnologyUpgrade -> avionicsTechnologyUpgrade =
                AvionicsTechnologyType.valueOf(sz)
        }
    }

    override fun getPropertyInfo(i: Int, h: Hashtable<*, *>?, pi: PropertyInfo) {
        when (AircraftProp.entries[i]) {
            AircraftProp.PIDTailNumber -> {
                pi.type = PropertyInfo.STRING_CLASS
                pi.name = "TailNumber"
            }
            AircraftProp.PIDAircratID -> {
                pi.type = PropertyInfo.INTEGER_CLASS
                pi.name = "AircraftID"
            }
            AircraftProp.PIDModelID -> {
                pi.type = PropertyInfo.INTEGER_CLASS
                pi.name = "ModelID"
            }
            AircraftProp.PIDVersion -> {
                pi.type = PropertyInfo.INTEGER_CLASS
                pi.name = "Version"
            }
            AircraftProp.PIDRevision -> {
                pi.type = PropertyInfo.INTEGER_CLASS
                pi.name = "Revision"
            }
            AircraftProp.PIDICAO -> {
                pi.type = PropertyInfo.STRING_CLASS
                pi.name = "ICAO"
            }
            AircraftProp.PIDInstanctTypeID -> {
                pi.type = PropertyInfo.INTEGER_CLASS
                pi.name = "InstanceTypeID"
            }
            AircraftProp.PIDLastVOR -> {
                pi.type = Date::class.java
                pi.name = "LastVOR"
            }
            AircraftProp.PIDLastAltimeter -> {
                pi.type = Date::class.java
                pi.name = "LastAltimeter"
            }
            AircraftProp.PIDLastTransponder -> {
                pi.type = Date::class.java
                pi.name = "LastTransponder"
            }
            AircraftProp.PIDLastELT -> {
                pi.type = Date::class.java
                pi.name = "LastELT"
            }
            AircraftProp.PIDLastStatic -> {
                pi.type = Date::class.java
                pi.name = "LastStatic"
            }
            AircraftProp.PIDLastAnnual -> {
                pi.type = Date::class.java
                pi.name = "LastAnnual"
            }
            AircraftProp.PIDRegistrationDue -> {
                pi.type = Date::class.java
                pi.name = "RegistrationDue"
            }
            AircraftProp.PIDLast100 -> {
                pi.type = Double::class.java
                pi.name = "Last100"
            }
            AircraftProp.PIDLastOil -> {
                pi.type = Double::class.java
                pi.name = "LastOilChange"
            }
            AircraftProp.PIDLastEngine -> {
                pi.type = Double::class.java
                pi.name = "LastNewEngine"
            }
            AircraftProp.PIDMaintNotes -> {
                pi.type = PropertyInfo.STRING_CLASS
                pi.name = "MaintenanceNote"
            }
            AircraftProp.PIDHideFromSelection -> {
                pi.type = PropertyInfo.BOOLEAN_CLASS
                pi.name = "HideFromSelection"
            }
            AircraftProp.PIDCopyPICName -> {
                pi.type = PropertyInfo.BOOLEAN_CLASS
                pi.name = "CopyPICNameWithCrossfill"
            }
            AircraftProp.PIDPilotRole -> {
                pi.type = PropertyInfo.STRING_CLASS
                pi.name = "RoleForPilot"
            }
            AircraftProp.PIDPublicNotes -> {
                pi.type = PropertyInfo.STRING_CLASS
                pi.name = "PublicNotes"
            }
            AircraftProp.PIDPrivateNotes -> {
                pi.type = PropertyInfo.STRING_CLASS
                pi.name = "PrivateNotes"
            }
            AircraftProp.PIDDefaultImage -> {
                pi.type = PropertyInfo.STRING_CLASS
                pi.name = "DefaultImage"
            }
            AircraftProp.PIDDefaultTemplates -> {
                pi.type = PropertyInfo.VECTOR_CLASS
                pi.name = "DefaultTemplates"
                pi.elementType = PropertyInfo()
                pi.elementType.type = PropertyInfo.INTEGER_CLASS
                pi.elementType.name = "Integer"
                pi.type = PropertyInfo.BOOLEAN_CLASS
                pi.name = "IsGlass"
            }
            AircraftProp.PIDIsGlass -> {
                pi.type = PropertyInfo.BOOLEAN_CLASS
                pi.name = "IsGlass"
            }
            AircraftProp.PIDGlassUpgradeDate -> {
                pi.type = Date::class.java
                pi.name = "GlassUpgradeDate"
            }
            AircraftProp.PIDAvionicsTechnologyUpgrade -> {
                pi.type = PropertyInfo.STRING_CLASS
                pi.name = "AvionicsTechnologyUpgrade"
            }
        }
    }

    override val defaultImage : MFBImageInfo? get() {
        if (hasImage()) {
            for (mfbii in aircraftImages!!) if (mfbii.thumbnailFile.compareTo(
                    mDefaultImage,
                    ignoreCase = true
                ) == 0
            ) return mfbii
            return aircraftImages!![0]
        }
        return null
    }

    // region Inspections
    fun nextVOR(): Date? {
        return if (isNullDate(lastVOR)) null else MFBUtil.addDays(
            MFBUtil.localDateFromUTCDate(
                lastVOR
            )!!, 30
        )
    }

    fun nextAnnual(): Date? {
        return if (isNullDate(lastAnnual)) null else MFBUtil.addCalendarMonths(
            MFBUtil.localDateFromUTCDate(
                lastAnnual
            )!!, 12
        )
    }

    fun nextELT(): Date? {
        return if (isNullDate(lastELT)) null else MFBUtil.addCalendarMonths(
            MFBUtil.localDateFromUTCDate(
                lastELT
            )!!, 12
        )
    }

    fun nextAltimeter(): Date? {
        return if (isNullDate(lastAltimeter)) null else MFBUtil.addCalendarMonths(
            MFBUtil.localDateFromUTCDate(
                lastAltimeter
            )!!, 24
        )
    }

    fun nextStatic(): Date? {
        return if (isNullDate(lastStatic)) null else MFBUtil.addCalendarMonths(
            MFBUtil.localDateFromUTCDate(
                lastStatic
            )!!, 24
        )
    }

    fun nextTransponder(): Date? {
        return if (isNullDate(lastTransponder)) null else MFBUtil.addCalendarMonths(
            MFBUtil.localDateFromUTCDate(
                lastTransponder
            )!!, 24
        )
    }

    fun nextDueLabel(dt: Date?, szFormat: String?, c: Context?): String {
        return if (dt == null) "" else String.format(
            Locale.getDefault(),
            szFormat!!,
            getDateFormat(c).format(dt)
        )
    } // endregion

    companion object {
        @Suppress("UNUSED")
        private const val serialVersionUID = 2L
        @JvmField
        val rgidInstanceTypes = intArrayOf(
            R.string.aircraftInstanceTypeReal,
            R.string.aircraftInstanceTypeSimUncertified,
            R.string.aircraftInstanceTypeSimLogAppchs,
            R.string.aircraftInstanceTypeSimLogAppchsLandings,
            R.string.aircraftInstanceTypeATD
        )
        private const val PREFIX_SIM = "SIM"
        private const val PREFIX_ANON = "#"
        @JvmStatic
        fun getAircraftById(idAircraft: Int, rgac: Array<Aircraft>?): Aircraft? {
            if (rgac != null) {
                for (ac in rgac) if (ac.aircraftID == idAircraft) return ac
            }
            return null
        }

        private val hashHighWaterHobbs = Hashtable<Int, Double>()
        private val hashHighWaterTach = Hashtable<Int, Double>()
        private val hashHighWaterFlightMeter = Hashtable<Int, Double>()
        fun updateHobbsForAircraft(hobbs: Double, idAircraft: Int) {
            if (!hashHighWaterHobbs.containsKey(idAircraft)) hashHighWaterHobbs[idAircraft] = hobbs
            val d = hashHighWaterHobbs[idAircraft]
            if (d != null) hashHighWaterHobbs[idAircraft] = max(d, hobbs)
        }

        fun updateTachForAircraft(tach: Double, idAircraft: Int) {
            if (!hashHighWaterTach.containsKey(idAircraft)) hashHighWaterTach[idAircraft] = tach
            val d = hashHighWaterTach[idAircraft]
            if (d != null) hashHighWaterTach[idAircraft] = max(d, tach)
        }

        fun updateFlightMeterForAircraft(meter: Double, idAircraft: Int) {
            if (!hashHighWaterFlightMeter.containsKey(idAircraft)) hashHighWaterFlightMeter[idAircraft] = meter
            val d = hashHighWaterFlightMeter[idAircraft]
            if (d != null) hashHighWaterFlightMeter[idAircraft] = max(d, meter)
        }

        @JvmStatic
        fun getHighWaterHobbsForAircraft(idAircraft: Int): Double {
            return hashHighWaterHobbs.getOrDefault(idAircraft, 0.0)
        }

        @JvmStatic
        fun getHighWaterTachForAircraft(idAircraft: Int): Double {
            return hashHighWaterTach.getOrDefault(idAircraft, 0.0)
        }

        @JvmStatic
        fun getHighWaterMeterForAircraft(idAircraft: Int) : Double {
            return hashHighWaterFlightMeter.getOrDefault(idAircraft, 0.0)
        }
    }
}