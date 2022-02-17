package com.myflightbook.android.marshal

import org.ksoap2.serialization.Marshal
import kotlin.Throws
import org.kobjects.isodate.IsoDate
import org.ksoap2.serialization.PropertyInfo
import org.ksoap2.serialization.SoapSerializationEnvelope
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlSerializer
import java.io.IOException
import java.util.*

/**
 *
 * @author Vladimir
 * Used to marshal Dates - crucial to serialization for SOAP
 * See http://seesharpgears.blogspot.com/2010/11/implementing-ksoap-marshal-interface.html
 */
class MarshalDate : Marshal {
    @Throws(IOException::class, XmlPullParserException::class)
    override fun readInstance(parser: XmlPullParser, namespace: String, name: String,
                              expected: PropertyInfo): Any {
        return IsoDate.stringToDate(parser.nextText(), IsoDate.DATE_TIME)
    }

    override fun register(cm: SoapSerializationEnvelope) {
        cm.addMapping(cm.xsd, "DateTime", Date::class.java, this)
    }

    @Throws(IOException::class)
    override fun writeInstance(writer: XmlSerializer, obj: Any) {
        writer.text(IsoDate.dateToString(obj as Date, IsoDate.DATE_TIME))
    }
}