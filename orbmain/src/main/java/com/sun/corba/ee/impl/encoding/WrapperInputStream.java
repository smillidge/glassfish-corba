/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 1997-2018 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://oss.oracle.com/licenses/CDDL+GPL-1.1
 * or LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at LICENSE.txt.
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

package com.sun.corba.ee.impl.encoding;

import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;
import java.util.List;
import java.util.Collections;
import java.util.ArrayList;
import java.io.IOException;

import org.omg.CORBA.TypeCode ;
import org.omg.CORBA.Any ;


import org.omg.CORBA_2_3.portable.InputStream;

import com.sun.corba.ee.impl.corba.TypeCodeImpl;

public class WrapperInputStream extends org.omg.CORBA_2_3.portable.InputStream implements TypeCodeReader
{
    private CDRInputObject stream;
    private Map<Integer,TypeCodeImpl> typeMap = null;
    private int startPos = 0;

    public WrapperInputStream(CDRInputObject s) {
        super();
        stream = s;
        startPos = stream.getPosition();
    }

    public int read() throws IOException { return stream.read(); }
    public int read(byte b[]) throws IOException { return stream.read(b); }
    public int read(byte b[], int off, int len) throws IOException {
        return stream.read(b, off, len);
    }
    public long skip(long n) throws IOException { return stream.skip(n); }
    public int available() throws IOException { return stream.available(); }
    public void close() throws IOException { stream.close(); }
    public void mark(int readlimit) { stream.mark(readlimit); }
    public void reset() { stream.reset(); }
    public boolean markSupported() { return stream.markSupported(); }
    public int getPosition() { return stream.getPosition(); }
    public void consumeEndian() { stream.consumeEndian(); }
    public boolean read_boolean() { return stream.read_boolean(); }
    public char read_char() { return stream.read_char(); }
    public char read_wchar() { return stream.read_wchar(); }
    public byte read_octet() { return stream.read_octet(); }
    public short read_short() { return stream.read_short(); }
    public short read_ushort() { return stream.read_ushort(); }
    public int read_long() { return stream.read_long(); }
    public int read_ulong() { return stream.read_ulong(); }
    public long read_longlong() { return stream.read_longlong(); }
    public long read_ulonglong() { return stream.read_ulonglong(); }
    public float read_float() { return stream.read_float(); }
    public double read_double() { return stream.read_double(); }
    public String read_string() { return stream.read_string(); }
    public String read_wstring() { return stream.read_wstring(); }

    public void read_boolean_array(boolean[] value, int offset, int length) {
        stream.read_boolean_array(value, offset, length);
    }
    public void read_char_array(char[] value, int offset, int length) {
        stream.read_char_array(value, offset, length);
    }
    public void read_wchar_array(char[] value, int offset, int length) {
        stream.read_wchar_array(value, offset, length);
    }
    public void read_octet_array(byte[] value, int offset, int length) {
        stream.read_octet_array(value, offset, length);
    }
    public void read_short_array(short[] value, int offset, int length) {
        stream.read_short_array(value, offset, length);
    }
    public void read_ushort_array(short[] value, int offset, int length) {
        stream.read_ushort_array(value, offset, length);
    }
    public void read_long_array(int[] value, int offset, int length) {
        stream.read_long_array(value, offset, length);
    }
    public void read_ulong_array(int[] value, int offset, int length) {
        stream.read_ulong_array(value, offset, length);
    }
    public void read_longlong_array(long[] value, int offset, int length) {
        stream.read_longlong_array(value, offset, length);
    }
    public void read_ulonglong_array(long[] value, int offset, int length) {
        stream.read_ulonglong_array(value, offset, length);
    }
    public void read_float_array(float[] value, int offset, int length) {
        stream.read_float_array(value, offset, length);
    }
    public void read_double_array(double[] value, int offset, int length) {
        stream.read_double_array(value, offset, length);
    }

    public org.omg.CORBA.Object read_Object() { return stream.read_Object(); }
    public java.io.Serializable read_value() {return stream.read_value();}
    public TypeCode read_TypeCode() { return stream.read_TypeCode(); }
    public Any read_any() { return stream.read_any(); }
    @SuppressWarnings({"deprecation"})
    public org.omg.CORBA.Principal read_Principal() { return stream.read_Principal(); }
    public java.math.BigDecimal read_fixed() { return stream.read_fixed(); }
    public org.omg.CORBA.Context read_Context() { return stream.read_Context(); }

    public org.omg.CORBA.ORB orb() { return stream.orb(); }

    public void addTypeCodeAtPosition(TypeCodeImpl tc, int position) {
        if (typeMap == null) {
            //if (TypeCodeImpl.debug) System.out.println("Creating typeMap");
            typeMap = new HashMap<Integer,TypeCodeImpl>(16);
        }
        //if (TypeCodeImpl.debug) System.out.println(this + " adding tc " 
        //  + tc + " at position " + position);
        typeMap.put(position, tc);
    }

    public TypeCodeImpl getTypeCodeAtPosition(int position) {
        if (typeMap == null)
            return null;
        //if (TypeCodeImpl.debug) System.out.println("Getting tc " 
        //    + typeMap.get(position) + " at position " + position);
        return typeMap.get(position);
    }

    public void setEnclosingInputStream(InputStream enclosure) {
        // WrapperInputStream has no enclosure
    }

    public TypeCodeReader getTopLevelStream() {
        // WrapperInputStream has no enclosure
        return this;
    }

    public int getTopLevelPosition() {
        //if (TypeCodeImpl.debug) System.out.println("WrapperInputStream.getTopLevelPosition " +
            //"returning getPosition " + getPosition() + " - startPos " + startPos +
            //" = " + (getPosition() - startPos));
        return getPosition() - startPos;
    }

    public void performORBVersionSpecificInit() {
        // This is never actually called on a WrapperInputStream, but
        // exists to satisfy the interface requirement.
        stream.performORBVersionSpecificInit();
    }

    public void resetCodeSetConverters() {
        stream.resetCodeSetConverters();
    }

    public void printTypeMap() {
        System.out.println("typeMap = {");
        List<Integer> sortedKeys = new ArrayList<Integer>(typeMap.keySet());
        Collections.sort(sortedKeys);
        Iterator<Integer> i = sortedKeys.iterator();
        while (i.hasNext()) {
            Integer pos = i.next();
            TypeCodeImpl tci = typeMap.get(pos);
            System.out.println("  key = " + pos.intValue() + ", value = " + tci.description());
        }
        System.out.println("}");
    }
}
