import java.io.*;

import org.kxml2.io.*;
import org.xmlpull.v1.*;

public class EventList {

    public static void main (String [] args) throws IOException, XmlPullParserException{
	
	
	for (int i = 0; i < 2; i++) {

	    XmlReader xr = new XmlReader ();
	    xr.setInput (new FileReader (args [0]));

	    System.out.println ("");
	    System.out.println ("*** next" + (i==0 ? "Token":"")+" () event list ***");
	    System.out.println ("");
	
	    do {
		if (i == 0) xr.nextToken ();
		else xr.next ();

		System.out.println (xr.getPositionDescription ());

		/*		System.out.println ("depth:" + xr.getDepth () 
				    + " nspcount (depth) "+xr.getNamespacesCount (xp.getDepth ()));

		for (int k = 0; k < xp.getNamespacesCount (xr.getDepth ()); k++)
		    System.out.println ("prefix: "+x.getNamespacesPrefix (k)+" uri: "+xp.getNamespacesUri (k));
		*/

	    }
	    while (xr.getType () != XmlPullParser.END_DOCUMENT);
	}
    }

}





