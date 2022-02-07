/*
	MyFlightbook for Android - provides native access to MyFlightbook
	pilot's logbook
    Copyright (C) 2017-2022 MyFlightbook, LLC

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

import com.myflightbook.android.webservices.AuthToken.Companion.isValid
import com.myflightbook.android.webservices.MFBSoap.Companion.isOnline
import android.os.Bundle
import android.content.Intent
import android.app.Activity
import android.widget.AdapterView.OnItemClickListener
import com.myflightbook.android.MFBMain.Invalidatable
import androidx.activity.result.ActivityResultLauncher
import model.LazyThumbnailLoader.ThumbnailedItem
import model.Aircraft
import model.MFBImageInfo
import android.os.AsyncTask
import com.myflightbook.android.webservices.MFBSoap
import android.app.ProgressDialog
import android.content.Context
import com.myflightbook.android.webservices.AircraftSvc
import com.myflightbook.android.webservices.AuthToken
import model.MFBUtil
import model.MFBConstants
import androidx.core.text.HtmlCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import model.LazyThumbnailLoader
import android.util.Log
import android.view.*
import android.widget.*
import androidx.activity.result.ActivityResult
import androidx.fragment.app.ListFragment
import java.lang.Exception
import java.util.*

class ActAircraft : ListFragment(), OnItemClickListener, Invalidatable {
    private var mAircraftrows: Array<AircraftRowItem>? = null
    private var mFhasheaders = false
    private var mNewAircraftLauncher: ActivityResultLauncher<Intent>? = null

    internal enum class RowType {
        DATA_ITEM, HEADER_ITEM
    }

    internal class AircraftRowItem : ThumbnailedItem {
        var aircraftItem: Aircraft? = null
        var title: String? = null
        var rowType = RowType.DATA_ITEM

        constructor(obj: Aircraft?) {
            aircraftItem = obj
        }

        constructor(szTitle: String?) {
            rowType = RowType.HEADER_ITEM
            title = szTitle
        }

        override val defaultImage : MFBImageInfo? get() {
            return if (rowType == RowType.HEADER_ITEM) null else aircraftItem!!.defaultImage
        }
    }

    private class RefreshAircraftTask(c: Context?, aa: ActAircraft) :
        AsyncTask<Void?, Void?, MFBSoap>() {
        private var mPd: ProgressDialog? = null
        var mResult: Array<Aircraft>? = null
        val mCtxt: AsyncWeakContext<ActAircraft> = AsyncWeakContext(c, aa)

        override fun doInBackground(vararg params: Void?): MFBSoap {
            val aircraftSvc = AircraftSvc()
            mResult = aircraftSvc.getAircraftForUser(AuthToken.m_szAuthToken, mCtxt.context)
            return aircraftSvc
        }

        override fun onPreExecute() {
            mPd =
                MFBUtil.showProgress(mCtxt.context, mCtxt.context!!.getString(R.string.prgAircraft))
        }

        override fun onPostExecute(svc: MFBSoap) {
            try {
                if (mPd != null) mPd!!.dismiss()
            } catch (e: Exception) {
                Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e))
            }
            val aa = mCtxt.callingActivity
            if (aa == null || !aa.isAdded || aa.isDetached || aa.activity == null) return
            val rgac = mResult!!
            if (rgac.isEmpty()) MFBUtil.alert(
                aa,
                aa.getString(R.string.txtError),
                aa.getString(R.string.errNoAircraftFound)
            )
            val lstFavorite = ArrayList<Aircraft>()
            val lstArchived = ArrayList<Aircraft>()
            for (ac in rgac) {
                if (ac.hideFromSelection) lstArchived.add(ac) else lstFavorite.add(ac)
            }
            aa.mFhasheaders = lstArchived.size > 0 && lstFavorite.size > 0
            val arRows = ArrayList<AircraftRowItem>()
            if (aa.mFhasheaders) arRows.add(AircraftRowItem(aa.getString(R.string.lblFrequentlyUsedAircraft)))
            for (ac in lstFavorite) arRows.add(AircraftRowItem(ac))
            if (aa.mFhasheaders) arRows.add(AircraftRowItem(aa.getString(R.string.lblArchivedAircraft)))
            for (ac in lstArchived) arRows.add(AircraftRowItem(ac))
            aa.mAircraftrows = arRows.toTypedArray()
            aa.populateList()
        }

    }

    private inner class AircraftAdapter(
        c: Context?,
        rgac: Array<AircraftRowItem>?
    ) : ArrayAdapter<AircraftRowItem?>(
        c!!, R.layout.aircraft, rgac ?: arrayOfNulls<AircraftRowItem>(0)
    ) {
        override fun getViewTypeCount(): Int {
            return if (mFhasheaders) 2 else 1
        }

        override fun getItemViewType(position: Int): Int {
            return if (mAircraftrows == null || mAircraftrows!!.isEmpty()) RowType.DATA_ITEM.ordinal else mAircraftrows!![position].rowType.ordinal
        }

        private fun checkRowType(v: View): RowType {
            return if (v.findViewById<View?>(R.id.lblTableRowSectionHeader) == null) RowType.DATA_ITEM else RowType.HEADER_ITEM
        }

        override fun getView(position: Int, vIn: View?, parent: ViewGroup): View {
            var v = vIn
            val rt = RowType.values()[getItemViewType(position)]
            if (v == null || checkRowType(v) != rt) {
                val vi =
                    requireActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
                val layoutID =
                    if (rt == RowType.HEADER_ITEM) R.layout.listviewsectionheader else R.layout.aircraft
                v = vi.inflate(layoutID, parent, false)
            }
            if (mAircraftrows == null || mAircraftrows!!.isEmpty()) return v!!
            if (rt == RowType.HEADER_ITEM) {
                val tvSectionHeader = v!!.findViewById<TextView>(R.id.lblTableRowSectionHeader)
                tvSectionHeader.text = mAircraftrows!![position].title
                return v
            }
            val ac = mAircraftrows!![position].aircraftItem
            val tvTail = v!!.findViewById<TextView>(R.id.txtAircraftDetails)

            // Show the camera if the aircraft has images.
            val imgCamera = v.findViewById<ImageView>(R.id.imgCamera)
            if (ac!!.hasImage()) {
                val mfbii = ac.aircraftImages!![0]
                val b = mfbii.bitmapFromThumb()
                if (b != null) {
                    imgCamera.setImageBitmap(b)
                }
            } else {
                imgCamera.setImageResource(R.drawable.noimage)
            }
            val textColor =
                tvTail.currentTextColor and 0x00FFFFFF or if (ac.hideFromSelection) -0x78000000 else -0x1000000
            tvTail.setTextColor(textColor)
            val szInstanceType =
                " " + if (ac.isReal()) "" else getString(Aircraft.rgidInstanceTypes[ac.instanceTypeID - 1])
            val szAircraftDetails = String.format(
                Locale.getDefault(),
                "<big><b>%s</b></big> <i>%s</i><br />%s %s",
                ac.displayTailNumber(),
                (ac.modelDescription + " " + ac.modelCommonName).trim { it <= ' ' } + szInstanceType,
                ac.privateNotes,
                ac.publicNotes)
            tvTail.text = HtmlCompat.fromHtml(szAircraftDetails, HtmlCompat.FROM_HTML_MODE_LEGACY)
            return v
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        setHasOptionsMenu(true)
        return inflater.inflate(R.layout.aircraftlist, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        MFBMain.registerNotifyResetAll(this)
        val srl: SwipeRefreshLayout = requireView().findViewById(R.id.swiperefresh)
        srl.setOnRefreshListener {
            srl.isRefreshing = false
            refreshAircraft()
        }
        mNewAircraftLauncher = registerForActivityResult(
            StartActivityForResult()
        ) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                invalidate()
            }
        }
    }

    override fun onDestroy() {
        MFBMain.unregisterNotify(this)
        super.onDestroy()
    }

    // update the list if our array is null
    override fun onResume() {
        super.onResume()
        if (isValid() && mAircraftrows == null) {
            val st = RefreshAircraftTask(activity, this)
            st.execute()
        } else populateList()
    }

    private fun populateList() {
        if (mAircraftrows == null) return
        val a = activity ?: return
        val aa = AircraftAdapter(a, mAircraftrows)
        listAdapter = aa
        listView.onItemClickListener = this
        Thread(LazyThumbnailLoader(mAircraftrows as Array<ThumbnailedItem>, aa)).start()
    }

    override fun onItemClick(parent: AdapterView<*>?, view: View, position: Int, id: Long) {
        if (mAircraftrows == null || position < 0 || position >= mAircraftrows!!.size || mAircraftrows!![position].rowType == RowType.HEADER_ITEM) return
        val i = Intent(activity, EditAircraftActivity::class.java)
        val ac = mAircraftrows!![position].aircraftItem
        i.putExtra(ActEditAircraft.AIRCRAFTID, ac!!.aircraftID)
        mNewAircraftLauncher!!.launch(i)
    }

    private fun addAircraft() {
        mNewAircraftLauncher!!.launch(Intent(activity, NewAircraftActivity::class.java))
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.aircraftmenu, menu)
    }

    private fun refreshAircraft() {
        val ac = AircraftSvc()
        ac.flushCache()
        val st = RefreshAircraftTask(activity, this)
        st.execute()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (!isOnline(context)) {
            MFBUtil.alert(context, getString(R.string.txtError), getString(R.string.errNoInternet))
            return true
        }
        when (item.itemId) {
            R.id.menuRefreshAircraft -> refreshAircraft()
            R.id.menuNewAircraft -> addAircraft()
            else -> return super.onOptionsItemSelected(
                item
            )
        }
        return true
    }

    override fun invalidate() {
        AircraftSvc().flushCache()
        mAircraftrows = null
        val aa = this.listAdapter as AircraftAdapter?
        aa?.notifyDataSetChanged()
    }
}