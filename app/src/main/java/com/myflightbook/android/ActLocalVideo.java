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

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.VideoView;

import java.io.File;

import Model.MFBConstants;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

public class ActLocalVideo extends AppCompatActivity {
    private String szTempFile = "";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.localvideo);
        String szURL = this.getIntent().getStringExtra(MFBConstants.intentViewURL);
        szTempFile = this.getIntent().getStringExtra(MFBConstants.intentViewTempFile);

        VideoView video = findViewById(R.id.video);
        // Load and start the movie
        video.setVideoURI(Uri.parse(szURL));
        video.start();
    }

    public void onPause() {
        super.onPause();
        if (szTempFile != null && szTempFile.length() > 0) {
            if (!(new File(szTempFile).delete()))
                Log.v(MFBConstants.LOG_TAG, "Local video delete failed");
        }
    }

    public static void ViewTempFile(android.app.Activity a, File f) {
        Intent i = new Intent(a, ActLocalVideo.class);
        i.putExtra(MFBConstants.intentViewURL, FileProvider.getUriForFile(a, BuildConfig.APPLICATION_ID + ".provider", f).toString());
        i.putExtra(MFBConstants.intentViewTempFile, f.getAbsolutePath());
        a.startActivity(i);
    }
}
