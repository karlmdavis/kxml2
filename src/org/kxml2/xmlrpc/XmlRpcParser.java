/* kxmlrpc
 *
 * The contents of this file are subject to the Enhydra Public License
 * Version 1.1 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License
 * on the Enhydra web site ( http://www.enhydra.org/ ).
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See
 * the License for the specific terms governing rights and limitations
 * under the License.
 *
 * The Initial Developer of kxmlrpc is Kyle Gabhart. Copyright (C) 2001 
 * Kyle Gabhart -- kyle.gabhart@enhydra.org . All Rights Reserved.
 *
 * Contributor(s): Stefan Haustein
 */

package org.kxml2.xmlrpc;

import java.io.FileReader;
import java.io.IOException;
import java.util.Vector;
import java.util.Hashtable;

import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import org.kobjects.isodate.IsoDate;
import org.kobjects.base64.Base64;

/**
 * This abstract base class provides basic XML-RPC parsing capabilities. The 
 * kxml parser is required by this class.
 *
 * @author David Li
 */
public class XmlRpcParser {

    private XmlPullParser       parser = null;

    /**
     * @param parser    a XmlPullParser object
     */
    public XmlRpcParser(XmlPullParser parser) {
        this.parser = parser;
    }
    
    /**
     * @return kxmlrpc maps XML-RPC structs to java.util.Hashtables
     */
    private Hashtable parseStruct() throws IOException, XmlPullParserException {
	Hashtable result = new Hashtable();
        int type;
	
        // parser.require(XmlPullParser.START_TAG, "", "struct");
        type = parser.nextTag();
	while(type != XmlPullParser.END_TAG) {
            // parser.require(XmlPullParser.START_TAG, "", "member");
            parser.nextTag();
	    // parser.require( XmlPullParser.START_TAG, "", "name" );
            String name = parser.nextText();
	    // parser.require( XmlPullParser.END_TAG, "", "name" );
	    parser.nextTag();
	    result.put( name, parseValue() ); // parse this member value
	    // parser.require( XmlPullParser.END_TAG, "", "member" );
	    type = parser.nextTag();
	}
        // parser.require(XmlPullParser.END_TAG, "", "struct");
        parser.nextTag();
	return result;
    }


    private Object parseValue() throws IOException, XmlPullParserException {
	Object result = null;
        int event;
        
	// parser.require(XmlPullParser.START_TAG, "", "value");
	event = parser.nextTag();

	if (event == XmlPullParser.START_TAG) {
	    String name = parser.getName();
            if(name.equals("array")) {
                result = parseArray();
            } else if(name.equals("struct")) {
                result = parseStruct(); 
            } else {
                if( name.equals("string") ) {
                    result = parser.nextText();
                } else if( name.equals("i4") || name.equals("int") ) {
                    result = new Integer (Integer.parseInt(parser.nextText().trim()));
                } else if( name.equals("boolean") ) {
                    result = new Boolean(parser.nextText().trim().equals("1"));
                } else if(name.equals("dateTime.iso8601")) {
                    result = IsoDate.stringToDate(parser.nextText(), IsoDate.DATE_TIME );
                } else if( name.equals("base64") ) {
                    result = Base64.decode(parser.nextText());
                } else if( name.equals("double") ) {
                    result = parser.nextText();
                }
                // parser.require( XmlPullParser.END_TAG, "", name );
                parser.nextTag();
            }
	}
	// parser.require( XmlPullParser.END_TAG, "", "value" );
        parser.nextTag();
	return result;
    }

    private Vector parseArray() throws XmlPullParserException, IOException {
        // parser.require( XmlPullParser.START_TAG, "", "array" );
	parser.nextTag();
        // parser.require( XmlPullParser.START_TAG, "", "data" );
        int type = parser.nextTag();

	Vector vec = new Vector();
	while( type != XmlPullParser.END_TAG ) {
	    vec.addElement( parseValue() ); 
            type = parser.getEventType();
	}

        // parser.require( XmlPullParser.END_TAG, "", "data" );
        parser.nextTag();
        // parser.require( XmlPullParser.END_TAG, "", "array" );
        parser.nextTag();

	return vec;
    }//end parseArray()


    private Object parseFault() throws XmlPullParserException, IOException {
        // parser.require( XmlPullParser.START_TAG, "", "fault" );
	parser.nextTag();
        Object value = parseValue();
        // parser.require( XmlPullParser.END_TAG, "", "fault" );
	parser.nextTag();
        return value;
    }

    /**
     * All data in an XML-RPC call is passed as a parameter. This method parses 
     * the parameter values out of each parameter by calling the parseValue() 
     * method. 
     */
    private Object parseParams() throws XmlPullParserException, IOException {
        Vector params = new Vector();
        int type;
        
	// parser.require( XmlPullParser.START_TAG, "", "params" );
	type = parser.nextTag();
        
	while(type != XmlPullParser.END_TAG ) {
	    // parser.require( XmlPullParser.START_TAG, "", "param" );
	    parser.nextTag();
	    params.addElement(parseValue());
	    // parser.require( XmlPullParser.END_TAG, "", "param" );
	    type = parser.nextTag();
	} 
	
	// parser.require( XmlPullParser.END_TAG, "", "params" );
	parser.nextTag();

        return params;
    }

    /** 
     * Called by a client to parse an XML-RPC response returned by a server.
     *
     * @return The return parameter sent back by the server.
     */
    public Object parseResponse() throws XmlPullParserException, IOException {
        Object result = null;
        int event;

        parser.nextTag();
        // parser.require(XmlPullParser.START_TAG, "", "methodResponse");
        event = parser.nextTag();
        if (event == XmlPullParser.START_TAG) {
            if ("fault".equals(parser.getName())) {
                result = parseFault();
            } else if ("params".equals(parser.getName())) {
                result = parseParams();
            } 
        } 
        // parser.require(XmlPullParser.END_TAG, "", "methodResponse");
        return result;
    }

    public static void main(String[] args) throws Exception {
        XmlPullParser parser = new org.kxml2.io.KXmlParser();
        parser.setInput(new FileReader(args[0]));
        XmlRpcParser rpcParser = new XmlRpcParser(parser);
        rpcParser.parseResponse();
    } 
}
