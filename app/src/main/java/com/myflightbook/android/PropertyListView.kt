/*
    MyFlightbook for Android - provides native access to MyFlightbook
    pilot's logbook
    Copyright (C) 2026 MyFlightbook, LLC

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
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import android.widget.ExpandableListView

/** Keeps the label and editor together when a property requests visibility. */
class PropertyListView(context: Context, attrs: AttributeSet?) : ExpandableListView(context, attrs) {
    private val revealFocusedProperty = Runnable {
        val row = focusedChild as? PropertyEdit ?: return@Runnable
        val editor = row.findFocus() ?: return@Runnable
        if (height <= listPaddingTop + listPaddingBottom) return@Runnable
        // The override below expands this to the whole row when it fits; otherwise
        // keep the editor's focus rectangle visible in a small window.
        val rectangle = Rect()
        editor.getFocusedRect(rectangle)
        row.offsetDescendantRectToMyCoords(editor, rectangle)
        requestChildRectangleOnScreen(row, rectangle, true)
    }

    init {
        // Preserve the focused editor when ListView lays out its children after a resize.
        itemsCanFocus = true
    }

    override fun requestChildFocus(child: View, focused: View) {
        super.requestChildFocus(child, focused)
        scheduleReveal()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        // The new bounds are already set, but the old children are still attached.
        // Move the focused row into the smaller viewport before ListView lays out
        // and recycles children. Its deferred focus restoration is too late if the
        // editor has already left the visible rows and disconnected from the IME.
        if (h < oldh) revealFocusedProperty.run()
        super.onSizeChanged(w, h, oldw, oldh)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        // Focus may already be set when the keyboard changes the available height.
        if (changed) scheduleReveal()
    }

    override fun requestChildRectangleOnScreen(child: View, rectangle: Rect, immediate: Boolean): Boolean {
        val target = if (child is PropertyEdit &&
            child.height <= height - listPaddingTop - listPaddingBottom) {
            Rect(0, 0, child.width, child.height)
        } else {
            rectangle
        }
        // ListView scrolls only the distance needed, in either direction. Use its
        // original three-argument API, which is supported on every app API level.
        return super.requestChildRectangleOnScreen(child, target, immediate)
    }

    private fun scheduleReveal() {
        // Wait until focus/resize layout has finished, and coalesce repeated requests.
        removeCallbacks(revealFocusedProperty)
        post(revealFocusedProperty)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(revealFocusedProperty)
        super.onDetachedFromWindow()
    }
}
