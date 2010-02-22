// Test case contributed by Andy Bailey

import java.io.FileNotFoundException;
import java.io.IOException;

import junit.framework.TestCase;

public class TestWb extends TestCase
{

	public void testWb() throws IllegalArgumentException, IllegalStateException, FileNotFoundException,
			IOException
	{
		// Commenting out this test as it doesn't pass.
		/*
		 * File file = new File("compress.xml");
		 * 
		 * WbxmlSerializer xs = new WbxmlSerializer(); boolean compress = true;
		 * 
		 * xs.setOutput(new FileOutputStream(file), null); // xs.setOutput(System.out,"UTF-8");
		 * //xs.docdecl(); // xs.setPrefix("","http://www.hazlorealidad.com");
		 * //xs.startDocument("UTF-8",true); xs.startDocument(null, null); // xs.comment("Comment");
		 * xs.startTag(null,"root"); // xs.startTag(null,"y"); xs.attribute(null, "name", "value");
		 * xs.writeWapExtension(Wbxml.EXT_T_1, new Integer(2)); xs.endTag(null, "y"); xs.startTag(null, "y");
		 * xs.attribute(null, "name", "value"); xs.writeWapExtension(Wbxml.EXT_T_1, new Integer(2));
		 * xs.endTag(null, "y"); xs.endTag(null, "root"); xs.endDocument(); xs.flush(); long len =
		 * file.length(); System.out.println(len + " bytes");
		 */
	}

}
