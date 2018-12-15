/*
	MyFlightbook for Android - provides native access to MyFlightbook
	pilot's logbook
    Copyright (C) 2017-2018 MyFlightbook, LLC

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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.support.v4.app.Fragment;
import android.util.Base64;
import android.util.Log;

import com.myflightbook.android.R;
import com.myflightbook.android.WebServices.UTCDate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

public class MFBUtil {

    // region Alerts
    public static void Alert(Context c, String szTitle, String szMessage) {
        if (c == null || c instanceof Activity && ((Activity) c).isFinishing())
            return;

        AlertDialog.Builder builder = new AlertDialog.Builder(c);
        builder.setMessage(szMessage);
        builder.setTitle(szTitle);
        builder.setNegativeButton(c.getString(R.string.lblOK), (dialog, id) -> dialog.cancel());
        AlertDialog alert = builder.create();
        try {
            alert.show();
        }
        catch (Exception ignored) { }
    }

    public static void Alert(Fragment f, String szTitle, String szMessage) {
        Alert(f.getActivity(), szTitle, szMessage);
    }
    //endregion

    // region Progress utilities
    public static ProgressDialog ShowProgress(Context c, String szMessage) {
        ProgressDialog pd = null;

        try {
            // we do this in a try/catch block because we can get a windowleaked crash if we rotate the device
            // in progress.  Doesn't seem to affect the overall worker thread, fortunately, but we can just
            // return the bogus progress dialog if it becomes bogus.
            // do a search on "View not attached to window manager" for details.
            pd = new ProgressDialog(c, R.style.MFBProgressStyle);
            pd.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            pd.isIndeterminate();
            pd.setMessage(szMessage);
            pd.show();
        } catch (Exception ex) {
            Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(ex));
        }
        return pd;
    }

    public static ProgressDialog ShowProgress(Fragment f, String szMessage) {
        return ShowProgress(f.getActivity(), szMessage);
    }
    // endregion

    // region Date utilities
    // Creates a local date that "looks" like the UTC date from which it is derived.
    // I.e., if it is a-b-c d:e UTC, this will be a-b-c d:e PST
    public static Date LocalDateFromUTCDate(Date dt) {
        // Get the date's d/m/y in UTC
        GregorianCalendar c = UTCDate.UTCCalendar();
        c.setTime(dt);
        int y = c.get(Calendar.YEAR);
        int m = c.get(Calendar.MONTH);
        int d = c.get(Calendar.DAY_OF_MONTH);

        // Now create a date in the local timezone that looks like the UTC date
        c = new GregorianCalendar();
        c.set(y, m, d, 0, 0, 0);
        return c.getTime();
    }

    // Creates a UTC date that "looks" like the UTC date from which it is derived.
    // I.e., if it is a-b-c d:e PST, this will be a-b-c d:e UTC
    public static Date UTCDateFromLocalDate(Date dt) {
        // Get the date's d/m/y in the current locale
        GregorianCalendar c = new GregorianCalendar();
        c.setTime(dt);
        int y = c.get(Calendar.YEAR);
        int m = c.get(Calendar.MONTH);
        int d = c.get(Calendar.DAY_OF_MONTH);

        // now create a bogus UTC date at midnight in what appears to be the local date
        // this is what we will send across the wire.
        c = UTCDate.UTCCalendar();
        c.set(y, m, d, 0, 0, 0);
        return c.getTime();
    }


    public static Date removeSeconds(Date dt) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(dt);
        cal.set(Calendar.SECOND, 0);
        return cal.getTime();
    }

    public static Date nowWith0Seconds() {
        return removeSeconds(new Date());
    }
    // endregion

    // region Latitude/Longitude strings (for EXIF)
    static String makeLatLongString(double d) {
        d = Math.abs(d);
        int degrees = (int) d;
        double remainder = d - degrees;
        int minutes = (int) (remainder * 60D);
        int seconds = (int) (((remainder * 60D) - minutes) * 60D * 1000D);
        return degrees + "/1," + minutes + "/1," + seconds + "/1000";
    }
    // endregion

    // region Object serialization/deserialization
    private static String serializeToString(Serializable o) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            oos.writeObject(o);
            oos.flush();
            return new String(Base64.encode(bos.toByteArray(), Base64.DEFAULT));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    private static <T> T deserializeFromString(String s) {
        if (s == null || s.length() == 0)
            return null;

        try {
            byte rgb[] = Base64.decode(s, Base64.DEFAULT);
            ByteArrayInputStream bis = new ByteArrayInputStream(rgb);
            ObjectInputStream ois = new ObjectInputStream(bis);
            return (T) ois.readObject();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

    static <T> T clone(Serializable obj) {
        String s = serializeToString(obj);
        if (s.length() == 0)
            return null;

        return deserializeFromString(s);
    }
    // endregion
}
