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
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.myflightbook.android.WebServices.AircraftSvc;
import com.myflightbook.android.WebServices.AuthToken;
import com.myflightbook.android.WebServices.CommitFlightSvc;
import com.myflightbook.android.WebServices.MFBSoap;
import com.myflightbook.android.WebServices.PendingFlightSvc;
import com.myflightbook.android.WebServices.RecentFlightsSvc;
import com.myflightbook.android.WebServices.UTCDate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

import Model.Aircraft;
import Model.DecimalEdit;
import Model.DecimalEdit.EditMode;
import Model.FlightQuery;
import Model.LazyThumbnailLoader;
import Model.LogbookEntry;
import Model.MFBConstants;
import Model.MFBImageInfo;
import Model.MFBImageInfo.ImageCacheCompleted;
import Model.MFBUtil;
import Model.PackAndGo;
import Model.PendingFlight;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.text.HtmlCompat;
import androidx.fragment.app.ListFragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class ActRecentsWS extends ListFragment implements OnItemSelectedListener, ImageCacheCompleted, MFBMain.Invalidatable {

    private LogbookEntry[] m_rgLe = new LogbookEntry[0];

    private final ArrayList<LogbookEntry> m_rgExistingFlights = new ArrayList<>();
    public static PendingFlight[] cachedPendingFlights = null;

    private boolean fCouldBeMore = true;

    public static final String VIEWEXISTINGFLIGHT = "com.myflightbook.android.ViewFlight";
    private ActivityResultLauncher<Intent> mQueryLauncher = null;

    private static Boolean m_fIsRefreshing = false;

    public static Aircraft[] m_rgac = null;

    public enum FlightDetail {Low, Medium, High}

    public static Boolean fShowFlightImages = true;
    public static FlightDetail flightDetail = FlightDetail.Low;

    private FlightQuery currentQuery = new FlightQuery();

    private class FlightAdapter extends ArrayAdapter<LogbookEntry> {
        FlightAdapter(Context c,
                      LogbookEntry[] rgpp) {
            super(c, R.layout.flightitem, rgpp);
        }

        private String formattedTimeForLabel(int idLabel, double val, EditMode em) {
            return (val == 0) ? "" : String.format(Locale.getDefault(), "<b>%s:</b> %s ", getString(idLabel), DecimalEdit.StringForMode(val, em));
        }

        private String formattedTimeForLabel(int idLabel, int val) {
            return (val == 0) ? "" : String.format(Locale.getDefault(), "<b>%s:</b> %d ", getString(idLabel), val);
        }

        private String formattedTimeForLabel(int idLabel, Date dtStart, Date dtEnd) {
            if (dtStart == null || dtEnd == null || UTCDate.IsNullDate(dtStart) || UTCDate.IsNullDate(dtEnd))
                return "";

            Boolean fLocal = DlgDatePicker.fUseLocalTime;
            return String.format(Locale.getDefault(), "<b>%s: </b> %s - %s ", getString(idLabel), UTCDate.formatDate(fLocal, dtStart, this.getContext()), UTCDate.formatDate(fLocal, dtEnd, this.getContext()));
        }

        @Override
        public
        @NonNull
        View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                LayoutInflater vi = (LayoutInflater) requireActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                assert vi != null;
                v = vi.inflate(R.layout.flightitem, parent, false);
            }

            // Trigger the next batch of flights
            if (ActRecentsWS.this.fCouldBeMore && position + 1 >= ActRecentsWS.this.m_rgLe.length)
                ActRecentsWS.this.refreshRecentFlights(false);

            LogbookEntry le = this.getItem(position);
            assert le != null;

            boolean fIsAwaitingUpload = le.IsAwaitingUpload();
            boolean fIsPendingFlight = le instanceof PendingFlight;

            TextView txtError = v.findViewById(R.id.txtError);
            ImageView ivCamera = v.findViewById(R.id.imgCamera);
            ImageView ivSigState = v.findViewById(R.id.imgSigState);

            if (m_rgac == null)
                m_rgac = (new AircraftSvc()).getCachedAircraft();

            Aircraft ac = Aircraft.getAircraftById(le.idAircraft, m_rgac);
            EditMode em = DecimalEdit.DefaultHHMM ? EditMode.HHMM : EditMode.DECIMAL;

            if (ActRecentsWS.fShowFlightImages) {
                ivCamera.setVisibility(View.VISIBLE);
                if (le.hasImages() || (ac != null && ac.HasImage())) {
                    MFBImageInfo mfbii = le.hasImages() ? le.rgFlightImages[0] : ac.getDefaultImage();
                    Bitmap b = mfbii.bitmapFromThumb();
                    if (b != null) {
                        ivCamera.setImageBitmap(b);
                    }
                } else
                    ivCamera.setImageResource(R.drawable.noimage);
            }
            else
                ivCamera.setVisibility(View.GONE);

            switch (le.signatureStatus) {
                case None:
                    ivSigState.setVisibility(View.GONE);
                    break;
                case Valid:
                    ivSigState.setVisibility(View.VISIBLE);
                    ivSigState.setImageResource(R.drawable.sigok);
                    ivSigState.setContentDescription(getString(R.string.cdIsSignedValid));
                    break;
                case Invalid:
                    ivSigState.setVisibility(View.VISIBLE);
                    ivSigState.setImageResource(R.drawable.siginvalid);
                    ivSigState.setContentDescription(getString(R.string.cdIsSignedInvalid));
                    break;
            }

            txtError.setText(le.szError);
            txtError.setVisibility(le.szError != null && le.szError.length() > 0 ? View.VISIBLE : View.GONE);

            String szTailNumber = ((le.szTailNumDisplay == null || le.szTailNumDisplay.length() == 0) && ac != null) ? ac.displayTailNumber() : le.szTailNumDisplay;
            assert szTailNumber != null;

            TextView txtHeader = v.findViewById(R.id.txtFlightHeader);
            String szHeaderHTML = String.format(Locale.getDefault(),
                    "<strong><big>%s %s %s</big></strong>%s <i><strong><font color='gray'>%s</font></strong></i>",
                    TextUtils.htmlEncode(DateFormat.getDateFormat(this.getContext()).format(le.dtFlight)),
                    (fIsAwaitingUpload ? getString(R.string.txtAwaitingUpload) : (fIsPendingFlight ? getString(R.string.txtPendingAddition) : "")),
                    TextUtils.htmlEncode(szTailNumber.trim()),
                    TextUtils.htmlEncode(ac == null ? "" : String.format(Locale.getDefault(), " (%s)", ac.ModelDescription)),
                    TextUtils.htmlEncode(le.szRoute.trim()));
            txtHeader.setText(HtmlCompat.fromHtml(szHeaderHTML, HtmlCompat.FROM_HTML_MODE_LEGACY));

            Pattern pBold = Pattern.compile("(\\*)([^*_\\r\\n]*)(\\*)", Pattern.CASE_INSENSITIVE);
            Pattern pItalic = Pattern.compile("(_)([^*_\\r\\n]*)_", Pattern.CASE_INSENSITIVE);
            String szComments = pBold.matcher(TextUtils.htmlEncode(le.szComments)).replaceAll("<strong>$2</strong>");
            szComments = pItalic.matcher(szComments).replaceAll("<em>$2</em>");

            TextView txtComments = v.findViewById(R.id.txtComments);
            txtComments.setVisibility(szComments.length() == 0 ? View.GONE : View.VISIBLE);
            txtComments.setText(HtmlCompat.fromHtml(szComments, HtmlCompat.FROM_HTML_MODE_LEGACY));

            TextView txtFlightTimes = v.findViewById(R.id.txtFlightTimes);
            StringBuilder sb = new StringBuilder();
            if (flightDetail != FlightDetail.Low) {
                sb.append(formattedTimeForLabel(R.string.lblTotal, le.decTotal, em));
                sb.append(formattedTimeForLabel(R.string.lblLandingsAlt, le.cLandings));
                sb.append(formattedTimeForLabel(R.string.lblApproaches, le.cApproaches));
                sb.append(formattedTimeForLabel(R.string.lblNight, le.decNight, em));
                sb.append(formattedTimeForLabel(R.string.lblSimIMC, le.decSimulatedIFR, em));
                sb.append(formattedTimeForLabel(R.string.lblIMC, le.decIMC, em));
                sb.append(formattedTimeForLabel(R.string.lblXC, le.decXC, em));
                sb.append(formattedTimeForLabel(R.string.lblDual, le.decDual, em));
                sb.append(formattedTimeForLabel(R.string.lblGround, le.decGrndSim, em));
                sb.append(formattedTimeForLabel(R.string.lblCFI, le.decCFI, em));
                sb.append(formattedTimeForLabel(R.string.lblSIC, le.decSIC, em));
                sb.append(formattedTimeForLabel(R.string.lblPIC, le.decPIC, em));

                if (flightDetail == FlightDetail.High) {
                    sb.append(formattedTimeForLabel(R.string.autoEngine, le.dtEngineStart, le.dtEngineEnd));
                    sb.append(formattedTimeForLabel(R.string.autoFlight, le.dtFlightStart, le.dtFlightEnd));
                }
            }
            txtFlightTimes.setVisibility(sb.length() == 0 ? View.GONE : View.VISIBLE);
            txtFlightTimes.setText(HtmlCompat.fromHtml(sb.toString(), HtmlCompat.FROM_HTML_MODE_LEGACY));

            // show queued flights different from others.
            Typeface tf = Typeface.DEFAULT;
            int tfNew = (fIsAwaitingUpload || fIsPendingFlight) ? Typeface.ITALIC : Typeface.NORMAL;
            txtComments.setTypeface(tf, tfNew);
            txtHeader.setTypeface(tf, tfNew);
            txtFlightTimes.setTypeface(tf, tfNew);

            int backColor = ContextCompat.getColor(getContext(), fIsAwaitingUpload || fIsPendingFlight ? R.color.pendingBackground : R.color.colorBackground);
            v.setBackgroundColor(backColor);
            ivCamera.setBackgroundColor(backColor);
            ivSigState.setBackgroundColor(backColor);
            txtError.setBackgroundColor(backColor);
            txtComments.setBackgroundColor(backColor);
            txtFlightTimes.setBackgroundColor(backColor);
            txtHeader.setBackgroundColor(backColor);

            return v;
        }
    }

    private static class SubmitQueuedFlightsTask extends AsyncTask<Void, String, Boolean> implements MFBSoap.MFBSoapProgressUpdate {
        private ProgressDialog m_pd = null;
        private final AsyncWeakContext<ActRecentsWS> m_ctxt;
        private final LogbookEntry[] m_rgle;
        boolean m_fErrorsFound = false;
        boolean m_fFlightsPosted = false;

        SubmitQueuedFlightsTask(Context c, ActRecentsWS arws, LogbookEntry[] rgle) {
            super();
            m_ctxt = new AsyncWeakContext<>(c, arws);
            m_rgle = rgle;
        }

        protected boolean submitFlight(LogbookEntry le) {
            /*
                Scenarios:
                 - fForcePending is false, Regular flight, new or existing: call CommitFlightWithOptions
                 - fForcePending is false, Pending flight without a pending ID call CommitFlightWithOptions.  Shouldn't happen, but no big deal if it does
                 - fForcePending is false, Pending flight with a Pending ID: call CommitPendingFlight to commit it
                 - fForcePending is false, Pending flight without a pending ID: THROW EXCEPTION, how did this happen?

                 - fForcePending is true, Regular flight that is not new/pending (sorry about ambiguous "pending"): THROW EXCEPTION; this is an error
                 - fForcePending is true, Regular flight that is NEW: call CreatePendingFlight
                 - fForcePending is true, PendingFlight without a PendingID: call CreatePendingFlight.  Shouldn't happen, but no big deal if it does
                 - fForcePending is true, PendingFlight with a PendingID: call UpdatePendingFlight
             */
            PendingFlight pf = (le instanceof PendingFlight) ? (PendingFlight) le : null;
            if (le.fForcePending) {
                if (le.IsExistingFlight())
                    throw new IllegalStateException("Attempt to save an existing flight as a pending flight");
                if (pf == null || pf.getPendingID().length() == 0) {
                    PendingFlightSvc pfs = new PendingFlightSvc();
                    return ((ActRecentsWS.cachedPendingFlights = pfs.CreatePendingFlight(AuthToken.m_szAuthToken, le, m_ctxt.getContext())) != null);
                } else {
                    // existing pending flight but still force pending - call updatependingflight
                    PendingFlightSvc pfs = new PendingFlightSvc();
                    return ((ActRecentsWS.cachedPendingFlights = pfs.UpdatePendingFlight(AuthToken.m_szAuthToken, pf, m_ctxt.getContext())) != null);
                }
            } else {
                // Not force pending.
                // If regular flight (new or existing), or pending without a pendingID
                if (pf == null || pf.getPendingID().length() == 0) {
                    CommitFlightSvc cf = new CommitFlightSvc();
                    return cf.FCommitFlight(AuthToken.m_szAuthToken, le, m_ctxt.getContext());
                } else {
                    // By definition, here pf is non-null and it has a pending ID so it is a valid pending flight and we are not forcing - call commitpendingflight
                    PendingFlightSvc pfs = new PendingFlightSvc();
                    pfs.m_Progress = this;
                    PendingFlight[] rgpf = pfs.CommitPendingFlight(AuthToken.m_szAuthToken, pf, m_ctxt.getContext());
                    if (rgpf != null)
                        ActRecentsWS.cachedPendingFlights = rgpf;

                    pf.szError = pfs.getLastError();
                    return (rgpf != null && (pf.szError == null || pf.szError.length() == 0));  // we want to show any error
                }
            }
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            if (m_rgle == null || m_rgle.length == 0)
                return false;

            m_fErrorsFound = m_fFlightsPosted = false;

            int cFlights = m_rgle.length;
            int iFlight = 1;
            Context c = m_ctxt.getContext();
            String szFmtUploadProgress = c.getString(R.string.prgSavingFlights);
            for (LogbookEntry le : m_rgle) {
                String szStatus = String.format(szFmtUploadProgress, iFlight, cFlights);
                if (m_pd != null)
                    NotifyProgress((iFlight * 100) / cFlights, szStatus);
                iFlight++;
                le.SyncProperties();    // pull in the properties for the flight.
                if (submitFlight(le)) {
                    m_fFlightsPosted = true;
                    le.DeleteUnsubmittedFlightFromLocalDB();
                } else {
                    le.idFlight = LogbookEntry.ID_QUEUED_FLIGHT_UNSUBMITTED;    // don't auto-resubmit until the error is fixed.
                    le.ToDB();  // save the error so that we will display it on refresh.
                    m_fErrorsFound = true;
                }
            }

            return m_fFlightsPosted || m_fErrorsFound;
        }

        protected void onPreExecute() {
            Context c = m_ctxt.getContext();
            m_pd = MFBUtil.ShowProgress(c, c.getString(R.string.prgSavingFlight));
        }

        protected void onPostExecute(Boolean fResult) {
            try {
                if (m_pd != null)
                    m_pd.dismiss();
            } catch (Exception e) {
                Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e));
            }

            if (!fResult)   // nothing changed - nothing to do.
                return;

            ActRecentsWS arws = m_ctxt.getCallingActivity();

            if (arws == null || !arws.isAdded() || arws.isDetached() || arws.getActivity() == null)
                return;

            if (m_fFlightsPosted) {  // flight was added/updated, so invalidate stuff.

                MFBMain.invalidateCachedTotals();
                arws.refreshRecentFlights(true);
            }
            else if (m_fErrorsFound) {
                arws.m_rgLe = LogbookEntry.mergeFlightLists(LogbookEntry.mergeFlightLists(LogbookEntry.getQueuedAndUnsubmittedFlights(), arws.currentQuery.HasCriteria() ? null : ActRecentsWS.cachedPendingFlights), arws.m_rgExistingFlights.toArray(new LogbookEntry[0]));
                arws.populateList();
            }
        }

        protected void onProgressUpdate(String... msg) {
            m_pd.setMessage(msg[0]);
        }

        public void NotifyProgress(int percentageComplete, String szMsg) {
            this.publishProgress(szMsg);
        }
    }

    private static class RefreshFlightsTask extends AsyncTask<Void, Void, Boolean> {
        private RecentFlightsSvc m_rfSvc = null;
        Boolean fClearCache = false;
        private final AsyncWeakContext<ActRecentsWS> m_ctxt;

        RefreshFlightsTask(Context c, ActRecentsWS arws) {
            super();
            m_ctxt = new AsyncWeakContext<>(c, arws);
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            Context c = m_ctxt.getContext();
            ActRecentsWS arws = m_ctxt.getCallingActivity();
            if (c == null || arws == null)  // can't do anything without a context
                return false;

            m_rfSvc = new RecentFlightsSvc();
            LogbookEntry[] rgleQueuedAndUnsubmitted = LogbookEntry.getQueuedAndUnsubmittedFlights();

            if (fClearCache)
                RecentFlightsSvc.ClearCachedFlights();
            final int cFlightsPageSize = 15;
            LogbookEntry[] rgle = m_rfSvc.RecentFlightsWithQueryAndOffset(AuthToken.m_szAuthToken, arws.currentQuery, arws.m_rgExistingFlights.size(), cFlightsPageSize, c);

            // Refresh pending flights, if it's null
            if (ActRecentsWS.cachedPendingFlights == null)
                ActRecentsWS.cachedPendingFlights = new PendingFlightSvc().PendingFlightsForUser(AuthToken.m_szAuthToken, c);

            arws.fCouldBeMore = (rgle != null && rgle.length >= cFlightsPageSize);

            if (rgle != null) {
                arws.m_rgExistingFlights.addAll(Arrays.asList(rgle));
                arws.m_rgLe = LogbookEntry.mergeFlightLists(LogbookEntry.mergeFlightLists(rgleQueuedAndUnsubmitted, arws.currentQuery.HasCriteria() ? null : ActRecentsWS.cachedPendingFlights), arws.m_rgExistingFlights.toArray(new LogbookEntry[0]));
            }
            return rgle != null;
        }

        protected void onPreExecute() {
        }

        protected void onPostExecute(Boolean b) {
            m_fIsRefreshing = false;
            Context c = m_ctxt.getContext();
            ActRecentsWS arws = m_ctxt.getCallingActivity();
            if (c == null || arws == null || !arws.isAdded() || arws.isDetached() || arws.getActivity() == null)  // can't do anything without a context
                return;

            arws.populateList();

            // Turn off the swipe refresh layout here because, unlike most other refreshable screens,
            // this one doesn't show a progress indicator (because of continuous scroll)
            // So if this was swipe-to-refresh, then we leave the indicator up until the operation is complete.
            View v = arws.getView();
            if (v != null) {
                SwipeRefreshLayout srl = v.findViewById(R.id.swiperefresh);
                if (srl != null)
                    srl.setRefreshing(false);
            }

            if (arws.getView() != null) {
                TextView tv = arws.getView().findViewById(R.id.txtFlightQueryStatus);
                tv.setText(c.getString(arws.currentQuery != null && arws.currentQuery.HasCriteria() ? R.string.fqStatusNotAllflights : R.string.fqStatusAllFlights));
            }

            if (!b && m_rfSvc != null) {
                MFBUtil.Alert(arws, c.getString(R.string.txtError), m_rfSvc.getLastError());
            }
        }
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        this.setHasOptionsMenu(true);
        return inflater.inflate(R.layout.recentsws, container, false);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onViewCreated (@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        MFBMain.registerNotifyDataChange(this);
        MFBMain.registerNotifyResetAll(this);

        Intent i = requireActivity().getIntent();
        if (i != null) {
            Object o = i.getSerializableExtra(ActFlightQuery.QUERY_TO_EDIT);
            if (o != null)
                currentQuery = (FlightQuery) o;
        }

        fCouldBeMore = true;

        mQueryLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        currentQuery = (FlightQuery) Objects.requireNonNull(result.getData()).getSerializableExtra(ActFlightQuery.QUERY_TO_EDIT);
                    }
                });

        SwipeRefreshLayout srl = requireView().findViewById(R.id.swiperefresh);
        if (srl != null) {
            srl.setOnRefreshListener(() -> refreshRecentFlights(true));
        }
    }

    public void onDestroy() {
        MFBMain.unregisterNotify(this);
        super.onDestroy();
    }

    public void refreshRecentFlights(boolean fClearAll) {
        if (!AuthToken.FIsValid()) {
            MFBUtil.Alert(this, getString(R.string.txtError), getString(R.string.errMustBeSignedInToViewRecentFlights));
            fCouldBeMore = false;
        }

        if (fClearAll) {
            m_rgExistingFlights.clear();
            cachedPendingFlights = null;  // force fetch of these on first batch
            fCouldBeMore = true;
            this.getListView().setSelectionFromTop(0, 0);
        }

        if (MFBSoap.IsOnline(getContext())) {
            if (!m_fIsRefreshing) {
                m_fIsRefreshing = true; // don't refresh if already in progress.
                Log.d(MFBConstants.LOG_TAG, "ActRecentsWS - Refreshing From Server");
                RefreshFlightsTask rft = new RefreshFlightsTask(getContext(), this);
                rft.fClearCache = fClearAll;
                rft.execute();
            }
        } else {
            this.m_rgLe = LogbookEntry.getQueuedAndUnsubmittedFlights();

            PackAndGo p = new PackAndGo(getContext());
            Date dtLastPack = p.lastFlightsPackDate();
            if (dtLastPack != null) {
                this.m_rgLe = LogbookEntry.mergeFlightLists(this.m_rgLe, p.cachedFlights());
                MFBUtil.Alert(getContext(), getString(R.string.packAndGoOffline), String.format(Locale.getDefault(), getString(R.string.packAndGoUsingCached), java.text.DateFormat.getDateInstance().format(dtLastPack)));
            } else
                MFBUtil.Alert(getContext(), getString(R.string.txtError), getString(R.string.errNoInternet));

            this.fCouldBeMore = false;
            populateList();
        }
    }

    public void onResume() {
        super.onResume();

        if (!RecentFlightsSvc.HasCachedFlights())
            refreshRecentFlights(true);
        else
            populateList();
    }

    private void populateList() {
        if (getView() == null)
            return;

        int index = this.getListView().getFirstVisiblePosition();
        View v = this.getListView().getChildAt(0);
        int top = (v == null) ? 0 : v.getTop();
        if (index >= m_rgLe.length)
            index = top = 0;

        FlightAdapter fa = new FlightAdapter(getActivity(), m_rgLe);
        setListAdapter(fa);

        getListView().setSelectionFromTop(index, top);

        getListView().setOnItemClickListener((adapterView, view, position, l) -> {
            if (position < 0 || position >= m_rgLe.length)
                return;

            Intent i = new Intent(getActivity(), EditFlightActivity.class);
            i.putExtra(VIEWEXISTINGFLIGHT, m_rgLe[position]);
            startActivity(i);
        });
        if (ActRecentsWS.fShowFlightImages)
            new Thread(new LazyThumbnailLoader(m_rgLe, (FlightAdapter) this.getListAdapter())).start();

        LogbookEntry[] rgUnsubmitted = LogbookEntry.getUnsubmittedFlights();
        if (rgUnsubmitted != null && rgUnsubmitted.length > 0 && MFBSoap.IsOnline(getContext())) {
            SubmitQueuedFlightsTask spft = new SubmitQueuedFlightsTask(getContext(), this, rgUnsubmitted);
            spft.execute();
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.recentswsmenu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        int id = item.getItemId();
        if (id == R.id.refreshRecents)
            refreshRecentFlights(true);
        else if (id == R.id.findFlights) {
            if (MFBSoap.IsOnline(getContext())) {
                Intent i = new Intent(getActivity(), FlightQueryActivity.class);
                i.putExtra(ActFlightQuery.QUERY_TO_EDIT, currentQuery);
                mQueryLauncher.launch(i);
            } else
                MFBUtil.Alert(getContext(), getString(R.string.txtError), getString(R.string.errNoInternet));
        } else
            return super.onOptionsItemSelected(item);
        return true;
    }

    public void imgCompleted(MFBImageInfo sender) {
        ((FlightAdapter) Objects.requireNonNull(this.getListAdapter())).notifyDataSetChanged();
    }

    public void onItemSelected(AdapterView<?> arg0, View arg1, int arg2,
                               long arg3) {
    }

    public void onNothingSelected(AdapterView<?> arg0) {
    }

    public void invalidate() {
        m_rgac = null;
        m_rgExistingFlights.clear();
        fCouldBeMore = true;
        FlightAdapter fa = (FlightAdapter) this.getListAdapter();
        if (fa != null)
            fa.notifyDataSetChanged();
    }
}
