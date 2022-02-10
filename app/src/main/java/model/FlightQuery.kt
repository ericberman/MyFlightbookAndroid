/*
	MyFlightbook for Android - provides native access to MyFlightbook
	pilot's logbook
    Copyright (C) 2017-2022 MyFlightbook, LLC

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

import com.myflightbook.android.webservices.UTCDate.getUTCCalendar
import org.kobjects.isodate.IsoDate
import org.ksoap2.serialization.KvmSerializable
import org.ksoap2.serialization.PropertyInfo
import org.ksoap2.serialization.SoapObject
import java.io.Serializable
import java.util.*

open class FlightQuery : SoapableObject(), KvmSerializable, Serializable {
    enum class DateRanges {
        None, AllTime, YTD, Tailing6Months, Trailing12Months, ThisMonth, PrevMonth, PrevYear, Trailing30, Trailing90, Custom
    }

    enum class GroupConjunction {
        Any, All, None
    }

    enum class FlightDistance {
        AllFlights, LocalOnly, NonLocalOnly
    }

    enum class AircraftInstanceRestriction {
        AllAircraft, RealOnly, TrainingOnly
    }

    enum class EngineTypeRestriction {
        AllEngines, Piston, Jet, Turboprop, AnyTurbine, Electric
    }

    private enum class FlightQueryProp {
        // Date properties.
        PIDDateRange, PIDDateMin, PIDDateMax,  // Text, aircraft, airport, make
        PIDGeneralText, PIDAircraftList, PIDAirportList, PIDMakeList, PIDModelName, PIDTypeNames, PIDCatClassList, PIDFlightDistance,  // Flight attributes - 17
        PIDIsPublic, PIDHasNightLandings, PIDHasFullStopLandings, PIDHasLandings, PIDHasApproaches, PIDHasHolds, PIDHaxXC, PIDHasSimIMCTime, PIDHasGroundSim, PIDHasIMC, PIDHasAnyInstrument, PIDHasNight, PIDHasDual, PIDHasCFI, PIDHasSIC, PIDHasPIC, PIDHasTotalTime, PIDHasTelemetry, PIDHasImages, PIDIsSigned, PIDFlightCharacteristicsConjunction,  // Flight properties
        PIDProperties, PIDPropertiesConjunction,  // Aircraft attributes
        PIDIsComplex, PIDHasFlaps, PIDIsHighPerformance, PIDIsConstantSpeedProp, PIDMotorGlider, PIDMultiEngineHeli, PIDIsRetract, PIDIsGlass, PIDIsTAA, PIDIsTailwheel, PIDEngineType, PIDInstanceType, PIDQueryName
    }

    // Whoo boy, lots of properties here.
    var dateRange: DateRanges? = null
    var dateMin: Date? = null
    var dateMax: Date? = null
    var generalText: String? = null
    var aircraftList: Array<Aircraft> = arrayOf()
    var airportList: Array<String> = arrayOf()
    var makeList: Array<MakeModel> = arrayOf()
    var modelName: String? = null
    private var typeNames: Array<String> = arrayOf()
    var catClassList: Array<CategoryClass> = arrayOf()
    var propertyTypes: Array<CustomPropertyType> = arrayOf()
    var distance: FlightDistance? = null
    var flightCharacteristicsConjunction = GroupConjunction.All
    var propertiesConjunction = GroupConjunction.Any

    // Flight attributes - 15 + GroundSim = 16
    var isPublic = false
    var hasNightLandings = false
    var hasFullStopLandings = false
    var hasLandings = false
    var hasApproaches = false
    var hasHolds = false
    var hasXC = false
    var hasSimIMCTime = false
    var hasGroundSim = false
    var hasIMC = false
    var hasAnyInstrument = false
    var hasNight = false
    var hasDual = false
    var hasCFI = false
    var hasSIC = false
    var hasPIC = false
    var hasTotalTime = false
    var hasTelemetry = false
    var hasImages = false
    var isSigned = false

    // Aircraft attributes - 9
    var isComplex = false
    var hasFlaps = false
    var isHighPerformance = false
    var isConstantSpeedProp = false
    var isRetract = false
    var isGlass = false
    var isTAA = false
    var isTailwheel = false
    var isMotorglider = false
    var isMultiEngineHeli = false
    var queryName = ""
    var aircraftInstanceTypes = AircraftInstanceRestriction.AllAircraft
    var engineType = EngineTypeRestriction.AllEngines
    private fun setDatesForRange(dr: DateRanges) {
        val gc: Calendar = getUTCCalendar()
        val dtNow = MFBUtil.getUTCDateFromLocalDate(Date())
        gc.time = dtNow
        when (dr) {
            DateRanges.Custom, DateRanges.None -> {}
            DateRanges.AllTime -> {
                gc[1900, 0] = 1
                dateMin = gc.time
                dateMax = dtNow
            }
            DateRanges.YTD -> {
                gc[gc[Calendar.YEAR], 0] = 1
                dateMin = gc.time
                dateMax = dtNow
            }
            DateRanges.Tailing6Months -> {
                dateMax = dtNow
                gc.add(Calendar.MONTH, -6)
                dateMin = gc.time
            }
            DateRanges.Trailing12Months -> {
                dateMax = dtNow
                gc.add(Calendar.YEAR, -1)
                dateMin = gc.time
            }
            DateRanges.Trailing30 -> {
                dateMax = dtNow
                gc.add(Calendar.DATE, -30)
                dateMin = gc.time
            }
            DateRanges.Trailing90 -> {
                dateMax = dtNow
                gc.add(Calendar.DATE, -90)
                dateMin = gc.time
            }
            DateRanges.ThisMonth -> {
                dateMax = dtNow
                gc[gc[Calendar.YEAR], gc[Calendar.MONTH]] = 1
                dateMin = gc.time
            }
            DateRanges.PrevMonth -> {
                gc[gc[Calendar.YEAR], gc[Calendar.MONTH]] = 1
                gc.add(Calendar.DAY_OF_YEAR, -1)
                dateMax = gc.time
                gc[gc[Calendar.YEAR], gc[Calendar.MONTH]] = 1
                dateMin = gc.time
            }
            DateRanges.PrevYear -> {
                val year = gc[Calendar.YEAR] - 1
                gc[year, 0] = 1
                dateMin = gc.time
                gc[year, 11] = 31
                dateMax = gc.time
            }
        }
    }

    fun setQueryDateRange(dr: DateRanges) {
        dateRange = dr
        setDatesForRange(dr)
    }

    fun init() {
        aircraftList = arrayOf()
        airportList = arrayOf()
        setQueryDateRange(DateRanges.AllTime)
        queryName = ""
        modelName = queryName
        generalText = modelName
        typeNames = arrayOf()
        makeList = arrayOf()
        catClassList = arrayOf()
        propertyTypes = arrayOf()
        distance = FlightDistance.AllFlights
        engineType = EngineTypeRestriction.AllEngines
        aircraftInstanceTypes = AircraftInstanceRestriction.AllAircraft
        propertiesConjunction = GroupConjunction.Any
        flightCharacteristicsConjunction = GroupConjunction.All
        isMotorglider = false
        isMultiEngineHeli = isMotorglider
        isTailwheel = isMultiEngineHeli
        isTAA = isTailwheel
        isGlass = isTAA
        isRetract = isGlass
        isConstantSpeedProp = isRetract
        isHighPerformance = isConstantSpeedProp
        hasFlaps = isHighPerformance
        isComplex = hasFlaps
        hasImages = isComplex
        hasTelemetry = hasImages
        hasTotalTime = hasTelemetry
        hasPIC = hasTotalTime
        hasSIC = hasPIC
        hasCFI = hasSIC
        hasDual = hasCFI
        hasNight = hasDual
        hasAnyInstrument = hasNight
        hasIMC = hasAnyInstrument
        hasGroundSim = hasIMC
        hasSimIMCTime = hasGroundSim
        hasXC = hasSimIMCTime
        hasHolds = hasXC
        hasApproaches = hasHolds
        hasLandings = hasApproaches
        hasFullStopLandings = hasLandings
        hasNightLandings = hasFullStopLandings
        isSigned = hasNightLandings
        isPublic = isSigned
    }

    fun hasAircraftCriteria(): Boolean {
        return isComplex || isConstantSpeedProp || isGlass || isTAA || isHighPerformance || isRetract || isTailwheel || hasFlaps || isMultiEngineHeli || engineType != EngineTypeRestriction.AllEngines || aircraftInstanceTypes != AircraftInstanceRestriction.AllAircraft
    }

    fun hasFlightCriteria(): Boolean {
        return isPublic || hasApproaches || hasCFI || hasDual || hasFullStopLandings || hasLandings || hasHolds || hasIMC || hasAnyInstrument ||
                hasNight || hasNightLandings || hasPIC || hasSIC || hasTotalTime || hasSimIMCTime || hasTelemetry || hasImages || hasXC || isSigned || hasGroundSim
    }

    fun hasCriteria(): Boolean {
        return generalText!!.isNotEmpty() || airportList.isNotEmpty() || dateRange != DateRanges.AllTime || aircraftList.isNotEmpty() || makeList.isNotEmpty() || modelName!!.isNotEmpty() || typeNames.isNotEmpty() || catClassList.isNotEmpty() || propertyTypes.isNotEmpty() ||
                hasAircraftCriteria() ||
                hasFlightCriteria()
    }

    fun hasSearchProperty(cpt: CustomPropertyType): Boolean {
        for (cpt2 in propertyTypes) if (cpt.idPropType == cpt2.idPropType) return true
        return false
    }

    override fun toProperties(so: SoapObject) {
        // Unused
    }

    public override fun fromProperties(so: SoapObject) {
        // Dates:
        dateRange = DateRanges.valueOf(so.getPropertyAsString("DateRange"))
        dateMin = IsoDate.stringToDate(so.getPropertyAsString("DateMin"), IsoDate.DATE)
        dateMax = IsoDate.stringToDate(so.getPropertyAsString("DateMax"), IsoDate.DATE)

        // General Text:
        generalText = readNullableString(so, "GeneralText")

        // Flight Characteristics:
        hasFullStopLandings =
            java.lang.Boolean.parseBoolean(so.getPropertySafelyAsString("HasFullStopLandings"))
        hasNightLandings =
            java.lang.Boolean.parseBoolean(so.getPropertySafelyAsString("HasNightLandings"))
        hasLandings = java.lang.Boolean.parseBoolean(so.getPropertySafelyAsString("HasLandings"))
        hasApproaches =
            java.lang.Boolean.parseBoolean(so.getPropertySafelyAsString("HasApproaches"))
        hasHolds = java.lang.Boolean.parseBoolean(so.getPropertySafelyAsString("HasHolds"))
        hasTelemetry = java.lang.Boolean.parseBoolean(so.getPropertySafelyAsString("HasTelemetry"))
        hasImages = java.lang.Boolean.parseBoolean(so.getPropertySafelyAsString("HasImages"))
        hasXC = java.lang.Boolean.parseBoolean(so.getPropertySafelyAsString("HasXC"))
        hasSimIMCTime =
            java.lang.Boolean.parseBoolean(so.getPropertySafelyAsString("HasSimIMCTime"))
        hasIMC = java.lang.Boolean.parseBoolean(so.getPropertySafelyAsString("HasIMC"))
        hasAnyInstrument =
            java.lang.Boolean.parseBoolean(so.getPropertySafelyAsString("HasAnyInstrument"))
        hasNight = java.lang.Boolean.parseBoolean(so.getPropertySafelyAsString("HasNight"))
        isPublic = java.lang.Boolean.parseBoolean(so.getPropertySafelyAsString("IsPublic"))
        hasGroundSim = java.lang.Boolean.parseBoolean(so.getPropertySafelyAsString("HasGroundSim"))
        hasDual = java.lang.Boolean.parseBoolean(so.getPropertySafelyAsString("HasDual"))
        hasCFI = java.lang.Boolean.parseBoolean(so.getPropertySafelyAsString("HasCFI"))
        hasSIC = java.lang.Boolean.parseBoolean(so.getPropertySafelyAsString("HasSIC"))
        hasPIC = java.lang.Boolean.parseBoolean(so.getPropertySafelyAsString("HasPIC"))
        hasTotalTime = java.lang.Boolean.parseBoolean(so.getPropertySafelyAsString("HasTotalTime"))
        isSigned = java.lang.Boolean.parseBoolean(so.getPropertySafelyAsString("IsSigned"))
        val szFlightCharacteristicsConj =
            so.getPropertySafelyAsString("FlightCharacteristicsConjunction")
        if (szFlightCharacteristicsConj != null && szFlightCharacteristicsConj.isNotEmpty()) flightCharacteristicsConjunction =
            GroupConjunction.valueOf(szFlightCharacteristicsConj)

        // Airports
        val airports = so.getProperty("AirportList") as SoapObject
        val cAirports = airports.propertyCount
        val rgAirports = ArrayList<String>()
        for (i in 0 until cAirports)
            rgAirports.add(airports.getPropertyAsString(i))
        airportList = rgAirports.toTypedArray()

        distance = FlightDistance.valueOf(so.getPropertyAsString("Distance"))

        // Aircraft
        val aircraft = so.getProperty("AircraftList") as SoapObject
        val cAircraft = aircraft.propertyCount
        val rgaircraft = ArrayList<Aircraft>()
        for (i in 0 until cAircraft) {
            val ac = Aircraft()
            ac.fromProperties((aircraft.getProperty(i) as SoapObject))
            rgaircraft.add(ac)
        }
        aircraftList = rgaircraft.toTypedArray()

        // Models:
        modelName = readNullableString(so, "ModelName")
        val typeNames = so.getProperty("TypeNames") as SoapObject
        val cTypeNames = typeNames.propertyCount
        val alTypes = ArrayList<String>()
        for (i in 0 until cTypeNames) {
            alTypes.add(readNullableString(typeNames, i))
        }
        this.typeNames = alTypes.toTypedArray()

        val models = so.getProperty("MakeList") as SoapObject
        val cmodels = models.propertyCount
        val rgMakes = ArrayList<MakeModel>()
        for (i in 0 until cmodels) {
            val mm = MakeModel()
            mm.fromProperties((models.getProperty(i) as SoapObject))
            rgMakes.add(mm)
        }
        makeList = rgMakes.toTypedArray()

        // Category/class list
        val catclass = so.getProperty("CatClasses") as SoapObject
        val cCatClasses = catclass.propertyCount
        val rgcatclass = ArrayList<CategoryClass>()

        for (i in 0 until cCatClasses) {
            val cc = CategoryClass()
            cc.fromProperties((catclass.getProperty(i) as SoapObject))
            rgcatclass.add(cc)
        }
        catClassList = rgcatclass.toTypedArray()

        // Aircraft attributes
        engineType =
            EngineTypeRestriction.valueOf(so.getPropertyAsString("EngineType"))
        aircraftInstanceTypes = AircraftInstanceRestriction.valueOf(so.getPropertyAsString("AircraftInstanceTypes"))
        isComplex = java.lang.Boolean.parseBoolean(so.getPropertySafelyAsString("IsComplex"))
        hasFlaps = java.lang.Boolean.parseBoolean(so.getPropertySafelyAsString("HasFlaps"))
        isHighPerformance =
            java.lang.Boolean.parseBoolean(so.getPropertySafelyAsString("IsHighPerformance"))
        isConstantSpeedProp =
            java.lang.Boolean.parseBoolean(so.getPropertySafelyAsString("IsConstantSpeedProp"))
        isRetract = java.lang.Boolean.parseBoolean(so.getPropertySafelyAsString("IsRetract"))
        isGlass = java.lang.Boolean.parseBoolean(so.getPropertySafelyAsString("IsGlass"))
        isTAA =
            java.lang.Boolean.parseBoolean(so.getPropertySafelyAsString("IsTechnicallyAdvanced"))
        isTailwheel = java.lang.Boolean.parseBoolean(so.getPropertySafelyAsString("IsTailwheel"))
        isMotorglider =
            java.lang.Boolean.parseBoolean(so.getPropertySafelyAsString("IsMotorglider"))
        isMultiEngineHeli =
            java.lang.Boolean.parseBoolean(so.getPropertySafelyAsString("IsMultiEngineHeli"))

        // properties list
        val properties = so.getProperty("PropertyTypes") as SoapObject
        val cProps = properties.propertyCount
        val rgPropTypes = ArrayList<CustomPropertyType>()
        for (i in 0 until cProps) {
            val cpt = CustomPropertyType()
            cpt.fromProperties((properties.getProperty(i) as SoapObject))
            rgPropTypes.add(cpt)
        }
        propertyTypes = rgPropTypes.toTypedArray()
        val szPropsConj = so.getPropertySafelyAsString("PropertiesConjunction")
        if (szPropsConj != null && szPropsConj.isNotEmpty()) propertiesConjunction = GroupConjunction.valueOf(szPropsConj)
        queryName = so.getPropertySafelyAsString("QueryName")
    }

    override fun getProperty(i: Int): Any {
        when (FlightQueryProp.values()[i]) {
            FlightQueryProp.PIDDateRange -> return dateRange.toString()
            FlightQueryProp.PIDDateMin -> return dateMin!!
            FlightQueryProp.PIDDateMax -> return dateMax!!
            FlightQueryProp.PIDGeneralText -> return generalText!!
            FlightQueryProp.PIDAircraftList -> return Vector(listOf(*aircraftList))
            FlightQueryProp.PIDAirportList -> return Vector(listOf(*airportList))
            FlightQueryProp.PIDFlightDistance -> return distance.toString()
            FlightQueryProp.PIDMakeList -> return Vector(listOf(*makeList))
            FlightQueryProp.PIDModelName -> return modelName!!
            FlightQueryProp.PIDTypeNames -> return Vector(listOf(*typeNames))
            FlightQueryProp.PIDCatClassList -> return Vector(listOf(*catClassList))
            FlightQueryProp.PIDProperties -> return Vector(listOf(*propertyTypes))
            FlightQueryProp.PIDIsPublic -> return isPublic
            FlightQueryProp.PIDIsSigned -> return isSigned
            FlightQueryProp.PIDHasNightLandings -> return hasNightLandings
            FlightQueryProp.PIDHasFullStopLandings -> return hasFullStopLandings
            FlightQueryProp.PIDHasLandings -> return hasLandings
            FlightQueryProp.PIDHasApproaches -> return hasApproaches
            FlightQueryProp.PIDHasHolds -> return hasHolds
            FlightQueryProp.PIDHaxXC -> return hasXC
            FlightQueryProp.PIDHasSimIMCTime -> return hasSimIMCTime
            FlightQueryProp.PIDHasGroundSim -> return hasGroundSim
            FlightQueryProp.PIDHasIMC -> return hasIMC
            FlightQueryProp.PIDHasAnyInstrument -> return hasAnyInstrument
            FlightQueryProp.PIDHasNight -> return hasNight
            FlightQueryProp.PIDHasDual -> return hasDual
            FlightQueryProp.PIDHasCFI -> return hasCFI
            FlightQueryProp.PIDHasSIC -> return hasSIC
            FlightQueryProp.PIDHasPIC -> return hasPIC
            FlightQueryProp.PIDHasTotalTime -> return hasTotalTime
            FlightQueryProp.PIDHasTelemetry -> return hasTelemetry
            FlightQueryProp.PIDHasImages -> return hasImages
            FlightQueryProp.PIDIsComplex -> return isComplex
            FlightQueryProp.PIDHasFlaps -> return hasFlaps
            FlightQueryProp.PIDIsHighPerformance -> return isHighPerformance
            FlightQueryProp.PIDIsConstantSpeedProp -> return isConstantSpeedProp
            FlightQueryProp.PIDIsRetract -> return isRetract
            FlightQueryProp.PIDIsGlass -> return isGlass
            FlightQueryProp.PIDIsTAA -> return isTAA
            FlightQueryProp.PIDIsTailwheel -> return isTailwheel
            FlightQueryProp.PIDMotorGlider -> return isMotorglider
            FlightQueryProp.PIDMultiEngineHeli -> return isMultiEngineHeli
            FlightQueryProp.PIDEngineType -> return engineType.toString()
            FlightQueryProp.PIDInstanceType -> return aircraftInstanceTypes.toString()
            FlightQueryProp.PIDQueryName -> return queryName
            FlightQueryProp.PIDFlightCharacteristicsConjunction -> return flightCharacteristicsConjunction.toString()
            FlightQueryProp.PIDPropertiesConjunction -> return propertiesConjunction.toString()
        }
    }

    override fun getPropertyCount(): Int {
        return FlightQueryProp.values().size
    }

    override fun getPropertyInfo(i: Int, arg1: Hashtable<*, *>?, pi: PropertyInfo) {
        when (FlightQueryProp.values()[i]) {
            FlightQueryProp.PIDDateRange -> {
                pi.type = PropertyInfo.STRING_CLASS
                pi.name = "DateRange"
            }
            FlightQueryProp.PIDDateMin -> {
                pi.type = PropertyInfo.OBJECT_CLASS
                pi.name = "DateMin"
            }
            FlightQueryProp.PIDDateMax -> {
                pi.type = PropertyInfo.OBJECT_CLASS
                pi.name = "DateMax"
            }
            FlightQueryProp.PIDGeneralText -> {
                pi.type = PropertyInfo.STRING_CLASS
                pi.name = "GeneralText"
            }
            FlightQueryProp.PIDAircraftList -> {
                pi.type = PropertyInfo.VECTOR_CLASS
                pi.name = "AircraftList"
                pi.elementType = PropertyInfo()
                pi.elementType.type = PropertyInfo.OBJECT_CLASS
                pi.elementType.name = "Aircraft"
            }
            FlightQueryProp.PIDAirportList -> {
                pi.type = PropertyInfo.VECTOR_CLASS
                pi.name = "AirportList"
                pi.elementType = PropertyInfo()
                pi.elementType.type = PropertyInfo.STRING_CLASS
                pi.elementType.name = "string"
            }
            FlightQueryProp.PIDMakeList -> {
                pi.type = PropertyInfo.VECTOR_CLASS
                pi.name = "MakeList"
                pi.elementType = PropertyInfo()
                pi.elementType.type = PropertyInfo.OBJECT_CLASS
                pi.elementType.name = "MakeModel"
            }
            FlightQueryProp.PIDModelName -> {
                pi.type = PropertyInfo.STRING_CLASS
                pi.name = "ModelName"
            }
            FlightQueryProp.PIDTypeNames -> {
                pi.type = PropertyInfo.VECTOR_CLASS
                pi.name = "TypeNames"
                pi.elementType = PropertyInfo()
                pi.elementType.type = PropertyInfo.STRING_CLASS
                pi.elementType.name = "string"
            }
            FlightQueryProp.PIDCatClassList -> {
                pi.type = PropertyInfo.VECTOR_CLASS
                pi.name = "CatClasses"
                pi.elementType = PropertyInfo()
                pi.elementType.type = PropertyInfo.OBJECT_CLASS
                pi.elementType.name = "CategoryClass"
            }
            FlightQueryProp.PIDProperties -> {
                pi.type = PropertyInfo.VECTOR_CLASS
                pi.name = "PropertyTypes"
                pi.elementType = PropertyInfo()
                pi.elementType.type = PropertyInfo.OBJECT_CLASS
                pi.elementType.name = "CustomPropertyType"
            }
            FlightQueryProp.PIDFlightDistance -> {
                pi.type = PropertyInfo.STRING_CLASS
                pi.name = "Distance"
            }
            FlightQueryProp.PIDIsPublic -> {
                pi.type = PropertyInfo.BOOLEAN_CLASS
                pi.name = "IsPublic"
            }
            FlightQueryProp.PIDIsSigned -> {
                pi.type = PropertyInfo.BOOLEAN_CLASS
                pi.name = "IsSigned"
            }
            FlightQueryProp.PIDHasNightLandings -> {
                pi.type = PropertyInfo.BOOLEAN_CLASS
                pi.name = "HasNightLandings"
            }
            FlightQueryProp.PIDHasFullStopLandings -> {
                pi.type = PropertyInfo.BOOLEAN_CLASS
                pi.name = "HasFullStopLandings"
            }
            FlightQueryProp.PIDHasLandings -> {
                pi.type = PropertyInfo.BOOLEAN_CLASS
                pi.name = "HasLandings"
            }
            FlightQueryProp.PIDHasApproaches -> {
                pi.type = PropertyInfo.BOOLEAN_CLASS
                pi.name = "HasApproaches"
            }
            FlightQueryProp.PIDHasHolds -> {
                pi.type = PropertyInfo.BOOLEAN_CLASS
                pi.name = "HasHolds"
            }
            FlightQueryProp.PIDHaxXC -> {
                pi.type = PropertyInfo.BOOLEAN_CLASS
                pi.name = "HasXC"
            }
            FlightQueryProp.PIDHasSimIMCTime -> {
                pi.type = PropertyInfo.BOOLEAN_CLASS
                pi.name = "HasSimIMCTime"
            }
            FlightQueryProp.PIDHasGroundSim -> {
                pi.type = PropertyInfo.BOOLEAN_CLASS
                pi.name = "HasGroundSim"
            }
            FlightQueryProp.PIDHasIMC -> {
                pi.type = PropertyInfo.BOOLEAN_CLASS
                pi.name = "HasIMC"
            }
            FlightQueryProp.PIDHasAnyInstrument -> {
                pi.type = PropertyInfo.BOOLEAN_CLASS
                pi.name = "HasAnyInstrument"
            }
            FlightQueryProp.PIDHasNight -> {
                pi.type = PropertyInfo.BOOLEAN_CLASS
                pi.name = "HasNight"
            }
            FlightQueryProp.PIDHasDual -> {
                pi.type = PropertyInfo.BOOLEAN_CLASS
                pi.name = "HasDual"
            }
            FlightQueryProp.PIDHasCFI -> {
                pi.type = PropertyInfo.BOOLEAN_CLASS
                pi.name = "HasCFI"
            }
            FlightQueryProp.PIDHasSIC -> {
                pi.type = PropertyInfo.BOOLEAN_CLASS
                pi.name = "HasSIC"
            }
            FlightQueryProp.PIDHasPIC -> {
                pi.type = PropertyInfo.BOOLEAN_CLASS
                pi.name = "HasPIC"
            }
            FlightQueryProp.PIDHasTotalTime -> {
                pi.type = PropertyInfo.BOOLEAN_CLASS
                pi.name = "HasTotalTime"
            }
            FlightQueryProp.PIDHasTelemetry -> {
                pi.type = PropertyInfo.BOOLEAN_CLASS
                pi.name = "HasTelemetry"
            }
            FlightQueryProp.PIDHasImages -> {
                pi.type = PropertyInfo.BOOLEAN_CLASS
                pi.name = "HasImages"
            }
            FlightQueryProp.PIDIsComplex -> {
                pi.type = PropertyInfo.BOOLEAN_CLASS
                pi.name = "IsComplex"
            }
            FlightQueryProp.PIDHasFlaps -> {
                pi.type = PropertyInfo.BOOLEAN_CLASS
                pi.name = "HasFlaps"
            }
            FlightQueryProp.PIDIsHighPerformance -> {
                pi.type = PropertyInfo.BOOLEAN_CLASS
                pi.name = "IsHighPerformance"
            }
            FlightQueryProp.PIDIsConstantSpeedProp -> {
                pi.type = PropertyInfo.BOOLEAN_CLASS
                pi.name = "IsConstantSpeedProp"
            }
            FlightQueryProp.PIDIsRetract -> {
                pi.type = PropertyInfo.BOOLEAN_CLASS
                pi.name = "IsRetract"
            }
            FlightQueryProp.PIDIsGlass -> {
                pi.type = PropertyInfo.BOOLEAN_CLASS
                pi.name = "IsGlass"
            }
            FlightQueryProp.PIDIsTAA -> {
                pi.type = PropertyInfo.BOOLEAN_CLASS
                pi.name = "IsTechnicallyAdvanced"
            }
            FlightQueryProp.PIDIsTailwheel -> {
                pi.type = PropertyInfo.BOOLEAN_CLASS
                pi.name = "IsTailwheel"
            }
            FlightQueryProp.PIDMotorGlider -> {
                pi.type = PropertyInfo.BOOLEAN_CLASS
                pi.name = "IsMotorglider"
            }
            FlightQueryProp.PIDMultiEngineHeli -> {
                pi.type = PropertyInfo.BOOLEAN_CLASS
                pi.name = "IsMultiEngineHeli"
            }
            FlightQueryProp.PIDEngineType -> {
                pi.type = PropertyInfo.STRING_CLASS
                pi.name = "EngineType"
            }
            FlightQueryProp.PIDInstanceType -> {
                pi.type = PropertyInfo.STRING_CLASS
                pi.name = "AircraftInstanceTypes"
            }
            FlightQueryProp.PIDPropertiesConjunction -> {
                pi.type = PropertyInfo.STRING_CLASS
                pi.name = "PropertiesConjunction"
            }
            FlightQueryProp.PIDFlightCharacteristicsConjunction -> {
                pi.type = PropertyInfo.STRING_CLASS
                pi.name = "FlightCharacteristicsConjunction"
            }
            FlightQueryProp.PIDQueryName -> {
                pi.type = PropertyInfo.STRING_CLASS
                pi.name = "QueryName"
            }
        }
    }

    override fun setProperty(i: Int, value: Any) {}

    companion object {
        private const val serialVersionUID = 4L
        fun dateRangeToString(dr: DateRanges?): String {
            return when (dr) {
                DateRanges.AllTime -> "All Time"
                DateRanges.PrevMonth -> "Previous Month"
                DateRanges.PrevYear -> "Previous Year"
                DateRanges.Tailing6Months -> "Trailing 6 Months"
                DateRanges.ThisMonth -> "This Month"
                DateRanges.Trailing12Months -> "Trailing 12 months"
                DateRanges.YTD -> "Year-to-date"
                DateRanges.Custom -> "(Custom)"
                else -> ""
            }
        }
    }

    init {
        init()
    }
}