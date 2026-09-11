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
package org.eclipse.daanse.test;

import java.net.URL;
import java.util.Map;

import org.eclipse.daanse.olap.check.runtime.api.OlapCheckSuiteSupplier;
import org.eclipse.daanse.rolap.mapping.instance.api.CatalogTestInstance;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.CatalogSupplier;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartDatabaseSupplier;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.mapping.model.catalog.Catalog;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.RollupPolicy;
import org.eclipse.daanse.rolap.mapping.model.provider.CatalogMappingSupplier;

import org.eclipse.daanse.rolap.SchemaModifiersEmf;

/**
 * One {@link CatalogTestInstance} per (test method, {@link RollupPolicy})
 * combination for the {@link AccessControlTest} scenarios that used to call
 * {@code AccessControlTestModifierNN(catalog, policy)} up to three times
 * within a single test method, resetting the catalog between calls -- the
 * new testkit builds the catalog once per test, so each policy variant is
 * now its own test method backed by its own instance here.
 */
final class AccessControlRollupInstances {

    private AccessControlRollupInstances() {
    }

    private abstract static class Base implements CatalogTestInstance {
        @Override
        public OlapCheckSuiteSupplier checkSuiteSupplier() {
            return null;
        }

        @Override
        public Map<String, URL> csvResources() {
            return new FoodmartTestInstance().dataSupplier().csvResources();
        }

        @Override
        public org.eclipse.daanse.cwm.testkit.api.DatabaseSupplier databaseSupplier() {
            return new FoodmartDatabaseSupplier();
        }
    }

    // ---- testRollupBottomLevel: AccessControlTestModifier39 ----

    public static class RollupBottomLevelFull extends Base {
        @Override
        public String name() {
            return "mondrian.AccessControlTest.testRollupBottomLevel.FULL";
        }

        @Override
        public CatalogMappingSupplier mappingSupplier() {
            Catalog catalog = new CatalogSupplier().get();
            return new SchemaModifiersEmf.AccessControlTestModifier39(catalog, RollupPolicy.FULL);
        }
    }

    public static class RollupBottomLevelPartial extends Base {
        @Override
        public String name() {
            return "mondrian.AccessControlTest.testRollupBottomLevel.PARTIAL";
        }

        @Override
        public CatalogMappingSupplier mappingSupplier() {
            Catalog catalog = new CatalogSupplier().get();
            return new SchemaModifiersEmf.AccessControlTestModifier39(catalog, RollupPolicy.PARTIAL);
        }
    }

    public static class RollupBottomLevelHidden extends Base {
        @Override
        public String name() {
            return "mondrian.AccessControlTest.testRollupBottomLevel.HIDDEN";
        }

        @Override
        public CatalogMappingSupplier mappingSupplier() {
            Catalog catalog = new CatalogSupplier().get();
            return new SchemaModifiersEmf.AccessControlTestModifier39(catalog, RollupPolicy.HIDDEN);
        }
    }

    // ---- testRollupPolicyGreatGrandchildInvisible: AccessControlTestModifier40 ----

    public static class GreatGrandchildInvisibleFull extends Base {
        @Override
        public String name() {
            return "mondrian.AccessControlTest.testRollupPolicyGreatGrandchildInvisible.FULL";
        }

        @Override
        public CatalogMappingSupplier mappingSupplier() {
            Catalog catalog = new CatalogSupplier().get();
            return new SchemaModifiersEmf.AccessControlTestModifier40(catalog, RollupPolicy.FULL);
        }
    }

    public static class GreatGrandchildInvisiblePartial extends Base {
        @Override
        public String name() {
            return "mondrian.AccessControlTest.testRollupPolicyGreatGrandchildInvisible.PARTIAL";
        }

        @Override
        public CatalogMappingSupplier mappingSupplier() {
            Catalog catalog = new CatalogSupplier().get();
            return new SchemaModifiersEmf.AccessControlTestModifier40(catalog, RollupPolicy.PARTIAL);
        }
    }

    public static class GreatGrandchildInvisibleHidden extends Base {
        @Override
        public String name() {
            return "mondrian.AccessControlTest.testRollupPolicyGreatGrandchildInvisible.HIDDEN";
        }

        @Override
        public CatalogMappingSupplier mappingSupplier() {
            Catalog catalog = new CatalogSupplier().get();
            return new SchemaModifiersEmf.AccessControlTestModifier40(catalog, RollupPolicy.HIDDEN);
        }
    }

    // ---- testRollupPolicySimultaneous: AccessControlTestModifier41 ----

    public static class SimultaneousFull extends Base {
        @Override
        public String name() {
            return "mondrian.AccessControlTest.testRollupPolicySimultaneous.FULL";
        }

        @Override
        public CatalogMappingSupplier mappingSupplier() {
            Catalog catalog = new CatalogSupplier().get();
            return new SchemaModifiersEmf.AccessControlTestModifier41(catalog, RollupPolicy.FULL);
        }
    }

    public static class SimultaneousPartial extends Base {
        @Override
        public String name() {
            return "mondrian.AccessControlTest.testRollupPolicySimultaneous.PARTIAL";
        }

        @Override
        public CatalogMappingSupplier mappingSupplier() {
            Catalog catalog = new CatalogSupplier().get();
            return new SchemaModifiersEmf.AccessControlTestModifier41(catalog, RollupPolicy.PARTIAL);
        }
    }

    public static class SimultaneousHidden extends Base {
        @Override
        public String name() {
            return "mondrian.AccessControlTest.testRollupPolicySimultaneous.HIDDEN";
        }

        @Override
        public CatalogMappingSupplier mappingSupplier() {
            Catalog catalog = new CatalogSupplier().get();
            return new SchemaModifiersEmf.AccessControlTestModifier41(catalog, RollupPolicy.HIDDEN);
        }
    }

    // ---- testGoodman: AccessControlTestModifier42 ----

    public static class GoodmanPartial extends Base {
        @Override
        public String name() {
            return "mondrian.AccessControlTest.testGoodman.PARTIAL";
        }

        @Override
        public CatalogMappingSupplier mappingSupplier() {
            Catalog catalog = new CatalogSupplier().get();
            return new SchemaModifiersEmf.AccessControlTestModifier42(catalog, RollupPolicy.PARTIAL);
        }
    }

    public static class GoodmanFull extends Base {
        @Override
        public String name() {
            return "mondrian.AccessControlTest.testGoodman.FULL";
        }

        @Override
        public CatalogMappingSupplier mappingSupplier() {
            Catalog catalog = new CatalogSupplier().get();
            return new SchemaModifiersEmf.AccessControlTestModifier42(catalog, RollupPolicy.FULL);
        }
    }

    public static class GoodmanHidden extends Base {
        @Override
        public String name() {
            return "mondrian.AccessControlTest.testGoodman.HIDDEN";
        }

        @Override
        public CatalogMappingSupplier mappingSupplier() {
            Catalog catalog = new CatalogSupplier().get();
            return new SchemaModifiersEmf.AccessControlTestModifier42(catalog, RollupPolicy.HIDDEN);
        }
    }

    // ---- testRollupPolicyWithNative: AccessControlTestModifier29 ----
    // One instance per (RollupPolicy x defaultMember x hasAll) combination --
    // the original test looped over all 12 and rebuilt the catalog each time.

    private static final String NON_ALL_DEFAULT_MEMBER = "[Store2].[USA].[CA]";

    private abstract static class WithNativeBase extends Base {
        abstract RollupPolicy policy();

        abstract String defaultMember();

        abstract boolean hasAll();

        @Override
        public CatalogMappingSupplier mappingSupplier() {
            Catalog catalog = new CatalogSupplier().get();
            return new SchemaModifiersEmf.AccessControlTestModifier29(catalog, hasAll(), defaultMember(), policy());
        }
    }

    public static class WithNativeFullNonAllDefaultHasAll extends WithNativeBase {
        @Override
        public String name() {
            return "mondrian.AccessControlTest.testRollupPolicyWithNative.FULL.nonAllDefault.hasAll";
        }

        @Override
        RollupPolicy policy() {
            return RollupPolicy.FULL;
        }

        @Override
        String defaultMember() {
            return NON_ALL_DEFAULT_MEMBER;
        }

        @Override
        boolean hasAll() {
            return true;
        }
    }

    public static class WithNativeFullNonAllDefaultNoAll extends WithNativeBase {
        @Override
        public String name() {
            return "mondrian.AccessControlTest.testRollupPolicyWithNative.FULL.nonAllDefault.noAll";
        }

        @Override
        RollupPolicy policy() {
            return RollupPolicy.FULL;
        }

        @Override
        String defaultMember() {
            return NON_ALL_DEFAULT_MEMBER;
        }

        @Override
        boolean hasAll() {
            return false;
        }
    }

    public static class WithNativeFullNoDefaultHasAll extends WithNativeBase {
        @Override
        public String name() {
            return "mondrian.AccessControlTest.testRollupPolicyWithNative.FULL.noDefault.hasAll";
        }

        @Override
        RollupPolicy policy() {
            return RollupPolicy.FULL;
        }

        @Override
        String defaultMember() {
            return null;
        }

        @Override
        boolean hasAll() {
            return true;
        }
    }

    public static class WithNativeFullNoDefaultNoAll extends WithNativeBase {
        @Override
        public String name() {
            return "mondrian.AccessControlTest.testRollupPolicyWithNative.FULL.noDefault.noAll";
        }

        @Override
        RollupPolicy policy() {
            return RollupPolicy.FULL;
        }

        @Override
        String defaultMember() {
            return null;
        }

        @Override
        boolean hasAll() {
            return false;
        }
    }

    public static class WithNativePartialNonAllDefaultHasAll extends WithNativeBase {
        @Override
        public String name() {
            return "mondrian.AccessControlTest.testRollupPolicyWithNative.PARTIAL.nonAllDefault.hasAll";
        }

        @Override
        RollupPolicy policy() {
            return RollupPolicy.PARTIAL;
        }

        @Override
        String defaultMember() {
            return NON_ALL_DEFAULT_MEMBER;
        }

        @Override
        boolean hasAll() {
            return true;
        }
    }

    public static class WithNativePartialNonAllDefaultNoAll extends WithNativeBase {
        @Override
        public String name() {
            return "mondrian.AccessControlTest.testRollupPolicyWithNative.PARTIAL.nonAllDefault.noAll";
        }

        @Override
        RollupPolicy policy() {
            return RollupPolicy.PARTIAL;
        }

        @Override
        String defaultMember() {
            return NON_ALL_DEFAULT_MEMBER;
        }

        @Override
        boolean hasAll() {
            return false;
        }
    }

    public static class WithNativePartialNoDefaultHasAll extends WithNativeBase {
        @Override
        public String name() {
            return "mondrian.AccessControlTest.testRollupPolicyWithNative.PARTIAL.noDefault.hasAll";
        }

        @Override
        RollupPolicy policy() {
            return RollupPolicy.PARTIAL;
        }

        @Override
        String defaultMember() {
            return null;
        }

        @Override
        boolean hasAll() {
            return true;
        }
    }

    public static class WithNativePartialNoDefaultNoAll extends WithNativeBase {
        @Override
        public String name() {
            return "mondrian.AccessControlTest.testRollupPolicyWithNative.PARTIAL.noDefault.noAll";
        }

        @Override
        RollupPolicy policy() {
            return RollupPolicy.PARTIAL;
        }

        @Override
        String defaultMember() {
            return null;
        }

        @Override
        boolean hasAll() {
            return false;
        }
    }

    public static class WithNativeHiddenNonAllDefaultHasAll extends WithNativeBase {
        @Override
        public String name() {
            return "mondrian.AccessControlTest.testRollupPolicyWithNative.HIDDEN.nonAllDefault.hasAll";
        }

        @Override
        RollupPolicy policy() {
            return RollupPolicy.HIDDEN;
        }

        @Override
        String defaultMember() {
            return NON_ALL_DEFAULT_MEMBER;
        }

        @Override
        boolean hasAll() {
            return true;
        }
    }

    public static class WithNativeHiddenNonAllDefaultNoAll extends WithNativeBase {
        @Override
        public String name() {
            return "mondrian.AccessControlTest.testRollupPolicyWithNative.HIDDEN.nonAllDefault.noAll";
        }

        @Override
        RollupPolicy policy() {
            return RollupPolicy.HIDDEN;
        }

        @Override
        String defaultMember() {
            return NON_ALL_DEFAULT_MEMBER;
        }

        @Override
        boolean hasAll() {
            return false;
        }
    }

    public static class WithNativeHiddenNoDefaultHasAll extends WithNativeBase {
        @Override
        public String name() {
            return "mondrian.AccessControlTest.testRollupPolicyWithNative.HIDDEN.noDefault.hasAll";
        }

        @Override
        RollupPolicy policy() {
            return RollupPolicy.HIDDEN;
        }

        @Override
        String defaultMember() {
            return null;
        }

        @Override
        boolean hasAll() {
            return true;
        }
    }

    public static class WithNativeHiddenNoDefaultNoAll extends WithNativeBase {
        @Override
        public String name() {
            return "mondrian.AccessControlTest.testRollupPolicyWithNative.HIDDEN.noDefault.noAll";
        }

        @Override
        RollupPolicy policy() {
            return RollupPolicy.HIDDEN;
        }

        @Override
        String defaultMember() {
            return null;
        }

        @Override
        boolean hasAll() {
            return false;
        }
    }
}
