/*
	MyFlightbook for Android - provides native access to MyFlightbook
	pilot's logbook
    Copyright (C) 2017-2025 MyFlightbook, LLC

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
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.*
import android.view.ContextMenu.ContextMenuInfo
import android.widget.*
import android.widget.AdapterView.OnItemClickListener
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.myflightbook.android.ActMFBForm.GallerySource
import com.myflightbook.android.webservices.AircraftSvc
import com.myflightbook.android.webservices.AuthToken
import com.myflightbook.android.webservices.AuthToken.Companion.isValid
import com.myflightbook.android.webservices.MakesandModelsSvc
import kotlinx.coroutines.launch
import model.Aircraft
import model.CountryCode.Companion.bestGuessForCurrentLocale
import model.MFBImageInfo
import model.MFBImageInfo.Companion.getLocalImagesForId
import model.MFBImageInfo.PictureDestination
import model.MFBUtil.alert
import model.MakesandModels
import model.MakesandModels.Companion.getMakeModelByID
import java.util.*

class ActNewAircraft : ActMFBForm(), View.OnClickListener, AdapterView.OnItemSelectedListener,
    GallerySource {
    private var mAc1: Aircraft? = Aircraft()
    private var szTailLast = ""
    private var autoCompleteAdapter: AutoCompleteAdapter? = null
    private var fNoTrigger = false // true to suppress autosuggestions
    private var mSelectMakeLauncher: ActivityResultLauncher<Intent>? = null

    internal class AutoCompleteAdapter(context: Context, resource: Int) :
        ArrayAdapter<Aircraft?>(context, resource) {
        private val mMatchingAircraft: MutableList<Aircraft> = ArrayList()
        fun setData(list: List<Aircraft>?) {
            mMatchingAircraft.clear()
            mMatchingAircraft.addAll(list!!)
        }

        override fun getCount(): Int {
            return mMatchingAircraft.size
        }

        override fun getItem(position: Int): Aircraft {
            return mMatchingAircraft[position]
        }

        fun getObject(position: Int): Aircraft {
            return mMatchingAircraft[position]
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val v: View = if (convertView == null) {
                val inflater = (context
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater)
                inflater.inflate(android.R.layout.simple_list_item_2, null)
            } else convertView
            val ac = getObject(position)
            var tv = v.findViewById<TextView>(android.R.id.text1)
            tv.text = ac.displayTailNumber()
            tv.setTypeface(tv.typeface, Typeface.BOLD)
            tv = v.findViewById(android.R.id.text2)
            var mm: MakesandModels? = null
            if (AvailableMakesAndModels == null || getMakeModelByID(
                    ac.modelID,
                    AvailableMakesAndModels
                ).also { mm = it } == null
            ) tv.text = ac.modelDescription else if (mm != null) tv.text = mm.description
            return v
        }
    }

    private fun suggestAircraft(szPrefix : String) {
        lifecycleScope.launch {
        doAsync<AircraftSvc, Array<Aircraft>?>(
            requireActivity(),
            AircraftSvc(),
            null,
            { s -> s.aircraftForPrefix(AuthToken.m_szAuthToken!!, szPrefix, requireContext()) },
            { _, result ->
                if (result != null) {
                    val lst = ArrayList(listOf(*result))
                    autoCompleteAdapter!!.setData(lst)
                    autoCompleteAdapter!!.notifyDataSetChanged()
                }
            }
        )
        }
    }

    private fun getMakes() {
        lifecycleScope.launch {
            doAsync<MakesandModelsSvc, Array<MakesandModels>?>(
                requireActivity(),
                MakesandModelsSvc(),
                getString(R.string.prgMakes),
                { s-> s.getMakesAndModels(requireContext())},
                { _, result -> AvailableMakesAndModels = result }
            )
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.newaircraft, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!isValid()) {
            alert(
                this,
                getString(R.string.errCannotAddAircraft),
                getString(R.string.errMustBeSignedInToCreateAircraft)
            )
            cancel()
            return
        }

        val menuHost: MenuHost = requireActivity()

        // Add menu items without using the Fragment Menu APIs
        // Note how we can tie the MenuProvider to the viewLifecycleOwner
        // and an optional Lifecycle.State (here, RESUMED) to indicate when
        // the menu should be visible
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
                inflater.inflate(R.menu.newaircraft, menu)
            }

            override fun onMenuItemSelected(item: MenuItem): Boolean {
                // Handle item selection
                val id = item.itemId
                if (id == R.id.menuChoosePicture) choosePicture() else if (id == R.id.menuTakePicture) takePicture() else if (id == R.id.menuAddAircraft) {
                    fromView()
                    val ac = mAc1!!

                    ac.errorString = ""
                    if (!ac.isValid(requireContext()))
                        alert(requireContext(), getString(R.string.txtError), ac.errorString)
                    else {
                        lifecycleScope.launch {
                            doAsync<AircraftSvc, Array<Aircraft>?>(
                                requireActivity(),
                                AircraftSvc(),
                                getString(R.string.prgNewAircraft),
                                { s ->
                                    s.addAircraft(AuthToken.m_szAuthToken!!, ac, requireContext())
                                },
                                { svc, _ ->
                                    if (svc.lastError.isNotEmpty())
                                        alert(requireContext(), getString(R.string.txtError), svc.lastError)
                                    else
                                        dismiss()
                                }
                            )
                        }
                    }
                } else return false
                return true
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)

        mSelectMakeLauncher = registerForActivityResult(
            StartActivityForResult()
        ) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                val selectedMakeIndex = result.data!!.getIntExtra(
                    MODELFORAIRCRAFT, 0
                )
                if (AvailableMakesAndModels != null && AvailableMakesAndModels!!.size > selectedMakeIndex) setCurrentMakeModel(
                    AvailableMakesAndModels!![selectedMakeIndex]
                )
            }
        }

        // Give the aircraft a tailnumber based on locale
        mAc1!!.tailNumber = bestGuessForCurrentLocale().prefix!!
        findViewById(R.id.btnMakeModel)!!.setOnClickListener(this)
        findViewById(R.id.ckAnonymous)!!.setOnClickListener(this)
        val rgszInstanceTypes : ArrayList<String> = ArrayList()
        for (i in 0 until Aircraft.rgidInstanceTypes.count())
            rgszInstanceTypes.add(getString(Aircraft.rgidInstanceTypes[i]))

        val sp = findViewById(R.id.spnAircraftType) as Spinner?
        val adapter = ArrayAdapter(requireActivity(), R.layout.mfbsimpletextitem, rgszInstanceTypes)
        sp!!.adapter = adapter
        sp.setSelection(0)
        sp.onItemSelectedListener = this

        // Autocompletion based on code at https://www.truiton.com/2018/06/android-autocompletetextview-suggestions-from-webservice-call/
        val act = findViewById(R.id.txtTail) as AutoCompleteTextView?
        act!!.inputType = InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_CLASS_TEXT
        act.threshold = 3
        autoCompleteAdapter =
            AutoCompleteAdapter(requireContext(), android.R.layout.simple_list_item_2)
        val aca = autoCompleteAdapter!!
        act.setAdapter(autoCompleteAdapter)
        act.onItemClickListener =
            OnItemClickListener { _: AdapterView<*>?, _: View?, position: Int, _: Long ->
                mAc1 = aca.getObject(position)
                setCurrentMakeModel(getMakeModelByID(mAc1!!.modelID, AvailableMakesAndModels))
                toView()
            }
        act.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                autoCompleteAdapter!!.notifyDataSetChanged()
                if (!fNoTrigger) {
                    val szTail = act.text.toString()
                    if (szTail.length > 2)
                        suggestAircraft(szTail)
                }
            }

            override fun afterTextChanged(s: Editable) {}
        })

        // Get available makes/models, but only if we have none.  Can refresh.
        // This avoids getting makes/models when just getting a picture.
        if (AvailableMakesAndModels == null || AvailableMakesAndModels!!.isEmpty())
            getMakes()
        toView()
    }

    override fun onPause() {
        super.onPause()
        fromView()
    }

    override fun onResume() {
        super.onResume()
        toView()
    }

    private fun cancel() {
        val i = Intent()
        requireActivity().setResult(Activity.RESULT_CANCELED, i)
        requireActivity().finish()
    }

    private fun dismiss() {
        val i = Intent()
        val a: Activity? = activity
        if (a != null) {
            a.setResult(Activity.RESULT_OK, i)
            a.finish()
        }
    }

    //region Image support
    override fun chooseImageCompleted(data : Uri?) {
        addGalleryImage(data)
    }

    override fun takePictureCompleted(result: ActivityResult?) {
        addCameraImage(mTempfilepath, false)
    }

    //endregion
    private fun setCurrentMakeModel(mm: MakesandModels?) {
        if (mm != null) {
            mAc1!!.modelID = mm.modelId
            val b = findViewById(R.id.btnMakeModel) as Button?
            b!!.text = mm.description
        }
    }

    private fun fromView() {
        mAc1!!.tailNumber =
            (findViewById(R.id.txtTail) as AutoCompleteTextView?)!!.text.toString().uppercase(
                Locale.getDefault()
            )
        mAc1!!.instanceTypeID =
            (findViewById(R.id.spnAircraftType) as Spinner?)!!.selectedItemPosition + 1
        if (mAc1!!.instanceTypeID > 1) // not a real aircraft - auto-assign a tail
            mAc1!!.tailNumber = "SIM"
    }

    private fun toView() {
        val et = findViewById(R.id.txtTail) as AutoCompleteTextView?
        // don't trigger autosuggest if the user isn't typing.
        fNoTrigger = true
        et!!.setText(mAc1!!.tailNumber)
        fNoTrigger = false
        et.setSelection(mAc1!!.tailNumber.length)
        (findViewById(R.id.spnAircraftType) as Spinner?)!!.setSelection(mAc1!!.instanceTypeID - 1)
        setCurrentMakeModel(getMakeModelByID(mAc1!!.modelID, AvailableMakesAndModels))
        findViewById(R.id.tblrowTailnumber)!!.visibility =
            if (mAc1!!.isAnonymous() || !mAc1!!.isReal()) View.GONE else View.VISIBLE
        findViewById(R.id.tblrowIsAnonymous)!!.visibility =
            if (mAc1!!.isReal()) View.VISIBLE else View.GONE
        refreshGallery()
    }

    private fun saveLastTail() {
        if (mAc1!!.isReal() && !mAc1!!.isAnonymous()) szTailLast =
            (findViewById(R.id.txtTail) as AutoCompleteTextView?)!!.text.toString().uppercase(
                Locale.getDefault()
            )
    }

    private fun toggleAnonymous(sender: CheckBox) {
        saveLastTail()
        mAc1!!.tailNumber = if (mAc1!!.isAnonymous()) szTailLast else mAc1!!.anonTailNumber()
        sender.isChecked = mAc1!!.isAnonymous()
        toView()
    }

    override fun onClick(v: View) {
        val id = v.id
        if (id == R.id.btnMakeModel) {
            val i = Intent(activity, ActSelectMake::class.java)
            i.putExtra(MODELFORAIRCRAFT, mAc1!!.modelID)
            mSelectMakeLauncher!!.launch(i)
        } else if (id == R.id.ckAnonymous) toggleAnonymous(v as CheckBox)
    }

    override fun onItemSelected(
        arg0: AdapterView<*>?, arg1: View?, arg2: Int,
        arg3: Long
    ) {
        saveLastTail()
        val sp = findViewById(R.id.spnAircraftType) as Spinner?
        mAc1!!.instanceTypeID = sp!!.selectedItemPosition + 1
        toView()
    }

    override fun onNothingSelected(arg0: AdapterView<*>?) {}

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)
        val inflater = requireActivity().menuInflater
        inflater.inflate(R.menu.imagemenu, menu)
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        return if (id == R.id.menuAddComment || id == R.id.menuDeleteImage || id == R.id.menuViewImage) onImageContextItemSelected(
            item,
            this
        ) else true
    }

    /*
     * GallerySource methods
     * (non-Javadoc)
     * @see com.myflightbook.android.ActMFBForm.GallerySource#getGalleryID()
     */
    override fun getGalleryID(): Int {
        return R.id.tblImageTable
    }

    override fun getGalleryHeader(): View? {
        return null
    }

    override fun getImages(): Array<MFBImageInfo> {
        return if (mAc1 == null || mAc1!!.aircraftImages == null) arrayOf() else mAc1!!.aircraftImages!!
    }

    override fun setImages(rgmfbii: Array<MFBImageInfo>?) {
        mAc1!!.aircraftImages = rgmfbii
    }

    override fun newImage(mfbii: MFBImageInfo?) {
        mfbii!!.setPictureDestination(PictureDestination.AircraftImage)
        mfbii.targetID = mAc1!!.aircraftID.toLong()
        mfbii.toDB()
        mAc1!!.aircraftImages =
            getLocalImagesForId(mAc1!!.aircraftID.toLong(), PictureDestination.AircraftImage)
    }

    override fun refreshGallery() {
        setUpImageGallery(getGalleryID(), getImages(), getGalleryHeader())
    }

    override fun getPictureDestination(): PictureDestination {
        return PictureDestination.AircraftImage
    }

    companion object {
        @JvmField
        var AvailableMakesAndModels: Array<MakesandModels>? = null
        const val MODELFORAIRCRAFT = "com.myflightbook.android.aircraftModelID"
    }
}