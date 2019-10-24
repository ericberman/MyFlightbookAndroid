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
package com.myflightbook.android;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ListFragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.myflightbook.android.WebServices.AircraftSvc;
import com.myflightbook.android.WebServices.AuthToken;
import com.myflightbook.android.WebServices.MFBSoap;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;

import Model.Aircraft;
import Model.LazyThumbnailLoader;
import Model.LazyThumbnailLoader.ThumbnailedItem;
import Model.MFBConstants;
import Model.MFBImageInfo;
import Model.MFBUtil;

public class ActAircraft extends ListFragment implements OnItemClickListener, MFBMain.Invalidatable {
    private AircraftRowItem[] m_aircraftRows = null;
    private Boolean m_fHasHeaders = false;

    private enum RowType {DATA_ITEM, HEADER_ITEM}

    static class AircraftRowItem implements ThumbnailedItem {

        Aircraft aircraftItem = null;
        String title = null;
        RowType rowType = RowType.DATA_ITEM;

        AircraftRowItem(Aircraft obj) {
            aircraftItem = obj;
        }

        AircraftRowItem(String szTitle) {
            rowType = RowType.HEADER_ITEM;
            title = szTitle;
        }

        public MFBImageInfo getDefaultImage() {
            return (rowType == RowType.HEADER_ITEM) ? null : aircraftItem.getDefaultImage();
        }
    }

    private static class RefreshAircraftTask extends AsyncTask<Void, Void, MFBSoap> {
        private ProgressDialog m_pd = null;
        Object m_Result = null;
        final AsyncWeakContext<ActAircraft> m_ctxt;

        RefreshAircraftTask(Context c, ActAircraft aa) {
            super();
            m_ctxt = new AsyncWeakContext<>(c, aa);
        }

        @Override
        protected MFBSoap doInBackground(Void... params) {
            AircraftSvc as = new AircraftSvc();
            m_Result = as.AircraftForUser(AuthToken.m_szAuthToken, m_ctxt.getContext());
            return as;
        }

        protected void onPreExecute() {
            m_pd = MFBUtil.ShowProgress(m_ctxt.getContext(), m_ctxt.getContext().getString(R.string.prgAircraft));
        }

        protected void onPostExecute(MFBSoap svc) {
            try {
                if (m_pd != null)
                    m_pd.dismiss();
            } catch (Exception e) {
                Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e));
            }

            ActAircraft aa = m_ctxt.getCallingActivity();
            if (aa == null || !aa.isAdded() || aa.isDetached() || aa.getActivity() == null)
                return;

            Aircraft[] rgac = (Aircraft[]) m_Result;
            if (rgac == null)
                MFBUtil.Alert(aa, aa.getString(R.string.txtError), svc.getLastError());
            else {
                if (rgac.length == 0)
                    MFBUtil.Alert(aa, aa.getString(R.string.txtError), aa.getString(R.string.errNoAircraftFound));
                ArrayList<Aircraft> lstFavorite = new ArrayList<>();
                ArrayList<Aircraft> lstArchived = new ArrayList<>();
                for (Aircraft ac : rgac) {
                    if (ac.HideFromSelection)
                        lstArchived.add(ac);
                    else
                        lstFavorite.add(ac);
                }

                aa.m_fHasHeaders = (lstArchived.size() > 0 && lstFavorite.size() > 0);

                ArrayList<AircraftRowItem> arRows = new ArrayList<>();

                if (aa.m_fHasHeaders)
                    arRows.add(new AircraftRowItem(aa.getString(R.string.lblFrequentlyUsedAircraft)));
                for (Aircraft ac : lstFavorite)
                    arRows.add(new AircraftRowItem(ac));
                if (aa.m_fHasHeaders)
                    arRows.add(new AircraftRowItem(aa.getString(R.string.lblArchivedAircraft)));
                for (Aircraft ac : lstArchived)
                    arRows.add(new AircraftRowItem(ac));

                aa.m_aircraftRows = arRows.toArray(new AircraftRowItem[0]);

                aa.populateList();
            }
        }
    }

    private class AircraftAdapter extends ArrayAdapter<AircraftRowItem> {
        AircraftAdapter(Context c, AircraftRowItem[] rgac) {
            super(c, R.layout.aircraft, rgac == null ? new AircraftRowItem[0] : rgac);
        }

        @Override
        public int getViewTypeCount() {
            return m_fHasHeaders ? 2 : 1;
        }

        @Override
        public int getItemViewType(int position) {
            if (m_aircraftRows == null || m_aircraftRows.length == 0)
                return RowType.DATA_ITEM.ordinal();

            return m_aircraftRows[position].rowType.ordinal();
        }

        @Override
        public
        @NonNull
        View getView(int position, @Nullable View v, @NonNull ViewGroup parent) {
            RowType rt = RowType.values()[getItemViewType(position)];

            if (v == null) {
                LayoutInflater vi = (LayoutInflater) Objects.requireNonNull(getActivity()).getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                int layoutID = (rt == RowType.HEADER_ITEM) ? R.layout.listviewsectionheader : R.layout.aircraft;
                assert vi != null;
                v = vi.inflate(layoutID, parent, false);
            }

            if (m_aircraftRows == null || m_aircraftRows.length == 0)
                return v;

            if (rt == RowType.HEADER_ITEM) {
                TextView tvSectionHeader = v.findViewById(R.id.lblTableRowSectionHeader);
                tvSectionHeader.setText(m_aircraftRows[position].title);
                return v;
            }

            Aircraft ac = m_aircraftRows[position].aircraftItem;

            TextView tvTail = v.findViewById(R.id.txtAircraftDetails);

            // Show the camera if the aircraft has images.
            ImageView imgCamera = v.findViewById(R.id.imgCamera);
            if (ac.HasImage()) {
                MFBImageInfo mfbii = ac.AircraftImages[0];
                Bitmap b = mfbii.bitmapFromThumb();
                if (b != null) {
                    imgCamera.setImageBitmap(b);
                }
            } else {
                imgCamera.setImageResource(R.drawable.noimage);
            }

            int textColor = (tvTail.getCurrentTextColor() & 0x00FFFFFF) | (ac.HideFromSelection ? 0x88000000 : 0xFF000000);
            tvTail.setTextColor(textColor);

            String szInstanceType = " " + (ac.IsReal() ? "" : getString(Aircraft.rgidInstanceTypes[ac.InstanceTypeID - 1]));

            String szAircraftDetails = String.format(Locale.getDefault(), "<big><b>%s</b></big> <i>%s</i><br />%s %s", ac.displayTailNumber(), (ac.ModelDescription + " " + ac.ModelCommonName).trim() + szInstanceType, ac.PrivateNotes, ac.PublicNotes);
            tvTail.setText(Html.fromHtml(szAircraftDetails));

            return v;
        }
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        this.setHasOptionsMenu(true);
        return inflater.inflate(R.layout.aircraftlist, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        MFBMain.registerNotifyResetAll(this);

        SwipeRefreshLayout srl = Objects.requireNonNull(getView()).findViewById(R.id.swiperefresh);
        srl.setOnRefreshListener(() -> {
            srl.setRefreshing(false);
            refreshAircraft();
        });
    }

    public void onDestroy() {
        MFBMain.unregisterNotify(this);
        super.onDestroy();
    }

    // update the list if our array is null
    public void onResume() {
        super.onResume();
        if (AuthToken.FIsValid() && m_aircraftRows == null) {
            RefreshAircraftTask st = new RefreshAircraftTask(getActivity(), this);
            st.execute();
        } else
            populateList();
    }

    private void populateList() {
        if (m_aircraftRows == null)
            return;
        Activity a = getActivity();
        if (a == null)
            return;
        AircraftAdapter aa = new AircraftAdapter(a, m_aircraftRows);
        setListAdapter(aa);
        getListView().setOnItemClickListener(this);
        new Thread(new LazyThumbnailLoader(m_aircraftRows, aa)).start();
    }

    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (m_aircraftRows == null || position < 0 || position >= m_aircraftRows.length || m_aircraftRows[position].rowType == RowType.HEADER_ITEM)
            return;

        Intent i = new Intent(getActivity(), EditAircraftActivity.class);
        Aircraft ac = m_aircraftRows[position].aircraftItem;
        i.putExtra(ActEditAircraft.AIRCRAFTID, ac.AircraftID);
        startActivityForResult(i, ActEditAircraft.BEGIN_EDIT_AIRCRAFT_REQUEST_CODE);
    }

    private void AddAircraft() {
        Intent i = new Intent(getActivity(), NewAircraftActivity.class);
        startActivityForResult(i, ActNewAircraft.BEGIN_NEW_AIRCRAFT_REQUEST_CODE);
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK && resultCode != Activity.RESULT_CANCELED) {
            invalidate();
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.aircraftmenu, menu);
    }

    private void refreshAircraft() {
        AircraftSvc ac = new AircraftSvc();
        ac.FlushCache();
        RefreshAircraftTask st = new RefreshAircraftTask(getActivity(), this);
        st.execute();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.menuRefreshAircraft: {
                refreshAircraft();
            }
            return true;
            case R.id.menuNewAircraft:
                AddAircraft();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void invalidate() {
        new AircraftSvc().FlushCache();
        m_aircraftRows = null;
        AircraftAdapter aa = (AircraftAdapter) this.getListAdapter();
        if (aa != null)
            aa.notifyDataSetChanged();
    }
}