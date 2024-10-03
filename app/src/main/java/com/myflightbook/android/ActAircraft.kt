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

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.core.text.HtmlCompat
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.ListFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.myflightbook.android.MFBMain.Invalidatable
import com.myflightbook.android.webservices.AircraftSvc
import com.myflightbook.android.webservices.AuthToken
import com.myflightbook.android.webservices.AuthToken.Companion.isValid
import com.myflightbook.android.webservices.MFBSoap.Companion.isOnline
import kotlinx.coroutines.launch
import model.*
import model.LazyThumbnailLoader.ThumbnailedItem
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

    private suspend fun refreshAircraftInBackground() {
        ActMFBForm.doAsync<AircraftSvc, Array<Aircraft>?>(requireActivity(),
            AircraftSvc(),
            getString(R.string.prgAircraft),
            {
                    s : AircraftSvc -> s.getAircraftForUser(AuthToken.m_szAuthToken, requireContext())
            },
            {
                svc: AircraftSvc, result : Array<Aircraft>? ->

                val rgac = result ?: arrayOf()
                if (rgac.isEmpty())
                    MFBUtil.alert(
                        requireContext(),
                        getString(R.string.txtError),
                        svc.lastError.ifEmpty { getString(R.string.errNoAircraftFound) }
                    )
                else {
                    val lstFavorite = ArrayList<Aircraft>()
                    val lstArchived = ArrayList<Aircraft>()
                    for (ac in rgac) {
                        if (ac.hideFromSelection) lstArchived.add(ac) else lstFavorite.add(ac)
                    }
                    mFhasheaders = lstArchived.size > 0 && lstFavorite.size > 0
                    val arRows = ArrayList<AircraftRowItem>()
                    if (mFhasheaders) arRows.add(AircraftRowItem(getString(R.string.lblFrequentlyUsedAircraft)))
                    for (ac in lstFavorite) arRows.add(AircraftRowItem(ac))
                    if (mFhasheaders) arRows.add(AircraftRowItem(getString(R.string.lblArchivedAircraft)))
                    for (ac in lstArchived) arRows.add(AircraftRowItem(ac))
                    mAircraftrows = arRows.toTypedArray()
                    populateList()
                }
            }
        )
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
                if (ac.isAnonymous()) ac.modelCommonName else (ac.modelDescription + " " + ac.modelCommonName).trim { it <= ' ' } + szInstanceType,
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

        val menuHost: MenuHost = requireActivity()

        // Add menu items without using the Fragment Menu APIs
        // Note how we can tie the MenuProvider to the viewLifecycleOwner
        // and an optional Lifecycle.State (here, RESUMED) to indicate when
        // the menu should be visible
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
                inflater.inflate(R.menu.aircraftmenu, menu)
            }

            override fun onMenuItemSelected(item: MenuItem): Boolean {
                if (!isOnline(context)) {
                    MFBUtil.alert(context, getString(R.string.txtError), getString(R.string.errNoInternet))
                    return true
                }
                when (item.itemId) {
                    R.id.menuRefreshAircraft -> refreshAircraft()
                    R.id.menuNewAircraft -> addAircraft()
                    else -> return false
                }
                return true
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    override fun onDestroy() {
        MFBMain.unregisterNotify(this)
        super.onDestroy()
    }

    // update the list if our array is null
    override fun onResume() {
        super.onResume()
        if (isValid() && mAircraftrows == null) {
            lifecycleScope.launch {
                refreshAircraftInBackground()
            }
        } else populateList()
    }

    private fun populateList() {
        if (mAircraftrows == null) return
        val a = activity ?: return
        val aa = AircraftAdapter(a, mAircraftrows)
        listAdapter = aa
        listView.onItemClickListener = this
        val mrows = mAircraftrows
        val rgthumbs = ArrayList<ThumbnailedItem>()
        for (row in mrows!!) {
            rgthumbs.add(row)
        }

        LazyThumbnailLoader(
            rgthumbs.toTypedArray(),
            aa,
            lifecycleScope
        ).start()
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

    private fun refreshAircraft() {
        val ac = AircraftSvc()
        ac.flushCache()
        lifecycleScope.launch {
            refreshAircraftInBackground()
        }
    }

    override fun invalidate() {
        AircraftSvc().flushCache()
        mAircraftrows = null
        val aa = this.listAdapter as AircraftAdapter?
        aa?.notifyDataSetChanged()
    }
}