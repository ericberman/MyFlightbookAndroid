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
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.AsyncTask
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.view.*
import android.view.ContextMenu.ContextMenuInfo
import android.widget.*
import android.widget.AdapterView.OnItemClickListener
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import com.myflightbook.android.ActMFBForm.GallerySource
import com.myflightbook.android.webservices.AircraftSvc
import com.myflightbook.android.webservices.AuthToken
import com.myflightbook.android.webservices.AuthToken.Companion.isValid
import com.myflightbook.android.webservices.MFBSoap
import com.myflightbook.android.webservices.MFBSoap.MFBSoapProgressUpdate
import com.myflightbook.android.webservices.MakesandModelsSvc
import model.Aircraft
import model.CountryCode.Companion.bestGuessForCurrentLocale
import model.MFBConstants
import model.MFBImageInfo
import model.MFBImageInfo.Companion.getLocalImagesForId
import model.MFBImageInfo.PictureDestination
import model.MFBUtil.alert
import model.MFBUtil.showProgress
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
        private val mMatchingAircraft: MutableList<Aircraft>
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
                ).also { mm = it!! } == null
            ) tv.text = ac.modelDescription else tv.text = mm!!.description
            return v
        }

        init {
            mMatchingAircraft = ArrayList()
        }
    }

    private class SuggestAircraftTask(
        c: Context?,
        val mPrefix: String,
        ana: ActNewAircraft
    ) : AsyncTask<Void?, Void?, MFBSoap>() {
        var mResult: Any? = null
        val mCtxt: AsyncWeakContext<ActNewAircraft> = AsyncWeakContext(c, ana)
        override fun doInBackground(vararg params: Void?): MFBSoap {
            val `as` = AircraftSvc()
            mResult = `as`.aircraftForPrefix(AuthToken.m_szAuthToken!!, mPrefix, mCtxt.context!!)
            return `as`
        }

        override fun onPostExecute(svc: MFBSoap) {
            val aa = mCtxt.callingActivity
            if (aa == null || !aa.isAdded || aa.isDetached || aa.activity == null || aa.autoCompleteAdapter == null) return
            var rgac = mResult as Array<Aircraft>?
            if (rgac == null) rgac = arrayOf()
            val lst = ArrayList(listOf(*rgac))
            aa.autoCompleteAdapter!!.setData(lst)
            aa.autoCompleteAdapter!!.notifyDataSetChanged()
        }

    }

    private class SaveAircraftTask(
        c: Context?,
        private val mAc: Aircraft?,
        ana: ActNewAircraft
    ) : AsyncTask<Aircraft?, String?, MFBSoap>(), MFBSoapProgressUpdate {
        private var mPd: ProgressDialog? = null
        var mResult: Any? = null
        private val mCtxt: AsyncWeakContext<ActNewAircraft> = AsyncWeakContext(c, ana)
        override fun doInBackground(vararg params: Aircraft?): MFBSoap {
            val acs = AircraftSvc()
            acs.mProgress = this
            mResult = acs.addAircraft(AuthToken.m_szAuthToken!!, mAc!!, mCtxt.context!!)
            return acs
        }

        override fun onPreExecute() {
            mPd = showProgress(mCtxt.context, mCtxt.context!!.getString(R.string.prgNewAircraft))
        }

        override fun onPostExecute(svc: MFBSoap) {
            try {
                if (mPd != null) mPd!!.dismiss()
            } catch (e: Exception) {
                Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e))
            }
            val ana = mCtxt.callingActivity
            if (ana == null || !ana.isAdded || ana.isDetached || ana.activity == null) return
            val rgac = mResult as Array<Aircraft>?
            if (rgac == null || rgac.isEmpty()) alert(
                ana,
                ana.getString(R.string.txtError),
                svc.lastError
            ) else ana.dismiss()
        }

        override fun onProgressUpdate(vararg msg: String?) {
            mPd!!.setMessage(msg[0])
        }

        override fun notifyProgress(percentageComplete: Int, szMsg: String?) {
            publishProgress(szMsg)
        }

    }

    private class GetMakesTask(c: Context?, ana: ActNewAircraft) :
        AsyncTask<Void?, Void?, MFBSoap>() {
        private var mPd: ProgressDialog? = null
        private val mCtxt: AsyncWeakContext<ActNewAircraft> = AsyncWeakContext(c, ana)
        override fun doInBackground(vararg params: Void?): MFBSoap {
            val mms = MakesandModelsSvc()
            AvailableMakesAndModels = mms.getMakesAndModels(mCtxt.context)
            return mms
        }

        override fun onPreExecute() {
            mPd = showProgress(mCtxt.context, mCtxt.context!!.getString(R.string.prgMakes))
        }

        override fun onPostExecute(svc: MFBSoap) {
            try {
                if (mPd != null) mPd!!.dismiss()
            } catch (e: Exception) {
                Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e))
            }
            val ana = mCtxt.callingActivity
            val c = mCtxt.context
            if (ana == null || c == null) return
            if (AvailableMakesAndModels == null || AvailableMakesAndModels!!.isEmpty()) {
                alert(
                    c,
                    c.getString(R.string.txtError),
                    c.getString(R.string.errCannotRetrieveMakes)
                )
                ana.cancel()
            }
        }

    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        setHasOptionsMenu(true)
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
        val ana = this
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
                    if (szTail.length > 2) {
                        val sat = SuggestAircraftTask(context, szTail, ana)
                        sat.execute()
                    }
                }
            }

            override fun afterTextChanged(s: Editable) {}
        })

        // Get available makes/models, but only if we have none.  Can refresh.
        // This avoids getting makes/models when just getting a picture.
        if (AvailableMakesAndModels == null || AvailableMakesAndModels!!.isEmpty()) {
            val gt = GetMakesTask(this.activity, this)
            gt.execute()
        }
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

    private fun newAircraft() {
        mAc1 = Aircraft()

        // Clean up any pending image turds that could be lying around
        val mfbii = MFBImageInfo(PictureDestination.AircraftImage)
        mfbii.deletePendingImages(mAc1!!.aircraftID.toLong())

        // Give the aircraft a tailnumber based on locale
        mAc1!!.tailNumber = bestGuessForCurrentLocale().prefix!!
        toView()
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
    override fun chooseImageCompleted(result: ActivityResult?) {
        addGalleryImage(result!!.data!!)
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
        arg0: AdapterView<*>?, arg1: View, arg2: Int,
        arg3: Long
    ) {
        saveLastTail()
        val sp = findViewById(R.id.spnAircraftType) as Spinner?
        mAc1!!.instanceTypeID = sp!!.selectedItemPosition + 1
        toView()
    }

    override fun onNothingSelected(arg0: AdapterView<*>?) {}

    // @Override
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.newaircraft, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle item selection
        val id = item.itemId
        if (id == R.id.menuChoosePicture) choosePicture() else if (id == R.id.menuTakePicture) takePicture() else if (id == R.id.menuAddAircraft) {
            fromView()
            if (mAc1!!.isValid(requireContext())) {
                val st = SaveAircraftTask(activity, mAc1, this)
                st.execute(mAc1)
            } else alert(this, getString(R.string.txtError), mAc1!!.errorString)
        } else return super.onOptionsItemSelected(item)
        return true
    }

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