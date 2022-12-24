// https://github.com/domoritz/open-mensa-android/blob/master/src/android/support/v4/app/ExpandableListFragment.java
package com.myflightbook.android

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.view.*
import android.view.ContextMenu.ContextMenuInfo
import android.view.View.OnCreateContextMenuListener
import android.view.animation.AnimationUtils
import android.widget.*
import android.widget.AdapterView.OnItemClickListener
import android.widget.ExpandableListView.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

open class ExpandableListFragment : Fragment(), OnCreateContextMenuListener, OnChildClickListener,
    OnGroupCollapseListener, OnGroupExpandListener {
    private val mHandler = Handler()
    private val mRequestFocus =
        Runnable { mExpandableList!!.focusableViewAvailable(mExpandableList) }
    private val mOnClickListener =
        OnItemClickListener { parent: AdapterView<*>?, v: View, position: Int, id: Long ->
            onListItemClick(
                parent as ExpandableListView?,
                v,
                position,
                id
            )
        }

    /**
     * Get the ExpandableListAdapter associated with this activity's
     * ExpandableListView.
     */
    private var expandableListAdapter: ExpandableListAdapter? = null
    private var mExpandableList: ExpandableListView? = null
    private var mFinishedStart = false
    private var mEmptyView: View? = null
    private var mStandardEmptyView: TextView? = null
    private var mProgressContainer: View? = null
    private var mExpandableListContainer: View? = null
    private var mEmptyText: CharSequence? = null
    private var mExpandableListShown = false

    /**
     * Provide default implementation to return a simple list view. Subclasses
     * can override to replace with their own layout. If doing so, the
     * returned view hierarchy *must* have a ListView whose id
     * is [android.R.id.list] and can optionally
     * have a sibling view id [android.R.id.empty]
     * that is to be shown when the list is empty.
     *
     *
     * If you are overriding this method with your own custom content,
     * consider including the standard layout [android.R.layout.list_content]
     * in your layout file, so that you continue to retain all of the standard
     * behavior of ListFragment. In particular, this is currently the only
     * way to have the built-in indeterminant progress state be shown.
     */
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val context: Context = requireActivity()
        val root = FrameLayout(context)

        // ------------------------------------------------------------------
        val pframe = LinearLayout(context)
        pframe.id = INTERNAL_PROGRESS_CONTAINER_ID
        pframe.orientation = LinearLayout.VERTICAL
        pframe.visibility = View.GONE
        pframe.gravity = Gravity.CENTER
        val progress = ProgressBar(
            context, null,
            android.R.attr.progressBarStyleLarge
        )
        pframe.addView(
            progress, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        root.addView(
            pframe, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        // ------------------------------------------------------------------
        val lframe = FrameLayout(context)
        lframe.id = INTERNAL_LIST_CONTAINER_ID
        val tv = TextView(activity)
        tv.id = INTERNAL_EMPTY_ID
        tv.gravity = Gravity.CENTER
        lframe.addView(
            tv, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        val lv = ExpandableListView(activity)
        lv.id = android.R.id.list
        lv.isDrawSelectorOnTop = false
        lframe.addView(
            lv, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        root.addView(
            lframe, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        // ------------------------------------------------------------------
        root.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        )
        return root
    }

    /**
     * Attach to list view once the view hierarchy has been created.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ensureList()
    }

    /**
     * Detach from list view.
     */
    override fun onDestroyView() {
        mHandler.removeCallbacks(mRequestFocus)
        mExpandableList = null
        mExpandableListShown = false
        mExpandableListContainer = null
        mProgressContainer = mExpandableListContainer
        mEmptyView = mProgressContainer
        mStandardEmptyView = null
        super.onDestroyView()
    }

    /**
     * This method will be called when an item in the list is selected.
     * Subclasses should override. Subclasses can call
     * getListView().getItemAtPosition(position) if they need to access the
     * data associated with the selected item.
     *
     * @param l The ListView where the click happened
     * @param v The view that was clicked within the ListView
     * @param position The position of the view in the list
     * @param id The row id of the item that was clicked
     */
    @Suppress("EmptyMethod")
    private fun onListItemClick(l: ExpandableListView?, v: View, position: Int, id: Long) {}

    /**
     * Set the currently selected list item to the specified
     * position with the adapter's data
     *
     */
    @Suppress("UNUSED")
    fun setSelection(position: Int) {
        ensureList()
        mExpandableList!!.setSelection(position)
    }

    /**
     * Get the position of the currently selected list item.
     */
    @Suppress("UNUSED")
    val selectedItemPosition: Int
        get() {
            ensureList()
            return mExpandableList!!.selectedItemPosition
        }

    /**
     * Get the cursor row ID of the currently selected list item.
     */
    val selectedItemId: Long
        get() {
            ensureList()
            return mExpandableList!!.selectedItemId
        }

    /**
     * Get the activity's list view widget.
     */
    val listView: ExpandableListView?
        get() {
            ensureList()
            return mExpandableList
        }

    /**
     * The default content for a ListFragment has a TextView that can
     * be shown when the list is empty. If you would like to have it
     * shown, call this method to supply the text it should use.
     */
    @Suppress("UNUSED")
    fun setEmptyText(text: CharSequence?) {
        ensureList()
        checkNotNull(mStandardEmptyView) { "Can't be used with a custom content view" }
        mStandardEmptyView!!.text = text
        if (mEmptyText == null) {
            mExpandableList!!.emptyView = mStandardEmptyView
        }
        mEmptyText = text
    }

    /**
     * Control whether the list is being displayed. You can make it not
     * displayed if you are waiting for the initial data to show in it. During
     * this time an indeterminant progress indicator will be shown instead.
     *
     *
     * Applications do not normally need to use this themselves. The default
     * behavior of ListFragment is to start with the list not being shown, only
     * showing it once an adapter is given with ListAdapter.
     * If the list at that point had not been shown, when it does get shown
     * it will be do without the user ever seeing the hidden state.
     *
     * @param shown If true, the list view is shown; if false, the progress
     * indicator. The initial value is true.
     */
    @Suppress("UNUSED")
    fun setListShown(shown: Boolean) {
        setListShown(shown, true)
    }

    /**
     * Like [.setListShown], but no animation is used when
     * transitioning from the previous state.
     */
    @Suppress("UNUSED")
    fun setListShownNoAnimation(shown: Boolean) {
        setListShown(shown, false)
    }

    /**
     * Control whether the list is being displayed. You can make it not
     * displayed if you are waiting for the initial data to show in it. During
     * this time an indeterminant progress indicator will be shown instead.
     *
     * @param shown If true, the list view is shown; if false, the progress
     * indicator. The initial value is true.
     * @param animate If true, an animation will be used to transition to the
     * new state.
     */
    private fun setListShown(shown: Boolean, animate: Boolean) {
        ensureList()
        checkNotNull(mProgressContainer) { "Can't be used with a custom content view" }
        if (mExpandableListShown == shown) {
            return
        }
        mExpandableListShown = shown
        if (shown) {
            if (animate) {
                mProgressContainer!!.startAnimation(
                    AnimationUtils.loadAnimation(
                        activity, android.R.anim.fade_out
                    )
                )
                mExpandableListContainer!!.startAnimation(
                    AnimationUtils.loadAnimation(
                        activity, android.R.anim.fade_in
                    )
                )
            } else {
                mProgressContainer!!.clearAnimation()
                mExpandableListContainer!!.clearAnimation()
            }
            mProgressContainer!!.visibility = View.GONE
            mExpandableListContainer!!.visibility = View.VISIBLE
        } else {
            if (animate) {
                mProgressContainer!!.startAnimation(
                    AnimationUtils.loadAnimation(
                        activity, android.R.anim.fade_in
                    )
                )
                mExpandableListContainer!!.startAnimation(
                    AnimationUtils.loadAnimation(
                        activity, android.R.anim.fade_out
                    )
                )
            } else {
                mProgressContainer!!.clearAnimation()
                mExpandableListContainer!!.clearAnimation()
            }
            mProgressContainer!!.visibility = View.VISIBLE
            mExpandableListContainer!!.visibility = View.GONE
        }
    }
    /**
     * Get the ListAdapter associated with this activity's ListView.
     */// The list was hidden, and previously didn't have an
    // adapter. It is now time to show it.
    /**
     * Provide the cursor for the list view.
     */
    var listAdapter: ExpandableListAdapter?
        get() = expandableListAdapter
        set(adapter) {
            val hadAdapter = expandableListAdapter != null
            expandableListAdapter = adapter
            if (mExpandableList != null) {
                mExpandableList!!.setAdapter(adapter)
                if (!mExpandableListShown && !hadAdapter) {
                    // The list was hidden, and previously didn't have an
                    // adapter. It is now time to show it.
                    val v = view
                    if (v != null) setListShown(true, requireView().windowToken != null)
                }
            }
        }

    private fun ensureList() {
        if (mExpandableList != null) {
            return
        }
        val root = view ?: throw IllegalStateException("Content view not yet created")
        if (root is ExpandableListView) {
            mExpandableList = root
        } else {
            mStandardEmptyView = root.findViewById(INTERNAL_EMPTY_ID)
            if (mStandardEmptyView == null) {
                mEmptyView = root.findViewById(android.R.id.empty)
            } else {
                mStandardEmptyView!!.visibility = View.GONE
            }
            mProgressContainer = root.findViewById(INTERNAL_PROGRESS_CONTAINER_ID)
            mExpandableListContainer = root.findViewById(INTERNAL_LIST_CONTAINER_ID)
            val rawExpandableListView = root.findViewById<View>(android.R.id.list)
            if (rawExpandableListView !is ExpandableListView) {
                if (rawExpandableListView == null) {
                    throw RuntimeException(
                        "Your content must have a ListView whose id attribute is " +
                                "'android.R.id.list'"
                    )
                }
                throw RuntimeException(
                    "Content has view with id attribute 'android.R.id.list' "
                            + "that is not a ListView class"
                )
            }
            mExpandableList = rawExpandableListView
            if (mEmptyView != null) {
                mExpandableList!!.emptyView = mEmptyView
            } else if (mEmptyText != null) {
                val ev = mStandardEmptyView
                ev!!.text = mEmptyText
                mExpandableList!!.emptyView = ev
            }
        }
        mExpandableListShown = true
        mExpandableList!!.onItemClickListener = mOnClickListener
        // add invisible indicator
        mExpandableList!!.setGroupIndicator(
            ContextCompat.getDrawable(
                requireContext(),
                R.drawable.expander_group
            )
        )
        if (expandableListAdapter != null) {
            val adapter = expandableListAdapter!!
            expandableListAdapter = null
            listAdapter = adapter
        } else {
            // We are starting without an adapter, so assume we won't
            // have our data right away and start with the progress indicator.
            if (mProgressContainer != null) {
                setListShown(
                    shown = false,
                    animate = false
                )
            }
        }
        mHandler.post(mRequestFocus)
    }

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
    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenuInfo?) {}

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
    // /**
    // * Ensures the expandable list view has been created before Activity restores all
    // * of the view states.
    // *
    // *@see Activity#onRestoreInstanceState(Bundle)
    // */
    // @Override
    // protected void onRestoreInstanceState(Bundle state) {
    // ensureList();
    // super.onRestoreInstanceState(state);
    // }
    /**
     * Updates the screen state (current list and other views) when the
     * content changes.
     *
     */
    @Suppress("UNUSED")
    fun onContentChanged() {
// super.onContentChanged();
        val v = view ?: return
        val emptyView = requireView().findViewById<View>(android.R.id.empty)
        mExpandableList = requireView().findViewById(android.R.id.list)
        if (mExpandableList == null) {
            throw RuntimeException(
                "Your content must have a ExpandableListView whose id attribute is " +
                        "'android.R.id.list'"
            )
        }
        if (emptyView != null) {
            mExpandableList!!.emptyView = emptyView
        }
        mExpandableList!!.setOnChildClickListener(this)
        mExpandableList!!.setOnGroupExpandListener(this)
        mExpandableList!!.setOnGroupCollapseListener(this)
        if (mFinishedStart) {
            listAdapter = expandableListAdapter
        }
        mFinishedStart = true
    }

    /**
     * Get the activity's expandable list view widget. This can be used to get the selection,
     * set the selection, and many other useful functions.
     *
     * @see ExpandableListView
     */
    val expandableListView: ExpandableListView?
        get() {
            ensureList()
            return mExpandableList
        }

    /**
     * Gets the ID of the currently selected group or child.
     *
     * @return The ID of the currently selected group or child.
     */
    @Suppress("UNUSED")
    val selectedId: Long
        get() = mExpandableList!!.selectedId

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
    @Suppress("UNUSED")
    val selectedPosition: Long
        get() = mExpandableList!!.selectedPosition

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
    @Suppress("UNUSED")
    fun setSelectedChild(
        groupPosition: Int,
        childPosition: Int,
        shouldExpandGroup: Boolean
    ): Boolean {
        return mExpandableList!!.setSelectedChild(groupPosition, childPosition, shouldExpandGroup)
    }

    /**
     * Sets the selection to the specified group.
     * @param groupPosition The position of the group that should be selected.
     */
    @Suppress("UNUSED")
    fun setSelectedGroup(groupPosition: Int) {
        mExpandableList!!.setSelectedGroup(groupPosition)
    }

    companion object {
        private val INTERNAL_EMPTY_ID = View.generateViewId()
        private val INTERNAL_PROGRESS_CONTAINER_ID = View.generateViewId()
        private val INTERNAL_LIST_CONTAINER_ID = View.generateViewId()
    }
}