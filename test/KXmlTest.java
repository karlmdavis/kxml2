// this is just a dummy class to avoid circular references in exl

import org.xmlpull.v1.tests.*;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestResult;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

public class KXmlTest extends PackageTests {
   public static Test suite() {
   		return PackageTests.suite ();
   }
	    public static void main (String[] args) {
	    	PackageTests.main (args);
	    }
}