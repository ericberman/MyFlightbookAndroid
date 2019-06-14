/*
	MyFlightbook for Android - provides native access to MyFlightbook
	pilot's logbook
    Copyright (C) 2017-2019 MyFlightbook, LLC

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

import android.support.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;
import java.util.Locale;

public class TemplateGroup implements Comparable<TemplateGroup> {
    private int group;
    public String groupDisplayName;
    public ArrayList<PropertyTemplate> templates;

    public TemplateGroup() {
        group = 0;
        groupDisplayName = "";
        templates = new ArrayList<>();
    }

    private TemplateGroup(int Group, String GroupName) {
        group = Group;
        groupDisplayName = GroupName;
        templates = new ArrayList<>();
    }

    @Override
    public int compareTo(@NonNull TemplateGroup o) {
        if (group == PropertyTemplate.GROUP_AUTO)
            return o.group == 0 ? 0 : -1;

        return groupDisplayName.compareTo(o.groupDisplayName);
    }

    @Override
    public String toString() {
        return String.format(Locale.getDefault(), "%s (%d templates)", groupDisplayName, templates.size());
    }

    // region sorting/grouping
    public static TemplateGroup[] groupTemplates(PropertyTemplate[] rgpt) {
        if (rgpt == null || rgpt.length == 0)
            return new TemplateGroup[0];

        Hashtable<Integer, TemplateGroup> h = new Hashtable<>();

        for (PropertyTemplate pt : rgpt) {
            TemplateGroup tg;
            if (!h.containsKey(pt.GroupAsInt))
                h.put(pt.GroupAsInt, tg = new TemplateGroup(pt.GroupAsInt, pt.GroupDisplayName));
            else
                tg = h.get(pt.GroupAsInt);

            tg.templates.add(pt);
        }

        ArrayList<TemplateGroup> al = new ArrayList<>(h.values());
        Collections.sort(al);
        for (TemplateGroup tg : al)
            Collections.sort(tg.templates);

        return al.toArray(new TemplateGroup[0]);
    }
    // endregion}
}
