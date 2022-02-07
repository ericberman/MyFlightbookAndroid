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

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class ViewTemplatesActivity : AppCompatActivity() {
    private var mViewtemplates: ActViewTemplates? = null
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragmenthost)
        val fragmentManager = supportFragmentManager
        val fragmentTransaction = fragmentManager
            .beginTransaction()
        mViewtemplates = ActViewTemplates()
        fragmentTransaction.replace(android.R.id.content, mViewtemplates!!)
        fragmentTransaction.commitAllowingStateLoss()
    }

    override fun onBackPressed() {
        val mIntent = Intent()
        val b = Bundle()
        b.putSerializable(
            ActViewTemplates.ACTIVE_PROPERTYTEMPLATES,
            mViewtemplates!!.mActivetemplates
        )
        mIntent.putExtras(b)
        setResult(RESULT_OK, mIntent)
        finish()
        super.onBackPressed()
    }
}