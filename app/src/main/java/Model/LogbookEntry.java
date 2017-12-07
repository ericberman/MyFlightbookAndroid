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

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.text.Html;
import android.util.Log;

import com.myflightbook.android.ActRecentsWS;
import com.myflightbook.android.MFBMain;
import com.myflightbook.android.WebServices.AircraftSvc;
import com.myflightbook.android.WebServices.AuthToken;
import com.myflightbook.android.WebServices.CustomPropertyTypesSvc;
import com.myflightbook.android.WebServices.UTCDate;

import org.kobjects.isodate.IsoDate;
import org.ksoap2.serialization.KvmSerializable;
import org.ksoap2.serialization.PropertyInfo;
import org.ksoap2.serialization.SoapObject;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Hashtable;
import java.util.Locale;
import java.util.Vector;

import Model.MFBImageInfo.PictureDestination;

public class LogbookEntry extends SoapableObject implements KvmSerializable, LazyThumbnailLoader.ThumbnailedItem {

    public static final int ID_NEW_FLIGHT = -1;
    public static final int ID_PENDING_FLIGHT = -2;
    public static final int ID_QUEUED_FLIGHT_UNSUBMITTED = -3;

    private enum FlightProp {
        pidFlightId, pidUser, pidFlightDate, pidCatClassOverride,
        pidAircraft, pidcApproaches, pidcAppPrecision, pidcAppNP, pidcLandings, pidcFSNight,
        pidcFSDay, piddXC, piddNight, piddIMC, piddSimulatedIFR, piddGrnd, piddDual, piddPIC,
        piddCFI, piddSIC, piddTotal, pidfHold, pidszCommets, pidszRoute, pidfPublic, pidszErr,
        piddtFStart, piddtFEnd, piddtEStart, piddtEEnd, piddHobbsStart, piddHobbsEnd,
        pidszModelDisplay, pidszTailDisplay, pidszCatClassDisplay, pidfHasData, pidszData,
        pidProperties, pidExistingImages
    }

    public enum SigStatus {
        None, Valid, Invalid
    }

    public long idLocalDB = -1;
    public int idFlight = ID_NEW_FLIGHT;
    private String szUser = "";
    public Date dtFlight = new Date();
    @SuppressWarnings("WeakerAccess")
    public int idCatClassOverride = 0;
    public int idAircraft = -1;
    public int cApproaches = 0;
    @SuppressWarnings("WeakerAccess")
    public int cApproachPrecision = 0;
    @SuppressWarnings("WeakerAccess")
    public int cApproachNonPrecision = 0;
    public int cLandings = 0;
    public int cNightLandings = 0;
    public int cFullStopLandings = 0;
    public Double decXC = 0.0;
    public Double decNight = 0.0;
    public Double decIMC = 0.0;
    public Double decSimulatedIFR = 0.0;
    public Double decGrndSim = 0.0;
    public Double decDual = 0.0;
    public Double decPIC = 0.0;
    public Double decCFI = 0.0;
    public Double decSIC = 0.0;
    public Double decTotal = 0.0;
    public Boolean fHold = false;
    public String szComments = "";
    public String szRoute = "";
    public Boolean fPublic = false;
    public Date dtFlightStart = UTCDate.NullDate();
    public Date dtFlightEnd = UTCDate.NullDate();
    public Date dtEngineStart = UTCDate.NullDate();
    public Date dtEngineEnd = UTCDate.NullDate();
    public Double hobbsStart = 0.0;
    public Double hobbsEnd = 0.0;
    public String szFlightData = "";
    public FlightProperty[] rgCustomProperties;
    public MFBImageInfo[] rgFlightImages; // Only used on existing flights.

    // non-persisted values.
    public String szError = "";
    private String szModelDisplay = "";
    public String szTailNumDisplay = "";
    private String szCatClassDisplay = "";
    private Boolean fHasDataStream = false;
    public SigStatus signatureStatus = SigStatus.None;
    public String signatureComments = "";
    public Date signatureDate = null;
    public String signatureCFICert = "";
    public Date signatureCFIExpiration = null;
    public String signatureCFIName = "";
    public Boolean signatureHasDigitizedSig = false;

    private void Init() {
        this.szUser = AuthToken.m_szEmail;
        this.cApproaches = this.cApproachNonPrecision = this.cApproachPrecision =
                this.cFullStopLandings = this.cLandings = this.cNightLandings = 0;
        this.decCFI = this.decNight = this.decPIC = this.decSIC = this.decIMC =
                this.decSimulatedIFR = this.decTotal = this.decXC = this.decGrndSim =
                        this.decDual = this.hobbsEnd = this.hobbsStart = 0.0;
        this.dtEngineEnd = this.dtEngineStart = this.dtFlightEnd = this.dtFlightStart = UTCDate.NullDate();
        this.dtFlight = new Date();
        this.fHasDataStream = this.fHold = this.fPublic = false;
        this.idAircraft = -1;
        this.idCatClassOverride = 0;
        this.idFlight = ID_NEW_FLIGHT;
        this.szCatClassDisplay = this.szComments = this.szError =
                this.szFlightData = this.szModelDisplay = this.szRoute =
                        this.szTailNumDisplay = "";
        this.rgFlightImages = null;
    }

    // region constructors/initialization
    // Initialize a flight from the specified ID,
    // else save a copy in the database and return that id
    private void InitFromId(long id) {
        Init();
        if (id < 0)
            ToDB();
        else {
            this.idLocalDB = id;
            FromDB();
            // if this failed, save it and return the new dbID.
            if (this.idLocalDB < 0)
                ToDB();
        }
    }

    public LogbookEntry() {
        super();
        Init();
    }

    public LogbookEntry(long id) {
        super();
        InitFromId(id);
    }

    public LogbookEntry(int idAircraft, Boolean isPublic) {
        super();
        Init();
        this.idAircraft = idAircraft;
        this.fPublic = isPublic;
    }

    public LogbookEntry(SoapObject so) {
        super();
        Init();
        FromProperties(so);
    }
    // endregion

    public LogbookEntry Clone() {
        if (this.idFlight < 0)
            return null;

        LogbookEntry leNew = new LogbookEntry(this.idLocalDB);

        leNew.idFlight = ID_NEW_FLIGHT;
        leNew.rgCustomProperties = new FlightProperty[this.rgCustomProperties == null ? 0 : this.rgCustomProperties.length];
        if (this.rgCustomProperties != null) {
            for (int i = 0; i < this.rgCustomProperties.length; i++) {
                FlightProperty fp = new FlightProperty(this.rgCustomProperties[i]);
                fp.idFlight = ID_NEW_FLIGHT;
                fp.idProp = FlightProperty.ID_PROP_NEW;
                leNew.rgCustomProperties[i] = fp;
            }
        }
        leNew.rgFlightImages = new MFBImageInfo[0];

        leNew.dtFlight = new Date();
        leNew.dtEngineStart = UTCDate.NullDate();
        leNew.dtEngineEnd = UTCDate.NullDate();
        leNew.dtFlightStart = UTCDate.NullDate();
        leNew.dtFlightEnd = UTCDate.NullDate();
        leNew.hobbsEnd = leNew.hobbsStart = 0.0;
        leNew.szFlightData = "";
        leNew.rgFlightImages = new MFBImageInfo[0];

        for (FlightProperty fp : leNew.rgCustomProperties) {
            fp.idFlight = ID_NEW_FLIGHT;
            fp.idProp = FlightProperty.ID_PROP_NEW;
        }
        return leNew;
    }

    public LogbookEntry CloneAndReverse() {
        LogbookEntry leNew = Clone();
        if (leNew.szRoute != null) {
            String[] airports = Airport.SplitCodes(leNew.szRoute);
            StringBuilder sb = new StringBuilder();
            for (int i = airports.length - 1; i >= 0; i--) {
                sb.append(airports[i]).append(" ");
            }
            leNew.szRoute = sb.toString();
        }
        return leNew;
    }

    public void AddImageForflight(MFBImageInfo mfbii) {
        // save it to the DB for uploading...
        mfbii.setTargetID(this.idLocalDB);
        mfbii.toDB();
        Log.w(MFBConstants.LOG_TAG, String.format("Adding image %d for flight %d", mfbii.getID(), idLocalDB));

        // and append it to rgExisting.
        MFBImageInfo[] rgmfbNew = (rgFlightImages == null) ? new MFBImageInfo[1] : new MFBImageInfo[rgFlightImages.length + 1];
        int i = 0;
        if (rgFlightImages != null) {
            for (MFBImageInfo m : rgFlightImages) {
                rgmfbNew[i] = m;
                i++;
            }
        }

        // and append the newly added image
        rgmfbNew[i] = mfbii;
        this.rgFlightImages = rgmfbNew;
    }

    public void getImagesForFlight() {
        // don't update once this has been initialized - we can overwrite downloaded images.
        if (this.rgFlightImages == null)
            this.rgFlightImages = MFBImageInfo.getLocalImagesForId(this.idLocalDB, PictureDestination.FlightImage);
    }

    public void DeletePendingImagesForFlight() {
        MFBImageInfo mfbii = new MFBImageInfo(MFBImageInfo.PictureDestination.FlightImage);
        mfbii.DeletePendingImages(this.idLocalDB);
        this.rgFlightImages = null; // since it's invalid; we'll need to reload the flight from recents now.
    }

    public Boolean hasImages() {
        return this.rgFlightImages != null && this.rgFlightImages.length > 0;
    }

    public Boolean IsNewFlight() {
        return this.idFlight == LogbookEntry.ID_NEW_FLIGHT;
    }

    public Boolean IsPendingFlight() {
        return this.idFlight <= LogbookEntry.ID_PENDING_FLIGHT;
    }

    public Boolean IsQueuedFlight() { return this.idFlight == ID_QUEUED_FLIGHT_UNSUBMITTED; }

    public Boolean IsExistingFlight() {
        return this.idFlight > 0;
    }

    public Boolean isKnownEngineStart() {
        return !UTCDate.IsNullDate(dtEngineStart);
    }

    public Boolean isKnownEngineEnd() {
        return !UTCDate.IsNullDate(dtEngineEnd);
    }

    public Boolean isKnownFlightStart() {
        return !UTCDate.IsNullDate(dtFlightStart);
    }

    public Boolean isKnownFlightEnd() {
        return !UTCDate.IsNullDate(dtFlightEnd);
    }

    public Boolean isKnownEngineTime() {
        return isKnownEngineStart() && isKnownEngineEnd();
    }

    Boolean FlightInProgress() {
        return (isKnownEngineStart() || isKnownFlightStart()) && !isKnownEngineEnd();
    }

    public Boolean isKnownFlightTime() {
        return isKnownFlightStart() && isKnownFlightEnd();
    }

    public Boolean isEmptyFlight() {
        Boolean fHasAtMostTachStart = rgCustomProperties == null || rgCustomProperties.length == 0 ||
                (rgCustomProperties.length == 1 && rgCustomProperties[0].idPropType == CustomPropertyType.idPropTypeTachStart);

        return (idFlight == ID_NEW_FLIGHT &&
                szComments.length() == 0 &&
                szRoute.length() == 0 &&
                cApproaches == 0 &&
                cLandings == 0 &&
                cFullStopLandings == 0 &&
                cNightLandings == 0 &&
                !fHold &&
                hobbsEnd == 0 &&
                decNight == 0 &&
                decSimulatedIFR == 0 &&
                decIMC == 0 &&
                decXC == 0 &&
                decDual == 0 &&
                decGrndSim == 0 &&
                decCFI == 0 &&
                decSIC == 0 &
                        decPIC == 0 &&
                decTotal == 0 &&
                fHasAtMostTachStart
        );
    }

    // Custom Properties
    public void SyncProperties() {
        this.rgCustomProperties = FlightProperty.FromDB(this.idLocalDB);
        // In memory, the properties need to refer to the same ID as the calling flight
        // whereas in the DB, they refer to the LOCAL id of the flight.
        // Note that this is OK because it is ALWAYS a one-way process: from the DB into memory; we never
        // persist from memory back to the DB.
        for (FlightProperty fp : this.rgCustomProperties)
            fp.idFlight = this.idFlight;
    }

    void AddNightTakeoff() {
        // expand the list of all properties, even ones that aren't currently set
        FlightProperty[] rgfpAll = FlightProperty.CrossProduct(FlightProperty.FromDB(this.idLocalDB), CustomPropertyTypesSvc.getCachedPropertyTypes());

        // find the nighttime takeoff property
        for (FlightProperty fp : rgfpAll) {
            if (fp.idPropType == CustomPropertyType.idPropTypeNightTakeOff) {
                // increment it, distill the properties, and save 'em to the db.
                fp.intValue++;
                FlightProperty[] rgfpUpdated = FlightProperty.DistillList(rgfpAll);
                FlightProperty.RewritePropertiesForFlight(this.idLocalDB, rgfpUpdated);
                SyncProperties();
                return;
            }
        }
    }

    // serialization methods
    public int getPropertyCount() {
        return FlightProp.values().length;
    }

    public Object getProperty(int i) {
        FlightProp fp = FlightProp.values()[i];
        Object o = null;
        switch (fp) {
            case pidAircraft:
                return this.idAircraft;
            case pidcAppNP:
                return this.cApproachNonPrecision;
            case pidcAppPrecision:
                return this.cApproachPrecision;
            case pidcApproaches:
                return this.cApproaches;
            case pidCatClassOverride:
                return this.idCatClassOverride;
            case pidcFSDay:
                return this.cFullStopLandings;
            case pidcFSNight:
                return this.cNightLandings;
            case pidcLandings:
                return this.cLandings;
            case piddCFI:
                return this.decCFI;
            case piddDual:
                return this.decDual;
            case piddGrnd:
                return this.decGrndSim;
            case piddHobbsEnd:
                return this.hobbsEnd;
            case piddHobbsStart:
                return this.hobbsStart;
            case piddIMC:
                return this.decIMC;
            case piddNight:
                return this.decNight;
            case piddPIC:
                return this.decPIC;
            case piddSIC:
                return this.decSIC;
            case piddSimulatedIFR:
                return this.decSimulatedIFR;
            case piddTotal:
                return this.decTotal;
            case piddtEEnd:
                return this.dtEngineEnd;
            case piddtEStart:
                return this.dtEngineStart;
            case piddtFEnd:
                return this.dtFlightEnd;
            case piddtFStart:
                return this.dtFlightStart;
            case piddXC:
                return this.decXC;
            case pidfHasData:
                return this.fHasDataStream;
            case pidfHold:
                return this.fHold;
            case pidFlightDate:
                return this.dtFlight;
            case pidFlightId:
                return this.idFlight;
            case pidfPublic:
                return this.fPublic;
            case pidszCatClassDisplay:
                return this.szCatClassDisplay;
            case pidszCommets:
                return this.szComments;
            case pidszData:
                return this.szFlightData;
            case pidszErr:
                return this.szError;
            case pidszModelDisplay:
                return this.szModelDisplay;
            case pidszRoute:
                return this.szRoute;
            case pidszTailDisplay:
                return this.szTailNumDisplay;
            case pidUser:
                return this.szUser;
            case pidProperties:
                // return this.rgCustomProperties;
                return new Vector<>(Arrays.asList(this.rgCustomProperties));
            case pidExistingImages:
                return new Vector<>(Arrays.asList(this.rgFlightImages));
            default:
                break;
        }
        return o;
    }

    public void setProperty(int i, Object value) {
        FlightProp fp = FlightProp.values()[i];
        String sz = value.toString();
        switch (fp) {
            case pidAircraft:
                this.idAircraft = Integer.parseInt(sz);
                break;
            case pidcAppNP:
                this.cApproachNonPrecision = Integer.parseInt(sz);
                break;
            case pidcAppPrecision:
                this.cApproachPrecision = Integer.parseInt(sz);
                break;
            case pidcApproaches:
                this.cApproaches = Integer.parseInt(sz);
                break;
            case pidCatClassOverride:
                this.idCatClassOverride = Integer.parseInt(sz);
                break;
            case pidcFSDay:
                this.cFullStopLandings = Integer.parseInt(sz);
                break;
            case pidcFSNight:
                this.cNightLandings = Integer.parseInt(sz);
                break;
            case pidcLandings:
                this.cLandings = Integer.parseInt(sz);
                break;
            case piddCFI:
                this.decCFI = Double.parseDouble(sz);
                break;
            case piddDual:
                this.decDual = Double.parseDouble(sz);
                break;
            case piddGrnd:
                this.decGrndSim = Double.parseDouble(sz);
                break;
            case piddHobbsEnd:
                this.hobbsEnd = Double.parseDouble(sz);
                break;
            case piddHobbsStart:
                this.hobbsStart = Double.parseDouble(sz);
                break;
            case piddIMC:
                this.decIMC = Double.parseDouble(sz);
                break;
            case piddNight:
                this.decNight = Double.parseDouble(sz);
                break;
            case piddPIC:
                this.decPIC = Double.parseDouble(sz);
                break;
            case piddSIC:
                this.decSIC = Double.parseDouble(sz);
                break;
            case piddSimulatedIFR:
                this.decSimulatedIFR = Double.parseDouble(sz);
                break;
            case piddTotal:
                this.decTotal = Double.parseDouble(sz);
                break;
            case piddtEEnd:
                this.dtEngineEnd = IsoDate.stringToDate(sz, IsoDate.DATE_TIME);
                break;
            case piddtEStart:
                this.dtEngineStart = IsoDate.stringToDate(sz, IsoDate.DATE_TIME);
                break;
            case piddtFEnd:
                this.dtFlightEnd = IsoDate.stringToDate(sz, IsoDate.DATE_TIME);
                break;
            case piddtFStart:
                this.dtFlightStart = IsoDate.stringToDate(sz, IsoDate.DATE_TIME);
                break;
            case piddXC:
                this.decXC = Double.parseDouble(sz);
                break;
            case pidfHasData:
                this.fHasDataStream = Boolean.parseBoolean(sz);
                break;
            case pidfHold:
                this.fHold = Boolean.parseBoolean(sz);
                break;
            case pidFlightDate:
                this.dtFlight = IsoDate.stringToDate(sz, IsoDate.DATE);
                break;
            case pidFlightId:
                this.idFlight = Integer.parseInt(sz);
                break;
            case pidfPublic:
                this.fPublic = Boolean.parseBoolean(sz);
                break;
            case pidszCatClassDisplay:
                this.szCatClassDisplay = sz;
                break;
            case pidszCommets:
                this.szComments = sz;
                break;
            case pidszData:
                this.szFlightData = sz;
                break;
            case pidszErr:
                this.szError = sz;
                break;
            case pidszModelDisplay:
                this.szModelDisplay = sz;
                break;
            case pidszRoute:
                this.szRoute = sz;
                break;
            case pidszTailDisplay:
                this.szTailNumDisplay = sz;
                break;
            case pidUser:
                this.szUser = sz;
                break;
            case pidProperties:
                break;
            case pidExistingImages:
                break;
            default:
                break;
        }
    }

    @SuppressWarnings("rawtypes")
    public void getPropertyInfo(int i, Hashtable h, PropertyInfo pi) {
        FlightProp fp = FlightProp.values()[i];
        switch (fp) {
            case pidAircraft:
                pi.type = PropertyInfo.INTEGER_CLASS;
                pi.name = "AircraftID";
                break;
            case pidcAppNP:
                pi.type = PropertyInfo.INTEGER_CLASS;
                pi.name = "NonPrecisionApproaches";
                break;
            case pidcAppPrecision:
                pi.type = PropertyInfo.INTEGER_CLASS;
                pi.name = "PrecisionApproaches";
                break;
            case pidcApproaches:
                pi.type = PropertyInfo.INTEGER_CLASS;
                pi.name = "Approaches";
                break;
            case pidCatClassOverride:
                pi.type = PropertyInfo.INTEGER_CLASS;
                pi.name = "CatClassOverride";
                break;
            case pidcFSDay:
                pi.type = PropertyInfo.INTEGER_CLASS;
                pi.name = "FullStopLandings";
                break;
            case pidcFSNight:
                pi.type = PropertyInfo.INTEGER_CLASS;
                pi.name = "NightLandings";
                break;
            case pidcLandings:
                pi.type = PropertyInfo.INTEGER_CLASS;
                pi.name = "Landings";
                break;
            case piddCFI:
                pi.type = PropertyInfo.OBJECT_CLASS;
                pi.name = "CFI";
                break;
            case piddDual:
                pi.type = PropertyInfo.OBJECT_CLASS;
                pi.name = "Dual";
                break;
            case piddGrnd:
                pi.type = PropertyInfo.OBJECT_CLASS;
                pi.name = "GroundSim";
                break;
            case piddHobbsEnd:
                pi.type = PropertyInfo.OBJECT_CLASS;
                pi.name = "HobbsEnd";
                break;
            case piddHobbsStart:
                pi.type = PropertyInfo.OBJECT_CLASS;
                pi.name = "HobbsStart";
                break;
            case piddIMC:
                pi.type = PropertyInfo.OBJECT_CLASS;
                pi.name = "IMC";
                break;
            case piddNight:
                pi.type = PropertyInfo.OBJECT_CLASS;
                pi.name = "Nighttime";
                break;
            case piddPIC:
                pi.type = PropertyInfo.OBJECT_CLASS;
                pi.name = "PIC";
                break;
            case piddSIC:
                pi.type = PropertyInfo.OBJECT_CLASS;
                pi.name = "SIC";
                break;
            case piddSimulatedIFR:
                pi.type = PropertyInfo.OBJECT_CLASS;
                pi.name = "SimulatedIFR";
                break;
            case piddTotal:
                pi.type = PropertyInfo.OBJECT_CLASS;
                pi.name = "TotalFlightTime";
                break;
            case piddtEEnd:
                pi.type = PropertyInfo.OBJECT_CLASS;
                pi.name = "EngineEnd";
                break;
            case piddtEStart:
                pi.type = PropertyInfo.OBJECT_CLASS;
                pi.name = "EngineStart";
                break;
            case piddtFEnd:
                pi.type = PropertyInfo.OBJECT_CLASS;
                pi.name = "FlightEnd";
                break;
            case piddtFStart:
                pi.type = PropertyInfo.OBJECT_CLASS;
                pi.name = "FlightStart";
                break;
            case piddXC:
                pi.type = PropertyInfo.OBJECT_CLASS;
                pi.name = "CrossCountry";
                break;
            case pidfHasData:
                pi.type = PropertyInfo.BOOLEAN_CLASS;
                pi.name = "FHasData";
                break;
            case pidfHold:
                pi.type = PropertyInfo.BOOLEAN_CLASS;
                pi.name = "fHoldingProcedures";
                break;
            case pidFlightDate:
                pi.type = PropertyInfo.OBJECT_CLASS;
                pi.name = "Date";
                break;
            case pidFlightId:
                pi.type = PropertyInfo.INTEGER_CLASS;
                pi.name = "FlightID";
                break;
            case pidfPublic:
                pi.type = PropertyInfo.BOOLEAN_CLASS;
                pi.name = "fIsPublic";
                break;
            case pidszCatClassDisplay:
                pi.type = PropertyInfo.STRING_CLASS;
                pi.name = "CatClassDisplay";
                break;
            case pidszCommets:
                pi.type = PropertyInfo.STRING_CLASS;
                pi.name = "Comment";
                break;
            case pidszData:
                pi.type = PropertyInfo.STRING_CLASS;
                pi.name = "FlightData";
                break;
            case pidszErr:
                pi.type = PropertyInfo.STRING_CLASS;
                pi.name = "Error";
                break;
            case pidszModelDisplay:
                pi.type = PropertyInfo.STRING_CLASS;
                pi.name = "ModelDisplay";
                break;
            case pidszRoute:
                pi.type = PropertyInfo.STRING_CLASS;
                pi.name = "Route";
                break;
            case pidszTailDisplay:
                pi.type = PropertyInfo.STRING_CLASS;
                pi.name = "TailNumDisplay";
                break;
            case pidUser:
                pi.type = PropertyInfo.STRING_CLASS;
                pi.name = "User";
                break;
            case pidProperties:
                pi.type = PropertyInfo.VECTOR_CLASS;
                pi.name = "CustomProperties";
                pi.elementType = new PropertyInfo();
                pi.elementType.type = PropertyInfo.OBJECT_CLASS;
                pi.elementType.name = "CustomFlightProperty";
                break;
            case pidExistingImages:
                pi.type = PropertyInfo.VECTOR_CLASS;
                pi.name = "FlightImages";
                pi.elementType = new PropertyInfo();
                pi.elementType.type = PropertyInfo.OBJECT_CLASS;
                pi.elementType.name = "MFBImageInfo";
                break;
            default:
                break;
        }
    }

    public void ToProperties(SoapObject so) {
        so.addProperty("User", szUser);
        so.addProperty("AircraftID", idAircraft);
        so.addProperty("CatClassOverride", idCatClassOverride);
        so.addProperty("NightLandings", cNightLandings);
        so.addProperty("FullStopLandings", cFullStopLandings);
        so.addProperty("Landings", cLandings);
        so.addProperty("Approaches", cApproaches);
        so.addProperty("cApproachPrecision", cApproachPrecision);
        so.addProperty("cApproachNonPrecision", cApproachNonPrecision);
        AddDouble(so, "CrossCountry", decXC);
        AddDouble(so, "Nighttime", decNight);
        AddDouble(so, "IMC", decIMC);
        AddDouble(so, "SimulatedIFR", decSimulatedIFR);
        AddDouble(so, "GroundSim", decGrndSim);
        AddDouble(so, "Dual", decDual);
        AddDouble(so, "CFI", decCFI);
        AddDouble(so, "PIC", decPIC);
        AddDouble(so, "SIC", decSIC);
        AddDouble(so, "TotalFlightTime", decTotal);
        so.addProperty("fHoldingProcedures", fHold);
        so.addProperty("Route", szRoute);
        so.addProperty("Comment", szComments);
        so.addProperty("fIsPublic", fPublic);
        so.addProperty("Date", IsoDate.dateToString(dtFlight, IsoDate.DATE));
        so.addProperty("FlightID", idFlight);

        AddDouble(so, "HobbsStart", hobbsStart);
        AddDouble(so, "HobbsEnd", hobbsEnd);
        AddNullableDate(so, "FlightStart", dtFlightStart);
        AddNullableDate(so, "FlightEnd", dtFlightEnd);
        AddNullableDate(so, "EngineStart", dtEngineStart);
        AddNullableDate(so, "EngineEnd", dtEngineEnd);

        so.addProperty("ModelDisplay", szModelDisplay);
        so.addProperty("TailNumDisplay", szTailNumDisplay);
        so.addProperty("CatClassDisplay", szCatClassDisplay);
        so.addProperty("FlightData", szFlightData);
        so.addProperty("CustomProperties", rgCustomProperties);
        // we don't need to write back existing image properties
    }

    public void FromProperties(SoapObject so) {
        szError = "";

        try {
            idFlight = Integer.parseInt(so.getProperty("FlightID").toString());
        } catch (Exception ignored) {
        }

        szUser = so.getProperty("User").toString();
        idAircraft = Integer.parseInt(so.getProperty("AircraftID").toString());
        idCatClassOverride = Integer.parseInt(so
                .getProperty("CatClassOverride").toString());
        cNightLandings = Integer.parseInt(so.getProperty("NightLandings")
                .toString());
        cFullStopLandings = Integer.parseInt(so.getProperty("FullStopLandings")
                .toString());
        cLandings = Integer.parseInt(so.getProperty("Landings").toString());
        cApproaches = Integer.parseInt(so.getProperty("Approaches").toString());
        String szPrecApproaches = so.getPropertySafelyAsString("PrecisionApproaches");
        String szNonPrecApproaches = so.getPropertySafelyAsString("PrecisionApproaches");
        cApproachPrecision = szPrecApproaches.length() > 0 ? Integer.parseInt(szPrecApproaches) : 0;
        cApproachNonPrecision = szNonPrecApproaches.length() > 0 ? Integer.parseInt(szNonPrecApproaches) : 0;
        decXC = Double.parseDouble(so.getProperty("CrossCountry").toString());
        decNight = Double.parseDouble(so.getProperty("Nighttime").toString());
        decIMC = Double.parseDouble(so.getProperty("IMC").toString());
        decSimulatedIFR = Double.parseDouble(so.getProperty("SimulatedIFR")
                .toString());
        decGrndSim = Double.parseDouble(so.getProperty("GroundSim").toString());
        decDual = Double.parseDouble(so.getProperty("Dual").toString());
        decCFI = Double.parseDouble(so.getProperty("CFI").toString());
        decPIC = Double.parseDouble(so.getProperty("PIC").toString());
        decSIC = Double.parseDouble(so.getProperty("SIC").toString());
        decTotal = Double.parseDouble(so.getProperty("TotalFlightTime")
                .toString());
        fHold = Boolean.parseBoolean(so.getProperty("fHoldingProcedures")
                .toString());

        szRoute = Html.fromHtml(ReadNullableString(so, "Route")).toString();
        szComments = Html.fromHtml(ReadNullableString(so, "Comment")).toString();
        fPublic = Boolean.parseBoolean(so.getProperty("fIsPublic").toString());
        dtFlight = IsoDate.stringToDate(so.getProperty("Date").toString(), IsoDate.DATE);

        dtFlightStart = ReadNullableDate(so, "FlightStart");
        dtFlightEnd = ReadNullableDate(so, "FlightEnd");
        dtEngineStart = ReadNullableDate(so, "EngineStart");
        dtEngineEnd = ReadNullableDate(so, "EngineEnd");

        hobbsStart = Double.parseDouble(so.getProperty("HobbsStart").toString());
        hobbsEnd = Double.parseDouble(so.getProperty("HobbsEnd").toString());
        szModelDisplay = so.getProperty("ModelDisplay").toString();
        szTailNumDisplay = so.getProperty("TailNumDisplay").toString();
        szCatClassDisplay = so.getProperty("CatClassDisplay").toString();

        // FlightData is not always present.
        try {
            szFlightData = so.getProperty("FlightData").toString();
        } catch (Exception ignored) {
        }

        String szSigState = so.getPropertySafelyAsString("CFISignatureState");
        signatureStatus = szSigState.length() > 0 ? SigStatus.valueOf(szSigState) : SigStatus.None;
        // Remaining fields not always present.
        try {
            signatureComments = so.getPropertySafelyAsString("CFIComments");
            signatureDate = ReadNullableDate(so, "CFISignatureDate");
            signatureCFICert = so.getPropertySafelyAsString("CFICertificate");
            signatureCFIExpiration = ReadNullableDate(so, "CFIExpiration");
            signatureCFIName = so.getPropertySafelyAsString("CFIName");
            signatureHasDigitizedSig = Boolean.parseBoolean(so.getProperty("HasDigitizedSig").toString());
        } catch (Exception ignored) {
        }

        SoapObject props = (SoapObject) so.getPropertySafely("CustomProperties");
        int cProps = props.getPropertyCount();
        rgCustomProperties = new FlightProperty[cProps];
        for (int i = 0; i < cProps; i++) {
            FlightProperty fp = new FlightProperty();
            fp.FromProperties((SoapObject) props.getProperty(i));
            rgCustomProperties[i] = fp;
        }

        SoapObject images = (SoapObject) so.getPropertySafely("FlightImages");
        if (images != null) {
            int cImages = images.getPropertyCount();
            rgFlightImages = new MFBImageInfo[cImages];
            for (int i = 0; i < cImages; i++) {
                MFBImageInfo mfbii = new MFBImageInfo();
                mfbii.FromProperties((SoapObject) images.getProperty(i));
                rgFlightImages[i] = mfbii;
            }
        }
    }

    public void DeletePendingFlight() {
        if (idLocalDB <= 0)
            return;

        // kill the images for the flight while we still have referential integrity in the localID
        DeletePendingImagesForFlight();

        SQLiteDatabase db = MFBMain.mDBHelper.getWritableDatabase();

        try {
            String[] rgIdArg = {String.format(Locale.US, "%d", this.idLocalDB)};
            // delete the flight itself
            db.delete("Flights", "_id = ?", rgIdArg);

            // delete flightproperties for this flight.
            db.delete(FlightProperty.TABLENAME, "idFlight = ?", rgIdArg);
            this.idLocalDB = -1;
        } catch (Exception e) {
            Log.v(MFBConstants.LOG_TAG, "Error deleting pending flight - " + e.getMessage());
        }
    }

    public boolean ToDB() {
        boolean fResult = true;

        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);

        ContentValues cv = new ContentValues();
        cv.put("idFlight", idFlight);
        cv.put("szUser", szUser);
        cv.put("dtFlight", df.format(dtFlight));
        cv.put("idCatClassOverride", idCatClassOverride);
        cv.put("idAircraft", idAircraft);
        cv.put("cApproaches", cApproaches);
        cv.put("cApproachPrecision", cApproachPrecision);
        cv.put("cApproachNonPrecision", cApproachNonPrecision);
        cv.put("cLandings", cLandings);
        cv.put("cNightLandings", cNightLandings);
        cv.put("cFullStopLandings", cFullStopLandings);
        cv.put("decXC", decXC);
        cv.put("decIMC", decIMC);
        cv.put("decSimulatedIFR", decSimulatedIFR);
        cv.put("decGrndSim", decGrndSim);
        cv.put("decDual", decDual);
        cv.put("decNight", decNight);
        cv.put("decPIC", decPIC);
        cv.put("decCFI", decCFI);
        cv.put("decSIC", decSIC);
        cv.put("decTotal", decTotal);
        cv.put("fHold", fHold.toString());
        cv.put("szComments", szComments);
        cv.put("szRoute", szRoute);
        cv.put("fPublic", fPublic.toString());
        cv.put("dtFlightStart", df.format(dtFlightStart));
        cv.put("dtFlightEnd", df.format(dtFlightEnd));
        cv.put("dtEngineStart", df.format(dtEngineStart));
        cv.put("dtEngineEnd", df.format(dtEngineEnd));
        cv.put("hobbsStart", hobbsStart);
        cv.put("hobbsEnd", hobbsEnd);
        cv.put("szFlightData", szFlightData);
        cv.put("szError", szError);

        SQLiteDatabase db = MFBMain.mDBHelper.getWritableDatabase();
        try {
            if (this.idLocalDB <= 0)
                this.idLocalDB = db.insert("Flights", null, cv);
            else
                db.update("Flights", cv, "_id = ?", new String[]{String.format(Locale.US, "%d", this.idLocalDB)});
        } catch (Exception e) {
            fResult = false;
            szError = e.getMessage();
            Log.e(MFBConstants.LOG_TAG, "Error persisting logbook entry to db - " + szError);
        }
        return fResult;
    }

    private void FromCursor(Cursor c) {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);

        try {
            idFlight = c.getInt(c.getColumnIndex("idFlight"));
            szUser = c.getString(c.getColumnIndex("szUser"));
            dtFlight = df.parse(c.getString(c.getColumnIndex("dtFlight")));
            idCatClassOverride = c.getInt(c.getColumnIndex("idCatClassOverride"));
            idAircraft = c.getInt(c.getColumnIndex("idAircraft"));
            cApproaches = c.getInt(c.getColumnIndex("cApproaches"));
            cApproachPrecision = c.getInt(c.getColumnIndex("cApproachPrecision"));
            cApproachNonPrecision = c.getInt(c.getColumnIndex("cApproachNonPrecision"));
            cLandings = c.getInt(c.getColumnIndex("cLandings"));
            cNightLandings = c.getInt(c.getColumnIndex("cNightLandings"));
            cFullStopLandings = c.getInt(c.getColumnIndex("cFullStopLandings"));
            decXC = c.getDouble(c.getColumnIndex("decXC"));
            decIMC = c.getDouble(c.getColumnIndex("decIMC"));
            decSimulatedIFR = c.getDouble(c.getColumnIndex("decSimulatedIFR"));
            decGrndSim = c.getDouble(c.getColumnIndex("decGrndSim"));
            decDual = c.getDouble(c.getColumnIndex("decDual"));
            decNight = c.getDouble(c.getColumnIndex("decNight"));
            decPIC = c.getDouble(c.getColumnIndex("decPIC"));
            decCFI = c.getDouble(c.getColumnIndex("decCFI"));
            decSIC = c.getDouble(c.getColumnIndex("decSIC"));
            decTotal = c.getDouble(c.getColumnIndex("decTotal"));
            fHold = Boolean.parseBoolean(c.getString(c.getColumnIndex("fHold")));
            szComments = c.getString(c.getColumnIndex("szComments"));
            szRoute = c.getString(c.getColumnIndex("szRoute"));
            fPublic = Boolean.parseBoolean(c.getString(c.getColumnIndex("fPublic")));
            dtFlightStart = df.parse(c.getString(c.getColumnIndex("dtFlightStart")));
            dtFlightEnd = df.parse(c.getString(c.getColumnIndex("dtFlightEnd")));
            dtEngineStart = df.parse(c.getString(c.getColumnIndex("dtEngineStart")));
            dtEngineEnd = df.parse(c.getString(c.getColumnIndex("dtEngineEnd")));
            hobbsStart = c.getDouble(c.getColumnIndex("hobbsStart"));
            hobbsEnd = c.getDouble(c.getColumnIndex("hobbsEnd"));
            szFlightData = c.getString(c.getColumnIndex("szFlightData"));
            szError = c.getString(c.getColumnIndex("szError"));
        } catch (Exception e) {
            Log.e(MFBConstants.LOG_TAG, "FromCursor failed: " + e.getLocalizedMessage());
            this.idLocalDB = -1;
        }
    }

    private void FromDB() {
        if (this.idLocalDB > 0) {
            SQLiteDatabase db = MFBMain.mDBHelper.getWritableDatabase();
            Cursor c = null;

            try {
                c = db.query("Flights", null, "_id = ?", new String[]{String.format(Locale.US, "%d", this.idLocalDB)}, null, null, null);

                if (c != null && c.getCount() == 1) {
                    c.moveToFirst();
                    FromCursor(c);
                } else
                    throw new Exception("Query for flight from db failed!");
            } catch (Exception e) {
                Log.e(MFBConstants.LOG_TAG, "Requested stored flight failed to load - resetting");
                this.idLocalDB = -1;
            } finally {
                if (c != null)
                    c.close();
            }
        }
    }

    private static LogbookEntry[] getFlightsWithIdFlight(int idFlight) {
        LogbookEntry[] rglePending = new LogbookEntry[0];

        SQLiteDatabase db = MFBMain.mDBHelper.getWritableDatabase();
        Cursor c = null;

        try {
            c = db.query("Flights", null, "idFlight = ?", new String[]{String.format(Locale.US, "%d", idFlight)}, null, null, null);

            if (c != null) {
                rglePending = new LogbookEntry[c.getCount()];
                int i = 0;
                while (c.moveToNext()) {
                    LogbookEntry le = new LogbookEntry();
                    le.FromCursor(c);
                    le.idLocalDB = c.getLong(c.getColumnIndex("_id"));
                    le.rgFlightImages = MFBImageInfo.getLocalImagesForId(le.idLocalDB, PictureDestination.FlightImage);
                    rglePending[i++] = le;
                }
            } else
                throw new Exception("Query for flight from db failed!");
        } catch (Exception e) {
            Log.e("LogbookEntry", "Error retrieving pending flights: " + e.getLocalizedMessage());
        } finally {
            if (c != null)
                c.close();
        }

        return rglePending;
    }

    public static LogbookEntry[] getPendingFlights() {
        return getFlightsWithIdFlight(LogbookEntry.ID_PENDING_FLIGHT);
    }

    public static LogbookEntry[] getQueuedFlights() {
        return getFlightsWithIdFlight(LogbookEntry.ID_QUEUED_FLIGHT_UNSUBMITTED);
    }

    public static LogbookEntry[] getQueuedAndPendingFlights() {
        return mergeFlightLists(getPendingFlights(), getQueuedFlights());
    }

    public static LogbookEntry[] getNewFlights() {
        return getFlightsWithIdFlight(LogbookEntry.ID_NEW_FLIGHT);
    }

    public static LogbookEntry[] mergeFlightLists(LogbookEntry[] rgle1, LogbookEntry[] rgle2) {
        if (rgle1 == null && rgle2 == null)
            return new LogbookEntry[0];
        if (rgle1 == null)
            return rgle2;
        if (rgle2 == null)
            return rgle1;

        LogbookEntry[] rgleReturn = new LogbookEntry[rgle1.length + rgle2.length];
        int i = 0;
        for (LogbookEntry le : rgle1)
            rgleReturn[i++] = le;
        for (LogbookEntry le : rgle2)
            rgleReturn[i++] = le;

        return rgleReturn;
    }

    public MFBImageInfo getDefaultImage() {
        if (hasImages())
            return this.rgFlightImages[0];
        else {
            if (ActRecentsWS.m_rgac == null)
                ActRecentsWS.m_rgac = (new AircraftSvc()).getCachedAircraft();
            Aircraft ac = Aircraft.getAircraftById(this.idAircraft, ActRecentsWS.m_rgac);
            if (ac != null && ac.HasImage())
                return ac.AircraftImages[0];
        }
        return null;
    }

}
