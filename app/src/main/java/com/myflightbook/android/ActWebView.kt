/*
	MyFlightbook for Android - provides native access to MyFlightbook
	pilot's logbook
    Copyright (C) 2017-2023 MyFlightbook, LLC

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

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.webkit.*
import android.webkit.WebChromeClient.FileChooserParams
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import model.MFBConstants
import java.io.File

class ActWebView : AppCompatActivity() {
    private var szTempFile: String? = ""
    private var mUploadMessage: ValueCallback<Uri>? = null
    private var uploadMessage: ValueCallback<Array<Uri>>? = null
    private var mFileChooser: ActivityResultLauncher<Intent>? = null

    // Code to enable file upload adapted from https://stackoverflow.com/questions/11724129/android-webview-file-upload.
    @SuppressLint("SetJavaScriptEnabled")
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.webview)
        val btnBack = findViewById<Button>(R.id.btnWebBack)
        val btnForward = findViewById<Button>(R.id.btnWebForward)
        val btnRefresh = findViewById<Button>(R.id.btnWebRefresh)
        val szURL = this.intent.getStringExtra(MFBConstants.intentViewURL)
        szTempFile = this.intent.getStringExtra(MFBConstants.intentViewTempFile)
        mFileChooser = registerForActivityResult(
            StartActivityForResult()
        ) { result: ActivityResult ->
            if (result.resultCode == RESULT_OK) {
                if (uploadMessage == null) return@registerForActivityResult
                uploadMessage!!.onReceiveValue(
                    FileChooserParams.parseResult(
                        result.resultCode,
                        result.data
                    )
                )
                uploadMessage = null
            }
        }
        val wv = findViewById<WebView>(R.id.wvMain)
        wv.webChromeClient = object : WebChromeClient() {
            // For 3.0+ Devices (Start)
            // onActivityResult attached before constructor
            @Suppress("UNUSED_PARAMETER", "UNUSED")
            private fun openFileChooser(uploadMsg: ValueCallback<Uri>?, acceptType: String?) {
                mUploadMessage = uploadMsg
                val i = Intent(Intent.ACTION_GET_CONTENT)
                i.addCategory(Intent.CATEGORY_OPENABLE)
                i.type = "image/*|application/pdf"
                mFileChooser!!.launch(i)
            }

            // For Lollipop 5.0+ Devices
            override fun onShowFileChooser(
                mWebView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                if (uploadMessage != null) {
                    uploadMessage!!.onReceiveValue(null)
                    uploadMessage = null
                }
                uploadMessage = filePathCallback
                val intent = fileChooserParams.createIntent()
                try {
                    mFileChooser!!.launch(intent)
                } catch (e: ActivityNotFoundException) {
                    uploadMessage = null
                    Toast.makeText(
                        this@ActWebView.applicationContext,
                        "Cannot Open File Chooser",
                        Toast.LENGTH_LONG
                    ).show()
                    return false
                }
                return true
            }

            //For Android 4.1 only
            @Suppress("UNUSED_PARAMETER", "UNUSED")
            private fun openFileChooser(
                uploadMsg: ValueCallback<Uri>?,
                acceptType: String?,
                capture: String?
            ) {
                mUploadMessage = uploadMsg
                val intent = Intent(Intent.ACTION_GET_CONTENT)
                intent.addCategory(Intent.CATEGORY_OPENABLE)
                intent.type = "image/*|application/pdf"
                mFileChooser!!.launch(Intent.createChooser(intent, "File Browser"))
            }

            @Suppress("UNUSED_PARAMETER", "UNUSED")
            private fun openFileChooser(uploadMsg: ValueCallback<Uri>?) {
                mUploadMessage = uploadMsg
                val i = Intent(Intent.ACTION_GET_CONTENT)
                i.addCategory(Intent.CATEGORY_OPENABLE)
                i.type = "image/*|application/pdf"
                mFileChooser!!.launch(Intent.createChooser(i, "File Chooser"))
            }
        }
        val ws = wv.settings
        ws.javaScriptEnabled = true
        ws.builtInZoomControls = true
        ws.setSupportZoom(true)
        ws.allowFileAccess = true
        ws.allowContentAccess = true
        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, url: WebResourceRequest): Boolean {
                return false
            }
        }
        val u = szURL!!
        val fn = szTempFile
        if (u.endsWith(".JPEG", true) || u.endsWith(".JPG", true) && (fn == null || fn.isEmpty())) {
            val html =
                "<html><body><img src=\"${u}\" width=\"100%\" /></body></html>"
            wv.loadData(html, "text/html", null)
        } else
            wv.loadUrl(u)
        wv.setDownloadListener { url: String?, _: String?, _: String?, mimetype: String, _: Long ->
            if (mimetype.compareTo(
                    "text/calendar",
                    ignoreCase = true
                ) == 0 || mimetype.compareTo("application/pdf", ignoreCase = true) == 0
            ) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                finish()
            }
        }
        btnBack.setOnClickListener { if (wv.canGoBack()) wv.goBack() else finish() }
        btnForward.setOnClickListener { if (wv.canGoForward()) wv.goForward() }
        btnRefresh.setOnClickListener { wv.reload() }
    }

    public override fun onPause() {
        super.onPause()
        if (szTempFile != null && szTempFile!!.isNotEmpty()) {
            if (!File(szTempFile!!).delete()) {
                Log.e(MFBConstants.LOG_TAG, "Delete of temp file failed in ActWebView")
            }
        }
    }

    companion object {
        fun viewTempFile(a: Activity, f: File) {
            val i = Intent(a, ActWebView::class.java)
            i.putExtra(
                MFBConstants.intentViewURL,
                FileProvider.getUriForFile(a, BuildConfig.APPLICATION_ID + ".provider", f)
                    .toString()
            )
            i.putExtra(MFBConstants.intentViewTempFile, f.absolutePath)
            a.startActivity(i)
        }

        fun viewURL(a: Activity, szURL: String?) {
            val i = Intent(a, ActWebView::class.java)
            i.putExtra(MFBConstants.intentViewURL, szURL)
            a.startActivity(i)
        }
    }
}