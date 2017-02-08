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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.OnMapLongClickListener;
import com.google.android.gms.maps.GoogleMap.OnMarkerClickListener;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.myflightbook.android.WebServices.AuthToken;
import com.myflightbook.android.WebServices.RecentFlightsSvc;

import java.util.HashMap;
import java.util.Locale;

import Model.Airport;
import Model.FlightQuery;
import Model.LatLong;
import Model.LocSample;
import Model.LogbookEntry;
import Model.MFBImageInfo;
import Model.MFBLocation;
import Model.MFBUtil;

public class ActFlightMap extends Activity implements OnMapReadyCallback, OnClickListener, OnMarkerClickListener, OnGlobalLayoutListener, OnCheckedChangeListener, OnMapLongClickListener {

    private LatLngBounds m_llb = null;
    private LogbookEntry m_le = null;
    private Airport[] m_rgapRoute = null;
    private LatLong[] m_rgFlightRoute = null;
    private Boolean m_fHasHadLayout = false;
    private Boolean m_fShowAllAirports = false;
    private HashMap<String, Airport> m_hmAirports = new HashMap<>();
    private HashMap<String, MFBImageInfo> m_hmImages = new HashMap<>();
    private String m_passedAliases = "";

    static final int DimensionImageOverlay = 60;
    static final int RadiusImage = 10;
    static final int BorderImage = 3;

    static final int ZOOM_LEVEL_AIRPORT = 13;
    static final int ZOOM_LEVEL_AREA = 8;

    // intent keys
    public static final String ROUTEFORFLIGHT = "com.myflightbook.android.RouteForFlight";
    public static final String PENDINGFLIGHTID = "com.myflightbook.android.pendingflightid";
    public static final String EXISTINGFLIGHTID = "com.myflightbook.android.existingflightid";
    public static final String NEWFLIGHTID = "com.myflightbook.android.newflightid";
    public static final String ALIASES = "com.myflightbook.android.aliases";
    public static final int REQUEST_ROUTE = 58372;

    @SuppressLint("InflateParams")
    public boolean onMarkerClick(Marker marker) {
        Airport ap;
        MFBImageInfo mfbii;

        if ((ap = m_hmAirports.get(marker.getId())) != null) {
            AlertDialog.Builder dialog = new AlertDialog.Builder(this);
            dialog.setTitle(marker.getTitle());
            final Airport ap2 = ap;
            dialog.setIcon(ContextCompat.getDrawable(ActFlightMap.this, ap.IsPort() ? R.drawable.airport : R.drawable.tower));
            dialog.setNeutralButton(R.string.lblCancel, (d, which) -> d.dismiss());

            if (ap.IsPort())
                dialog.setPositiveButton(R.string.menuFlightSearch, (dlg, which) -> {
                        FlightQuery fq = new FlightQuery();
                        fq.Init();
                        // get any airport aliases
                        Airport[] rgAlias = Airport.getNearbyAirports(ap2.getLocation(), 0.01, 0.01);
                        StringBuilder szAirports = new StringBuilder(ap2.AirportID);
                        if (m_passedAliases.length() > 0)
                            szAirports.append(String.format(", %s", m_passedAliases));
                        for (Airport ap3 : rgAlias)
                            if (ap3.Type.compareTo(ap2.Type) == 0)
                                szAirports.append(String.format(Locale.getDefault(), " %s", ap3.AirportID));
                        fq.AirportList = Airport.SplitCodes(szAirports.toString());
                        Intent i = new Intent(ActFlightMap.this, RecentFlightsActivity.class);
                        Bundle b = new Bundle();
                        b.putSerializable(RecentFlightsActivity.REQUEST_FLIGHT_QUERY, fq);
                        i.putExtras(b);
                        ActFlightMap.this.startActivity(i);
                        dlg.dismiss();
                });
            dialog.setMessage(marker.getSnippet());
            dialog.show();
            return true;
        } else if ((mfbii = m_hmImages.get(marker.getId())) != null) {
            AlertDialog.Builder dialog = new AlertDialog.Builder(this);

            LayoutInflater vi = getLayoutInflater();
            View v = vi.inflate(R.layout.mapimageitem, null);
            dialog.setView(v);

            ImageView i = (ImageView) v.findViewById(R.id.imgMFBIIImage);
            TextView t = (TextView) v.findViewById(R.id.txtMFBIIComment);

            final MFBImageInfo mfbii2 = mfbii;

            i.setOnClickListener((v2) -> mfbii2.ViewFullImageInWebView(ActFlightMap.this));

            mfbii.LoadImageForImageView(true, i);
            t.setText(mfbii.Comment);
            t.setTextColor(Color.WHITE);
            v.setBackgroundColor(Color.DKGRAY);

            dialog.show();
            return true;
        }

        return false;
    }


    private class FetchFlightPathTask implements Runnable {
        public void run() {
            RecentFlightsSvc rfs = new RecentFlightsSvc();
            m_rgFlightRoute = rfs.FlightPathForFlight(AuthToken.m_szAuthToken, m_le.idFlight);
            if ((m_rgFlightRoute != null) && (m_rgFlightRoute.length > 0))
                ActFlightMap.this.runOnUiThread(() -> updateMapElements());
        }
    }

    private void addImageMarker(MFBImageInfo mfbii, LatLngBounds.Builder llb) {
        Bitmap bmap = MFBImageInfo.getRoundedCornerBitmap(mfbii.bitmapFromThumb(), Color.LTGRAY, RadiusImage, BorderImage, DimensionImageOverlay, DimensionImageOverlay, ActFlightMap.this);

        Marker m;

        GoogleMap map = getMap();
        if (map != null && (m = map.addMarker(new MarkerOptions()
                .position(mfbii.Location.getLatLng())
                .title(mfbii.Comment)
                .icon(BitmapDescriptorFactory.fromBitmap(bmap)))) != null) {
            m_hmImages.put(m.getId(), mfbii);
            if (llb != null)
                llb.include(mfbii.Location.getLatLng());
        }
    }

    private void updateMapElements() {
        updateMapElements(false);
    }

    private void updateMapElements(Boolean fNoResize) {
        EditText t = (EditText) findViewById(R.id.txtMapRoute);
        GoogleMap map = getMap();
        if (map == null)
            return;

        LatLngBounds.Builder llb = new LatLngBounds.Builder();

        // 4 layers to add:
        //  - Airports
        //  - Airport-to-airport route
        //  - Flight path (if available)
        //  - Images (if geotagged and available)

        // first, set up for the overlays and use a latlonbox to find the right zoom area
        m_llb = null; // start over
        map.clear();
        m_hmAirports.clear();
        m_hmImages.clear();

        // Add the airports
        if (m_fShowAllAirports) {
            if (map.getCameraPosition().zoom >= ZOOM_LEVEL_AREA) {
                m_llb = map.getProjection().getVisibleRegion().latLngBounds;
                m_rgapRoute = Airport.getNearbyAirports(MFBLocation.LastSeenLoc(), m_llb);
            } else
                m_rgapRoute = new Airport[0];
        } else
            m_rgapRoute = Airport.AirportsInRouteOrder(t.getText().toString(), MFBLocation.LastSeenLoc());

        if (m_rgapRoute == null)
            m_rgapRoute = new Airport[0];

        if (!m_fShowAllAirports) {
            // Add the airport route; we'll draw the airports on top of them.
            // Note that we don't do this if m_le is null because then we would connect the dots.
            if (m_le != null && !m_fShowAllAirports) {
                PolylineOptions po = new PolylineOptions().geodesic(true).color(Color.BLUE).width(4);

                for (Airport ap : m_rgapRoute) {
                    LatLong ll = ap.getLatLong();
                    llb.include(ll.getLatLng());
                    po.add(ll.getLatLng());
                }
                map.addPolyline(po);
            }

            // Then add the flight path, if available
            if (m_rgFlightRoute != null) {
                PolylineOptions po = new PolylineOptions().geodesic(true).color(Color.RED).width(2);
                for (LatLong ll : m_rgFlightRoute) {
                    llb.include(ll.getLatLng());
                    po.add(ll.getLatLng());
                }
                map.addPolyline(po);
            }

            // Kind of a hack - get the aliases for the airport specified
            if (m_rgapRoute.length == 1) {
                Intent i = getIntent();
                m_passedAliases = i.getStringExtra(ALIASES);
            }
        }

        for (Airport ap : m_rgapRoute) {
            LatLong ll = ap.getLatLong();
            String szNM = getString(R.string.abbrevNauticalMiles);
            String szContent = String.format("%s (%s) %s", ap.FacilityName, ap.AirportID, (ap.Distance > 0) ? String.format(Locale.getDefault(), " (%.1f%s)", ap.Distance, szNM) : "");
            llb.include(ll.getLatLng());

            Marker m = map.addMarker(new MarkerOptions().position(ll.getLatLng()).icon(BitmapDescriptorFactory.fromResource(ap.IsPort() ? R.drawable.airport : R.drawable.tower)).title(szContent));
            if (ap.IsPort())
                m_hmAirports.put(m.getId(), ap);
        }

        // Add images
        if (!m_fShowAllAirports && m_le != null && m_le.rgFlightImages != null) {
            for (MFBImageInfo mfbii : m_le.rgFlightImages) {
                if (mfbii.HasGeoTag()) {
                    if (mfbii.getThumbnail() != null) {
                        addImageMarker(mfbii, llb);
                    } else {
                        mfbii.LoadImageAsync(true, (sender) -> {
                                addImageMarker(sender, null);
                                if (m_llb != null)
                                    m_llb.including(sender.Location.getLatLng());
                        });
                    }
                }
            }
        }

        try {
            m_llb = llb.build();
        } catch (IllegalStateException ex) {
            m_llb = null;
        }

        if (m_fHasHadLayout && !fNoResize)
            autoZoom();

        // Save as KML only if there is a path.
        findViewById(R.id.btnExportKML).setVisibility(m_rgFlightRoute == null || this.m_rgFlightRoute.length == 0 ? View.GONE : View.VISIBLE);
    }

    private GoogleMap m_gMap = null;

    public void onMapReady(GoogleMap googleMap) {
        if (m_gMap == null) {
            m_gMap = googleMap;

            GoogleMap map = getMap();
            if (map == null) {
                MFBUtil.Alert(this, getString(R.string.txtError), getString(R.string.errNoGoogleMaps));
                finish();
                return;
            }

            map.setMapType(GoogleMap.MAP_TYPE_HYBRID);
            UiSettings settings = map.getUiSettings();
            settings.setCompassEnabled(false);
            settings.setRotateGesturesEnabled(false);
            settings.setScrollGesturesEnabled(true);
            settings.setZoomControlsEnabled(false);
            settings.setZoomGesturesEnabled(true);

            View mapView = getFragmentManager().findFragmentById(R.id.mfbMap).getView();
            if (mapView != null && mapView.getViewTreeObserver() != null && mapView.getViewTreeObserver().isAlive()) {
                mapView.getViewTreeObserver().addOnGlobalLayoutListener(this);
            }

            map.setOnMarkerClickListener(this);
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                map.setMyLocationEnabled(true);
            }
            map.setOnMapLongClickListener(this);

            updateMapElements();
        }
    }

    private GoogleMap getMap() {
        if (m_gMap != null)
            return m_gMap;

        MapFragment mf = (MapFragment) getFragmentManager().findFragmentById(R.id.mfbMap);
        try {
            mf.getMapAsync(this);
        } catch (Exception ex) {
            Log.e("MFBAndroid", ex.getLocalizedMessage());
        }
        return null;
    }

    private void autoZoom() {
        GoogleMap gm = getMap();
        if (gm == null)
            return;

        if (m_llb == null) {
            Location l = MFBLocation.LastSeenLoc();
            if (l != null)
                gm.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(l.getLatitude(), l.getLongitude()), ZOOM_LEVEL_AREA));
        } else {
            double height = Math.abs(m_llb.northeast.latitude - m_llb.southwest.latitude);
            double width = Math.abs(m_llb.northeast.longitude - m_llb.southwest.longitude);

            gm.moveCamera(CameraUpdateFactory.newLatLngBounds(m_llb, 20));
            if (height < 0.001 || width < 0.001)
                gm.moveCamera(CameraUpdateFactory.zoomTo((m_rgapRoute != null && m_rgapRoute.length == 1) ? ZOOM_LEVEL_AIRPORT : ZOOM_LEVEL_AREA));
        }
    }

    public void onGlobalLayout() {
        m_fHasHadLayout = true;

        autoZoom();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.flightmap);
        m_gMap = getMap();
        EditText t = (EditText) findViewById(R.id.txtMapRoute);

        ImageButton b = (ImageButton) findViewById(R.id.btnUpdateMapRoute);
        b.setOnClickListener(this);

        ImageButton btnExport = (ImageButton) findViewById(R.id.btnExportKML);
        btnExport.setOnClickListener(this);

        ToggleButton tb = (ToggleButton) findViewById(R.id.ckShowAllAirports);
        tb.setOnCheckedChangeListener(this);
        tb.setSelected(m_fShowAllAirports);

        Intent i = getIntent();

        String szRoute = i.getStringExtra(ROUTEFORFLIGHT);
        t.setText(szRoute);

        long idPending = i.getLongExtra(PENDINGFLIGHTID, 0);
        int idExisting = i.getIntExtra(EXISTINGFLIGHTID, 0);
        long idNew = i.getIntExtra(NEWFLIGHTID, 0);

        if (idPending > 0) {
            m_le = new LogbookEntry(idPending);

            if (m_le.IsPendingFlight())
                this.m_rgFlightRoute = LocSample.samplesFromDataString(m_le.szFlightData);
        } else if (idExisting > 0) {
            m_le = RecentFlightsSvc.GetCachedFlightByID(idExisting);
            FetchFlightPathTask ffpt = new FetchFlightPathTask();
            new Thread(ffpt).start();
        } else if (idNew != 0) {
            m_le = MFBMain.getNewFlightListener().getInProgressFlight();
            this.m_rgFlightRoute = LocSample.flightPathFromDB();
        } else // all airports
        {
            t.setVisibility(View.GONE);
            b.setVisibility(View.GONE);
        }
    }

    private void setShowAllAirports(Boolean f) {
        m_fShowAllAirports = f;
        LinearLayout ll = (LinearLayout) findViewById(R.id.llMapToolbar);
        ll.setVisibility(f ? View.INVISIBLE : View.VISIBLE);
        updateMapElements(true);
    }

    @SuppressLint("WorldReadableFiles")
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btnUpdateMapRoute:
                updateMapElements();
                break;
            case R.id.btnExportKML:
                String szKML = LocSample.getFlightDataStringAsKML(this.m_rgFlightRoute);

                Intent sendIntent = new Intent();
                sendIntent.setAction(Intent.ACTION_SEND);
                sendIntent.putExtra(Intent.EXTRA_TEXT, szKML);
                sendIntent.putExtra(Intent.EXTRA_SUBJECT, "KML.kml");
                sendIntent.setType("text/xml");
                startActivity(Intent.createChooser(sendIntent, getResources().getText(R.string.txtShareKML)));
                break;
        }
    }

    public void onMapLongClick(LatLng point) {
        EditText t = (EditText) findViewById(R.id.txtMapRoute);
        String szAdHoc = new LatLong(point.latitude, point.longitude).toAdHocLocString();
        t.setText((t.getText() + " " + szAdHoc).trim());
    }

    public void onCheckedChanged(CompoundButton v, boolean isChecked) {
        switch (v.getId()) {
            case R.id.ckShowAllAirports:
                setShowAllAirports(v.isChecked());
                break;
            default:
                break;
        }
    }

    @Override
    public void onBackPressed() {
        setResult(RESULT_OK, getIntent());
        String newRoute = ((EditText) findViewById(R.id.txtMapRoute)).getText().toString().toUpperCase(Locale.getDefault());
        getIntent().putExtra(ROUTEFORFLIGHT, newRoute);
        super.onBackPressed();
    }
}
