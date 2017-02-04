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
/*
 * Helper class for dealing with forms.
 */

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Gallery;
import android.widget.RadioButton;

import com.myflightbook.android.DlgDatePicker.DateTimeUpdate;
import com.myflightbook.android.WebServices.AuthToken;
import com.myflightbook.android.WebServices.ImagesSvc;
import com.myflightbook.android.WebServices.MFBSoap;
import com.myflightbook.android.WebServices.UTCDate;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import Model.DecimalEdit;
import Model.DecimalEdit.EditMode;
import Model.MFBConstants;
import Model.MFBImageInfo;
import Model.MFBLocation;
import Model.MFBUtil;

public class ActMFBForm extends Fragment {
	public interface GallerySource
	{
		public int getGalleryID();
		public MFBImageInfo[] getImages();
		public void setImages(MFBImageInfo[] rgmfbii);
		public void newImage(MFBImageInfo mfbii);
		public MFBImageInfo.PictureDestination getDestination();
		public void refreshGallery();
	}
	
	protected static final String keyTempFileInProgress = "uriFileInProgress";
	protected static final int CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE = 1483;
	protected static final int CAPTURE_VIDEO_ACTIVITY_REQUEST_CODE = 1968;
	protected static final int SELECT_IMAGE_ACTIVITY_REQUEST_CODE = 1849;
	protected static final int CAMERA_PERMISSION_IMAGE = 83;
	protected static final int CAMERA_PERMISSION_VIDEO = 84;
	protected static final String TEMP_IMG_FILE_NAME = "takenpicture";
	protected String m_TempFilePath = "";
	
	private class AddCameraTask extends AsyncTask<String, String, Boolean> implements MFBSoap.MFBSoapProgressUpdate
	{
		MFBImageInfo mfbii = null;
		public Boolean fGeoTag = true;
		public Boolean fDeleteFileWhenDone = false;
		public Boolean fAddToGallery = false;
		public Boolean m_fVideo = false;
		
		public AddCameraTask(int ActivityRequestCode, Boolean fVideo)
		{
			m_fVideo = fVideo;
			fAddToGallery = fDeleteFileWhenDone =  (ActivityRequestCode == CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE || ActivityRequestCode == CAPTURE_VIDEO_ACTIVITY_REQUEST_CODE);
			fGeoTag = (ActivityRequestCode == CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE);
		}
		
		@Override
		protected Boolean doInBackground(String... params) {
			String szFilename = params[0];
			if (szFilename == null || szFilename.length() == 0)
			{
				Log.e("MFBAndroid", "No filename passed back!!!");
				return false;
			}

			// Add the image/video to the gallery if necessary (i.e., if from the camera)
			if (fAddToGallery) {
//				try {
//					if (m_fVideo) {
//						// Because Android is fucked up, there's no analog to insertImage below
//						// So this is taken from http://stackoverflow.com/questions/2114168/android-mediastore-insertvideo
//						// Save the name and description of a video in a ContentValues map.
//						ContentValues values = new ContentValues(2);
//						values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
//						// values.put(MediaStore.Video.Media.DATA, f.getAbsolutePath());
//
//						// Add a new record (identified by uri) without the video, but with the values just set.
//						ContentResolver cr = ActMFBForm.this.getActivity().getContentResolver();
//						Uri uri = cr.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
//
//						// Now get a handle to the file for that record, and save the data into it.
//						InputStream is = null;
//						OutputStream os = null;
//						try {
//							is = new java.io.FileInputStream(szFilename);
//							os = cr.openOutputStream(uri);
//							byte[] buffer = new byte[4096];
//							int len;
//							while ((len = is.read(buffer)) != -1)
//								os.write(buffer, 0, len);
//							is.close();
//							os.flush();
//							os.close();
//						} catch (Exception e) {
//							Log.e("MFBAndroid", "exception while writing video: ", e);
//						}
//
//						ActMFBForm.this.getActivity().sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE));
//					} else
//						MediaStore.Images.Media.insertImage(ActMFBForm.this.getActivity().getContentResolver(), szFilename, "", "");
//				} catch (FileNotFoundException e) {
//					e.printStackTrace();
//				} catch (Exception e) {
//					e.printStackTrace();
//				}
				ActMFBForm.this.getActivity().getApplicationContext().sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(new File(szFilename))));
			}
			
			GallerySource gs = (GallerySource) ActMFBForm.this;
			mfbii = new MFBImageInfo(gs.getDestination());
			return mfbii.initFromCamera(szFilename, fGeoTag ? MFBLocation.LastSeenLoc() : null, m_fVideo, fDeleteFileWhenDone);
		}

		protected void onPreExecute() {
		}

		protected void onPostExecute(Boolean b) {
			if (b && mfbii != null)
			{
				GallerySource gs = (GallerySource) ActMFBForm.this;
				gs.newImage(mfbii);
				gs.refreshGallery();
			}
		}

		public void NotifyProgress(int percentageComplete, String szMsg) {
		}
	}
	
	protected void AddCameraImage(final String szFilename, Boolean fVideo)
	{
		AddCameraTask act = new AddCameraTask(CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE, fVideo);
		act.execute(szFilename);
	}
	
	// Activity pseudo support.
	protected View findViewById(int id)	{ View v = getView(); return v == null ? v : v.findViewById(id); }
	protected void finish() { getActivity().finish();}
	
	protected void AddGalleryImage(final Intent i)
	{
		Uri selectedImage = i.getData();
        String[] filePathColumn = {MediaStore.Images.Media.DATA, MediaStore.Images.Media.MIME_TYPE};

        ContentResolver cr = getActivity().getContentResolver();
        Cursor cursor = cr.query(
                           selectedImage, filePathColumn, null, null, null);
        cursor.moveToFirst();

        String szFilename = cursor.getString(cursor.getColumnIndex(filePathColumn[0]));
        String szMimeType = cursor.getString(cursor.getColumnIndex(filePathColumn[1]));
        Boolean fIsVideo = szMimeType.toLowerCase(Locale.getDefault()).startsWith("video");
        cursor.close();

		AddCameraTask act = new AddCameraTask(SELECT_IMAGE_ACTIVITY_REQUEST_CODE, fIsVideo);
		act.execute(szFilename);
	}
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// need to restore this here because OnResume may come after the onActivityResult call
		SharedPreferences mPrefs = getActivity().getPreferences(Activity.MODE_PRIVATE);
		m_TempFilePath = mPrefs.getString(keyTempFileInProgress, "");				
	}
	
	protected void AddListener(int id)
	{
		Button b = (Button)findViewById(id);
		b.setOnClickListener((View.OnClickListener) this);   
	}
	
	protected void SetDateTime(int id, Date d, DateTimeUpdate delegate, DlgDatePicker.datePickMode dpm)
	{
		DlgDatePicker dlg = new DlgDatePicker(this.getActivity(), dpm, d);
		dlg.m_delegate = delegate;
		dlg.m_id = id;
		dlg.show();		
	}
	
	protected Integer IntFromField(int id) {
		DecimalEdit v = (DecimalEdit) findViewById(id);
		return v.getIntValue();
	}

	protected void SetIntForField(int id, int value) {
		DecimalEdit v = (DecimalEdit) findViewById(id);
		v.setIntValue(value);
	}

	protected Double DoubleFromField(int id) {
		DecimalEdit v = (DecimalEdit) findViewById(id);
		return v.getDoubleValue();
	}

	protected void SetDoubleForField(int id, Double d) {
		DecimalEdit v = (DecimalEdit) findViewById(id);
		v.setDoubleValue(d);
	}

	@SuppressLint("SimpleDateFormat")
	protected void SetUTCDateForField(int id, Date d) {
		Button b = (Button) findViewById(id);
		if (d == null || UTCDate.IsNullDate(d))
			b.setText(getString(R.string.lblTouchForNow));
		else
			b.setText(UTCDate.formatDate(DlgDatePicker.fUseLocalTime, d, this.getActivity()));
	}
	
	protected void SetLocalDateForField(int id, Date d)
	{
		Button b = (Button) findViewById(id);
		if (UTCDate.IsNullDate(d))
			b.setText(getString(R.string.lblTouchForToday));
		else
			b.setText(DateFormat.getDateFormat(getActivity()).format(d));
	}

	protected String StringFromField(int id) {
		EditText e = (EditText) findViewById(id);
		return e.getText().toString();
	}

	protected void SetStringForField(int id, String s) {
		EditText e = (EditText) findViewById(id);
		e.setText(s);
	}

	protected Boolean CheckState(int id) {
		CheckBox c = (CheckBox) findViewById(id);
		return c.isChecked();
	}

	protected void SetCheckState(int id, Boolean f) {
		CheckBox c = (CheckBox) findViewById(id);
		c.setChecked(f);
	}
	
	protected void SetRadioButton(int id)
	{
		RadioButton rb = (RadioButton) findViewById(id);
		rb.setChecked(true);
	}
	
	protected void setUpImageGallery(int idGallery, MFBImageInfo[] rgMfbii)
	{
		// Set up the gallery for any pictures
		Gallery g = (Gallery) findViewById(idGallery);
		if (g == null)
			return;

		MFBImageInfo.ImageAdapter ia = new MFBImageInfo.ImageAdapter(rgMfbii, getActivity());
		g.setAdapter(ia);
		ia.notifyDataSetChanged();
		registerForContextMenu(g);
	}
	
	protected void setDecimalEditMode(int id, EditMode em)
	{
		DecimalEdit e = (DecimalEdit) findViewById(id);
		e.setMode(em);
	}
	
	public boolean onImageContextItemSelected(MenuItem item, final GallerySource src)
	{
		AdapterContextMenuInfo acmi = (AdapterContextMenuInfo) item.getMenuInfo();
		long idImg = acmi.targetView.getId();
		
		final MFBImageInfo mfbii = src.getImages()[(int) idImg - MFBImageInfo.idImageGalleryIdBase];
		
		switch (item.getItemId())
		{
		case R.id.menuAddComment:
			DlgImageComment dlgComment = new DlgImageComment(getActivity(), 
					mfbii, 
					new DlgImageComment.AnnotationUpdate() 
			{ public void updateAnnotation(MFBImageInfo mfbii) { setUpImageGallery(src.getGalleryID(), src.getImages());}});
			dlgComment.show();
			break;
		case R.id.menuDeleteImage:
			new AlertDialog.Builder(getActivity())
			.setIcon(android.R.drawable.ic_dialog_alert)
			.setTitle(R.string.lblConfirm)
			.setMessage(R.string.lblConfirmImageDelete)
			.setPositiveButton(R.string.lblOK, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					// Image can be both local AND on server (aircraft)
					// Need to delete in both places, as appropriate.
					if (mfbii.IsLocal())
						mfbii.deleteFromDB();
					if (mfbii.IsOnServer())
					{
						ImagesSvc is = new ImagesSvc();
						is.DeleteImage(AuthToken.m_szAuthToken, mfbii);
					}
					
					// Now remove this from the existing images in the source
					ArrayList<MFBImageInfo> alNewImages = new ArrayList<MFBImageInfo>();
					MFBImageInfo[] rgMfbii = src.getImages();
					for (MFBImageInfo m : rgMfbii) // re-add images that are NOT the one being deleted
						if (mfbii.getID() != m.getID() || m.ThumbnailFile.compareTo(mfbii.ThumbnailFile) != 0)
							alNewImages.add(m);
					src.setImages(alNewImages.toArray(new MFBImageInfo[0]));

					setUpImageGallery(src.getGalleryID(), src.getImages());
				}
			})
			.setNegativeButton(R.string.lblCancel, null)
			.show();
			break;
		case R.id.menuViewImage:
			mfbii.ViewFullImageInWebView(getActivity());
			break;
		}
		return true;
	}
	
	public void ChoosePicture(GallerySource gs)
	{
		Intent i = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
		i.setType("image/* video/*");
		startActivityForResult(i, SELECT_IMAGE_ACTIVITY_REQUEST_CODE); 
	}
	
	private Boolean fCheckImagePermissions(int permission) {	
		if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
			return true;
		
	    // Should we show an explanation?
	    if (ActivityCompat.shouldShowRequestPermissionRationale(getActivity(), Manifest.permission.CAMERA)) {
	        // No explanation needed, we can request the permission.
	        ActivityCompat.requestPermissions(getActivity(),
	                new String[]{Manifest.permission.CAMERA}, permission);
	    }
	    return false;
	}

	public void TakePicture()
	{
		File fTemp = null;
		if (MFBConstants.fFakePix)
		{
			String[] rgSamples = {"sampleimg.jpg", "sampleimg2.jpg", "sampleimg3.jpg"};
			for (String szAsset : rgSamples)
			{
				FileOutputStream fos = null;
				try {
					InputStream is = getActivity().getAssets().open(szAsset);

					fTemp = File.createTempFile(TEMP_IMG_FILE_NAME, ".jpg");
					fTemp.deleteOnExit();
					m_TempFilePath = fTemp.getAbsolutePath(); // need to save this for when the picture comes back
					
					fos = new FileOutputStream(fTemp);
					
					byte[] rgBuffer = new byte[1024];
					
					int length;
					try {
						while ((length = is.read(rgBuffer)) > 0) {
							fos.write(rgBuffer, 0, length);
						}
					} 
					catch (IOException e) { e.printStackTrace(); }
					
					AddCameraImage(m_TempFilePath, false);
					
				} catch (IOException e) 
				{
					e.printStackTrace(); 
				}
				finally
				{
					if (fos != null)
						try {
							fos.close();
						} catch (IOException e) {
							e.printStackTrace();
						}
				}
			}
			return;
		}
		try {
			if (!fCheckImagePermissions(CAMERA_PERMISSION_IMAGE))
				return;
			fTemp = File.createTempFile(TEMP_IMG_FILE_NAME, ".jpg", Environment.getExternalStorageDirectory());
			fTemp.deleteOnExit();
			m_TempFilePath = fTemp.getAbsolutePath(); // need to save this for when the picture comes back

			SharedPreferences mPrefs = getActivity().getPreferences(Activity.MODE_PRIVATE);
	    	SharedPreferences.Editor ed = mPrefs.edit();
	    	ed.putString(keyTempFileInProgress, m_TempFilePath);
	    	ed.commit();

			Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
			intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(fTemp));
			intent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1);
			startActivityForResult(intent, CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE);
		} catch (IOException e) {
			e.printStackTrace();
			MFBUtil.Alert(getActivity(), getString(R.string.txtError), getString(R.string.errNoCamera));
		}
	}
	
	public void TakeVideo()
	{
		File fTemp = null;

		try {
			if (!fCheckImagePermissions(CAMERA_PERMISSION_VIDEO))
				return;
			fTemp = File.createTempFile(TEMP_IMG_FILE_NAME, ".mp4", Environment.getExternalStorageDirectory());
			fTemp.deleteOnExit();
			m_TempFilePath = fTemp.getAbsolutePath(); // need to save this for when the picture comes back

			SharedPreferences mPrefs = getActivity().getPreferences(Activity.MODE_PRIVATE);
	    	SharedPreferences.Editor ed = mPrefs.edit();
	    	ed.putString(keyTempFileInProgress, m_TempFilePath);
	    	ed.commit();

			Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
			intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(fTemp));
			intent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1);
			startActivityForResult(intent, CAPTURE_VIDEO_ACTIVITY_REQUEST_CODE);
		} catch (IOException e) {
			e.printStackTrace();
			MFBUtil.Alert(getActivity(), getString(R.string.txtError), getString(R.string.errNoCamera));
		}
	}
}
