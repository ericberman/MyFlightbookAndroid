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
package model;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.media.ThumbnailUtils;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;
import android.util.TypedValue;
import android.widget.ImageView;

import com.myflightbook.android.ActWebView;
import com.myflightbook.android.MFBMain;
import com.myflightbook.android.R;
import com.myflightbook.android.webservices.AuthToken;

import org.ksoap2.serialization.KvmSerializable;
import org.ksoap2.serialization.PropertyInfo;
import org.ksoap2.serialization.SoapObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import androidx.exifinterface.media.ExifInterface;

public class MFBImageInfo extends SoapableObject implements KvmSerializable, Serializable {

    private static final long serialVersionUID = 1L;
    private static final int TH_WIDTH = 150;
    private static final int TH_HEIGHT = 120;
    private static final String EXTENSION_IMG = ".jpg";
    private static final String EXTENSION_VID = ".3gp";

    public static final int idImageGalleryIdBase = -2000;

    // for serialization
    private enum FPProp {
        pidComment, pidVirtualPath, pidThumbnailFile, pidURLThumbnail, pidURLFullImage, pidWidth, pidHeight, pidTHWidth, pidTHHeight, pidLocation, pidImageType
    }

    private enum ImageFileType {JPEG, PDF, S3PDF, S3VideoMP4}

    private int Width;
    private int Height;
    private int WidthThumbnail;
    private int HeightThumbnail;
    public String Comment = "";
    private String VirtualPath = "";
    public String ThumbnailFile = "";
    private String URLFullImage = "";
    private String URLThumbnail = "";
    public LatLong Location;
    private ImageFileType ImageType = ImageFileType.JPEG;

    public static final String TABLENAME = "PicturesToPost";

    private PictureDestination m_pd = PictureDestination.FlightImage;

    private byte[] m_imgData = null;
    private byte[] m_imgThumb = null;
    private long m_id = -1;
    private long m_idTarget = -1;

    public enum PictureDestination {FlightImage, AircraftImage}

    private String m_szURL = MFBConstants.szURL_FlightPicture;
    private String m_keyName = MFBConstants.szIMG_KEY_Flight;
    private String m_key = "";

    public interface ImageCacheCompleted {
        void imgCompleted(MFBImageInfo sender);
    }

    private static class AsyncLoadURL extends AsyncTask<Void, Void, Drawable> {
        private final String m_URL;
        private final WeakReference<ImageView> imgView;
        private final Boolean mFIsThumbnail;
        private final ImageCacheCompleted m_icc;
        private final WeakReference<MFBImageInfo>  m_mfbii;

        AsyncLoadURL(ImageView iv, String url, Boolean fIsThumnbnail, ImageCacheCompleted icc, MFBImageInfo mfbii) {
            super();
            imgView = new WeakReference<>(iv);
            m_mfbii = new WeakReference<>(mfbii);
            m_URL = url;
            mFIsThumbnail = fIsThumnbnail;
            m_icc = icc;
        }

        @Override
        protected Drawable doInBackground(Void... params) {
            Drawable d = null;
            try {
                InputStream is = (InputStream) new URL(m_URL).getContent();
                d = Drawable.createFromStream(is, "src name");
                if (d != null) {
                    MFBImageInfo mfbii = m_mfbii.get();

                    if (mfbii != null) {
                        BitmapDrawable bd = (BitmapDrawable) d;
                        Bitmap bmp = bd.getBitmap();
                        ByteArrayOutputStream s = new ByteArrayOutputStream();
                        bmp.compress((mfbii.ImageType == ImageFileType.PDF || mfbii.ImageType == ImageFileType.S3PDF) ? CompressFormat.PNG : Bitmap.CompressFormat.JPEG, 100, s);
                        if (mFIsThumbnail)
                            mfbii.m_imgThumb = s.toByteArray();
                        else
                            mfbii.m_imgData = s.toByteArray();
                    }
                }
            } catch (Exception e) {
                Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e));
            }
            return d;
        }

        protected void onPreExecute() {
        }

        protected void onPostExecute(Drawable d) {
            if (d != null) {
                ImageView iv = imgView.get();
                if (iv != null)
                    iv.setImageDrawable(d);

                if (m_icc != null)
                    m_icc.imgCompleted(m_mfbii.get());
            }
        }
    }

    //region Constructors
    public MFBImageInfo() {
        super();
    }

    public MFBImageInfo(long id) {
        super();
        setID(id);
    }

    public MFBImageInfo(PictureDestination pd) {
        super();
        setPictureDestination(pd);
    }

    public MFBImageInfo(PictureDestination pd, long id) {
        super();
        setID(id);
        setPictureDestination(pd);
    }
    //endregion

    /*
     * Note that an image can be BOTH local AND on server.
     */
    public Boolean IsLocal() {
        return m_id > 0;
    }

    public Boolean IsOnServer() {
        return VirtualPath.length() > 0;
    }

    private Boolean IsVideo() {
        return ImageType == ImageFileType.S3VideoMP4;
    }

    public Boolean HasGeoTag() {
        return this.Location != null && (this.Location.Latitude != 0 || this.Location.Longitude != 0);
    }

    public void setKey(int key) {
        m_key = String.format(Locale.US, "%d", key);
    }

    private void setID(long id) {
        m_id = id;
    }

    public long getID() {
        return m_id;
    }

    public void setTargetID(long id) {
        m_idTarget = id;
    }

    private long getTargetID() {
        return m_idTarget;
    }

    public void setPictureDestination(PictureDestination pd) {
        m_pd = pd;
        switch (pd) {
            case FlightImage:
                m_szURL = MFBConstants.szURL_FlightPicture;
                m_keyName = MFBConstants.szIMG_KEY_Flight;
                break;
            case AircraftImage:
                m_szURL = MFBConstants.szURL_AircraftPicture;
                m_keyName = MFBConstants.szIMG_KEY_Aircraft;
                break;
        }
    }

    public void DeletePendingImages(long id) {
        SQLiteDatabase db = MFBMain.mDBHelper.getWritableDatabase();
        String szId = (m_pd == PictureDestination.FlightImage) ? "idFlight" : "idAircraft";
        long[] rgIds = null;

        try (Cursor c = db.query(TABLENAME, new String[]{"_id"}, szId + " = ?", new String[]{String.format(Locale.US, "%d", id)}, null, null, null)) {
            // Get each one individually so that the associated image file gets deleted too.
            if (c != null) {
                rgIds = new long[c.getCount()];
                int i = 0;
                while (c.moveToNext())
                    rgIds[i++] = c.getLong(0);
            }
        } catch (Exception e) {
            Log.e(MFBConstants.LOG_TAG, "Error deleting pending images: " + e.getMessage());
        }

        if (rgIds != null) {
            for (long idImg : rgIds) {
                MFBImageInfo mfbii = new MFBImageInfo(idImg);
                mfbii.deleteFromDB();
            }
        }
    }

    public static MFBImageInfo[] getLocalImagesForId(long id, PictureDestination pd) {
        SQLiteDatabase db = MFBMain.mDBHelper.getWritableDatabase();
        String szId = (pd == PictureDestination.FlightImage) ? "idFlight" : "idAircraft";
        MFBImageInfo[] rgMfbii = new MFBImageInfo[0];

        try (Cursor c = db.query(MFBImageInfo.TABLENAME, null, szId + " = ?", new String[]{String.format(Locale.US, "%d", id)}, null, null, null)) {
            if (c != null) {

                rgMfbii = new MFBImageInfo[c.getCount()];
                int i = 0;
                while (c.moveToNext()) {
                    MFBImageInfo mfbii = new MFBImageInfo(pd, c.getLong(c.getColumnIndexOrThrow("_id")));
                    mfbii.fromCursor(c, true, false); // initialize it with the thumbnail.
                    rgMfbii[i++] = mfbii;
                }
            }
        } catch (Exception e) {
            Log.e(MFBConstants.LOG_TAG, "Error getting images for id: " + e.getMessage());
        }

        return rgMfbii;
    }

    public static MFBImageInfo[] getAllAircraftImages() {
        SQLiteDatabase db = MFBMain.mDBHelper.getWritableDatabase();
        MFBImageInfo[] rgMfbii = new MFBImageInfo[0];

        try (Cursor c = db.query(MFBImageInfo.TABLENAME, null, "idAircraft > 0", null, null, null, null)) {
            if (c != null) {

                rgMfbii = new MFBImageInfo[c.getCount()];
                int i = 0;
                while (c.moveToNext()) {
                    MFBImageInfo mfbii = new MFBImageInfo(PictureDestination.AircraftImage, c.getLong(c.getColumnIndexOrThrow("_id")));
                    mfbii.fromCursor(c, false, false); // no thumbnail
                    rgMfbii[i++] = mfbii;
                }
            }
        } catch (Exception e) {
            Log.e(MFBConstants.LOG_TAG, "Error getting aircraft images: " + e.getMessage());
        }

        return rgMfbii;
    }

    public static MFBImageInfo[] getAircraftImagesForId(int idAircraft, MFBImageInfo[] rgAircraftImages) {
        ArrayList<MFBImageInfo> al = new ArrayList<>();

        for (MFBImageInfo mfbii : rgAircraftImages) {
            if (mfbii.getTargetID() == idAircraft)
                al.add(mfbii);
        }
        return al.toArray(new MFBImageInfo[0]);
    }

    private String getImagePrefix() {
        return String.format("mfb%s", m_pd.toString());
    }

    private String getImageSuffix() {
        return IsVideo() ? EXTENSION_VID : EXTENSION_IMG;
    }

    public String getImageFile() {
        return String.format(Locale.US, "%s%d%s", getImagePrefix(), m_id, getImageSuffix());
    }

    private String getAbsoluteImageFile() {
        return MFBMain.getAppFilesPath() + "/" + getImageFile();
    }

    /*
     * Delete any image file matching the specified destination that is not in the specified list.
     */
    public static void DeleteOrphansNotInList(PictureDestination pd, ArrayList<String> alImages, Context c) {
        MFBImageInfo mfbii = new MFBImageInfo(pd);
        String szPrefix = mfbii.getImagePrefix();
        String szSuffix = mfbii.getImageSuffix();

        Log.w(MFBConstants.LOG_TAG, String.format("Delete orphans for %s", pd.toString()));
        // Get a list of all the files
        String[] rgszFiles = c.fileList();

        // Now delete any images that match the prefix but which aren't in our list.
        for (String szFile : rgszFiles)
            if (szFile.startsWith(szPrefix) && szFile.endsWith(szSuffix) && !alImages.contains(szFile)) {
                Log.e(MFBConstants.LOG_TAG, "ORPHAN FOUND TO DELETE: " + szFile);
                c.deleteFile(szFile);
            }
    }

    public void toDB() {
        ContentValues cv = new ContentValues();

        if (m_imgThumb != null && m_imgThumb.length > 0)
            cv.put("thmbData", m_imgThumb);
        cv.put("szComment", Comment);
        if (m_idTarget >= 0)
            cv.put((m_pd == PictureDestination.FlightImage) ? "idFlight" : "idAircraft", m_idTarget);

        cv.put("Width", Width);
        cv.put("Height", Height);
        cv.put("WidthThumbnail", WidthThumbnail);
        cv.put("HeightThumbnail", HeightThumbnail);
        cv.put("VirtualPath", VirtualPath);
        cv.put("ThumbnailFile", ThumbnailFile);
        cv.put("URLFullImage", URLFullImage);
        cv.put("URLThumbnail", URLThumbnail);
        cv.put("ImageType", ImageType.ordinal());

        SQLiteDatabase db = MFBMain.mDBHelper.getWritableDatabase();
        try {
            if (m_id < 0)
                m_id = db.insert(MFBImageInfo.TABLENAME, null, cv);
            else
                db.update(MFBImageInfo.TABLENAME, cv, "_id = ?", new String[]{String.format(Locale.US, "%d", m_id)});

            // now need to save the image data, if present
            if (m_imgData != null && m_imgData.length > 0) {
                try (FileOutputStream fos = new FileOutputStream(getAbsoluteImageFile())) {
                    fos.write(m_imgData);
                } catch (IOException e) {
                    Log.e(MFBConstants.LOG_TAG, "Error saving image full file: " + e.getMessage() + Log.getStackTraceString(e));
                }
            }
        } catch (Exception e) {
            Log.e(MFBConstants.LOG_TAG, "Error adding image: " + e.getMessage() + Log.getStackTraceString(e));
        }
    }

    public void deleteFromDB() {
        SQLiteDatabase db = MFBMain.mDBHelper.getReadableDatabase();
        try {
            db.delete(TABLENAME, "_id = ?", new String[]{String.format(Locale.US, "%d", m_id)});
            if (!(new File(getAbsoluteImageFile()).delete())) {
                Log.v(MFBConstants.LOG_TAG, "unable to delete image file from DB.");
            }
        } catch (Exception ex) {
            Log.e(MFBConstants.LOG_TAG, String.format("Unable to delete image %d: %s", m_id, ex.getMessage()));
        }
    }

    private void fromCursor(Cursor c, Boolean fGetThumb, Boolean fGetFullImage) {
        Comment = c.getString(c.getColumnIndexOrThrow("szComment"));
        if (fGetThumb)
            m_imgThumb = c.getBlob(c.getColumnIndexOrThrow("thmbData"));
        if (fGetFullImage) {
            m_imgData = null;
            try (FileInputStream fis = new FileInputStream(getAbsoluteImageFile())) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] b = new byte[1024];
                int bytesRead;
                try {
                    while ((bytesRead = fis.read(b)) != -1)
                        baos.write(b, 0, bytesRead);
                    m_imgData = baos.toByteArray();
                } catch (IOException e) {
                    Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e));
                }
            } catch (IOException e) {
                Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e));
            }
        }
        if (m_pd == PictureDestination.FlightImage)
            m_idTarget = c.getLong(c.getColumnIndexOrThrow("idFlight"));
        else
            m_idTarget = c.getLong(c.getColumnIndexOrThrow("idAircraft"));

        Width = c.getInt(c.getColumnIndexOrThrow("Width"));
        Height = c.getInt(c.getColumnIndexOrThrow("Height"));
        WidthThumbnail = c.getInt(c.getColumnIndexOrThrow("WidthThumbnail"));
        HeightThumbnail = c.getInt(c.getColumnIndexOrThrow("HeightThumbnail"));
        VirtualPath = c.getString(c.getColumnIndexOrThrow("VirtualPath"));
        ThumbnailFile = c.getString(c.getColumnIndexOrThrow("ThumbnailFile"));
        URLThumbnail = c.getString(c.getColumnIndexOrThrow("URLThumbnail"));
        URLFullImage = c.getString(c.getColumnIndexOrThrow("URLFullImage"));
        ImageType = ImageFileType.values()[c.getInt(c.getColumnIndexOrThrow("ImageType"))];
    }

    private void fromDB(Boolean fGetThumb, Boolean fGetFullImage) {
        m_imgData = new byte[0];
        m_imgThumb = null;

        // don't bother if it's going to miss.
        if (m_id <= 0)
            return;

        SQLiteDatabase db = MFBMain.mDBHelper.getReadableDatabase();
        ArrayList<String> alColumns = new ArrayList<>();
        alColumns.add("szComment");
        alColumns.add("idFlight");
        alColumns.add("idAircraft");
        if (fGetThumb)
            alColumns.add("thmbData");
        if (fGetFullImage)
            alColumns.add("imageData");

        alColumns.add("Width");
        alColumns.add("Height");
        alColumns.add("WidthThumbnail");
        alColumns.add("HeightThumbnail");
        alColumns.add("VirtualPath");
        alColumns.add("ThumbnailFile");
        alColumns.add("URLFullImage");
        alColumns.add("URLThumbnail");
        alColumns.add("ImageType");


        String[] rgszColumns = new String[alColumns.size()];
        alColumns.toArray(rgszColumns);

        try (Cursor c = db.query(TABLENAME, rgszColumns, "_id = ?", new String[]{String.format(Locale.US, "%d", m_id)}, null, null, null)) {
            if (c != null && c.getCount() > 0) {
                c.moveToNext();
                fromCursor(c, fGetThumb, fGetFullImage);
            }
        } catch (Exception ex) {
            Log.e(MFBConstants.LOG_TAG, String.format("Unable to load image %d: %s", m_id, ex.getMessage()));
        }
    }

    private void setMatrixOrientation(Matrix m, int o) {
        switch (o) {
            case ExifInterface.ORIENTATION_ROTATE_180:
                m.postRotate(180);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                m.postRotate(270);
                break;
            case ExifInterface.ORIENTATION_ROTATE_90:
                m.postRotate(90);
                break;
            case ExifInterface.ORIENTATION_NORMAL:
            default:
                break;
        }
    }

    public Boolean initFromCamera(String szFile, Location curLoc, Boolean fVideo, Boolean fDeleteFileWhenDone) {
        // Convert it to a Jpeg and geotag it.
        FileInputStream fis = null;
        boolean fResult = false;
        int orientation = ExifInterface.ORIENTATION_NORMAL;
        m_imgData = m_imgThumb = null;

        if (szFile == null || szFile.length() == 0)
            return false;

        File fTemp = new File(szFile);
        if (!fTemp.exists())
            return false;

        if (fVideo) {
            this.ImageType = MFBImageInfo.ImageFileType.S3VideoMP4;

            Bitmap b = ThumbnailUtils.createVideoThumbnail(szFile, MediaStore.Images.Thumbnails.MINI_KIND);
            if (b != null) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                b.compress(CompressFormat.JPEG, 100, bos);
                m_imgThumb = bos.toByteArray();
                System.gc();
            }
        } else {
            ExifInterface ei = null;
            try {
                ei = new ExifInterface(fTemp.getAbsolutePath());
            } catch (IOException ignored) {
            }

            // Geotag it, if necessary, and get rotation.
            if (ei != null) {
                try {
                    if (curLoc != null) {
                        if (ei.getLatLong() == null) {
                            ei.setAttribute(ExifInterface.TAG_GPS_LATITUDE, MFBUtil.makeLatLongString(curLoc.getLatitude()));
                            ei.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, MFBUtil.makeLatLongString(curLoc.getLongitude()));
                        }

                        // even if it already has a latlong, make sure that the latituderef is set:
                        ei.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, curLoc.getLatitude() < 0 ? "S" : "N");
                        ei.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, curLoc.getLongitude() < 0 ? "W" : "E");
                        ei.saveAttributes();
                    }

                    // get the orientation
                    orientation = Integer.parseInt(Objects.requireNonNull(ei.getAttribute(ExifInterface.TAG_ORIENTATION)));
                } catch (Exception e) {
                    Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e));
                }
            }

            // get or make a thumbnail, BEFORE we load all the bytes of the image into memory
            // if (ei != null)
            //	m_imgThumb = ei.getThumbnail();
            Matrix m = new Matrix();
            Bitmap bSrc;

            // create the thumbnail
            bSrc = (m_imgThumb == null) ? BitmapFactory.decodeFile(szFile) : BitmapFactory.decodeByteArray(m_imgThumb, 0, m_imgThumb.length);
            if (bSrc == null)
                return false;
            int hOrig = bSrc.getHeight();
            int wOrig = bSrc.getWidth();
            m.preScale(((float) TH_WIDTH / (float) wOrig), ((float) TH_HEIGHT / (float) hOrig));

            setMatrixOrientation(m, orientation);

            Bitmap bThumb = Bitmap.createBitmap(bSrc, 0, 0, wOrig, hOrig, m, true);
            bSrc.recycle();
            System.gc(); // free up some memory
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bThumb.compress(CompressFormat.JPEG, 100, bos);
            m_imgThumb = bos.toByteArray();
            bThumb.recycle();
            System.gc();
        }

        try {
            // now refresh the byte array
            fis = new FileInputStream(fTemp);
            m_imgData = new byte[(int) fTemp.length()];
            if (fis.read(m_imgData) > 0)
                fResult = true;
        } catch (Exception e) {
            Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e));
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e));
                }
            }
            if (fDeleteFileWhenDone) {
                if (!fTemp.delete())  // delete the temp file.
                    Log.w(MFBConstants.LOG_TAG, "Delete of temp file failed");
            }
        }

        return fResult;
    }

    public Bitmap bitmapFromThumb() {
        if (m_imgThumb == null || m_imgThumb.length == 0)
            return null;

        return BitmapFactory.decodeByteArray(m_imgThumb, 0, m_imgThumb.length);
    }

    private Bitmap bitmapFromImage() {
        if (m_imgData == null || m_imgData.length == 0)
            return null;

        return BitmapFactory.decodeByteArray(m_imgData, 0, m_imgData.length);
    }

    public byte[] getThumbnail() {
        return m_imgThumb;
    }

    public Boolean UploadPendingImage(long idImg, Context c) {
        this.setID(idImg);
        fromDB(false, false);    // actual bytes could be long.

        boolean fResult = false;

        try (FileInputStream fis = new FileInputStream(getAbsoluteImageFile())) {
            String szBase = "https://" + MFBConstants.szIP;
            String szBoundary = UUID.randomUUID().toString();
            String szBoundaryDivider = String.format("--%s\r\n", szBoundary);

            URL url= new URL(szBase + m_szURL);
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setDoOutput(true);
            urlConnection.setChunkedStreamingMode(0);
            urlConnection.setRequestMethod("POST");
            urlConnection.setRequestProperty("Content-Type", String.format("multipart/form-data; boundary=%s", szBoundary));

            try (OutputStream out = new BufferedOutputStream(urlConnection.getOutputStream())) {
                if (m_key.length() == 0)
                    throw new Exception("No valid key provided");

                out.write(szBoundaryDivider.getBytes(StandardCharsets.UTF_8));
                out.write("Content-Disposition: form-data; name=\"txtAuthToken\"\r\n\r\n".getBytes(StandardCharsets.UTF_8));
                out.write(String.format("%s\r\n%s", AuthToken.m_szAuthToken, szBoundaryDivider).getBytes(StandardCharsets.UTF_8));

                out.write("Content-Disposition: form-data; name=\"txtComment\"\r\n\r\n".getBytes(StandardCharsets.UTF_8));
                out.write(String.format("%s\r\n%s", Comment, szBoundaryDivider).getBytes(StandardCharsets.UTF_8));

                out.write(String.format("Content-Disposition: form-data; name=\"%s\"\r\n\r\n", this.m_keyName).getBytes(StandardCharsets.UTF_8));
                out.write(String.format("%s\r\n%s", this.m_key, szBoundaryDivider).getBytes(StandardCharsets.UTF_8));

                out.write(String.format(Locale.getDefault(), "Content-Disposition: form-data; name=\"imgPicture\"; filename=\"myimage%s\"\r\n", getImageSuffix()).getBytes(StandardCharsets.UTF_8));
                out.write(String.format(Locale.getDefault(), "Content-Type: %s\r\nContent-Transfer-Encoding: binary\r\n\r\n", IsVideo() ? "video/mp4" : "image/jpeg").getBytes(StandardCharsets.UTF_8));

                byte[] b = new byte[1024];
                int bytesRead;
                while ((bytesRead = fis.read(b)) != -1)
                    out.write(b, 0, bytesRead);

                out.write(String.format("\r\n\r\n--%s--\r\n", szBoundary).getBytes(StandardCharsets.UTF_8));

                out.flush();

                int status = urlConnection.getResponseCode();
                if (status != HttpURLConnection.HTTP_OK)
                    throw new Exception(String.format(Locale.US, "Bad response - status = %d", status));

                byte[] rgResponse = new byte[1024];
                try (InputStream in = new BufferedInputStream(urlConnection.getInputStream())) {
                    int cBytes = in.read(rgResponse);
                    Log.v(MFBConstants.LOG_TAG, String.format(Locale.US, "%d bytes read in uploadpendingimage", cBytes));
                }
                catch (Exception ex) {
                    Log.e(MFBConstants.LOG_TAG, "error uploading pending image: " + ex.getMessage());
                    throw ex;
                }

                String sz = new String(rgResponse, StandardCharsets.UTF_8);
                if (!sz.contains("OK"))
                    throw new Exception(sz);

                fResult = true;
            } catch (Exception ex) {
                final String szErr = ex.getMessage();
                Log.e(MFBConstants.LOG_TAG, "Error uploading image: " + szErr);

                Handler h = new Handler(c.getMainLooper());
                h.post(() -> MFBUtil.Alert(c, c.getString(R.string.txtError), szErr));
            } finally {
                urlConnection.disconnect();
                m_imgData = null; // free up a potentially large block of memory
            }
        } catch (IOException e) {
            Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e));
        }

        return fResult;
    }

	/*
     * Serialization
	 */

    public int getPropertyCount() {
        return FPProp.values().length;
    }

    public Object getProperty(int arg0) {
        FPProp f = FPProp.values()[arg0];

        switch (f) {
            case pidComment:
                return this.Comment;
            case pidVirtualPath:
                return this.VirtualPath;
            case pidThumbnailFile:
                return this.ThumbnailFile;
            case pidURLFullImage:
                return this.URLFullImage;
            case pidURLThumbnail:
                return this.URLThumbnail;
            case pidWidth:
                return this.Width;
            case pidHeight:
                return this.Height;
            case pidTHWidth:
                return this.WidthThumbnail;
            case pidTHHeight:
                return this.HeightThumbnail;
            case pidLocation:
                return this.Location;
            case pidImageType:
                return this.ImageType.toString();
        }
        return null;
    }

    public void getPropertyInfo(int arg0, Hashtable h, PropertyInfo pi) {
        FPProp f = FPProp.values()[arg0];
        switch (f) {
            case pidComment:
                pi.type = PropertyInfo.STRING_CLASS;
                pi.name = "Comment";
                break;
            case pidVirtualPath:
                pi.type = PropertyInfo.STRING_CLASS;
                pi.name = "VirtualPath";
                break;
            case pidThumbnailFile:
                pi.type = PropertyInfo.STRING_CLASS;
                pi.name = "ThumbnailFile";
                break;
            case pidURLThumbnail:
                pi.type = PropertyInfo.STRING_CLASS;
                pi.name = "URLThumbnail";
                break;
            case pidURLFullImage:
                pi.type = PropertyInfo.STRING_CLASS;
                pi.name = "URLFullImage";
                break;
            case pidWidth:
                pi.type = PropertyInfo.INTEGER_CLASS;
                pi.name = "Width";
                break;
            case pidHeight:
                pi.type = PropertyInfo.INTEGER_CLASS;
                pi.name = "Height";
                break;
            case pidTHWidth:
                pi.type = PropertyInfo.INTEGER_CLASS;
                pi.name = "WidthThumbnail";
                break;
            case pidTHHeight:
                pi.type = PropertyInfo.INTEGER_CLASS;
                pi.name = "HeightThumbnail";
                break;
            case pidLocation:
                pi.type = PropertyInfo.OBJECT_CLASS;
                pi.name = "Location";
                break;
            case pidImageType:
                pi.type = PropertyInfo.STRING_CLASS;
                pi.name = "ImageType";
                break;
            default:
                break;
        }
    }

    public void setProperty(int arg0, Object arg1) {
        FPProp f = FPProp.values()[arg0];
        String sz = arg1.toString();
        switch (f) {
            case pidComment:
                this.Comment = sz;
                break;
            case pidVirtualPath:
                this.VirtualPath = sz;
                break;
            case pidThumbnailFile:
                this.ThumbnailFile = sz;
                break;
            case pidURLFullImage:
                this.URLFullImage = sz;
                break;
            case pidURLThumbnail:
                this.URLThumbnail = sz;
                break;
            case pidWidth:
                this.Width = Integer.parseInt(sz);
                break;
            case pidHeight:
                this.Height = Integer.parseInt(sz);
                break;
            case pidTHWidth:
                this.WidthThumbnail = Integer.parseInt(sz);
                break;
            case pidTHHeight:
                this.HeightThumbnail = Integer.parseInt(sz);
                break;
            case pidImageType:
                this.ImageType = ImageFileType.valueOf(sz);
                break;
            case pidLocation:
            default:
                break;
        }
    }

    @Override
    public void ToProperties(SoapObject so) {
        so.addProperty("Comment", Comment);
        so.addProperty("VirtualPath", VirtualPath);
        so.addProperty("URLFullImage", URLFullImage);
        so.addProperty("URLThumbnail", URLThumbnail);
        so.addProperty("ThumbnailFile", ThumbnailFile);
        so.addProperty("Width", Width);
        so.addProperty("Height", Height);
        so.addProperty("WidthThumbnail", WidthThumbnail);
        so.addProperty("HeightThumbnail", HeightThumbnail);
        so.addProperty("Location", Location);
        so.addProperty("ImageType", ImageType);
    }

    @Override
    public void FromProperties(SoapObject so) {
        Comment = ReadNullableString(so, "Comment");
        VirtualPath = ReadNullableString(so, "VirtualPath");
        URLThumbnail = ReadNullableString(so, "URLThumbnail");
        URLFullImage = ReadNullableString(so, "URLFullImage");
        ThumbnailFile = ReadNullableString(so, "ThumbnailFile");
        Width = Integer.parseInt(so.getProperty("Width").toString());
        Height = Integer.parseInt(so.getProperty("Height").toString());
        WidthThumbnail = Integer.parseInt(so.getProperty("WidthThumbnail").toString());
        HeightThumbnail = Integer.parseInt(so.getProperty("HeightThumbnail").toString());

        if (so.hasProperty("Location")) {
            SoapObject location = (SoapObject) so.getProperty("Location");
            Location = new LatLong();
            Location.FromProperties(location);
        }

        if (so.hasProperty("ImageType"))
            ImageType = ImageFileType.valueOf(so.getProperty("ImageType").toString());
    }

    private String getURLFullImage() {
        return String.format(Locale.US, "https://%s%s", MFBConstants.szIP, this.URLFullImage);
    }

    private String getURLThumbnail() {
        return this.URLThumbnail.startsWith("/") ? String.format(Locale.US, "https://%s%s", MFBConstants.szIP, this.URLThumbnail) : this.URLThumbnail;
    }

    public void LoadImageAsync(Boolean fThumbnail, ImageCacheCompleted delegate) {
        // return if we already have everything cached.
        if (fThumbnail && this.m_imgThumb != null && this.m_imgThumb.length > 0)
            return;
        if (!fThumbnail && this.m_imgData != null && this.m_imgData.length > 0)
            return;

        AsyncLoadURL alu = new AsyncLoadURL(null, (fThumbnail) ? getURLThumbnail() : getURLFullImage(), fThumbnail, delegate, this);
        alu.execute();
    }

    /*
     * Asynchronously fill an imageview with thumbnail or full image
     * Uses from the database, if necessary.
     */
    public void LoadImageForImageView(Boolean fThumbnail, ImageView i) {
        // if it's local, try to pull the image from the db first.  Note that his could fail.
        if (IsLocal())
            this.fromDB(fThumbnail, !fThumbnail);

        if (fThumbnail && this.m_imgThumb != null && this.m_imgThumb.length > 0) {
            i.setImageBitmap(this.bitmapFromThumb());
            return;
        }
        if (!fThumbnail && this.m_imgData != null && this.m_imgData.length > 0) {
            i.setImageBitmap(this.bitmapFromImage());
            return;
        }

        AsyncLoadURL alu = new AsyncLoadURL(i, (fThumbnail) ? getURLThumbnail() : getURLFullImage(), fThumbnail, null, this);
        alu.execute();
    }

    private static float ResizeRatio(int maxHeight, int maxWidth, int Height, int Width) {
        float ratioX = ((float) maxWidth / (float) Width);
        float ratioY = ((float) maxHeight / (float) Height);
        float minRatio = 1.0F;
        if (ratioX < 1.0 || ratioY < 1.0) {
            minRatio = Math.min(ratioX, ratioY);
        }
        return minRatio;
    }

    // below is adapted from http://stackoverflow.com/questions/11012556/border-over-a-bitmap-with-rounded-corners-in-android - thanks!
    // That in turn came from http://ruibm.com/?p=184
    public static Bitmap getRoundedCornerBitmap(Bitmap bitmap, int color, int cornerDips, int borderDips, int maxHeight, int maxWidth, Context context) {

        int wSrc = bitmap.getWidth();
        int hSrc = bitmap.getHeight();
        float scaledMaxHeight = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, (float) maxHeight, context.getResources().getDisplayMetrics());
        float scaledMaxWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, (float) maxWidth, context.getResources().getDisplayMetrics());
        float resizeRatio = ResizeRatio((int) scaledMaxHeight, (int) scaledMaxWidth, hSrc, wSrc);
        int wScaled = (int) (wSrc * resizeRatio);
        int hScaled = (int) (hSrc * resizeRatio);
        final Rect rectSrc = new Rect(0, 0, wSrc, hSrc);
        final Rect rectDst = new Rect(0, 0, wScaled, hScaled);
        Bitmap output = Bitmap.createBitmap(wScaled, hScaled, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        final int borderSizePx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, (float) borderDips,
                context.getResources().getDisplayMetrics());
        final int cornerSizePx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, (float) cornerDips,
                context.getResources().getDisplayMetrics());
//	    int borderSizePx = borderDips;
//	    int cornerSizePx = cornerDips;
        final Paint paint = new Paint();
        final RectF rectF = new RectF(rectDst);

        // prepare canvas for transfer
        paint.setAntiAlias(true);
        paint.setColor(0xFFFFFFFF);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawARGB(0, 0, 0, 0);
        canvas.drawRoundRect(rectF, cornerSizePx, cornerSizePx, paint);

        // draw bitmap
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bitmap, rectSrc, rectDst, paint);

        // draw border
        paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth((float) borderSizePx);
        canvas.drawRoundRect(rectF, cornerSizePx, cornerSizePx, paint);

        return output;
    }

    public void ViewFullImageInWebView(android.app.Activity a) {
        if (URLFullImage.length() > 0) {
            // Image is on server - easy - just use webview
            ActWebView.ViewURL(a, getURLFullImage());
        } else {
            // Image is local only - need to write it out to a temp file

            // Ensure that we have the full bytes.
            if (m_imgData == null || m_imgData.length == 0)
                fromDB(true, true);

            // Write the full image to a file
            try {
                File fTemp = File.createTempFile("tempView", getImageSuffix(), a.getExternalFilesDir(Environment.DIRECTORY_PICTURES));
                fTemp.deleteOnExit();
                FileOutputStream fos = new FileOutputStream(fTemp);
                try {
                    fos.write(m_imgData);
                    fos.close();
                    m_imgData = null;    // clean up for memory
                    System.gc();
                    ActWebView.ViewTempFile(a, fTemp);
                } catch (IOException e) {
                    Log.e(MFBConstants.LOG_TAG, "Error saving image full file: " + e.getMessage());
                    Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e));
                }
            } catch (IOException e) {
                Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e));
            }
        }
    }
}
