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
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Gallery;
import android.widget.LinearLayout;
import android.widget.RadioButton;
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
	protected static final int GALLERY_PERMISSION = 85;
	protected static final String TEMP_IMG_FILE_NAME = "takenpicture";
	protected String m_TempFilePath = "";
	
	private class AddCameraTask extends AsyncTask<String, String, Boolean> implements MFBSoap.MFBSoapProgressUpdate
	{
		MFBImageInfo mfbii = null;
		public Boolean fGeoTag = true;
		public Boolean fDeleteFileWhenDone = false;
		public Boolean fAddToGallery = false;
		public Boolean m_fVideo = false;
		ContentResolver m_cr = null;
		
		public AddCameraTask(int ActivityRequestCode, Boolean fVideo, ContentResolver cr)
		{
			m_fVideo = fVideo;
			fAddToGallery = fDeleteFileWhenDone =  (ActivityRequestCode == CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE || ActivityRequestCode == CAPTURE_VIDEO_ACTIVITY_REQUEST_CODE);
			fGeoTag = (ActivityRequestCode == CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE);
			m_cr = cr;
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
				File f = new File(szFilename);
				Uri uriSource = FileProvider.getUriForFile(ActMFBForm.this.getContext(), "com.example.android.fileprovider", f);
				ActMFBForm.this.getActivity().sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uriSource));
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
		AddCameraTask act = new AddCameraTask(CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE, fVideo, this.getActivity().getContentResolver());
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

		AddCameraTask act = new AddCameraTask(SELECT_IMAGE_ACTIVITY_REQUEST_CODE, fIsVideo, cr);
		act.execute(szFilename);
	}
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// need to restore this here because OnResume may come after the onActivityResult call
		SharedPreferences mPrefs = getActivity().getPreferences(Activity.MODE_PRIVATE);
		m_TempFilePath = mPrefs.getString(keyTempFileInProgress, "");				
	}

	//region binding data to forms
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
	//endregion

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

	//region Image/video permissions
	private Boolean fCheckImagePermissions(int permission) {
		switch (permission) {
			case CAMERA_PERMISSION_IMAGE:
			case CAMERA_PERMISSION_VIDEO:
				if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
						ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
						ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
					return true;
				else
					requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, permission);
				break;
			case GALLERY_PERMISSION:
				if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
						ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
					return true;
				else
					requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, permission);
				break;
		}
	    return false;
	}

	@Override
	public void onRequestPermissionsResult(int requestCode,
										   String permissions[], int[] grantResults) {
		Boolean fAllGranted = true;
		for (int i : grantResults)
			if (i != PackageManager.PERMISSION_GRANTED)
				fAllGranted = false;

		switch (requestCode) {
			case CAMERA_PERMISSION_IMAGE:
				if (fAllGranted && grantResults.length == 3)
					TakePicture();
				break;
			case CAMERA_PERMISSION_VIDEO:
				if (fAllGranted && grantResults.length == 3)
					TakeVideo();
				break;
			case GALLERY_PERMISSION:
				if (fAllGranted && grantResults.length == 2)
					ChoosePicture();
				break;
		}
	}
	//endregion

	//region image/video selection
	public void ChoosePicture()
	{
		Intent i = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
		i.setType("image/* video/*");
		startActivityForResult(i, SELECT_IMAGE_ACTIVITY_REQUEST_CODE);
	}

	public void TakePicture()
	{
		File fTemp = null;
		File storageDir = getActivity().getExternalFilesDir(Environment.DIRECTORY_PICTURES);
		if (MFBConstants.fFakePix)
		{
			String[] rgSamples = {"sampleimg.jpg", "sampleimg2.jpg", "sampleimg3.jpg"};
			for (String szAsset : rgSamples)
			{
				FileOutputStream fos = null;
				try {
					InputStream is = getActivity().getAssets().open(szAsset);

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

			fTemp = File.createTempFile(TEMP_IMG_FILE_NAME, ".jpg", storageDir);
			fTemp.deleteOnExit();
			m_TempFilePath = fTemp.getAbsolutePath(); // need to save this for when the picture comes back

			SharedPreferences mPrefs = getActivity().getPreferences(Activity.MODE_PRIVATE);
	    	SharedPreferences.Editor ed = mPrefs.edit();
	    	ed.putString(keyTempFileInProgress, m_TempFilePath);
	    	ed.commit();

			Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
			Uri uriImage = FileProvider.getUriForFile(this.getContext(), "com.example.android.fileprovider", fTemp);
			intent.putExtra(MediaStore.EXTRA_OUTPUT, uriImage);
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
		File storageDir = getActivity().getExternalFilesDir(Environment.DIRECTORY_PICTURES);

		try {
			if (!fCheckImagePermissions(CAMERA_PERMISSION_VIDEO))
				return;
			fTemp = File.createTempFile(TEMP_IMG_FILE_NAME, ".mp4", storageDir);
			fTemp.deleteOnExit();
			m_TempFilePath = fTemp.getAbsolutePath(); // need to save this for when the picture comes back

			SharedPreferences mPrefs = getActivity().getPreferences(Activity.MODE_PRIVATE);
	    	SharedPreferences.Editor ed = mPrefs.edit();
	    	ed.putString(keyTempFileInProgress, m_TempFilePath);
	    	ed.commit();

			Uri uriImage = FileProvider.getUriForFile(this.getContext(), "com.example.android.fileprovider", fTemp);

			Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
			intent.putExtra(MediaStore.EXTRA_OUTPUT, uriImage);
			intent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1);
			startActivityForResult(intent, CAPTURE_VIDEO_ACTIVITY_REQUEST_CODE);
		} catch (IOException e) {
			e.printStackTrace();
			MFBUtil.Alert(getActivity(), getString(R.string.txtError), getString(R.string.errNoCamera));
		}
	}
	//endregion

	//region expand/collapse
	// next two methods are adapted from http://stackoverflow.com/questions/19263312/how-to-achieve-smooth-expand-collapse-animation
	public void expandView(final View v) {
		v.measure(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		final int targtetHeight = v.getMeasuredHeight();

		v.getLayoutParams().height = 0;
		v.setVisibility(View.VISIBLE);
		Animation a = new Animation()
		{
			@Override
			protected void applyTransformation(float interpolatedTime, Transformation t) {
				v.getLayoutParams().height = interpolatedTime == 1
						? LinearLayout.LayoutParams.WRAP_CONTENT
						: (int)(targtetHeight * interpolatedTime);
				v.requestLayout();
			}

			@Override
			public boolean willChangeBounds() {
				return true;
			}
		};

		a.setDuration((int)(targtetHeight / v.getContext().getResources().getDisplayMetrics().density));
		v.startAnimation(a);
	}

	public void collapseView(final View v) {
		final int initialHeight = v.getMeasuredHeight();

		Animation a = new Animation()
		{
			@Override
			protected void applyTransformation(float interpolatedTime, Transformation t) {
				if(interpolatedTime == 1){
					v.setVisibility(View.GONE);
				}else{
					v.getLayoutParams().height = initialHeight - (int)(initialHeight * interpolatedTime);
					v.requestLayout();
				}
			}

			@Override
			public boolean willChangeBounds() {
				return true;
			}
		};

		a.setDuration((int)(initialHeight / v.getContext().getResources().getDisplayMetrics().density));
		v.startAnimation(a);
	}

	public void setExpandedState(TextView v, View target, Boolean fExpanded) {
		Drawable d = null;
		if (fExpanded) {
			collapseView(target);
			d = ContextCompat.getDrawable(getActivity(), R.drawable.expand_light);
		} else {
			expandView(target);
			d = ContextCompat.getDrawable(getActivity(), R.drawable.collapse_light);
		}
		v.setCompoundDrawablesWithIntrinsicBounds(d, null, null, null);
	}
	//endregion
}
