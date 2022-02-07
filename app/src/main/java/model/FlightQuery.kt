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
import org.ksoap2.serialization.KvmSerializable
import org.ksoap2.serialization.SoapObject
import org.kobjects.isodate.IsoDate
import org.ksoap2.serialization.PropertyInfo
import java.io.Serializable
import java.util.*
import kotlin.collections.ArrayList

open class FlightQuery : SoapableObject(), KvmSerializable, Serializable {
    enum class DateRanges {
        none, AllTime, YTD, Tailing6Months, Trailing12Months, ThisMonth, PrevMonth, PrevYear, Trailing30, Trailing90, Custom
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
    var DateRange: DateRanges? = null
    var DateMin: Date? = null
    var DateMax: Date? = null
    var GeneralText: String? = null
    var AircraftList: Array<Aircraft> = arrayOf()
    var AirportList: Array<String> = arrayOf()
    var MakeList: Array<MakeModel> = arrayOf()
    var ModelName: String? = null
    var TypeNames: Array<String> = arrayOf()
    var CatClassList: Array<CategoryClass> = arrayOf()
    var PropertyTypes: Array<CustomPropertyType> = arrayOf()
    var Distance: FlightDistance? = null
    var FlightCharacteristicsConjunction = GroupConjunction.All
    var PropertiesConjunction = GroupConjunction.Any

    // Flight attributes - 15 + GroundSim = 16
    var IsPublic = false
    var HasNightLandings = false
    var HasFullStopLandings = false
    var HasLandings = false
    var HasApproaches = false
    var HasHolds = false
    var HasXC = false
    var HasSimIMCTime = false
    var HasGroundSim = false
    var HasIMC = false
    var HasAnyInstrument = false
    var HasNight = false
    var HasDual = false
    var HasCFI = false
    var HasSIC = false
    var HasPIC = false
    var HasTotalTime = false
    var HasTelemetry = false
    var HasImages = false
    var IsSigned = false

    // Aircraft attributes - 9
    var IsComplex = false
    var HasFlaps = false
    var IsHighPerformance = false
    var IsConstantSpeedProp = false
    var IsRetract = false
    var IsGlass = false
    var IsTAA = false
    var IsTailwheel = false
    var IsMotorglider = false
    var IsMultiEngineHeli = false
    var QueryName = ""
    var AircraftInstanceTypes = AircraftInstanceRestriction.AllAircraft
    var EngineType = EngineTypeRestriction.AllEngines
    private fun setDatesForRange(dr: DateRanges?) {
        val gc: Calendar = getUTCCalendar()
        val dtNow = MFBUtil.getUTCDateFromLocalDate(Date())
        gc.time = dtNow
        when (dr) {
            DateRanges.Custom, DateRanges.none -> {}
            DateRanges.AllTime -> {
                gc[1940, 0] = 1
                DateMin = gc.time
                DateMax = dtNow
            }
            DateRanges.YTD -> {
                gc[gc[Calendar.YEAR], 0] = 1
                DateMin = gc.time
                DateMax = dtNow
            }
            DateRanges.Tailing6Months -> {
                DateMax = dtNow
                gc.add(Calendar.MONTH, -6)
                DateMin = gc.time
            }
            DateRanges.Trailing12Months -> {
                DateMax = dtNow
                gc.add(Calendar.YEAR, -1)
                DateMin = gc.time
            }
            DateRanges.Trailing30 -> {
                DateMax = dtNow
                gc.add(Calendar.DATE, -30)
                DateMin = gc.time
            }
            DateRanges.Trailing90 -> {
                DateMax = dtNow
                gc.add(Calendar.DATE, -90)
                DateMin = gc.time
            }
            DateRanges.ThisMonth -> {
                DateMax = dtNow
                gc[gc[Calendar.YEAR], gc[Calendar.MONTH]] = 1
                DateMin = gc.time
            }
            DateRanges.PrevMonth -> {
                gc[gc[Calendar.YEAR], gc[Calendar.MONTH]] = 1
                gc.add(Calendar.DAY_OF_YEAR, -1)
                DateMax = gc.time
                gc[gc[Calendar.YEAR], gc[Calendar.MONTH]] = 1
                DateMin = gc.time
            }
            DateRanges.PrevYear -> {
                val year = gc[Calendar.YEAR] - 1
                gc[year, 0] = 1
                DateMin = gc.time
                gc[year, 11] = 31
                DateMax = gc.time
            }
        }
    }

    fun SetDateRange(dr: DateRanges?) {
        DateRange = dr
        setDatesForRange(dr)
    }

    fun Init() {
        AircraftList = arrayOf()
        AirportList = arrayOf()
        SetDateRange(DateRanges.AllTime)
        QueryName = ""
        ModelName = QueryName
        GeneralText = ModelName
        TypeNames = arrayOf()
        MakeList = arrayOf()
        CatClassList = arrayOf()
        PropertyTypes = arrayOf()
        Distance = FlightDistance.AllFlights
        EngineType = EngineTypeRestriction.AllEngines
        AircraftInstanceTypes = AircraftInstanceRestriction.AllAircraft
        PropertiesConjunction = GroupConjunction.Any
        FlightCharacteristicsConjunction = GroupConjunction.All
        IsMotorglider = false
        IsMultiEngineHeli = IsMotorglider
        IsTailwheel = IsMultiEngineHeli
        IsTAA = IsTailwheel
        IsGlass = IsTAA
        IsRetract = IsGlass
        IsConstantSpeedProp = IsRetract
        IsHighPerformance = IsConstantSpeedProp
        HasFlaps = IsHighPerformance
        IsComplex = HasFlaps
        HasImages = IsComplex
        HasTelemetry = HasImages
        HasTotalTime = HasTelemetry
        HasPIC = HasTotalTime
        HasSIC = HasPIC
        HasCFI = HasSIC
        HasDual = HasCFI
        HasNight = HasDual
        HasAnyInstrument = HasNight
        HasIMC = HasAnyInstrument
        HasGroundSim = HasIMC
        HasSimIMCTime = HasGroundSim
        HasXC = HasSimIMCTime
        HasHolds = HasXC
        HasApproaches = HasHolds
        HasLandings = HasApproaches
        HasFullStopLandings = HasLandings
        HasNightLandings = HasFullStopLandings
        IsSigned = HasNightLandings
        IsPublic = IsSigned
    }

    fun HasAircraftCriteria(): Boolean {
        return IsComplex || IsConstantSpeedProp || IsGlass || IsTAA || IsHighPerformance || IsRetract || IsTailwheel || HasFlaps || IsMultiEngineHeli || EngineType != EngineTypeRestriction.AllEngines || AircraftInstanceTypes != AircraftInstanceRestriction.AllAircraft
    }

    fun HasFlightCriteria(): Boolean {
        return IsPublic || HasApproaches || HasCFI || HasDual || HasFullStopLandings || HasLandings || HasHolds || HasIMC || HasAnyInstrument ||
                HasNight || HasNightLandings || HasPIC || HasSIC || HasTotalTime || HasSimIMCTime || HasTelemetry || HasImages || HasXC || IsSigned || HasGroundSim
    }

    fun HasCriteria(): Boolean {
        return GeneralText!!.isNotEmpty() || AirportList.isNotEmpty() || DateRange != DateRanges.AllTime || AircraftList.isNotEmpty() || MakeList.isNotEmpty() || ModelName!!.isNotEmpty() || TypeNames.isNotEmpty() || CatClassList.isNotEmpty() || PropertyTypes.isNotEmpty() ||
                HasAircraftCriteria() ||
                HasFlightCriteria()
    }

    fun HasSearchProperty(cpt: CustomPropertyType): Boolean {
        for (cpt2 in PropertyTypes) if (cpt.idPropType == cpt2.idPropType) return true
        return false
    }

    override fun toProperties(so: SoapObject) {
        // Unused
    }

    public override fun fromProperties(so: SoapObject) {
        // Dates:
        DateRange = DateRanges.valueOf(so.getPropertyAsString("DateRange"))
        DateMin = IsoDate.stringToDate(so.getPropertyAsString("DateMin"), IsoDate.DATE)
        DateMax = IsoDate.stringToDate(so.getPropertyAsString("DateMax"), IsoDate.DATE)

        // General Text:
        GeneralText = readNullableString(so, "GeneralText")

        // Flight Characteristics:
        HasFullStopLandings =
            java.lang.Boolean.parseBoolean(so.getPropertySafelyAsString("HasFullStopLandings"))
        HasNightLandings =
            java.lang.Boolean.parseBoolean(so.getPropertySafelyAsString("HasNightLandings"))
        HasLandings = java.lang.Boolean.parseBoolean(so.getPropertySafelyAsString("HasLandings"))
        HasApproaches =
            java.lang.Boolean.parseBoolean(so.getPropertySafelyAsString("HasApproaches"))
        HasHolds = java.lang.Boolean.parseBoolean(so.getPropertySafelyAsString("HasHolds"))
        HasTelemetry = java.lang.Boolean.parseBoolean(so.getPropertySafelyAsString("HasTelemetry"))
        HasImages = java.lang.Boolean.parseBoolean(so.getPropertySafelyAsString("HasImages"))
        HasXC = java.lang.Boolean.parseBoolean(so.getPropertySafelyAsString("HasXC"))
        HasSimIMCTime =
            java.lang.Boolean.parseBoolean(so.getPropertySafelyAsString("HasSimIMCTime"))
        HasIMC = java.lang.Boolean.parseBoolean(so.getPropertySafelyAsString("HasIMC"))
        HasAnyInstrument =
            java.lang.Boolean.parseBoolean(so.getPropertySafelyAsString("HasAnyInstrument"))
        HasNight = java.lang.Boolean.parseBoolean(so.getPropertySafelyAsString("HasNight"))
        IsPublic = java.lang.Boolean.parseBoolean(so.getPropertySafelyAsString("IsPublic"))
        HasGroundSim = java.lang.Boolean.parseBoolean(so.getPropertySafelyAsString("HasGroundSim"))
        HasDual = java.lang.Boolean.parseBoolean(so.getPropertySafelyAsString("HasDual"))
        HasCFI = java.lang.Boolean.parseBoolean(so.getPropertySafelyAsString("HasCFI"))
        HasSIC = java.lang.Boolean.parseBoolean(so.getPropertySafelyAsString("HasSIC"))
        HasPIC = java.lang.Boolean.parseBoolean(so.getPropertySafelyAsString("HasPIC"))
        HasTotalTime = java.lang.Boolean.parseBoolean(so.getPropertySafelyAsString("HasTotalTime"))
        IsSigned = java.lang.Boolean.parseBoolean(so.getPropertySafelyAsString("IsSigned"))
        val szFlightCharacteristicsConj =
            so.getPropertySafelyAsString("FlightCharacteristicsConjunction")
        if (szFlightCharacteristicsConj != null && szFlightCharacteristicsConj.isNotEmpty()) FlightCharacteristicsConjunction =
            GroupConjunction.valueOf(szFlightCharacteristicsConj)

        // Airports
        val airports = so.getProperty("AirportList") as SoapObject
        val cAirports = airports.propertyCount
        val rgAirports = ArrayList<String>()
        for (i in 0 until cAirports)
            rgAirports.add(airports.getPropertyAsString(i))
        AirportList = rgAirports.toTypedArray()

        Distance = FlightDistance.valueOf(so.getPropertyAsString("Distance"))

        // Aircraft
        val aircraft = so.getProperty("AircraftList") as SoapObject
        val cAircraft = aircraft.propertyCount
        val rgaircraft = ArrayList<Aircraft>()
        for (i in 0 until cAircraft) {
            val ac = Aircraft()
            ac.fromProperties((aircraft.getProperty(i) as SoapObject))
            rgaircraft.add(ac)
        }
        AircraftList = rgaircraft.toTypedArray()

        // Models:
        ModelName = readNullableString(so, "ModelName")
        val typeNames = so.getProperty("TypeNames") as SoapObject
        val cTypeNames = typeNames.propertyCount
        val alTypes = ArrayList<String>()
        for (i in 0 until cTypeNames) {
            alTypes.add(readNullableString(typeNames, i))
        }
        TypeNames = alTypes.toTypedArray()

        val models = so.getProperty("MakeList") as SoapObject
        val cmodels = models.propertyCount
        val rgMakes = ArrayList<MakeModel>()
        for (i in 0 until cmodels) {
            val mm = MakeModel()
            mm.fromProperties((models.getProperty(i) as SoapObject))
            rgMakes.add(mm)
        }
        MakeList = rgMakes.toTypedArray()

        // Category/class list
        val catclass = so.getProperty("CatClasses") as SoapObject
        val cCatClasses = catclass.propertyCount
        val rgcatclass = ArrayList<CategoryClass>()

        for (i in 0 until cCatClasses) {
            val cc = CategoryClass()
            cc.fromProperties((catclass.getProperty(i) as SoapObject))
            rgcatclass.add(cc)
        }
        CatClassList = rgcatclass.toTypedArray()

        // Aircraft attributes
        EngineType =
            EngineTypeRestriction.valueOf(so.getPropertyAsString("EngineType"))
        AircraftInstanceTypes = AircraftInstanceRestriction.valueOf(so.getPropertyAsString("AircraftInstanceTypes"))
        IsComplex = java.lang.Boolean.parseBoolean(so.getPropertySafelyAsString("IsComplex"))
        HasFlaps = java.lang.Boolean.parseBoolean(so.getPropertySafelyAsString("HasFlaps"))
        IsHighPerformance =
            java.lang.Boolean.parseBoolean(so.getPropertySafelyAsString("IsHighPerformance"))
        IsConstantSpeedProp =
            java.lang.Boolean.parseBoolean(so.getPropertySafelyAsString("IsConstantSpeedProp"))
        IsRetract = java.lang.Boolean.parseBoolean(so.getPropertySafelyAsString("IsRetract"))
        IsGlass = java.lang.Boolean.parseBoolean(so.getPropertySafelyAsString("IsGlass"))
        IsTAA =
            java.lang.Boolean.parseBoolean(so.getPropertySafelyAsString("IsTechnicallyAdvanced"))
        IsTailwheel = java.lang.Boolean.parseBoolean(so.getPropertySafelyAsString("IsTailwheel"))
        IsMotorglider =
            java.lang.Boolean.parseBoolean(so.getPropertySafelyAsString("IsMotorglider"))
        IsMultiEngineHeli =
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
        PropertyTypes = rgPropTypes.toTypedArray()
        val szPropsConj = so.getPropertySafelyAsString("PropertiesConjunction")
        if (szPropsConj != null && szPropsConj.isNotEmpty()) PropertiesConjunction = GroupConjunction.valueOf(szPropsConj)
        QueryName = so.getPropertySafelyAsString("QueryName")
    }

    override fun getProperty(i: Int): Any {
        when (FlightQueryProp.values()[i]) {
            FlightQueryProp.PIDDateRange -> return DateRange.toString()
            FlightQueryProp.PIDDateMin -> return DateMin!!
            FlightQueryProp.PIDDateMax -> return DateMax!!
            FlightQueryProp.PIDGeneralText -> return GeneralText!!
            FlightQueryProp.PIDAircraftList -> return Vector(listOf(*AircraftList))
            FlightQueryProp.PIDAirportList -> return Vector(listOf(*AirportList))
            FlightQueryProp.PIDFlightDistance -> return Distance.toString()
            FlightQueryProp.PIDMakeList -> return Vector(listOf(*MakeList))
            FlightQueryProp.PIDModelName -> return ModelName!!
            FlightQueryProp.PIDTypeNames -> return Vector(listOf(*TypeNames))
            FlightQueryProp.PIDCatClassList -> return Vector(listOf(*CatClassList))
            FlightQueryProp.PIDProperties -> return Vector(listOf(*PropertyTypes))
            FlightQueryProp.PIDIsPublic -> return IsPublic
            FlightQueryProp.PIDIsSigned -> return IsSigned
            FlightQueryProp.PIDHasNightLandings -> return HasNightLandings
            FlightQueryProp.PIDHasFullStopLandings -> return HasFullStopLandings
            FlightQueryProp.PIDHasLandings -> return HasLandings
            FlightQueryProp.PIDHasApproaches -> return HasApproaches
            FlightQueryProp.PIDHasHolds -> return HasHolds
            FlightQueryProp.PIDHaxXC -> return HasXC
            FlightQueryProp.PIDHasSimIMCTime -> return HasSimIMCTime
            FlightQueryProp.PIDHasGroundSim -> return HasGroundSim
            FlightQueryProp.PIDHasIMC -> return HasIMC
            FlightQueryProp.PIDHasAnyInstrument -> return HasAnyInstrument
            FlightQueryProp.PIDHasNight -> return HasNight
            FlightQueryProp.PIDHasDual -> return HasDual
            FlightQueryProp.PIDHasCFI -> return HasCFI
            FlightQueryProp.PIDHasSIC -> return HasSIC
            FlightQueryProp.PIDHasPIC -> return HasPIC
            FlightQueryProp.PIDHasTotalTime -> return HasTotalTime
            FlightQueryProp.PIDHasTelemetry -> return HasTelemetry
            FlightQueryProp.PIDHasImages -> return HasImages
            FlightQueryProp.PIDIsComplex -> return IsComplex
            FlightQueryProp.PIDHasFlaps -> return HasFlaps
            FlightQueryProp.PIDIsHighPerformance -> return IsHighPerformance
            FlightQueryProp.PIDIsConstantSpeedProp -> return IsConstantSpeedProp
            FlightQueryProp.PIDIsRetract -> return IsRetract
            FlightQueryProp.PIDIsGlass -> return IsGlass
            FlightQueryProp.PIDIsTAA -> return IsTAA
            FlightQueryProp.PIDIsTailwheel -> return IsTailwheel
            FlightQueryProp.PIDMotorGlider -> return IsMotorglider
            FlightQueryProp.PIDMultiEngineHeli -> return IsMultiEngineHeli
            FlightQueryProp.PIDEngineType -> return EngineType.toString()
            FlightQueryProp.PIDInstanceType -> return AircraftInstanceTypes.toString()
            FlightQueryProp.PIDQueryName -> return QueryName
            FlightQueryProp.PIDFlightCharacteristicsConjunction -> return FlightCharacteristicsConjunction.toString()
            FlightQueryProp.PIDPropertiesConjunction -> return PropertiesConjunction.toString()
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
        fun DateRangeToString(dr: DateRanges?): String {
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
        Init()
    }
}