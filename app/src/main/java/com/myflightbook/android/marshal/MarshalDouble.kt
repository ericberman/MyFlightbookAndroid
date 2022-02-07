package com.myflightbook.android.marshal

import org.ksoap2.serialization.Marshal
import kotlin.Throws
import org.ksoap2.serialization.PropertyInfo
import org.ksoap2.serialization.SoapSerializationEnvelope
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlSerializer
import java.io.IOException

/**
 *
 * @author Vladimir
 * Used to marshal Doubles - crucial to serialization for SOAP
 * See http://seesharpgears.blogspot.com/2010/11/implementing-ksoap-marshal-interface.html
 */
class MarshalDouble : Marshal {
    @Throws(IOException::class, XmlPullParserException::class)
    override fun readInstance(parser: XmlPullParser, namespace: String, name: String,
                              expected: PropertyInfo): Any {
        return parser.nextText().toDouble()
    }

    override fun register(cm: SoapSerializationEnvelope) {
        // Kotlin mapping from Java maps double to the Java intrinsic class "Double", but we also need
        // to map to java.lang.Double.
        cm.addMapping(cm.xsd, "java.lang.Double", java.lang.Double::class.java, this)
        cm.addMapping(cm.xsd, "double", Double::class.java, this)
    }

    @Throws(IOException::class)
    override fun writeInstance(writer: XmlSerializer, obj: Any) {
        writer.text(obj.toString())
    }
}