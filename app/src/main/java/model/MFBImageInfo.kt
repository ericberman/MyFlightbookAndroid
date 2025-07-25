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
package model

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.graphics.*
import android.graphics.Bitmap.CompressFormat
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.location.Location
import android.media.ThumbnailUtils
import android.os.Build
import android.os.Handler
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.util.TypedValue
import android.widget.ImageView
import androidx.exifinterface.media.ExifInterface
import com.myflightbook.android.ActWebView
import com.myflightbook.android.MFBMain
import com.myflightbook.android.R
import com.myflightbook.android.webservices.AuthToken
import kotlinx.coroutines.*
import org.ksoap2.serialization.KvmSerializable
import org.ksoap2.serialization.PropertyInfo
import org.ksoap2.serialization.SoapObject
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.*
import androidx.core.graphics.createBitmap

class MFBImageInfo : SoapableObject, KvmSerializable, Serializable {
    // for serialization
    private enum class FPProp {
        PIDComment, PIDVirtualPath, PIDThumbnailFile, PIDURLThumbnail, PIDURLFullImage, PIDWidth, PIDHeight, PIDTHWidth, PIDTHHeight, PIDLocation, PIDImageType
    }

    private enum class ImageFileType {
        JPEG, PDF, S3PDF, S3VideoMP4
    }

    private var mWidth = 0
    private var mHeight = 0
    private var mWidthThumbnail = 0
    private var mHeightThumbnail = 0
    @JvmField
    var comment = ""
    private var virtualPath = ""
    var thumbnailFile = ""
    private var mURLFullImage = ""
    private var mURLThumbnail = ""
    var location: LatLong? = null
    private var imageType = ImageFileType.JPEG
    private var mPd = PictureDestination.FlightImage
    private var mImgdata: ByteArray? = null
    var thumbnail: ByteArray? = null
        private set
    var id: Long = -1
        private set
    var targetID: Long = -1

    enum class PictureDestination {
        FlightImage, AircraftImage
    }

    private var mSzurl = MFBConstants.szURL_FlightPicture
    private var mKeyname = MFBConstants.szIMG_KEY_Flight
    private var mKey = ""

    interface ImageCacheCompleted {
        fun imgCompleted(sender: MFBImageInfo?)
    }

    private suspend fun loadURLAsync(iv : ImageView?, urlAsString : String, fIsThumbnail : Boolean, icc : ImageCacheCompleted?) {
        if (urlAsString.isEmpty())
            return

        val uri = URL(urlAsString)
        var d: Drawable? = null
        val image = this

        withContext(Dispatchers.IO) {
            try {
                val inputStream = uri.content as InputStream
                d = Drawable.createFromStream(inputStream, "src name")

                if (d != null) {
                    val bd = d as BitmapDrawable
                    val bmp = bd.bitmap
                    val s = ByteArrayOutputStream()
                    bmp.compress(
                        if (imageType == ImageFileType.PDF || imageType == ImageFileType.S3PDF) CompressFormat.PNG else CompressFormat.JPEG,
                        100,
                        s
                    )
                    if (fIsThumbnail) thumbnail = s.toByteArray() else mImgdata = s.toByteArray()
                } else null
            } catch (e: Exception) {
                Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e))
            }
        }

        withContext(Dispatchers.Main) {
            if (d != null) {
                iv?.setImageDrawable(d)
                icc?.imgCompleted(image)
            }
        }
    }

    //region Constructors
    constructor() : super()
    constructor(id: Long) : super() {
        this.id = id
    }

    constructor(pd: PictureDestination) : super() {
        setPictureDestination(pd)
    }

    constructor(pd: PictureDestination, id: Long) : super() {
        this.id = id
        setPictureDestination(pd)
    }

    //endregion
    /*
     * Note that an image can be BOTH local AND on server.
     */
    fun isLocal(): Boolean {
        return id > 0
    }

    fun isOnServer(): Boolean {
        return virtualPath.isNotEmpty()
    }

    private fun isVideo(): Boolean {
        return imageType == ImageFileType.S3VideoMP4
    }

    fun hasGeoTag(): Boolean {
        return location != null && (location!!.latitude != 0.0 || location!!.longitude != 0.0)
    }

    fun setKey(key: Int) {
        mKey = String.format(Locale.US, "%d", key)
    }

    fun setPictureDestination(pd: PictureDestination) {
        mPd = pd
        when (pd) {
            PictureDestination.FlightImage -> {
                mSzurl = MFBConstants.szURL_FlightPicture
                mKeyname = MFBConstants.szIMG_KEY_Flight
            }
            PictureDestination.AircraftImage -> {
                mSzurl = MFBConstants.szURL_AircraftPicture
                mKeyname = MFBConstants.szIMG_KEY_Aircraft
            }
        }
    }

    fun deletePendingImages(id: Long) {
        val db = MFBMain.mDBHelper!!.writableDatabase
        val szId = if (mPd == PictureDestination.FlightImage) "idFlight" else "idAircraft"
        var rgIds: LongArray? = null
        try {
            db.query(
                TABLENAME,
                arrayOf("_id"),
                "$szId = ?",
                arrayOf(String.format(Locale.US, "%d", id)),
                null,
                null,
                null
            ).use { c ->
                // Get each one individually so that the associated image file gets deleted too.
                rgIds = LongArray(c.count)
                var i = 0
                while (c.moveToNext()) rgIds[i++] = c.getLong(0)
            }
        } catch (e: Exception) {
            Log.e(MFBConstants.LOG_TAG, "Error deleting pending images: " + e.message)
        }
        if (rgIds != null) {
            for (idImg in rgIds) {
                val mfbii = MFBImageInfo(idImg)
                mfbii.deleteFromDB()
            }
        }
    }

    private val imagePrefix: String
        get() = String.format("mfb%s", mPd.toString())
    private val imageSuffix: String
        get() = if (isVideo()) EXTENSION_VID else EXTENSION_IMG
    val imageFile: String
        get() = String.format(Locale.US, "%s%d%s", imagePrefix, id, imageSuffix)
    private val absoluteImageFile: String
        get() = MFBMain.appFilesPath + "/" + imageFile

    fun toDB() {
        val cv = ContentValues()
        if (thumbnail != null && thumbnail!!.isNotEmpty()) cv.put("thmbData", thumbnail)
        cv.put("szComment", comment)
        if (targetID >= 0) cv.put(
            if (mPd == PictureDestination.FlightImage) "idFlight" else "idAircraft",
            targetID
        )
        cv.put("Width", mWidth)
        cv.put("Height", mHeight)
        cv.put("WidthThumbnail", mWidthThumbnail)
        cv.put("HeightThumbnail", mHeightThumbnail)
        cv.put("VirtualPath", virtualPath)
        cv.put("ThumbnailFile", thumbnailFile)
        cv.put("URLFullImage", mURLFullImage)
        cv.put("URLThumbnail", mURLThumbnail)
        cv.put("ImageType", imageType.ordinal)
        val db = MFBMain.mDBHelper!!.writableDatabase
        try {
            if (id < 0) id = db.insert(TABLENAME, null, cv) else db.update(
                TABLENAME, cv, "_id = ?", arrayOf(
                    String.format(Locale.US, "%d", id)
                )
            )

            // now need to save the image data, if present
            if (mImgdata != null && mImgdata!!.isNotEmpty()) {
                try {
                    FileOutputStream(absoluteImageFile).use { fos -> fos.write(mImgdata) }
                } catch (e: IOException) {
                    Log.e(
                        MFBConstants.LOG_TAG,
                        "Error saving image full file: " + e.message + Log.getStackTraceString(e)
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(
                MFBConstants.LOG_TAG,
                "Error adding image: " + e.message + Log.getStackTraceString(e)
            )
        }
    }

    fun deleteFromDB() {
        val db = MFBMain.mDBHelper!!.readableDatabase
        try {
            db.delete(TABLENAME, "_id = ?", arrayOf(String.format(Locale.US, "%d", id)))
            if (!File(absoluteImageFile).delete()) {
                Log.v(MFBConstants.LOG_TAG, "unable to delete image file from DB.")
            }
        } catch (ex: Exception) {
            Log.e(
                MFBConstants.LOG_TAG,
                String.format("Unable to delete image %d: %s", id, ex.message)
            )
        }
    }

    private fun fromCursor(c: Cursor, fGetThumb: Boolean, fGetFullImage: Boolean) {
        comment = c.getString(c.getColumnIndexOrThrow("szComment"))
        if (fGetThumb) thumbnail = c.getBlob(c.getColumnIndexOrThrow("thmbData"))
        if (fGetFullImage) {
            mImgdata = null
            try {
                FileInputStream(absoluteImageFile).use { fis ->
                    val baos = ByteArrayOutputStream()
                    val b = ByteArray(1024)
                    var bytesRead: Int
                    try {
                        while (fis.read(b).also { bytesRead = it } != -1) baos.write(
                            b,
                            0,
                            bytesRead
                        )
                        mImgdata = baos.toByteArray()
                    } catch (e: IOException) {
                        Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e))
                    }
                }
            } catch (e: IOException) {
                Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e))
            }
        }
        targetID =
            if (mPd == PictureDestination.FlightImage) c.getLong(c.getColumnIndexOrThrow("idFlight")) else c.getLong(c.getColumnIndexOrThrow("idAircraft"))
        mWidth = c.getInt(c.getColumnIndexOrThrow("Width"))
        mHeight = c.getInt(c.getColumnIndexOrThrow("Height"))
        mWidthThumbnail = c.getInt(c.getColumnIndexOrThrow("WidthThumbnail"))
        mHeightThumbnail = c.getInt(c.getColumnIndexOrThrow("HeightThumbnail"))
        virtualPath = c.getString(c.getColumnIndexOrThrow("VirtualPath"))
        thumbnailFile = c.getString(c.getColumnIndexOrThrow("ThumbnailFile"))
        mURLThumbnail = c.getString(c.getColumnIndexOrThrow("URLThumbnail"))
        mURLFullImage = c.getString(c.getColumnIndexOrThrow("URLFullImage"))
        imageType = ImageFileType.entries.toTypedArray()[c.getInt(c.getColumnIndexOrThrow("ImageType"))]
    }

    private fun fromDB(fGetThumb: Boolean, fGetFullImage: Boolean) {
        mImgdata = ByteArray(0)
        thumbnail = null

        // don't bother if it's going to miss.
        if (id <= 0) return
        val db = MFBMain.mDBHelper!!.readableDatabase
        val alColumns = ArrayList<String>()
        alColumns.add("szComment")
        alColumns.add("idFlight")
        alColumns.add("idAircraft")
        if (fGetThumb) alColumns.add("thmbData")
        if (fGetFullImage) alColumns.add("imageData")
        alColumns.add("Width")
        alColumns.add("Height")
        alColumns.add("WidthThumbnail")
        alColumns.add("HeightThumbnail")
        alColumns.add("VirtualPath")
        alColumns.add("ThumbnailFile")
        alColumns.add("URLFullImage")
        alColumns.add("URLThumbnail")
        alColumns.add("ImageType")
        val rgszColumns = arrayOfNulls<String>(alColumns.size)
        alColumns.toArray(rgszColumns)
        try {
            db.query(
                TABLENAME,
                rgszColumns,
                "_id = ?",
                arrayOf(String.format(Locale.US, "%d", id)),
                null,
                null,
                null
            ).use { c ->
                if (c.count > 0) {
                    c.moveToNext()
                    fromCursor(c, fGetThumb, fGetFullImage)
                }
            }
        } catch (ex: Exception) {
            Log.e(
                MFBConstants.LOG_TAG,
                String.format("Unable to load image %d: %s", id, ex.message)
            )
        }
    }

    private fun setMatrixOrientation(m: Matrix, o: Int) {
        when (o) {
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_NORMAL -> {}
            else -> {}
        }
    }

    fun initFromCamera(
        szFile: String?,
        curLoc: Location?,
        fVideo: Boolean,
        fDeleteFileWhenDone: Boolean
    ): Boolean {
        // Convert it to a Jpeg and geotag it.
        var fis: FileInputStream? = null
        var fResult = false
        var orientation = ExifInterface.ORIENTATION_NORMAL
        thumbnail = null
        mImgdata = thumbnail
        if (szFile.isNullOrEmpty()) return false
        val fTemp = File(szFile)
        if (!fTemp.exists()) return false
        if (fVideo) {
            imageType = ImageFileType.S3VideoMP4
            val b =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ThumbnailUtils.createVideoThumbnail(fTemp, Size(TH_WIDTH, TH_HEIGHT), null)
                } else {
                    @Suppress("DEPRECATION")
                    ThumbnailUtils.createVideoThumbnail(szFile, MediaStore.Images.Thumbnails.MINI_KIND)
                }
            if (b != null) {
                val bos = ByteArrayOutputStream()
                b.compress(CompressFormat.JPEG, 100, bos)
                thumbnail = bos.toByteArray()
                System.gc()
            }
        } else {
            var ei: ExifInterface? = null
            try {
                ei = ExifInterface(fTemp.absolutePath)
            } catch (_: IOException) {
            }

            // Geotag it, if necessary, and get rotation.
            if (ei != null) {
                try {
                    if (curLoc != null) {
                        if (ei.latLong == null) {
                            ei.setAttribute(
                                ExifInterface.TAG_GPS_LATITUDE,
                                MFBUtil.makeLatLongString(curLoc.latitude)
                            )
                            ei.setAttribute(
                                ExifInterface.TAG_GPS_LONGITUDE,
                                MFBUtil.makeLatLongString(curLoc.longitude)
                            )
                        }

                        // even if it already has a latlong, make sure that the latituderef is set:
                        ei.setAttribute(
                            ExifInterface.TAG_GPS_LATITUDE_REF,
                            if (curLoc.latitude < 0) "S" else "N"
                        )
                        ei.setAttribute(
                            ExifInterface.TAG_GPS_LONGITUDE_REF,
                            if (curLoc.longitude < 0) "W" else "E"
                        )
                        ei.saveAttributes()
                    }

                    // get the orientation
                    orientation = ei.getAttribute(ExifInterface.TAG_ORIENTATION)!!.toInt()
                } catch (e: Exception) {
                    Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e))
                }
            }

            // get or make a thumbnail, BEFORE we load all the bytes of the image into memory
            // if (ei != null)
            //	m_imgThumb = ei.getThumbnail();
            val m = Matrix()
            val bSrc: Bitmap?

            // create the thumbnail
            val th = thumbnail
            bSrc =
                if (th == null) BitmapFactory.decodeFile(szFile) else BitmapFactory.decodeByteArray(
                    th, 0, th.size
                )
            if (bSrc == null) return false
            val hOrig = bSrc.height
            val wOrig = bSrc.width
            m.preScale(TH_WIDTH.toFloat() / wOrig.toFloat(), TH_HEIGHT.toFloat() / hOrig.toFloat())
            setMatrixOrientation(m, orientation)
            val bThumb = Bitmap.createBitmap(bSrc, 0, 0, wOrig, hOrig, m, true)
            bSrc.recycle()
            System.gc() // free up some memory
            val bos = ByteArrayOutputStream()
            bThumb.compress(CompressFormat.JPEG, 100, bos)
            thumbnail = bos.toByteArray()
            bThumb.recycle()
            System.gc()
        }
        try {
            // now refresh the byte array
            fis = FileInputStream(fTemp)
            mImgdata = ByteArray(fTemp.length().toInt())
            if (fis.read(mImgdata) > 0) fResult = true
        } catch (e: Exception) {
            Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e))
        } finally {
            if (fis != null) {
                try {
                    fis.close()
                } catch (e: IOException) {
                    Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e))
                }
            }
            if (fDeleteFileWhenDone) {
                if (!fTemp.delete()) // delete the temp file.
                    Log.w(MFBConstants.LOG_TAG, "Delete of temp file failed")
            }
        }
        return fResult
    }

    fun bitmapFromThumb(): Bitmap? {
        return if (thumbnail == null || thumbnail!!.isEmpty()) null else BitmapFactory.decodeByteArray(
            thumbnail, 0, thumbnail!!.size
        )
    }

    private fun bitmapFromImage(): Bitmap? {
        return if (mImgdata == null || mImgdata!!.isEmpty()) null else BitmapFactory.decodeByteArray(
            mImgdata,
            0,
            mImgdata!!.size
        )
    }

    fun uploadPendingImage(idImg: Long, c: Context): Boolean {
        id = idImg
        fromDB(
            fGetThumb = false,
            fGetFullImage = false
        ) // actual bytes could be long.
        var fResult = false
        try {
            FileInputStream(absoluteImageFile).use { fis ->
                val szBase = "https://" + MFBConstants.szIP
                val szBoundary = UUID.randomUUID().toString()
                val szBoundaryDivider = String.format("--%s\r\n", szBoundary)
                val url = URL(szBase + mSzurl)
                val urlConnection = url.openConnection() as HttpURLConnection
                urlConnection.doOutput = true
                urlConnection.setChunkedStreamingMode(0)
                urlConnection.requestMethod = "POST"
                urlConnection.setRequestProperty(
                    "Content-Type",
                    String.format("multipart/form-data; boundary=%s", szBoundary)
                )
                try {
                    BufferedOutputStream(urlConnection.outputStream).use { out ->
                        if (mKey.isEmpty()) throw Exception("No valid key provided")
                        out.write(szBoundaryDivider.toByteArray(StandardCharsets.UTF_8))
                        out.write(
                            "Content-Disposition: form-data; name=\"txtAuthToken\"\r\n\r\n".toByteArray(
                                StandardCharsets.UTF_8
                            )
                        )
                        out.write(
                            String.format("%s\r\n%s", AuthToken.m_szAuthToken, szBoundaryDivider)
                                .toByteArray(
                                    StandardCharsets.UTF_8
                                )
                        )
                        out.write(
                            "Content-Disposition: form-data; name=\"txtComment\"\r\n\r\n".toByteArray(
                                StandardCharsets.UTF_8
                            )
                        )
                        out.write(
                            String.format("%s\r\n%s", comment, szBoundaryDivider).toByteArray(
                                StandardCharsets.UTF_8
                            )
                        )
                        out.write(
                            String.format(
                                "Content-Disposition: form-data; name=\"%s\"\r\n\r\n",
                                mKeyname
                            ).toByteArray(
                                StandardCharsets.UTF_8
                            )
                        )
                        out.write(
                            String.format("%s\r\n%s", mKey, szBoundaryDivider).toByteArray(
                                StandardCharsets.UTF_8
                            )
                        )
                        out.write(
                            String.format(
                                Locale.getDefault(),
                                "Content-Disposition: form-data; name=\"imgPicture\"; filename=\"myimage%s\"\r\n",
                                imageSuffix
                            ).toByteArray(
                                StandardCharsets.UTF_8
                            )
                        )
                        out.write(
                            String.format(
                                Locale.getDefault(),
                                "Content-Type: %s\r\nContent-Transfer-Encoding: binary\r\n\r\n",
                                if (isVideo()) "video/mp4" else "image/jpeg"
                            ).toByteArray(
                                StandardCharsets.UTF_8
                            )
                        )
                        val b = ByteArray(1024)
                        var bytesRead: Int
                        while (fis.read(b).also { bytesRead = it } != -1) out.write(b, 0, bytesRead)
                        out.write(
                            String.format("\r\n\r\n--%s--\r\n", szBoundary).toByteArray(
                                StandardCharsets.UTF_8
                            )
                        )
                        out.flush()
                        val status = urlConnection.responseCode
                        if (status != HttpURLConnection.HTTP_OK) throw Exception(
                            String.format(
                                Locale.US,
                                "Bad response - status = %d",
                                status
                            )
                        )
                        val rgResponse = ByteArray(1024)
                        try {
                            BufferedInputStream(urlConnection.inputStream).use { `in` ->
                                val cBytes = `in`.read(rgResponse)
                                Log.v(
                                    MFBConstants.LOG_TAG,
                                    String.format(
                                        Locale.US,
                                        "%d bytes read in uploadpendingimage",
                                        cBytes
                                    )
                                )
                            }
                        } catch (ex: Exception) {
                            Log.e(
                                MFBConstants.LOG_TAG,
                                "error uploading pending image: " + ex.message
                            )
                            throw ex
                        }
                        val sz = String(rgResponse, StandardCharsets.UTF_8)
                        if (!sz.contains("OK")) throw Exception(sz)
                        fResult = true
                    }
                } catch (ex: Exception) {
                    val szErr = ex.message
                    Log.e(MFBConstants.LOG_TAG, "Error uploading image: $szErr")
                    val h = Handler(c.mainLooper)
                    h.post { MFBUtil.alert(c, c.getString(R.string.txtError), szErr) }
                } finally {
                    urlConnection.disconnect()
                    mImgdata = null // free up a potentially large block of memory
                }
            }
        } catch (e: IOException) {
            Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e))
        }
        return fResult
    }

    /*
     * Serialization
	 */
    override fun getPropertyCount(): Int {
        return FPProp.entries.size
    }

    override fun getProperty(arg0: Int): Any? {
        return when (FPProp.entries[arg0]) {
            FPProp.PIDComment -> comment
            FPProp.PIDVirtualPath -> virtualPath
            FPProp.PIDThumbnailFile -> thumbnailFile
            FPProp.PIDURLFullImage -> mURLFullImage
            FPProp.PIDURLThumbnail -> mURLThumbnail
            FPProp.PIDWidth -> mWidth
            FPProp.PIDHeight -> mHeight
            FPProp.PIDTHWidth -> mWidthThumbnail
            FPProp.PIDTHHeight -> mHeightThumbnail
            FPProp.PIDLocation -> location
            FPProp.PIDImageType -> imageType.toString()
        }
    }

    override fun getPropertyInfo(arg0: Int, h: Hashtable<*, *>?, pi: PropertyInfo) {
        when (FPProp.entries[arg0]) {
            FPProp.PIDComment -> {
                pi.type = PropertyInfo.STRING_CLASS
                pi.name = "Comment"
            }
            FPProp.PIDVirtualPath -> {
                pi.type = PropertyInfo.STRING_CLASS
                pi.name = "VirtualPath"
            }
            FPProp.PIDThumbnailFile -> {
                pi.type = PropertyInfo.STRING_CLASS
                pi.name = "ThumbnailFile"
            }
            FPProp.PIDURLThumbnail -> {
                pi.type = PropertyInfo.STRING_CLASS
                pi.name = "URLThumbnail"
            }
            FPProp.PIDURLFullImage -> {
                pi.type = PropertyInfo.STRING_CLASS
                pi.name = "URLFullImage"
            }
            FPProp.PIDWidth -> {
                pi.type = PropertyInfo.INTEGER_CLASS
                pi.name = "Width"
            }
            FPProp.PIDHeight -> {
                pi.type = PropertyInfo.INTEGER_CLASS
                pi.name = "Height"
            }
            FPProp.PIDTHWidth -> {
                pi.type = PropertyInfo.INTEGER_CLASS
                pi.name = "WidthThumbnail"
            }
            FPProp.PIDTHHeight -> {
                pi.type = PropertyInfo.INTEGER_CLASS
                pi.name = "HeightThumbnail"
            }
            FPProp.PIDLocation -> {
                pi.type = LatLong::class.java
                pi.name = "Location"
            }
            FPProp.PIDImageType -> {
                pi.type = PropertyInfo.STRING_CLASS
                pi.name = "ImageType"
            }
        }
    }

    override fun setProperty(arg0: Int, arg1: Any) {
        val f = FPProp.entries[arg0]
        val sz = arg1.toString()
        when (f) {
            FPProp.PIDComment -> comment = sz
            FPProp.PIDVirtualPath -> virtualPath = sz
            FPProp.PIDThumbnailFile -> thumbnailFile = sz
            FPProp.PIDURLFullImage -> mURLFullImage = sz
            FPProp.PIDURLThumbnail -> mURLThumbnail = sz
            FPProp.PIDWidth -> mWidth = sz.toInt()
            FPProp.PIDHeight -> mHeight = sz.toInt()
            FPProp.PIDTHWidth -> mWidthThumbnail = sz.toInt()
            FPProp.PIDTHHeight -> mHeightThumbnail = sz.toInt()
            FPProp.PIDImageType -> imageType = ImageFileType.valueOf(sz)
            FPProp.PIDLocation -> {}
        }
    }

    override fun toProperties(so: SoapObject) {
        so.addProperty("Comment", comment)
        so.addProperty("VirtualPath", virtualPath)
        so.addProperty("URLFullImage", mURLFullImage)
        so.addProperty("URLThumbnail", mURLThumbnail)
        so.addProperty("ThumbnailFile", thumbnailFile)
        so.addProperty("Width", mWidth)
        so.addProperty("Height", mHeight)
        so.addProperty("WidthThumbnail", mWidthThumbnail)
        so.addProperty("HeightThumbnail", mHeightThumbnail)
        so.addProperty("Location", location)
        so.addProperty("ImageType", imageType)
    }

    public override fun fromProperties(so: SoapObject) {
        comment = readNullableString(so, "Comment")
        virtualPath = readNullableString(so, "VirtualPath")
        mURLThumbnail = readNullableString(so, "URLThumbnail")
        mURLFullImage = readNullableString(so, "URLFullImage")
        thumbnailFile = readNullableString(so, "ThumbnailFile")
        mWidth = so.getProperty("Width").toString().toInt()
        mHeight = so.getProperty("Height").toString().toInt()
        mWidthThumbnail = so.getProperty("WidthThumbnail").toString().toInt()
        mHeightThumbnail = so.getProperty("HeightThumbnail").toString().toInt()
        if (so.hasProperty("Location")) {
            val location = so.getProperty("Location") as SoapObject
            this.location = LatLong()
            this.location!!.fromProperties(location)
        }
        if (so.hasProperty("ImageType")) imageType =
            ImageFileType.valueOf(so.getProperty("ImageType").toString())
    }

    private fun getURLFullImage(): String {
        return String.format(Locale.US, "https://%s%s", MFBConstants.szIP, mURLFullImage)
    }

    private fun getURLThumbnail(): String {
        return if (mURLThumbnail.startsWith("/")) String.format(
            Locale.US,
            "https://%s%s",
            MFBConstants.szIP,
            mURLThumbnail
        ) else mURLThumbnail
    }

    suspend fun loadImageAsync(fThumbnail: Boolean, delegate: ImageCacheCompleted?) {
        // return if we already have everything cached.
        if (fThumbnail && thumbnail != null && thumbnail!!.isNotEmpty()) return
        if (!fThumbnail && mImgdata != null && mImgdata!!.isNotEmpty()) return
        loadURLAsync(null, if (fThumbnail) getURLThumbnail() else getURLFullImage(), fThumbnail, delegate)
    }

    /*
     * Asynchronously fill an imageview with thumbnail or full image
     * Uses from the database, if necessary.
     */
    suspend fun loadImageForImageView(fThumbnail: Boolean, i: ImageView) {
        // if it's local, try to pull the image from the db first.  Note that his could fail.
        if (isLocal()) fromDB(fThumbnail, !fThumbnail)
        if (fThumbnail && thumbnail != null && thumbnail!!.isNotEmpty()) {
            withContext(Dispatchers.Main) {
                i.setImageBitmap(bitmapFromThumb())
            }
            return
        }
        if (!fThumbnail && mImgdata != null && mImgdata!!.isNotEmpty()) {
            withContext(Dispatchers.Main) {
                i.setImageBitmap(bitmapFromImage())
            }
            return
        }
        loadURLAsync(i, if (fThumbnail) getURLThumbnail() else getURLFullImage(), fThumbnail, null)
    }

    fun viewFullImageInWebView(a: Activity) {
        if (mURLFullImage.isNotEmpty()) {
            // Image is on server - easy - just use webview
            ActWebView.viewURL(a, getURLFullImage())
        } else {
            // Image is local only - need to write it out to a temp file

            // Ensure that we have the full bytes.
            if (mImgdata == null || mImgdata!!.isEmpty()) fromDB(
                fGetThumb = true,
                fGetFullImage = true
            )

            // Write the full image to a file
            try {
                val fTemp = File.createTempFile(
                    "tempView",
                    imageSuffix,
                    a.cacheDir
                )
                fTemp.deleteOnExit()
                val fos = FileOutputStream(fTemp)
                try {
                    fos.write(mImgdata)
                    fos.close()
                    mImgdata = null // clean up for memory
                    System.gc()
                    ActWebView.viewTempFile(a, fTemp)
                } catch (e: IOException) {
                    Log.e(MFBConstants.LOG_TAG, "Error saving image full file: " + e.message)
                    Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e))
                }
            } catch (e: IOException) {
                Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e))
            }
        }
    }

    companion object {
        @Suppress("UNUSED")
        private const val serialVersionUID = 1L
        private const val TH_WIDTH = 150
        private const val TH_HEIGHT = 120
        private const val EXTENSION_IMG = ".jpg"
        private const val EXTENSION_VID = ".3gp"
        const val idImageGalleryIdBase = -2000
        const val TABLENAME = "PicturesToPost"
        @JvmStatic
        fun getLocalImagesForId(id: Long, pd: PictureDestination): Array<MFBImageInfo> {
            val db = MFBMain.mDBHelper!!.writableDatabase
            val szId = if (pd == PictureDestination.FlightImage) "idFlight" else "idAircraft"
            val rgMfbii = ArrayList<MFBImageInfo>()
            try {
                db.query(
                    TABLENAME,
                    null,
                    "$szId = ?",
                    arrayOf(String.format(Locale.US, "%d", id)),
                    null,
                    null,
                    null
                ).use { c ->
                    while (c.moveToNext()) {
                        val mfbii = MFBImageInfo(pd, c.getLong(c.getColumnIndexOrThrow("_id")))
                        mfbii.fromCursor(
                            c,
                            fGetThumb = true,
                            fGetFullImage = false
                        ) // initialize it with the thumbnail.
                        rgMfbii.add(mfbii)
                    }
                }
            } catch (e: Exception) {
                Log.e(MFBConstants.LOG_TAG, "Error getting images for id: " + e.message)
            }
            return rgMfbii.toTypedArray()
        }

        // no thumbnail
        @JvmStatic
        val allAircraftImages: Array<MFBImageInfo>
            get() {
                val db = MFBMain.mDBHelper!!.writableDatabase
                val rgMfbii = ArrayList<MFBImageInfo>()
                try {
                    db.query(TABLENAME, null, "idAircraft > 0", null, null, null, null).use { c ->
                        while (c.moveToNext()) {
                            val mfbii = MFBImageInfo(
                                PictureDestination.AircraftImage,
                                c.getLong(c.getColumnIndexOrThrow("_id"))
                            )
                            mfbii.fromCursor(
                                c,
                                fGetThumb = false,
                                fGetFullImage = false
                            ) // no thumbnail
                            rgMfbii.add(mfbii)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(MFBConstants.LOG_TAG, "Error getting aircraft images: " + e.message)
                }
                return rgMfbii.toTypedArray()
            }

        fun getAircraftImagesForId(
            idAircraft: Int,
            rgAircraftImages: Array<MFBImageInfo>
        ): Array<MFBImageInfo> {
            val al = ArrayList<MFBImageInfo>()
            for (mfbii in rgAircraftImages) {
                if (mfbii.targetID == idAircraft.toLong()) al.add(mfbii)
            }
            return al.toTypedArray()
        }

        /*
     * Delete any image file matching the specified destination that is not in the specified list.
     */
        @JvmStatic
        fun deleteOrphansNotInList(
            pd: PictureDestination,
            alImages: ArrayList<String?>,
            c: Context
        ) {
            val mfbii = MFBImageInfo(pd)
            val szPrefix = mfbii.imagePrefix
            val szSuffix = mfbii.imageSuffix
            Log.w(MFBConstants.LOG_TAG, String.format("Delete orphans for %s", pd.toString()))
            // Get a list of all the files
            val rgszFiles = c.fileList()

            // Now delete any images that match the prefix but which aren't in our list.
            for (szFile in rgszFiles) if (szFile.startsWith(szPrefix) && szFile.endsWith(szSuffix) && !alImages.contains(
                    szFile
                )
            ) {
                Log.e(MFBConstants.LOG_TAG, "ORPHAN FOUND TO DELETE: $szFile")
                c.deleteFile(szFile)
            }
        }

        private fun resizeRatio(maxHeight: Int, maxWidth: Int, Height: Int, Width: Int): Float {
            val ratioX = maxWidth.toFloat() / Width.toFloat()
            val ratioY = maxHeight.toFloat() / Height.toFloat()
            var minRatio = 1.0f
            if (ratioX < 1.0 || ratioY < 1.0) {
                minRatio = ratioX.coerceAtMost(ratioY)
            }
            return minRatio
        }

        // below is adapted from http://stackoverflow.com/questions/11012556/border-over-a-bitmap-with-rounded-corners-in-android - thanks!
        // That in turn came from http://ruibm.com/?p=184
        fun getRoundedCornerBitmap(
            bitmap: Bitmap,
            color: Int,
            cornerDips: Int,
            borderDips: Int,
            maxHeight: Int,
            maxWidth: Int,
            context: Context
        ): Bitmap {
            val wSrc = bitmap.width
            val hSrc = bitmap.height
            val scaledMaxHeight = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                maxHeight.toFloat(),
                context.resources.displayMetrics
            )
            val scaledMaxWidth = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                maxWidth.toFloat(),
                context.resources.displayMetrics
            )
            val resizeRatio =
                resizeRatio(scaledMaxHeight.toInt(), scaledMaxWidth.toInt(), hSrc, wSrc)
            val wScaled = (wSrc * resizeRatio).toInt()
            val hScaled = (hSrc * resizeRatio).toInt()
            val rectSrc = Rect(0, 0, wSrc, hSrc)
            val rectDst = Rect(0, 0, wScaled, hScaled)
            val output = createBitmap(wScaled, hScaled)
            val canvas = Canvas(output)
            val borderSizePx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, borderDips.toFloat(),
                context.resources.displayMetrics
            ).toInt()
            val cornerSizePx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, cornerDips.toFloat(),
                context.resources.displayMetrics
            ).toInt()
            //	    int borderSizePx = borderDips;
//	    int cornerSizePx = cornerDips;
            val paint = Paint()
            val rectF = RectF(rectDst)

            // prepare canvas for transfer
            paint.isAntiAlias = true
            paint.color = -0x1
            paint.style = Paint.Style.FILL
            canvas.drawARGB(0, 0, 0, 0)
            canvas.drawRoundRect(rectF, cornerSizePx.toFloat(), cornerSizePx.toFloat(), paint)

            // draw bitmap
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(bitmap, rectSrc, rectDst, paint)

            // draw border
            paint.color = color
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = borderSizePx.toFloat()
            canvas.drawRoundRect(rectF, cornerSizePx.toFloat(), cornerSizePx.toFloat(), paint)
            return output
        }
    }
}