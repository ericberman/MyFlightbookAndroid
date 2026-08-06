/*
	MyFlightbook for Android - provides native access to MyFlightbook
	pilot's logbook
    Copyright (C) 2017-2026 MyFlightbook, LLC

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

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.fragment.app.ListFragment
import com.myflightbook.android.webservices.AuthToken.Companion.isValid
import com.myflightbook.android.webservices.MFBSoap.Companion.isOnline
import model.MFBConstants.authRedirWithParams
import model.MFBUtil.alert

class ActTraining : ListFragment(), OnItemClickListener {
    internal class TrainingItem(val idTitle: Int, val szURLDest: String)

    private val mRgtrainingitems = arrayOf(
        TrainingItem(R.string.lblStudents, "students"),
        TrainingItem(R.string.lblInstructors, "instructors"),
        TrainingItem(R.string.lblReqSigs, "reqsigs"),
        TrainingItem(R.string.lblEndorsements, ENDORSE_ITEM),
        TrainingItem(R.string.lbl8710, "8710"),
        TrainingItem(R.string.lblModelRollup, "ModelRollup"),
        TrainingItem(R.string.lblTimeRollup, "TimeRollup"),
        TrainingItem(R.string.lblAchievements, "badges"),
        TrainingItem(R.string.lblRatingsProgress, "progress")
    )

    private inner class TrainingAdapter(
        c: Context?,
        private val mRgti: Array<TrainingItem>?
    ) : ArrayAdapter<TrainingItem?>(
        c!!, R.layout.trainingitem, mRgti!!
    ) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            var v = convertView
            if (v == null) {
                val vi =
                    (requireActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater)
                v = vi.inflate(R.layout.trainingitem, parent, false)
            }
            if (mRgti == null) return v!!
            val ti = mRgti[position]
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

    override fun onItemClick(parent: AdapterView<*>?, view: View, position: Int, id: Long) {
        // TODO: IsOnline doesn't work from main thread.
        if (!isValid() || !isOnline(context)) {
            alert(this, getString(R.string.txtError), getString(R.string.errTrainingNotAvailable))
            return
        }
        if (position >= 0 && position < mRgtrainingitems.size) {
            val szDest = mRgtrainingitems[position].szURLDest
            ActWebView.viewURL(requireActivity(), authRedirWithParams("d=$szDest", context))
        }
    }

    companion object {
        private const val ENDORSE_ITEM = "endorse"
    }
}