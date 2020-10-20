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
/*
 * Helper class for dealing with forms.
 */

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.myflightbook.android.DlgDatePicker.DateTimeUpdate;
import com.myflightbook.android.WebServices.AuthToken;
import com.myflightbook.android.WebServices.ImagesSvc;
import com.myflightbook.android.WebServices.MFBSoap;
import com.myflightbook.android.WebServices.UTCDate;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import Model.DecimalEdit;
import Model.DecimalEdit.EditMode;
import Model.MFBConstants;
import Model.MFBImageInfo;
import Model.MFBLocation;
import Model.MFBUtil;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

public class ActMFBForm extends Fragment {
    interface GallerySource {
        @SuppressWarnings("SameReturnValue")
        int getGalleryID();

        View getGalleryHeader();

        MFBImageInfo[] getImages();

        void setImages(MFBImageInfo[] rgmfbii);

        void newImage(MFBImageInfo mfbii);

        MFBImageInfo.PictureDestination getDestination();

        void refreshGallery();
    }

    private static final String keyTempFileInProgress = "uriFileInProgress";
    static final int CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE = 1483;
    static final int CAPTURE_VIDEO_ACTIVITY_REQUEST_CODE = 1968;
    static final int SELECT_IMAGE_ACTIVITY_REQUEST_CODE = 1849;
    private static final int CAMERA_PERMISSION_IMAGE = 83;
    private static final int CAMERA_PERMISSION_VIDEO = 84;
    private static final int GALLERY_PERMISSION = 85;
    private static final String TEMP_IMG_FILE_NAME = "takenpicture";
    String m_TempFilePath = "";

    private static class AddCameraTask extends AsyncTask<String, String, Boolean> implements MFBSoap.MFBSoapProgressUpdate {
        MFBImageInfo mfbii = null;
        final Boolean fGeoTag;
        public Boolean fDeleteFileWhenDone;
        final Boolean fAddToGallery;
        final Boolean m_fVideo;
        final AsyncWeakContext<ActMFBForm> m_ctxt;

        AddCameraTask(int ActivityRequestCode, Boolean fVideo, ActMFBForm frm) {
            super();
            m_ctxt = new AsyncWeakContext<>(frm.getContext(), frm);
            m_fVideo = fVideo;
            fAddToGallery = fDeleteFileWhenDone = (ActivityRequestCode == CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE || ActivityRequestCode == CAPTURE_VIDEO_ACTIVITY_REQUEST_CODE);
            fGeoTag = (ActivityRequestCode == CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE);
        }

        @Override
        protected Boolean doInBackground(String... params) {
            String szFilename = params[0];
            if (szFilename == null || szFilename.length() == 0) {
                Log.e(MFBConstants.LOG_TAG, "No filename passed back!!!");
                return false;
            }

            // Add the image/video to the gallery if necessary (i.e., if from the camera)
            if (fAddToGallery) {
                File f = new File(szFilename);
                Uri uriSource = FileProvider.getUriForFile(m_ctxt.getContext(), BuildConfig.APPLICATION_ID + ".provider", f);
                m_ctxt.getCallingActivity().requireActivity().sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uriSource));
            }

            GallerySource gs = (GallerySource) m_ctxt.getCallingActivity();
            mfbii = new MFBImageInfo(gs.getDestination());
            return mfbii.initFromCamera(szFilename, fGeoTag ? MFBLocation.LastSeenLoc() : null, m_fVideo, fDeleteFileWhenDone);
        }

        protected void onPreExecute() {
        }

        protected void onPostExecute(Boolean b) {
            ActMFBForm frm = m_ctxt.getCallingActivity();

            if (frm == null)
                return;
            if (b && mfbii != null) {
                GallerySource gs = (GallerySource) frm;
                gs.newImage(mfbii);
                gs.refreshGallery();
            }
        }

        public void NotifyProgress(int percentageComplete, String szMsg) {
        }
    }

    void AddCameraImage(final String szFilename, Boolean fVideo) {
        AddCameraTask act = new AddCameraTask(CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE, fVideo, this);
        act.execute(szFilename);
    }

    // Activity pseudo support.
    View findViewById(int id) {
        View v = getView();
        return v == null ? null : v.findViewById(id);
    }

    void finish() {
        requireActivity().finish();
    }

    void AddGalleryImage(final Intent i) {
        Uri selectedImage = i.getData();
        String[] filePathColumn = {MediaStore.Images.Media.DATA, MediaStore.Images.Media.MIME_TYPE};

        ContentResolver cr = requireActivity().getContentResolver();
        assert selectedImage != null;
        Cursor cursor = cr.query(
                selectedImage, filePathColumn, null, null, null);
        if (cursor != null) {
            cursor.moveToFirst();

            String szFilename = cursor.getString(cursor.getColumnIndex(filePathColumn[0]));
            String szMimeType = cursor.getString(cursor.getColumnIndex(filePathColumn[1]));
            Boolean fIsVideo = szMimeType.toLowerCase(Locale.getDefault()).startsWith("video");
            cursor.close();

            AddCameraTask act = new AddCameraTask(SELECT_IMAGE_ACTIVITY_REQUEST_CODE, fIsVideo, this);

            if (szFilename == null || szFilename.length() == 0) { // try reading it into a temp file
                InputStream in = null;
                OutputStream o = null;
                try {
                    File fTemp = File.createTempFile("img", null, requireActivity().getExternalFilesDir(Environment.DIRECTORY_PICTURES));
                    fTemp.deleteOnExit();
                    in = cr.openInputStream(selectedImage);
                    o = new FileOutputStream(fTemp);

                    byte[] rgBuffer = new byte[1024];

                    int length;
                    try {
                        while ((length = in.read(rgBuffer)) > 0) {
                            o.write(rgBuffer, 0, length);
                        }
                    } catch (IOException e) {
                        Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e));
                    }

                    szFilename = fTemp.getAbsolutePath();
                    act.fDeleteFileWhenDone = true; // delete the temp file when done.
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (Exception ex) {
                    Log.e(MFBConstants.LOG_TAG, "Error copying input telemetry to new flight: " + ex.getMessage());
                }
                finally {
                    if (in != null) {
                        try {
                            in.close();
                        } catch (IOException ignored) { }
                    }
                    if (o != null) {
                        try {
                            o.close();
                        } catch (IOException ignored) { }
                    }
                }

                if (szFilename == null || szFilename.length() == 0)
                    return;
            }

            act.execute(szFilename == null || szFilename.length() == 0 ? selectedImage.toString() : szFilename);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(MFBMain.NightModePref);

        super.onCreate(savedInstanceState);
        // need to restore this here because OnResume may come after the onActivityResult call
        SharedPreferences mPrefs = requireActivity().getPreferences(Activity.MODE_PRIVATE);
        m_TempFilePath = mPrefs.getString(keyTempFileInProgress, "");
    }

    //region binding data to forms
    void AddListener(int id) {
        findViewById(id).setOnClickListener((View.OnClickListener) this);
    }

    void SetDateTime(int id, Date d, DateTimeUpdate delegate, DlgDatePicker.datePickMode dpm) {
        DlgDatePicker dlg = new DlgDatePicker(this.requireActivity(), dpm, d);
        dlg.m_delegate = delegate;
        dlg.m_id = id;
        dlg.show();
    }

    Integer IntFromField(int id) {
        DecimalEdit v = (DecimalEdit) findViewById(id);
        return v.getIntValue();
    }

    void SetIntForField(int id, int value) {
        DecimalEdit v = (DecimalEdit) findViewById(id);
        v.setIntValue(value);
    }

    Double DoubleFromField(int id) {
        DecimalEdit v = (DecimalEdit) findViewById(id);
        return v.getDoubleValue();
    }

    void SetDoubleForField(int id, Double d) {
        DecimalEdit v = (DecimalEdit) findViewById(id);
        v.setDoubleValue(d);
    }

    void SetUTCDateForField(int id, Date d) {
        TextView b = (TextView) findViewById(id);
        if (d == null || UTCDate.IsNullDate(d))
            b.setText(getString(R.string.lblTouchForNow));
        else
            b.setText(UTCDate.formatDate(DlgDatePicker.fUseLocalTime, d, getContext()));
    }

    void SetLocalDateForField(int id, Date d) {
        TextView b = (TextView) findViewById(id);
        if (UTCDate.IsNullDate(d))
            b.setText(getString(R.string.lblTouchForToday));
        else
            b.setText(DateFormat.getDateFormat(requireActivity()).format(d));
    }

    String StringFromField(int id) {
        TextView e = (TextView) findViewById(id);
        return e.getText().toString();
    }

    void SetStringForField(int id, String s) {
        TextView e = (TextView) findViewById(id);
        e.setText(s);
    }

    Boolean CheckState(int id) {
        CheckBox c = (CheckBox) findViewById(id);
        return c.isChecked();
    }

    void SetCheckState(int id, Boolean f) {
        CheckBox c = (CheckBox) findViewById(id);
        c.setChecked(f);
    }

    void SetRadioButton(int id) {
        RadioButton rb = (RadioButton) findViewById(id);
        rb.setChecked(true);
    }
    //endregion

    private MFBImageInfo mfbiiLastClicked;

    void setUpImageGallery(int idGallery, MFBImageInfo[] rgMfbii, View headerView) {
        // Set up the gallery for any pictures
        if (rgMfbii == null)
            return;

        if (headerView != null)
            headerView.setVisibility(rgMfbii.length == 0 ? View.GONE : View.VISIBLE);

        Activity a = requireActivity();

        LayoutInflater l = a.getLayoutInflater();
        TableLayout tl = (TableLayout) findViewById(idGallery);
        if (tl == null)
            return;
        tl.removeAllViews();
        int i = 0;
        for (MFBImageInfo mfbii : rgMfbii) {
            try {
                // TableRow tr = new TableRow(this);

                TableRow tr = (TableRow) l.inflate(R.layout.imageitem, tl, false);
                tr.setId(MFBImageInfo.idImageGalleryIdBase + i++);

                ImageView iv = tr.findViewById(R.id.imageItemView);
                ((TextView) tr.findViewById(R.id.imageItemComment)).setText(mfbii.Comment);

                mfbii.LoadImageForImageView(true, iv);

                registerForContextMenu(tr);

                final MFBImageInfo mfbiiFinal = mfbii;
                tr.setOnClickListener((v) -> mfbiiFinal.ViewFullImageInWebView(requireActivity()));
                tr.setOnLongClickListener((View v) -> {
                    mfbiiLastClicked = mfbiiFinal;
                    requireActivity().openContextMenu(v);
                    return true;
                });

                tl.addView(tr, new TableLayout.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT));
            } catch (NullPointerException ex) { // should never happen.
                Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(ex));
            }
        }
    }

    void setDecimalEditMode(int id) {
        DecimalEdit e = (DecimalEdit) findViewById(id);
        e.setMode(EditMode.HHMM);
    }

    boolean onImageContextItemSelected(MenuItem item, GallerySource src) {
        if (mfbiiLastClicked == null)    // should never be true
            return false;

        final MFBImageInfo mfbii = mfbiiLastClicked;

        int id = item.getItemId();

        if (id == R.id.menuAddComment) {
            DlgImageComment dlgComment = new DlgImageComment(requireActivity(),
                    mfbii, (mfbii2) -> setUpImageGallery(src.getGalleryID(), src.getImages(), src.getGalleryHeader()));
            dlgComment.show();
        } else if (id == R.id.menuDeleteImage) {
            new AlertDialog.Builder(requireActivity(), R.style.MFBDialog)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setTitle(R.string.lblConfirm)
                    .setMessage(R.string.lblConfirmImageDelete)
                    .setPositiveButton(R.string.lblOK, (dialog, which) -> {
                        // Image can be both local AND on server (aircraft)
                        // Need to delete in both places, as appropriate.
                        if (mfbii.IsLocal())
                            mfbii.deleteFromDB();
                        if (mfbii.IsOnServer()) {
                            ImagesSvc is = new ImagesSvc();
                            is.DeleteImage(AuthToken.m_szAuthToken, mfbii, getContext());
                        }

                        // Now remove this from the existing images in the source
                        ArrayList<MFBImageInfo> alNewImages = new ArrayList<>();
                        MFBImageInfo[] rgMfbii = src.getImages();
                        for (MFBImageInfo m : rgMfbii) // re-add images that are NOT the one being deleted
                            if (mfbii.getID() != m.getID() || m.ThumbnailFile.compareTo(mfbii.ThumbnailFile) != 0)
                                alNewImages.add(m);
                        src.setImages(alNewImages.toArray(new MFBImageInfo[0]));

                        setUpImageGallery(src.getGalleryID(), src.getImages(), src.getGalleryHeader());
                    })
                    .setNegativeButton(R.string.lblCancel, null)
                    .show();
        } else if (id == R.id.menuViewImage)
            mfbii.ViewFullImageInWebView(requireActivity());

        mfbiiLastClicked = null;
        return true;
    }

    //region Image/video permissions
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean fCheckImagePermissions(int permission) {
        boolean fNeedWritePerm;

        fNeedWritePerm = (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q); // no need to request WRITE_EXTERNAL_STORAGE in 29 and later.

        switch (permission) {
            case CAMERA_PERMISSION_IMAGE:
            case CAMERA_PERMISSION_VIDEO:
                if (ContextCompat.checkSelfPermission(requireActivity(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                        ContextCompat.checkSelfPermission(requireActivity(), Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                        (!fNeedWritePerm || ContextCompat.checkSelfPermission(requireActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED))
                    return true;
                else
                    requestPermissions(fNeedWritePerm ? new String[]{Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE} :
                            new String[] {Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE}
                    , permission);
                break;
            case GALLERY_PERMISSION:
                if (ContextCompat.checkSelfPermission(requireActivity(), Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                        (!fNeedWritePerm || ContextCompat.checkSelfPermission(requireActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED))
                    return true;
                else
                    requestPermissions(fNeedWritePerm ? new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE} :
                            new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, permission);
                break;
        }
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        boolean fAllGranted = true;
        for (int i : grantResults)
            if (i != PackageManager.PERMISSION_GRANTED) {
                fAllGranted = false;
                break;
            }

        switch (requestCode) {
            case CAMERA_PERMISSION_IMAGE:
                if (fAllGranted)
                    TakePicture();
                break;
            case CAMERA_PERMISSION_VIDEO:
                if (fAllGranted)
                    TakeVideo();
                break;
            case GALLERY_PERMISSION:
                if (fAllGranted)
                    ChoosePicture();
                break;
        }
    }
    //endregion

    //region image/video selection
    void ChoosePicture() {
        if (!fCheckImagePermissions(GALLERY_PERMISSION))
            return;
        Intent i = new Intent(Intent.ACTION_PICK);
        i.setDataAndType(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/* video/*");
        startActivityForResult(i, SELECT_IMAGE_ACTIVITY_REQUEST_CODE);
    }

    void TakePicture() {
        File fTemp;
        File storageDir = requireActivity().getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (MFBConstants.fFakePix) {
            String[] rgSamples = {"sampleimg.jpg", "sampleimg2.jpg", "sampleimg3.jpg"};
            for (String szAsset : rgSamples) {
                FileOutputStream fos = null;
                try {
                    InputStream is = requireActivity().getAssets().open(szAsset);

                    fTemp = File.createTempFile(TEMP_IMG_FILE_NAME, ".jpg", storageDir);
                    fTemp.deleteOnExit();
                    m_TempFilePath = fTemp.getAbsolutePath(); // need to save this for when the picture comes back

                    fos = new FileOutputStream(fTemp);

                    byte[] rgBuffer = new byte[1024];

                    int length;
                    try {
                        while ((length = is.read(rgBuffer)) > 0) {
                            fos.write(rgBuffer, 0, length);
                        }
                    } catch (IOException e) {
                        Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e));
                    }

                    AddCameraImage(m_TempFilePath, false);

                } catch (IOException e) {
                    Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e));
                } finally {
                    if (fos != null)
                        try {
                            fos.close();
                        } catch (IOException e) {
                            Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e));
                        }
                }
            }
            return;
        }
        try {
            if (!fCheckImagePermissions(CAMERA_PERMISSION_IMAGE))
                return;

            fTemp = File.createTempFile(TEMP_IMG_FILE_NAME, ".jpg", storageDir);
            fTemp.deleteOnExit();
            m_TempFilePath = fTemp.getAbsolutePath(); // need to save this for when the picture comes back

            SharedPreferences mPrefs = requireActivity().getPreferences(Activity.MODE_PRIVATE);
            SharedPreferences.Editor ed = mPrefs.edit();
            ed.putString(keyTempFileInProgress, m_TempFilePath);
            ed.apply();

            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            Uri uriImage = FileProvider.getUriForFile(this.requireContext(), BuildConfig.APPLICATION_ID + ".provider", fTemp);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, uriImage);
            intent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1);
            startActivityForResult(intent, CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE);
        } catch (IOException e) {
            Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e));
            MFBUtil.Alert(requireActivity(), getString(R.string.txtError), getString(R.string.errNoCamera));
        }
    }

    void TakeVideo() {
        File fTemp;
        File storageDir = requireActivity().getExternalFilesDir(Environment.DIRECTORY_PICTURES);

        try {
            if (!fCheckImagePermissions(CAMERA_PERMISSION_VIDEO))
                return;
            fTemp = File.createTempFile(TEMP_IMG_FILE_NAME, ".mp4", storageDir);
            fTemp.deleteOnExit();
            m_TempFilePath = fTemp.getAbsolutePath(); // need to save this for when the picture comes back

            SharedPreferences mPrefs = requireActivity().getPreferences(Activity.MODE_PRIVATE);
            SharedPreferences.Editor ed = mPrefs.edit();
            ed.putString(keyTempFileInProgress, m_TempFilePath);
            ed.apply();

            Uri uriImage = FileProvider.getUriForFile(this.requireContext(), BuildConfig.APPLICATION_ID + ".provider", fTemp);

            Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, uriImage);
            intent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1);
            startActivityForResult(intent, CAPTURE_VIDEO_ACTIVITY_REQUEST_CODE);
        } catch (IOException e) {
            Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e));
            MFBUtil.Alert(requireActivity(), getString(R.string.txtError), getString(R.string.errNoCamera));
        }
    }
    //endregion

    //region expand/collapse
    // next two methods are adapted from http://stackoverflow.com/questions/19263312/how-to-achieve-smooth-expand-collapse-animation
    private void expandView(final View v) {
        v.measure(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        final int targtetHeight = v.getMeasuredHeight();

        v.getLayoutParams().height = 0;
        v.setVisibility(View.VISIBLE);
        Animation a = new Animation() {
            @Override
            protected void applyTransformation(float interpolatedTime, Transformation t) {
                v.getLayoutParams().height = interpolatedTime == 1
                        ? LinearLayout.LayoutParams.WRAP_CONTENT
                        : (int) (targtetHeight * interpolatedTime);
                v.requestLayout();
            }

            @Override
            public boolean willChangeBounds() {
                return true;
            }
        };

        a.setDuration((int) (targtetHeight / v.getContext().getResources().getDisplayMetrics().density));
        v.startAnimation(a);
    }

    private void collapseView(final View v) {
        final int initialHeight = v.getMeasuredHeight();

        Animation a = new Animation() {
            @Override
            protected void applyTransformation(float interpolatedTime, Transformation t) {
                if (interpolatedTime == 1) {
                    v.setVisibility(View.GONE);
                } else {
                    v.getLayoutParams().height = initialHeight - (int) (initialHeight * interpolatedTime);
                    v.requestLayout();
                }
            }

            @Override
            public boolean willChangeBounds() {
                return true;
            }
        };

        a.setDuration((int) (initialHeight / v.getContext().getResources().getDisplayMetrics().density));
        v.startAnimation(a);
    }

    void setExpandedState(TextView v, View target, Boolean fExpanded) {
        setExpandedState(v, target, fExpanded, true);
    }

    void setExpandedState(TextView v, View target, Boolean fExpanded, Boolean fAnimated) {
        Drawable d;
        if (fExpanded) {
            if (fAnimated)
                expandView(target);
            else
                target.setVisibility(View.VISIBLE);
            d = ContextCompat.getDrawable(requireActivity(), R.drawable.collapse_light);
        } else {
            if (fAnimated)
                collapseView(target);
            else
                target.setVisibility(View.GONE);
            d = ContextCompat.getDrawable(requireActivity(), R.drawable.expand_light);
        }
        v.setCompoundDrawablesWithIntrinsicBounds(d, null, null, null);
    }
    //endregion
}
