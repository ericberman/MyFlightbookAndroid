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
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.TextView;

import com.myflightbook.android.MultiSpinner.MultiSpinnerListener;
import com.myflightbook.android.WebServices.AircraftSvc;
import com.myflightbook.android.WebServices.CustomPropertyTypesSvc;
import com.myflightbook.android.WebServices.RecentFlightsSvc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import Model.Aircraft;
import Model.Airport;
import Model.CategoryClass;
import Model.CustomPropertyType;
import Model.FlightQuery;
import Model.FlightQuery.DateRanges;
import Model.MFBUtil;
import Model.MakeModel;

public class ActFlightQuery extends ActMFBForm implements MultiSpinnerListener, android.view.View.OnClickListener,  DlgDatePicker.DateTimeUpdate{
	static private FlightQuery CurrentQuery = null;
	private Aircraft[] m_rgac = null;
	private MakeModel[] m_rgmm = null;
	
	public static FlightQuery GetCurrentQuery()
	{
        if (CurrentQuery == null)
        	(CurrentQuery = new FlightQuery()).Init();
        return CurrentQuery;
	}
	
	public static void SetCurrentQuery(FlightQuery fq)
	{
		CurrentQuery = fq;
	}
	
	protected Aircraft[] GetCurrentAircraft()
	{
		if (m_rgac == null)
			m_rgac = new AircraftSvc().getCachedAircraft();
		return m_rgac;
	}
	
	protected MakeModel[] GetActiveMakes()
	{
		if (m_rgmm == null)
		{
			Aircraft[] rgac = GetCurrentAircraft();
			Map<String, MakeModel> htmm = new HashMap<>();
			for (Aircraft ac : rgac)
			{
				if (!htmm.containsKey(ac.ModelDescription))
				{
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

	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		this.setHasOptionsMenu(true);
		return inflater.inflate(R.layout.flightquery, container, false);
    }
	
	@Override
	public void onActivityCreated(Bundle savedInstanceState)
	{
		super.onActivityCreated(savedInstanceState);
        
        GetCurrentQuery();
        
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
		AddListener(R.id.txtFQACFeatures);
		AddListener(R.id.txtFQFlightFeatures);

		setExpandCollapseState();
    }

	protected void setExpandCollapseState()
	{
		setExpandedState((TextView)findViewById(R.id.txtFQDatesHeader), findViewById(R.id.sectFQDates), CurrentQuery.DateRange != DateRanges.AllTime);
		setExpandedState((TextView)findViewById(R.id.txtFQACFeatures), findViewById(R.id.sectFQAircraftFeatures), CurrentQuery.HasAircraftCriteria());
		setExpandedState((TextView)findViewById(R.id.txtFQFlightFeatures), findViewById(R.id.sectFQFlightFeatures), CurrentQuery.HasFlightCriteria());

	}
       
    @Override
	public void onPause()
	{
		super.onPause();
    	fromForm();
    	ActTotals.SetNeedsRefresh(true);
    	RecentFlightsSvc.ClearCachedFlights();
    	
    	// in case things change before next time, clear out the cached arrays.
    	m_rgac = null;
    	m_rgmm = null;
    }
    
    protected void syncSpinners()
    {
    	MultiSpinner msAircraft = (MultiSpinner)findViewById(R.id.multispinnerAircraft);
    	MultiSpinner msMakes = (MultiSpinner)findViewById(R.id.multispinnerModels);
    	MultiSpinner msCatClasses = (MultiSpinner) findViewById(R.id.multispinnerCategoryClasses);
    	MultiSpinner msProperties = (MultiSpinner) findViewById(R.id.multispinnerProps);
    	
		Aircraft[] rgac = GetCurrentAircraft();
		MakeModel[] rgmm = GetActiveMakes();
    	CategoryClass[] rgcc = CategoryClass.AllCatClasses(); 
    	CustomPropertyType[] rgcpt = CustomPropertyTypesSvc.getSearchableProperties();

    	msAircraft.setItems(rgac, getString(R.string.fqFlightAircraft), this);
        msMakes.setItems(rgmm, getString(R.string.fqFlightModel), this);
        msCatClasses.setItems(rgcc, getString(R.string.ccHeader), this);
        msProperties.setItems(rgcpt, getString(R.string.fqProperties), this);
        
        // set the relevant aircraft
    	boolean[] rgSelectedAc = msAircraft.getSelected();
    	for (int i = 0; i < rgac.length; i++)
    	{
    		rgSelectedAc[i] = false;
    		for (Aircraft ac : CurrentQuery.AircraftList)
    			if (ac.AircraftID == rgac[i].AircraftID)
    			{
    				rgSelectedAc[i] = true;
    				break;
    			}
    	}
    	
    	// set the relevant models
    	boolean[] rgSelectedMakes = msMakes.getSelected();
    	for (int i = 0; i < rgmm.length; i++)
    	{
    		rgSelectedMakes[i] = false;
    		for (MakeModel mm : CurrentQuery.MakeList)
    			if (mm.MakeModelId == rgmm[i].MakeModelId)
    			{
    				rgSelectedMakes[i] = true;
    				break;
    			}
    	}
    	
    	// set the relevant categories/classes
    	boolean[] rgSelectedCc = msCatClasses.getSelected();
    	for (int i = 0; i < rgcc.length; i++)
    	{
    		rgSelectedCc[i] = false;
    		for (CategoryClass cc : CurrentQuery.CatClassList)
    			if (cc.IdCatClass == rgcc[i].IdCatClass)
    			{
    				rgSelectedCc[i] = true;
    				break;
    			}
    	}
    	
    	// set the relevant properties
    	boolean[] rgSelectedProperties = msProperties.getSelected();
    	for (int i = 0; i < rgSelectedProperties.length; i++)
    	{
    		rgSelectedProperties[i] = false;
    		for (CustomPropertyType cpt : CurrentQuery.PropertyTypes)
    			if (cpt.idPropType == rgcpt[i].idPropType)
    			{
    				rgSelectedProperties[i] = true;
    				break;
    			}
    	}
    	
    	msAircraft.refresh();
    	msMakes.refresh();
    	msCatClasses.refresh();
    	msProperties.refresh();
	}
    
    @Override
	public void onResume()
    {
    	super.onResume();
    	syncSpinners();
    	toForm();
    }
    
    public static void resetCriteria()
    {
    	GetCurrentQuery().Init();
    }
    
    public void reset()
    {
    	resetCriteria();
    	syncSpinners();
    	toForm();
    }
    
    protected void toForm()
    {
    	SetStringForField(R.id.fqGeneralText, CurrentQuery.GeneralText);
    	SetStringForField(R.id.fqModelName, CurrentQuery.ModelName);
    	SetStringForField(R.id.fqAirports, TextUtils.join(" ", CurrentQuery.AirportList));
    	
		SetLocalDateForField(R.id.btnfqDateStart, MFBUtil.LocalDateFromUTCDate(CurrentQuery.DateMin));
		SetLocalDateForField(R.id.btnfqDateEnd, MFBUtil.LocalDateFromUTCDate(CurrentQuery.DateMax));
		
		switch (CurrentQuery.DateRange)
		{
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
        
        switch (CurrentQuery.EngineType)
        {
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
        
        switch (CurrentQuery.AircraftInstanceTypes)
        {
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
        
        switch (CurrentQuery.Distance)
        {
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
    
    protected void fromForm()
    {
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
	
	public void onItemsSelected(MultiSpinner sender, boolean[] selected) {
		switch (sender.getId())
		{
		case R.id.multispinnerAircraft:
			ArrayList<Aircraft> alAc = new ArrayList<>();
			Aircraft[] rgac = GetCurrentAircraft();
			for (int i = 0; i < selected.length; i++)
				if (selected[i])
					alAc.add(rgac[i]);
			CurrentQuery.AircraftList = alAc.toArray(new Aircraft[0]);
			break;
		case R.id.multispinnerModels:
			ArrayList<MakeModel> alMm = new ArrayList<>();
			MakeModel[] rgmm = GetActiveMakes();
			for (int i = 0; i < selected.length; i++)
				if (selected[i])
					alMm.add(rgmm[i]);
			CurrentQuery.MakeList = alMm.toArray(new MakeModel[0]);
			break;
		case R.id.multispinnerCategoryClasses:
			ArrayList<CategoryClass> alcc = new ArrayList<>();
			CategoryClass[] rgcc = CategoryClass.AllCatClasses();
			for (int i = 0; i < selected.length; i++)
				if (selected[i])
					alcc.add(rgcc[i]);
			CurrentQuery.CatClassList = alcc.toArray(new CategoryClass[0]);
			break;
		case R.id.multispinnerProps:
			ArrayList<CustomPropertyType> alcpt = new ArrayList<>();
			CustomPropertyType[] rgcpt = CustomPropertyTypesSvc.getSearchableProperties();
			for (int i = 0; i< selected.length; i++)
				if (selected[i])
					alcpt.add(rgcpt[i]);
			CurrentQuery.PropertyTypes = alcpt.toArray(new CustomPropertyType[alcpt.size()]);
			break;
		}
	}
	
	public void updateDate(int id, Date dt) {
		switch (id)
		{
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
	
	public void onClick(View v) {
		int id = v.getId();
		switch (id)
		{
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
		case R.id.txtFQACFeatures: {
			View target = findViewById(R.id.sectFQAircraftFeatures);
			setExpandedState((TextView) v, target, target.getVisibility() != View.VISIBLE);
		}
			break;
		case R.id.txtFQDatesHeader: {
			View target = findViewById(R.id.sectFQDates);
			setExpandedState((TextView) v, target, target.getVisibility() != View.VISIBLE);
		}
			break;
		case R.id.txtFQFlightFeatures: {
			View target = findViewById(R.id.sectFQFlightFeatures);
			setExpandedState((TextView) v, target, target.getVisibility() != View.VISIBLE);
		}
			break;
		}
		
		toForm();
	}
}
