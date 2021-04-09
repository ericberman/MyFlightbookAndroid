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
package com.myflightbook.android;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.Toast;

import java.io.File;

import Model.MFBConstants;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

public class ActWebView extends AppCompatActivity {

    private String szTempFile = "";
    private ValueCallback<Uri> mUploadMessage;
    private ValueCallback<Uri[]> uploadMessage;
    private ActivityResultLauncher<Intent> mFileChooser = null;

    // Code to enable file upload adapted from https://stackoverflow.com/questions/11724129/android-webview-file-upload.

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.webview);

        Button btnBack = findViewById(R.id.btnWebBack);
        Button btnForward = findViewById(R.id.btnWebForward);
        Button btnRefresh = findViewById(R.id.btnWebRefresh);

        String szURL = this.getIntent().getStringExtra(MFBConstants.intentViewURL);
        szTempFile = this.getIntent().getStringExtra(MFBConstants.intentViewTempFile);

        mFileChooser = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        if (uploadMessage == null)
                            return;
                        uploadMessage.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result.getResultCode(), result.getData()));
                        uploadMessage = null;
                    }
                });

        WebView wv = findViewById(R.id.wvMain);
        wv.setWebChromeClient(new WebChromeClient() {
            // For 3.0+ Devices (Start)
            // onActivityResult attached before constructor
            protected void openFileChooser(ValueCallback<Uri> uploadMsg, String acceptType)
            {
                mUploadMessage = uploadMsg;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("image/*|application/pdf");
                mFileChooser.launch(i);
            }


            // For Lollipop 5.0+ Devices
            @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
            public boolean onShowFileChooser(WebView mWebView, ValueCallback<Uri[]> filePathCallback, WebChromeClient.FileChooserParams fileChooserParams)
            {
                if (uploadMessage != null) {
                    uploadMessage.onReceiveValue(null);
                    uploadMessage = null;
                }

                uploadMessage = filePathCallback;

                Intent intent = fileChooserParams.createIntent();
                try
                {
                    mFileChooser.launch(intent);
                } catch (ActivityNotFoundException e)
                {
                    uploadMessage = null;
                    Toast.makeText(ActWebView.this.getApplicationContext(), "Cannot Open File Chooser", Toast.LENGTH_LONG).show();
                    return false;
                }
                return true;
            }

            //For Android 4.1 only
            protected void openFileChooser(ValueCallback<Uri> uploadMsg, @SuppressWarnings("UnusedParameters") String acceptType, @SuppressWarnings("UnusedParameters") String capture)
            {
                mUploadMessage = uploadMsg;
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("image/*|application/pdf");
                mFileChooser.launch(Intent.createChooser(intent, "File Browser"));
            }

            protected void openFileChooser(ValueCallback<Uri> uploadMsg)
            {
                mUploadMessage = uploadMsg;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("image/*|application/pdf");
                mFileChooser.launch(Intent.createChooser(i, "File Chooser"));
            }
        });

        WebSettings ws = wv.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setBuiltInZoomControls(true);
        ws.setSupportZoom(true);
        ws.setAllowFileAccess(true);
        ws.setAllowContentAccess(true);

        wv.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest url) {
                return false;
            }
        });
        assert szURL != null;
        wv.loadUrl(szURL);
        wv.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            if (mimetype.compareToIgnoreCase("text/calendar") == 0 || mimetype.compareToIgnoreCase("application/pdf") == 0) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                finish();
            }
        });

        btnBack.setOnClickListener(view -> {
            if (wv.canGoBack())
                wv.goBack();
            else
                finish();
        });

        btnForward.setOnClickListener(view -> {
            if (wv.canGoForward())
                wv.goForward();
        });

        btnRefresh.setOnClickListener(view -> wv.reload());
    }

    public void onPause() {
        super.onPause();
        if (szTempFile != null && szTempFile.length() > 0) {
            if (!(new File(szTempFile).delete())) {
                Log.e(MFBConstants.LOG_TAG, "Delete of temp file failed in ActWebView");
            }
        }
    }

    public static void ViewTempFile(android.app.Activity a, File f) {
        Intent i = new Intent(a, ActWebView.class);
        i.putExtra(MFBConstants.intentViewURL, FileProvider.getUriForFile(a, BuildConfig.APPLICATION_ID + ".provider", f).toString());
        i.putExtra(MFBConstants.intentViewTempFile, f.getAbsolutePath());
        a.startActivity(i);
    }

    public static void ViewURL(android.app.Activity a, String szURL) {
        Intent i = new Intent(a, ActWebView.class);
        i.putExtra(MFBConstants.intentViewURL, szURL);
        a.startActivity(i);
    }
}
