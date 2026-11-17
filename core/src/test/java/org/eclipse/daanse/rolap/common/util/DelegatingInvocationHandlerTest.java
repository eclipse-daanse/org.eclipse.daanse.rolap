/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.rolap.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

import org.junit.jupiter.api.Test;

/**
 * A proxy over a delegating handler runs a method the handler declares itself, else the target's; without a target
 * the call is refused naming the method; the target's own exception comes through unwrapped.
 */
class DelegatingInvocationHandlerTest {

    public interface Pair {
        String a();

        String b();
    }

    static Pair proxyOver(DelegatingInvocationHandler handler) {
        return (Pair) Proxy.newProxyInstance(Pair.class.getClassLoader(), new Class<?>[] { Pair.class }, handler);
    }

    static final Pair TARGET = new Pair() {
        @Override
        public String a() {
            return "target a";
        }

        @Override
        public String b() {
            throw new IllegalStateException("target b failed");
        }
    };

    @Test
    void anAnonymousHandlerRunsItsOwnMethodAndHandsTheRestToTheTarget() {
        Pair pair = proxyOver(new DelegatingInvocationHandler() {
            @SuppressWarnings("unused")
            public String a() {
                return "handler a";
            }

            @Override
            protected Object getTarget() {
                return TARGET;
            }
        });

        assertThat(pair.a()).isEqualTo("handler a");
        assertThatThrownBy(pair::b).isInstanceOf(IllegalStateException.class).hasMessage("target b failed");
    }

    @Test
    void withoutATargetTheCallIsRefusedNamingTheMethod() {
        Pair pair = proxyOver(new DelegatingInvocationHandler() {
            @Override
            protected Object getTarget() throws InvocationTargetException {
                return null;
            }
        });
        assertThatThrownBy(pair::b).isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("b()");
    }
}
