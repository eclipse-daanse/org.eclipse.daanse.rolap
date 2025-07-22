/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2003-2005 Julian Hyde
 * Copyright (C) 2005-2017 Hitachi Vantara
 * Copyright (C) 2004-2005 TONBELLER AG
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

import java.util.EventObject;

import org.eclipse.daanse.olap.api.NativeEvaluator;
import org.eclipse.daanse.olap.api.function.FunctionDefinition;
import org.eclipse.daanse.olap.api.query.component.Expression;

/**
 * A factory for {@link org.eclipse.daanse.olap.api.NativeEvaluator}.
 * If the instance returns null,
 * then the interpreter must compute the result itself.
 * If it returns a NativeEvaluator
 * the interpreter may choose to let the NativeEvaluator compute the result.
 *
 * There exist multiple RolapNative implementations, e.g. for CrossJoin,
 * TopCount, Filter etc. If the arguments of these functions are simple enough
 * to be evaluated in SQL then a NativeEvaluator will be returned that performs
 * the computations in SQL. Otherwise null will be returned.
 */
public abstract class RolapNative {

    private boolean enabled;

    public static class NativeEvent extends EventObject {
        private final NativeEvaluator neval;
        public NativeEvent(Object source, NativeEvaluator neval) {
            super(source);
            this.neval = neval;
        }
        NativeEvaluator getNativeEvaluator() {
            return neval;
        }
    }

    public static class TupleEvent extends EventObject {
        private final TupleReader tupleReader;

        public TupleEvent(Object source, TupleReader tupleReader) {
            super(source);
            this.tupleReader = tupleReader;
        }

        TupleReader getTupleReader() {
            return tupleReader;
        }
    }

    public interface Listener {
        void foundEvaluator(NativeEvent e);
        void foundInCache(TupleEvent e);
        void executingSql(TupleEvent e);
    }

    protected Listener listener;

    /**
     * If function can be implemented in SQL, returns a NativeEvaluator that
     * computes the result; otherwise returns null.
     */
    abstract NativeEvaluator createEvaluator(
        RolapEvaluator evaluator,
        FunctionDefinition fun,
        Expression[] args, final boolean enableNativeFilter);

    /**
     * if enabled == false, then createEvaluator will always return null
     */
    boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    Listener getListener() {
        return listener;
    }

    void setListener(Listener listener) {
        this.listener = listener;
    }

    /**
     * Sets whether to use hard caching for testing.
     * When using soft references, we can not test caching
     * because things may be garbage collected during the tests.
     */
    abstract void useHardCache(boolean hard);
}
