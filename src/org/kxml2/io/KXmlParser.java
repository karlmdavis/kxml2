/* kXML
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
 * The Initial Developer of kXML is Stefan Haustein. Copyright (C)
 * 2000, 2001, 2002 Stefan Haustein, D-46045 Oberhausen (Rhld.),
 * Germany. All Rights Reserved.
 *
 * Contributor(s): Paul Palaszewski, Wilhelm Fitzpatrick, 
 *                 Eric Foster-Johnson, Michael Angel, Liam Quinn
 *
 * */

package org.kxml2.io;

import java.io.*;
import java.util.*;

import org.xmlpull.v1.*;

/** A simple, pull based XML parser. This classe replaces the
    XmlParser class and the corresponding event classes. */

public class KXmlParser implements org.xmlpull.v1.XmlPullParser {

    static final private String UNEXPECTED_EOF = "Unexpected EOF"; 
    static final private String ILLEGAL_TYPE = "Wrong event type";
    static final private int LEGACY = 999;

    // general

    private boolean reportNspAttr;
    private boolean processNsp;
    private boolean relaxed;
    private Hashtable entityMap;
    private int depth;
    private String [] elementStack = new String [16];
    private String [] nspStack = new String [8];
    private int [] nspCounts = new int [4]; 
    //    private int normalize;

    // source

    private Reader reader;
    
    private char [] srcBuf; 

    private int srcPos;
    private int srcCount;

    //    private boolean eof;

    private int line;
    private int column;
    
    // txtbuffer

    private char [] txtBuf = new char [128]; 
    private int txtPos;

    // Event-related

    private int type;
    private String text;
    private boolean isWhitespace;
    private String namespace;
    private String prefix;
    private String name;

    private boolean degenerated;
    private int attributeCount;
    private String [] attributes = new String [16];

    private int [] peek = new int [2];
    private int peekCount;
    private boolean wasCR;

    private boolean unresolved; 
    private boolean token;


    public KXmlParser () {
	this (Runtime.getRuntime ().freeMemory () >= 1048576 ? 8192 : 128);
    }


    public KXmlParser (int sz) {
        if (sz > 1) srcBuf = new char [sz];
    }


    private final boolean adjustNsp () throws XmlPullParserException {

	boolean any = false; 


	for (int i = 0; i < attributeCount << 2; i += 4) { 
            // * 4 - 4; i >= 0; i -= 4) {
	    
	    String attrName = attributes [i+2];
	    int cut = attrName.indexOf (':'); 
	    String prefix;

 	    if (cut != -1) {
	 	prefix = attrName.substring (0, cut);
		attrName = attrName.substring (cut+1);
	    }   
	    else if (attrName.equals ("xmlns")) { 
		prefix = attrName;
		attrName = null; 
	    } 
 	    else continue;

 	    if (!prefix.equals ("xmlns")) {
	 	 any = true;
 	    } 
	    else {
		int j = (nspCounts [depth]++) << 1;

		nspStack = ensureCapacity (nspStack, j+2);
		nspStack [j] = attrName;
		nspStack [j+1] = attributes [i+3];
		
		if (attrName != null && attributes [i+3].equals ("")) 
		    exception ("illegal empty namespace");

		//  prefixMap = new PrefixMap (prefixMap, attrName, attr.getValue ());
	    
		//System.out.println (prefixMap);

		if (!reportNspAttr) {
		    System.arraycopy 
			(attributes, i+4, attributes, i, 
			 ((--attributeCount) << 2) -i);

                    i -= 4;
                } 
		else
		    any = true;
	    }
	}


	if (any) {
	    for (int i = (attributeCount << 2) - 4; i >= 0; i -= 4) {

		String attrName = attributes [i+2];
		int cut = attrName.indexOf (':'); 
		
		if (cut == 0)  
		    throw new RuntimeException  
			("illegal attribute name: "+attrName+ " at "+this); 
		
		else if (cut != -1) {		     
		    String attrPrefix = attrName.substring (0, cut); 

		    attrName = attrName.substring (cut+1); 
			
		    String attrNs = getNamespace (attrPrefix);
			
		    if (attrNs == null)  
			throw new RuntimeException  
			    ("Undefined Prefix: "+attrPrefix + " in " + this); 
		    
		    attributes [i] = attrNs;
		    attributes [i+1] = attrPrefix;
		    attributes [i+2] = attrName;

                    for (int j = (attributeCount << 2) - 4; j > i; j -= 4) 
                        if (attrName.equals (attributes [j+2]) 
                            && attrNs.equals (attributes [j])) 
                            exception ("Duplicate Attribute: {"
                                       +attrNs+"}"+attrName);
                    
		} 
	    } 
	} 
	
	int cut = name.indexOf (':');
	
	if (cut == 0)
	    exception ("illegal tag name: "+ name);
	else if (cut != -1) {
	    prefix = name.substring (0, cut); 
	    name = name.substring (cut+1); 
	}
	
	this.namespace = getNamespace (prefix);
	
	if (this.namespace == null) { 
	    if (prefix != null)  
		exception ("undefined prefix: "+prefix);
	    this.namespace = NO_NAMESPACE;
	}

	return any;
    } 
 

    private final String[] ensureCapacity (String [] arr, int required) { 
	if (arr.length >= required) return arr;
	String [] bigger = new String [required + 16];
	System.arraycopy (arr, 0, bigger, 0, arr.length);
	return bigger;
    }


    private final void exception (String desc) throws XmlPullParserException {
	throw new XmlPullParserException (desc, this, null);
    }


    /** common base for next and nextToken. Clears the state, 
	except from txtPos and whitespace. Does not set the type variable */

    private final void nextImpl () throws IOException, XmlPullParserException {

	if (reader == null) exception ("No Input specified");
	
	attributeCount = -1;

	if (degenerated) {
	    degenerated = false;
	    depth--;
	    type = END_TAG;
            return;
	}
	
	prefix = null;
	name = null;
	namespace = null;
	text = null;

	type = peekType ();

	switch (type) {

	case ENTITY_REF:
	    pushEntity ();
	    break;

	case START_TAG: 
	    parseStartTag (); 
	    break;

	case END_TAG: 
	    parseEndTag (); 
	    break;

	case END_DOCUMENT:
	    break;

	case TEXT: 
	    pushText ('<', !token); 
	    if (depth == 0) {
		if (isWhitespace) 
		    type = IGNORABLE_WHITESPACE;
		// make exception switchable for instances.chg... !!!!
		//	else 
		//    exception ("text '"+getText ()+"' not allowed outside root element");
	    }
	    break;

	case CDSECT:
	    parseLegacy (true);
	    break;

	default:
	    type = parseLegacy (token);
	}
    }


    private final int parseLegacy (boolean push) throws IOException, XmlPullParserException {
	
	String req = "";
	int term;
	int result;

	read (); // <
	int c = read ();
	
	if (c == '?') { 
	    term = '?';
	    result = PROCESSING_INSTRUCTION;
	}
	else if (c == '!') {
	    if (peek (0) == '-') {
		result = COMMENT;
		req = "--";
		term = '-'; 
	    }
            else if (peek (0) == '[') {
                result = CDSECT;
                req = "[CDATA[";
                term = ']';
            }
	    else { 
	        result = DOCDECL;
	        req = "DOCTYPE";
	        term = -1;
	    }
	}
	else {
            exception ("illegal: <"+c);
            return -1;
        }

	for (int i = 0; i < req.length (); i++) 
	    read (req.charAt (i));
	
	if (result == DOCDECL) 
	    parseDoctype (push);
	else {
	    while (true) {
		c = read ();
		if (c == -1) exception (UNEXPECTED_EOF);

		if (push) push (c);
		
		if ((term == '?' || c == term)
		    && peek (0) == term && peek (1) == '>') break;
	    }
	    read ();
	    read ();
	    
	    if (push && term != '?') 
		txtPos--;
	    
	}
	return result;
    }
    

    /** precondition: &lt! consumed */ 
    
    
    private final void parseDoctype (boolean push)
	throws IOException, XmlPullParserException {
	
	int nesting = 1;
	boolean quoted = false;

	while (true) {
	    int i = read ();
	    switch (i) {

	    case -1:
	        exception (UNEXPECTED_EOF);
		
	    case '\'': quoted = !quoted; break;

	    case '<': 
		if (!quoted) nesting++;
		break;
		
	    case '>':
		if (!quoted) {
		    if ((--nesting) == 0) 
			return;
		}
		break;
	    }
	    if (push) push (i);
	}
    }
    
    
    /* precondition: &lt;/ consumed */

    private final void parseEndTag () throws IOException, XmlPullParserException {

	if (depth == 0) 
	    exception ("element stack empty");
	
	read ();  // '<'
	read ();  // '/'
	name = readName ();

	int sp = (--depth) << 2;

	if (!name.equals(elementStack [sp+3])) 
	    exception ("expected: "+elementStack [depth]);

	skip ();
	read ('>');

	namespace = elementStack [sp];
	prefix = elementStack [sp+1];
	name = elementStack [sp+2];
    }


    
    
    private final int peekType () throws IOException {
	switch (peek (0)) {
	case -1: 
	    return END_DOCUMENT;
	case '&': 
	    return ENTITY_REF;
	case '<':
	    switch (peek (1)) {
	    case '/': return END_TAG;
	    case '[': return CDSECT;
	    case '?': 
	    case '!': return LEGACY;
	    default: 
		return START_TAG;
	    }
	default:
	    return TEXT;
	}
    }

    
    private final String get (int pos) {
	return new String (txtBuf, pos, txtPos - pos);
    }

    /*
    private final String pop (int pos) {
	String result = new String (txtBuf, pos, txtPos - pos);
	txtPos = pos;
	return result;
    }
    */

    private final void push (int c) {
        if ((c == '\r' || c == '\n') 
            && (!token || type == START_TAG)) {

            if (c == '\n' && wasCR) {
                wasCR = false;
                return;
            }

            wasCR = c == '\r';
            c = type == START_TAG ? ' ' : '\n';
        }
        else wasCR = false;

        isWhitespace &= c <= ' ';

	if (txtPos == txtBuf.length) {
	    char[] bigger = new char [txtPos * 4 / 3 + 4];
	    System.arraycopy (txtBuf, 0, bigger, 0, txtPos);
	    txtBuf = bigger;
	}
	
	txtBuf [txtPos++] = (char) c;
    }


    /** Sets name and attributes */
    
    private final void parseStartTag () throws IOException, XmlPullParserException  {
	
	read (); // <
	name = readName ();
        attributeCount = 0;
	
	
	while (true) { 
	    skip ();
	    
	    int c = peek (0);
	    
	    if (c == '/') {
		degenerated = true;
		read ();
		skip ();
		read ('>');
		break;
	    }
	    
	    if (c == '>') { 
		read ();
		break;
	    }
	    
	    if (c == -1) 
		exception (UNEXPECTED_EOF); 
	    
	    String attrName = readName ();
	    
	    if (attrName.length() == 0)
		exception ("attr name expected");
	    
	    skip ();
	    read ('='); 
	    skip ();
	    int delimiter = read ();
	    
	    if (delimiter != '\'' && delimiter != '"') {
		if (!relaxed)
		    exception
			("<" + name + ">: invalid delimiter: " 
			 + (char) delimiter);  
		
		delimiter = ' ';
	    }
	    
	    int i = (attributeCount++) << 2;
	    
	    attributes = ensureCapacity (attributes, i + 4); 

	    attributes [i++] = "";
	    attributes [i++] = null;
	    attributes [i++] = attrName;

	    int p = txtPos;
	    pushText (delimiter, true);

	    attributes [i] = get (p);
	    txtPos = p;

	    if (delimiter != ' ')
		read ();  // skip endquote
	}       


	int sp = depth++ << 2;

	elementStack = ensureCapacity (elementStack, sp + 4);
	elementStack [sp+3] = name;

	if (depth >= nspCounts.length) {
	    int [] bigger = new int [depth + 4];
	    System.arraycopy (nspCounts, 0, bigger, 0, nspCounts.length);
	    nspCounts = bigger;
	}

	nspCounts [depth] = nspCounts [depth-1];

	for (int i = attributeCount-1; i > 0; i--) {
	    for (int j = 0; j < i; j++) {
		if (getAttributeName (i).equals (getAttributeName (j)))
		    exception
			("Duplicate Attribute: "+getAttributeName (i));
	    }
	}

	if (processNsp) 
	    adjustNsp ();
	else 
	    namespace = "";


	elementStack [sp] = namespace;
	elementStack [sp+1] = prefix;
	elementStack [sp+2] = name;
    }
    

    /** result: isWhitespace; if the setName parameter is set,
	the name of the entity is stored in "name" */

    private final void pushEntity () throws IOException, XmlPullParserException{

	read (); // &
	
	int pos = txtPos;

	while (true) {
	    int c = read ();
	    if (c == ';') break; 
	    if (c == -1) exception (UNEXPECTED_EOF);
	    push (c);
	}
	
	String code = get (pos);
	txtPos = pos;
	if (token && type == ENTITY_REF) name = code;

	if (code.charAt (0) == '#') {
	    int c = (code.charAt (1) == 'x' 
		     ? Integer.parseInt (code.substring (2), 16) 
		     : Integer.parseInt (code.substring (1)));
	    push (c);
            return;
	}

	String result = (String) entityMap.get (code);

	unresolved = result == null;

        if (unresolved) {
            if (!token) exception ("unresolved: &"+code+";");
        }
        else {
            for (int i = 0; i < result.length (); i++) 
                push (result.charAt (i));
        }
    }
	
    
    /** types:
	'<': parse to any token (for nextToken ())
	'"': parse to quote
	' ': parse to whitespace or '>'
    */



    private final void pushText (int delimiter, 
                                 boolean resolveEntities) throws IOException, XmlPullParserException {
		
	int next = peek (0);
	
	while (next != -1 && next != delimiter) { // covers eof, '<', '"' 
	    
	    if (delimiter == ' ') 
		if (next <= ' ' || next == '>') break;
		
	    if (next == '&') {
		if (!resolveEntities) break;
		
		pushEntity ();
	    }
	    else 
		push (read ());
	    

	    next = peek (0);
	}
    }    
    

    private final void read (char c) throws IOException, XmlPullParserException {
        int a = read ();
        if (a != c)
	    exception ("expected: '"+c+"' actual: '"+((char)a)+"'");
    }

    
    private final int read () throws IOException {
	int result = peekCount == 0 ? peek (0) : peek [0];
	peek [0] = peek [1];
	peekCount--;

	column++;

	if (result == '\n') {

            line++;
            column = 1;
	}


	return result;
    }


    private final int peek (int pos) throws IOException {

	while (pos >= peekCount) {

	    int nw;

	    if (srcBuf.length <= 1) 
		nw = reader.read ();
	    else if (srcPos < srcCount) 
		nw = srcBuf [srcPos++];
	    else {
		srcCount = reader.read (srcBuf, 0, srcBuf.length);
		if (srcCount <= 0) 
		    nw = -1;
		else 
		    nw = srcBuf [0]; 
		
		srcPos = 1;
	    }

            /*
            if ((nw == '\n' || nw == '\r') 
                && (type == START_TAG || !token)) {

                if (!wasCR || nw == '\r')
                    peek [peekCount++] = (type == START_TAG) 
                        ? ' ' : '\n';

                wasCR = nw == '\r';
            }
	    else {
            */

            peek [peekCount++] = nw;

                //	wasCR = false;
                //  }
	}

	return peek [pos];
    }


    
    private final String readName () throws IOException, 
    XmlPullParserException {

	int pos = txtPos;
	int c = peek (0);
	if ((c < 'a' || c > 'z')
	    && (c < 'A' || c > 'Z')
	    && c != '_' && c != ':')
	    exception ("name expected");

	do {
	    push (read ());
	    c = peek (0);
	}
	while ((c >= 'a' && c <= 'z')
	       || (c >= 'A' && c <= 'Z')
	       || (c >= '0' && c <= '9')
	       || c == '_' || c == '-'
	       || c == ':' || c == '.');
	
	String result = get (pos);
	txtPos = pos;
	return result; 
    }
    
  

    private final void skip () throws IOException {
	
	while (true) {
	    int c = peek (0);
	    if (c > ' ' || c == -1) break; 
	    read ();
	}
    }
    

 
 
    //--------------- public part starts here... ---------------
    
    
    public void setInput (Reader reader)  throws XmlPullParserException{
	this.reader = reader;

	line = 1;
	column = 0;
	type = START_DOCUMENT;
	name = null;
	namespace = null;
	degenerated = false;
        attributeCount = -1;

	if (reader == null) return;

	srcPos = 0;
	srcCount = 0;
	peekCount = 0;
	depth = 0;

	entityMap = new Hashtable ();
	entityMap.put ("amp", "&");
	entityMap.put ("apos", "'");
	entityMap.put ("gt", ">");
	entityMap.put ("lt", "<");
	entityMap.put ("quot", "\"");
    }

    
    public boolean getFeature (String feature) {
	if (XmlPullParser.FEATURE_PROCESS_NAMESPACES.equals 
	    (feature))
	    return processNsp;
	else if (XmlPullParser.FEATURE_REPORT_NAMESPACE_ATTRIBUTES.equals 
		 (feature)) 
		 return reportNspAttr;
	else return false;
    }
 

    public void defineEntityReplacementText (String entity, String value) 
	throws XmlPullParserException {
	entityMap.put (entity, value);
    }


    public Object getProperty (String property) {
	return null;
    }

     
    public int getNamespaceCount (int depth) {
	if (depth > this.depth) throw new IndexOutOfBoundsException ();
	return nspCounts [depth];
    }

    
    public String getNamespacePrefix (int pos) {
	return nspStack [pos << 1];
    }

    
    public String getNamespaceUri(int pos) {
	return nspStack [(pos << 1) + 1];
    }
    
    
    public String getNamespace (String prefix) {

	if ("xml".equals (prefix)) return "http://www.w3.org/XML/1998/namespace";
	if ("xmlns".equals (prefix)) return "http://www.w3.org/2000/xmlns/";

	for (int i = (getNamespaceCount (getDepth ()) << 1) - 2; i >= 0; i -= 2) {
	    if (prefix == null) {
		if (nspStack [i] == null) return nspStack [i+1];
	    }
	    else if (prefix.equals (nspStack [i])) return nspStack [i+1];
	}
	return null;
    }
    
    
    public int getDepth() {
	return depth;
    }
    
    
    public String getPositionDescription () {
	
	StringBuffer buf = new StringBuffer 
            (type < TYPES.length ? TYPES [type] : "unknown");
	buf.append (' ');
	
	if (type == START_TAG || type == END_TAG) {	    
	    if (degenerated) buf.append ("(empty) ");
	    buf.append ('<');
	    if (type == END_TAG) 
		buf.append ('/');

	    if (prefix != null)
		buf.append ("{"+namespace+"}"+prefix+":");
	    buf.append (name);
	    
	    int cnt = attributeCount << 2;
	    for (int i = 0; i < cnt; i += 4) {
		buf.append (' ');
		if (attributes [i+1] != null) 
		    buf.append ("{" +attributes [i]+"}"
				+attributes[i+1]+":");
		buf.append (attributes [i+2]
			    +"='"+attributes [i+3]+"'");
	    }


	    buf.append ('>');
	}
	else if (type == IGNORABLE_WHITESPACE);
	else if (type != TEXT)
	    buf.append (getText ());
	else if (isWhitespace)
	    buf.append ("(whitespace)");
	else {
	    String text = getText ();
	    if (text.length() > 16) 
		text = text.substring (0, 16)+"...";
	    buf.append (text);
	}
    
	buf.append (" @"+line+":"+column);
	return buf.toString ();
    }
    
    
    public int getLineNumber() {
	return line;
    }
    
    public int getColumnNumber() {
	return column;
    }     
    
    
    public boolean isWhitespace() throws XmlPullParserException {
	if (type != TEXT && type != IGNORABLE_WHITESPACE && type != CDSECT) 
	     exception (ILLEGAL_TYPE);
	return isWhitespace;
    }
    
    
    public String getText () {
	return type < TEXT || (type == ENTITY_REF && unresolved) 
            ? null : get (0);
    }
    
    public char[] getTextCharacters (int [] poslen) {
	if (type >= TEXT) {
            if (type == ENTITY_REF) {
                poslen [0] = 0;
                poslen [1] = name.length ();
                return name.toCharArray ();
            }
            poslen [0] = 0;
            poslen [1] = txtPos;
            return txtBuf;
        }
        
        poslen [0] = -1;
        poslen [1] = -1;
        return null;
    }



    public String getNamespace () {
	return namespace;
    }

    
    public String getName() {
	return name;
    }
    
    
    public String getPrefix() {
	return prefix;
    }

    
    public boolean isEmptyElementTag() throws XmlPullParserException {
	if (type != START_TAG) exception (ILLEGAL_TYPE);
	return degenerated;
    }
    

    public int getAttributeCount() {
	return attributeCount;
    }
    

    public String getAttributeNamespace (int index) {
	if (index >= attributeCount) throw new IndexOutOfBoundsException ();
	return attributes [index << 2];
    }
    
 
    public String getAttributeName(int index) {
	if (index >= attributeCount) throw new IndexOutOfBoundsException ();
	return attributes [(index << 2) + 2];
    }
    

    public String getAttributePrefix(int index){
	if (index >= attributeCount) throw new IndexOutOfBoundsException ();
	return attributes [(index << 2) + 1];
    }
    
    
    public String getAttributeValue(int index) {
	if (index >= attributeCount) throw new IndexOutOfBoundsException ();
	return attributes [(index << 2) + 3];
    }

    
    public String getAttributeValue(String namespace,
                                    String name) {

	for (int i = (attributeCount << 2)-4; i >= 0; i -= 4) {
	    if (attributes [i+2].equals (name) 
		&& (namespace == null || attributes [i].equals (namespace)))
		return attributes [i+3];
	}
	
	return null;
    }


    public int getEventType() throws XmlPullParserException {
	return type;
    }



    public int next() throws XmlPullParserException, IOException {

	txtPos = 0;
	isWhitespace = true;
	int minType = 9999;
	token = false;

	do {
	    nextImpl ();
            if (type < minType) minType = type;
            //	    if (curr <= TEXT) type = curr; 
	}
	while (minType > TEXT || (minType == TEXT && peekType () >= TEXT));

        //        if (type > TEXT) type = TEXT;
        type = minType;

	return type;
    }



    public int nextToken() throws XmlPullParserException, IOException {
	
	isWhitespace = true;
	txtPos = 0;

        token = true;
        nextImpl ();
	return type; 
    }
	

    //-----------------------------------------------------------------------------
    // utility methods to mak XML parsing easier ...

    /**
     * test if the current event is of the given type and if the
     * namespace and name do match. null will match any namespace
     * and any name. If the current event is TEXT with isWhitespace()=
     * true, and the required type is not TEXT, next () is called prior
     * to the test. If the test is not passed, an exception is
     * thrown. The exception text indicates the parser position,
     * the expected event and the current event (not meeting the
     * requirement.
     *
     * <p>essentially it does this
     * <pre>
     *  if (getType() == TEXT && type != TEXT && isWhitespace ())
     *    next ();
     *
     *  if (type != getType
     *  || (namespace != null && !namespace.equals (getNamespace ()))
     *  || (names != null && !name.equals (getName ())
     *     throw new XmlPullParserException ( "....");
     * </pre>
     */
    public void require (int type, String namespace, String name)
        throws XmlPullParserException, IOException {

	if (this.type == TEXT && type != TEXT && isWhitespace ())
	    next ();
     
	if (type != this.type
	    || (namespace != null && !namespace.equals (getNamespace ()))
	    || (name != null && !name.equals (getName ())))
	    exception ("expected: "+TYPES[type]+" {"+namespace+"}"+name);
    }

    /**
     * If the current event is text, the value of getText is
     * returned and next() is called. Otherwise, an empty
     * String ("") is returned. Useful for reading element
     * content without needing to performing an additional
     * check if the element is empty.
     *
     * <p>essentially it does this
     * <pre>
     *   if (getType != TEXT) return ""
     *    String result = getText ();
     *    next ();
     *    return result;
     *  </pre>
     */
 
    public String readText () throws XmlPullParserException, IOException {
	
	if (type != TEXT) return "";

	String result = getText ();
	next ();
	return result;
    }


   
    public void setFeature (String feature, boolean value) 
	throws XmlPullParserException {
	if (XmlPullParser.FEATURE_PROCESS_NAMESPACES.equals (feature))
	    processNsp = value;
	else if (XmlPullParser.FEATURE_REPORT_NAMESPACE_ATTRIBUTES.equals 
		 (feature)) reportNspAttr = value;
	else exception
	    ("unsupported feature: "+feature);
    }


    public void setProperty (String property, Object value) throws XmlPullParserException {
	throw new XmlPullParserException 
	    ("unsupported property: "+property);
    }
}
