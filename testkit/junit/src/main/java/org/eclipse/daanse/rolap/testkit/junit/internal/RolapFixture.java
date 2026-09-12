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
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.daanse.cwm.testkit.api.DataSupplier;
import org.eclipse.daanse.cwm.testkit.api.DatabaseSupplier;
import org.eclipse.daanse.rolap.mapping.instance.api.CatalogTestInstance;
import org.eclipse.daanse.rolap.mapping.model.provider.CatalogMappingSupplier;
import org.eclipse.daanse.rolap.testkit.junit.api.ContextModifier;
import org.eclipse.daanse.rolap.testkit.junit.api.ContextScope;
import org.eclipse.daanse.rolap.testkit.junit.api.DbScope;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;

/**
 * The resolved, validated form of one {@link RolapContextTest} annotation.
 * Immutable; instantiation of suppliers happens once here.
 */
public final class RolapFixture {

    private final CatalogTestInstance instance;
    private final CatalogMappingSupplier mappingSupplier;
    private final DatabaseSupplier databaseSupplier;
    private final DataSupplier dataSupplier;
    private final List<Class<? extends ContextModifier>> modifierClasses;
    private final DbScope dbScope;
    private final String scopeName;
    private final ContextScope contextScope;
    private final String fixtureKey;

    private RolapFixture(CatalogTestInstance instance, CatalogMappingSupplier mappingSupplier,
            DatabaseSupplier databaseSupplier, DataSupplier dataSupplier,
            List<Class<? extends ContextModifier>> modifierClasses, DbScope dbScope, String scopeName,
            ContextScope contextScope, String fixtureKey) {
        this.instance = instance;
        this.mappingSupplier = mappingSupplier;
        this.databaseSupplier = databaseSupplier;
        this.dataSupplier = dataSupplier;
        this.modifierClasses = modifierClasses;
        this.dbScope = dbScope;
        this.scopeName = scopeName;
        this.contextScope = contextScope;
        this.fixtureKey = fixtureKey;
    }

    public static RolapFixture resolve(RolapContextTest annotation, String describedLocation) {
        boolean hasInstance = annotation.value() != CatalogTestInstance.class;
        boolean hasSuppliers = annotation.catalog().length > 0
                || annotation.database() != DatabaseSupplier.class
                || annotation.data() != DataSupplier.class;
        if (hasInstance && hasSuppliers) {
            throw new ExtensionConfigurationException("@RolapContextTest at " + describedLocation
                    + ": 'value' and the supplier form (catalog/database/data) are mutually exclusive");
        }
        if (!hasInstance && annotation.catalog().length == 0) {
            throw new ExtensionConfigurationException("@RolapContextTest at " + describedLocation
                    + ": either 'value' or at least one 'catalog' supplier is required");
        }
        if (annotation.dbScope() == DbScope.NAMED && annotation.scopeName().isBlank()) {
            throw new ExtensionConfigurationException("@RolapContextTest at " + describedLocation
                    + ": DbScope.NAMED requires a non-blank scopeName");
        }

        CatalogTestInstance instance = null;
        CatalogMappingSupplier mapping;
        DatabaseSupplier database = null;
        DataSupplier data = null;
        String fixtureKey;
        if (hasInstance) {
            instance = instantiate(annotation.value(), describedLocation);
            mapping = instance.mappingSupplier();
            database = instance.databaseSupplier();
            data = instance.dataSupplier();
            fixtureKey = annotation.value().getName();
        } else {
            mapping = CatalogComposer.compose(List.of(annotation.catalog()), describedLocation);
            if (annotation.database() != DatabaseSupplier.class) {
                database = instantiate(annotation.database(), describedLocation);
            }
            if (annotation.data() != DataSupplier.class) {
                data = instantiate(annotation.data(), describedLocation);
            }
            fixtureKey = Stream
                    .concat(Stream.of((Class<?>[]) annotation.catalog()),
                            Stream.of(annotation.database(), annotation.data()))
                    .map(Class::getName).collect(Collectors.joining("+"));
        }
        if (mapping == null) {
            throw new ExtensionConfigurationException(
                    "@RolapContextTest at " + describedLocation + ": no catalog mapping supplier available");
        }
        return new RolapFixture(instance, mapping, database, data, List.of(annotation.modifiers()),
                annotation.dbScope(), annotation.scopeName(), annotation.contextScope(), fixtureKey);
    }

    static <T> T instantiate(Class<T> type, String describedLocation) {
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new ExtensionConfigurationException("@RolapContextTest at " + describedLocation
                    + ": cannot instantiate " + type.getName() + " via no-arg constructor", e);
        }
    }

    public CatalogTestInstance instance() {
        return instance;
    }

    public CatalogMappingSupplier mappingSupplier() {
        // Guarded: the generated suppliers mutate a JVM-shared model on get().
        return SharedMappingAccess.guarded(mappingSupplier);
    }

    public DatabaseSupplier databaseSupplier() {
        return databaseSupplier;
    }

    public DataSupplier dataSupplier() {
        return dataSupplier;
    }

    public List<Class<? extends ContextModifier>> modifierClasses() {
        return modifierClasses;
    }

    public DbScope dbScope() {
        return dbScope;
    }

    public String scopeName() {
        return scopeName;
    }

    public ContextScope contextScope() {
        return contextScope;
    }

    /** Stable identity of the data this fixture loads; PER_RUNTIME sharing key. */
    public String fixtureKey() {
        return fixtureKey;
    }

    /**
     * Identity of the DATABASE a fixture needs — the schema and data suppliers
     * only. Catalog mappings and modifiers are in-JVM metadata and never touch
     * the database, so fixtures differing only in catalog share one loaded
     * database (the legacy harness's one-database-per-JVM behaviour). The
     * phase-1 instance form keys on the instance class: its CSV set is part of
     * the instance.
     */
    /**
     * Identity of the DDL a fixture's schema supplier produces, for the database
     * key. The supplier's class is only usable when it is a real class: instances
     * that return a method reference (for example
     * {@code ExplicitRecognizerTestInstances::databaseSchema}) get a fresh
     * synthetic lambda class on every call, whose name would make the key unstable
     * and silently defeat the load-once guard. Those fall back to the instance
     * class, which is stable and no coarser than the pre-existing behaviour.
     */
    private static String schemaIdentity(CatalogTestInstance instance) {
        DatabaseSupplier supplier = instance.databaseSupplier();
        if (supplier == null) {
            return "-";
        }
        Class<?> type = supplier.getClass();
        if (type.isSynthetic() || type.getCanonicalName() == null) {
            return instance.getClass().getName();
        }
        return type.getName();
    }

    public String databaseKey() {
        if (instance != null) {
            // Key on the CSV set the instance loads, not on its class: fixtures that
            // differ only in catalog mapping then share one loaded database. The 24
            // AccessControlRollupInstances variants all delegate to FoodmartTestInstance's
            // CSVs, so they collapse onto one; ExplicitRecognizerTestInstances add two
            // extra header CSVs, so they stay separate. Keying on databaseSupplier()
            // instead would be unstable -- some instances return a method reference,
            // whose getClass().getName() differs on every call.
            Map<String, URL> csv = instance.csvResources();
            if (csv != null && !csv.isEmpty()) {
                return "csv:" + new TreeSet<>(csv.keySet()) + "+" + schemaIdentity(instance);
            }
            return instance.getClass().getName();
        }
        return (databaseSupplier == null ? "-" : databaseSupplier.getClass().getName()) + "+"
                + (dataSupplier == null ? "-" : dataSupplier.getClass().getName());
    }
}
