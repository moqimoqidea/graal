/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.preview.panama.core;

import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

/**
 * System lookups are currently unsupported, but this would be possible and might be useful to do
 * so. There would be two issues to solve; other than that, the JDK's implementation can be reused:
 * <ul>
 * <li>The library path(s): there might not be a JDK on the machine running the native image (on
 * linux64, the loaded libraries are {libc, libm, libdl})</li>
 * <li>Library loading: libraries are currently loaded in "the global scope", which is not exactly
 * the correct behavior</li>
 * </ul>
 */
@TargetClass(className = "jdk.internal.foreign.SystemLookup")
@Substitute
@SuppressWarnings("unused")
public final class Target_jdk_internal_foreign_SystemLookup {
    @Delete("Default lookup is not supported.")
    public static native Target_jdk_internal_foreign_SystemLookup getInstance();
}

@TargetClass(className = "jdk.internal.foreign.SystemLookup", innerClass = "WindowsFallbackSymbols")
@Delete
@SuppressWarnings("unused")
final class Target_jdk_internal_foreign_SystemLookup_WindowsFallbackSymbols {
}
