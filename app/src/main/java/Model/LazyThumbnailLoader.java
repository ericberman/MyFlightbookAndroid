/*
	MyFlightbook for Android - provides native access to MyFlightbook
	pilot's logbook
    Copyright (C) 2017 MyFlightbook, LLC

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
package Model;

import android.graphics.Bitmap;
import android.widget.ArrayAdapter;

import Model.MFBImageInfo.ImageCacheCompleted;

public class LazyThumbnailLoader implements Runnable, ImageCacheCompleted {
    private ThumbnailedItem[] m_rgItems;

    private ArrayAdapter m_li;

    public interface ThumbnailedItem {
        MFBImageInfo getDefaultImage();
    }

    public LazyThumbnailLoader(ThumbnailedItem[] rgItems, ArrayAdapter li) {
        m_rgItems = rgItems;
        m_li = li;
    }

    private void getNextThumb() {
        for (ThumbnailedItem ti : m_rgItems) {
            MFBImageInfo mfbii = ti.getDefaultImage();
            if (mfbii != null) {
                Bitmap b = mfbii.bitmapFromThumb();
                if (b == null) {
                    mfbii.LoadImageAsync(true, this);
                    return;
                }
            }
        }
    }

    public void run() {
        getNextThumb();
    }

    public void imgCompleted(MFBImageInfo sender) {
        m_li.notifyDataSetChanged();
        getNextThumb();
    }
}
