import java.io.*;
import java.util.Vector;

import org.kxml2.io.*;
import org.xmlpull.v1.*;

import javax.microedition.midlet.*;
import javax.microedition.lcdui.*;
import javax.microedition.io.*;


public class Newsreader extends MIDlet implements CommandListener {

    static final String URL = "http://www.newsforge.com/newsforge.xml";
    static final String TITLE = "NewsForge";
	
    Vector descriptions = new Vector ();
    List newsList = new List (TITLE, Choice.IMPLICIT);
    TextBox textBox = new TextBox ("", "", 256, TextField.ANY);
    Display display;

    Command backCmd = new Command ("Back", Command.BACK, 0);

    class ReadThread extends Thread {
   
	public void run () {
	    try {
		HttpConnection httpConnection = 
		    (HttpConnection) Connector.open (URL);

	        XmlPullParser parser = new KXmlParser ();

                parser.setInput (new InputStreamReader 
		    (httpConnection.openInputStream ()));
		
                //		parser.relaxed = true;

		parser.next ();
		parser.require (parser.START_TAG, null, "backslash");
                parser.readText (); // skip space
		
		while (parser.next () != parser.END_TAG) 
		    readStory (parser);
		   
		parser.require (parser.END_TAG, null, "backslash");
		parser.next ();

		parser.require (parser.END_DOCUMENT, null, null);
	    }
	    catch (Exception e) {
		e.printStackTrace ();
		descriptions.addElement (e.toString ());
		newsList.append ("Error", null);		
	    }
	}
	

	/** Read a story and append it to the list */

	void readStory (XmlPullParser parser) throws IOException, XmlPullParserException {

	    parser.require (parser.START_TAG, null, "story");
	    
	    String title = null;
	    String description = null;

	    while (parser.next () != parser.END_TAG) {
		
		parser.require (parser.START_TAG, null, null);
		String name = parser.getName ();
		parser.next ();

		String text = parser.readText ();

		if (name.equals ("title"))
		    title = text;
		else if (name.equals ("description"))
		    description = text;
		

		parser.require (parser.END_TAG, null, name);
	    }
	    parser.require (parser.END_TAG, null, "story");

	    if (description != null && title != null) { 
		descriptions.addElement (description);
		newsList.append (title, null);
	    }
            
            parser.readText ();
	}
    }

    public void startApp () {
	display = Display.getDisplay (this);
	display.setCurrent (newsList);
	newsList.setCommandListener (this);
	textBox.setCommandListener (this);
	textBox.addCommand (backCmd);
	new ReadThread ().start ();
    }


    public void pauseApp () {
    }



    public void commandAction (Command c, Displayable d) {

	if (c == List.SELECT_COMMAND) {
	    
	    String text = (String) descriptions.elementAt 
		(newsList.getSelectedIndex ());
		 
	    if (textBox.getMaxSize () < text.length ()) 
		textBox.setMaxSize (text.length ());
	    
	    textBox.setString (text);
	    display.setCurrent (textBox);
	}
	else if (c == backCmd)
	    display.setCurrent (newsList);
    }
 

    public void destroyApp (boolean really) {
    }


}



