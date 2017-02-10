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

import org.ksoap2.serialization.KvmSerializable;
import org.ksoap2.serialization.PropertyInfo;
import org.ksoap2.serialization.SoapObject;

import java.util.Hashtable;


public class PostingOptions extends SoapableObject implements KvmSerializable {

    private enum postingOptions {pidFacebook, pidTwitter}

    public Boolean m_fTweet = false;
    public Boolean m_fPostFacebook = false;

    // serialization methods
    public int getPropertyCount() {
        return postingOptions.values().length;
    }

    public Object getProperty(int i) {
        postingOptions po = postingOptions.values()[i];
        Object o = null;
        switch (po) {
            case pidFacebook:
                return this.m_fPostFacebook;
            case pidTwitter:
                return this.m_fTweet;
            default:
                break;
        }
        return o;
    }

    public void setProperty(int i, Object value) {
        postingOptions po = postingOptions.values()[i];
        String sz = value.toString();
        switch (po) {
            case pidFacebook:
                this.m_fPostFacebook = Boolean.parseBoolean(sz);
                break;
            case pidTwitter:
                this.m_fTweet = Boolean.parseBoolean(sz);
                break;
            default:
                break;
        }
    }

    public void getPropertyInfo(int i, Hashtable h, PropertyInfo pi) {
        postingOptions po = postingOptions.values()[i];
        switch (po) {
            case pidTwitter:
                pi.type = PropertyInfo.BOOLEAN_CLASS;
                pi.name = "PostToTwitter";
                break;
            case pidFacebook:
                pi.type = PropertyInfo.BOOLEAN_CLASS;
                pi.name = "PostToFacebook";
                break;
            default:
                break;
        }
    }

    @Override
    public void ToProperties(SoapObject so) {
        so.addProperty("PostToFacebook", m_fPostFacebook);
        so.addProperty("PostToTwitter", m_fTweet);
    }

    @Override
    public void FromProperties(SoapObject so) {
        m_fPostFacebook = Boolean.parseBoolean(so.getProperty("PostToFaceBook").toString());
        m_fTweet = Boolean.parseBoolean(so.getProperty("PostToTwitter").toString());
    }

}
