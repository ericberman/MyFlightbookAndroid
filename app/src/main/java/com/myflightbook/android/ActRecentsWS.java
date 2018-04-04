/*
	MyFlightbook for Android - provides native access to MyFlightbook
	pilot's logbook
    Copyright (C) 2017-2018 MyFlightbook, LLC

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
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ListFragment;
import android.text.Html;
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
import com.myflightbook.android.WebServices.RecentFlightsSvc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

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
import Model.PostingOptions;

public class ActRecentsWS extends ListFragment implements OnItemSelectedListener, ImageCacheCompleted, MFBMain.Invalidatable {

    private LogbookEntry[] m_rgLe = new LogbookEntry[0];

    private ArrayList<LogbookEntry> m_rgExistingFlights = new ArrayList<>();

    private boolean fCouldBeMore = true;

    public static final String VIEWEXISTINGFLIGHTID = "com.myflightbook.android.ViewFlightID";
    public static final String VIEWEXISTINGFLIGHTLOCALID = "com.myflightbook.android.ViewFlightLocalID";

    private static Boolean m_fIsRefreshing = false;

    public static Aircraft[] m_rgac = null;

    public static Boolean fShowFlightImages = true;
    public static Boolean fShowFlightTimes = true;

    private FlightQuery currentQuery = new FlightQuery();

    private class FlightAdapter extends ArrayAdapter<LogbookEntry> {
        FlightAdapter(Context c, int rid,
                      LogbookEntry[] rgpp) {
            super(c, rid, rgpp);
        }

        private String formattedTimeForLabel(int idLabel, double val, EditMode em) {
            return (val == 0) ? "" : String.format(Locale.getDefault(), "<b>%s:</b> %s ", getString(idLabel), DecimalEdit.StringForMode(val, em));
        }

        private String formattedTimeForLabel(int idLabel, int val) {
            return (val == 0) ? "" : String.format(Locale.getDefault(), "<b>%s:</b> %d ", getString(idLabel), val);
        }

        @Override
        public
        @NonNull
        View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                LayoutInflater vi = (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                assert vi != null;
                v = vi.inflate(R.layout.flightitem, parent, false);
            }

            // Trigger the next batch of flights
            if (ActRecentsWS.this.fCouldBeMore && position + 1 >= ActRecentsWS.this.m_rgLe.length)
                ActRecentsWS.this.refreshRecentFlights(false);

            LogbookEntry le = this.getItem(position);
            assert le != null;

            TextView txtError = v.findViewById(R.id.txtError);
            ImageView ivCamera = v.findViewById(R.id.imgCamera);
            ImageView ivSigState = v.findViewById(R.id.imgSigState);

            if (m_rgac == null)
                m_rgac = (new AircraftSvc()).getCachedAircraft();

            Boolean fIsPending;

            Aircraft ac = Aircraft.getAircraftById(le.idAircraft, m_rgac);
            EditMode em = DecimalEdit.DefaultHHMM ? EditMode.HHMM : EditMode.DECIMAL;

            if (ActRecentsWS.fShowFlightImages) {
                ivCamera.setVisibility(View.VISIBLE);
                if (le.hasImages() || (ac != null && ac.HasImage())) {
                    //noinspection ConstantConditions - ac.AircraftImages cannot be null because ac.HasImage() has already verified that it isn't.
                    MFBImageInfo mfbii = le.hasImages() ? le.rgFlightImages[0] : ac.AircraftImages[0];
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

            fIsPending = le.IsPendingFlight();

            txtError.setText(le.szError);
            txtError.setVisibility(le.szError != null && le.szError.length() > 0 ? View.VISIBLE : View.GONE);

            String szTailNumber = ((le.szTailNumDisplay == null || le.szTailNumDisplay.length() == 0) && ac != null) ? ac.displayTailNumber() : le.szTailNumDisplay;
            assert szTailNumber != null;

            TextView txtHeader = v.findViewById(R.id.txtFlightHeader);
            String szHeaderHTML = String.format(Locale.getDefault(),
                    "<strong><big>%s</big> %s (%s)</strong> <i><font color='gray'>%s</font></i>",
                    TextUtils.htmlEncode(DateFormat.getDateFormat(this.getContext()).format(le.dtFlight)),
                    (le.IsPendingFlight() ? (" " + getString(R.string.txtPending)) : ""),
                    TextUtils.htmlEncode(szTailNumber.trim()),
                    TextUtils.htmlEncode(le.szRoute.trim()));
            txtHeader.setText(Html.fromHtml(szHeaderHTML));

            TextView txtComments = v.findViewById(R.id.txtComments);
            txtComments.setVisibility(le.szComments.length() == 0 ? View.GONE : View.VISIBLE);
            txtComments.setText(le.szComments);

            TextView txtFlightTimes = v.findViewById(R.id.txtFlightTimes);
            StringBuilder sb = new StringBuilder();
            if (fShowFlightTimes) {
                sb.append(formattedTimeForLabel(R.string.lblLandingsAlt, le.cLandings));
                sb.append(formattedTimeForLabel(R.string.lblApproaches, le.cApproaches));
                sb.append(formattedTimeForLabel(R.string.lblNight, le.decNight, em));
                sb.append(formattedTimeForLabel(R.string.lblSimIMC, le.decSimulatedIFR, em));
                sb.append(formattedTimeForLabel(R.string.lblIMC, le.decIMC, em));
                sb.append(formattedTimeForLabel(R.string.lblNight, le.decXC, em));
                sb.append(formattedTimeForLabel(R.string.lblDual, le.decDual, em));
                sb.append(formattedTimeForLabel(R.string.lblGround, le.decGrndSim, em));
                sb.append(formattedTimeForLabel(R.string.lblCFI, le.decCFI, em));
                sb.append(formattedTimeForLabel(R.string.lblSIC, le.decSIC, em));
                sb.append(formattedTimeForLabel(R.string.lblPIC, le.decPIC, em));
                sb.append(formattedTimeForLabel(R.string.lblTotal, le.decTotal, em));
            }
            txtFlightTimes.setVisibility(sb.length() == 0 ? View.GONE : View.VISIBLE);
            txtFlightTimes.setText(Html.fromHtml(sb.toString()));

            // show pending flights different from others.
            Typeface tf = Typeface.DEFAULT;
            txtComments.setTypeface(tf, fIsPending ? Typeface.ITALIC : Typeface.NORMAL);
            v.setBackgroundColor(fIsPending ? Color.LTGRAY : Color.WHITE);

            return v;
        }
    }

    private static class SubmitPendingFlightsTask extends AsyncTask<Void, String, Boolean> implements MFBSoap.MFBSoapProgressUpdate {
        private ProgressDialog m_pd = null;
        private AsyncWeakContext<ActRecentsWS> m_ctxt;
        private LogbookEntry[] m_rgle;
        boolean m_fErrorsFound = false;
        boolean m_fFlightsPosted = false;

        SubmitPendingFlightsTask(Context c, ActRecentsWS arws, LogbookEntry[] rgle) {
            super();
            m_ctxt = new AsyncWeakContext<>(c, arws);
            m_rgle = rgle;
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            if (m_rgle == null || m_rgle.length == 0)
                return false;

            m_fErrorsFound = m_fFlightsPosted = false;

            Context c = m_ctxt.getContext();
            PostingOptions po = new PostingOptions();
            CommitFlightSvc cf = new CommitFlightSvc();
            cf.m_Progress = this;
            int cFlights = m_rgle.length;
            int iFlight = 1;
            String szFmtUploadProgress = c.getString(R.string.prgSavingFlights);
            for (LogbookEntry le : m_rgle) {
                String szStatus = String.format(szFmtUploadProgress, iFlight, cFlights);
                if (m_pd != null)
                    NotifyProgress((iFlight * 100) / cFlights, szStatus);
                iFlight++;
                le.SyncProperties();    // pull in the properties for the flight.
                if (cf.FCommitFlight(AuthToken.m_szAuthToken, le, po, c)) {
                    m_fFlightsPosted = true;
                    le.DeletePendingFlight();
                } else {
                    le.szError = cf.getLastError();
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

            if (arws == null)
                return;

            if (m_fFlightsPosted) {  // flight was added/updated, so invalidate stuff.

                MFBMain.invalidateCachedTotals();
                arws.refreshRecentFlights(true);
            }
            else if (m_fErrorsFound) {
                arws.m_rgLe = LogbookEntry.mergeFlightLists(LogbookEntry.getQueuedAndPendingFlights(), arws.m_rgExistingFlights.toArray(new LogbookEntry[arws.m_rgExistingFlights.size()]));
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
        private AsyncWeakContext<ActRecentsWS> m_ctxt;

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
            LogbookEntry[] rglePending = LogbookEntry.getQueuedAndPendingFlights();

            if (fClearCache)
                RecentFlightsSvc.ClearCachedFlights();
            final int cFlightsPageSize = 15;
            LogbookEntry[] rgle = m_rfSvc.RecentFlightsWithQueryAndOffset(AuthToken.m_szAuthToken, arws.currentQuery, arws.m_rgExistingFlights.size(), cFlightsPageSize, c);

            arws.fCouldBeMore = (rgle != null && rgle.length >= cFlightsPageSize);

            if (rgle != null) {
                arws.m_rgExistingFlights.addAll(Arrays.asList(rgle));
                arws.m_rgLe = LogbookEntry.mergeFlightLists(rglePending, arws.m_rgExistingFlights.toArray(new LogbookEntry[arws.m_rgExistingFlights.size()]));
            }
            return rgle != null;
        }

        protected void onPreExecute() {
        }

        protected void onPostExecute(Boolean b) {
            m_fIsRefreshing = false;
            Context c = m_ctxt.getContext();
            ActRecentsWS arws = m_ctxt.getCallingActivity();
            if (c == null || arws == null)  // can't do anything without a context
                return;

            arws.populateList();

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
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        MFBMain.registerNotifyDataChange(this);
        MFBMain.registerNotifyResetAll(this);

        Intent i = getActivity().getIntent();
        if (i != null) {
            Object o = i.getSerializableExtra(ActFlightQuery.QUERY_TO_EDIT);
            if (o != null)
                currentQuery = (FlightQuery) o;
        }

        fCouldBeMore = true;
        if (m_rgLe.length == 0)
            refreshRecentFlights(false);
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case ActFlightQuery.QUERY_REQUEST_CODE:
                if (resultCode == Activity.RESULT_OK) {
                    currentQuery = (FlightQuery) data.getSerializableExtra(ActFlightQuery.QUERY_TO_EDIT);
                }
            default:
                break;
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
            if (m_rgExistingFlights != null)
                m_rgExistingFlights.clear();
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
            this.m_rgLe = LogbookEntry.getQueuedAndPendingFlights();
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

        FlightAdapter fa = new FlightAdapter(getActivity(), R.layout.flightitem, m_rgLe);
        setListAdapter(fa);

        getListView().setSelectionFromTop(index, top);

        getListView().setOnItemClickListener((adapterView, view, position, l) -> {
            if (position < 0 || position >= m_rgLe.length)
                return;

            Intent i = new Intent(getActivity(), EditFlightActivity.class);
            i.putExtra(VIEWEXISTINGFLIGHTID, m_rgLe[position].idFlight);
            i.putExtra(VIEWEXISTINGFLIGHTLOCALID, m_rgLe[position].idLocalDB);
            startActivity(i);
        });
        if (ActRecentsWS.fShowFlightImages)
            new Thread(new LazyThumbnailLoader(m_rgLe, (FlightAdapter) this.getListAdapter())).start();

        LogbookEntry[] rglePending = LogbookEntry.getPendingFlights();
        if (rglePending != null && rglePending.length > 0 && MFBSoap.IsOnline(getContext())) {
            SubmitPendingFlightsTask spft = new SubmitPendingFlightsTask(getContext(), this, rglePending);
            spft.execute();
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.recentswsmenu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.refreshRecents:
                refreshRecentFlights(true);
                return true;
            case R.id.findFlights:
                Intent i = new Intent(getActivity(), FlightQueryActivity.class);
                i.putExtra(ActFlightQuery.QUERY_TO_EDIT, currentQuery);
                startActivityForResult(i, ActFlightQuery.QUERY_REQUEST_CODE);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void imgCompleted(MFBImageInfo sender) {
        ((FlightAdapter) this.getListAdapter()).notifyDataSetChanged();
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
