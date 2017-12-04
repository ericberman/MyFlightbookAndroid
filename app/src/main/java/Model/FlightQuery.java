/*
	MyFlightbook for Android - provides native access to MyFlightbook
	pilot's logbook
    Copyright (C) 2017 MyFlightbook, LLC

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
package Model;

import com.myflightbook.android.WebServices.UTCDate;

import org.kobjects.isodate.IsoDate;
import org.ksoap2.serialization.KvmSerializable;
import org.ksoap2.serialization.PropertyInfo;
import org.ksoap2.serialization.SoapObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Hashtable;
import java.util.Vector;

public class FlightQuery extends SoapableObject implements KvmSerializable, Serializable {

	private static final long serialVersionUID = 3L;

	public enum DateRanges {
		none,
		AllTime,
		YTD,
		Tailing6Months,
		Trailing12Months,
		ThisMonth,
		PrevMonth,
		PrevYear,
		Trailing30,
		Trailing90,
		Custom
		}
		
	public enum FlightDistance { AllFlights, LocalOnly, NonLocalOnly}
	
    public enum AircraftInstanceRestriction { AllAircraft, RealOnly, TrainingOnly }
    
    public enum EngineTypeRestriction { AllEngines, Piston, Jet, Turboprop, AnyTurbine, Electric }
	
	private enum FlightQueryProp {
		// Date properties.
		pidDateRange, pidDateMin, pidDateMax,
		// Text, aircraft, airport, make
		pidGeneralText, pidAircraftList, pidAirportList, pidMakeList, pidModelName, pidTypeNames, pidCatClassList, pidFlightDistance,
		// Flight attributes - 17
		pidIsPublic, pidHasNightLandings, pidHasFullStopLandings, pidHasLandings, pidHasApproaches,
		pidHasHolds, pidHaxXC, pidHasSimIMCTime, pidHasGroundSim, pidHasIMC, pidHasAnyInstrument, pidHasNight, 
		pidHasDual, pidHasCFI, pidHasSIC, pidHasPIC, pidHasTotalTime, pidHasTelemetry, pidHasImages, pidIsSigned,
		
		// Flight properties
		pidProperties,
		
		// Aircraft attributes
		pidIsComplex, pidHasFlaps, pidIsHighPerformance, pidIsConstantSpeedProp, pidMotorGlider, pidMultiEngineHeli,
		pidIsRetract, pidIsGlass, pidIsTailwheel, pidEngineType, pidInstanceType}

	// Whoo boy, lots of properties here.
	public DateRanges DateRange;
	public Date DateMin;
	public Date DateMax;
	
	public String GeneralText;
	public Aircraft[] AircraftList;
	public String[] AirportList;
	public MakeModel[] MakeList;
	public String ModelName;
	@SuppressWarnings("WeakerAccess")
	public String[] TypeNames;
	public CategoryClass[] CatClassList;
	public CustomPropertyType[] PropertyTypes;
	
	public FlightDistance Distance;

	// Flight attributes - 15 + GroundSim = 16
	public Boolean IsPublic = false;
	public Boolean HasNightLandings = false;
	public Boolean HasFullStopLandings = false;
	@SuppressWarnings("WeakerAccess")
	public Boolean HasLandings = false;
	public Boolean HasApproaches = false;
	public Boolean HasHolds = false;
	public Boolean HasXC = false;
	public Boolean HasSimIMCTime = false;
	@SuppressWarnings("WeakerAccess")
	public Boolean HasGroundSim = false;
	public Boolean HasIMC = false;
	@SuppressWarnings("WeakerAccess")
	public Boolean HasAnyInstrument = false;
	public Boolean HasNight = false;
	public Boolean HasDual = false;
	public Boolean HasCFI = false;
	public Boolean HasSIC = false;
	public Boolean HasPIC = false;
	@SuppressWarnings("WeakerAccess")
	public Boolean HasTotalTime = false;
	public Boolean HasTelemetry = false;
	public Boolean HasImages = false;
	public Boolean IsSigned = false;
	
	// Aircraft attributes - 9
	public Boolean IsComplex = false;
	public Boolean HasFlaps = false;
	public Boolean IsHighPerformance = false;
	public Boolean IsConstantSpeedProp = false;
	public Boolean IsRetract = false;
	public Boolean IsGlass = false;
	public Boolean IsTailwheel = false;
	public Boolean IsMotorglider = false;
	public Boolean IsMultiEngineHeli = false;
	
	public AircraftInstanceRestriction AircraftInstanceTypes = AircraftInstanceRestriction.AllAircraft;
	public EngineTypeRestriction EngineType = EngineTypeRestriction.AllEngines;
	
	private void setDatesForRange(DateRanges dr)
	{
        Calendar gc = UTCDate.UTCCalendar();
        
        Date dtNow = MFBUtil.UTCDateFromLocalDate(new Date());
        gc.setTime(dtNow);

        switch (dr)
		{
        case none:
        	break;
		case AllTime:
	        gc.set(1940, 0, 1);
	        DateMin = gc.getTime();
	        DateMax = dtNow;	
	        break;
		case YTD:
			gc.set(gc.get(Calendar.YEAR) , 0, 1);
			DateMin = gc.getTime();
			DateMax = dtNow;
			break;
		case Tailing6Months:
			DateMax = dtNow;
			gc.add(Calendar.MONTH, -6);
			DateMin = gc.getTime();
			break;
		case Trailing12Months:
			DateMax = dtNow;
			gc.add(Calendar.YEAR, -1);
			DateMin = gc.getTime();
			break;
		case Trailing30:
			DateMax = dtNow;
			gc.add(Calendar.DATE,  -30);
			DateMin = gc.getTime();
			break;
		case Trailing90:
			DateMax = dtNow;
			gc.add(Calendar.DATE,  -90);
			DateMin = gc.getTime();
			break;
		case ThisMonth:
			DateMax = dtNow;
			gc.set(gc.get(Calendar.YEAR), gc.get(Calendar.MONTH), 1);
			DateMin = gc.getTime();
			break;
		case PrevMonth:
			gc.set(gc.get(Calendar.YEAR), gc.get(Calendar.MONTH), 1);
			gc.add(Calendar.DAY_OF_YEAR, -1);
			DateMax = gc.getTime();
			gc.set(gc.get(Calendar.YEAR), gc.get(Calendar.MONTH), 1);
			DateMin = gc.getTime();
			break;
		case PrevYear:
			int year = gc.get(Calendar.YEAR) - 1;
			gc.set(year, 0, 1);
			DateMin = gc.getTime();
			gc.set(year, 11, 31);
			DateMax = gc.getTime();
		case Custom:
			break;
		}
   	}

	public void SetDateRange(DateRanges dr)
	{
		DateRange = dr;
		setDatesForRange(dr);
	}
	
    public void Init()
    {
        AircraftList = new Aircraft[0];
        AirportList = new String[0];
        SetDateRange(DateRanges.AllTime);
        GeneralText = ModelName = "";
        TypeNames = new String[0];
        MakeList = new MakeModel[0];
        CatClassList = new CategoryClass[0];
        PropertyTypes = new CustomPropertyType[0];
        Distance = FlightDistance.AllFlights;
        EngineType = EngineTypeRestriction.AllEngines;
        AircraftInstanceTypes = AircraftInstanceRestriction.AllAircraft;
        
    	IsPublic = 
    	IsSigned =
    	HasNightLandings = 
    	HasFullStopLandings = 
    	HasLandings =
    	HasApproaches = 
    	HasHolds = 
    	HasXC = 
    	HasSimIMCTime = 
    	HasGroundSim = 
    	HasIMC = 
    	HasAnyInstrument =
    	HasNight = 
    	HasDual = 
    	HasCFI = 
    	HasSIC = 
    	HasPIC = 
    	HasTotalTime =
    	HasTelemetry =
		HasImages =
    	IsComplex = 
    	HasFlaps = 
    	IsHighPerformance = 
    	IsConstantSpeedProp = 
    	IsRetract = 
    	IsGlass = 
    	IsTailwheel = 
    	IsMultiEngineHeli = 
    	IsMotorglider = false;
    }

    @SuppressWarnings("unused")
	public static String DateRangeToString(DateRanges dr)
    {
        switch (dr)
        {
            case AllTime:
                return "All Time";
            case PrevMonth:
                return "Previous Month";
            case PrevYear:
                return "Previous Year";
            case Tailing6Months:
                return "Trailing 6 Months";
            case ThisMonth:
                return "This Month";
            case Trailing12Months:
                return "Trailing 12 months";
            case YTD:
                return "Year-to-date";
            case Custom:
                return "(Custom)";
            default:
                return "";
        }
    }

    public Boolean HasAircraftCriteria()
    {
        return IsComplex || IsConstantSpeedProp || IsGlass || IsHighPerformance || IsRetract || IsTailwheel || HasFlaps || IsMultiEngineHeli || (EngineType != EngineTypeRestriction.AllEngines) || (AircraftInstanceTypes != AircraftInstanceRestriction.AllAircraft);
    }

    public Boolean HasFlightCriteria()
    {
        return IsPublic || HasApproaches || HasCFI || HasDual || HasFullStopLandings || HasLandings || HasHolds || HasIMC || HasAnyInstrument ||
            HasNight || HasNightLandings || HasPIC || HasSIC || HasTotalTime || HasSimIMCTime || HasTelemetry || HasImages || HasXC || IsSigned;
    }

    public Boolean HasCriteria()
    {
        return GeneralText.length() > 0 ||
            AirportList.length > 0 ||
            DateRange != DateRanges.AllTime ||
            AircraftList.length > 0 || 
            MakeList.length > 0 ||
            ModelName.length() > 0 || 
            TypeNames.length > 0 || 
            CatClassList.length > 0 ||
            PropertyTypes.length > 0 ||
            HasAircraftCriteria() || 
            HasFlightCriteria();
    }	

    @SuppressWarnings("unused")
	public Boolean HasSearchProperty(CustomPropertyType cpt)
    {
    	for (CustomPropertyType cpt2 : PropertyTypes)
    		if (cpt.idPropType == cpt2.idPropType)
    			return true;
    	return false;
    }
    
	@Override
	public void ToProperties(SoapObject so) {
	// Unused
	}

	@Override
	public void FromProperties(SoapObject so) {
		// Dates:
		this.DateRange = Enum.valueOf(FlightQuery.DateRanges.class, so.getPropertyAsString("DateRange"));

		this.DateMin = IsoDate.stringToDate(so.getPropertyAsString("DateMin"), IsoDate.DATE);
		this.DateMax = IsoDate.stringToDate(so.getPropertyAsString("DateMax"), IsoDate.DATE);

		// General Text:
		GeneralText = ReadNullableString(so, "GeneralText");
		
		// Flight Characteristics:
		this.HasFullStopLandings = Boolean.parseBoolean(so.getPropertySafelyAsString("HasFullStopLandings"));
		this.HasNightLandings = Boolean.parseBoolean(so.getPropertySafelyAsString("HasNightLandings"));
		this.HasLandings = Boolean.parseBoolean(so.getPropertySafelyAsString("HasLandings"));
		this.HasApproaches = Boolean.parseBoolean(so.getPropertySafelyAsString("HasApproaches"));
		this.HasHolds = Boolean.parseBoolean(so.getPropertySafelyAsString("HasHolds"));
		this.HasTelemetry = Boolean.parseBoolean(so.getPropertySafelyAsString("HasTelemetry"));
		this.HasImages = Boolean.parseBoolean(so.getPropertySafelyAsString("HasImages"));
		
		this.HasXC = Boolean.parseBoolean(so.getPropertySafelyAsString("HasXC"));
		this.HasSimIMCTime = Boolean.parseBoolean(so.getPropertySafelyAsString("HasSimIMCTime"));
		this.HasIMC = Boolean.parseBoolean(so.getPropertySafelyAsString("HasIMC"));
		this.HasAnyInstrument = Boolean.parseBoolean(so.getPropertySafelyAsString("HasAnyInstrument"));
		this.HasNight = Boolean.parseBoolean(so.getPropertySafelyAsString("HasNight"));
		this.IsPublic = Boolean.parseBoolean(so.getPropertySafelyAsString("IsPublic"));
		
		this.HasGroundSim = Boolean.parseBoolean(so.getPropertySafelyAsString("HasGroundSim"));
		this.HasDual = Boolean.parseBoolean(so.getPropertySafelyAsString("HasDual"));
		this.HasCFI = Boolean.parseBoolean(so.getPropertySafelyAsString("HasCFI"));
		this.HasSIC = Boolean.parseBoolean(so.getPropertySafelyAsString("HasSIC"));
		this.HasPIC = Boolean.parseBoolean(so.getPropertySafelyAsString("HasPIC"));
		this.HasTotalTime = Boolean.parseBoolean(so.getPropertySafelyAsString("HasTotalTime"));
		this.IsSigned = Boolean.parseBoolean(so.getPropertySafelyAsString("IsSigned"));

		// Airports
		SoapObject airports = (SoapObject) so.getProperty("AirportList");
		int cAirports = airports.getPropertyCount();
		AirportList = new String[cAirports];
		for (int i = 0; i < cAirports; i++)
			AirportList[i] = airports.getPropertyAsString(i);
		Distance = Enum.valueOf(FlightQuery.FlightDistance.class, so.getPropertyAsString("Distance"));

		// Aircraft
		SoapObject aircraft = (SoapObject) so.getProperty("AircraftList");
		int cAircraft = aircraft.getPropertyCount();
		AircraftList = new Aircraft[cAircraft];
		for (int i = 0; i < cAircraft; i++)
		{
			Aircraft ac = new Aircraft();
			ac.FromProperties((SoapObject) aircraft.getProperty(i));
			AircraftList[i] = ac;
		}
		
		// Models:
		ModelName = ReadNullableString(so, "ModelName");

		SoapObject typeNames = (SoapObject) so.getProperty("TypeNames");
		int cTypeNames = typeNames.getPropertyCount();
		ArrayList<String> alTypes = new ArrayList<>();
		for (int i = 0; i < cTypeNames; i++)
		{
			String s = ReadNullableString(typeNames, i);
			if (s.length() > 0)
				alTypes.add(s);
		}
		TypeNames = alTypes.toArray(new String[alTypes.size()]);
		
		SoapObject models = (SoapObject) so.getProperty("MakeList");
		int cmodels = models.getPropertyCount();
		MakeList = new MakeModel[cmodels];
		for (int i = 0; i < cmodels; i++)
		{
			MakeModel mm = new MakeModel();
			mm.FromProperties((SoapObject) models.getProperty(i));
			MakeList[i] = mm;
		}
		
		// Category/class list
		SoapObject catclass = (SoapObject) so.getProperty("CatClasses");
		int cCatClasses = catclass.getPropertyCount();
		CatClassList = new CategoryClass[cCatClasses];
		for (int i = 0; i < cCatClasses; i++)
		{
			CategoryClass cc = new CategoryClass();
			cc.FromProperties((SoapObject) catclass.getProperty(i));
			CatClassList[i] = cc;
		}
		
		// Aircraft attributes
		EngineType = Enum.valueOf(FlightQuery.EngineTypeRestriction.class, so.getPropertyAsString("EngineType"));
		AircraftInstanceTypes = Enum.valueOf(FlightQuery.AircraftInstanceRestriction.class, so.getPropertyAsString("AircraftInstanceTypes"));
	   	IsComplex = Boolean.parseBoolean(so.getPropertySafelyAsString("IsComplex"));
    	HasFlaps = Boolean.parseBoolean(so.getPropertySafelyAsString("HasFlaps"));
    	IsHighPerformance = Boolean.parseBoolean(so.getPropertySafelyAsString("IsHighPerformance"));
    	IsConstantSpeedProp = Boolean.parseBoolean(so.getPropertySafelyAsString("IsConstantSpeedProp"));
    	IsRetract = Boolean.parseBoolean(so.getPropertySafelyAsString("IsRetract"));
    	IsGlass = Boolean.parseBoolean(so.getPropertySafelyAsString("IsGlass"));
    	IsTailwheel = Boolean.parseBoolean(so.getPropertySafelyAsString("IsTailwheel"));
    	IsMotorglider = Boolean.parseBoolean(so.getPropertySafelyAsString("IsMotorglider"));
    	IsMultiEngineHeli = Boolean.parseBoolean(so.getPropertySafelyAsString("IsMultiEngineHeli"));
		
		// properties list
		SoapObject properties = (SoapObject) so.getProperty("PropertyTypes");
		int cProps = properties.getPropertyCount();
		PropertyTypes = new CustomPropertyType[cProps];
		for (int i = 0; i < cProps; i++)
		{
			CustomPropertyType cpt = new CustomPropertyType();
			cpt.FromProperties((SoapObject) properties.getProperty(i));
			PropertyTypes[i] = cpt;
		}
 	}

	public Object getProperty(int i) {
		FlightQueryProp fqp = FlightQueryProp.values()[i];
		switch (fqp) {
		// Date properties.
		case pidDateRange:
			return DateRange.toString();
		case pidDateMin:
			return DateMin;
		case pidDateMax:
			return DateMax;
		// Text: aircraft: airport: make
		case pidGeneralText:
			return GeneralText;
		case pidAircraftList:
			return new Vector<>(Arrays.asList(this.AircraftList));
		case pidAirportList:
			return new Vector<>(Arrays.asList(this.AirportList));
		case pidFlightDistance:
			return Distance.toString();
		case pidMakeList:
			return new Vector<>(Arrays.asList(this.MakeList));
		case pidModelName:
			return ModelName;
		case pidTypeNames:
			return new Vector<>(Arrays.asList(this.TypeNames));
		case pidCatClassList:
			return new Vector<>(Arrays.asList(this.CatClassList));
		case pidProperties:
			return new Vector<>(Arrays.asList(this.PropertyTypes));
		// Flight attributes - 14
		case pidIsPublic:
			return IsPublic;
		case pidIsSigned:
			return IsSigned;
		case pidHasNightLandings:
			return HasNightLandings;
		case pidHasFullStopLandings:
			return HasFullStopLandings;
		case pidHasLandings:
			return HasLandings;
		case pidHasApproaches:
			return HasApproaches;
		case pidHasHolds:
			return HasHolds;
		case pidHaxXC:
			return HasXC;
		case pidHasSimIMCTime:
			return HasSimIMCTime;
		case pidHasGroundSim:
			return HasGroundSim;
		case pidHasIMC:
			return HasIMC;
		case pidHasAnyInstrument:
			return HasAnyInstrument;
		case pidHasNight:
			return HasNight;
		case pidHasDual:
			return HasDual;
		case pidHasCFI:
			return HasCFI;
		case pidHasSIC:
			return HasSIC;
		case pidHasPIC:
			return HasPIC;
		case pidHasTotalTime:
			return HasTotalTime;
		case pidHasTelemetry:
			return HasTelemetry;
		case pidHasImages:
			return HasImages;
		// Aircraft attributes
		case pidIsComplex:
			return IsComplex;
		case pidHasFlaps:
			return HasFlaps;
		case pidIsHighPerformance:
			return IsHighPerformance;
		case pidIsConstantSpeedProp:
			return IsConstantSpeedProp;
		case pidIsRetract:
			return IsRetract;
		case pidIsGlass:
			return IsGlass;
		case pidIsTailwheel:
			return IsTailwheel;
		case pidMotorGlider:
			return IsMotorglider;
		case pidMultiEngineHeli:
			return IsMultiEngineHeli;
		case pidEngineType:
			return EngineType.toString();
		case pidInstanceType:
			return AircraftInstanceTypes.toString();
		default:
			break;
		}
		return null;
	}

	public int getPropertyCount() {
		return FlightQueryProp.values().length;
	}

	@SuppressWarnings("rawtypes")
	public void getPropertyInfo(int i, Hashtable arg1, PropertyInfo pi) {
		FlightQueryProp fqp = FlightQueryProp.values()[i];
		switch (fqp) {
		// Date properties.
		case pidDateRange:
			pi.type = PropertyInfo.STRING_CLASS;
			pi.name = "DateRange";
			break;
		case pidDateMin:
			pi.type = PropertyInfo.OBJECT_CLASS;
			pi.name = "DateMin";
			break;
		case pidDateMax:
			pi.type = PropertyInfo.OBJECT_CLASS;
			pi.name = "DateMax";
			break;
		// Text: aircraft: airport: make
		case pidGeneralText:
			pi.type = PropertyInfo.STRING_CLASS;
			pi.name = "GeneralText";
			break;
		case pidAircraftList:
			pi.type = PropertyInfo.VECTOR_CLASS;
			pi.name = "AircraftList";
			pi.elementType = new PropertyInfo();
			pi.elementType.type = PropertyInfo.OBJECT_CLASS;
			pi.elementType.name = "Aircraft";
			break;
		case pidAirportList:
			pi.type = PropertyInfo.VECTOR_CLASS;
			pi.name = "AirportList";
			pi.elementType = new PropertyInfo();
			pi.elementType.type = PropertyInfo.STRING_CLASS;
			pi.elementType.name = "string";
			break;
		case pidMakeList:
			pi.type = PropertyInfo.VECTOR_CLASS;
			pi.name = "MakeList";
			pi.elementType = new PropertyInfo();
			pi.elementType.type = PropertyInfo.OBJECT_CLASS;
			pi.elementType.name = "MakeModel";			
			break;
		case pidModelName:
			pi.type = PropertyInfo.STRING_CLASS;
			pi.name = "ModelName";
			break;
		case pidTypeNames:
			pi.type = PropertyInfo.VECTOR_CLASS;
			pi.name = "TypeNames";
			pi.elementType = new PropertyInfo();
			pi.elementType.type = PropertyInfo.STRING_CLASS;
			pi.elementType.name = "string";
			break;
		case pidCatClassList:
			pi.type = PropertyInfo.VECTOR_CLASS;
			pi.name = "CatClasses";
			pi.elementType = new PropertyInfo();
			pi.elementType.type = PropertyInfo.OBJECT_CLASS;
			pi.elementType.name = "CategoryClass";			
			break;
		case pidProperties:
			pi.type = PropertyInfo.VECTOR_CLASS;
			pi.name = "PropertyTypes";
			pi.elementType = new PropertyInfo();
			pi.elementType.type = PropertyInfo.OBJECT_CLASS;
			pi.elementType.name = "CustomPropertyType";			
			break;
		case pidFlightDistance:
			pi.type = PropertyInfo.STRING_CLASS;
			pi.name = "Distance";
			break;
		// Flight attributes
		case pidIsPublic:
			pi.type = PropertyInfo.BOOLEAN_CLASS;
			pi.name = "IsPublic";
			break;
		case pidIsSigned:
			pi.type = PropertyInfo.BOOLEAN_CLASS;
			pi.name = "IsSigned";
			break;
		case pidHasNightLandings:
			pi.type = PropertyInfo.BOOLEAN_CLASS;
			pi.name = "HasNightLandings";
			break;
		case pidHasFullStopLandings:
			pi.type = PropertyInfo.BOOLEAN_CLASS;
			pi.name = "HasFullStopLandings";
			break;
		case pidHasLandings:
			pi.type = PropertyInfo.BOOLEAN_CLASS;
			pi.name = "HasLandings";
			break;
		case pidHasApproaches:
			pi.type = PropertyInfo.BOOLEAN_CLASS;
			pi.name = "HasApproaches";
			break;
		case pidHasHolds:
			pi.type = PropertyInfo.BOOLEAN_CLASS;
			pi.name = "HasHolds";
			break;
		case pidHaxXC:
			pi.type = PropertyInfo.BOOLEAN_CLASS;
			pi.name = "HasXC";
			break;
		case pidHasSimIMCTime:
			pi.type = PropertyInfo.BOOLEAN_CLASS;
			pi.name = "HasSimIMCTime";
			break;
		case pidHasGroundSim:
			pi.type = PropertyInfo.BOOLEAN_CLASS;
			pi.name = "HasGroundSim";
			break;
		case pidHasIMC:
			pi.type = PropertyInfo.BOOLEAN_CLASS;
			pi.name = "HasIMC";
			break;
		case pidHasAnyInstrument:
			pi.type = PropertyInfo.BOOLEAN_CLASS;
			pi.name = "HasAnyInstrument";
			break;
		case pidHasNight:
			pi.type = PropertyInfo.BOOLEAN_CLASS;
			pi.name = "HasNight";
			break;
		case pidHasDual:
			pi.type = PropertyInfo.BOOLEAN_CLASS;
			pi.name = "HasDual";
			break;
		case pidHasCFI:
			pi.type = PropertyInfo.BOOLEAN_CLASS;
			pi.name = "HasCFI";
			break;
		case pidHasSIC:
			pi.type = PropertyInfo.BOOLEAN_CLASS;
			pi.name = "HasSIC";
			break;
		case pidHasPIC:
			pi.type = PropertyInfo.BOOLEAN_CLASS;
			pi.name = "HasPIC";
			break;
		case pidHasTotalTime:
			pi.type = PropertyInfo.BOOLEAN_CLASS;
			pi.name = "HasTotalTime";
			break;
		case pidHasTelemetry:
			pi.type = PropertyInfo.BOOLEAN_CLASS;
			pi.name = "HasTelemetry";
			break;
		case pidHasImages:
			pi.type = PropertyInfo.BOOLEAN_CLASS;
			pi.name = "HasImages";
			break;
		// Aircraft attributes
		case pidIsComplex:
			pi.type = PropertyInfo.BOOLEAN_CLASS;
			pi.name = "IsComplex";
			break;
		case pidHasFlaps:
			pi.type = PropertyInfo.BOOLEAN_CLASS;
			pi.name = "HasFlaps";
			break;
		case pidIsHighPerformance:
			pi.type = PropertyInfo.BOOLEAN_CLASS;
			pi.name = "IsHighPerformance";
			break;
		case pidIsConstantSpeedProp:
			pi.type = PropertyInfo.BOOLEAN_CLASS;
			pi.name = "IsConstantSpeedProp";
			break;
		case pidIsRetract:
			pi.type = PropertyInfo.BOOLEAN_CLASS;
			pi.name = "IsRetract";
			break;
		case pidIsGlass:
			pi.type = PropertyInfo.BOOLEAN_CLASS;
			pi.name = "IsGlass";
			break;
		case pidIsTailwheel:
			pi.type = PropertyInfo.BOOLEAN_CLASS;
			pi.name = "IsTailwheel";
			break;
		case pidMotorGlider:
			pi.type = PropertyInfo.BOOLEAN_CLASS;
			pi.name = "IsMotorglider";
			break;
		case pidMultiEngineHeli:
			pi.type = PropertyInfo.BOOLEAN_CLASS;
			pi.name = "IsMultiEngineHeli";
			break;
		case pidEngineType:
			pi.type = PropertyInfo.STRING_CLASS;
			pi.name = "EngineType";
			break;
		case pidInstanceType:
			pi.type = PropertyInfo.STRING_CLASS;
			pi.name = "AircraftInstanceTypes";
			break;
		default:
			break;
		}
	}

	public void setProperty(int i, Object value) {
	}

}
