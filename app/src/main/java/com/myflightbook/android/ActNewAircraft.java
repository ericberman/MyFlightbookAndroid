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
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.TextView;

import com.myflightbook.android.WebServices.AircraftSvc;
import com.myflightbook.android.WebServices.AuthToken;
import com.myflightbook.android.WebServices.MFBSoap;
import com.myflightbook.android.WebServices.MakesandModelsSvc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import Model.Aircraft;
import Model.CountryCode;
import Model.MFBConstants;
import Model.MFBImageInfo;
import Model.MFBImageInfo.PictureDestination;
import Model.MFBUtil;
import Model.MakesandModels;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ActNewAircraft extends ActMFBForm implements android.view.View.OnClickListener, OnItemSelectedListener, ActMFBForm.GallerySource {

    public static MakesandModels[] AvailableMakesAndModels = null;
    private Aircraft m_ac = new Aircraft();
    private String szTailLast = "";
    public final static String MODELFORAIRCRAFT = "com.myflightbook.android.aircraftModelID";
    private AutoCompleteAdapter autoCompleteAdapter;
    private boolean fNoTrigger = false; // true to suppress autosuggestions
    private ActivityResultLauncher<Intent> mSelectMakeLauncher = null;

    static class AutoCompleteAdapter extends ArrayAdapter<Aircraft> {
        private final List<Aircraft> mMatchingAircraft;

        @SuppressWarnings("SameParameterValue")
        AutoCompleteAdapter(@NonNull Context context, int resource) {
            super(context, resource);
            mMatchingAircraft = new ArrayList<>();
        }

        void setData(List<Aircraft> list) {
            mMatchingAircraft.clear();
            mMatchingAircraft.addAll(list);
        }

        @Override
        public int getCount() {
            return mMatchingAircraft.size();
        }

        @Nullable
        @Override
        public Aircraft getItem(int position) {
            return mMatchingAircraft.get(position);
        }

        Aircraft getObject(int position) {
            return mMatchingAircraft.get(position);
        }

        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {

            View v;
            if (convertView == null) {
                LayoutInflater inflater = (LayoutInflater) getContext()
                        .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                assert inflater != null;
                v = inflater.inflate(android.R.layout.simple_list_item_2, null);
            } else
                v = convertView;

            Aircraft ac = getObject(position);
            TextView tv = v.findViewById(android.R.id.text1);
            tv.setText(ac.displayTailNumber());
            tv.setTypeface(tv.getTypeface(), Typeface.BOLD);

            tv = v.findViewById(android.R.id.text2);
            MakesandModels mm;
            if (AvailableMakesAndModels == null || ((mm = MakesandModels.getMakeModelByID(ac.ModelID, AvailableMakesAndModels)) == null))
                tv.setText(ac.ModelDescription);
            else
                tv.setText(mm.Description);

            return v;
        }
    }

    private static class SuggestAircraftTask extends AsyncTask<Void, Void, MFBSoap> {
        Object m_Result = null;
        final String mPrefix;
        final AsyncWeakContext<ActNewAircraft> m_ctxt;

        SuggestAircraftTask(Context c, String szPrefix, ActNewAircraft ana) {
            super();
            mPrefix = szPrefix;
            m_ctxt = new AsyncWeakContext<>(c, ana);
        }

        @Override
        protected MFBSoap doInBackground(Void... params) {
            AircraftSvc as = new AircraftSvc();
            m_Result = as.AircraftForPrefix(AuthToken.m_szAuthToken, mPrefix, m_ctxt.getContext());
            return as;
        }

        protected void onPostExecute(MFBSoap svc) {
            ActNewAircraft aa = m_ctxt.getCallingActivity();
            if (aa == null || !aa.isAdded() || aa.isDetached() || aa.getActivity() == null || aa.autoCompleteAdapter == null)
                return;

            Aircraft[] rgac = (Aircraft[]) m_Result;
            if (rgac == null)
                rgac = new Aircraft[0];

            ArrayList<Aircraft> lst = new ArrayList<>(Arrays.asList(rgac));
            aa.autoCompleteAdapter.setData(lst);
            aa.autoCompleteAdapter.notifyDataSetChanged();
        }
    }

    private static class SaveAircraftTask extends AsyncTask<Aircraft, String, MFBSoap> implements MFBSoap.MFBSoapProgressUpdate {
        private ProgressDialog m_pd = null;
        Object m_Result = null;
        private final AsyncWeakContext<ActNewAircraft> m_ctxt;
        private final Aircraft mAc;

        SaveAircraftTask(Context c, Aircraft ac, ActNewAircraft ana) {
            super();
            mAc = ac;
            m_ctxt = new AsyncWeakContext<>(c, ana);
        }

        @Override
        protected MFBSoap doInBackground(Aircraft... params) {
            AircraftSvc acs = new AircraftSvc();
            acs.m_Progress = this;
            m_Result = acs.AddAircraft(AuthToken.m_szAuthToken, mAc, m_ctxt.getContext());
            return acs;
        }

        protected void onPreExecute() {
            m_pd = MFBUtil.ShowProgress(m_ctxt.getContext(), m_ctxt.getContext().getString(R.string.prgNewAircraft));
        }

        protected void onPostExecute(MFBSoap svc) {
            try {
                if (m_pd != null)
                    m_pd.dismiss();
            } catch (Exception e) {
                Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e));
            }

            ActNewAircraft ana = m_ctxt.getCallingActivity();

            if (ana == null || !ana.isAdded() || ana.isDetached() || ana.getActivity() == null)
                return;

            Aircraft[] rgac = (Aircraft[]) m_Result;
            if (rgac == null || rgac.length == 0)
                MFBUtil.Alert(ana, ana.getString(R.string.txtError), svc.getLastError());
            else
                ana.Dismiss();
        }

        protected void onProgressUpdate(String... msg) {
            m_pd.setMessage(msg[0]);
        }

        public void NotifyProgress(int percentageComplete, String szMsg) {
            this.publishProgress(szMsg);
        }
    }

    private static class GetMakesTask extends AsyncTask<Void, Void, MFBSoap> {
        private ProgressDialog m_pd = null;
        private final AsyncWeakContext<ActNewAircraft> m_ctxt;

        GetMakesTask(Context c, ActNewAircraft ana) {
            super();
            m_ctxt = new AsyncWeakContext<>(c, ana);
        }

        @Override
        protected MFBSoap doInBackground(Void... params) {
            MakesandModelsSvc mms = new MakesandModelsSvc();
            AvailableMakesAndModels = mms.GetMakesAndModels(m_ctxt.getContext());

            return mms;
        }

        protected void onPreExecute() {
            m_pd = MFBUtil.ShowProgress(m_ctxt.getContext(), m_ctxt.getContext().getString(R.string.prgMakes));
        }

        protected void onPostExecute(MFBSoap svc) {
            try {
                if (m_pd != null)
                    m_pd.dismiss();
            } catch (Exception e) {
                Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e));
            }

            ActNewAircraft ana = m_ctxt.getCallingActivity();
            Context c = m_ctxt.getContext();

            if (ana == null || c == null)
                return;

            if (AvailableMakesAndModels == null || AvailableMakesAndModels.length == 0) {
                MFBUtil.Alert(c, c.getString(R.string.txtError), c.getString(R.string.errCannotRetrieveMakes));
                ana.Cancel();
            }
        }
    }

    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        this.setHasOptionsMenu(true);
        return inflater.inflate(R.layout.newaircraft, container, false);
    }

    @Override
    public void onViewCreated (@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (!AuthToken.FIsValid()) {
            MFBUtil.Alert(this, getString(R.string.errCannotAddAircraft), getString(R.string.errMustBeSignedInToCreateAircraft));
            Cancel();
            return;
        }

        mSelectMakeLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        int selectedMakeIndex = Objects.requireNonNull(result.getData()).getIntExtra(MODELFORAIRCRAFT, 0);
                        if (AvailableMakesAndModels != null && AvailableMakesAndModels.length > selectedMakeIndex)
                            setCurrentMakeModel(AvailableMakesAndModels[selectedMakeIndex]);
                    }
                });

        // Give the aircraft a tailnumber based on locale
        m_ac.TailNumber = CountryCode.BestGuessForCurrentLocale().Prefix;

        findViewById(R.id.btnMakeModel).setOnClickListener(this);
        findViewById(R.id.ckAnonymous).setOnClickListener(this);

        String[] rgszInstanceTypes = new String[Aircraft.rgidInstanceTypes.length];
        for (int i = 0; i < Aircraft.rgidInstanceTypes.length; i++)
            rgszInstanceTypes[i] = getString(Aircraft.rgidInstanceTypes[i]);

        Spinner sp = (Spinner) findViewById(R.id.spnAircraftType);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireActivity(), R.layout.mfbsimpletextitem, rgszInstanceTypes);
        sp.setAdapter(adapter);
        sp.setSelection(0);
        sp.setOnItemSelectedListener(this);

        // Autocompletion based on code at https://www.truiton.com/2018/06/android-autocompletetextview-suggestions-from-webservice-call/
        final ActNewAircraft ana = this;
        AutoCompleteTextView act = (AutoCompleteTextView) findViewById(R.id.txtTail);
        act.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_CLASS_TEXT);
        act.setThreshold(3);
        autoCompleteAdapter = new AutoCompleteAdapter(requireContext(), android.R.layout.simple_list_item_2);
        final AutoCompleteAdapter aca = autoCompleteAdapter;
        act.setAdapter(autoCompleteAdapter);
        act.setOnItemClickListener(
                (parent, v, position, id) -> {
                    m_ac = aca.getObject(position);
                    setCurrentMakeModel(MakesandModels.getMakeModelByID(m_ac.ModelID, AvailableMakesAndModels));
                    toView();
                });

        act.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                autoCompleteAdapter.notifyDataSetChanged();
                if (!fNoTrigger) {
                    String szTail = act.getText().toString();
                    if (szTail.length() > 2) {
                        SuggestAircraftTask sat = new SuggestAircraftTask(getContext(), szTail, ana);
                        sat.execute();
                    }
                }
            }

            @Override
            public void afterTextChanged(Editable s) { }
        });

        // Get available makes/models, but only if we have none.  Can refresh.
        // This avoids getting makes/models when just getting a picture.
        if (AvailableMakesAndModels == null || AvailableMakesAndModels.length == 0) {
            GetMakesTask gt = new GetMakesTask(this.getActivity(), this);
            gt.execute();
        }

        toView();
    }

    public void onPause() {
        super.onPause();
        fromView();
    }

    public void onResume() {
        super.onResume();
        toView();
    }

    private void Cancel() {
        Intent i = new Intent();
        requireActivity().setResult(Activity.RESULT_CANCELED, i);
        requireActivity().finish();
    }

    private void newAircraft() {
        m_ac = new Aircraft();

        // Clean up any pending image turds that could be lying around
        MFBImageInfo mfbii = new MFBImageInfo(MFBImageInfo.PictureDestination.AircraftImage);
        mfbii.DeletePendingImages(m_ac.AircraftID);

        // Give the aircraft a tailnumber based on locale
        m_ac.TailNumber = CountryCode.BestGuessForCurrentLocale().Prefix;

        toView();
    }

    private void Dismiss() {
        Intent i = new Intent();
        Activity a = getActivity();
        if (a != null) {
            a.setResult(Activity.RESULT_OK, i);
            a.finish();
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            switch (requestCode) {
                case SELECT_IMAGE_ACTIVITY_REQUEST_CODE:
                    AddGalleryImage(data);
                    break;
                case CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE:
                    AddCameraImage(m_TempFilePath, false);
                    break;
            }
        }
    }

    private void setCurrentMakeModel(MakesandModels mm) {
        if (mm != null) {
            m_ac.ModelID = mm.ModelId;
            Button b = (Button) findViewById(R.id.btnMakeModel);
            b.setText(mm.Description);
        }
    }

    private void fromView() {
        m_ac.TailNumber = ((AutoCompleteTextView) findViewById(R.id.txtTail)).getText().toString().toUpperCase(Locale.getDefault());
        m_ac.InstanceTypeID = ((Spinner) findViewById(R.id.spnAircraftType)).getSelectedItemPosition() + 1;

        if (m_ac.InstanceTypeID > 1) // not a real aircraft - auto-assign a tail
            m_ac.TailNumber = "SIM";
    }

    private void toView() {
        AutoCompleteTextView et = (AutoCompleteTextView) findViewById(R.id.txtTail);
        // don't trigger autosuggest if the user isn't typing.
        fNoTrigger = true;
        et.setText(m_ac.TailNumber);
        fNoTrigger = false;
        et.setSelection(m_ac.TailNumber.length());
        ((Spinner) findViewById(R.id.spnAircraftType)).setSelection(m_ac.InstanceTypeID - 1);
        setCurrentMakeModel(MakesandModels.getMakeModelByID(m_ac.ModelID, AvailableMakesAndModels));

        findViewById(R.id.tblrowTailnumber).setVisibility(m_ac.IsAnonymous() || !m_ac.IsReal() ? View.GONE : View.VISIBLE);
        findViewById(R.id.tblrowIsAnonymous).setVisibility(m_ac.IsReal() ? View.VISIBLE : View.GONE);
        refreshGallery();
    }

    private void saveLastTail() {
        if (m_ac.IsReal() && !m_ac.IsAnonymous())
            this.szTailLast = ((AutoCompleteTextView) findViewById(R.id.txtTail)).getText().toString().toUpperCase(Locale.getDefault());
    }

    private void toggleAnonymous(CheckBox sender) {
        saveLastTail();
        m_ac.TailNumber = (m_ac.IsAnonymous()) ? this.szTailLast : m_ac.anonTailNumber();
        sender.setChecked(m_ac.IsAnonymous());
        toView();
    }

    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.btnMakeModel) {
            Intent i = new Intent(getActivity(), ActSelectMake.class);
            i.putExtra(MODELFORAIRCRAFT, m_ac.ModelID);
            mSelectMakeLauncher.launch(i);
        } else if (id == R.id.ckAnonymous)
            toggleAnonymous((CheckBox) v);
    }

    public void onItemSelected(AdapterView<?> arg0, View arg1, int arg2,
                               long arg3) {
        saveLastTail();
        Spinner sp = (Spinner) findViewById(R.id.spnAircraftType);
        m_ac.InstanceTypeID = sp.getSelectedItemPosition() + 1;
        toView();
    }

    public void onNothingSelected(AdapterView<?> arg0) {

    }

    // @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.newaircraft, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        int id = item.getItemId();
        if (id == R.id.menuChoosePicture)
            ChoosePicture();
        else if (id == R.id.menuTakePicture)
            TakePicture();
        else if (id == R.id.menuAddAircraft) {
            fromView();

            if (m_ac.FIsValid(getContext())) {
                SaveAircraftTask st = new SaveAircraftTask(getActivity(), m_ac, this);
                st.execute(m_ac);
            } else
                MFBUtil.Alert(this, getString(R.string.txtError), m_ac.ErrorString);
        } else
            return super.onOptionsItemSelected(item);
        return true;
    }

    @Override
    public void onCreateContextMenu(@NonNull ContextMenu menu, @NonNull View v, ContextMenuInfo menuInfo) {
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

    /*
     * GallerySource methods
     * (non-Javadoc)
     * @see com.myflightbook.android.ActMFBForm.GallerySource#getGalleryID()
     */
    public int getGalleryID() {
        return R.id.tblImageTable;
    }

    public View getGalleryHeader() {
        return null;
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
