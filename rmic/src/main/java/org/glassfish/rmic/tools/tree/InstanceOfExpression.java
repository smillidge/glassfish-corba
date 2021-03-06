/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 1994-2018 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.rmic.tools.tree;

import org.glassfish.rmic.tools.java.*;
import org.glassfish.rmic.tools.asm.Assembler;
import org.glassfish.rmic.tools.asm.Label;
import java.io.PrintStream;
import java.util.Hashtable;

/**
 * WARNING: The contents of this source file are not part of any
 * supported API.  Code that depends on them does so at its own risk:
 * they are subject to change or removal without notice.
 */
public
class InstanceOfExpression extends BinaryExpression {
    /**
     * constructor
     */
    public InstanceOfExpression(long where, Expression left, Expression right) {
        super(INSTANCEOF, where, Type.tBoolean, left, right);
    }

    /**
     * Check the expression
     */
    public Vset checkValue(Environment env, Context ctx, Vset vset, Hashtable<Object, Object> exp) {
        vset = left.checkValue(env, ctx, vset, exp);
        right = new TypeExpression(right.where, right.toType(env, ctx));

        if (right.type.isType(TC_ERROR) || left.type.isType(TC_ERROR)) {
            // An error was already reported
            return vset;
        }

        if (!right.type.inMask(TM_CLASS|TM_ARRAY)) {
            env.error(right.where, "invalid.arg.type", right.type, opNames[op]);
            return vset;
        }
        try {
            if (!env.explicitCast(left.type, right.type)) {
                env.error(where, "invalid.instanceof", left.type, right.type);
            }
        } catch (ClassNotFound e) {
            env.error(where, "class.not.found", e.name, opNames[op]);
        }
        return vset;
    }

    /**
     * Inline
     */
    public Expression inline(Environment env, Context ctx) {
        return left.inline(env, ctx);
    }
    public Expression inlineValue(Environment env, Context ctx) {
        left = left.inlineValue(env, ctx);
        return this;
    }

    public int costInline(int thresh, Environment env, Context ctx) {
        if (ctx == null) {
            return 1 + left.costInline(thresh, env, ctx);
        }
        // sourceClass is the current class trying to inline this method
        ClassDefinition sourceClass = ctx.field.getClassDefinition();
        try {
            // We only allow the inlining if the current class can access
            // the "instance of" class
            if (right.type.isType(TC_ARRAY) ||
                 sourceClass.permitInlinedAccess(env, env.getClassDeclaration(right.type)))
                return 1 + left.costInline(thresh, env, ctx);
        } catch (ClassNotFound e) {
        }
        return thresh;
    }




    /**
     * Code
     */
    public void codeValue(Environment env, Context ctx, Assembler asm) {
        left.codeValue(env, ctx, asm);
        if (right.type.isType(TC_CLASS)) {
            asm.add(where, opc_instanceof, env.getClassDeclaration(right.type));
        } else {
            asm.add(where, opc_instanceof, right.type);
        }
    }
    void codeBranch(Environment env, Context ctx, Assembler asm, Label lbl, boolean whenTrue) {
        codeValue(env, ctx, asm);
        asm.add(where, whenTrue ? opc_ifne : opc_ifeq, lbl, whenTrue);
    }
    public void code(Environment env, Context ctx, Assembler asm) {
        left.code(env, ctx, asm);
    }

    /**
     * Print
     */
    public void print(PrintStream out) {
        out.print("(" + opNames[op] + " ");
        left.print(out);
        out.print(" ");
        if (right.op == TYPE) {
            out.print(right.type.toString());
        } else {
            right.print(out);
        }
        out.print(")");
    }
}
