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

import android.widget.BaseAdapter
import model.MFBImageInfo.ImageCacheCompleted

class LazyThumbnailLoader(
    private val m_rgItems: Array<ThumbnailedItem>?,
    private val m_li: BaseAdapter
) : Runnable, ImageCacheCompleted {
    interface ThumbnailedItem {
        val defaultImage: MFBImageInfo?
    }

    private val nextThumb: Unit
        get() {
            if (m_rgItems == null) return
            for (ti in m_rgItems) {
                val mfbii = ti.defaultImage
                if (mfbii != null) {
                    val b = mfbii.bitmapFromThumb()
                    if (b == null) {
                        mfbii.loadImageAsync(true, this)
                        return
                    }
                }
            }
        }

    override fun run() {
        nextThumb
    }

    override fun imgCompleted(sender: MFBImageInfo?) {
        m_li.notifyDataSetChanged()
        nextThumb
    }
}