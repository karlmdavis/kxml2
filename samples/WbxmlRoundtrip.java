import java.io.*;

import org.kxml2.io.*;
import org.kxml2.wap.*;
import org.xmlpull.v1.*;

/*
 * Created on 25.09.2003
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */

/**
 * @author haustein
 *
 * To change the template for this generated type comment go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
public class WbxmlRoundtrip {

	public static void main(String[] argv) throws Exception {
		
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		
		XmlPullParser xp = new KXmlParser();
		xp.setInput(new FileReader(argv[0]));
		XmlSerializer xs = new WbxmlSerializer();
		xs.setOutput(bos, null); 
		
		new Roundtrip(xp, xs).roundTrip();
		
		byte[] wbxml = bos.toByteArray();
		
		for(int i = 0; i < wbxml.length; i += 16){
			for (int j = i; j < Math.min(i + 16, wbxml.length); j ++) {
				int b = ((int) wbxml[j]) & 0x0ff;
				System.out.print(Integer.toHexString(b / 16));
				System.out.print(Integer.toHexString(b % 16));
				System.out.print(' ');
			}
			
			for (int j = i; j < Math.min(i + 16, wbxml.length); j ++) {
				int b = wbxml[j];
				System.out.print(b >= 32 && b <= 127 ? (char) b : '?');
			}
			
			System.out.println();
		}
		
		ByteArrayInputStream bis = new ByteArrayInputStream(wbxml);
		
		xp = new WbxmlParser();
		xp.setInput(bis, null);
		
		xs = new KXmlSerializer();
		xs.setOutput(System.out, null);
		
		new Roundtrip(xp, xs).roundTrip();
	}

}
