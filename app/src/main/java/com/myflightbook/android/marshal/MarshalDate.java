package com.myflightbook.android.marshal;
import org.kobjects.isodate.IsoDate;
import org.ksoap2.serialization.Marshal;
import org.ksoap2.serialization.PropertyInfo;
import org.ksoap2.serialization.SoapSerializationEnvelope;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;
import java.util.Date;

/**
 * 
 * @author Vladimir
 * Used to marshal Dates - crucial to serialization for SOAP
 * See http://seesharpgears.blogspot.com/2010/11/implementing-ksoap-marshal-interface.html
 */
public class MarshalDate implements Marshal
{

        public Object readInstance(XmlPullParser parser, String namespace, String name, 
                PropertyInfo expected) throws IOException, XmlPullParserException {
            
            return IsoDate.stringToDate(parser.nextText(), IsoDate.DATE_TIME);
            
            
        }


        public void register(SoapSerializationEnvelope cm) {
             cm.addMapping(cm.xsd, "DateTime", Date.class, this);
            
        }


        public void writeInstance(XmlSerializer writer, Object obj) throws IOException {
             writer.text(IsoDate.dateToString((Date) obj, IsoDate.DATE_TIME));
            }
    
}
