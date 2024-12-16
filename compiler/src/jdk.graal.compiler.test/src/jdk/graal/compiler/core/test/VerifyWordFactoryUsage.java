/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package jdk.graal.compiler.core.test;

import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.phases.VerifyPhase;
import jdk.vm.ci.meta.MetaUtil;

/**
 * Ensures that Graal compiler code uses {@link jdk.graal.compiler.word.WordFactory} instead of
 * {@link org.graalvm.word.WordFactory} to create word values.
 */
public class VerifyWordFactoryUsage extends VerifyPhase<CoreProviders> {

    @Override
    public boolean checkContract() {
        return false;
    }

    @Override
    protected void verify(StructuredGraph graph, CoreProviders context) {

        String badWordFactory = MetaUtil.toInternalName(org.graalvm.word.WordFactory.class.getName());

        for (MethodCallTargetNode t : graph.getNodes(MethodCallTargetNode.TYPE)) {
            if (t.targetMethod().getDeclaringClass().getName().equals(badWordFactory)) {
                throw new VerificationError("accessing %s in %s is prohibited - use %s instead",
                                badWordFactory,
                                graph.method().format("%H.%n(%p)"),
                                jdk.graal.compiler.word.WordFactory.class.getName());

            }
        }
    }
}
