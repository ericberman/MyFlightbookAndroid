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
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.ExpandableListAdapter
import android.widget.ExpandableListView
import model.CustomExceptionHandler

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

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        // Focus may already be set when the keyboard changes the available height.
        // Don't scroll synchronously from onSizeChanged: that runs inside layout(),
        // before layoutChildren, and scrolling there adds/detaches rows mid-pass,
        // which led to an NPE in AbsListView.RecycleBin.addScrapView. Defer instead.
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

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        return try {
            super.dispatchTouchEvent(ev)
        } catch (ex: NullPointerException) {
            // Framework bug: Editor's cursor-drag magnifier can NPE in Magnifier.getPosition() when the
            // EditText's window surface is invalid (e.g., row mid-relayout during an IME resize).
            // Swallow only that specific failure; rethrow anything else.
            if (ex.stackTrace.any { it.className == "android.widget.Magnifier" }) true else throw ex
        }
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(revealFocusedProperty)
        removeCallbacks(rebuildList)
        super.onDetachedFromWindow()
    }

    //region Recovery + diagnostics for issue #350
    /*
     * ListView.layoutChildren() counts its rows, does some focus bookkeeping, then walks that many
     * rows. Since at least build 197, something occasionally removes a row in between, and ListView
     * crashes with an NPE in AbsListView.RecycleBin (addScrapView or fillActiveViews). The traces
     * don't show what removed it, so:
     *  - While layoutChildren() runs, record the stack of the first row removal/detach (and any
     *    removal at all from a background thread). If the NPE then happens, that stack shows the
     *    culprit, because ListView's own removals come after the point where it crashes.
     *  - On that specific NPE, don't crash: rebuild the list (keeping expanded groups and scroll
     *    position) and save a non-fatal report with the recorded stack. It's emailed with the
     *    crash reports the next time the app starts.
     */
    @Volatile private var inLayoutChildren = false
    @Volatile private var removalTrace: Throwable? = null
    @Volatile private var offThreadTrace: Throwable? = null
    private var childCountAtLayoutStart = 0
    private var recoveryCount = 0

    private fun noteRemoval(what: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            // Views must never be touched off the main thread; keep the first time it happens.
            if (offThreadTrace == null)
                offThreadTrace = Throwable("$what (childCount now $childCount) on background thread ${Thread.currentThread().name}")
        } else if (inLayoutChildren && removalTrace == null) {
            removalTrace = Throwable("$what (childCount now $childCount) during layoutChildren")
        }
    }

    override fun onViewRemoved(child: View?) {
        noteRemoval("Row removed")
        super.onViewRemoved(child)
    }

    override fun detachViewFromParent(child: View?) {
        noteRemoval("Row detached")
        super.detachViewFromParent(child)
    }

    override fun detachViewFromParent(index: Int) {
        noteRemoval("Row $index detached")
        super.detachViewFromParent(index)
    }

    override fun detachViewsFromParent(start: Int, count: Int) {
        noteRemoval("Rows $start..${start + count - 1} detached")
        super.detachViewsFromParent(start, count)
    }

    override fun detachAllViewsFromParent() {
        noteRemoval("All rows detached")
        super.detachAllViewsFromParent()
    }

    override fun layoutChildren() {
        childCountAtLayoutStart = childCount
        removalTrace = null
        inLayoutChildren = true
        try {
            super.layoutChildren()
        } catch (ex: NullPointerException) {
            // Only the known RecycleBin failure, and give up after a few tries rather than loop forever.
            if (recoveryCount >= MAX_RECOVERIES ||
                ex.stackTrace.none { it.className == "android.widget.AbsListView\$RecycleBin" }) throw ex
            recoveryCount++
            recoverFromBrokenLayout(ex)
        } finally {
            inLayoutChildren = false
        }
    }

    private fun recoverFromBrokenLayout(ex: NullPointerException) {
        val focused = findFocus()
        val report = IllegalStateException(
            "PropertyListView recovered from ListView layout NPE (#$recoveryCount). " +
                    "Rows at layout start: $childCountAtLayoutStart, now: $childCount; " +
                    "groups: ${expandableListAdapter?.groupCount}; " +
                    "focused: ${focused?.javaClass?.name}; " +
                    "first removal recorded during layout: ${removalTrace?.message ?: "none"}; " +
                    "background-thread removal: ${offThreadTrace?.message ?: "none"}",
            ex
        )
        removalTrace?.let { report.addSuppressed(it) }
        offThreadTrace?.let { report.addSuppressed(it) }
        CustomExceptionHandler.reportNonFatal(context, report)

        // ListView had temporarily detached the focused editor before it crashed; undo that.
        if (focused != null && focused.isTemporarilyDetached) focused.dispatchFinishTemporaryDetach()

        // The list's internal recycling state is now unreliable. Rebuild it after this layout pass.
        removeCallbacks(rebuildList)
        post(rebuildList)
    }

    private val rebuildList = Runnable {
        val adapter: ExpandableListAdapter = expandableListAdapter ?: return@Runnable
        val expanded = (0 until adapter.groupCount).filter { isGroupExpanded(it) }
        val firstPosition = firstVisiblePosition
        val firstTop = (getChildAt(0)?.top ?: listPaddingTop) - listPaddingTop
        // setAdapter() clears all rows and ListView's recycler, then starts fresh.
        setAdapter(adapter)
        for (group in expanded) expandGroup(group)
        setSelectionFromTop(firstPosition, firstTop)
    }

    companion object {
        private const val MAX_RECOVERIES = 3
    }
    //endregion
}
