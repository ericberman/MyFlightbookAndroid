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
package model

import android.widget.BaseAdapter
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import model.MFBImageInfo.ImageCacheCompleted

class LazyThumbnailLoader(
    private val mRgitems: Array<ThumbnailedItem>?,
    private val mLi: BaseAdapter,
    private val scope : LifecycleCoroutineScope
) : ImageCacheCompleted {
    interface ThumbnailedItem {
        val defaultImage: MFBImageInfo?
    }

    fun start() {
        if (mRgitems == null)
            return

        for (ti in mRgitems) {
            val delegate = this
            scope.launch(Dispatchers.IO) {
                val mfbii = ti.defaultImage
                if (mfbii != null) {
                    val b = mfbii.bitmapFromThumb()
                    if (b == null) {
                        mfbii.loadImageAsync(true, delegate)
                    }
                }
            }
        }
    }

    override fun imgCompleted(sender: MFBImageInfo?) {
        mLi.notifyDataSetChanged()
    }
}