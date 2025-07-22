/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2004-2005 TONBELLER AG
 * Copyright (C) 2006-2017 Hitachi Vantara and others
 * All Rights Reserved.
 *
 * ---- All changes after Fork in 2023 ------------------------
 *
 * Project: Eclipse daanse
 *
 * Copyright (c) 2023 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors after Fork in 2023:
 *   SmartCity Jena - initial
 */

package org.eclipse.daanse.rolap.common;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.eclipse.daanse.olap.api.NativeEvaluator;
import org.eclipse.daanse.olap.api.function.FunctionDefinition;
import org.eclipse.daanse.olap.api.query.component.Expression;

/**
 * Composite of {@link RolapNative}s. Uses chain of responsibility
 * to select the appropriate {@link RolapNative} evaluator.
 */
public class RolapNativeRegistry extends RolapNative {

    private Map<String, RolapNative> nativeEvaluatorMap =
        new HashMap<>();

    private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
    private final Lock readLock = readWriteLock.readLock();
    private final Lock writeLock = readWriteLock.writeLock();

    public RolapNativeRegistry(boolean enableNativeFilter, boolean enableNativeCrossJoin, boolean enableNativeTopCount) {
        super.setEnabled(true);
        // Mondrian functions which might be evaluated natively.
        register("NonEmptyCrossJoin".toUpperCase(), new RolapNativeCrossJoin(enableNativeCrossJoin));
        register("CrossJoin".toUpperCase(), new RolapNativeCrossJoin(enableNativeCrossJoin));
        register("TopCount".toUpperCase(), new RolapNativeTopCount(enableNativeTopCount));
        register("Filter".toUpperCase(), new RolapNativeFilter(enableNativeFilter));
    }

    /**
     * Returns the matching NativeEvaluator or null if fun can not
     * be executed in SQL for the given context and arguments.
     */
    @Override
	public NativeEvaluator createEvaluator(
        RolapEvaluator evaluator, FunctionDefinition fun, Expression[] args, final boolean enableNativeFilter)
    {
        if (!isEnabled()) {
            return null;
        }

        RolapNative rn = null;
        readLock.lock();
        try {
            rn = nativeEvaluatorMap.get(fun.getFunctionMetaData().operationAtom().name().toUpperCase());
        } finally {
            readLock.unlock();
        }

        if (rn == null) {
            return null;
        }

        NativeEvaluator ne = rn.createEvaluator(evaluator, fun, args, enableNativeFilter);

        if (ne != null) {
            if (listener != null) {
                NativeEvent e = new NativeEvent(this, ne);
                listener.foundEvaluator(e);
            }
        }
        return ne;
    }

    public void register(String funName, RolapNative rn) {
        writeLock.lock();
        try {
            nativeEvaluatorMap.put(funName, rn);
        } finally {
            writeLock.unlock();
        }
    }

    /** for testing */
    @Override
    public
	void setListener(Listener listener) {
        super.setListener(listener);
        readLock.lock();
        try {
            for (RolapNative rn : nativeEvaluatorMap.values()) {
                rn.setListener(listener);
            }
        } finally {
            readLock.unlock();
        }
    }

    /** for testing */
    @Override
    public
	void useHardCache(boolean hard) {
        readLock.lock();
        try {
            for (RolapNative rn : nativeEvaluatorMap.values()) {
                rn.useHardCache(hard);
            }
        } finally {
            readLock.unlock();
        }
    }

    void flushAllNativeSetCache() {
        readLock.lock();
        try {
            for (String key : nativeEvaluatorMap.keySet()) {
                RolapNative currentRolapNative = nativeEvaluatorMap.get(key);
                if (currentRolapNative instanceof RolapNativeSet currentRolapNativeSet
                        && currentRolapNative != null)
                {
                    currentRolapNativeSet.flushCache();
                }
            }
        } finally {
            readLock.unlock();
        }
    }
}
