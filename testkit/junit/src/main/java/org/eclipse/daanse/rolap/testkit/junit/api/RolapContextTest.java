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
package org.eclipse.daanse.rolap.testkit.junit.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.eclipse.daanse.cwm.testkit.api.DataSupplier;
import org.eclipse.daanse.cwm.testkit.api.DatabaseSupplier;
import org.eclipse.daanse.rolap.mapping.instance.api.CatalogTestInstance;
import org.eclipse.daanse.rolap.mapping.model.provider.CatalogMappingSupplier;
import org.eclipse.daanse.rolap.testkit.junit.RolapContextExtension;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Provisions database, catalog and rolap context for a test class or method,
 * and enables type-based injection of {@code Connection}, {@code Context},
 * {@code ActiveDatabase}, {@code Dialect}, {@code DataSource} and declared
 * {@link ContextModifier} instances into parameters and {@link InjectRolap}
 * fields.
 *
 * <p>Two mutually exclusive catalog forms: {@link #value()} (a
 * {@link CatalogTestInstance} bundling mapping, schema and data) or the
 * supplier form ({@link #catalog()}, {@link #database()}, {@link #data()}).
 *
 * <p>A method-level annotation replaces the class-level configuration
 * entirely for that test; nothing is merged.
 */
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
@ExtendWith(RolapContextExtension.class)
public @interface RolapContextTest {

    /** Form A: a complete test instance. Default sentinel = unset. */
    Class<? extends CatalogTestInstance> value() default CatalogTestInstance.class;

    /**
     * Form B: catalog mapping suppliers, composed left-to-right. Each class
     * after the first is built via a constructor taking the previous
     * supplier's {@code Catalog}, or its no-arg constructor if none exists.
     */
    Class<? extends CatalogMappingSupplier>[] catalog() default {};

    /** Form B: CWM database schema supplier. Default sentinel = unset. */
    Class<? extends DatabaseSupplier> database() default DatabaseSupplier.class;

    /** Form B: data supplier (CSV/programmatic). Default sentinel = unset. */
    Class<? extends DataSupplier> data() default DataSupplier.class;

    /**
     * Context modifiers, applied in declaration order after the context is
     * built and closed in reverse order after the test. Instances are
     * injectable by their concrete type.
     */
    Class<? extends ContextModifier>[] modifiers() default {};

    /** Lifetime of the loaded database (see {@link DbScope}). */
    DbScope dbScope() default DbScope.PER_RUNTIME;

    /** Required for {@link DbScope#NAMED}: instances sharing this name share the database. */
    String scopeName() default "";

    /** Lifetime of the rolap context (see {@link ContextScope}). */
    ContextScope contextScope() default ContextScope.PER_TEST;
}
