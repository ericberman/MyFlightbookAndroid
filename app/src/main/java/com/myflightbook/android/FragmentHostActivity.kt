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

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment

class FragmentHostActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_FRAGMENT_CLASS = "fragment_class"
        const val EXTRA_FRAGMENT_ARGS = "fragment_args"

        inline fun <reified T : Fragment> createIntent(context: Context, args: Bundle? = null): Intent {
            return Intent(context, FragmentHostActivity::class.java).apply {
                putExtra(EXTRA_FRAGMENT_CLASS, T::class.java.name)
                putExtra(EXTRA_FRAGMENT_ARGS, args)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Set early to avoid edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, true)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragmenthost2) // <- your FrameLayout container

        if (savedInstanceState != null) return

        val className = intent.getStringExtra(EXTRA_FRAGMENT_CLASS)
        val args = intent.getBundleExtra(EXTRA_FRAGMENT_ARGS)

        if (className != null) {
            val fragment = supportFragmentManager.fragmentFactory.instantiate(
                classLoader,
                className
            ).apply {
                arguments = args
            }
            // fragment.arguments = args

            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit()
        }
    }
}