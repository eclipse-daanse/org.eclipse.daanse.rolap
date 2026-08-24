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
 *   SmartCity Jena - initial
 */
package org.eclipse.daanse.rolap.testkit.assertions;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.core.AbstractBasicContext;

/**
 * Scopes one or more context-configuration overrides to a block, restoring the
 * prior state on close:
 *
 * <pre>{@code
 * try (var override = ConfigOverride.of(context)
 *         .set(ConfigConstants.ENABLE_NATIVE_CROSS_JOIN, true)) {
 *     ...
 * }
 * }</pre>
 *
 * <p>
 * Each {@link #set(String, Object)} call remembers what the key held - another
 * value, or nothing - immediately before that call. {@link #close()} undoes the
 * calls in reverse order, so setting the same key more than once still restores
 * the state from before the first {@code set} rather than an intermediate one.
 * </p>
 *
 * <p>
 * Needs a {@code context} backed by {@link AbstractBasicContext} - true of every
 * ROLAP context in this codebase - since that is where the writable side of the
 * configuration lives; anything else is rejected up front by {@link #of(Context)}.
 * </p>
 */
//TODO not sure that we should use this class. @RolapConfig is better
public final class ConfigOverride implements AutoCloseable {

    private final AbstractBasicContext<?> context;
    private final Deque<Restore> restores = new ArrayDeque<>();
    private boolean closed;

    private ConfigOverride(AbstractBasicContext<?> context) {
        this.context = context;
    }

    /** Starts a scope of overrides against {@code context}'s configuration. */
    public static ConfigOverride of(Context<?> context) {
        Objects.requireNonNull(context, "context");
        if (!(context instanceof AbstractBasicContext<?> abstractBasicContext)) {
            throw new IllegalArgumentException(
                    "ConfigOverride needs an AbstractBasicContext-backed Context, got " + context.getClass());
        }
        return new ConfigOverride(abstractBasicContext);
    }

    /**
     * Overrides {@code key} to {@code value} until this override is closed.
     * Chainable - {@code ConfigOverride.of(context).set(a, 1).set(b, 2)}.
     */
    public ConfigOverride set(String key, Object value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        if (closed) {
            throw new IllegalStateException("ConfigOverride already closed");
        }
        Object previous = context.getConfigValue(key, null, Object.class);
        restores.push(new Restore(key, previous));
        context.putConfigValue(key, value);
        return this;
    }

    /** Restores every overridden key to its pre-{@link #set} state, in reverse order. */
    
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        while (!restores.isEmpty()) {
            Restore restore = restores.pop();
            if (restore.previous == null) {
                context.removeConfigValue(restore.key);
            } else {
                //context.putConfigValue(key, value);
                context.putConfigValue(restore.key, restore.previous);
            }
        }
    }

    private record Restore(String key, Object previous) {
    }
}
