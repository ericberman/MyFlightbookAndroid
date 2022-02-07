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
package model

import org.ksoap2.serialization.KvmSerializable
import com.myflightbook.android.MFBMain
import com.myflightbook.android.R
import org.ksoap2.serialization.SoapObject
import org.ksoap2.serialization.PropertyInfo
import java.io.Serializable
import java.util.*

class CategoryClass : SoapableObject, KvmSerializable, Serializable {
    enum class CatClassID {
        None, ASEL, AMEL, ASES, AMES, Glider, Helicopter, Gyroplane, PoweredLift, Airship, HotAirBalloon, GasBalloon, PoweredParachuteLand, PoweredParachuteSea, WeightShiftControlLand, WeightShiftControlSea, UnmannedAerialSystem, PoweredParaglider
    }

    private var mCatClass: String? = null
    private var mCategory: String? = null
    private var mClass: String? = null
    private var mAltCatClass = 0
    var idCatClass: CatClassID? = null

    enum class CatClassProp {
        PIDCatClass, PIDCategory, PIDClass, PIDAltCatClass, PIDCatClassId
    }

    private fun init(ccid: CatClassID) {
        idCatClass = ccid
        mCatClass = localizedString()
        mClass = ""
        mCategory = mClass
        mAltCatClass = 0
    }

    internal constructor() : super() {
        init(CatClassID.None)
    }

    private constructor(ccid: CatClassID) : super() {
        init(ccid)
    }

    private fun localizedString(): String {
        val r = MFBMain.appResources!!
        return when (idCatClass) {
            CatClassID.None -> "(none)"
            CatClassID.ASEL -> r.getString(R.string.ccASEL)
            CatClassID.AMEL -> r.getString(R.string.ccAMEL)
            CatClassID.ASES -> r.getString(R.string.ccASES)
            CatClassID.AMES -> r.getString(R.string.ccAMES)
            CatClassID.Glider -> r.getString(R.string.ccGlider)
            CatClassID.Helicopter -> r.getString(R.string.ccHelicopter)
            CatClassID.Gyroplane -> r.getString(R.string.ccGyroplane)
            CatClassID.PoweredLift -> r.getString(R.string.ccPoweredLift)
            CatClassID.Airship -> r.getString(R.string.ccAirship)
            CatClassID.HotAirBalloon -> r.getString(R.string.ccHotAirBalloon)
            CatClassID.GasBalloon -> r.getString(R.string.ccGasBalloon)
            CatClassID.PoweredParachuteLand -> r.getString(R.string.ccPoweredParachuteLand)
            CatClassID.PoweredParachuteSea -> r.getString(R.string.ccPoweredParachuteSea)
            CatClassID.WeightShiftControlLand -> r.getString(R.string.ccWeightShiftControlLand)
            CatClassID.WeightShiftControlSea -> r.getString(R.string.ccWeightShiftControlSea)
            CatClassID.UnmannedAerialSystem -> r.getString(R.string.ccUAS)
            CatClassID.PoweredParaglider -> r.getString(R.string.ccPoweredParaglider)
            else -> "(none)"
        }
    }

    override fun toString(): String {
        return localizedString()
    }

    override fun getPropertyCount(): Int {
        return CatClassProp.values().size
    }

    override fun getProperty(arg0: Int): Any {
        return when (CatClassProp.values()[arg0]) {
            CatClassProp.PIDCatClass -> mCatClass!!
            CatClassProp.PIDCategory -> mCategory!!
            CatClassProp.PIDClass -> mClass!!
            CatClassProp.PIDAltCatClass -> mAltCatClass
            CatClassProp.PIDCatClassId -> idCatClass.toString()
        }
    }

    override fun getPropertyInfo(i: Int, arg1: Hashtable<*, *>?, pi: PropertyInfo) {
        when (CatClassProp.values()[i]) {
            CatClassProp.PIDCatClass -> {
                pi.type = PropertyInfo.STRING_CLASS
                pi.name = "CatClass"
            }
            CatClassProp.PIDCategory -> {
                pi.type = PropertyInfo.STRING_CLASS
                pi.name = "Category"
            }
            CatClassProp.PIDClass -> {
                pi.type = PropertyInfo.STRING_CLASS
                pi.name = "Class"
            }
            CatClassProp.PIDAltCatClass -> {
                pi.type = PropertyInfo.INTEGER_CLASS
                pi.name = "AltCatClass"
            }
            CatClassProp.PIDCatClassId -> {
                pi.type = PropertyInfo.STRING_CLASS
                pi.name = "IdCatClass"
            }
        }
    }

    override fun setProperty(i: Int, value: Any) {
        val ccp = CatClassProp.values()[i]
        val sz = value.toString()
        when (ccp) {
            CatClassProp.PIDCatClass -> mCatClass = sz
            CatClassProp.PIDCategory -> mCategory = sz
            CatClassProp.PIDClass -> mClass = sz
            CatClassProp.PIDAltCatClass -> mAltCatClass = sz.toInt()
            CatClassProp.PIDCatClassId -> idCatClass = CatClassID.valueOf(sz)
        }
    }

    override fun toProperties(so: SoapObject) {
        so.addProperty("CatClass", mCatClass)
        so.addProperty("Category", mCategory)
        so.addProperty("Class", mClass)
        so.addProperty("AltCatClass", mAltCatClass)
        so.addProperty("IdCatClass", idCatClass.toString())
    }

    public override fun fromProperties(so: SoapObject) {
        mCatClass = so.getPropertyAsString("CatClass")
        mCategory = so.getPropertyAsString("Category")
        mClass = so.getPropertyAsString("Class")
        mAltCatClass = so.getPropertyAsString("AltCatClass").toInt()
        idCatClass = CatClassID.valueOf(so.getPropertyAsString("IdCatClass"))
    }

    companion object {
        private const val serialVersionUID = 2L
        fun getAllCatClasses(): Array<CategoryClass> {
            val lst = ArrayList<CategoryClass>()
            for (ccid in CatClassID.values()) if (ccid != CatClassID.None) lst.add(
                CategoryClass(
                    ccid
                )
            )
            return lst.toTypedArray()
        }
    }
}