/*
	MyFlightbook for Android - provides native access to MyFlightbook
	pilot's logbook
    Copyright (C) 2017-2020 MyFlightbook, LLC

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
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.location.Location;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
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
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.myflightbook.android.WebServices.AuthToken;
import com.myflightbook.android.WebServices.MFBSoap;
import com.myflightbook.android.WebServices.RecentFlightsSvc;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Objects;

import Model.Airport;
import Model.FlightQuery;
import Model.GPX;
import Model.LatLong;
import Model.LocSample;
import Model.LogbookEntry;
import Model.MFBConstants;
import Model.MFBImageInfo;
import Model.MFBLocation;
import Model.MFBUtil;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

public class ActFlightMap extends AppCompatActivity implements OnMapReadyCallback, OnClickListener, OnMarkerClickListener, OnGlobalLayoutListener, OnCheckedChangeListener, OnMapLongClickListener {

    private LatLngBounds m_llb = null;
    private LogbookEntry m_le = null;
    private Airport[] m_rgapRoute = null;
    private LatLong[] m_rgFlightRoute = null;
    private String m_GPXPath = null;
    private Boolean m_fHasHadLayout = false;
    private Boolean m_fShowAllAirports = false;
    private final HashMap<String, Airport> m_hmAirports = new HashMap<>();
    private final HashMap<String, MFBImageInfo> m_hmImages = new HashMap<>();
    private String m_passedAliases = "";

    private static final int DimensionImageOverlay = 60;
    private static final int RadiusImage = 10;
    private static final int BorderImage = 3;

    private static final int ZOOM_LEVEL_AIRPORT = 13;
    private static final int ZOOM_LEVEL_AREA = 8;

    // intent keys
    public static final String ROUTEFORFLIGHT = "com.myflightbook.android.RouteForFlight";
    public static final String PENDINGFLIGHTID = "com.myflightbook.android.pendingflightid";
    public static final String EXISTINGFLIGHTID = "com.myflightbook.android.existingflightid";
    public static final String NEWFLIGHTID = "com.myflightbook.android.newflightid";
    public static final String ALIASES = "com.myflightbook.android.aliases";
    public static final int REQUEST_ROUTE = 58372;

    private static class SendGPXTask extends AsyncTask<Void, Void, MFBSoap> {
        String m_Result = "";
        final int m_idFlight;
        private final AsyncWeakContext<ActFlightMap> m_ctxt;

        SendGPXTask(Context c, ActFlightMap afm, int idFlight) {
            super();
            m_ctxt = new AsyncWeakContext<>(c, afm);
            m_idFlight = idFlight;
        }

        @Override
        protected MFBSoap doInBackground(Void... params) {
            RecentFlightsSvc rf = new RecentFlightsSvc();
            m_Result = rf.FlightPathForFlightGPX(AuthToken.m_szAuthToken, m_idFlight, m_ctxt.getContext());
            return rf;
        }

        protected void onPreExecute() {
        }

        protected void onPostExecute(MFBSoap svc) {
            ActFlightMap afm = m_ctxt.getCallingActivity();
            if (m_Result != null && m_Result.length() > 0 && afm != null) {
                afm.sendGPX(m_Result);
            }
        }
    }

    public boolean onMarkerClick(Marker marker) {
        Airport ap;
        MFBImageInfo mfbii;

        if ((ap = m_hmAirports.get(marker.getId())) != null) {
            AlertDialog.Builder dialog = new AlertDialog.Builder(this, R.style.MFBDialog);
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
                        b.putSerializable(ActFlightQuery.QUERY_TO_EDIT, fq);
                        i.putExtras(b);
                        ActFlightMap.this.startActivity(i);
                        dlg.dismiss();
                });
            dialog.setMessage(marker.getSnippet());
            dialog.show();
            return true;
        } else if ((mfbii = m_hmImages.get(marker.getId())) != null) {
            AlertDialog.Builder dialog = new AlertDialog.Builder(this, R.style.MFBDialog);

            LayoutInflater vi = getLayoutInflater();
            @SuppressLint("InflateParams") View v = vi.inflate(R.layout.mapimageitem, null);
            dialog.setView(v);

            ImageView i = v.findViewById(R.id.imgMFBIIImage);
            TextView t = v.findViewById(R.id.txtMFBIIComment);

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
        private final int m_idFlight;

        FetchFlightPathTask(int idFlight) {
            m_idFlight = idFlight;
        }
        public void run() {
            RecentFlightsSvc rfs = new RecentFlightsSvc();
            m_rgFlightRoute = rfs.FlightPathForFlight(AuthToken.m_szAuthToken, m_idFlight, ActFlightMap.this);
            if ((m_rgFlightRoute != null) && (m_rgFlightRoute.length > 0))
                ActFlightMap.this.runOnUiThread(ActFlightMap.this::updateMapElements);
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
        EditText t = findViewById(R.id.txtMapRoute);
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
            if (m_le != null) {
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
            String szTitle = String.format(Locale.getDefault(), "%s %s", ap.AirportID, (ap.Distance > 0) ? String.format(Locale.getDefault(), " (%.1f%s)", ap.Distance, szNM) : "");
            String szLocale = String.format(Locale.getDefault(), "%s %s", ap.Country, ap.Admin1).trim();
            String szSnippet = String.format("%s %s", ap.FacilityName, szLocale.length() == 0 ? "" : String.format(Locale.getDefault(), "(%s)", szLocale)).trim();
            llb.include(ll.getLatLng());

            Marker m = map.addMarker(new MarkerOptions()
                    .position(ll.getLatLng()).anchor(0.5f, 0.5f)
                    .icon(BitmapDescriptorFactory.fromResource(ap.IsPort() ? R.drawable.airport : R.drawable.tower)).title(szTitle).snippet(szSnippet));
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

        // Save as GPX only if there is a path.
        String state = Environment.getExternalStorageState();
        boolean fIsMounted = Environment.MEDIA_MOUNTED.equals(state);

        boolean fHasNoPath = m_rgFlightRoute == null || this.m_rgFlightRoute.length == 0;
        findViewById(R.id.btnExportGPX).setVisibility(fHasNoPath || !fIsMounted ? View.GONE : View.VISIBLE);
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

            SupportMapFragment mf = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.mfbMap);
            View mapView = Objects.requireNonNull(mf).getView();
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

        SupportMapFragment mf = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.mfbMap);
        try {
            Objects.requireNonNull(mf).getMapAsync(this);
        } catch (Exception ex) {
            Log.e(MFBConstants.LOG_TAG, Objects.requireNonNull(ex.getLocalizedMessage()));
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
        EditText t = findViewById(R.id.txtMapRoute);

        ImageButton b = findViewById(R.id.btnUpdateMapRoute);
        b.setOnClickListener(this);

        ImageButton btnExport = findViewById(R.id.btnExportGPX);
        btnExport.setOnClickListener(this);

        ToggleButton tb = findViewById(R.id.ckShowAllAirports);
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

            if (m_le.IsPendingFlight()) {
                this.m_rgFlightRoute = LocSample.samplesFromDataString(m_le.szFlightData);
                m_GPXPath = GPX.getFlightDataStringAsGPX(m_rgFlightRoute);    // initialize the GPX path
            }
        } else if (idExisting > 0) {
            m_le = RecentFlightsSvc.GetCachedFlightByID(idExisting, this);
            if (m_le != null) {
                FetchFlightPathTask ffpt = new FetchFlightPathTask(idExisting);
                new Thread(ffpt).start();
            }
        } else if (idNew != 0) {
            m_le = MFBMain.getNewFlightListener().getInProgressFlight(this);
            this.m_rgFlightRoute = LocSample.flightPathFromDB();
            m_GPXPath = GPX.getFlightDataStringAsGPX(m_rgFlightRoute);    // initialize the GPX path.
        } else // all airports
        {
            t.setVisibility(View.GONE);
            b.setVisibility(View.GONE);
        }
    }

    private void setShowAllAirports(Boolean f) {
        m_fShowAllAirports = f;
        LinearLayout ll = findViewById(R.id.llMapToolbar);
        ll.setVisibility(f ? View.INVISIBLE : View.VISIBLE);
        updateMapElements(true);
    }

    private String filenameForPath()
    {
        return m_le.IsExistingFlight() ?
                String.format(Locale.getDefault(), "%s%d", getString(R.string.txtFileNameExisting), m_le.idFlight) :
                getString(m_le.IsNewFlight() ? R.string.txtFileNameNew : R.string.txtFileNamePending);
    }

    private void sendGPX(String szGPX) {
        if (szGPX == null || m_le == null)
            return;

        m_GPXPath = szGPX;

        String szBaseName = filenameForPath();
        String szFileName = String.format(Locale.getDefault(), "%s.gpx", szBaseName);
        File p = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        File f = new File(p, szFileName);
        try {
            FileOutputStream fos = new FileOutputStream(f);
            OutputStreamWriter osw = new OutputStreamWriter(fos);
            osw.append(szGPX);
            osw.close();
            fos.flush();
            fos.close();

            Uri uriFile = FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".provider", f);
            sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uriFile));

            Intent sendIntent = new Intent();
            sendIntent.setAction(Intent.ACTION_SEND);
            sendIntent.putExtra(Intent.EXTRA_STREAM, uriFile);
            sendIntent.putExtra(Intent.EXTRA_SUBJECT, szFileName);
            sendIntent.putExtra(Intent.EXTRA_TITLE, szFileName);
            sendIntent.setType("application/gpx+xml");
            sendIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(sendIntent, getResources().getText(R.string.txtShareGPX)));
        }
        catch (FileNotFoundException e) {
            Log.e("Exception", "openFileOutput failed" + e.toString());
        }
        catch (IOException e) {
            Log.e("Exception", "File write failed: " + e.toString());
        }
        catch (SecurityException e) {
            Log.e("Exception", "Security exception writing file: " + e.toString());
        }
    }

    private final int PERMISSION_REQUEST_WRITE_GPX = 50372;

    private Boolean checkDocPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) // no need to request WRITE_EXTERNAL_STORAGE in 29 and later.
            return true;

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
            return true;

        // Should we show an explanation?
        ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST_WRITE_GPX);
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_WRITE_GPX) {
            if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                onClick(findViewById(R.id.btnExportGPX));
            }
            return;
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.btnUpdateMapRoute)
            updateMapElements();
        else if (id == R.id.btnExportGPX) {
            if (!checkDocPermissions())
                return;

            if (m_GPXPath == null && m_le != null && !m_le.IsPendingFlight() && !m_le.IsNewFlight()) {
                SendGPXTask st = new SendGPXTask(this, this, m_le.idFlight);
                st.execute();
            } else
                sendGPX(m_GPXPath);
        }
    }

    public void onMapLongClick(LatLng point) {
        EditText t = findViewById(R.id.txtMapRoute);
        String szAdHoc = new LatLong(point.latitude, point.longitude).toAdHocLocString();
        t.setText((t.getText() + " " + szAdHoc).trim());
    }

    public void onCheckedChanged(CompoundButton v, boolean isChecked) {
        if (v.getId() == R.id.ckShowAllAirports) {
            setShowAllAirports(v.isChecked());
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
