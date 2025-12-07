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

import java.util.*

class TemplateGroup private constructor(group: Int, groupName: String) : Comparable<TemplateGroup> {
    private var group = group
    @JvmField
    var groupDisplayName = groupName
    @JvmField
    var templates = ArrayList<PropertyTemplate>()

    init {
        templates = ArrayList()
    }

    override fun compareTo(other: TemplateGroup): Int {
        return if (this@TemplateGroup.group == PropertyTemplate.GROUP_AUTO) if (other.group == 0) 0 else -1 else groupDisplayName.compareTo(
            other.groupDisplayName
        )
    }

    override fun toString(): String {
        return String.format(
            Locale.getDefault(),
            "%s (%d templates)",
            groupDisplayName,
            templates.size
        )
    }

    companion object {
        // region sorting/grouping
        @JvmStatic
        fun groupTemplates(rgpt: Array<PropertyTemplate>?): Array<TemplateGroup> {
            if (rgpt == null || rgpt.isEmpty()) return arrayOf()
            val h = Hashtable<Int, TemplateGroup>()
            for (pt in rgpt) {
                var tg: TemplateGroup?
                if (!h.containsKey(pt.groupAsInt)) h[pt.groupAsInt] =
                    TemplateGroup(pt.groupAsInt, pt.groupDisplayName).also { tg = it } else tg =
                    h[pt.groupAsInt]
                assert(tg != null)
                tg!!.templates.add(pt)
            }
            val al = ArrayList(h.values)
            al.sort()
            for (tg in al)
                tg.templates.sort()
            return al.toTypedArray()
        } // endregion}
    }
}