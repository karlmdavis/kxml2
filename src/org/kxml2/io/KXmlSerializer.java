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

	private String[] elementStack = new String[12];  //nsp/prefix/name
	private int[] nspCounts = new int[4];
	private int auto;
	private String[] nspStack = new String[8];  //prefix/nsp

	private final void check() throws IOException {
		if (!pending) return;
		writer.write(">");
		pending = false;
		
		if (nspCounts.length < depth+2) {
			int [] hlp = new int [depth+5];
			System.arraycopy(nspCounts, 0, hlp, 0, depth+1);
			nspCounts = hlp;
		}
		
		nspCounts [depth+1] = nspCounts[depth];
	}

	private final void writeEscaped(String s, int quot) throws IOException {
		StringBuffer buf = new StringBuffer();
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			switch (c) {
				case '&' :
					writer.write("&amp");
					break;
				case '>' :
					writer.write("&gt");
					break;
				case '<' :
					writer.write("&lt");
					break;
				case '"' :
				case '\'' :
					if (c == quot) {
						writer.write(c == '"' ? "&quot" : "&apos");
						break;
					}
				default :
					writer.write(c);
			}
		}
	}

/*
	private final void writeIndent() throws IOException {
		writer.write("\r\n");
		for (int i = 0; i < depth; i++)
			writer.write(' ');
	}*/

	public KXmlSerializer () {
		nspCounts [0] = 1;
		nspCounts [1] = 1;
		nspStack [1] = "";	
	}

	public void docdecl(String dd) {
		throw new RuntimeException("NYI");
	}

	public void endDocument() throws IOException {
		check();
	}

	public void entityRef(String name) throws IOException {
		check();
		writer.write('&');
		writer.write(name);
		writer.write(';');
	}

	public boolean getFeature(String name) {
		return false;
	}

	private final String getPrefix (String namespace) {
		for (int i = nspCounts [depth] * 2 - 2; i >= 0; i -= 2) {
			if (nspStack[i+1].equals(namespace)) return nspStack[i];
		}

		depth--;
		String prefix = "n"+(auto++);
		setPrefix (prefix, namespace);
		depth++;
		return prefix;
	}

	public Object getProperty(String name) {
		throw new RuntimeException("Unsupported property");
	}

	public void ignorableWhitespace(String s) throws IOException {
		check ();
		writer.write (s);
	}

	public void setFeature(String name, boolean value) {
		throw new RuntimeException("Unsupported Feature");
	}

	public void setProperty(String name, Object value) {
		throw new RuntimeException("Unsupported Property:" + value);
	}


	public void setPrefix(String prefix, String namespace) {
	
		int pos = (nspCounts [depth+1]++) << 1;
		
		if (nspStack.length < pos+1) {
			String [] hlp = new String [nspStack.length + 16];
			System.arraycopy(nspStack, 0, hlp, 0, pos);
			nspStack = hlp;		
		}
		
		nspStack [pos++] = prefix;
		nspStack [pos] = namespace;
	}


	public void setOutput(Writer writer) {
		this.writer = writer;
	}

	public void setOutput(OutputStream os, String encoding)
		throws IOException {
		this.writer =
			encoding == null
				? new OutputStreamWriter(os)
				: new OutputStreamWriter(os, encoding);
	}

	public void startDocument(String encoding, Boolean standalone)
		throws IOException {
		writer.write("");
	}

	public void startTag(String namespace, String name) throws IOException {
		check();
		//	if (indent) writeIndent ();

		int esp = depth*3;

		if (elementStack.length < esp+3) {
			String [] hlp = new String [elementStack.length + 12];
			System.arraycopy(elementStack, 0, hlp, 0, esp);
			elementStack = hlp;
		}

		depth++;
					
		String prefix = getPrefix(namespace);

		elementStack[esp++] = namespace;
		elementStack[esp++] = prefix;
		elementStack[esp] = name;

		writer.write('<');
		if (prefix != null) {
			writer.write(prefix);
			writer.write(':');
		}

		writer.write(name);

		for (int i=nspCounts[depth-1]; i < nspCounts[depth]; i++) {
			writer.write (' ');
			writer.write ("xmlns");
			if (nspStack [i*2] != null) {
				writer.write (':');
				writer.write(nspStack[i*2]);
			}
			writer.write ("=\"");
			writeEscaped (nspStack[i*2+1], '"');
			writer.write ('"');
		}

		pending = true;
	}

	public void attribute(String namespace, String name, String value)
		throws IOException {
		if (!pending)
			throw new RuntimeException("illegal position for attribute");

		int cnt = nspCounts [depth];

		String prefix = getPrefix(namespace);
		
		if (cnt != nspCounts [depth]) {
			writer.write (' ');
			writer.write ("xmlns");
			if (nspStack [cnt*2] != null) {
				writer.write (':');
				writer.write(nspStack[cnt*2]);
			}
			writer.write ("=\"");
			writeEscaped (nspStack[cnt*2+1], '"');
			writer.write ('"');
		}

		writer.write(' ');
		if (prefix != null) {
			writer.write(prefix);
			writer.write(':');
		}
		writer.write(name);
		writer.write('=');
		char q = value.indexOf('"') == -1 ? '"' : '\'';
		writer.write(q);
		writeEscaped(value, '"');
		writer.write(q);
	}

	public void flush() throws IOException {
		check();
		writer.flush();
	}
/*
	public void close() throws IOException {
		check();
		writer.close();
	}
*/
	public void endTag(String namespace, String name) throws IOException {
		depth--;

		if (!elementStack [depth * 3].equals (namespace) 
			|| !elementStack [depth * 3+2].equals (name)) 
				throw new RuntimeException ("start/end tag mismatch");
		
		if (pending) {
			writer.write(" />");
			pending = false;
		}
		else {
			writer.write ("</");
			String prefix = elementStack [depth*3+1];
			if (prefix != null) {
				writer.write (prefix);
				writer.write (':');
			}
			writer.write (name);
			writer.write ('>');
		}

		nspCounts [depth+1] = nspCounts[depth];
	}

	public void text(String text) throws IOException {
		check();
		writeEscaped(text, -1);
	}

	public void text(char[] text, int start, int len) throws IOException {
		text (new String (text, start, len));
	}

	public void cdsect(String data) throws IOException {
		check ();
		writer.write ("<![CDSECT");
		writer.write (data);
		writer.write ("]>");		
	}

	public void comment(String comment) throws IOException {
		check ();
		writer.write ("<!--");
		writer.write (comment);
		writer.write ("-->");
	}

	public void processingInstruction(String pi) throws IOException {
		check ();
		writer.write ("<?");
		writer.write (pi);
		writer.write ('>');
	}
}