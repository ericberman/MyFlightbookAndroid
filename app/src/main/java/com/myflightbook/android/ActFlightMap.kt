/*
	MyFlightbook for Android - provides native access to MyFlightbook
	pilot's logbook
    Copyright (C) 2017-2025 MyFlightbook, LLC

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
package com.myflightbook.android

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.widget.*
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMap.OnMapLongClickListener
import com.google.android.gms.maps.GoogleMap.OnMarkerClickListener
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.myflightbook.android.webservices.AuthToken
import com.myflightbook.android.webservices.RecentFlightsSvc
import com.myflightbook.android.webservices.RecentFlightsSvc.Companion.getCachedFlightByID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import model.*
import java.io.*
import java.util.*
import kotlin.math.abs

class ActFlightMap : AppCompatActivity(), OnMapReadyCallback, View.OnClickListener,
    OnMarkerClickListener, OnGlobalLayoutListener, CompoundButton.OnCheckedChangeListener,
    OnMapLongClickListener, MFBImageInfo.ImageCacheCompleted, CoroutineScope by MainScope() {
    private var mLlb: LatLngBounds? = null
    private var mLe: LogbookEntry? = null
    private var mRgaproute: Array<Airport>? = null
    private var mRgflightroute: Array<LatLong> = arrayOf()
    private var mGpxpath: String? = null
    private var mFhashadlayout = false
    private var mFshowallairports = false
    private val mHmairports = HashMap<String, Airport>()
    private val mHmimages = HashMap<String, MFBImageInfo>()
    private var mPassedaliases: String? = ""
    private var mGmap: GoogleMap? = null

    override fun onDestroy() {
        super.onDestroy()
        cancel()
    }

    override fun onMarkerClick(marker: Marker): Boolean {
        val ap = mHmairports[marker.id]
        val mfbii = mHmimages[marker.id]
        if (ap != null) {
            val dialog = AlertDialog.Builder(this, R.style.MFBDialog)
            dialog.setTitle(marker.title)
            dialog.setIcon(
                ContextCompat.getDrawable(
                    this@ActFlightMap,
                    if (ap.isPort()) R.drawable.airport else R.drawable.tower
                )
            )
            dialog.setNeutralButton(R.string.lblCancel) { d: DialogInterface, _: Int -> d.dismiss() }
            if (ap.isPort()) dialog.setPositiveButton(R.string.menuFlightSearch) { dlg: DialogInterface, _: Int ->
                val fq = FlightQuery()
                fq.init()
                // get any airport aliases
                val rgAlias = Airport.getNearbyAirports(ap.location, 0.01, 0.01)
                val szAirports = StringBuilder(ap.airportID)
                if (mPassedaliases!!.isNotEmpty()) szAirports.append(
                    String.format(
                        ", %s",
                        mPassedaliases
                    )
                )
                for (ap3 in rgAlias) if (ap3.facilityType.compareTo(ap.facilityType) == 0) szAirports.append(
                    String.format(
                        Locale.getDefault(), " %s", ap3.airportID
                    )
                )
                fq.airportList = Airport.splitCodes(szAirports.toString())
                val b = Bundle()
                b.putSerializable(ActFlightQuery.QUERY_TO_EDIT, fq)
                val i = FragmentHostActivity.createIntent<ActRecentsWS>(this, b)
                i.putExtras(b)
                this@ActFlightMap.startActivity(i)
                dlg.dismiss()
            }
            dialog.setMessage(marker.snippet)
            dialog.show()
            return true
        } else if (mfbii != null) {
            val dialog = AlertDialog.Builder(this, R.style.MFBDialog)
            val vi = layoutInflater
            @SuppressLint("InflateParams") val v = vi.inflate(R.layout.mapimageitem, null)
            dialog.setView(v)
            val i = v.findViewById<ImageView>(R.id.imgMFBIIImage)
            val t = v.findViewById<TextView>(R.id.txtMFBIIComment)
            i.setOnClickListener { mfbii.viewFullImageInWebView(this@ActFlightMap) }
            lifecycleScope.launch {
                mfbii.loadImageForImageView(true, i)
            }
            t.text = mfbii.comment
            t.setTextColor(Color.WHITE)
            v.setBackgroundColor(Color.DKGRAY)
            dialog.show()
            return true
        }
        return false
    }

    private fun fetchFlightPath(idFlight: Int, ctxt : Context)  {
        Thread {
            val rfs = RecentFlightsSvc()
            val rgll = rfs.getFlightPathForFlight(AuthToken.m_szAuthToken, idFlight, ctxt)
            if (rgll.isNotEmpty()) {
                runOnUiThread {
                    mRgflightroute = rgll
                    updateMapElements()
                }
            }
        }.start()
    }

    private fun addImageMarker(mfbii: MFBImageInfo, llb: LatLngBounds.Builder?) {
        val bmap = MFBImageInfo.getRoundedCornerBitmap(
            mfbii.bitmapFromThumb()!!,
            Color.LTGRAY,
            RadiusImage,
            BorderImage,
            DimensionImageOverlay,
            DimensionImageOverlay,
            this@ActFlightMap
        )
        if (!mfbii.hasGeoTag())
            return
        val map = mGmap
        if (map != null) {
            val m = map.addMarker(
                MarkerOptions()
                    .position(mfbii.location!!.latLng)
                    .title(mfbii.comment)
                    .icon(BitmapDescriptorFactory.fromBitmap(bmap))
            )
            if (m != null) {
                mHmimages[m.id] = mfbii
                llb?.include(mfbii.location!!.latLng)
            }
        }
    }

    private fun updateMapElements(fNoResize: Boolean = false) {
        val t = findViewById<EditText>(R.id.txtMapRoute)
        Log.d(MFBConstants.LOG_TAG, String.format(Locale.getDefault(), "updateMapElements, mGMap is%s null", if (mGmap == null) "" else " NOT"))
        val map = mGmap ?: return
        val llb = LatLngBounds.Builder()

        // 4 layers to add:
        //  - Airports
        //  - Airport-to-airport route
        //  - Flight path (if available)
        //  - Images (if geotagged and available)

        // first, set up for the overlays and use a latlonbox to find the right zoom area
        mLlb = null // start over
        map.clear()
        mHmairports.clear()
        mHmimages.clear()

        // Add the airports
        if (mFshowallairports) {
            if (map.cameraPosition.zoom >= ZOOM_LEVEL_AREA) {
                val bounds = map.projection.visibleRegion.latLngBounds
                mLlb = bounds
                mRgaproute = if (MFBLocation.lastSeenLoc() == null) arrayOf() else Airport.getNearbyAirports(MFBLocation.lastSeenLoc()!!, bounds)
            } else mRgaproute = arrayOf()
        } else mRgaproute =
            Airport.airportsInRouteOrder(t.text.toString(), MFBLocation.lastSeenLoc())
        if (!mFshowallairports) {
            // Add the airport route; we'll draw the airports on top of them.
            // Note that we don't do this if m_le is null because then we would connect the dots.
            if (mLe != null) {
                val po = PolylineOptions().geodesic(true).color(RouteColor).width(8f)
                for (ap in mRgaproute!!) {
                    val ll = ap.latLong
                    llb.include(ll.latLng)
                    po.add(ll.latLng)
                }
                map.addPolyline(po)
            }

            // Then add the flight path, if available
            val po = PolylineOptions().geodesic(true).color(PathColor).width(8f)
            for (ll in mRgflightroute) {
                llb.include(ll.latLng)
                po.add(ll.latLng)
            }
            map.addPolyline(po)

            // Kind of a hack - get the aliases for the airport specified
            if (mRgaproute!!.size == 1) {
                val i = intent
                mPassedaliases = i.getStringExtra(ALIASES)
            }
        }
        for (ap in mRgaproute!!) {
            val ll = ap.latLong
            val szNM = getString(R.string.abbrevNauticalMiles)
            val szTitle = String.format(Locale.getDefault(), "%s %s", ap.airportID, ap.facilityName)
            val sb = StringBuilder()
            if (ap.country != null && ap.country!!.isNotEmpty() && !ap.country!!.startsWith("--")) {
                if (ap.admin1 != null && ap.admin1!!.isNotEmpty()) {
                    sb.append(ap.admin1)
                    sb.append(", ")
                }
                sb.append(ap.country)
            }
            val szLocale = sb.toString()
            val szSnippet = String.format(
                "%s %s", szLocale, if (ap.distance > 0) String.format(
                    Locale.getDefault(), " (%.1f%s)", ap.distance, szNM
                ) else ""
            ).trim { it <= ' ' }
            llb.include(ll.latLng)
            val m = map.addMarker(
                MarkerOptions()
                    .position(ll.latLng).anchor(0.5f, 0.5f)
                    .icon(BitmapDescriptorFactory.fromResource(if (ap.isPort()) R.drawable.airport else R.drawable.tower))
                    .title(szTitle).snippet(szSnippet)
            )
            if (m != null) mHmairports[m.id] = ap
        }

        // Add images
        if (!mFshowallairports && mLe != null && mLe!!.rgFlightImages != null) {
            for (mfbii in mLe!!.rgFlightImages!!) {
                if (mfbii.hasGeoTag()) {
                    if (mfbii.thumbnail != null) {
                        addImageMarker(mfbii, llb)
                    } else {
                        val delegate = this
                        lifecycleScope.launch {
                            mfbii.loadImageAsync(true, delegate)
                        }
                    }
                }
            }
        }
        mLlb = try {
            llb.build()
        } catch (_ : IllegalStateException) {
            null
        }
        if (mFhashadlayout && !fNoResize) autoZoom()

        // Save as GPX only if there is a path.
        val state = Environment.getExternalStorageState()
        val fIsMounted = Environment.MEDIA_MOUNTED == state
        val fHasNoPath = mRgflightroute.isEmpty()
        findViewById<View>(R.id.btnExportGPX).visibility =
            if (fHasNoPath || !fIsMounted) View.GONE else View.VISIBLE
    }

    override fun imgCompleted(sender: MFBImageInfo?) {
        if (sender == null || !sender.hasGeoTag())
            return

        addImageMarker(sender, null)
        if (mLlb != null)
            mLlb!!.including(sender.location!!.latLng)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mGmap = googleMap
        Log.d(MFBConstants.LOG_TAG, "onMapReady called")
        val map = mGmap
        if (map == null) {
            MFBUtil.alert(
                this,
                getString(R.string.txtError),
                getString(R.string.errNoGoogleMaps)
            )
            finish()
            return
        }
        map.isIndoorEnabled = false
        map.mapType = MapType
        val settings = map.uiSettings
        settings.isCompassEnabled = false
        settings.isRotateGesturesEnabled = true
        settings.isScrollGesturesEnabled = true
        settings.isZoomControlsEnabled = false
        settings.isZoomGesturesEnabled = true
        val mf = supportFragmentManager.findFragmentById(R.id.mfbMap) as SupportMapFragment?
        val mapView = mf?.view
        if (mapView != null && mapView.viewTreeObserver != null && mapView.viewTreeObserver.isAlive) {
            mapView.viewTreeObserver.addOnGlobalLayoutListener(this)
        }
        map.setOnMarkerClickListener(this)
        map.setOnCameraIdleListener { updateMapElements(true) }
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            map.isMyLocationEnabled = true
        }
        map.setOnMapLongClickListener(this)
        updateMapElements()
    }

    private fun autoZoom() {
        val gm = mGmap ?: return
        if (mLlb == null) {
            val l = MFBLocation.lastSeenLoc()
            Log.d(MFBConstants.LOG_TAG, String.format(Locale.getDefault(),"AutoZoom: no box, %s", if (l == null) "and no location so no zoom" else "but location"))
            if (l != null) gm.moveCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(
                        l.latitude,
                        l.longitude
                    ), ZOOM_LEVEL_AREA.toFloat()
                )
            )
        } else {
            val height = abs(mLlb!!.northeast.latitude - mLlb!!.southwest.latitude)
            val width = abs(mLlb!!.northeast.longitude - mLlb!!.southwest.longitude)
            gm.moveCamera(CameraUpdateFactory.newLatLngBounds(mLlb!!, 20))
            Log.d(MFBConstants.LOG_TAG, String.format(Locale.getDefault(), "AutoZoom, height=%.1f, width=%.1f", height, width))
            if (height < 0.001 || width < 0.001) gm.moveCamera(CameraUpdateFactory.zoomTo(if (mRgaproute != null && mRgaproute!!.size == 1) ZOOM_LEVEL_AIRPORT.toFloat() else ZOOM_LEVEL_AREA.toFloat()))
        }
    }

    override fun onGlobalLayout() {
        Log.d(MFBConstants.LOG_TAG, "onGlobalLayout called")
        mFhashadlayout = true
        autoZoom()
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.flightmap)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.layout_root)) { view, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.setPadding(0, statusBarHeight, 0, 0)
            insets
        }

        // get the map - this will set mGMap in onMapReady
        val mf = supportFragmentManager.findFragmentById(R.id.mfbMap) as SupportMapFragment?
        try {
            mf?.getMapAsync(this)
        } catch (ex: Exception) {
            Log.e(MFBConstants.LOG_TAG, Objects.requireNonNull(ex.localizedMessage))
        }

        val t = findViewById<EditText>(R.id.txtMapRoute)
        val b = findViewById<ImageButton>(R.id.btnUpdateMapRoute)
        b.setOnClickListener(this)
        val btnExport = findViewById<ImageButton>(R.id.btnExportGPX)
        btnExport.setOnClickListener(this)
        val tb = findViewById<ToggleButton>(R.id.ckShowAllAirports)
        tb.setOnCheckedChangeListener(this)
        tb.isSelected = mFshowallairports
        val i = intent
        val szRoute = i.getStringExtra(ROUTEFORFLIGHT)
        t.setText(szRoute)
        val idPending = i.getLongExtra(PENDINGFLIGHTID, 0L)
        val idExisting = i.getIntExtra(EXISTINGFLIGHTID, 0)
        val idNew = i.getIntExtra(NEWFLIGHTID, 0).toLong()
        if (idPending > 0) {
            mLe = LogbookEntry(idPending)
            if (mLe!!.isAwaitingUpload()) {
                @Suppress("UNCHECKED_CAST")
                mRgflightroute = LocSample.samplesFromDataString(mLe!!.szFlightData) as Array<LatLong>
                mGpxpath = GPX.getFlightDataStringAsGPX(mRgflightroute) // initialize the GPX path
            }
        } else if (idExisting > 0) {
            mLe = getCachedFlightByID(idExisting, this)
            if (mLe != null)
                fetchFlightPath(idExisting, this)
        } else if (idNew != 0L) {
            mLe = MFBMain.newFlightListener!!.getInProgressFlight(this)
            @Suppress("UNCHECKED_CAST")
            mRgflightroute = LocSample.flightPathFromDB() as Array<LatLong>
            mGpxpath = GPX.getFlightDataStringAsGPX(mRgflightroute) // initialize the GPX path.
        } else  // all airports
        {
            t.visibility = View.GONE
            b.visibility = View.GONE
        }

        onBackPressedDispatcher.addCallback(this /* lifecycle owner */) {
            setResult(RESULT_OK, intent)
            val newRoute =
                t.text.toString().uppercase(
                    Locale.getDefault()
                )
            intent.putExtra(ROUTEFORFLIGHT, newRoute)
            finish()
        }
    }

    private fun setShowAllAirports(f: Boolean) {
        mFshowallairports = f
        val ll = findViewById<LinearLayout>(R.id.llMapToolbar)
        ll.visibility = if (f) View.INVISIBLE else View.VISIBLE
        updateMapElements(true)
    }

    override fun onClick(v: View) {
        val id = v.id
        if (id == R.id.btnUpdateMapRoute) updateMapElements() else if (id == R.id.btnExportGPX) {
            if (!checkDocPermissions()) return
            if (mGpxpath == null && mLe != null && !mLe!!.isAwaitingUpload() && !mLe!!.isNewFlight()) {
                sendGpxAsync(mLe!!.idFlight, this)
            } else sendGPX(mGpxpath)
        }
    }

    override fun onMapLongClick(point: LatLng) {
        val t = findViewById<EditText>(R.id.txtMapRoute)
        val szAdHoc = LatLong(point.latitude, point.longitude).toAdHocLocString()
        t.setText(String.format(Locale.getDefault(), "%s %s", t.text, szAdHoc).trim { it <= ' ' })
    }

    override fun onCheckedChanged(v: CompoundButton, isChecked: Boolean) {
        if (v.id == R.id.ckShowAllAirports) {
            setShowAllAirports(v.isChecked)
        }
    }

    // region GPX export
    private fun sendGpxAsync(idFlight : Int, ctxt : Context) {
        Thread {
            val rf = RecentFlightsSvc()
            val resultGPX = rf.getFlightPathForFlightGPX(AuthToken.m_szAuthToken, idFlight, ctxt)
            if (resultGPX.isNotEmpty()) {
                runOnUiThread {
                    mGpxpath = resultGPX
                    sendGPX(mGpxpath)
                }
            }

        }.start()
    }

    private fun filenameForPath(): String {
        return if (mLe!!.isExistingFlight()) String.format(
            Locale.getDefault(),
            "%s%d",
            getString(R.string.txtFileNameExisting),
            mLe!!.idFlight
        ) else getString(if (mLe!!.isNewFlight()) R.string.txtFileNameNew else R.string.txtFileNamePending)
    }

    private fun sendGPX(szGPX: String?) {
        if (szGPX == null || mLe == null) return
        mGpxpath = szGPX
        val szBaseName = filenameForPath()
        val szFileName = String.format(Locale.getDefault(), "%s.gpx", szBaseName)
        val p = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        val f = File(p, szFileName)
        try {
            val fos = FileOutputStream(f)
            val osw = OutputStreamWriter(fos)
            osw.append(szGPX)
            osw.close()
            fos.flush()
            fos.close()
            val uriFile =
                FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".provider", f)
            MediaScannerConnection.scanFile(this, arrayOf(f.toString()), null, null)
            val sendIntent = Intent()
            sendIntent.action = Intent.ACTION_SEND
            sendIntent.putExtra(Intent.EXTRA_STREAM, uriFile)
            sendIntent.putExtra(Intent.EXTRA_SUBJECT, szFileName)
            sendIntent.putExtra(Intent.EXTRA_TITLE, szFileName)
            sendIntent.type = "application/gpx+xml"
            sendIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(sendIntent, resources.getText(R.string.txtShareGPX)))
        } catch (e: FileNotFoundException) {
            Log.e("Exception", "openFileOutput failed$e")
        } catch (e: IOException) {
            Log.e("Exception", "File write failed: $e")
        } catch (e: SecurityException) {
            Log.e("Exception", "Security exception writing file: $e")
        }
    }

    private val _permissionREQUESTWRITEGPX = 50372
    private fun checkDocPermissions(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) // no need to request WRITE_EXTERNAL_STORAGE in 29 and later.
            return true
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        ) return true

        // Should we show an explanation?
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
            _permissionREQUESTWRITEGPX
        )
        return false
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>, grantResults: IntArray
    ) {
        if (requestCode == _permissionREQUESTWRITEGPX) {
            if (grantResults.size == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                onClick(findViewById(R.id.btnExportGPX))
            }
            return
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
    // endregion

    companion object {
        private const val DimensionImageOverlay = 60
        private const val RadiusImage = 10
        private const val BorderImage = 3
        private const val ZOOM_LEVEL_AIRPORT = 13
        private const val ZOOM_LEVEL_AREA = 8

        // intent keys
        const val ROUTEFORFLIGHT = "com.myflightbook.android.RouteForFlight"
        const val PENDINGFLIGHTID = "com.myflightbook.android.pendingflightid"
        const val EXISTINGFLIGHTID = "com.myflightbook.android.existingflightid"
        const val NEWFLIGHTID = "com.myflightbook.android.newflightid"
        const val ALIASES = "com.myflightbook.android.aliases"

        var RouteColor = Color.BLUE
        var PathColor = Color.RED
        var MapType = GoogleMap.MAP_TYPE_SATELLITE
    }
}