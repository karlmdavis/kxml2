
import org.xmlpull.v1.XmlPullParser;
import org.kxml2.io.KXmlParser;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;

// (C) Michael Walker, sun.com

public class LongStringTest	{

	    
	    public static void main(String[] args) {
			String xmlStr =  "<start>"
				+ "<ThisIsALongTagName/>"
				+ "<ThisIsALongTagName/>"
				+ "<ThisIsALongTagName/>"
				+ "<ThisIsALongTagName/>"
				+ "<ThisIsALongTagName/>"
				+ "<ThisIsALongTagName/>"
				+ "</start>";
		System.out.println("xmlStr length: " + xmlStr.length());

		ByteArrayInputStream bais = new ByteArrayInputStream(xmlStr.getBytes());
		InputStreamReader isr = new InputStreamReader(bais);

		try {
		    KXmlParser parser = new KXmlParser();
		    parser.setInput(isr);
		    while (parser.next() != XmlPullParser.END_DOCUMENT) {
			if (parser.getEventType() == XmlPullParser.START_TAG) {
			    System.out.println("start tag: " + parser.getName());
			}
		    }
		} catch (Exception ex) {
		    ex.printStackTrace();
		}
	}

}
