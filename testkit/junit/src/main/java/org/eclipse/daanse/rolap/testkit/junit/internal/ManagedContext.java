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
 *   SmartCity Jena, Stefan Bischof - initial
 */
package org.eclipse.daanse.rolap.testkit.junit.internal;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.daanse.jdbc.datasource.testkit.api.ActiveDatabase;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.api.connection.ConnectionProps;
import org.eclipse.daanse.rolap.testkit.junit.api.ContextModifier;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * One provisioned context lifetime: the {@link ExtensionTestContext}, the
 * connections handed out, and applied {@link ContextModifier}s. Implements
 * {@link ExtensionContext.Store.CloseableResource} so JUnit guarantees
 * {@link #close()} — modifiers reversed, then connections, then a guarded
 * deactivate (idempotent; the pool is owned by ActiveDatabase, not this).
 */
public final class ManagedContext implements ExtensionContext.Store.CloseableResource {

    private final ExtensionTestContext context;
    private final ActiveDatabase database;
    private final List<Connection> connections = new CopyOnWriteArrayList<>();
    private final Map<Class<? extends ContextModifier>, ContextModifier> modifiers = new LinkedHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    private ManagedContext(ExtensionTestContext context, ActiveDatabase database) {
        this.context = context;
        this.database = database;
    }

    public static ManagedContext open(RolapFixture fixture, ActiveDatabase database, Map<String, Object> config) {
        ExtensionTestContext ctx = new ExtensionTestContext(database.connectionPool(), database.dialect(),
                fixture.mappingSupplier());
        ManagedContext managed = new ManagedContext(ctx, database);
        try {
            config.forEach(ctx::putConfig);
            for (Class<? extends ContextModifier> type : fixture.modifierClasses()) {
                ContextModifier modifier = RolapFixture.instantiate(type, type.getName());
                managed.modifiers.put(type, modifier);
                modifier.apply(ctx);
            }
        } catch (Exception e) {
            managed.closeQuietly();
            throw e instanceof RuntimeException re ? re
                    : new IllegalStateException("ContextModifier apply failed", e);
        }
        return managed;
    }

    public org.eclipse.daanse.olap.api.Context<?> context() {
        return context;
    }

    public ActiveDatabase database() {
        return database;
    }

    /** Opens (and tracks) a connection; empty role list = default role. */
    public Connection connection(List<String> roles) {
        Connection connection;
        if (roles.isEmpty()) {
            connection = context.getConnectionWithDefaultRole();
        } else {
            List<String> known = context.getAccessRoles();
            for (String role : roles) {
                if (!known.contains(role)) {
                    throw new ExtensionConfigurationException("@Roles: role '" + role
                            + "' is not defined in the catalog; available roles: " + known);
                }
            }
            connection = context.getConnection(new ConnectionProps(roles));
        }
        connections.add(connection);
        return connection;
    }

    public ContextModifier modifier(Class<?> type) {
        ContextModifier modifier = modifiers.get(type);
        if (modifier == null) {
            throw new ExtensionConfigurationException("Modifier " + type.getName()
                    + " is not declared in @RolapContextTest(modifiers = ...) for this context");
        }
        return modifier;
    }

    public boolean hasModifier(Class<?> type) {
        return modifiers.containsKey(type);
    }

    @Override
    public void close() throws Exception {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        Exception first = null;
        List<ContextModifier> reversed = new CopyOnWriteArrayList<>(modifiers.values());
        for (int i = reversed.size() - 1; i >= 0; i--) {
            try {
                reversed.get(i).close();
            } catch (Exception e) {
                first = suppress(first, e);
            }
        }
        for (Connection connection : connections) {
            try {
                connection.close();
            } catch (RuntimeException e) {
                first = suppress(first, e);
            }
        }
        try {
            context.deactivate(Map.of());
        } catch (Exception e) {
            first = suppress(first, e);
        }
        if (first != null) {
            throw first;
        }
    }

    private void closeQuietly() {
        try {
            close();
        } catch (Exception e) {
            // the original failure is more interesting; this cleanup failure is secondary
        }
    }

    private static Exception suppress(Exception first, Exception next) {
        if (first == null) {
            return next;
        }
        first.addSuppressed(next);
        return first;
    }
}
