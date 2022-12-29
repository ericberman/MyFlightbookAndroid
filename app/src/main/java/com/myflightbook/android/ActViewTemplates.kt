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

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.TextView
import androidx.fragment.app.ListFragment
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.myflightbook.android.webservices.AuthToken
import com.myflightbook.android.webservices.CustomPropertyTypesSvc
import kotlinx.coroutines.launch
import model.MFBConstants
import model.PropertyTemplate
import model.PropertyTemplate.Companion.getSharedTemplates
import model.TemplateGroup.Companion.groupTemplates

class ActViewTemplates : ListFragment(), OnItemClickListener {
    enum class RowType {
        DATA_ITEM, HEADER_ITEM
    }

    private var mTemplaterows: Array<TemplateRowItem> = arrayOf()
    @JvmField
    var mActivetemplates: HashSet<PropertyTemplate?>? = HashSet()

    private fun refreshPropertyTypes() {
        lifecycleScope.launch {
            ActMFBForm.doAsync(
                requireActivity(),
                CustomPropertyTypesSvc(),
                getString(R.string.prgCPT),
                {s -> s.getCustomPropertyTypes(AuthToken.m_szAuthToken, false, requireContext())},
                {
                    _, result ->
                    if (result != null) {
                        mTemplaterows = arrayOf()
                        populateList()
                    }
                }
            )
        }
    }

    internal class TemplateRowItem(obj: PropertyTemplate?) {
        var pt: PropertyTemplate? = obj
        var title: String? = null
        var rowType = RowType.DATA_ITEM

        constructor(name : String) : this(null) {
            rowType = RowType.HEADER_ITEM
            title = name
        }
    }

    private inner class TemplateAdapter(
        c: Context?,
        rgTemplates: Array<TemplateRowItem>?
    ) : ArrayAdapter<TemplateRowItem?>(
        c!!, R.layout.propertytemplateitem, rgTemplates ?: arrayOfNulls<TemplateRowItem>(0)
    ) {
        override fun getViewTypeCount(): Int {
            return 2
        }

        override fun getItemViewType(position: Int): Int {
            return if (mTemplaterows.isEmpty()) RowType.DATA_ITEM.ordinal else mTemplaterows[position].rowType.ordinal
        }

        override fun getView(position: Int, vIn: View?, parent: ViewGroup): View {
            var v = vIn
            val rt = RowType.values()[getItemViewType(position)]
            if (v == null) {
                val vi =
                    requireActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
                val layoutID =
                    if (rt == RowType.HEADER_ITEM) R.layout.listviewsectionheader else R.layout.propertytemplateitem
                v = vi.inflate(layoutID, parent, false)
            }
            if (mTemplaterows.isEmpty()) return v!!
            if (rt == RowType.HEADER_ITEM) {
                val tvSectionHeader = v!!.findViewById<TextView>(R.id.lblTableRowSectionHeader)
                tvSectionHeader.text = mTemplaterows[position].title
                return v
            }
            val pt = mTemplaterows[position].pt
            val tvName = v!!.findViewById<TextView>(R.id.txtTemplateName)
            tvName.text = pt!!.name
            val tvDescription = v.findViewById<TextView>(R.id.txtDescription)
            tvDescription.text = pt.description
            val ckIsActive = v.findViewById<CheckBox>(R.id.ckActiveTemplate)
            ckIsActive.isChecked =
                mActivetemplates!!.contains(mTemplaterows[position].pt)
            ckIsActive.setOnClickListener { this@ActViewTemplates.onItemClick(null, v, position, 0) }
            return v
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.selecttemplate, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val i = requireActivity().intent
        try {
            val b = i.extras!!
            mActivetemplates =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    b.getSerializable(ACTIVE_PROPERTYTEMPLATES, HashSet<PropertyTemplate?>()::class.java)
                else
                    @Suppress("UNCHECKED_CAST", "DEPRECATION")
                    b.getSerializable(ACTIVE_PROPERTYTEMPLATES) as HashSet<PropertyTemplate?>?
        } catch (ex: ClassCastException) {
            Log.e(MFBConstants.LOG_TAG, ex.message!!)
        }
        val srl: SwipeRefreshLayout = requireView().findViewById(R.id.swiperefresh)
        srl.setOnRefreshListener {
            srl.isRefreshing = false
            refreshPropertyTypes()
        }
        val rgpt = getSharedTemplates(
            requireActivity().getSharedPreferences(
                PropertyTemplate.PREF_KEY_TEMPLATES,
                Activity.MODE_PRIVATE
            )
        )
        if (rgpt == null || rgpt.isEmpty()) refreshPropertyTypes()
    }

    override fun onResume() {
        super.onResume()
        populateList()
    }

    private fun refreshRows(a: Context): Array<TemplateRowItem> {
        if (mTemplaterows.isEmpty()) {
            val al: ArrayList<TemplateRowItem> = ArrayList()
            val rgtg = groupTemplates(
                getSharedTemplates(
                    a.getSharedPreferences(
                        PropertyTemplate.PREF_KEY_TEMPLATES,
                        Activity.MODE_PRIVATE
                    )
                )
            )
            for (tg in rgtg) {
                al.add(TemplateRowItem(tg.groupDisplayName))
                for (pt in tg.templates) al.add(TemplateRowItem(pt))
            }
            mTemplaterows = al.toArray(arrayOfNulls<TemplateRowItem>(0))
        }
        return mTemplaterows
    }

    private fun populateList() {
        val a = requireActivity()
        val ta = TemplateAdapter(a, refreshRows(a))
        listAdapter = ta
        listView.onItemClickListener = this
    }

    override fun onItemClick(parent: AdapterView<*>?, view: View, position: Int, id: Long) {
        if (position < 0 || position >= mTemplaterows.size || mTemplaterows[position].rowType == RowType.HEADER_ITEM) return
        val pt = mTemplaterows[position].pt
        if (mActivetemplates!!.contains(pt)) mActivetemplates!!.remove(pt) else mActivetemplates!!.add(
            pt
        )
        val ck = view.findViewById<CheckBox>(R.id.ckActiveTemplate)
        ck.isChecked = mActivetemplates!!.contains(pt)
    }

    companion object {
        const val ACTIVE_PROPERTYTEMPLATES = "com.myflightbook.android.viewactivetemplates"
    }
}