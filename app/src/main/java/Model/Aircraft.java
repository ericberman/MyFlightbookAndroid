/*
	MyFlightbook for Android - provides native access to MyFlightbook
	pilot's logbook
    Copyright (C) 2017-2019 MyFlightbook, LLC

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

import android.content.Context;
import android.support.annotation.NonNull;
import android.text.format.DateFormat;

import com.myflightbook.android.R;
import com.myflightbook.android.WebServices.UTCDate;

import org.kobjects.isodate.IsoDate;
import org.ksoap2.serialization.KvmSerializable;
import org.ksoap2.serialization.PropertyInfo;
import org.ksoap2.serialization.SoapObject;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Locale;
import java.util.Vector;

import Model.MFBImageInfo.PictureDestination;


public class Aircraft extends SoapableObject implements KvmSerializable, Serializable, LazyThumbnailLoader.ThumbnailedItem {
    private static final long serialVersionUID = 2L;

    public static final int[] rgidInstanceTypes = {
            R.string.aircraftInstanceTypeReal,
            R.string.aircraftInstanceTypeSimUncertified,
            R.string.aircraftInstanceTypeSimLogAppchs,
            R.string.aircraftInstanceTypeSimLogAppchsLandings,
            R.string.aircraftInstanceTypeATD};

    private enum AircraftProp {
        pidTailNumber, pidAircratID, pidModelID, pidInstanctTypeID, pidVersion, pidICAO,
        pidAvionicsTechnologyUpgrade, pidIsGlass, pidGlassUpgradeDate,
        pidLastVOR, pidLastAltimeter, pidLastTransponder, pidLastELT, pidLastStatic, pidLastAnnual, pidRegistrationDue,
        pidLast100, pidLastOil, pidLastEngine, pidPublicNotes, pidPrivateNotes, pidDefaultImage, pidDefaultTemplates,
        pidHideFromSelection, pidPilotRole, pidCopyPICName
    }

    public String TailNumber = "";
    public int AircraftID = -1;
    public int ModelID = -1;
    public int InstanceTypeID = 1;
    public String ModelCommonName = "";
    public String ModelDescription = "";
    public String ICAO = "";
    public int Version = 0;
    public MFBImageInfo[] AircraftImages = new MFBImageInfo[0];
    public String DefaultImage = "";
    public final HashSet<Integer> DefaultTemplates = new HashSet<>();

    // Maintenance fields
    public Date LastVOR;
    public Date LastAltimeter;
    public Date LastTransponder;
    public Date LastELT;
    public Date LastStatic;
    public Date LastAnnual;
    public Date RegistrationDue;
    public double Last100;
    public double LastOil;
    public double LastEngine;

    // Glass
    public enum AvionicsTechnologyType { None, Glass, TAA }
    public AvionicsTechnologyType AvionicsTechnologyUpgrade = AvionicsTechnologyType.None;
    public Boolean IsGlass;
    public Date GlassUpgradeDate = null;

    // Notes
    public String PublicNotes;
    public String PrivateNotes;

    // User preferences
    public enum PilotRole {None, PIC, SIC, CFI}
    public Boolean HideFromSelection;
    public Boolean CopyPICNameWithCrossfill;
    public PilotRole RoleForPilot;

    public String ErrorString = "";

    public Aircraft() {
        super();
    }

    @NonNull
    @Override
    public String toString() {
        return String.format("%s (%s)", TailNumber, ModelDescription);
    }

    public Aircraft(SoapObject so) {
        super();
        FromProperties(so);
    }

    public boolean HasImage() {
        return (AircraftImages != null) && (AircraftImages.length > 0);
    }

    private final static String PREFIX_SIM = "SIM";
    private final static String PREFIX_ANON = "#";

    public boolean IsReal() {
        return InstanceTypeID == 1;
    }

    public boolean IsAnonymous() {
        return TailNumber.startsWith(PREFIX_ANON);
    }

    public String displayTailNumber() {
        if (IsAnonymous())
            return String.format("(%s)", ModelDescription);
        return TailNumber;
    }

    public String anonTailNumber() {
        return String.format(Locale.US, "%s%05d", PREFIX_ANON, ModelID);
    }

    public boolean FIsValid(Context c) {
        // check to see that the tailnumber begins with a country code

        boolean fStartsWithSim = TailNumber.toUpperCase(Locale.getDefault()).startsWith(PREFIX_SIM);

        if (IsAnonymous()) {
            // nothing to check - by definition, the way we know we are anonymous is that we begin with PREFIX_ANON
            return true;
        } else if (IsReal()) {
            // Real aircraft - MUST NOT begin with SIM prefix
            if (fStartsWithSim) {
                ErrorString = c.getString(R.string.errRealAircraftCantUseSIM);
                return false;
            }

            CountryCode cc = CountryCode.BestGuessPrefixForTail(TailNumber);
            if (cc == null) {
                ErrorString = c.getString(R.string.errInvalidCountryPrefix);
                return false;
            }
        } else {
            // A sim MUST begin with SIM prefix
            if (!fStartsWithSim) {
                ErrorString = c.getString(R.string.errSimsMustStartWithSIM);
                return false;
            }
        }

        return true;
    }

    public static Aircraft getAircraftById(int idAircraft, Aircraft[] rgac) {
        if (rgac != null) {
            for (Aircraft ac : rgac)
                if (ac.AircraftID == idAircraft)
                    return ac;
        }
        return null;
    }

    public void ToProperties(SoapObject so) {
        so.addProperty("Tailnumber", TailNumber);
        so.addProperty("AircraftID", AircraftID);
        so.addProperty("ModelID", ModelID);
        so.addProperty("Version", Version);
        so.addProperty("ICAO", ICAO);
        so.addProperty("InstanceTypeID", InstanceTypeID);
        so.addProperty("ModelCommonName", ModelCommonName);
        so.addProperty("ModelDescription", ModelDescription);

        so.addProperty("LastVOR", LastVOR);
        so.addProperty("LastAltimeter", LastAltimeter);
        so.addProperty("LastTransponder", LastTransponder);
        so.addProperty("LastELT", LastELT);
        so.addProperty("LastStatic", LastStatic);
        so.addProperty("LastAnnual", LastAnnual);
        so.addProperty("RegistrationDue", RegistrationDue);
        so.addProperty("Last100", Last100);
        so.addProperty("LastOilChange", LastOil);
        so.addProperty("LastNewEngine", LastEngine);

        so.addProperty("RoleForPilot", RoleForPilot);
        so.addProperty("CopyPICNameWithCrossfill", CopyPICNameWithCrossfill);
        so.addProperty("HideFromSelection", HideFromSelection);

        so.addProperty("IsGlass", IsGlass);
        so.addProperty("GlassUpgradeDate", GlassUpgradeDate);
        so.addProperty("AvionicsTechnologyUpgrade", AvionicsTechnologyUpgrade);

        so.addProperty("PublicNotes", PublicNotes);
        so.addProperty("PrivateNotes", PrivateNotes);

        so.addProperty("DefaultImage", DefaultImage);
    }

    public void FromProperties(SoapObject so) {
        TailNumber = so.getProperty("TailNumber").toString();
        AircraftID = Integer.parseInt(so.getProperty("AircraftID").toString());
        ModelID = Integer.parseInt(so.getProperty("ModelID").toString());
        InstanceTypeID = Integer.parseInt(so.getProperty("InstanceTypeID").toString());
        ModelCommonName = so.getProperty("ModelCommonName").toString();
        ModelDescription = so.getProperty("ModelDescription").toString();
        ICAO = so.getProperty("ICAO").toString();
        Version = Integer.parseInt(so.getProperty("Version").toString());

        LastVOR = ReadNullableDate(so, "LastVOR");
        LastAltimeter = ReadNullableDate(so, "LastAltimeter");
        LastTransponder = ReadNullableDate(so, "LastTransponder");
        LastELT = ReadNullableDate(so, "LastELT");
        LastStatic = ReadNullableDate(so, "LastStatic");
        LastAnnual = ReadNullableDate(so, "LastAnnual");
        RegistrationDue = ReadNullableDate(so, "RegistrationDue");
        Last100 = Double.parseDouble(so.getProperty("Last100").toString());
        LastOil = Double.parseDouble(so.getProperty("LastOilChange").toString());
        LastEngine = Double.parseDouble(so.getProperty("LastNewEngine").toString());

        HideFromSelection = Boolean.parseBoolean(so.getProperty("HideFromSelection").toString());
        CopyPICNameWithCrossfill = Boolean.parseBoolean(so.getProperty("CopyPICNameWithCrossfill").toString());
        RoleForPilot = PilotRole.valueOf(so.getProperty("RoleForPilot").toString());

        IsGlass = Boolean.parseBoolean(so.getProperty("IsGlass").toString());
        GlassUpgradeDate = ReadNullableDate(so,"GlassUpgradeDate");
        AvionicsTechnologyUpgrade = AvionicsTechnologyType.valueOf(so.getProperty("AvionicsTechnologyUpgrade").toString());

        PublicNotes = ReadNullableString(so, "PublicNotes");
        PrivateNotes = ReadNullableString(so, "PrivateNotes");

        SoapObject images = (SoapObject) so.getProperty("AircraftImages");
        if (images != null) {
            int cImages = images.getPropertyCount();
            AircraftImages = new MFBImageInfo[cImages];
            for (int i = 0; i < cImages; i++) {
                MFBImageInfo mfbii = new MFBImageInfo(PictureDestination.AircraftImage);
                mfbii.setTargetID(AircraftID);
                mfbii.FromProperties((SoapObject) images.getProperty(i));
                AircraftImages[i] = mfbii;
            }

            DefaultImage = ReadNullableString(so, "DefaultImage");
        }

        SoapObject templates = (SoapObject) so.getProperty("DefaultTemplates");
        int cVals = templates.getPropertyCount();
        for (int i = 0; i < cVals; i++)
            DefaultTemplates.add(Integer.parseInt(templates.getPropertyAsString(i)));
    }

    // serialization methods
    public int getPropertyCount() {
        return AircraftProp.values().length;
    }

    public Object getProperty(int i) {
        AircraftProp ap = AircraftProp.values()[i];
        switch (ap) {
            case pidTailNumber:
                return TailNumber;
            case pidAircratID:
                return AircraftID;
            case pidModelID:
                return ModelID;
            case pidVersion:
                return Version;
            case pidICAO:
                return ICAO;
            case pidInstanctTypeID:
                return InstanceTypeID;
            case pidLastVOR:
                return LastVOR;
            case pidLastAltimeter:
                return LastAltimeter;
            case pidLastTransponder:
                return LastTransponder;
            case pidLastELT:
                return LastELT;
            case pidLastStatic:
                return LastStatic;
            case pidLastAnnual:
                return LastAnnual;
            case pidRegistrationDue:
                return RegistrationDue;
            case pidLast100:
                return Last100;
            case pidLastOil:
                return LastOil;
            case pidLastEngine:
                return LastEngine;
            case pidHideFromSelection:
                return HideFromSelection;
            case pidCopyPICName:
                return CopyPICNameWithCrossfill;
            case pidPilotRole:
                return RoleForPilot.toString();
            case pidPublicNotes:
                return PublicNotes;
            case pidPrivateNotes:
                return PrivateNotes;
            case pidDefaultImage:
                return DefaultImage;
            case pidDefaultTemplates:
                return new Vector<>(Arrays.asList(DefaultTemplates.toArray()));
            case pidIsGlass:
                return IsGlass;
            case pidGlassUpgradeDate:
                return GlassUpgradeDate;
            case pidAvionicsTechnologyUpgrade:
                return AvionicsTechnologyUpgrade;
            default:
                return null;
        }
    }

    public void setProperty(int i, Object value) {
        AircraftProp ap = AircraftProp.values()[i];
        String sz = value.toString();
        switch (ap) {
            case pidTailNumber:
                TailNumber = sz;
                break;
            case pidAircratID:
                AircraftID = Integer.parseInt(sz);
                break;
            case pidModelID:
                ModelID = Integer.parseInt(sz);
                break;
            case pidICAO:
                ICAO = sz;
                break;
            case pidVersion:
                Version = Integer.parseInt(sz);
                break;
            case pidInstanctTypeID:
                InstanceTypeID = Integer.parseInt(sz);
                break;
            case pidLastVOR:
                LastVOR = IsoDate.stringToDate(sz, IsoDate.DATE);
                break;
            case pidLastAltimeter:
                LastAltimeter = IsoDate.stringToDate(sz, IsoDate.DATE);
                break;
            case pidLastTransponder:
                LastTransponder = IsoDate.stringToDate(sz, IsoDate.DATE);
                break;
            case pidLastELT:
                LastELT = IsoDate.stringToDate(sz, IsoDate.DATE);
                break;
            case pidLastStatic:
                LastStatic = IsoDate.stringToDate(sz, IsoDate.DATE);
                break;
            case pidLastAnnual:
                LastAnnual = IsoDate.stringToDate(sz, IsoDate.DATE);
                break;
            case pidRegistrationDue:
                RegistrationDue = IsoDate.stringToDate(sz, IsoDate.DATE);
                break;
            case pidLast100:
                Last100 = Double.parseDouble(sz);
                break;
            case pidLastOil:
                LastOil = Double.parseDouble(sz);
                break;
            case pidLastEngine:
                LastEngine = Double.parseDouble(sz);
                break;
            case pidHideFromSelection:
                HideFromSelection = Boolean.parseBoolean(sz);
                break;
            case pidCopyPICName:
                CopyPICNameWithCrossfill = Boolean.parseBoolean(sz);
                break;
            case pidPilotRole:
                RoleForPilot = PilotRole.valueOf(sz);
                break;
            case pidPublicNotes:
                PublicNotes = sz;
                break;
            case pidPrivateNotes:
                PrivateNotes = sz;
                break;
            case pidDefaultImage:
                DefaultImage = sz;
                break;
            case pidDefaultTemplates:
                DefaultTemplates.clear();
                Integer[] rgVals = (Integer[]) value;
                DefaultTemplates.addAll(Arrays.asList(rgVals));
                break;
            case pidIsGlass:
                IsGlass = Boolean.parseBoolean(sz);
                break;
            case pidGlassUpgradeDate:
                GlassUpgradeDate = IsoDate.stringToDate(sz, IsoDate.DATE);
                break;
            case pidAvionicsTechnologyUpgrade:
                AvionicsTechnologyUpgrade = AvionicsTechnologyType.valueOf(sz);
                break;
            default:
                break;
        }
    }

    @SuppressWarnings("rawtypes")
    public void getPropertyInfo(int i, @SuppressWarnings("rawtypes") Hashtable h, PropertyInfo pi) {
        AircraftProp ap = AircraftProp.values()[i];
        switch (ap) {
            case pidTailNumber:
                pi.type = PropertyInfo.STRING_CLASS;
                pi.name = "TailNumber";
                break;
            case pidAircratID:
                pi.type = PropertyInfo.INTEGER_CLASS;
                pi.name = "AircraftID";
                break;
            case pidModelID:
                pi.type = PropertyInfo.INTEGER_CLASS;
                pi.name = "ModelID";
                break;
            case pidVersion:
                pi.type = PropertyInfo.INTEGER_CLASS;
                pi.name = "Version";
                break;
            case pidICAO:
                pi.type = PropertyInfo.STRING_CLASS;
                pi.name = "ICAO";
                break;
            case pidInstanctTypeID:
                pi.type = PropertyInfo.INTEGER_CLASS;
                pi.name = "InstanceTypeID";
                break;
            case pidLastVOR:
                pi.type = PropertyInfo.OBJECT_CLASS;
                pi.name = "LastVOR";
                break;
            case pidLastAltimeter:
                pi.type = PropertyInfo.OBJECT_CLASS;
                pi.name = "LastAltimeter";
                break;
            case pidLastTransponder:
                pi.type = PropertyInfo.OBJECT_CLASS;
                pi.name = "LastTransponder";
                break;
            case pidLastELT:
                pi.type = PropertyInfo.OBJECT_CLASS;
                pi.name = "LastELT";
                break;
            case pidLastStatic:
                pi.type = PropertyInfo.OBJECT_CLASS;
                pi.name = "LastStatic";
                break;
            case pidLastAnnual:
                pi.type = PropertyInfo.OBJECT_CLASS;
                pi.name = "LastAnnual";
                break;
            case pidRegistrationDue:
                pi.type = PropertyInfo.OBJECT_CLASS;
                pi.name = "RegistrationDue";
                break;
            case pidLast100:
                pi.type = PropertyInfo.OBJECT_CLASS;
                pi.name = "Last100";
                break;
            case pidLastOil:
                pi.type = PropertyInfo.OBJECT_CLASS;
                pi.name = "LastOilChange";
                break;
            case pidLastEngine:
                pi.type = PropertyInfo.OBJECT_CLASS;
                pi.name = "LastNewEngine";
                break;
            case pidHideFromSelection:
                pi.type = PropertyInfo.BOOLEAN_CLASS;
                pi.name = "HideFromSelection";
                break;
            case pidCopyPICName:
                pi.type = PropertyInfo.BOOLEAN_CLASS;
                pi.name = "CopyPICNameWithCrossfill";
                break;
            case pidPilotRole:
                pi.type = PropertyInfo.STRING_CLASS;
                pi.name = "RoleForPilot";
                break;
            case pidPublicNotes:
                pi.type = PropertyInfo.STRING_CLASS;
                pi.name = "PublicNotes";
                break;
            case pidPrivateNotes:
                pi.type = PropertyInfo.STRING_CLASS;
                pi.name = "PrivateNotes";
                break;
            case pidDefaultImage:
                pi.type = PropertyInfo.STRING_CLASS;
                pi.name = "DefaultImage";
                break;
            case pidDefaultTemplates:
                pi.type = PropertyInfo.VECTOR_CLASS;
                pi.name = "DefaultTemplates";
                pi.elementType = new PropertyInfo();
                pi.elementType.type = PropertyInfo.INTEGER_CLASS;
                pi.elementType.name = "Integer";
            case pidIsGlass:
                pi.type = PropertyInfo.BOOLEAN_CLASS;
                pi.name = "IsGlass";
                break;
            case pidGlassUpgradeDate:
                pi.type = PropertyInfo.OBJECT_CLASS;
                pi.name = "GlassUpgradeDate";
                break;
            case pidAvionicsTechnologyUpgrade:
                pi.type = PropertyInfo.STRING_CLASS;
                pi.name = "AvionicsTechnologyUpgrade";
                break;
            default:
                break;
        }
    }

    public MFBImageInfo getDefaultImage() {
        if (HasImage()) {
            for (MFBImageInfo mfbii : AircraftImages)
                if (mfbii.ThumbnailFile.compareToIgnoreCase(DefaultImage) == 0)
                    return mfbii;
            return AircraftImages[0];
        }
        return null;
    }

    private static final Hashtable<Integer, Double> hashHighWaterHobbs = new Hashtable<>();
    private static final Hashtable<Integer, Double> hashHighWaterTach = new Hashtable<>();

    public static void updateHobbsForAircraft(Double hobbs, Integer idAircraft) {
        if (!hashHighWaterHobbs.containsKey(idAircraft))
            hashHighWaterHobbs.put(idAircraft, hobbs);

        Double d = hashHighWaterHobbs.get(idAircraft);
        if (d != null)
            hashHighWaterHobbs.put(idAircraft, Math.max(d, hobbs));
    }

    public static void updateTachForAircraft(Double tach, Integer idAircraft) {
        if (!hashHighWaterTach.containsKey(idAircraft))
            hashHighWaterTach.put(idAircraft, tach);

        Double d = hashHighWaterTach.get(idAircraft);
        if (d != null)
            hashHighWaterTach.put(idAircraft, Math.max(d, tach));
    }

    public static Double getHighWaterHobbsForAircraft(Integer idAircraft) {
        return hashHighWaterHobbs.containsKey(idAircraft) ? hashHighWaterHobbs.get(idAircraft) : 0.0;
    }

    public static Double getHighWaterTachForAircraft(Integer idAircraft) {
        return hashHighWaterTach.containsKey(idAircraft) ? hashHighWaterTach.get(idAircraft) : 0.0;
    }

    // region Inspections
    public Date NextVOR() {
        return UTCDate.IsNullDate(LastVOR) ? null : MFBUtil.AddDays(MFBUtil.LocalDateFromUTCDate(LastVOR), 30);
    }

    public Date NextAnnual() {
        return UTCDate.IsNullDate(LastAnnual) ? null : MFBUtil.AddCalendarMonths(MFBUtil.LocalDateFromUTCDate(LastAnnual), 12);
    }

    public Date NextELT() {
        return UTCDate.IsNullDate(LastELT) ? null : MFBUtil.AddCalendarMonths(MFBUtil.LocalDateFromUTCDate(LastELT), 12);
    }

    public Date NextAltimeter() {
        return UTCDate.IsNullDate(LastAltimeter) ? null : MFBUtil.AddCalendarMonths(MFBUtil.LocalDateFromUTCDate(LastAltimeter), 24);
    }

    public Date NextStatic () {
        return UTCDate.IsNullDate(LastStatic) ? null : MFBUtil.AddCalendarMonths(MFBUtil.LocalDateFromUTCDate(LastStatic), 24);
    }

    public Date NextTransponder() {
        return UTCDate.IsNullDate(LastTransponder) ? null : MFBUtil.AddCalendarMonths(MFBUtil.LocalDateFromUTCDate(LastTransponder), 24);
    }

    public String NextDueLabel(Date dt, String szFormat, Context c) {
        if (dt == null)
            return "";
        return String.format(Locale.getDefault(), szFormat, DateFormat.getDateFormat(c).format(dt));
    }
    // endregion
}
