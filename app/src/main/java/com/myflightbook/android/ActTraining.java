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
package com.myflightbook.android;

import android.Manifest;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.myflightbook.android.webservices.AuthToken;
import com.myflightbook.android.webservices.MFBSoap;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.fragment.app.ListFragment;
import model.MFBConstants;
import model.MFBUtil;

public class ActTraining extends ListFragment implements OnItemClickListener {

    private static final String endorseItem = "endorse";
    private ActivityResultLauncher<String[]> mPermissionLauncher;

    static class TrainingItem {
        final int idTitle;
        final String szURLDest;

        TrainingItem(int TitleID, String DestinationURL) {
            idTitle = TitleID;
            szURLDest = DestinationURL;
        }
    }

    private final TrainingItem[] m_rgTrainingItems =
            {
                    new TrainingItem(R.string.lblStudents, "students"),
                    new TrainingItem(R.string.lblInstructors, "instructors"),
                    new TrainingItem(R.string.lblReqSigs, "reqsigs"),
                    new TrainingItem(R.string.lblEndorsements, endorseItem),
                    new TrainingItem(R.string.lbl8710, "8710"),
                    new TrainingItem(R.string.lblModelRollup, "ModelRollup"),
                    new TrainingItem(R.string.lblTimeRollup, "TimeRollup"),
                    new TrainingItem(R.string.lblAchievements, "badges"),
                    new TrainingItem(R.string.lblRatingsProgress, "progress")
            };


    private class TrainingAdapter extends ArrayAdapter<TrainingItem> {
        private final TrainingItem[] m_rgti;

        TrainingAdapter(Context c, TrainingItem[] rgti) {
            super(c, R.layout.trainingitem, rgti);
            m_rgti = rgti;
        }

        @Override
        public @NonNull View getView(int position, View convertView, @NonNull ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                LayoutInflater vi = (LayoutInflater) requireActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                assert vi != null;
                v = vi.inflate(R.layout.trainingitem, parent, false);
            }

            if (m_rgti == null)
                return v;

            TrainingItem ti = m_rgti[position];

            TextView tvti = v.findViewById(R.id.txtTrainingItem);
            tvti.setText(ActTraining.this.getString(ti.idTitle));

            return v;
        }
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        this.setHasOptionsMenu(false);

        mPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    if (result == null)
                        return;
                    boolean fAllGranted = true;
                    for (String sz : result.keySet()) {
                        Boolean b = result.get(sz);
                        fAllGranted = fAllGranted && (b != null && b);
                    }

                    // perform the actual click.
                    int position = lastPositionClicked;
                    lastPositionClicked = -1;   // clear this out now.
                    if (!fAllGranted || position < 0 || position > m_rgTrainingItems.length)
                        return;

                    String szDest = m_rgTrainingItems[position].szURLDest;
                    ActWebView.ViewURL(getActivity(), MFBConstants.AuthRedirWithParams("d=" + szDest, getContext()));
                });

        return inflater.inflate(R.layout.training, container, false);
    }

    // update the list if our array is null
    public void onResume() {
        super.onResume();
        populateList();
    }

    private void populateList() {
        TrainingAdapter ta = new TrainingAdapter(getActivity(), m_rgTrainingItems);
        setListAdapter(ta);
        getListView().setOnItemClickListener(this);
    }

    private int lastPositionClicked = -1;

    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        // TODO: IsOnline doesn't work from main thread.
        if (!AuthToken.FIsValid() || !MFBSoap.IsOnline(getContext())) {
            MFBUtil.Alert(this, getString(R.string.txtError), getString(R.string.errTrainingNotAvailable));
            return;
        }

        lastPositionClicked = position;

        boolean fNeedsWritePerm = (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q); // no need to request WRITE_EXTERNAL_STORAGE in 29 and later.

        // check for permissions and handle click there.
        mPermissionLauncher.launch(fNeedsWritePerm ?
                new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE} :
                new String[]{Manifest.permission.READ_EXTERNAL_STORAGE});
    }
}
