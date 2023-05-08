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

import static com.oracle.svm.core.util.VMError.unsupportedFeature;
import static com.oracle.svm.preview.panama.core.NativeEntryPointInfo.checkType;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.invoke.MethodType;
import java.util.Arrays;
import java.util.stream.Stream;

import com.oracle.svm.core.graal.code.MemoryAssignment;

import jdk.internal.foreign.CABI;
import jdk.internal.foreign.abi.Binding;
import jdk.internal.foreign.abi.CallingSequence;
import jdk.internal.foreign.abi.CapturableState;
import jdk.internal.foreign.abi.LinkerOptions;
import jdk.internal.foreign.abi.VMStorage;
import jdk.internal.foreign.abi.x64.X86_64Architecture;
import jdk.internal.foreign.abi.x64.sysv.CallArranger;
import jdk.vm.ci.amd64.AMD64;

public abstract class AbiUtils {
    private abstract static class X86_64 extends AbiUtils {
        private static final int INTEGER_OFFSET = 0;
        private static final int VECTOR_OFFSET = 16;
        static {
            assert AMD64.rax == AMD64.allRegisters.get(INTEGER_OFFSET);
            assert AMD64.xmm0 == AMD64.allRegisters.get(VECTOR_OFFSET);
        }

        public MemoryAssignment[] toMemoryAssignment(VMStorage[] argMoves, boolean forReturn) {
            int size = 0;
            for (VMStorage move : argMoves) {
                // Placeholders are ignored. They will be handled further down the line
                if (move.type() != X86_64Architecture.StorageType.PLACEHOLDER) {
                    ++size;
                }
                if (move.type() == X86_64Architecture.StorageType.X87) {
                    throw unsupportedFeature("Unsupported register kind: X87");
                }
                if (move.type() == X86_64Architecture.StorageType.STACK && forReturn) {
                    throw unsupportedFeature("Unsupported register kind for return: STACK");
                }
            }

            MemoryAssignment[] storages = new MemoryAssignment[size];
            int i = 0;
            for (VMStorage move : argMoves) {
                if (move.type() != X86_64Architecture.StorageType.PLACEHOLDER) {
                    storages[i++] = switch (move.type()) {
                        case X86_64Architecture.StorageType.INTEGER -> MemoryAssignment.toRegister(move.indexOrOffset() + INTEGER_OFFSET);
                        case X86_64Architecture.StorageType.VECTOR -> MemoryAssignment.toRegister(move.indexOrOffset() + VECTOR_OFFSET);
                        case X86_64Architecture.StorageType.STACK -> MemoryAssignment.toStack(move.indexOrOffset());
                        default -> throw unsupportedFeature("Unhandled VMStorage: " + move);
                    };
                }
            }

            return storages;
        }
    }

    public static final AbiUtils SysV = new X86_64() {
        private static Stream<Binding.VMStore> argMoveBindingsStream(CallingSequence callingSequence) {
            return callingSequence.argumentBindings()
                            .filter(Binding.VMStore.class::isInstance)
                            .map(Binding.VMStore.class::cast);
        }

        private static Stream<Binding.VMLoad> retMoveBindingsStream(CallingSequence callingSequence) {
            return callingSequence.returnBindings().stream()
                            .filter(Binding.VMLoad.class::isInstance)
                            .map(Binding.VMLoad.class::cast);
        }

        private static Binding.VMLoad[] retMoveBindings(CallingSequence callingSequence) {
            return retMoveBindingsStream(callingSequence).toArray(Binding.VMLoad[]::new);
        }

        private VMStorage[] toStorageArray(Binding.Move[] moves) {
            return Arrays.stream(moves).map(Binding.Move::storage).toArray(VMStorage[]::new);
        }

        @Override
        public NativeEntryPointInfo makeEntrypoint(FunctionDescriptor desc, Linker.Option... options) {
            // From CallArranger.arrangeDowncall
            MethodType type = desc.toMethodType();
            CallArranger.Bindings bindings = CallArranger.getBindings(type, desc, false, LinkerOptions.forDowncall(desc, options));

            // From DowncallLinker.getBoundMethodHandle
            var callingSequence = bindings.callingSequence();
            var argMoves = toStorageArray(argMoveBindingsStream(callingSequence).toArray(Binding.VMStore[]::new));
            var returnMoves = toStorageArray(retMoveBindings(callingSequence));
            var methodType = callingSequence.calleeMethodType();
            var needsReturnBuffer = callingSequence.needsReturnBuffer();

            // From NativeEntrypoint.make
            checkType(methodType, needsReturnBuffer, callingSequence.capturedStateMask());
            var parametersAssignment = toMemoryAssignment(argMoves, false);
            var returnBuffering = needsReturnBuffer ? toMemoryAssignment(returnMoves, true) : null;
            return new NativeEntryPointInfo(methodType, parametersAssignment, returnBuffering, callingSequence.capturedStateMask());
        }

        @Override
        public int supportedCaptureMask() {
            return CapturableState.ERRNO.mask();
        }
    };

    public static AbiUtils getInstance() {
        /*
         * CABI being both 1) internal 2) currently getting implemented, the values of the enum
         * might change quickly; thus, a default seems like a must for now.
         */
        return switch (CABI.current()) {
            case SYS_V -> SysV;
            case WIN_64 -> throw unsupportedFeature("Foreign functions are not yet supported on Windows-x64.");
            default -> throw unsupportedFeature("Foreign functions are not yet supported on " + CABI.current() + ".");
        };
    }

    /**
     * This method re-implements a part of the logic from the JDK so that we can get the callee-type
     * (i.e. C type) of a function from its descriptor. Note that this process is ABI (i.e.
     * architecture and OS) dependant.
     */
    public abstract NativeEntryPointInfo makeEntrypoint(FunctionDescriptor desc, Linker.Option... options);

    /**
     * Generate a register allocation for SubstrateVM from the one generated by and for Panama
     * Foreign/HotSpot.
     */
    public abstract MemoryAssignment[] toMemoryAssignment(VMStorage[] moves, boolean forReturn);

    public abstract int supportedCaptureMask();
}
