/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.runtime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map.Entry;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.utilities.AssumedValue;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

@ExportLibrary(InteropLibrary.class)
@SuppressWarnings("static-method")
public final class LLVMScope implements TruffleObject {

    private final HashMap<String, LLVMSymbol> symbols;
    private final ArrayList<String> functionKeys;
    // private final HashMap<String, LLVMFunctionDescriptor> functions;

    public LLVMScope() {
        this.symbols = new HashMap<>();
        this.functionKeys = new ArrayList<>();
        // this.functions = new HashMap<>();
    }

    @TruffleBoundary
    public LLVMSymbol get(String name) {
        return symbols.get(name);
    }

    /*
     * @TruffleBoundary public LLVMFunctionDescriptor getFromFunctions(String name) { return
     * functions.get(name); }
     */

    @TruffleBoundary
    public String getKey(int idx) {
        return functionKeys.get(idx);
    }

    @TruffleBoundary
    public LLVMFunction getFunction(String name) {
        LLVMSymbol symbol = get(name);
        if (symbol != null && symbol.isFunction()) {
            return symbol.asFunction();
        }
        throw new IllegalStateException("Unknown function: " + name);
    }

    /*
     * @TruffleBoundary public LLVMFunctionDescriptor getFunctionDescriptor(String name) {
     * LLVMFunctionDescriptor symbol = functions.get(name); if (symbol != null) { return symbol; }
     * throw new IllegalStateException("Unknown function descriptor: " + name); }
     */

    @TruffleBoundary
    public LLVMGlobal getGlobalVariable(String name) {
        LLVMSymbol symbol = get(name);
        if (symbol != null && symbol.isGlobalVariable()) {
            return symbol.asGlobalVariable();
        }
        throw new IllegalStateException("Unknown global: " + name);
    }

    /*
     * @TruffleBoundary public void registerFD(LLVMFunctionDescriptor function) {
     * LLVMFunctionDescriptor existing = functions.get(function.getFunctionDetail().getName()); if
     * (existing == null) { assert !functions.containsKey(function.getFunctionDetail().getName());
     * functions.put(function.getFunctionDetail().getName(), function); } else { if (existing !=
     * function) { throw new IllegalStateException("Trying to add function " + function +
     * " to scope, while existing function " + existing + " with name " +
     * function.getFunctionDetail().getName() + " is already present."); } } }
     */

    @TruffleBoundary
    public void register(LLVMSymbol symbol) {
        LLVMSymbol existing = symbols.get(symbol.getName());
        if (existing == null) {
            put(symbol.getName(), symbol);
        } else {
            assert existing == symbol;
        }
    }

    @TruffleBoundary
    public boolean contains(String name) {
        return symbols.containsKey(name);
    }

    /*
     * @TruffleBoundary public boolean containsFD(String name) { return functions.containsKey(name);
     * }
     */

    @TruffleBoundary
    public boolean exports(LLVMContext context, String name) {
        LLVMSymbol localSymbol = get(name);
        LLVMSymbol globalSymbol = context.getGlobalScope().get(name);
        return localSymbol != null && localSymbol == globalSymbol;
    }

    public boolean isEmpty() {
        return symbols.isEmpty();
    }

    @TruffleBoundary
    public void addMissingEntries(LLVMScope other) {
        for (Entry<String, LLVMSymbol> entry : other.symbols.entrySet()) {
            symbols.putIfAbsent(entry.getKey(), entry.getValue());
            /*
             * if (entry.getValue().isFunction()) { functions.putIfAbsent(entry.getKey(),
             * other.functions.get(entry.getKey())); }
             */
        }
    }

    @TruffleBoundary
    public Collection<LLVMSymbol> values() {
        return symbols.values();
    }

    /*
     * @TruffleBoundary public Collection<LLVMFunctionDescriptor> functionDescriptorsValue() {
     * return functions.values(); }
     */

    @TruffleBoundary
    public void rename(String oldName, LLVMSymbol symbol) {
        remove(oldName);
        register(symbol);
    }

    public TruffleObject getKeys() {
        return new Keys(this);
    }

    private void put(String name, LLVMSymbol symbol) {
        assert !symbols.containsKey(name);
        symbols.put(name, symbol);

        if (symbol.isFunction()) {
            assert !functionKeys.contains(name);
            assert functionKeys.size() < symbols.size();
            functionKeys.add(name);
        }
    }

    private void remove(String name) {
        assert symbols.containsKey(name);
        LLVMSymbol removedSymbol = symbols.remove(name);

        if (removedSymbol.isFunction()) {
            boolean contained = functionKeys.remove(name);
            assert contained;
        }
    }

    @ExportMessage
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        return getKeys();
    }

    @ExportMessage
    boolean isMemberReadable(@SuppressWarnings("unused") String name) {
        return contains(name);
    }

    @ExportMessage
    Object readMember(String globalName,
                    @Cached BranchProfile exception,
                    @CachedContext(LLVMLanguage.class) LLVMContext context) throws UnknownIdentifierException {
        if (contains(globalName)) {
            LLVMSymbol symbol = get(globalName);
            if (symbol != null && symbol.isFunction()) {
                int index = symbol.getSymbolIndex(false);
                AssumedValue<LLVMPointer>[] symbolTable = context.findSymbolTable(symbol.getBitcodeID(false));
                LLVMPointer pointer = symbolTable[index].get();
                return LLVMManagedPointer.cast(pointer).getObject();
            }
            return symbol;
        }
        exception.enter();
        throw UnknownIdentifierException.create(globalName);
    }

    @ExportLibrary(InteropLibrary.class)
    static final class Keys implements TruffleObject {

        private final LLVMScope scope;

        private Keys(LLVMScope scope) {
            this.scope = scope;
        }

        @ExportMessage
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        long getArraySize() {
            return scope.functionKeys.size();
        }

        @ExportMessage
        boolean isArrayElementReadable(long index) {
            return 0 <= index && index < getArraySize();
        }

        @ExportMessage
        Object readArrayElement(long index,
                        @Cached BranchProfile exception) throws InvalidArrayIndexException {
            if (isArrayElementReadable(index)) {
                return scope.getKey((int) index);
            } else {
                exception.enter();
                throw InvalidArrayIndexException.create(index);
            }
        }
    }
}
