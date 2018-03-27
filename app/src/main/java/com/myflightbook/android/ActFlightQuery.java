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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.RadioButton;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.myflightbook.android.WebServices.AircraftSvc;
import com.myflightbook.android.WebServices.AuthToken;
import com.myflightbook.android.WebServices.CannedQuerySvc;
import com.myflightbook.android.WebServices.CustomPropertyTypesSvc;
import com.myflightbook.android.WebServices.MFBSoap;
import com.myflightbook.android.WebServices.RecentFlightsSvc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import Model.Aircraft;
import Model.Airport;
import Model.CannedQuery;
import Model.CategoryClass;
import Model.CustomPropertyType;
import Model.FlightQuery;
import Model.FlightQuery.DateRanges;
import Model.MFBUtil;
import Model.MakeModel;

public class ActFlightQuery extends ActMFBForm implements android.view.View.OnClickListener, DlgDatePicker.DateTimeUpdate {
    private FlightQuery CurrentQuery = null;
    private Aircraft[] m_rgac = null;
    private Aircraft[] m_rgacAll = null;
    private MakeModel[] m_rgmm = null;
    private Boolean fShowAllAircraft = false;
    private Boolean fCannedQueryClicked = false;
    public static final int QUERY_REQUEST_CODE = 50382;
    public static final String QUERY_TO_EDIT = "com.myflightbook.android.querytoedit";

    @SuppressLint("StaticFieldLeak")
    private class GetCannedQueryTask extends AsyncTask<Void, Void, MFBSoap> {
        private Context m_Context = null;

        GetCannedQueryTask(Context c) {
            super();
            m_Context = c;
        }

        @Override
        protected MFBSoap doInBackground(Void... params) {
            CannedQuerySvc cqSVC = new CannedQuerySvc();
            CannedQuery.setCannedQueries(cqSVC.GetNamedQueriesForUser(AuthToken.m_szAuthToken, m_Context));
            return cqSVC;
        }

        protected void onPreExecute() {
        }

        protected void onPostExecute(MFBSoap svc) {
            if (svc != null && svc.getLastError().length() == 0)
                ActFlightQuery.this.setUpNamedQueries();
        }
    }

    @SuppressLint("StaticFieldLeak")
    private class AddCannedQueryTask extends AsyncTask<Void, Void, MFBSoap> {
        private Context m_Context = null;
        private String m_name = null;
        private FlightQuery m_fq = null;

        AddCannedQueryTask(Context c, FlightQuery fq, String szName) {
            super();
            m_Context = c;
            m_fq = fq;
            m_name = szName;
        }

        @Override
        protected MFBSoap doInBackground(Void... params) {
            CannedQuerySvc cqSVC = new CannedQuerySvc();
            CannedQuery.setCannedQueries(cqSVC.AddNamedQueryForUser(AuthToken.m_szAuthToken, m_name, m_fq, m_Context));
            return cqSVC;
        }

        protected void onPreExecute() {
        }

        protected void onPostExecute(MFBSoap svc) {
        }
    }

    @SuppressLint("StaticFieldLeak")
    private class DeleteCannedQueryTask extends AsyncTask<Void, Void, MFBSoap> {
        private Context m_Context = null;
        private CannedQuery m_fq = null;

        DeleteCannedQueryTask(Context c, CannedQuery fq) {
            super();
            m_Context = c;
            m_fq = fq;
        }

        @Override
        protected MFBSoap doInBackground(Void... params) {
            CannedQuerySvc cqSVC = new CannedQuerySvc();
            CannedQuery.setCannedQueries(cqSVC.DeleteNamedQueryForUser(AuthToken.m_szAuthToken, m_fq, m_Context));
            return cqSVC;
        }

        protected void onPreExecute() {
        }

        protected void onPostExecute(MFBSoap svc) {
            if (svc != null) {
                if (svc.getLastError().length() == 0)
                    ActFlightQuery.this.setUpNamedQueries();
                else
                    MFBUtil.Alert(m_Context, m_Context.getString(R.string.txtError), svc.getLastError());
            }
        }
    }
    // We are going to return only active aircraft UNLESS:
    // a) fShowAllAircraft is true
    // b) all aircraft are active, or
    // c) the query references an inactive aircraft.
    // if b or c is true, we will set (a) to true.
    protected Aircraft[] GetCurrentAircraft() {
        if (m_rgac == null)
            m_rgac = m_rgacAll = new AircraftSvc().getCachedAircraft();

        if (fShowAllAircraft)
            m_rgac = m_rgacAll;
        else
        {
            ArrayList<Aircraft> lst = new ArrayList<>();
            for (Aircraft ac : m_rgac)
                if (!ac.HideFromSelection)
                    lst.add(ac);

            if (lst.size() == m_rgac.length)
                fShowAllAircraft = true;
            else if (CurrentQuery != null && CurrentQuery.AircraftList != null) {
                for (Aircraft ac : CurrentQuery.AircraftList)
                    if (ac.HideFromSelection)
                        fShowAllAircraft = true;
                if (!fShowAllAircraft)
                    m_rgac = lst.toArray(new Aircraft[0]);
            }
        }
        return m_rgac;
    }

    protected MakeModel[] GetActiveMakes() {
        if (m_rgmm == null) {
            GetCurrentAircraft();
            Map<String, MakeModel> htmm = new HashMap<>();
            for (Aircraft ac : m_rgacAll) {
                if (!htmm.containsKey(ac.ModelDescription)) {
                    MakeModel mm = new MakeModel();
                    mm.MakeModelId = ac.ModelID;
                    mm.Description = ac.ModelDescription;
                    htmm.put(String.format(Locale.US, "%d", mm.MakeModelId), mm);
                }
            }

            ArrayList<MakeModel> almm = new ArrayList<>();
            for (String key : htmm.keySet())
                almm.add(htmm.get(key));

            Collections.sort(almm);
            m_rgmm = almm.toArray(new MakeModel[0]);
        }
        return m_rgmm;
    }

    public FlightQuery getCurrentQuery() {
        fromForm();
        return CurrentQuery;
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        this.setHasOptionsMenu(true);
        return inflater.inflate(R.layout.flightquery, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        Intent i = getActivity().getIntent();
        CurrentQuery = (FlightQuery) i.getSerializableExtra(QUERY_TO_EDIT);
        if (CurrentQuery == null)
            CurrentQuery = new FlightQuery();

        if (CannedQuery.getCannedQueries() == null)
            new GetCannedQueryTask(getActivity()).execute();
        else
            setUpNamedQueries();

        AddListener(R.id.btnfqDateStart);
        AddListener(R.id.btnfqDateEnd);

        AddListener(R.id.rbAlltime);
        AddListener(R.id.rbCustom);
        AddListener(R.id.rbPreviousMonth);
        AddListener(R.id.rbPreviousYear);
        AddListener(R.id.rbThisMonth);
        AddListener(R.id.rbTrailing12);
        AddListener(R.id.rbTrailing6);
        AddListener(R.id.rbTrailing30);
        AddListener(R.id.rbTrailing90);
        AddListener(R.id.rbYTD);

        AddListener(R.id.rbAllEngines);
        AddListener(R.id.rbEngineJet);
        AddListener(R.id.rbEnginePiston);
        AddListener(R.id.rbEngineTurbine);
        AddListener(R.id.rbEngineTurboprop);
        AddListener(R.id.rbEngineElectric);

        AddListener(R.id.rbInstanceAny);
        AddListener(R.id.rbInstanceReal);
        AddListener(R.id.rbInstanceTraining);

        AddListener(R.id.rbDistanceAny);
        AddListener(R.id.rbDistanceLocal);
        AddListener(R.id.rbDistanceNonlocal);

        // Expand/collapse
        AddListener(R.id.txtFQDatesHeader);
        AddListener(R.id.txtFQAirportsHeader);
        AddListener(R.id.txtFQACFeatures);
        AddListener(R.id.txtFQFlightFeatures);
        AddListener(R.id.txtFQAircraftHeader);
        AddListener(R.id.txtFQModelsHeader);
        AddListener(R.id.txtFQCatClassHeader);
        AddListener(R.id.txtFQPropsHeader);
        AddListener(R.id.txtFQNamedQueryHeader);

        setUpChecklists();

        setExpandCollapseState();
    }

    protected void setExpandCollapseState() {
        setExpandedState((TextView) findViewById(R.id.txtFQDatesHeader), findViewById(R.id.sectFQDates), CurrentQuery.DateRange != DateRanges.AllTime);
        setExpandedState((TextView) findViewById(R.id.txtFQAirportsHeader), findViewById(R.id.tblFQAirports), CurrentQuery.AirportList.length > 0 || CurrentQuery.Distance != FlightQuery.FlightDistance.AllFlights);
        setExpandedState((TextView) findViewById(R.id.txtFQACFeatures), findViewById(R.id.sectFQAircraftFeatures), CurrentQuery.HasAircraftCriteria());
        setExpandedState((TextView) findViewById(R.id.txtFQFlightFeatures), findViewById(R.id.sectFQFlightFeatures), CurrentQuery.HasFlightCriteria());
        setExpandedState((TextView) findViewById(R.id.txtFQAircraftHeader), findViewById(R.id.llfqAircraft), CurrentQuery.AircraftList.length >  0);
        setExpandedState((TextView) findViewById(R.id.txtFQModelsHeader), findViewById(R.id.sectFQModels), CurrentQuery.MakeList.length > 0 || CurrentQuery.ModelName.length() > 0);
        setExpandedState((TextView) findViewById(R.id.txtFQCatClassHeader), findViewById(R.id.tblFQCatClass), CurrentQuery.CatClassList.length > 0);
        setExpandedState((TextView) findViewById(R.id.txtFQPropsHeader), findViewById(R.id.tblFQProps), CurrentQuery.PropertyTypes.length > 0);
        setExpandedState((TextView) findViewById(R.id.txtFQNamedQueryHeader), findViewById(R.id.sectFQNamedQueries), CannedQuery.getCannedQueries() != null && CannedQuery.getCannedQueries().length > 0);

        Button btnShowAll = (Button) findViewById(R.id.btnShowAllAircraft);
        btnShowAll.setOnClickListener(view -> {
            fromForm();
            fShowAllAircraft = true;
            setUpAircraftChecklist();
            toForm();
        });
    }

    @Override
    public void onPause() {
        super.onPause();
        if (!fCannedQueryClicked)
            fromForm();
        ActTotals.SetNeedsRefresh(true);
        RecentFlightsSvc.ClearCachedFlights();

        String szQueryName = StringFromField(R.id.txtNameForQuery);
        if (CurrentQuery.HasCriteria() && szQueryName.length() >0)
            new AddCannedQueryTask(getActivity(), CurrentQuery, szQueryName).execute();

        // in case things change before next time, clear out the cached arrays.
        m_rgac = null;
        m_rgmm = null;
    }

    // region Dynamic checklists - aircraft, models, category class, and properties
    interface CheckedTableListener {
        // called when a checkbox item is changed. fAdded is true if it's added, otherwise it is removed.
        void itemStateChanged(Object o, boolean fAdded);

        // Tests if the object shoud initially be selected
        boolean itemIsChecked(Object o);
    }

    protected void setUpDynamicCheckList(int idTable, Object[] rgItems, CheckedTableListener listener) {
        TableLayout tl = (TableLayout) findViewById(idTable);
        if (tl == null)
            return;
        tl.removeAllViews();

        LayoutInflater l = getActivity().getLayoutInflater();

        assert listener != null;

        if (rgItems == null)
            rgItems = new Object[0];

        for (Object o : rgItems) {
            TableRow tr = (TableRow) l.inflate(R.layout.checkboxtableitem, tl, false);

            final Object oFinal = o;
            CheckBox ck = tr.findViewById(R.id.checkbox);
            ck.setText(o.toString());
            ck.setChecked(listener.itemIsChecked(o));
            ck.setOnCheckedChangeListener((compoundButton, fChecked) -> listener.itemStateChanged(oFinal, fChecked));
            tl.addView(tr, new TableLayout.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT));
        }
    }

    protected void setUpAircraftChecklist() {
        setUpDynamicCheckList(R.id.tblFQAircraft, GetCurrentAircraft(), new CheckedTableListener() {
            @Override
            public void itemStateChanged(Object o, boolean fAdded) {
                List<Aircraft> lst = new ArrayList<>(Arrays.asList(CurrentQuery.AircraftList));
                if (fAdded)
                    lst.add((Aircraft) o);
                else {
                    Iterator<Aircraft> iterator = lst.iterator();
                    while (iterator.hasNext()) {
                        Aircraft ac = iterator.next();
                        if (ac.AircraftID == ((Aircraft) o).AircraftID)
                            iterator.remove();
                    }
                }
                CurrentQuery.AircraftList = lst.toArray(new Aircraft[0]);
            }

            @Override
            public boolean itemIsChecked(Object o) {
                for (Aircraft ac : CurrentQuery.AircraftList)
                    if (ac.AircraftID == ((Aircraft) o).AircraftID)
                        return true;
                return false;
            }
        });
        findViewById(R.id.btnShowAllAircraft).setVisibility(fShowAllAircraft ? View.GONE : View.VISIBLE);
    }

    protected void setUpChecklists() {
        setUpAircraftChecklist();

        setUpDynamicCheckList(R.id.tblFQModels, GetActiveMakes(), new CheckedTableListener() {
            @Override
            public void itemStateChanged(Object o, boolean fAdded) {
                List<MakeModel> lst = new ArrayList<>(Arrays.asList(CurrentQuery.MakeList));
                if (fAdded)
                    lst.add((MakeModel) o);
                else {
                    Iterator<MakeModel> iterator = lst.iterator();
                    while (iterator.hasNext()) {
                        MakeModel m = iterator.next();
                        if (m.MakeModelId == ((MakeModel) o).MakeModelId)
                            iterator.remove();
                    }
                }
                CurrentQuery.MakeList = lst.toArray(new MakeModel[0]);
            }

            @Override
            public boolean itemIsChecked(Object o) {
                for (MakeModel m : CurrentQuery.MakeList)
                    if (m.MakeModelId == ((MakeModel) o).MakeModelId)
                        return true;
                return false;
            }
        });

        setUpDynamicCheckList(R.id.tblFQCatClass, CategoryClass.AllCatClasses(), new CheckedTableListener() {
            @Override
            public void itemStateChanged(Object o, boolean fAdded) {
                List<CategoryClass> lst = new ArrayList<>(Arrays.asList(CurrentQuery.CatClassList));
                if (fAdded)
                    lst.add((CategoryClass) o);
                else {
                    Iterator<CategoryClass> iterator = lst.iterator();
                    while (iterator.hasNext()) {
                        CategoryClass cc = iterator.next();
                        if (cc.IdCatClass == ((CategoryClass) o).IdCatClass)
                            iterator.remove();
                    }
                }
                CurrentQuery.CatClassList = lst.toArray(new CategoryClass[0]);
            }

            @Override
            public boolean itemIsChecked(Object o) {
                for (CategoryClass cc : CurrentQuery.CatClassList)
                    if (cc.IdCatClass == ((CategoryClass) o).IdCatClass)
                        return true;
                return false;
            }
        });

        setUpDynamicCheckList(R.id.tblFQProps, CustomPropertyTypesSvc.getSearchableProperties(), new CheckedTableListener() {
            @Override
            public void itemStateChanged(Object o, boolean fAdded) {
                List<CustomPropertyType> lst = new ArrayList<>(Arrays.asList(CurrentQuery.PropertyTypes));
                if (fAdded)
                    lst.add((CustomPropertyType) o);
                else {
                    Iterator<CustomPropertyType> iterator = lst.iterator();
                    while (iterator.hasNext()) {
                        CustomPropertyType cpt = iterator.next();
                        if (cpt.idPropType == ((CustomPropertyType) o).idPropType)
                            iterator.remove();
                    }
                }
                CurrentQuery.PropertyTypes = lst.toArray(new CustomPropertyType[0]);
            }

            @Override
            public boolean itemIsChecked(Object o) {
                for (CustomPropertyType cpt : CurrentQuery.PropertyTypes)
                    if (cpt.idPropType == ((CustomPropertyType) o).idPropType)
                        return true;
                return false;
            }
        });
    }
    //endregion

    protected void setUpNamedQueries() {
        CannedQuery[] rgItems = CannedQuery.getCannedQueries();
        TableLayout tl = (TableLayout) findViewById(R.id.tblFQNamedQueries);
        if (tl == null)
            return;
        tl.removeAllViews();

        LayoutInflater l = getActivity().getLayoutInflater();

        if (rgItems == null)
            rgItems = new CannedQuery[0];

        for (CannedQuery o : rgItems) {
            TableRow tr = (TableRow) l.inflate(R.layout.namedquerytableitem, tl, false);
            TextView tv = tr.findViewById(R.id.lblSavedQuery);
            tv.setText(o.QueryName);
            tv.setOnClickListener(view -> {
                CurrentQuery = o;
                fCannedQueryClicked = true;
                Intent i = new Intent();
                i.putExtra(QUERY_TO_EDIT, o);
                getActivity().setResult(Activity.RESULT_OK, i);
                getActivity().finish();
            });
            ImageButton btnDelete = tr.findViewById(R.id.btnDeleteNamedQuery);
            btnDelete.setOnClickListener(view -> new AlertDialog.Builder(getActivity())
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setTitle(R.string.lblConfirm)
                    .setMessage(R.string.fqQueryDeleteConfirm)
                    .setPositiveButton(R.string.lblOK, (dialog, which) -> new DeleteCannedQueryTask(getContext(), o).execute())
                    .setNegativeButton(R.string.lblCancel, null)
                    .show());
            tl.addView(tr, new TableLayout.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        toForm();
        fCannedQueryClicked = false;
    }

    public void reset() {
        CurrentQuery.Init();
        setUpChecklists();
        toForm();
        setExpandCollapseState();
    }

    protected void toForm() {
        SetStringForField(R.id.fqGeneralText, CurrentQuery.GeneralText);
        SetStringForField(R.id.fqModelName, CurrentQuery.ModelName);
        SetStringForField(R.id.fqAirports, TextUtils.join(" ", CurrentQuery.AirportList));

        SetLocalDateForField(R.id.btnfqDateStart, MFBUtil.LocalDateFromUTCDate(CurrentQuery.DateMin));
        SetLocalDateForField(R.id.btnfqDateEnd, MFBUtil.LocalDateFromUTCDate(CurrentQuery.DateMax));

        switch (CurrentQuery.DateRange) {
            case none:
                break;
            case AllTime:
                SetRadioButton(R.id.rbAlltime);
                break;
            case YTD:
                SetRadioButton(R.id.rbYTD);
                break;
            case Trailing12Months:
                SetRadioButton(R.id.rbTrailing12);
                break;
            case Tailing6Months:
                SetRadioButton(R.id.rbTrailing6);
                break;
            case ThisMonth:
                SetRadioButton(R.id.rbThisMonth);
                break;
            case PrevMonth:
                SetRadioButton(R.id.rbPreviousMonth);
                break;
            case PrevYear:
                SetRadioButton(R.id.rbPreviousYear);
                break;
            case Trailing30:
                SetRadioButton(R.id.rbTrailing30);
                break;
            case Trailing90:
                SetRadioButton(R.id.rbTrailing90);
                break;
            case Custom:
                SetRadioButton(R.id.rbCustom);
                break;
        }

        SetCheckState(R.id.ckIsPublic, CurrentQuery.IsPublic);
        SetCheckState(R.id.ckIsSigned, CurrentQuery.IsSigned);
        SetCheckState(R.id.ckHasApproaches, CurrentQuery.HasApproaches);
        SetCheckState(R.id.ckHasCFI, CurrentQuery.HasCFI);
        SetCheckState(R.id.ckHasDual, CurrentQuery.HasDual);
        SetCheckState(R.id.ckHasFSLandings, CurrentQuery.HasFullStopLandings);
        SetCheckState(R.id.ckHasFSNightLandings, CurrentQuery.HasNightLandings);
        SetCheckState(R.id.ckHasHolds, CurrentQuery.HasHolds);
        SetCheckState(R.id.ckHasIMC, CurrentQuery.HasIMC);
        SetCheckState(R.id.ckHasNight, CurrentQuery.HasNight);
        SetCheckState(R.id.ckHasPIC, CurrentQuery.HasPIC);
        SetCheckState(R.id.ckHasSIC, CurrentQuery.HasSIC);
        SetCheckState(R.id.ckHasSimIMC, CurrentQuery.HasSimIMCTime);
        SetCheckState(R.id.ckHasTelemetry, CurrentQuery.HasTelemetry);
        SetCheckState(R.id.ckHasImages, CurrentQuery.HasImages);
        SetCheckState(R.id.ckHasXC, CurrentQuery.HasXC);

        SetCheckState(R.id.ckHasFlaps, CurrentQuery.HasFlaps);
        SetCheckState(R.id.ckIsComplex, CurrentQuery.IsComplex);
        SetCheckState(R.id.ckIsConstantProp, CurrentQuery.IsConstantSpeedProp);
        SetCheckState(R.id.ckisGlass, CurrentQuery.IsGlass);
        SetCheckState(R.id.ckIsHighPerf, CurrentQuery.IsHighPerformance);
        SetCheckState(R.id.ckIsRetract, CurrentQuery.IsRetract);
        SetCheckState(R.id.ckIsTailwheel, CurrentQuery.IsTailwheel);
        SetCheckState(R.id.ckIsMotorGlider, CurrentQuery.IsMotorglider);
        SetCheckState(R.id.ckIsMultiEngineHeli, CurrentQuery.IsMultiEngineHeli);

        switch (CurrentQuery.EngineType) {
            case AllEngines:
                SetRadioButton(R.id.rbAllEngines);
                break;
            case Piston:
                SetRadioButton(R.id.rbEnginePiston);
                break;
            case Jet:
                SetRadioButton(R.id.rbEngineJet);
                break;
            case Turboprop:
                SetRadioButton(R.id.rbEngineTurboprop);
                break;
            case AnyTurbine:
                SetRadioButton(R.id.rbEngineTurbine);
                break;
            case Electric:
                SetRadioButton(R.id.rbEngineElectric);
                break;
        }

        switch (CurrentQuery.AircraftInstanceTypes) {
            case AllAircraft:
                SetRadioButton(R.id.rbInstanceAny);
                break;
            case RealOnly:
                SetRadioButton(R.id.rbInstanceReal);
                break;
            case TrainingOnly:
                SetRadioButton(R.id.rbInstanceTraining);
                break;
        }

        switch (CurrentQuery.Distance) {
            case AllFlights:
                SetRadioButton(R.id.rbDistanceAny);
                break;
            case LocalOnly:
                SetRadioButton(R.id.rbDistanceLocal);
                break;
            case NonLocalOnly:
                SetRadioButton(R.id.rbDistanceNonlocal);
                break;
        }
    }

    protected void fromForm() {
        CurrentQuery.GeneralText = StringFromField(R.id.fqGeneralText);
        CurrentQuery.ModelName = StringFromField(R.id.fqModelName);
        String szAirports = StringFromField(R.id.fqAirports).trim().toUpperCase(Locale.getDefault());
        CurrentQuery.AirportList = (szAirports.length() > 0) ? Airport.SplitCodes(szAirports) : new String[0];

        CurrentQuery.IsPublic = CheckState(R.id.ckIsPublic);
        CurrentQuery.IsSigned = CheckState(R.id.ckIsSigned);
        CurrentQuery.HasApproaches = CheckState(R.id.ckHasApproaches);
        CurrentQuery.HasCFI = CheckState(R.id.ckHasCFI);
        CurrentQuery.HasDual = CheckState(R.id.ckHasDual);
        CurrentQuery.HasFullStopLandings = CheckState(R.id.ckHasFSLandings);
        CurrentQuery.HasNightLandings = CheckState(R.id.ckHasFSNightLandings);
        CurrentQuery.HasHolds = CheckState(R.id.ckHasHolds);
        CurrentQuery.HasIMC = CheckState(R.id.ckHasIMC);
        CurrentQuery.HasNight = CheckState(R.id.ckHasNight);
        CurrentQuery.HasPIC = CheckState(R.id.ckHasPIC);
        CurrentQuery.HasSIC = CheckState(R.id.ckHasSIC);
        CurrentQuery.HasSimIMCTime = CheckState(R.id.ckHasSimIMC);
        CurrentQuery.HasTelemetry = CheckState(R.id.ckHasTelemetry);
        CurrentQuery.HasImages = CheckState(R.id.ckHasImages);
        CurrentQuery.HasXC = CheckState(R.id.ckHasXC);

        CurrentQuery.HasFlaps = CheckState(R.id.ckHasFlaps);
        CurrentQuery.IsComplex = CheckState(R.id.ckIsComplex);
        CurrentQuery.IsConstantSpeedProp = CheckState(R.id.ckIsConstantProp);
        CurrentQuery.IsGlass = CheckState(R.id.ckisGlass);
        CurrentQuery.IsHighPerformance = CheckState(R.id.ckIsHighPerf);
        CurrentQuery.IsRetract = CheckState(R.id.ckIsRetract);
        CurrentQuery.IsTailwheel = CheckState(R.id.ckIsTailwheel);
        CurrentQuery.IsMotorglider = CheckState(R.id.ckIsMotorGlider);
        CurrentQuery.IsMultiEngineHeli = CheckState(R.id.ckIsMultiEngineHeli);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.flightquerymenu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.menuResetFlight:
                reset();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void updateDate(int id, Date dt) {
        switch (id) {
            case R.id.btnfqDateStart:
                CurrentQuery.DateMin = MFBUtil.UTCDateFromLocalDate(dt);
                CurrentQuery.DateRange = DateRanges.Custom;
                ((RadioButton) findViewById(R.id.rbCustom)).setChecked(true);
                break;
            case R.id.btnfqDateEnd:
                CurrentQuery.DateMax = MFBUtil.UTCDateFromLocalDate(dt);
                CurrentQuery.DateRange = DateRanges.Custom;
                ((RadioButton) findViewById(R.id.rbCustom)).setChecked(true);
                break;
        }

        toForm();
    }

    private void toggleHeader(View v, int idTarget) {
        View vFocus = getActivity().getCurrentFocus();
        if (vFocus != null)
            vFocus.clearFocus();  // prevent scrolling to the top (where the first text box is)
        View target = findViewById(idTarget);
        setExpandedState((TextView) v, target, target.getVisibility() != View.VISIBLE);
    }

    public void onClick(View v) {
        int id = v.getId();
        switch (id) {
            case R.id.btnfqDateEnd:
            case R.id.btnfqDateStart:
                DlgDatePicker dlg = new DlgDatePicker(this,
                        DlgDatePicker.datePickMode.LOCALDATEONLY,
                        id == R.id.btnfqDateStart ?
                                MFBUtil.LocalDateFromUTCDate(CurrentQuery.DateMin) :
                                MFBUtil.LocalDateFromUTCDate(CurrentQuery.DateMax));
                dlg.m_delegate = this;
                dlg.m_id = id;
                dlg.show();
                return;

            // All of the remaining items below are radio buttons
            case R.id.rbAlltime:
                CurrentQuery.SetDateRange(DateRanges.AllTime);
                break;
            case R.id.rbCustom:
                CurrentQuery.SetDateRange(DateRanges.Custom);
                break;
            case R.id.rbPreviousMonth:
                CurrentQuery.SetDateRange(DateRanges.PrevMonth);
                break;
            case R.id.rbPreviousYear:
                CurrentQuery.SetDateRange(DateRanges.PrevYear);
                break;
            case R.id.rbThisMonth:
                CurrentQuery.SetDateRange(DateRanges.ThisMonth);
                break;
            case R.id.rbTrailing12:
                CurrentQuery.SetDateRange(DateRanges.Trailing12Months);
                break;
            case R.id.rbTrailing6:
                CurrentQuery.SetDateRange(DateRanges.Tailing6Months);
                break;
            case R.id.rbTrailing30:
                CurrentQuery.SetDateRange(DateRanges.Trailing30);
                break;
            case R.id.rbTrailing90:
                CurrentQuery.SetDateRange(DateRanges.Trailing90);
                break;
            case R.id.rbYTD:
                CurrentQuery.SetDateRange(DateRanges.YTD);
                break;

            case R.id.rbAllEngines:
                CurrentQuery.EngineType = FlightQuery.EngineTypeRestriction.AllEngines;
                break;
            case R.id.rbEngineJet:
                CurrentQuery.EngineType = FlightQuery.EngineTypeRestriction.Jet;
                break;
            case R.id.rbEnginePiston:
                CurrentQuery.EngineType = FlightQuery.EngineTypeRestriction.Piston;
                break;
            case R.id.rbEngineTurbine:
                CurrentQuery.EngineType = FlightQuery.EngineTypeRestriction.AnyTurbine;
                break;
            case R.id.rbEngineTurboprop:
                CurrentQuery.EngineType = FlightQuery.EngineTypeRestriction.Turboprop;
                break;
            case R.id.rbEngineElectric:
                CurrentQuery.EngineType = FlightQuery.EngineTypeRestriction.Electric;
                break;

            case R.id.rbInstanceAny:
                CurrentQuery.AircraftInstanceTypes = FlightQuery.AircraftInstanceRestriction.AllAircraft;
                break;
            case R.id.rbInstanceReal:
                CurrentQuery.AircraftInstanceTypes = FlightQuery.AircraftInstanceRestriction.RealOnly;
                break;
            case R.id.rbInstanceTraining:
                CurrentQuery.AircraftInstanceTypes = FlightQuery.AircraftInstanceRestriction.TrainingOnly;
                break;

            case R.id.rbDistanceAny:
                CurrentQuery.Distance = FlightQuery.FlightDistance.AllFlights;
                break;
            case R.id.rbDistanceLocal:
                CurrentQuery.Distance = FlightQuery.FlightDistance.LocalOnly;
                break;
            case R.id.rbDistanceNonlocal:
                CurrentQuery.Distance = FlightQuery.FlightDistance.NonLocalOnly;
                break;
            case R.id.txtFQACFeatures:
                toggleHeader(v, R.id.sectFQAircraftFeatures);
                break;
            case R.id.txtFQDatesHeader:
                toggleHeader(v, R.id.sectFQDates);
                break;
            case R.id.txtFQAirportsHeader:
                toggleHeader(v, R.id.tblFQAirports);
                break;
            case R.id.txtFQFlightFeatures:
                toggleHeader(v, R.id.sectFQFlightFeatures);
                break;
            case R.id.txtFQAircraftHeader:
                toggleHeader(v, R.id.llfqAircraft);
                break;
            case R.id.txtFQModelsHeader:
                toggleHeader(v, R.id.sectFQModels);
                break;
            case R.id.txtFQCatClassHeader:
                toggleHeader(v, R.id.tblFQCatClass);
                break;
            case R.id.txtFQPropsHeader:
                toggleHeader(v, R.id.tblFQProps);
                break;
            case R.id.txtFQNamedQueryHeader:
                toggleHeader(v, R.id.sectFQNamedQueries);
                break;
        }

        toForm();
    }
}
