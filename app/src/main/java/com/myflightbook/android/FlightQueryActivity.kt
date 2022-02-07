/*
	MyFlightbook for Android - provides native access to MyFlightbook
	pilot's logbook
    Copyright (C) 2017-2020 MyFlightbook, LLC

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

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.content.Intent

class FlightQueryActivity : AppCompatActivity() {
    private var mFlightquery: ActFlightQuery? = null
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragmenthost)
        val fragmentManager = supportFragmentManager
        val fragmentTransaction = fragmentManager.beginTransaction()
        mFlightquery = ActFlightQuery()
        fragmentTransaction.replace(android.R.id.content, mFlightquery!!)
        fragmentTransaction.commitAllowingStateLoss()
    }

    override fun onBackPressed() {
        val mIntent = Intent()
        mIntent.putExtra(ActFlightQuery.QUERY_TO_EDIT, mFlightquery!!.getCurrentQuery())
        setResult(RESULT_OK, mIntent)
        finish()
        super.onBackPressed()
    }
}