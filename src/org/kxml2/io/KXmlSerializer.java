/* kXML 2
 *
 * Copyright (C) 2000, 2001, 2002 
 *               Stefan Haustein
 *               D-46045 Oberhausen (Rhld.),
 *               Germany. All Rights Reserved.
 *
 * The contents of this file are subject to the "Lesser GNU Public
 * License" (LGPL); you may not use this file except in compliance
 * with the License.
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See
 * the License for the specific terms governing rights and limitations
 * under the License.
 * */

package org.kxml2.io;

import java.io.*;
import org.xmlpull.v1.serializer.*;

public class KXmlSerializer implements XmlSerializer {

    private Writer writer;
    private boolean pending;

    private int depth;
    private String[] elementStack = new String [16];
    private String[] nspStack = new String [8];
    private int[] nspCounts = new int [4];

    private final void check () throws IOException {
	if (pending) writer.write (">");
    }

    private final void writeEscaped (String s, int quot) throws IOException {
	StringBuffer buf = new StringBuffer ();
	for (int i = 0; i < s.length (); i++) {
	    char c = s.charAt (i);
	    switch (c) {
	    case '&': writer.write ("&amp"); break;
	    case '>': writer.write ("&gt"); break;
	    case '<': writer.write ("&lt"); break;
	    case '"':
	    case '\'':
		if (c == quot) {
		    writer.write (c == '"' ? "&quot" : "&apos");
		    break;
		}
	    default: writer.write (c);
	    }
	}
    }

    private final void writeIndent () throws IOException {
	writer.write ("\r\n");
	for (int i = 0; i < depth; i++) writer.write (' '); 
    }

    public void docdecl (String dd) {
	throw new RuntimeException ("NYI");
    }

    public void endDocument () throws IOException {
	check ();
    }

    public void entityRef (String name) throws IOException {
	check ();
	writer.write ('&');
	writer.write (name);
	writer.write (';');
    }

    public boolean getFeature (String name) {
	return false;
    }
    
    public String getPrefix (String namespace, boolean create) {
	if (namespace != null && !namespace.equals ("")) 
	    throw new RuntimeException ("NYI");
	return null;
    }

    public Object getProperty (String name) {
	throw new RuntimeException ("Unsupported property");
    }
	
    public void ignorableWhitespace (String s) {
	throw new RuntimeException ("NYI");
    }

    public void setFeature (String name, boolean value) {
	throw new RuntimeException ("Unsupported Feature");
    }

    public void setProperty (String name, Object value) {
	throw new RuntimeException ("Unsupported Property:" +value);
    }


    public void setPrefix (String prefix, String namespace) {
	throw new RuntimeException ("NYI");
    }


    public void setOutput (Writer writer) {
	this.writer = writer;
    }

    public void setOutput (OutputStream os, String encoding) throws IOException {
	this.writer = new OutputStreamWriter (os, encoding);
    }

    public void startDocument (String encoding, 
			       Boolean standalone) throws IOException {
	writer.write ("");
    }

    public void startTag (String namespace, String name) throws IOException {
	check ();
	//	if (indent) writeIndent ();
	String prefix = getPrefix (namespace, true);

	writer.write ('<');
	if (prefix != null) {
	    writer.write (prefix);
	    writer.write (':');
	}

	writer.write (name);
	pending = true;
	depth ++;
    }


    public void attribute (String namespace, String name, 
			   String value) throws IOException {
	if (!pending) throw new RuntimeException 
	    ("illegal position for attribute");

	String prefix = getPrefix (namespace, true);

	writer.write (' ');
	if (prefix != null) {
	    writer.write (prefix);
	    writer.write (':');
	}
	writer.write (name);
	writer.write ('=');
	char q = value.indexOf ('"') == -1 ? '"' : '\'';
	writer.write (q);
	writeEscaped (value, '"');
	writer.write (q);
    }


    public void flush () throws IOException {
	check ();
	writer.flush ();
    }

    public void close () throws IOException {
	check ();
	writer.close ();
    }

    public void endTag (String namespace, String name) throws IOException {
	if (pending) {
	    depth--;
	    writer.write (" />");
	    pending = false;
	}
	else {
	    String prefix = getPrefix (namespace, false);
	    depth--;
	    writeIndent ();
	}
    }

    public void text (String text) throws IOException {
	check ();
	writeEscaped (text, -1);
    }

    public void text (char [] text, int start, int len) {
	throw new RuntimeException ("NYI");
    }

    public void cdsect (String data) {
	throw new RuntimeException ("NYI");
    }

    public void comment (String comment) throws IOException {
    }

    public void processingInstruction (String pi) {
	throw new RuntimeException ("NYI");
    }
}
