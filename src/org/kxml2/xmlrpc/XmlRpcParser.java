/* Copyright (c) 2003, David Li
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or
 * sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The  above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE. */

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
     * @return Maps XML-RPC structs to java.util.Hashtables
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
