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

/**
 * This exception is thrown by a kxmlrpc component to indicate an error on the remote servern 
 * (a Fault element has been returned by the server). In contrast, client-side errors throw 
 * a java.io.IOException object.
 */

public class XmlRpcException extends Exception {
    
    /**
     * These three fields represent three potential error levels
     */
    static final int NONE = 0;
    static final int RECOVERABLE = 1;
    static final int FATAL = 2;

    /**
     * The fault code of the exception.
     */
    public final int code;

    /**
     * This is the sole constructor for the KxmlRpcException class.
     * @param code an integer representing the error code
     * @param message a String containing the error message
     */    
    public XmlRpcException( int code, String message ) {
	super( message );
	this.code = code;
    }//end KxmlRpcException( int, String )
}//end class KxmlRpcException
