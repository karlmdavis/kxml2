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
 *                 Eric Foster-Johnson, Michael Angel
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

    // source

    private Reader reader;
    
    private char [] srcBuf; 

    private int srcPos;
    private int srcCount;

    private boolean eof;

    private int line;
    private int column;
    
    private int peek0;
    private int peek1;
    
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


    public KXmlParser () {
	this (Runtime.getRuntime ().freeMemory () >= 1048576 ? 8192 : 128);
    }


    public KXmlParser (int sz) {
        if (sz > 1) srcBuf = new char [sz];
    }


    private final void adjustNsp () throws XmlPullParserException {

	boolean any = false; 

	// countdown avoids problems with index when removing xmlns attrs

	for (int i = attributeCount * 4 - 4; i >= 0; i -= 4) {
	    
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
	 	if (!prefix.equals ("xml")) any = true;
 	    } 
	    else {
		int j = (nspCounts [depth]++) << 1;

		nspStack = ensureCapacity (nspStack, j+2);
		nspStack [j] = attrName;
		nspStack [j+1] = attributes [i+3];

		//  prefixMap = new PrefixMap (prefixMap, attrName, attr.getValue ());
	    
		//System.out.println (prefixMap);

		if (!reportNspAttr)
		    System.arraycopy 
			(attributes, i+4, attributes, i, 
			 ((--attributeCount) << 2) -i); 
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
		    if (!attrPrefix.equals ("xml")) { 
			attrName = attrName.substring (cut+1); 
			
			String attrNs = getNamespace (attrPrefix);
			
			if (attrNs == null)  
			    throw new RuntimeException  
				("Undefined Prefix: "+attrPrefix + " in " + this); 
			
			attributes [i] = attrNs;
			attributes [i+1] = attrPrefix;
			attributes [i+2] = attrName;
		    }
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
    } 
 

    private final String[] ensureCapacity (String [] arr, int required) { 
	if (arr.length >= required) return arr;
	String [] bigger = new String [required + 16];
	System.arraycopy (arr, 0, bigger, 0, arr.length);
	return bigger;
    }


    private final void exception (String desc) throws XmlPullParserException {
	throw new XmlPullParserException 
	    (desc + " pos: " +getPositionDescription ());
    }


    /** common base for next and nextToken. Clears the state, 
	except from txtPos and whitespace. Does not set the type variable */

    private final int nextImpl (boolean token) throws IOException, XmlPullParserException {
	
	attributeCount = 0;

	if (degenerated) {
	    degenerated = false;
	    depth--;
	    return END_TAG;
	}
	
	prefix = null;
	name = null;
	namespace = "";
	text = null;

	int type = peekType ();

	switch (type) {

	case ENTITY_REF:
	    isWhitespace &= pushEntity (true);
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
	    isWhitespace &= pushText ('<', !token); 
	    if (depth == 0) {
		if (isWhitespace) 
		    type = IGNORABLE_WHITESPACE;
		else 
		    exception ("text not allowed outside root element");
	    }
	    break;

	case CDSECT:
	    parseLegacy (true);
	    isWhitespace = false;
	    break;

	default:
	    type = parseLegacy (token);
	}

	return type;
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
	    if (peek0 == '-') {
		result = COMMENT;
		req = "--";
		term = '-'; 
	    }
	    else {
		result = DOCDECL;
		req = "DOCTYPE";
		term = -1;
	    }
	}
	else {
	    if (c != '[') exception ("illegal: <"+c);
	    result = CDSECT;
	    req = "CDATA[";
	    term = ']';
	}


	for (int i = 0; i < req.length (); i++) 
	    read (req.charAt (i));
	
	if (result == DOCDECL) 
	    parseDoctype (push);
	else {
	    while (true) {
		if (eof) exception (UNEXPECTED_EOF);
		
		c = read ();
		if (push) push (c);
		
		if ((term == '?' || c == term)
		    && peek0 == term && peek1 == '>') break;
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
	
	while (true) {
	    int i = read ();
	    switch (i) {

	    case -1:
	        exception (UNEXPECTED_EOF);
		
	    case '<': 
		nesting++;
		break;
		
	    case '>':
		if ((--nesting) == 0) 
		    return;
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


    
    
    private final int peekType () {
	switch (peek0) {
	case -1: 
	    return END_DOCUMENT;
	case '&': 
	    return ENTITY_REF;
	case '<':
	    switch (peek1) {
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
	if (c == 0) return;

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

	while (true) { 
	    skip ();
	    
	    int c = peek0;
	    
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

	if (processNsp)
	    adjustNsp ();
	
	elementStack [sp] = namespace;
	elementStack [sp+1] = prefix;
	elementStack [sp+2] = name;
    }
    

    /** result: isWhitespace; if the setName parameter is set,
	the name of the entity is stored in "name" */

    private final boolean pushEntity (boolean setName) throws IOException, XmlPullParserException{

	read (); // &
	
	int pos = txtPos;

	while (!eof && peek0 != ';') 
	    push (read ());
	
	String code = get (pos);
	txtPos = pos;
	if (setName) name = code;

	read (); 
	
	if (code.charAt (0) == '#') {
	    int c = (code.charAt (1) == 'x' 
		     ? Integer.parseInt (code.substring (2), 16) 
		     : Integer.parseInt (code.substring (1)));
	    push (c);
	    return c <= ' ';
	}

	String result = (String) entityMap.get (code);
	boolean whitespace = true;

	if (result == null) result = "&"+code+";";

	for (int i = 0; i < result.length (); i++) {
	    char c = result.charAt (i);
	    if (result.charAt (i) > ' ') 
		whitespace = false;
	    push (c);
	}

	return whitespace;
    }
	
    
    /** types:
	'<': parse to any token (for nextToken ())
	'"': parse to quote
	' ': parse to whitespace or '>'
    */



    private final boolean pushText (int delimiter, 
				    boolean resolveEntities) throws IOException, XmlPullParserException {
		
	boolean whitespace = true;
	int next = peek0;
	
	while (!eof && next != delimiter) { // covers eof, '<', '"' 
	    
	    if (delimiter == ' ') 
		if (next <= ' ' || next == '>') break;
		
	    if (next == '&') {
		if (!resolveEntities) break;
		
		if (!pushEntity (false))
		    whitespace = false;
	
	    }
	    else {
		if (next > ' ') 
		    whitespace = false;

		push (read ());
	    }

	    next = peek0;
	}
	
	return whitespace;
    }    
    

    private final void read (char c) throws IOException, XmlPullParserException {
	if (read () != c)
	    exception ("expected: '"+c+"'");
    }


    private final int read () throws IOException {

	int r = peek0;
	peek0 = peek1;

	if (peek0 == -1) {
	    eof = true;
	    return r;
	}
	else if (r == '\n' || r == '\r') {
	    line++;
	    column = 0;
	    if (r == '\r' && peek0 == '\n')
		peek0 = 0;
	}
	column++;

        if (srcBuf.length <= 1) {
            peek1 = reader.read ();
            return r;
        }
            

	if (srcPos >= srcCount) {

            srcCount = reader.read (srcBuf, 0, srcBuf.length);
            if (srcCount <= 0) {
                peek1 = -1;
                return r;
            }
            srcPos = 0;
	}
	
	peek1 = srcBuf [srcPos++];
	return r;
    }



    
    private final String readName () throws IOException, 
    XmlPullParserException {

	int pos = txtPos;
	int c = peek0;
	if ((c < 'a' || c > 'z')
	    && (c < 'A' || c > 'Z')
	    && c != '_' && c != ':')
	    exception ("name expected");

	do {
	    push (read ());
	    c = peek0;
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
	
	while (!eof && peek0 <= ' ') 
	    read ();
    }
    

 
 
    //--------------- public part starts here... ---------------
    
    
    public void setInput (Reader reader)  throws XmlPullParserException{
	this.reader = reader;
	
	if (reader == null) return;

	try {
	    peek0 = reader.read ();
	    peek1 = reader.read ();
	}
	catch (IOException e) {
	    throw new XmlPullParserException (e.toString (), e);
	}

	eof = peek0 == -1;

	entityMap = new Hashtable ();
	entityMap.put ("amp", "&");
	entityMap.put ("apos", "'");
	entityMap.put ("gt", ">");
	entityMap.put ("lt", "<");
	entityMap.put ("quot", "\"");

	line = 1;
	column = 1;
    }

    
    public boolean getFeature (String feature) {
	if ("http://xmlpull.org/v1/features/process-namespaces".equals 
	    (feature))
	    return processNsp;
	else return false;
    }
 

    public void defineCharacterEntity (String entity, String value) 
	throws XmlPullParserException {
	entityMap.put (entity, value);
    }


    public Object getProperty (String property) {
	return null;
    }

     
    public int getNamespaceCount (int depth) throws XmlPullParserException {
	if (depth > this.depth) throw new IndexOutOfBoundsException ();
	return nspCounts [depth];
    }

    
    public String getNamespacePrefix (int pos) throws XmlPullParserException  {
	return nspStack [pos << 1];
    }

    
    public String getNamespaceUri(int pos) throws XmlPullParserException {
	return nspStack [(pos << 1) + 1];
    }
    
    
    public String getNamespace (String prefix) throws XmlPullParserException {
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
	
	StringBuffer buf = new StringBuffer (TYPES [type]);
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
	return isWhitespace;
    }
    
    
    public String getText () {
	return get (0);
    }
    
    public char[] getTextCharacters (int [] poslen) {
	poslen [0] = 0;
	poslen [1] = txtPos;
	return txtBuf;
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

    
    public boolean isEmptyElementTag() {
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
	return attributes [(index << 2) + 2];
    }

    
    public String getAttributeValue(String namespace,
                                    String name) {

	for (int i = (attributeCount << 2)-4; i >= 0; i -= 4) {
	    if (attributes [i+2].equals (name) 
		&& attributes [i].equals (namespace))
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
	int curr;
	
	do {
	    curr = nextImpl (false);
	    if (curr <= TEXT) type = curr; 
	}
	while (curr > TEXT || curr == TEXT && peekType () >= TEXT);

	return type;
    }



    public int nextToken() throws XmlPullParserException, IOException {
	
	isWhitespace = true;
	txtPos = 0;

	return type = nextImpl (true);
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
	if ("http://xmlpull.org/v1/features/process-namespaces".equals 
	    (feature))
	    processNsp = value;
	else throw new XmlPullParserException 
	    ("unsupported feature: "+feature);
    }


    public void setProperty (String property, Object value) throws XmlPullParserException {
	throw new XmlPullParserException 
	    ("unsupported property: "+property);
    }
}
