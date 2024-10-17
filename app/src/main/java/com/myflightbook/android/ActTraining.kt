/*
	MyFlightbook for Android - provides native access to MyFlightbook
	pilot's logbook
    Copyright (C) 2017-2024 MyFlightbook, LLC

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
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.fragment.app.ListFragment
import com.myflightbook.android.webservices.AuthToken.Companion.isValid
import com.myflightbook.android.webservices.MFBSoap.Companion.isOnline
import model.MFBConstants.authRedirWithParams
import model.MFBUtil.alert

class ActTraining : ListFragment(), OnItemClickListener {
    private var mPermissionLauncher: ActivityResultLauncher<Array<String>>? = null

    internal class TrainingItem(val idTitle: Int, val szURLDest: String)

    private val mRgtrainingitems = arrayOf(
        TrainingItem(R.string.lblStudents, "students"),
        TrainingItem(R.string.lblInstructors, "instructors"),
        TrainingItem(R.string.lblReqSigs, "reqsigs"),
        TrainingItem(R.string.lblEndorsements, endorseItem),
        TrainingItem(R.string.lbl8710, "8710"),
        TrainingItem(R.string.lblModelRollup, "ModelRollup"),
        TrainingItem(R.string.lblTimeRollup, "TimeRollup"),
        TrainingItem(R.string.lblAchievements, "badges"),
        TrainingItem(R.string.lblRatingsProgress, "progress")
    )

    private inner class TrainingAdapter(
        c: Context?,
        private val m_rgti: Array<TrainingItem>?
    ) : ArrayAdapter<TrainingItem?>(
        c!!, R.layout.trainingitem, m_rgti!!
    ) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            var v = convertView
            if (v == null) {
                val vi =
                    (requireActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater)
                v = vi.inflate(R.layout.trainingitem, parent, false)
            }
            if (m_rgti == null) return v!!
            val ti = m_rgti[position]
            val tvti = v!!.findViewById<TextView>(R.id.txtTrainingItem)
            tvti.text = this@ActTraining.getString(ti.idTitle)
            return v
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        mPermissionLauncher = registerForActivityResult(
            RequestMultiplePermissions()
        ) { result: Map<String, Boolean>? ->
            if (result != null) {
                var fAllGranted = true
                for (sz in result.keys) {
                    val b = result[sz] ?: false
                    if (sz.compareTo(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == 0 && b) {
                        fAllGranted = true
                        break
                    }
                    fAllGranted = fAllGranted && b
                }

                // perform the actual click.
                val position = lastPositionClicked
                lastPositionClicked = -1 // clear this out now.
                if (position >= 0 && position < mRgtrainingitems.size && fAllGranted) {
                    val szDest = mRgtrainingitems[position].szURLDest
                    ActWebView.viewURL(requireActivity(), authRedirWithParams("d=$szDest", context))
                }
            }
        }
        return inflater.inflate(R.layout.training, container, false)
    }

    // update the list if our array is null
    override fun onResume() {
        super.onResume()
        populateList()
    }

    private fun populateList() {
        val ta = TrainingAdapter(activity, mRgtrainingitems)
        listAdapter = ta
        listView.onItemClickListener = this
    }

    private var lastPositionClicked = -1
    override fun onItemClick(parent: AdapterView<*>?, view: View, position: Int, id: Long) {
        // TODO: IsOnline doesn't work from main thread.
        if (!isValid() || !isOnline(context)) {
            alert(this, getString(R.string.txtError), getString(R.string.errTrainingNotAvailable))
            return
        }
        lastPositionClicked = position

        // check for permissions and handle click there.
        // See https://developer.android.com/reference/android/Manifest.permission#READ_EXTERNAL_STORAGE
        // If I understand the ever-changing Android requirements:
        // - Until Q (29), you need to request READ_EXTERNAL_STORAGE
        // - From Q (29) until Tiramasu (33), you don't need any permissions to read the user's media
        // From Tiramasu (33) onward, use READ_MEDIA_IMAGES/READ_MEDIA_VIDEO, not READ_EXTERNAL_STORAGE
        mPermissionLauncher!!.launch(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                arrayOf(
                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO
                )
            } else {
                arrayOf()
            }
        )
    }

    companion object {
        private const val endorseItem = "endorse"
    }
}