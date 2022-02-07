package com.myflightbook.android

import android.os.Bundle
import android.view.*
import android.view.ContextMenu.ContextMenuInfo
import android.view.View.OnCreateContextMenuListener
import android.widget.ExpandableListAdapter
import android.widget.ExpandableListView
import android.widget.ExpandableListView.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

// This code is from http://code.google.com/p/android/issues/detail?id=2732
/**
 * An activity that displays an expandable list of items by binding to a data
 * source implementing the ExpandableListAdapter, and exposes event handlers
 * when the user selects an item.
 *
 *
 * ExpandableListActivity hosts a
 * [ExpandableListView][android.widget.ExpandableListView] object that can
 * be bound to different data sources that provide a two-levels of data (the
 * top-level is group, and below each group are children). Binding, screen
 * layout, and row layout are discussed in the following sections.
 *
 *
 * **Screen Layout**
 *
 *
 *
 * ExpandableListActivity has a default layout that consists of a single,
 * full-screen, centered expandable list. However, if you desire, you can
 * customize the screen layout by setting your own view layout with
 * setContentView() in onCreate(). To do this, your own view MUST contain an
 * ExpandableListView object with the id "@android:id/list" (or
 * [android.R.id.list] if it's in code)
 *
 *
 * Optionally, your custom view can contain another view object of any type to
 * display when the list view is empty. This "empty list" notifier must have an
 * id "android:empty". Note that when an empty view is present, the expandable
 * list view will be hidden when there is no data to display.
 *
 *
 * The following code demonstrates an (ugly) custom screen layout. It has a list
 * with a green background, and an alternate red "no data" message.
 *
 *
 * <pre>
 * &lt;?xml version=&quot;1.0&quot; encoding=&quot;UTF-8&quot;?&gt;
 * &lt;LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
 * android:orientation=&quot;vertical&quot;
 * android:layout_width=&quot;fill_parent&quot;
 * android:layout_height=&quot;fill_parent&quot;
 * android:paddingLeft=&quot;8dp&quot;
 * android:paddingRight=&quot;8dp&quot;&gt;
 *
 * &lt;ExpandableListView android:id=&quot;@id/android:list&quot;
 * android:layout_width=&quot;fill_parent&quot;
 * android:layout_height=&quot;fill_parent&quot;
 * android:background=&quot;#00FF00&quot;
 * android:layout_weight=&quot;1&quot;
 * android:drawSelectorOnTop=&quot;false&quot;/&gt;
 *
 * &lt;TextView android:id=&quot;@id/android:empty&quot;
 * android:layout_width=&quot;fill_parent&quot;
 * android:layout_height=&quot;fill_parent&quot;
 * android:background=&quot;#FF0000&quot;
 * android:text=&quot;No data&quot;/&gt;
 * &lt;/LinearLayout&gt;
</pre> *
 *
 *
 *
 * **Row Layout**
 *
 * The [ExpandableListAdapter] set in the ExpandableListActivity
 * via [.setListAdapter] provides the [View]s
 * for each row. This adapter has separate methods for providing the group
 * [View]s and child [View]s. There are a couple provided
 * [ExpandableListAdapter]s that simplify use of adapters:
 * [SimpleCursorTreeAdapter] and [SimpleExpandableListAdapter].
 *
 *
 * With these, you can specify the layout of individual rows for groups and
 * children in the list. These constructor takes a few parameters that specify
 * layout resources for groups and children. It also has additional parameters
 * that let you specify which data field to associate with which object in the
 * row layout resource. The [SimpleCursorTreeAdapter] fetches data from
 * [Cursor]s and the [SimpleExpandableListAdapter] fetches data
 * from [List]s of [Map]s.
 *
 *
 *
 * Android provides some standard row layout resources. These are in the
 * [android.R.layout] class, and have names such as simple_list_item_1,
 * simple_list_item_2, and two_line_list_item. The following layout XML is the
 * source for the resource two_line_list_item, which displays two data
 * fields,one above the other, for each list row.
 *
 *
 * <pre>
 * &lt;?xml version=&quot;1.0&quot; encoding=&quot;utf-8&quot;?&gt;
 * &lt;LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
 * android:layout_width=&quot;fill_parent&quot;
 * android:layout_height=&quot;wrap_content&quot;
 * android:orientation=&quot;vertical&quot;&gt;
 *
 * &lt;TextView android:id=&quot;@+id/text1&quot;
 * android:textSize=&quot;16sp&quot;
 * android:textStyle=&quot;bold&quot;
 * android:layout_width=&quot;fill_parent&quot;
 * android:layout_height=&quot;wrap_content&quot;/&gt;
 *
 * &lt;TextView android:id=&quot;@+id/text2&quot;
 * android:textSize=&quot;16sp&quot;
 * android:layout_width=&quot;fill_parent&quot;
 * android:layout_height=&quot;wrap_content&quot;/&gt;
 * &lt;/LinearLayout&gt;
</pre> *
 *
 *
 *
 * You must identify the data bound to each TextView object in this layout. The
 * syntax for this is discussed in the next section.
 *
 *
 *
 * **Binding to Data**
 *
 *
 *
 * You bind the ExpandableListActivity's ExpandableListView object to data using
 * a class that implements the
 * [ExpandableListAdapter][android.widget.ExpandableListAdapter] interface.
 * Android provides two standard list adapters:
 * [SimpleExpandableListAdapter][android.widget.SimpleExpandableListAdapter]
 * for static data (Maps), and
 * [SimpleCursorTreeAdapter][android.widget.SimpleCursorTreeAdapter] for
 * Cursor query results.
 *
 *
 * @see .setListAdapter
 *
 * @see android.widget.ExpandableListView
 */
open class FixedExpandableListActivity : AppCompatActivity(), OnCreateContextMenuListener,
    OnChildClickListener, OnGroupCollapseListener, OnGroupExpandListener {
    /**
     * Get the ExpandableListAdapter associated with this activity's
     * ExpandableListView.
     */
    private var expandableListAdapter: ExpandableListAdapter? = null
    private var mList: ExpandableListView? = null
    private var mFinishedStart = false

    /**
     * Override this to populate the context menu when an item is long pressed. menuInfo
     * will contain an [android.widget.ExpandableListView.ExpandableListContextMenuInfo]
     * whose packedPosition is a packed position
     * that should be used with [ExpandableListView.getPackedPositionType] and
     * the other similar methods.
     *
     *
     * {@inheritDoc}
     */
    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenuInfo) {}

    /**
     * Override this for receiving callbacks when a child has been clicked.
     *
     *
     * {@inheritDoc}
     */
    override fun onChildClick(
        parent: ExpandableListView, v: View, groupPosition: Int,
        childPosition: Int, id: Long
    ): Boolean {
        return false
    }

    /**
     * Override this for receiving callbacks when a group has been collapsed.
     */
    override fun onGroupCollapse(groupPosition: Int) {}

    /**
     * Override this for receiving callbacks when a group has been expanded.
     */
    override fun onGroupExpand(groupPosition: Int) {}

    /**
     * Ensures the expandable list view has been created before Activity restores all
     * of the view states.
     *
     * @see AppCompatActivity.onRestoreInstanceState
     */
    override fun onRestoreInstanceState(state: Bundle) {
        ensureList()
        super.onRestoreInstanceState(state)
    }

    /**
     * Updates the screen state (current list and other views) when the
     * content changes.
     *
     * @see AppCompatActivity.onContentChanged
     */
    override fun onContentChanged() {
        super.onContentChanged()
        val emptyView = findViewById<View>(android.R.id.empty)
        /* Issue 3443 - http://code.google.com/p/android/issues/detail?id=3443
         * Just use another id to find expandable list view
         *  */mList = findViewById(android.R.id.list)
        if (mList == null) {
            throw RuntimeException(
                "Your content must have a ExpandableListView whose id attribute is " +
                        "'ru.ponkin.R.id.expandable_listview'"
            )
        }
        if (emptyView != null) {
            mList!!.emptyView = emptyView
        }
        mList!!.setOnChildClickListener(this)
        mList!!.setOnGroupExpandListener(this)
        mList!!.setOnGroupCollapseListener(this)
        mList!!.setGroupIndicator(ContextCompat.getDrawable(this, R.drawable.expander_group))
        if (mFinishedStart) {
            setListAdapter(expandableListAdapter)
        }
        mFinishedStart = true
    }

    /**
     * Provide the adapter for the expandable list.
     */
    fun setListAdapter(adapter: ExpandableListAdapter?) {
        synchronized(this) {
            ensureList()
            expandableListAdapter = adapter
            mList!!.setAdapter(adapter)
        }
    }

    /**
     * Get the activity's expandable list view widget.  This can be used to get the selection,
     * set the selection, and many other useful functions.
     *
     * @see ExpandableListView
     */
    val expandableListView: ExpandableListView?
        get() {
            ensureList()
            return mList
        }

    private fun ensureList() {
        if (mList != null) {
            return
        }
        setContentView(android.R.layout.expandable_list_content)
    }

    /**
     * Gets the ID of the currently selected group or child.
     *
     * @return The ID of the currently selected group or child.
     */
    val selectedId: Long
        get() = mList!!.selectedId

    /**
     * Gets the position (in packed position representation) of the currently
     * selected group or child. Use
     * [ExpandableListView.getPackedPositionType],
     * [ExpandableListView.getPackedPositionGroup], and
     * [ExpandableListView.getPackedPositionChild] to unpack the returned
     * packed position.
     *
     * @return A packed position representation containing the currently
     * selected group or child's position and type.
     */
    val selectedPosition: Long
        get() = mList!!.selectedPosition

    /**
     * Sets the selection to the specified child. If the child is in a collapsed
     * group, the group will only be expanded and child subsequently selected if
     * shouldExpandGroup is set to true, otherwise the method will return false.
     *
     * @param groupPosition The position of the group that contains the child.
     * @param childPosition The position of the child within the group.
     * @param shouldExpandGroup Whether the child's group should be expanded if
     * it is collapsed.
     * @return Whether the selection was successfully set on the child.
     */
    fun setSelectedChild(
        groupPosition: Int,
        childPosition: Int,
        shouldExpandGroup: Boolean
    ): Boolean {
        return mList!!.setSelectedChild(groupPosition, childPosition, shouldExpandGroup)
    }

    /**
     * Sets the selection to the specified group.
     * @param groupPosition The position of the group that should be selected.
     */
    fun setSelectedGroup(groupPosition: Int) {
        mList!!.setSelectedGroup(groupPosition)
    }
}