/*
	MyFlightbook for Android - provides native access to MyFlightbook
	pilot's logbook
    Copyright (C) 2017-2021 MyFlightbook, LLC

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
package com.myflightbook.android;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.myflightbook.android.WebServices.AircraftSvc;
import com.myflightbook.android.WebServices.AuthToken;
import com.myflightbook.android.WebServices.MFBSoap;
import com.myflightbook.android.WebServices.UTCDate;

import java.util.Date;
import java.util.Locale;

import Model.Aircraft;
import Model.Aircraft.PilotRole;
import Model.FlightQuery;
import Model.MFBConstants;
import Model.MFBImageInfo;
import Model.MFBImageInfo.PictureDestination;
import Model.MFBUtil;
import androidx.annotation.NonNull;

public class ActEditAircraft extends ActMFBForm implements android.view.View.OnClickListener,
        DlgDatePicker.DateTimeUpdate, ActMFBForm.GallerySource {
    public final static String AIRCRAFTID = "com.myflightbook.android.aircraftID";

    private Aircraft m_ac = null;

    private static class SubmitTask extends AsyncTask<Void, String, Boolean> implements MFBSoap.MFBSoapProgressUpdate {
        private ProgressDialog m_pd = null;
        private AircraftSvc m_acs = null;
        private final AsyncWeakContext<ActEditAircraft> m_ctxt;

        SubmitTask(Context c, ActEditAircraft aea) {
            super();
            m_ctxt = new AsyncWeakContext<>(c, aea);
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            Context c = m_ctxt.getContext();
            ActEditAircraft aea = m_ctxt.getCallingActivity();
            if (c == null || aea == null)
                return false;
            m_acs = new AircraftSvc();
            m_acs.m_Progress = this;
            m_acs.UpdateMaintenanceForAircraft(AuthToken.m_szAuthToken, aea.m_ac, c);
            return (m_acs.getLastError().length() == 0);
        }

        protected void onPreExecute() {
            m_pd = MFBUtil.ShowProgress(m_ctxt.getCallingActivity(), m_ctxt.getContext().getString(R.string.prgUpdatingAircraft));
        }

        protected void onPostExecute(Boolean b) {
            try {
                if (m_pd != null)
                    m_pd.dismiss();
            } catch (Exception e) {
                Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e));
            }

            ActEditAircraft aea = m_ctxt.getCallingActivity();
            if (aea == null || !aea.isAdded() || aea.getActivity() == null)
                return;

            if (b) {
                // force a refresh.
                m_acs.FlushCache();
                MFBMain.invalidateCachedTotals();    // could have updated maintenance, leading currency to be invalid.
                Intent i = new Intent();
                aea.getActivity().setResult(Activity.RESULT_OK, i);
                aea.finish();
            } else {
                MFBUtil.Alert(aea, aea.getString(R.string.txtError), m_acs.getLastError());
            }
        }

        protected void onProgressUpdate(String... msg) {
            m_pd.setMessage(msg[0]);
        }

        public void NotifyProgress(int percentageComplete, String szMsg) {
            this.publishProgress(szMsg);
        }
    }

    private static class DeleteTask extends AsyncTask<Void, String, MFBSoap> implements MFBSoap.MFBSoapProgressUpdate {
        private ProgressDialog m_pd = null;
        private final AsyncWeakContext<ActEditAircraft> m_ctxt;

        DeleteTask(Context c, ActEditAircraft aea) {
            super();
            m_ctxt = new AsyncWeakContext<>(c, aea);
        }

        @Override
        protected MFBSoap doInBackground(Void... params) {
            AircraftSvc m_acs = new AircraftSvc();
            m_acs.m_Progress = this;
            m_acs.DeleteAircraftForUser(AuthToken.m_szAuthToken, m_ctxt.getCallingActivity().m_ac.AircraftID, m_ctxt.getContext());
            return m_acs;
        }

        protected void onPreExecute() {
            m_pd = MFBUtil.ShowProgress(m_ctxt.getCallingActivity(), m_ctxt.getContext().getString(R.string.prgDeletingAircraft));
        }

        protected void onPostExecute(MFBSoap acs) {
            try {
                if (m_pd != null)
                    m_pd.dismiss();
            } catch (Exception e) {
                Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e));
            }

            Context c = m_ctxt.getContext();
            ActEditAircraft aea = m_ctxt.getCallingActivity();

            if (c == null || aea == null || acs == null || aea.getActivity() == null || !aea.isAdded())
                return;

            if (acs.getLastError().length() == 0) {
                Intent i = new Intent();
                aea.getActivity().setResult(Activity.RESULT_OK, i);
                aea.finish();
            } else
                MFBUtil.Alert(aea, c.getString(R.string.txtError), acs.getLastError());
        }

        protected void onProgressUpdate(String... msg) {
            m_pd.setMessage(msg[0]);
        }

        public void NotifyProgress(int percentageComplete, String szMsg) {
            this.publishProgress(szMsg);
        }
    }

    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        this.setHasOptionsMenu(true);
        return inflater.inflate(R.layout.editaircraft, container, false);
    }

    @Override
    public void onViewCreated (@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        AddListener(R.id.btnVORCheck);
        AddListener(R.id.btnTransponder);
        AddListener(R.id.btnPitotStatic);
        AddListener(R.id.btnAltimeter);
        AddListener(R.id.btnELT);
        AddListener(R.id.btnAnnual);
        AddListener(R.id.btnRegistration);
        AddListener(R.id.ckHideAircraftFromSelection);
        AddListener(R.id.rbRoleNone);
        AddListener(R.id.rbRoleCFI);
        AddListener(R.id.rbRolePIC);
        AddListener(R.id.rbRoleSIC);

        // Expand/collapse
        AddListener(R.id.acPrefsHeader);
        AddListener(R.id.acNotesHeader);
        AddListener(R.id.txtACMaintenance);
        AddListener(R.id.txtImageHeader);

        Intent i = requireActivity().getIntent();
        int idAircraft = i.getIntExtra(AIRCRAFTID, 0);
        if (idAircraft > 0) {
            AircraftSvc acs = new AircraftSvc();

            Aircraft[] rgac = acs.getCachedAircraft();

            for (Aircraft ac : rgac) {
                if (ac.AircraftID == idAircraft) {
                    m_ac = ac;
                    break;
                }
            }
        }

        toView();

        if (m_ac != null) {
            setExpandedState((TextView) findViewById(R.id.acPrefsHeader), findViewById(R.id.rbgPilotRole), m_ac.RoleForPilot != PilotRole.None, false);
            setExpandedState((TextView) findViewById(R.id.acNotesHeader), findViewById(R.id.sectACNotes), m_ac.PrivateNotes.length() + m_ac.PublicNotes.length() > 0, false);

            boolean fHideMaintenance = UTCDate.IsNullDate(m_ac.LastVOR) &&
                    UTCDate.IsNullDate(m_ac.LastTransponder) &&
                    UTCDate.IsNullDate(m_ac.LastStatic) &&
                    UTCDate.IsNullDate(m_ac.LastAltimeter) &&
                    UTCDate.IsNullDate(m_ac.LastELT) &&
                    UTCDate.IsNullDate(m_ac.LastAnnual) &&
                    UTCDate.IsNullDate(m_ac.RegistrationDue) &&
                    m_ac.Last100 == 0 &&
                    m_ac.LastOil == 0 &&
                    m_ac.LastEngine == 0;
            setExpandedState((TextView) findViewById(R.id.txtACMaintenance), findViewById(R.id.sectACMaintenance), !fHideMaintenance, false);
        }
    }

    @Override
    public void onCreateContextMenu(@NonNull ContextMenu menu, @NonNull View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = requireActivity().getMenuInflater();
        inflater.inflate(R.menu.imagemenu, menu);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menuAddComment || id == R.id.menuDeleteImage || id == R.id.menuViewImage)
            return onImageContextItemSelected(item, this);
        return true;
    }

    private void toView() {
        if (m_ac == null) {
            finish();
            return;
        }

        ((TextView) findViewById(R.id.txtTailNumber)).setText(m_ac.TailNumber);
        ((TextView) findViewById(R.id.txtAircraftType)).setText(getString(Aircraft.rgidInstanceTypes[m_ac.InstanceTypeID > 0 ? m_ac.InstanceTypeID - 1 : 0]));
        ((TextView) findViewById(R.id.txtAircraftMakeModel)).setText(String.format("%s%s",
                m_ac.ModelDescription,
                m_ac.ModelCommonName.length() > 0 ? String.format(" (%s)", m_ac.ModelCommonName.trim()) : ""));

        SetLocalDateForField(R.id.btnVORCheck, MFBUtil.LocalDateFromUTCDate(m_ac.LastVOR));
        SetLocalDateForField(R.id.btnAltimeter, MFBUtil.LocalDateFromUTCDate(m_ac.LastAltimeter));
        SetLocalDateForField(R.id.btnAnnual, MFBUtil.LocalDateFromUTCDate(m_ac.LastAnnual));
        SetLocalDateForField(R.id.btnTransponder, MFBUtil.LocalDateFromUTCDate(m_ac.LastTransponder));
        SetLocalDateForField(R.id.btnELT, MFBUtil.LocalDateFromUTCDate(m_ac.LastELT));
        SetLocalDateForField(R.id.btnPitotStatic, MFBUtil.LocalDateFromUTCDate(m_ac.LastStatic));
        SetLocalDateForField(R.id.btnRegistration, MFBUtil.LocalDateFromUTCDate(m_ac.RegistrationDue));


        SetStringForField(R.id.nextVOR, m_ac.NextDueLabel(m_ac.NextVOR(), getString(R.string.lblNextDue), getContext()));
        SetStringForField(R.id.nextAltimeter, m_ac.NextDueLabel(m_ac.NextAltimeter(), getString(R.string.lblNextDue), getContext()));
        SetStringForField(R.id.nextAnnual, m_ac.NextDueLabel(m_ac.NextAnnual(), getString(R.string.lblNextDue), getContext()));
        SetStringForField(R.id.nextTransponder, m_ac.NextDueLabel(m_ac.NextTransponder(), getString(R.string.lblNextDue), getContext()));
        SetStringForField(R.id.nextELT, m_ac.NextDueLabel(m_ac.NextELT(), getString(R.string.lblNextDue), getContext()));
        SetStringForField(R.id.nextPitotStatic, m_ac.NextDueLabel(m_ac.NextStatic(), getString(R.string.lblNextDue), getContext()));

        SetDoubleForField(R.id.txt100hr, m_ac.Last100);
        SetDoubleForField(R.id.txtOilChange, m_ac.LastOil);
        SetDoubleForField(R.id.txtNewEngine, m_ac.LastEngine);

        SetStringForField(R.id.txtPublicAircraftNotes, m_ac.PublicNotes);
        SetStringForField(R.id.txtPrivateAircraftNotes, m_ac.PrivateNotes);
        SetStringForField(R.id.txtMaintNotes, m_ac.MaintenanceNote);

        SetCheckState(R.id.ckHideAircraftFromSelection, !m_ac.HideFromSelection);
        switch (m_ac.RoleForPilot) {
            case None:
                SetRadioButton(R.id.rbRoleNone);
                break;
            case PIC:
                SetRadioButton(R.id.rbRolePIC);
                break;
            case SIC:
                SetRadioButton(R.id.rbRoleSIC);
                break;
            case CFI:
                SetRadioButton(R.id.rbRoleCFI);
                break;
        }

        setUpImageGallery(getGalleryID(), getImages(), getGalleryHeader());

        findViewById(R.id.sectMaintenance).setVisibility(m_ac.IsReal() && !m_ac.IsAnonymous() ? View.VISIBLE : View.GONE);
    }

    private void fromView() {
        // dates were changed synchronously, only need the decimal values.
        m_ac.Last100 = DoubleFromField(R.id.txt100hr);
        m_ac.LastOil = DoubleFromField(R.id.txtOilChange);
        m_ac.LastEngine = DoubleFromField(R.id.txtNewEngine);

        m_ac.PublicNotes = StringFromField(R.id.txtPublicAircraftNotes);
        m_ac.PrivateNotes = StringFromField(R.id.txtPrivateAircraftNotes);
        m_ac.MaintenanceNote = StringFromField(R.id.txtMaintNotes);
    }

    public void onClick(View v) {
        fromView();
        int id = v.getId();
        if (id ==R.id.btnVORCheck) {
            if (UTCDate.IsNullDate(m_ac.LastVOR))
                m_ac.LastVOR = MFBUtil.UTCDateFromLocalDate(new Date());
            else
                SetDateTime(id, MFBUtil.LocalDateFromUTCDate(m_ac.LastVOR), this, DlgDatePicker.datePickMode.LOCALDATENULLABLE);
        } else if (id == R.id.btnAltimeter) {
            if (UTCDate.IsNullDate(m_ac.LastAltimeter))
                m_ac.LastAltimeter = MFBUtil.UTCDateFromLocalDate(new Date());
            else
                SetDateTime(id, MFBUtil.LocalDateFromUTCDate(m_ac.LastAltimeter), this, DlgDatePicker.datePickMode.LOCALDATENULLABLE);
        } else if (id == R.id.btnAnnual) {
            if (UTCDate.IsNullDate(m_ac.LastAnnual))
                m_ac.LastAnnual = MFBUtil.UTCDateFromLocalDate(new Date());
            else
                SetDateTime(id, MFBUtil.LocalDateFromUTCDate(m_ac.LastAnnual), this, DlgDatePicker.datePickMode.LOCALDATENULLABLE);
        } else if (id == R.id.btnTransponder) {
            if (UTCDate.IsNullDate(m_ac.LastTransponder))
                m_ac.LastTransponder = MFBUtil.UTCDateFromLocalDate(new Date());
            else
                SetDateTime(id, MFBUtil.LocalDateFromUTCDate(m_ac.LastTransponder), this, DlgDatePicker.datePickMode.LOCALDATENULLABLE);
        } else if (id == R.id.btnELT) {
            if (UTCDate.IsNullDate(m_ac.LastELT))
                m_ac.LastELT = MFBUtil.UTCDateFromLocalDate(new Date());
            else
                SetDateTime(id, MFBUtil.LocalDateFromUTCDate(m_ac.LastELT), this, DlgDatePicker.datePickMode.LOCALDATENULLABLE);
        } else if (id == R.id.btnPitotStatic) {
            if (UTCDate.IsNullDate(m_ac.LastStatic))
                m_ac.LastStatic = MFBUtil.UTCDateFromLocalDate(new Date());
            else
                SetDateTime(id, MFBUtil.LocalDateFromUTCDate(m_ac.LastStatic), this, DlgDatePicker.datePickMode.LOCALDATENULLABLE);
        } else if (id == R.id.btnRegistration) {
            if (UTCDate.IsNullDate(m_ac.RegistrationDue))
                m_ac.RegistrationDue = MFBUtil.UTCDateFromLocalDate(new Date());
            else
                SetDateTime(id, MFBUtil.LocalDateFromUTCDate(m_ac.RegistrationDue), this, DlgDatePicker.datePickMode.LOCALDATENULLABLE);
        } else if (id == R.id.ckHideAircraftFromSelection)
            m_ac.HideFromSelection = !CheckState(id);
        else if (id == R.id.rbRoleNone)
            m_ac.RoleForPilot = PilotRole.None;
        else if (id == R.id.rbRolePIC)
            m_ac.RoleForPilot = PilotRole.PIC;
        else if (id == R.id.rbRoleSIC)
            m_ac.RoleForPilot = PilotRole.SIC;
        else if (id == R.id.rbRoleCFI)
            m_ac.RoleForPilot = PilotRole.CFI;
        else if (id == R.id.acNotesHeader) {
            View target = findViewById(R.id.sectACNotes);
            setExpandedState((TextView) v, target, target.getVisibility() != View.VISIBLE);
        } else if (id == R.id.acPrefsHeader) {
            View target = findViewById(R.id.rbgPilotRole);
            setExpandedState((TextView) v, target, target.getVisibility() != View.VISIBLE);
        } else if (id == R.id.txtACMaintenance) {
            View target = findViewById(R.id.sectACMaintenance);
            setExpandedState((TextView) v, target, target.getVisibility() != View.VISIBLE);
        } else if (id == R.id.txtImageHeader) {
            View target = findViewById(R.id.tblImageTable);
            setExpandedState((TextView) v, target, target.getVisibility() != View.VISIBLE);
        }

        toView();
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE && resultCode == Activity.RESULT_OK)
            AddCameraImage(m_TempFilePath, false);
        else if (requestCode == SELECT_IMAGE_ACTIVITY_REQUEST_CODE && resultCode == Activity.RESULT_OK)
            AddGalleryImage(data);
    }

    public void updateDate(int id, Date dt) {
        fromView();
        dt = MFBUtil.UTCDateFromLocalDate(dt);
        if (id == R.id.btnVORCheck) {
            m_ac.LastVOR = dt;
            SetLocalDateForField(R.id.btnVORCheck, m_ac.LastVOR);
        }else if (id == R.id.btnAltimeter) {
            m_ac.LastAltimeter = dt;
            SetLocalDateForField(R.id.btnAltimeter, m_ac.LastAltimeter);
        } else if (id == R.id.btnAnnual) {
            m_ac.LastAnnual = dt;
            SetLocalDateForField(R.id.btnAnnual, m_ac.LastAnnual);
        } else if (id == R.id.btnTransponder) {
            m_ac.LastTransponder = dt;
            SetLocalDateForField(R.id.btnTransponder, m_ac.LastTransponder);
        } else if (id == R.id.btnELT) {
            m_ac.LastELT = dt;
            SetLocalDateForField(R.id.btnELT, m_ac.LastELT);
        } else if (id == R.id.btnPitotStatic) {
            m_ac.LastStatic = dt;
            SetLocalDateForField(R.id.btnPitotStatic, m_ac.LastStatic);
        } else if (id == R.id.btnRegistration) {
            m_ac.RegistrationDue = dt;
            SetLocalDateForField(R.id.btnRegistration, m_ac.RegistrationDue);
        }
        toView();
    }

    private void updateAircraft() {
        fromView();
        SubmitTask st = new SubmitTask(getContext(), this);
        st.execute();
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.editaircraftmenu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menuChoosePicture)
            ChoosePicture();
        else if (id == R.id.menuTakePicture)
            TakePicture();
        else if (id == R.id.menuUpdateAircraft) {
            if (MFBSoap.IsOnline(getContext()))
                updateAircraft();
            else
                MFBUtil.Alert(getContext(), getString(R.string.txtError), getString(R.string.errNoInternet));
        } else if (id == R.id.menuDeleteAircraft)
            (new DeleteTask(getContext(), this)).execute();
        else if (id == R.id.menuViewSchedule) {
            if (MFBSoap.IsOnline(getContext()))
                ActWebView.ViewURL(getActivity(), MFBConstants.AuthRedirWithParams(String.format(Locale.US, "d=aircraftschedule&ac=%d", m_ac.AircraftID), getContext()));
            else
                MFBUtil.Alert(getContext(), getString(R.string.txtError), getString(R.string.errNoInternet));
        } else if (id == R.id.findFlights) {
            if (MFBSoap.IsOnline(getContext())) {
                FlightQuery fq = new FlightQuery();
                fq.Init();
                fq.AircraftList = new Aircraft[]{m_ac};
                Intent i = new Intent(getActivity(), RecentFlightsActivity.class);
                Bundle b = new Bundle();
                b.putSerializable(ActFlightQuery.QUERY_TO_EDIT, fq);
                i.putExtras(b);
                startActivity(i);
            } else
                MFBUtil.Alert(getContext(), getString(R.string.txtError), getString(R.string.errNoInternet));
        } else
            return super.onOptionsItemSelected(item);
        return true;
    }

    /*
     * GallerySource methods
     * (non-Javadoc)
     * @see com.myflightbook.android.ActMFBForm.GallerySource#getGalleryID()
     */
    public int getGalleryID() {
        return R.id.tblImageTable;
    }

    public View getGalleryHeader() {
        return findViewById(R.id.txtImageHeader);
    }

    public MFBImageInfo[] getImages() {
        if (m_ac == null || m_ac.AircraftImages == null)
            return new MFBImageInfo[0];
        else
            return m_ac.AircraftImages;
    }

    public void setImages(MFBImageInfo[] rgmfbii) {
        m_ac.AircraftImages = rgmfbii;
    }

    public void newImage(MFBImageInfo mfbii) {
        mfbii.setPictureDestination(PictureDestination.AircraftImage);
        mfbii.setTargetID(m_ac.AircraftID);
        mfbii.toDB();
        m_ac.AircraftImages = MFBImageInfo.getLocalImagesForId(m_ac.AircraftID, PictureDestination.AircraftImage);
    }

    public void refreshGallery() {
        setUpImageGallery(getGalleryID(), getImages(), getGalleryHeader());
    }

    public PictureDestination getDestination() {
        return PictureDestination.AircraftImage;
    }
}
