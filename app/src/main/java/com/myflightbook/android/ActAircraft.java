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

import Model.Aircraft;
import Model.LazyThumbnailLoader;
import Model.LazyThumbnailLoader.ThumbnailedItem;
import Model.MFBImageInfo;
import Model.MFBUtil;

public class ActAircraft extends ListFragment implements OnItemClickListener, MFBMain.Invalidatable {
    private AircraftRowItem[] m_aircraftRows = null;
    private Boolean m_fHasHeaders = false;

    public enum RowType {DATA_ITEM, HEADER_ITEM}

    protected class AircraftRowItem implements ThumbnailedItem {

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

    private class SoapTask extends AsyncTask<Void, Void, MFBSoap> {
        private ProgressDialog m_pd = null;
        Object m_Result = null;

        SoapTask() {
            super();
        }

        @Override
        protected MFBSoap doInBackground(Void... params) {
            AircraftSvc as = new AircraftSvc();
            m_Result = as.AircraftForUser(AuthToken.m_szAuthToken);
            return as;
        }

        protected void onPreExecute() {
            m_pd = MFBUtil.ShowProgress(ActAircraft.this, ActAircraft.this.getString(R.string.prgAircraft));
        }

        protected void onPostExecute(MFBSoap svc) {
            if (!isAdded() || getActivity().isFinishing())
                return;

            Aircraft[] rgac = (Aircraft[]) m_Result;
            if (rgac == null)
                MFBUtil.Alert(ActAircraft.this, getString(R.string.txtError), svc.getLastError());
            else {
                if (rgac.length == 0)
                    MFBUtil.Alert(ActAircraft.this, getString(R.string.txtError), getString(R.string.errNoAircraftFound));
                ArrayList<Aircraft> lstFavorite = new ArrayList<>();
                ArrayList<Aircraft> lstArchived = new ArrayList<>();
                for (Aircraft ac : rgac) {
                    if (ac.HideFromSelection)
                        lstArchived.add(ac);
                    else
                        lstFavorite.add(ac);
                }

                m_fHasHeaders = (lstArchived.size() > 0 && lstFavorite.size() > 0);

                ArrayList<AircraftRowItem> arRows = new ArrayList<>();

                if (m_fHasHeaders)
                    arRows.add(new AircraftRowItem(getString(R.string.lblFrequentlyUsedAircraft)));
                for (Aircraft ac : lstFavorite)
                    arRows.add(new AircraftRowItem(ac));
                if (m_fHasHeaders)
                    arRows.add(new AircraftRowItem(getString(R.string.lblArchivedAircraft)));
                for (Aircraft ac : lstArchived)
                    arRows.add(new AircraftRowItem(ac));

                m_aircraftRows = arRows.toArray(new AircraftRowItem[arRows.size()]);

                populateList();
            }

            try {
                m_pd.dismiss();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private class AircraftAdapter extends ArrayAdapter<AircraftRowItem> {
        AircraftAdapter(Context c, int rid, AircraftRowItem[] rgac) {
            super(c, rid, rgac == null ? new AircraftRowItem[0] : rgac);
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
                LayoutInflater vi = (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                int layoutID = (rt == RowType.HEADER_ITEM) ? R.layout.listviewsectionheader : R.layout.aircraft;
                v = vi.inflate(layoutID, parent, false);
            }

            if (m_aircraftRows == null || m_aircraftRows.length == 0)
                return v;

            if (rt == RowType.HEADER_ITEM) {
                TextView tvSectionHeader = (TextView) v.findViewById(R.id.lblTableRowSectionHeader);
                tvSectionHeader.setText(m_aircraftRows[position].title);
                return v;
            }

            Aircraft ac = m_aircraftRows[position].aircraftItem;

            TextView tvTail = (TextView) v.findViewById(R.id.txtTail);
            TextView tvModel = (TextView) v.findViewById(R.id.txtModel);
            TextView tvModelCommonName = (TextView) v.findViewById(R.id.txtCommonName);

            // Show the camera if the aircraft has images.
            ImageView imgCamera = (ImageView) v.findViewById(R.id.imgCamera);
            imgCamera.setVisibility(View.GONE);
            imgCamera.setImageBitmap(null);
            if (ac.HasImage()) {
                MFBImageInfo mfbii = ac.AircraftImages[0];
                Bitmap b = mfbii.bitmapFromThumb();
                if (b != null) {
                    imgCamera.setVisibility(View.VISIBLE);
                    imgCamera.setImageBitmap(b);
                }
            }

            int textColor = ac.HideFromSelection ? 0x88000000 : 0xFF000000;
            tvTail.setTextColor(textColor);
            tvModel.setTextColor(textColor);
            tvModelCommonName.setTextColor(textColor);

            tvTail.setText(ac.displayTailNumber());
            tvModel.setText(ac.ModelDescription);
            if (ac.ModelCommonName.length() > 0)
                tvModelCommonName.setText(String.format(" (%s)", ac.ModelCommonName.trim()));

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
    }

    public void onDestroy() {
        MFBMain.unregisterNotify(this);
        super.onDestroy();
    }

    // update the list if our array is null
    public void onResume() {
        super.onResume();
        if (AuthToken.FIsValid() && m_aircraftRows == null) {
            SoapTask st = new SoapTask();
            st.execute();
        } else
            populateList();
    }

    public void populateList() {
        if (m_aircraftRows == null)
            return;
        AircraftAdapter aa = new AircraftAdapter(getActivity(), R.layout.aircraft, m_aircraftRows);
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

    public void AddAircraft() {
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

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.menuRefreshAircraft: {
                AircraftSvc ac = new AircraftSvc();
                ac.FlushCache();
                SoapTask st = new SoapTask();
                st.execute();
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