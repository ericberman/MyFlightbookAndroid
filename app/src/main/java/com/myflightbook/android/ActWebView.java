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
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.File;

import Model.MFBConstants;

public class ActWebView extends Activity {

    public String szURL = "";
    public String szTempFile = "";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.webview);
        szURL = this.getIntent().getStringExtra(MFBConstants.intentViewURL);
        szTempFile = this.getIntent().getStringExtra(MFBConstants.intentViewTempFile);
        WebView wv = (WebView) findViewById(R.id.wvMain);
        wv.setWebChromeClient(new WebChromeClient());
        wv.getSettings().setJavaScriptEnabled(true);
        wv.getSettings().setBuiltInZoomControls(true);
        wv.getSettings().setSupportZoom(true);

        //noinspection deprecation
        wv.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return false;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest url) {
                return false;
            }
        });
        wv.loadUrl(szURL);
    }

    public void onPause() {
        super.onPause();
        if (szTempFile != null && szTempFile.length() > 0) {
            if (!(new File(szTempFile).delete())) {
                Log.e(MFBConstants.LOG_TAG, "Delete of temp file failed in ActWebView");
            }
        }
    }

    public static void ViewTempFile(Activity a, File f) {
        Intent i = new Intent(a, ActWebView.class);
        i.putExtra(MFBConstants.intentViewURL, Uri.fromFile(f).toString());
        i.putExtra(MFBConstants.intentViewTempFile, f.getAbsolutePath());
        a.startActivity(i);
    }

    public static void ViewURL(Activity a, String szURL) {
        Intent i = new Intent(a, ActWebView.class);
        i.putExtra(MFBConstants.intentViewURL, szURL);
        a.startActivity(i);
    }
}
