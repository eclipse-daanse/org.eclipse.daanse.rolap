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
package org.eclipse.daanse.rolap.aggmatcher;

import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.olap.check.runtime.api.OlapCheckSuiteSupplier;
import org.eclipse.daanse.rolap.mapping.instance.api.CatalogTestInstance;
import org.eclipse.daanse.rolap.mapping.model.catalog.Catalog;
import org.eclipse.daanse.rolap.mapping.model.catalog.CatalogFactory;
import org.eclipse.daanse.rolap.mapping.model.provider.CatalogMappingSupplier;

/**
 * One {@link CatalogTestInstance} per distinct {@code AggName}/{@code AggPattern}
 * configuration a test method in {@link AggMeasureFactCountTest} needs --
 * Phase-1 (CSV-with-SQL-type-row) fixtures only auto-load their tables under
 * Form A ({@code @RolapContextTest(value = ...)}); Form B's supplier
 * composition needs a full {@code DatabaseSupplier} to trigger any data
 * loading at all (see {@code DatabaseProvisioner.load}), which this fixture
 * has no use for.
 *
 * <p>Each instance composes the same base -- an empty catalog rebuilt from
 * scratch by {@link AggMeasureFactCountTestModifierEmf} (or one of its
 * {@link AggMeasureFactCountTestModifiers} subclasses) with a single custom
 * "Sales" cube over the {@code fact_csv_2016} fixture and a minimal,
 * self-contained Store dimension -- no dependency on the FoodMart catalog or
 * its data, since the queries in this test never reference Store members.
 */
final class AggMeasureFactCountTestInstances {

    private AggMeasureFactCountTestInstances() {
    }

    private static Catalog freshBase() {
        Catalog base = CatalogFactory.eINSTANCE.createCatalog();
        base.setName("AggMeasureFactCountTest");
        Schema databaseSchema = RelationalFactory.eINSTANCE.createSchema();
        base.getImportedElement().add(databaseSchema);
        return base;
    }

    private abstract static class Base implements CatalogTestInstance {
        @Override
        public OlapCheckSuiteSupplier checkSuiteSupplier() {
            return null;
        }

        @Override
        public Map<String, URL> csvResources() {
            Map<String, URL> m = new LinkedHashMap<>();
            m.put("store", getClass().getResource("aggmeasurefactcounttest/data/store.csv"));
            m.put("time_csv", getClass().getResource("aggmeasurefactcounttest/data/time_csv.csv"));
            m.put("fact_csv_2016", getClass().getResource("aggmeasurefactcounttest/data/fact_csv_2016.csv"));
            m.put("agg_c_6_fact_csv_2016", getClass().getResource("aggmeasurefactcounttest/data/agg_c_6_fact_csv_2016.csv"));
            m.put("agg_csv_different_column_names", getClass().getResource("aggmeasurefactcounttest/data/agg_csv_different_column_names.csv"));
            m.put("agg_csv_divide_by_zero", getClass().getResource("aggmeasurefactcounttest/data/agg_csv_divide_by_zero.csv"));
            return m;
        }
    }

    /** No {@code AggName}/{@code AggPattern} at all -- exercises the {@code DefaultRecognizer} naming-convention match. */
    public static class Default extends Base {
        @Override
        public String name() {
            return "mondrian.AggMeasureFactCountTest.Default";
        }

        @Override
        public CatalogMappingSupplier mappingSupplier() {
            return new AggMeasureFactCountTestModifierEmf(freshBase());
        }
    }

    public static class AggName extends Base {
        @Override
        public String name() {
            return "mondrian.AggMeasureFactCountTest.AggName";
        }

        @Override
        public CatalogMappingSupplier mappingSupplier() {
            return new AggMeasureFactCountTestModifiers.AggName(freshBase());
        }
    }

    public static class FactColumnNotExists extends Base {
        @Override
        public String name() {
            return "mondrian.AggMeasureFactCountTest.FactColumnNotExists";
        }

        @Override
        public CatalogMappingSupplier mappingSupplier() {
            return new AggMeasureFactCountTestModifiers.FactColumnNotExists(freshBase());
        }
    }

    public static class MeasureFactColumnNotExist extends Base {
        @Override
        public String name() {
            return "mondrian.AggMeasureFactCountTest.MeasureFactColumnNotExist";
        }

        @Override
        public CatalogMappingSupplier mappingSupplier() {
            return new AggMeasureFactCountTestModifiers.MeasureFactColumnNotExist(freshBase());
        }
    }

    public static class WithoutMeasureFactColumnElement extends Base {
        @Override
        public String name() {
            return "mondrian.AggMeasureFactCountTest.WithoutMeasureFactColumnElement";
        }

        @Override
        public CatalogMappingSupplier mappingSupplier() {
            return new AggMeasureFactCountTestModifiers.WithoutMeasureFactColumnElement(freshBase());
        }
    }

    public static class MeasureFactColumnAndAggFactCountNotExist extends Base {
        @Override
        public String name() {
            return "mondrian.AggMeasureFactCountTest.MeasureFactColumnAndAggFactCountNotExist";
        }

        @Override
        public CatalogMappingSupplier mappingSupplier() {
            return new AggMeasureFactCountTestModifiers.MeasureFactColumnAndAggFactCountNotExist(freshBase());
        }
    }

    public static class AggNameDifferentColumnNames extends Base {
        @Override
        public String name() {
            return "mondrian.AggMeasureFactCountTest.AggNameDifferentColumnNames";
        }

        @Override
        public CatalogMappingSupplier mappingSupplier() {
            return new AggMeasureFactCountTestModifiers.AggNameDifferentColumnNames(freshBase());
        }
    }

    public static class AggDivideByZero extends Base {
        @Override
        public String name() {
            return "mondrian.AggMeasureFactCountTest.AggDivideByZero";
        }

        @Override
        public CatalogMappingSupplier mappingSupplier() {
            return new AggMeasureFactCountTestModifiers.AggDivideByZero(freshBase());
        }
    }

    public static class AggPattern extends Base {
        @Override
        public String name() {
            return "mondrian.AggMeasureFactCountTest.AggPattern";
        }

        @Override
        public CatalogMappingSupplier mappingSupplier() {
            return new AggMeasureFactCountTestModifiers.AggPattern(freshBase());
        }
    }
}
