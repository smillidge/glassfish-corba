/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright (c) 1997-2011 Oracle and/or its affiliates. All rights reserved.
 * 
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * 
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 * 
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 * 
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package com.sun.corba.se.impl.presentation.rmi ;

import java.security.AccessController;
import java.security.PrivilegedAction;

import java.lang.reflect.Method ;
import java.lang.reflect.Proxy ;
import java.lang.reflect.InvocationTargetException ;



import org.omg.CORBA.portable.Delegate ;
import org.omg.CORBA.portable.ServantObject ;
import org.omg.CORBA.portable.ApplicationException ;
import org.omg.CORBA.portable.RemarshalException ;

import org.omg.CORBA.SystemException ;

import com.sun.corba.se.spi.orb.ORB ;


import com.sun.corba.se.spi.transport.ContactInfoList ;

import com.sun.corba.se.spi.protocol.ClientDelegate ;
import com.sun.corba.se.spi.protocol.LocalClientRequestDispatcher ;

import com.sun.corba.se.spi.presentation.rmi.InvocationInterceptor ;
import com.sun.corba.se.spi.presentation.rmi.DynamicMethodMarshaller ;
import com.sun.corba.se.spi.presentation.rmi.PresentationManager ;
import com.sun.corba.se.spi.presentation.rmi.PresentationDefaults ;
import com.sun.corba.se.spi.presentation.rmi.StubAdapter ;

import com.sun.corba.se.impl.javax.rmi.CORBA.Util ;
import com.sun.corba.se.spi.trace.IsLocal;
import org.glassfish.pfl.basic.proxy.LinkedInvocationHandler;
import org.glassfish.pfl.tf.spi.annotation.InfoMethod;

@IsLocal
public final class StubInvocationHandlerImpl implements LinkedInvocationHandler  
{
    private transient PresentationManager.ClassData classData ;
    private transient PresentationManager pm ;
    private transient org.omg.CORBA.Object stub ;
    private transient Proxy self ;

    public void setProxy( Proxy self )
    {
	this.self = self ;
    }

    public Proxy getProxy()
    {
	return self ;
    }

    public StubInvocationHandlerImpl( PresentationManager pm,
	PresentationManager.ClassData classData, org.omg.CORBA.Object stub ) 
    {
        if (!PresentationDefaults.inAppServer()) {
            SecurityManager s = System.getSecurityManager();
            if (s != null) {
                s.checkPermission(new DynamicAccessPermission("access"));
            }
        }

	this.classData = classData ;
	this.pm = pm ;
	this.stub = stub ;
    }

    @IsLocal
    private boolean isLocal(Delegate delegate)
    {
	boolean result = false ;
	if (delegate instanceof ClientDelegate) {
	    ClientDelegate cdel = (ClientDelegate)delegate ;
	    ContactInfoList cil = cdel.getContactInfoList() ;
            LocalClientRequestDispatcher lcrd =
                cil.getLocalClientRequestDispatcher() ;
            result = lcrd.useLocalInvocation( null ) ;
	}
	 
	return result ;
    }
    
    public Object invoke( Object proxy, final Method method,
	Object[] args ) throws Throwable {

	Delegate delegate = null ;
	try {
	    delegate = StubAdapter.getDelegate( stub ) ;
	} catch (SystemException ex) {
	    throw Util.getInstance().mapSystemException(ex) ;
	} 

	org.omg.CORBA.ORB delORB = delegate.orb( stub ) ;
	if (delORB instanceof ORB) {
	    ORB orb = (ORB)delORB ;

	    InvocationInterceptor interceptor = orb.getInvocationInterceptor() ;

	    try {
		interceptor.preInvoke() ;
	    } catch (Exception exc) {
		// XXX Should we log this?
	    }

	    try {
		return privateInvoke( delegate, proxy, method, args ) ;
	    } finally {
		try {
		    interceptor.postInvoke() ;
		} catch (Exception exc) {
		    // XXX Should we log this?
		}
	    }
	} else {
	    // Not our ORB: so handle without invocation interceptor.
	    return privateInvoke( delegate, proxy, method, args ) ;
	}
    }

    @InfoMethod
    private void takingRemoteBranch() {}

    @InfoMethod
    private void takingLocalBranch() {}

    /** Invoke the given method with the args and return the result.
     *  This may result in a remote invocation.
     *  @param proxy The proxy used for this class (null if not using java.lang.reflect.Proxy)
     */
    @IsLocal
    private Object privateInvoke( Delegate delegate, Object proxy, final Method method,
	Object[] args ) throws Throwable
    {
        boolean retry;
	do {
	    retry = false;
	    String giopMethodName = classData.getIDLNameTranslator().
	      getIDLName( method )  ;
	    DynamicMethodMarshaller dmm = 
	      pm.getDynamicMethodMarshaller( method ) ;
	   
	    if (!isLocal(delegate)) {
	        try {
		    takingRemoteBranch() ;
		    org.omg.CORBA_2_3.portable.InputStream in = null ;
		    try {
		        // create request
		        org.omg.CORBA_2_3.portable.OutputStream out = 
			  (org.omg.CORBA_2_3.portable.OutputStream)
			  delegate.request( stub, giopMethodName, true);
			// marshal arguments
			dmm.writeArguments( out, args ) ;
			// finish invocation
			in = (org.omg.CORBA_2_3.portable.InputStream)
			  delegate.invoke( stub, out);
			// unmarshal result
			return dmm.readResult( in ) ;
		    } catch (ApplicationException ex) {
		        throw dmm.readException( ex ) ;
		    } catch (RemarshalException ex) {
		      //return privateInvoke( delegate, proxy, method, args ) ; 
		      retry = true;
		    } finally {
		        delegate.releaseReply( stub, in );
		    }
		} catch (SystemException ex) {
		    throw Util.getInstance().mapSystemException(ex) ;
		} 
	    } else {
	        takingLocalBranch();
	        org.omg.CORBA.ORB orb = delegate.orb( stub ) ;
		ServantObject so = delegate.servant_preinvoke( stub, giopMethodName,
							       method.getDeclaringClass() );
		if (so == null) {
		    //return privateInvoke( delegate, proxy, method, args ) ; 
		    retry = true;
		    continue;
		}

		try {
		    Object[] copies = dmm.copyArguments( args, orb ) ;

		    if (!method.isAccessible()) {	
		        // Make sure that we can invoke a method from a normally
		        // inaccessible package, as this reflective class must always
		        // be able to invoke a non-public method.
		        AccessController.doPrivileged(new PrivilegedAction() {
			    public Object run() {
			        method.setAccessible( true ) ;
				return null ;
			    } 
			} ) ;
		    }

		    Object result = method.invoke( so.servant, copies ) ;

		    return dmm.copyResult( result, orb ) ;
		} catch (InvocationTargetException ex) {
		    Throwable mex = ex.getCause() ;
		    // mex should never be null, as null cannot be thrown
		    Throwable exCopy = (Throwable)Util.getInstance().copyObject(mex,orb);
		    if (dmm.isDeclaredException( exCopy ))
		        throw exCopy ;
		    else
		        throw Util.getInstance().wrapException(exCopy);
		} catch (Throwable thr) {
		    if (thr instanceof ThreadDeath)
		        throw (ThreadDeath)thr ;

		    // This is not a user thrown exception from the
		    // method call, so don't copy it.  This is either
		    // an error or a reflective invoke exception.
		    throw Util.getInstance().wrapException( thr ) ;
		} finally {		 
		    delegate.servant_postinvoke( stub, so);
		}
	    }
	} while (retry);
	return null;
    }
}
