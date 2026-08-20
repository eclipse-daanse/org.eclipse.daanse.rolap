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

import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.daanse.cwm.testkit.data.DataLayer;
import org.eclipse.daanse.cwm.testkit.database.DatabaseLayer;
import org.eclipse.daanse.jdbc.datasource.testkit.api.ActiveDatabase;
import org.eclipse.daanse.jdbc.datasource.testkit.api.DatabaseProvider;
import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.eclipse.daanse.sql.dialect.api.DialectFactory;
import org.eclipse.daanse.sql.dialect.api.DialectInitData;
import org.eclipse.daanse.rolap.testkit.core.CsvLoader;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;

/**
 * Provisions (activates + loads) databases for fixtures, caching them per
 * isolation key for the JVM run. Thread-safe: {@code computeIfAbsent}
 * guarantees a database loads exactly once even when classes race for the
 * same key.
 *
 * <p>Isolation keys carry a {@code rolap-junit:} prefix so they can't collide
 * with keys other jdbc-testkit consumers use.
 *
 * <p>Replicates {@code CatalogTestHarness.runDiscovered}'s load path
 * standalone; see docs/test-improvements/05-testkit-harness-refactoring.md
 * for the planned unification.
 */
final class DatabaseProvisioner {

    /** Prefix separating this extension's provider keys from everyone else's. */
    private static final String KEY_PREFIX = "rolap-junit:";

    private static final ConcurrentHashMap<String, DatabaseProvider> PROVIDER =
            new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Entry> DATABASES = new ConcurrentHashMap<>();

    private record Entry(ActiveDatabase database, String fixtureKey) {
    }

    private DatabaseProvisioner() {
    }

    /**
     * Returns the loaded database for the given isolation key, activating and
     * loading it on first use. A key reached by two DIFFERENT fixtures (only
     * possible with {@code DbScope.NAMED}) is a configuration error.
     */
    static ActiveDatabase provision(RolapFixture fixture, String isolationKey) {
        DatabaseProvider provider = PROVIDER.computeIfAbsent("selected", k -> DatabaseProvider.selected());
        String prefixedKey = KEY_PREFIX + isolationKey;
        Entry entry = DATABASES.computeIfAbsent(prefixedKey, key -> {
            ActiveDatabase database = provider.activate(key);
            try {
                load(fixture, database);
            } catch (Exception e) {
                throw new IllegalStateException(
                        "Database provisioning failed for " + key + " (fixture " + fixture.fixtureKey() + ")", e);
            }
            return new Entry(database, fixture.fixtureKey());
        });
        if (!entry.fixtureKey().equals(fixture.fixtureKey())) {
            throw new ExtensionConfigurationException("DbScope key '" + isolationKey
                    + "' is already provisioned with fixture " + entry.fixtureKey()
                    + " but this test declares fixture " + fixture.fixtureKey()
                    + " — classes sharing a NAMED scope must declare the same catalog/database/data");
        }
        return entry.database();
    }

    private static void load(RolapFixture fixture, ActiveDatabase database) throws Exception {
        if (fixture.databaseSupplier() != null) {
            // Phase-2 layered path: CWM Schema -> DDL -> data.
            DatabaseLayer.apply(database.dataSource(), database.dialect(), fixture.databaseSupplier().schema());
            if (fixture.dataSupplier() != null) {
                DataLayer.apply(database.dataSource(), database.dialect(), fixture.databaseSupplier().schema(),
                        fixture.dataSupplier());
            }
        } else if (fixture.instance() != null) {
            // Phase-1 backwards-compat: CSVs with SQL-type row.
            Map<String, URL> csv = fixture.instance().csvResources();
            if (csv != null && !csv.isEmpty()) {
                CsvLoader.load(database.dataSource(), new FixedDialectFactory(database.dialect()), csv);
            }
        }
        // Supplier form without database(): mapping-only fixture, nothing to load.
    }

    private record FixedDialectFactory(Dialect dialect) implements DialectFactory {
        @Override
        public Dialect createDialect(DialectInitData init) {
            return dialect;
        }
    }
}
