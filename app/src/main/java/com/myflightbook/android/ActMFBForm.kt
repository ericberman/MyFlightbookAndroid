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
package com.myflightbook.android

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.format.DateFormat
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.view.animation.Animation
import android.view.animation.Transformation
import android.widget.*
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.myflightbook.android.DlgDatePicker.DatePickMode
import com.myflightbook.android.DlgDatePicker.DateTimeUpdate
import com.myflightbook.android.webservices.AuthToken
import com.myflightbook.android.webservices.ImagesSvc
import com.myflightbook.android.webservices.MFBSoap
import com.myflightbook.android.webservices.MFBSoap.MFBSoapProgressUpdate
import com.myflightbook.android.webservices.UTCDate.formatDate
import com.myflightbook.android.webservices.UTCDate.isNullDate
import kotlinx.coroutines.*
import model.*
import model.DecimalEdit.EditMode
import model.MFBImageInfo.PictureDestination
import java.io.*
import java.util.*

/*
 * Helper class for dealing with forms.
 */ open class ActMFBForm : Fragment(), CoroutineScope by MainScope() {
    interface GallerySource {
        fun getGalleryID(): Int
        fun getGalleryHeader(): View?
        fun getImages(): Array<MFBImageInfo>
        fun setImages(rgmfbii: Array<MFBImageInfo>?)
        fun newImage(mfbii: MFBImageInfo?)
        fun getPictureDestination(): PictureDestination
        fun refreshGallery()
    }

    private var mChooseImageLauncher: ActivityResultLauncher<Array<String>?>? = null
    private var mTakePictureLauncher: ActivityResultLauncher<Array<String>?>? = null
    private var mTakeVideoLauncher: ActivityResultLauncher<Array<String>?>? = null
    @JvmField
    var mTempfilepath: String? = ""

    private class AddCameraTask(
        fVideo: Boolean,
        addToGallery: Boolean,
        geoTag: Boolean,
        frm: ActMFBForm
    ) : AsyncTask<String?, String?, Boolean>(), MFBSoapProgressUpdate {
        var mfbii: MFBImageInfo? = null
        val fGeoTag: Boolean = geoTag
        var fDeleteFileWhenDone: Boolean = addToGallery
        val fAddToGallery: Boolean = fDeleteFileWhenDone
        val mFvideo: Boolean = fVideo
        val mCtxt: AsyncWeakContext<ActMFBForm> = AsyncWeakContext(frm.context, frm)
        override fun doInBackground(vararg params: String?): Boolean {
            val szFilename = params[0]
            if (szFilename == null || szFilename.isEmpty()) {
                Log.e(MFBConstants.LOG_TAG, "No filename passed back!!!")
                return false
            }

            // Add the image/video to the gallery if necessary (i.e., if from the camera)
            if (fAddToGallery) {
                val f = File(szFilename)
                val uriSource = FileProvider.getUriForFile(
                    mCtxt.context!!,
                    BuildConfig.APPLICATION_ID + ".provider",
                    f
                )
                mCtxt.callingActivity!!.requireActivity()
                    .sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uriSource))
            }
            val gs = mCtxt.callingActivity as GallerySource
            mfbii = MFBImageInfo(gs.getPictureDestination())
            return mfbii!!.initFromCamera(
                szFilename,
                if (fGeoTag) MFBLocation.lastSeenLoc() else null,
                mFvideo,
                fDeleteFileWhenDone
            )
        }

        override fun onPreExecute() {}
        override fun onPostExecute(b: Boolean) {
            val frm = mCtxt.callingActivity ?: return
            if (b && mfbii != null) {
                val gs = frm as GallerySource
                gs.newImage(mfbii)
                gs.refreshGallery()
            }
        }

        override fun notifyProgress(percentageComplete: Int, szMsg: String?) {}

    }

    fun addCameraImage(szFilename: String?, fVideo: Boolean) {
        val act = AddCameraTask(fVideo, true, geoTag = true, frm = this)
        act.execute(szFilename)
    }

    // Activity pseudo support.
    fun findViewById(id: Int): View? {
        val v = view
        return v?.findViewById(id)
    }

    fun finish() {
        requireActivity().finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        cancel()
    }

    fun addGalleryImage(i: Intent) {
        val selectedImage = i.data
        val filePathColumn =
            arrayOf(MediaStore.Images.Media.DATA, MediaStore.Images.Media.MIME_TYPE)
        val cr = requireActivity().contentResolver
        assert(selectedImage != null)
        val cursor = cr.query(
            selectedImage!!, filePathColumn, null, null, null
        )
        if (cursor != null) {
            cursor.moveToFirst()
            var szFilename = cursor.getString(cursor.getColumnIndexOrThrow(filePathColumn[0]))
            val szMimeType = cursor.getString(cursor.getColumnIndexOrThrow(filePathColumn[1]))
            val fIsVideo = szMimeType.lowercase(Locale.getDefault()).startsWith("video")
            cursor.close()
            val act = AddCameraTask(fIsVideo, addToGallery = false, geoTag = false, frm = this)
            if (szFilename == null || szFilename.isEmpty()) { // try reading it into a temp file
                var inputStream: InputStream? = null
                var o: OutputStream? = null
                try {
                    val fTemp = File.createTempFile(
                        "img",
                        null,
                        requireActivity().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                    )
                    fTemp.deleteOnExit()
                    inputStream = cr.openInputStream(selectedImage)
                    o = FileOutputStream(fTemp)
                    val rgBuffer = ByteArray(1024)
                    var length: Int
                    try {
                        while (inputStream?.read(rgBuffer).apply { length = this!! }!! > 0) {
                            o.write(rgBuffer, 0, length)
                        }
                    } catch (e: IOException) {
                        Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e))
                    }
                    szFilename = fTemp.absolutePath
                    act.fDeleteFileWhenDone = true // delete the temp file when done.
                } catch (e: IOException) {
                    e.printStackTrace()
                } catch (ex: Exception) {
                    Log.e(
                        MFBConstants.LOG_TAG,
                        "Error copying input telemetry to new flight: " + ex.message
                    )
                } finally {
                    if (inputStream != null) {
                        try {
                            inputStream.close()
                        } catch (ignored: IOException) {
                        }
                    }
                    if (o != null) {
                        try {
                            o.close()
                        } catch (ignored: IOException) {
                        }
                    }
                }
                if (szFilename == null || szFilename.isEmpty()) return
            }
            act.execute(szFilename)
        }
    }

    private fun checkAllTrue(map: Map<String, Boolean>?): Boolean {
        if (map == null) return false
        var fAllGranted = true
        for (sz in map.keys) {
            val b = map[sz]
            fAllGranted = fAllGranted && b != null && b
        }
        return fAllGranted
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(MFBMain.NightModePref)
        super.onCreate(savedInstanceState)
        // need to restore this here because OnResume may come after the onActivityResult call
        val mPrefs = requireActivity().getPreferences(Activity.MODE_PRIVATE)
        mTempfilepath = mPrefs.getString(keyTempFileInProgress, "")

        // Set up launchers for camera and gallery
        val chooseImageLauncher = registerForActivityResult(
            StartActivityForResult()
        ) { result: ActivityResult? ->
            if (result != null && result.resultCode == Activity.RESULT_OK) chooseImageCompleted(
                result
            )
        }
        mChooseImageLauncher = registerForActivityResult(
            RequestMultiplePermissions()
        ) { result: Map<String, Boolean>? ->
            if (checkAllTrue(result)) {
                val i = Intent(Intent.ACTION_GET_CONTENT)
                i.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/* video/*")
                chooseImageLauncher.launch(i)
            }
        }
        val takePictureLauncher = registerForActivityResult(
            StartActivityForResult()
        ) { result: ActivityResult? ->
            if (result != null && result.resultCode == Activity.RESULT_OK) takePictureCompleted(
                result
            )
        }
        mTakePictureLauncher = registerForActivityResult(
            RequestMultiplePermissions()
        ) { result: Map<String, Boolean>? ->
            if (checkAllTrue(result)) {
                val fTemp: File
                val storageDir =
                    requireActivity().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                try {
                    fTemp = File.createTempFile(TEMP_IMG_FILE_NAME, ".jpg", storageDir)
                    fTemp.deleteOnExit()
                    mTempfilepath =
                        fTemp.absolutePath // need to save this for when the picture comes back
                    val prefs = requireActivity().getPreferences(Activity.MODE_PRIVATE)
                    val ed = prefs.edit()
                    ed.putString(keyTempFileInProgress, mTempfilepath)
                    ed.apply()
                    val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                    val uriImage = FileProvider.getUriForFile(
                        requireContext(),
                        BuildConfig.APPLICATION_ID + ".provider",
                        fTemp
                    )
                    intent.putExtra(MediaStore.EXTRA_OUTPUT, uriImage)
                    intent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1)
                    takePictureLauncher.launch(intent)
                } catch (e: IOException) {
                    Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e))
                    MFBUtil.alert(
                        requireActivity(),
                        getString(R.string.txtError),
                        getString(R.string.errNoCamera)
                    )
                }
            }
        }
        val takeVideoLauncher = registerForActivityResult(
            StartActivityForResult()
        ) { result: ActivityResult? ->
            if (result != null && result.resultCode == Activity.RESULT_OK) takeVideoCompleted(
                result
            )
        }
        mTakeVideoLauncher = registerForActivityResult(
            RequestMultiplePermissions()
        ) { result: Map<String, Boolean>? ->
            if (checkAllTrue(result)) {
                val fTemp: File
                val storageDir =
                    requireActivity().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                try {
                    fTemp = File.createTempFile(TEMP_IMG_FILE_NAME, ".mp4", storageDir)
                    fTemp.deleteOnExit()
                    mTempfilepath =
                        fTemp.absolutePath // need to save this for when the picture comes back
                    val prefs = requireActivity().getPreferences(Activity.MODE_PRIVATE)
                    val ed = prefs.edit()
                    ed.putString(keyTempFileInProgress, mTempfilepath)
                    ed.apply()
                    val uriImage = FileProvider.getUriForFile(
                        requireContext(),
                        BuildConfig.APPLICATION_ID + ".provider",
                        fTemp
                    )
                    val intent = Intent(MediaStore.ACTION_VIDEO_CAPTURE)
                    intent.putExtra(MediaStore.EXTRA_OUTPUT, uriImage)
                    intent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1)
                    takeVideoLauncher.launch(intent)
                } catch (e: IOException) {
                    Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e))
                    MFBUtil.alert(
                        requireActivity(),
                        getString(R.string.txtError),
                        getString(R.string.errNoCamera)
                    )
                }
            }
        }
    }

    //region binding data to forms
    fun addListener(id: Int) {
        findViewById(id)!!.setOnClickListener(this as View.OnClickListener)
    }

    fun setDateTime(id: Int, d: Date?, delegate: DateTimeUpdate?, dpm: DatePickMode?) {
        val dlg = DlgDatePicker(requireActivity(), dpm!!, d!!)
        dlg.mDelegate = delegate
        dlg.mId = id
        dlg.show()
    }

    fun intFromField(id: Int): Int {
        val v = findViewById(id) as DecimalEdit?
        return v!!.intValue
    }

    fun setIntForField(id: Int, value: Int) {
        val v = findViewById(id) as DecimalEdit?
        v!!.intValue = value
    }

    fun doubleFromField(id: Int): Double {
        val v = findViewById(id) as DecimalEdit?
        return v!!.doubleValue
    }

    fun setDoubleForField(id: Int, d: Double?) {
        val v = findViewById(id) as DecimalEdit?
        v!!.doubleValue = d!!
    }

    fun setUTCDateForField(id: Int, d: Date?) {
        val b = findViewById(id) as TextView?
        if (d == null || isNullDate(d)) b!!.text =
            getString(R.string.lblTouchForNow) else b!!.text =
            formatDate(DlgDatePicker.fUseLocalTime, d, context)
    }

    fun setLocalDateForField(id: Int, d: Date?) {
        val b = findViewById(id) as TextView?
        if (isNullDate(d)) b!!.text = getString(R.string.lblTouchForToday) else b!!.text =
            DateFormat.getDateFormat(requireActivity()).format(d!!)
    }

    fun stringFromField(id: Int): String {
        val e = findViewById(id) as TextView?
        return e!!.text.toString()
    }

    fun setStringForField(id: Int, s: String?) {
        val e = findViewById(id) as TextView?
        e!!.text = s
    }

    fun checkState(id: Int): Boolean {
        val c = findViewById(id) as CheckBox?
        return c!!.isChecked
    }

    fun setCheckState(id: Int, f: Boolean?) {
        val c = findViewById(id) as CheckBox?
        c!!.isChecked = f!!
    }

    fun setRadioButton(id: Int) {
        val rb = findViewById(id) as RadioButton?
        rb!!.isChecked = true
    }

    //endregion
    private var mfbiiLastClicked: MFBImageInfo? = null
    fun setUpImageGallery(idGallery: Int, rgMfbii: Array<MFBImageInfo>?, headerView: View?) {
        // Set up the gallery for any pictures
        if (rgMfbii == null) return
        if (headerView != null) headerView.visibility =
            if (rgMfbii.isEmpty()) View.GONE else View.VISIBLE
        val a: Activity = requireActivity()
        val l = a.layoutInflater
        val tl = findViewById(idGallery) as TableLayout? ?: return
        tl.removeAllViews()
        var i = 0
        for (mfbii in rgMfbii) {
            try {
                // TableRow tr = new TableRow(this);
                val tr = l.inflate(R.layout.imageitem, tl, false) as TableRow
                tr.id = MFBImageInfo.idImageGalleryIdBase + i++
                val iv = tr.findViewById<ImageView>(R.id.imageItemView)
                (tr.findViewById<View>(R.id.imageItemComment) as TextView).text = mfbii.comment
                mfbii.loadImageForImageView(true, iv)
                registerForContextMenu(tr)
                tr.setOnClickListener {
                    mfbii.viewFullImageInWebView(
                        requireActivity()
                    )
                }
                tr.setOnLongClickListener { v: View? ->
                    mfbiiLastClicked = mfbii
                    requireActivity().openContextMenu(v)
                    true
                }
                tl.addView(
                    tr,
                    TableLayout.LayoutParams(
                        TableRow.LayoutParams.MATCH_PARENT,
                        TableRow.LayoutParams.WRAP_CONTENT
                    )
                )
            } catch (ex: NullPointerException) { // should never happen.
                Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(ex))
            }
        }
    }

    fun setDecimalEditMode(id: Int) {
        val e = findViewById(id) as DecimalEdit?
        e!!.setMode(EditMode.HHMM)
    }

    fun onImageContextItemSelected(item: MenuItem, src: GallerySource): Boolean {
        val mfbii: MFBImageInfo? = mfbiiLastClicked
        if (mfbiiLastClicked == null) // should never be true
            return false
        when (item.itemId) {
            R.id.menuAddComment -> {
                val dlgComment = DlgImageComment(
                    requireActivity(),
                    mfbii,
                    object : DlgImageComment.AnnotationUpdate {
                        override fun updateAnnotation(mfbii: MFBImageInfo?) {
                            setUpImageGallery(
                                src.getGalleryID(),
                                src.getImages(),
                                src.getGalleryHeader())
                        }
                    }
                )
                dlgComment.show()
            }
            R.id.menuDeleteImage -> {
                AlertDialog.Builder(requireActivity(), R.style.MFBDialog)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setTitle(R.string.lblConfirm)
                    .setMessage(R.string.lblConfirmImageDelete)
                    .setPositiveButton(R.string.lblOK) { _: DialogInterface?, _: Int ->
                        // Image can be both local AND on server (aircraft)
                        // Need to delete in both places, as appropriate.
                        if (mfbii!!.isLocal()) mfbii.deleteFromDB()
                        if (mfbii.isOnServer()) {
                            val `is` = ImagesSvc()
                            `is`.deleteImage(AuthToken.m_szAuthToken, mfbii, context)
                        }

                        // Now remove this from the existing images in the source
                        val alNewImages = ArrayList<MFBImageInfo>()
                        val rgMfbii = src.getImages()
                        for (m in rgMfbii)  // re-add images that are NOT the one being deleted
                            if (mfbii.id != m.id || m.thumbnailFile.compareTo(mfbii.thumbnailFile) != 0) alNewImages.add(
                                m
                            )
                        src.setImages(alNewImages.toTypedArray())
                        setUpImageGallery(src.getGalleryID(), src.getImages(), src.getGalleryHeader())
                    }
                    .setNegativeButton(R.string.lblCancel, null)
                    .show()
            }
            R.id.menuViewImage -> mfbii!!.viewFullImageInWebView(requireActivity())
        }
        mfbiiLastClicked = null
        return true
    }

    //region Image/video permissions
    private fun getRequiredPermissions(permission: Int): Array<String>? {
        val fNeedWritePerm: Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q // no need to request WRITE_EXTERNAL_STORAGE in 29 and later.
        return when (permission) {
            CAMERA_PERMISSION_IMAGE, CAMERA_PERMISSION_VIDEO -> if (fNeedWritePerm) arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) else arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
            GALLERY_PERMISSION -> if (fNeedWritePerm) arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            else -> null //should never happen.
        }
    }

    //endregion
    //region image/video selection
    fun choosePicture() {
        mChooseImageLauncher!!.launch(getRequiredPermissions(GALLERY_PERMISSION))
    }

    fun takePicture() {
        mTakePictureLauncher!!.launch(getRequiredPermissions(CAMERA_PERMISSION_IMAGE))
    }

    fun takeVideo() {
        mTakeVideoLauncher!!.launch(getRequiredPermissions(CAMERA_PERMISSION_VIDEO))
    }

    protected open fun chooseImageCompleted(result: ActivityResult?) {
        // Override in calling subclass
    }

    protected open fun takePictureCompleted(result: ActivityResult?) {
        // Override in calling subclass
    }

    protected open fun takeVideoCompleted(result: ActivityResult?) {
        // Override in calling subclass
    }

    //endregion
    //region expand/collapse
    // next two methods are adapted from http://stackoverflow.com/questions/19263312/how-to-achieve-smooth-expand-collapse-animation
    private fun expandView(v: View) {
        v.measure(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        val targtetHeight = v.measuredHeight
        v.layoutParams.height = 0
        v.visibility = View.VISIBLE
        val a: Animation = object : Animation() {
            override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
                v.layoutParams.height =
                    if (interpolatedTime == 1f) LinearLayout.LayoutParams.WRAP_CONTENT else (targtetHeight * interpolatedTime).toInt()
                v.requestLayout()
            }

            override fun willChangeBounds(): Boolean {
                return true
            }
        }
        a.duration = (targtetHeight / v.context.resources.displayMetrics.density).toLong()
        v.startAnimation(a)
    }

    private fun collapseView(v: View) {
        val initialHeight = v.measuredHeight
        val a: Animation = object : Animation() {
            override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
                if (interpolatedTime == 1f) {
                    v.visibility = View.GONE
                } else {
                    v.layoutParams.height =
                        initialHeight - (initialHeight * interpolatedTime).toInt()
                    v.requestLayout()
                }
            }

            override fun willChangeBounds(): Boolean {
                return true
            }
        }
        a.duration = (initialHeight / v.context.resources.displayMetrics.density).toLong()
        v.startAnimation(a)
    }

    fun setExpandedState(v: TextView, target: View, fExpanded: Boolean) {
        setExpandedState(v, target, fExpanded, true)
    }

    fun setExpandedState(v: TextView, target: View, fExpanded: Boolean, fAnimated: Boolean) {
        val d: Drawable?
        if (fExpanded) {
            if (fAnimated) expandView(target) else target.visibility = View.VISIBLE
            d = ContextCompat.getDrawable(requireActivity(), R.drawable.collapse_light)
        } else {
            if (fAnimated) collapseView(target) else target.visibility = View.GONE
            d = ContextCompat.getDrawable(requireActivity(), R.drawable.expand_light)
        }
        v.setCompoundDrawablesWithIntrinsicBounds(d, null, null, null)
    } //endregion

    companion object {
        private const val keyTempFileInProgress = "uriFileInProgress"
        private const val CAMERA_PERMISSION_IMAGE = 83
        private const val CAMERA_PERMISSION_VIDEO = 84
        private const val GALLERY_PERMISSION = 85
        private const val TEMP_IMG_FILE_NAME = "takenpicture"

        //region async tasks
        suspend fun <T, U> doAsync(
            ctxt: Context,
            service : T,
            progressMsg : String?,
            inBackground : (service : T) -> U?,
            onComplete : (service: T, result : U?) ->Unit) {
            val pd = if (progressMsg != null) MFBUtil.showProgress(ctxt, progressMsg) else null

            val result = withContext(Dispatchers.IO) {
                inBackground(service)
            }

            pd?.dismiss()
            val soap = service as MFBSoap
            if (soap.lastError.isNotEmpty())
                MFBUtil.alert(
                    ctxt,
                    ctxt.getString(R.string.txtError),
                    soap.lastError
                )
            onComplete(service, result)
        }
        //endregion
    }
}