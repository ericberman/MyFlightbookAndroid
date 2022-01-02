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
package Model;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
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

import java.io.Serializable;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Hashtable;
import java.util.Locale;
import java.util.Objects;
import java.util.TimeZone;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import Model.MFBImageInfo.PictureDestination;
import androidx.core.text.HtmlCompat;

public class LogbookEntry extends SoapableObject implements KvmSerializable, Serializable, LazyThumbnailLoader.ThumbnailedItem {

    public static final int ID_NEW_FLIGHT = -1;
    public static final int ID_UNSUBMITTED_FLIGHT = -2;
    public static final int ID_QUEUED_FLIGHT_UNSUBMITTED = -3;

    private enum FlightProp {
        pidFlightId, pidUser, pidFlightDate, pidCatClassOverride,
        pidAircraft, pidcApproaches, pidcAppPrecision, pidcAppNP, pidcLandings, pidcFSNight,
        pidcFSDay, piddXC, piddNight, piddIMC, piddSimulatedIFR, piddGrnd, piddDual, piddPIC,
        piddCFI, piddSIC, piddTotal, pidfHold, pidszCommets, pidszRoute, pidfPublic, pidszErr,
        piddtFStart, piddtFEnd, piddtEStart, piddtEEnd, piddHobbsStart, piddHobbsEnd,
        pidszModelDisplay, pidszTailDisplay, pidszCatClassDisplay, pidfHasData, pidszData,
        pidProperties, pidExistingImages, pidSend, pidShare, pidPendingID
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
    public String shareLink = "";
    public String sendLink = "";

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

    public Boolean fForcePending = false;   // Indicate if any save operation should force to be a pending flight.
    protected String pendingID = "";    // error to use in LogbookEntry; used for PendingFlight serialization

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

        LogbookEntry leNew = MFBUtil.clone(this);

        if (leNew == null)
            return null;

        leNew.shareLink = leNew.sendLink = "";

        leNew.idFlight = ID_NEW_FLIGHT;
        if (leNew.rgCustomProperties == null)
            leNew.rgCustomProperties = new FlightProperty[0];
        else {
            for (FlightProperty fp : leNew.rgCustomProperties) {
                fp.idFlight = ID_NEW_FLIGHT;
                fp.idProp = FlightProperty.ID_PROP_NEW;
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

    public Boolean IsAwaitingUpload() {
        return this.idFlight <= LogbookEntry.ID_UNSUBMITTED_FLIGHT;
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

    boolean isEmptyFlight() {
        boolean fHasAtMostTachStart = rgCustomProperties == null || rgCustomProperties.length == 0 ||
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

    void addOrSetPropertyValue(int idPropType, double decValue) {
        // expand the list of all properties, even ones that aren't currently set
        FlightProperty[] rgfpAll = FlightProperty.CrossProduct(FlightProperty.FromDB(this.idLocalDB), CustomPropertyTypesSvc.getCachedPropertyTypes());

        // find the nighttime takeoff property
        for (FlightProperty fp : rgfpAll) {
            if (fp.idPropType == idPropType) {
                // set it, distill the properties, and save 'em to the db.
                fp.decValue = decValue;
                FlightProperty[] rgfpUpdated = FlightProperty.DistillList(rgfpAll);
                FlightProperty.RewritePropertiesForFlight(this.idLocalDB, rgfpUpdated);
                SyncProperties();
                return;
            }
        }
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

    public FlightProperty PropertyWithID(int id) {
        if (rgCustomProperties == null)
            return null;

        for (FlightProperty fp : rgCustomProperties) {
            if (fp.idPropType == id)
                return fp;
        }
        return null;
    }

    public void RemovePropertyWithID(int idPropType) {
        FlightProperty fp = PropertyWithID(idPropType);
        if (fp != null) {
            fp.intValue = 0;
            FlightProperty[] rgProps = FlightProperty.CrossProduct(rgCustomProperties, CustomPropertyTypesSvc.getCachedPropertyTypes());
            FlightProperty[] rgfpUpdated = FlightProperty.DistillList(rgProps);
            FlightProperty.RewritePropertiesForFlight(idLocalDB, rgfpUpdated);
        }
    }

    public void AddApproachDescription(String szApproachDesc) {
        // expand the list of all properties, even ones that aren't currently set
        FlightProperty[] rgfpAll = FlightProperty.CrossProduct(FlightProperty.FromDB(this.idLocalDB), CustomPropertyTypesSvc.getCachedPropertyTypes());

        // find the nighttime takeoff property
        for (FlightProperty fp : rgfpAll) {
            if (fp.idPropType == CustomPropertyType.idPropTypeApproachDesc) {
                fp.stringValue = (fp.stringValue + " " + szApproachDesc).trim();
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
            case pidSend:
                return this.sendLink;
            case pidShare:
                return this.shareLink;
            case pidPendingID:
                return this.pendingID;
            default:
                return null;
        }
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
            case pidSend:
                this.sendLink = sz;
                break;
            case pidShare:
                this.shareLink = sz;
                break;
            case pidProperties:
            case pidExistingImages:
            default:
                break;
        }
    }

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
            case pidSend:
                pi.type = PropertyInfo.STRING_CLASS;
                pi.name = "SendFlightLink";
                break;
            case pidShare:
                pi.type = PropertyInfo.STRING_CLASS;
                pi.name = "SocialMediaLink";
                break;
            case pidPendingID:
                pi.type = PropertyInfo.STRING_CLASS;
                pi.name = "PendingID";
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
        // we don't need to write back existing image properties or send/share properties
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

        szRoute = HtmlCompat.fromHtml(ReadNullableString(so, "Route"), HtmlCompat.FROM_HTML_MODE_LEGACY).toString();
        szComments = HtmlCompat.fromHtml(ReadNullableString(so, "Comment"), HtmlCompat.FROM_HTML_MODE_LEGACY).toString();
        fPublic = Boolean.parseBoolean(so.getProperty("fIsPublic").toString());
        dtFlight = IsoDate.stringToDate(so.getProperty("Date").toString(), IsoDate.DATE);

        dtFlightStart = ReadNullableDate(so, "FlightStart");
        dtFlightEnd = ReadNullableDate(so, "FlightEnd");
        dtEngineStart = ReadNullableDate(so, "EngineStart");
        dtEngineEnd = ReadNullableDate(so, "EngineEnd");

        hobbsStart = Double.parseDouble(so.getProperty("HobbsStart").toString());
        hobbsEnd = Double.parseDouble(so.getProperty("HobbsEnd").toString());
        szModelDisplay = ReadNullableString(so, "ModelDisplay");
        szTailNumDisplay = ReadNullableString(so, "TailNumDisplay");
        szCatClassDisplay = ReadNullableString(so, "CatClassDisplay");

        sendLink = so.getPropertySafelyAsString("SendFlightLink");
        shareLink = so.getPropertySafelyAsString("SocialMediaLink");

        // FlightData is not always present.
        try {
            szFlightData = ReadNullableString(so,"FlightData");
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

    public void DeleteUnsubmittedFlightFromLocalDB() {
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
            Log.v(MFBConstants.LOG_TAG, "Error deleting unsubmitted flight - " + e.getMessage());
        }
    }

    public boolean ToDB() {
        boolean fResult = true;

        SimpleDateFormat df = new SimpleDateFormat(MFBConstants.TIMESTAMP, Locale.US);
        df.setTimeZone(TimeZone.getTimeZone("UTC"));

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
        cv.put("forcePending", fForcePending.toString());
        cv.put("PendingID", pendingID);

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
        SimpleDateFormat df = new SimpleDateFormat(MFBConstants.TIMESTAMP, Locale.US);
        df.setTimeZone(TimeZone.getTimeZone("UTC"));

        try {
            idFlight = c.getInt(c.getColumnIndexOrThrow("idFlight"));
            szUser = c.getString(c.getColumnIndexOrThrow("szUser"));
            dtFlight = df.parse(c.getString(c.getColumnIndexOrThrow("dtFlight")));
            idCatClassOverride = c.getInt(c.getColumnIndexOrThrow("idCatClassOverride"));
            idAircraft = c.getInt(c.getColumnIndexOrThrow("idAircraft"));
            cApproaches = c.getInt(c.getColumnIndexOrThrow("cApproaches"));
            cApproachPrecision = c.getInt(c.getColumnIndexOrThrow("cApproachPrecision"));
            cApproachNonPrecision = c.getInt(c.getColumnIndexOrThrow("cApproachNonPrecision"));
            cLandings = c.getInt(c.getColumnIndexOrThrow("cLandings"));
            cNightLandings = c.getInt(c.getColumnIndexOrThrow("cNightLandings"));
            cFullStopLandings = c.getInt(c.getColumnIndexOrThrow("cFullStopLandings"));
            decXC = c.getDouble(c.getColumnIndexOrThrow("decXC"));
            decIMC = c.getDouble(c.getColumnIndexOrThrow("decIMC"));
            decSimulatedIFR = c.getDouble(c.getColumnIndexOrThrow("decSimulatedIFR"));
            decGrndSim = c.getDouble(c.getColumnIndexOrThrow("decGrndSim"));
            decDual = c.getDouble(c.getColumnIndexOrThrow("decDual"));
            decNight = c.getDouble(c.getColumnIndexOrThrow("decNight"));
            decPIC = c.getDouble(c.getColumnIndexOrThrow("decPIC"));
            decCFI = c.getDouble(c.getColumnIndexOrThrow("decCFI"));
            decSIC = c.getDouble(c.getColumnIndexOrThrow("decSIC"));
            decTotal = c.getDouble(c.getColumnIndexOrThrow("decTotal"));
            fHold = Boolean.parseBoolean(c.getString(c.getColumnIndexOrThrow("fHold")));
            szComments = c.getString(c.getColumnIndexOrThrow("szComments"));
            szRoute = c.getString(c.getColumnIndexOrThrow("szRoute"));
            fPublic = Boolean.parseBoolean(c.getString(c.getColumnIndexOrThrow("fPublic")));
            dtFlightStart = df.parse(c.getString(c.getColumnIndexOrThrow("dtFlightStart")));
            dtFlightEnd = df.parse(c.getString(c.getColumnIndexOrThrow("dtFlightEnd")));
            dtEngineStart = df.parse(c.getString(c.getColumnIndexOrThrow("dtEngineStart")));
            dtEngineEnd = df.parse(c.getString(c.getColumnIndexOrThrow("dtEngineEnd")));
            hobbsStart = c.getDouble(c.getColumnIndexOrThrow("hobbsStart"));
            hobbsEnd = c.getDouble(c.getColumnIndexOrThrow("hobbsEnd"));
            szFlightData = c.getString(c.getColumnIndexOrThrow("szFlightData"));
            szError = c.getString(c.getColumnIndexOrThrow("szError"));
            pendingID = c.getString(c.getColumnIndexOrThrow("PendingID"));
            fForcePending = Boolean.parseBoolean(c.getString(c.getColumnIndexOrThrow("forcePending")));
        } catch (Exception e) {
            Log.e(MFBConstants.LOG_TAG, "FromCursor failed: " + e.getLocalizedMessage());
            this.idLocalDB = -1;
        }
    }

    private void FromDB() {
        if (this.idLocalDB > 0) {
            SQLiteDatabase db = MFBMain.mDBHelper.getWritableDatabase();

            try (Cursor c = db.query("Flights", null, "_id = ?", new String[]{String.format(Locale.US, "%d", this.idLocalDB)}, null, null, null)) {
                if (c != null && c.getCount() == 1) {
                    c.moveToFirst();
                    FromCursor(c);
                } else
                    throw new Exception("Query for flight from db failed!");
            } catch (Exception e) {
                Log.e(MFBConstants.LOG_TAG, "Requested stored flight failed to load - resetting");
                this.idLocalDB = -1;
            }
        }
    }

    private static LogbookEntry[] getFlightsWithIdFlight(int idFlight) {
        LogbookEntry[] rgleLocal = new LogbookEntry[0];

        SQLiteDatabase db = MFBMain.mDBHelper.getWritableDatabase();

        try (Cursor c = db.query("Flights", null, "idFlight = ?", new String[]{String.format(Locale.US, "%d", idFlight)}, null, null, null)) {

            if (c != null) {
                rgleLocal = new LogbookEntry[c.getCount()];
                int i = 0;
                while (c.moveToNext()) {
                    // Check for a pending flight
                    String szPending = c.getString(c.getColumnIndexOrThrow("PendingID"));
                    LogbookEntry le = (szPending != null && szPending.length() > 0) ? new PendingFlight() : new LogbookEntry();
                    rgleLocal[i++] = le;
                    le.FromCursor(c);
                    le.idLocalDB = c.getLong(c.getColumnIndexOrThrow("_id"));
                    le.rgFlightImages = MFBImageInfo.getLocalImagesForId(le.idLocalDB, PictureDestination.FlightImage);
                }
            } else
                throw new Exception("Query for flight from db failed!");
        } catch (Exception e) {
            Log.e("LogbookEntry", "Error retrieving local flights: " + e.getLocalizedMessage());
        }

        return rgleLocal;
    }

    public static LogbookEntry[] getUnsubmittedFlights() {
        return getFlightsWithIdFlight(LogbookEntry.ID_UNSUBMITTED_FLIGHT);
    }

    private static LogbookEntry[] getQueuedFlights() {
        return getFlightsWithIdFlight(LogbookEntry.ID_QUEUED_FLIGHT_UNSUBMITTED);
    }

    public static LogbookEntry[] getQueuedAndUnsubmittedFlights() {
        return mergeFlightLists(getUnsubmittedFlights(), getQueuedFlights());
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

    // Autofill utilities
    public double autoFillHobbs(long totalTimePaused) {

        long dtHobbs = 0;
        long dtFlight = 0;
        long dtEngine = 0;

        // compute the flight time, in ms, if known
        if (isKnownFlightTime())
            dtFlight = dtFlightEnd.getTime() - dtFlightStart.getTime();

        // and engine time, if known.
        if (isKnownEngineTime())
            dtEngine = dtEngineEnd.getTime() - dtEngineStart.getTime();

        if (hobbsStart > 0) {
            switch (MFBLocation.fPrefAutoFillHobbs) {
                case EngineTime:
                    dtHobbs = dtEngine;
                    break;
                case FlightTime:
                    dtHobbs = dtFlight;
                    break;
                default:
                    break;
            }

            dtHobbs -= totalTimePaused;

            if (dtHobbs > 0) {
                hobbsEnd = hobbsStart + (dtHobbs / MFBConstants.MS_PER_HOUR);
                if (MFBLocation.fPrefRoundNearestTenth)
                    hobbsEnd = Math.round(hobbsEnd * 10.0) / 10.0;

            }
        }
        return dtHobbs;
    }

    public double autoFillTotal(Aircraft ac, long totalTimePaused) {
        double dtTotal = 0;
        // do autotime
        switch (MFBLocation.fPrefAutoFillTime) {
            case EngineTime:
                if (isKnownEngineTime())
                    dtTotal = (dtEngineEnd.getTime() - dtEngineStart.getTime() - totalTimePaused) / MFBConstants.MS_PER_HOUR;
                break;
            case FlightTime:
                if (isKnownFlightTime())
                    dtTotal = (dtFlightEnd.getTime() - dtFlightStart.getTime() - totalTimePaused) / MFBConstants.MS_PER_HOUR;
                break;
            case HobbsTime:
                // NOTE: we do NOT subtract totalTimePaused here because hobbs should already have subtracted pause time,
                // whether from being entered by user (hobbs on airplane pauses on ground or with engine stopped)
                // or from this being called by autohobbs (which has already subtracted it)
                if (hobbsStart > 0 && hobbsEnd > hobbsStart)
                    dtTotal = hobbsEnd - hobbsStart; // hobbs is already in hours
                break;
            case BlockTime: {
                long blockOut = 0;
                long blockIn = 0;
                if (rgCustomProperties != null) {
                    for (FlightProperty fp : rgCustomProperties) {
                        if (fp.idPropType == CustomPropertyType.idPropTypeBlockIn)
                            blockIn = MFBUtil.removeSeconds(fp.dateValue).getTime();
                        if (fp.idPropType == CustomPropertyType.idPropTypeBlockOut)
                            blockOut = MFBUtil.removeSeconds(fp.dateValue).getTime();
                    }
                    if (blockIn > 0 && blockOut > 0)
                        dtTotal = (blockIn - blockOut - totalTimePaused) / MFBConstants.MS_PER_HOUR;
                }
            }
            break;
            case FlightStartToEngineEnd:
                if (isKnownFlightStart() && isKnownEngineEnd())
                    dtTotal = (dtEngineEnd.getTime() - dtFlightStart.getTime() - totalTimePaused) / MFBConstants.MS_PER_HOUR;
                break;
            default:
                break;
        }

        if (dtTotal > 0) {
            boolean fIsReal = true;

            if (MFBLocation.fPrefRoundNearestTenth)
                dtTotal = Math.round(dtTotal * 10.0) / 10.0;

            // update totals and XC if this is a real aircraft, else ground sim
            if (ac != null && ac.InstanceTypeID == 1) {
                decTotal = dtTotal;
                decXC = (Airport.MaxDistanceForRoute(szRoute) > MFBConstants.NM_FOR_CROSS_COUNTRY) ? dtTotal : 0.0;
            } else
                decGrndSim = dtTotal;

        }
        return dtTotal;
    }

    private void autoFillCostOfFlight() {
        // Fill in cost of flight.
        Aircraft ac = Aircraft.getAircraftById(idAircraft, (new AircraftSvc()).getCachedAircraft());

        if (ac == null)
            return;

        Pattern p = Pattern.compile("#PPH:(\\d+(?:[.,]\\d+)?)#", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(ac.PrivateNotes.toUpperCase(Locale.getDefault()));
        if (!m.find() || m.group().length() == 0)
            return;

        double rate = 0;
        NumberFormat nf = NumberFormat.getInstance(Locale.getDefault());
        try {
            rate = Objects.requireNonNull(nf.parse(Objects.requireNonNull(m.group(1)))).doubleValue();
        }
        catch (ParseException e) {
            return;
        }

        if (rate == 0)
            return;

        FlightProperty fpTachStart = PropertyWithID(CustomPropertyType.idPropTypeTachStart);
        FlightProperty fpTachEnd = PropertyWithID(CustomPropertyType.idPropTypeTachEnd);
        double tachStart = fpTachStart != null ? fpTachStart.decValue : 0;
        double tachEnd = fpTachEnd != null ? fpTachEnd.decValue : 0;

        double time = (hobbsEnd > hobbsStart && hobbsStart > 0) ?
                hobbsEnd - hobbsStart :
                (tachEnd > tachStart && tachStart > 0) ? tachEnd - tachStart : decTotal;

        double cost = rate * time;
        if (cost > 0)
            addOrSetPropertyValue(CustomPropertyType.idPropTypeFlightCost, cost);
    }

    private void autoFillInstruction() {
        // Check for ground instruction given or received
        double dual = decDual;
        double cfi = decCFI;
        if ((dual > 0 && cfi == 0) || (cfi > 0 && dual == 0)) {
            FlightProperty fpLessonStart = PropertyWithID(CustomPropertyType.idPropTypeLessonStart);
            FlightProperty fpLessonEnd = PropertyWithID(CustomPropertyType.idPropTypeLessonEnd); 

            if (fpLessonEnd == null || fpLessonStart == null ||fpLessonEnd.dateValue.compareTo(fpLessonStart.dateValue) <= 0)
            return;

            double tsLesson = fpLessonEnd.dateValue.getTime() - fpLessonStart.dateValue.getTime();

            // pull out flight or engine time, whichever is greater
            double tsFlight = isKnownFlightEnd() && isKnownFlightStart() && dtFlightEnd.compareTo(dtFlightStart) > 0 ? dtFlightEnd.getTime() - dtFlightStart.getTime() : 0;
            double tsEngine = isKnownEngineEnd() && isKnownEngineStart() && dtEngineEnd.compareTo(dtEngineStart) > 0 ? dtEngineEnd.getTime() - dtEngineStart.getTime() : 0;

            double tsNonGround = Math.max(Math.max(tsFlight, tsEngine), 0);

            double groundHours = (tsLesson - tsNonGround) / MFBConstants.MS_PER_HOUR;

            int idPropTarget = dual > 0 ? CustomPropertyType.idPropTypeGroundInstructionReceived : CustomPropertyType.idPropTypeGroundInstructionGiven;

            if (groundHours > 0)
                addOrSetPropertyValue(idPropTarget, groundHours);
        }
    }

    public void autoFillFinish() {
        autoFillCostOfFlight();
        autoFillInstruction();
    }
}
