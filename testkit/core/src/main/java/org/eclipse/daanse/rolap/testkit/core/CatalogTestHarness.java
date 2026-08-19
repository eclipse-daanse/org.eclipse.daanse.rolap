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
package org.eclipse.daanse.rolap.testkit.core;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import javax.sql.DataSource;

import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.eclipse.daanse.sql.dialect.api.DialectFactory;
import org.eclipse.daanse.sql.dialect.api.DialectInitData;
import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.check.model.check.CatalogCheckResult;
import org.eclipse.daanse.olap.check.model.check.CheckExecutionResult;
import org.eclipse.daanse.olap.check.model.check.CheckFailure;
import org.eclipse.daanse.olap.check.model.check.CheckResult;
import org.eclipse.daanse.olap.check.model.check.CheckStatus;
import org.eclipse.daanse.olap.check.model.check.CubeCheckResult;
import org.eclipse.daanse.olap.check.model.check.DimensionCheckResult;
import org.eclipse.daanse.olap.check.model.check.HierarchyCheckResult;
import org.eclipse.daanse.olap.check.model.check.LevelCheckResult;
import org.eclipse.daanse.olap.check.model.check.MemberCheckResult;
import org.eclipse.daanse.olap.check.model.check.OlapCheckSuite;
import org.eclipse.daanse.olap.check.model.check.QueryCheckResult;
import org.eclipse.daanse.olap.check.runtime.api.OlapCheckSuiteSupplier;
import org.eclipse.daanse.olap.testkit.core.OlapCheckSuiteRunner;
import org.eclipse.daanse.rolap.mapping.instance.api.CatalogTestInstance;
import org.eclipse.daanse.rolap.mapping.model.provider.CatalogMappingSupplier;
import org.eclipse.daanse.rolap.testkit.api.CatalogTestSpec;
import org.eclipse.daanse.cwm.testkit.api.DatabaseCheckSuiteSupplier;
import org.eclipse.daanse.jdbc.datasource.testkit.api.ActiveDatabase;
import org.eclipse.daanse.jdbc.datasource.testkit.api.DatabaseProvider;
import org.eclipse.daanse.cwm.testkit.api.DatabaseSupplier;
import org.eclipse.daanse.cwm.testkit.api.DataSupplier;
import org.eclipse.daanse.rolap.testkit.api.LoadScope;
import org.eclipse.daanse.cwm.testkit.data.DataLayer;
import org.eclipse.daanse.cwm.testkit.database.DatabaseCheckExecutor;
import org.eclipse.daanse.cwm.testkit.database.DatabaseLayer;
import org.junit.jupiter.api.DynamicTest;
import org.opentest4j.AssertionFailedError;

/**
 * JUnit {@code @TestFactory} entry point. {@link #discoveredTests()} runs
 * every
 * {@link org.eclipse.daanse.rolap.mapping.instance.api.CatalogTestInstance}
 * on the classpath; {@link #dynamicTests(CatalogTestSpec...)} runs
 * hand-built specs. The DB is picked by
 * {@link DatabaseProvider#selected()}.
 */
public final class CatalogTestHarness {

    private static final Map<String, DatabaseProvider> providerCache = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> loadedSpecs = new ConcurrentHashMap<>();

    private CatalogTestHarness() {
    }

    public static Stream<DynamicTest> discoveredTests() {
        return discoveredTests(LoadScope.PER_CLASS);
    }

    /**
     * Explicit-scope variant. Use when the {@link StackWalker}-based caller
     * inference of {@link #discoveredTests(LoadScope)} is unreliable (e.g., JUnit
     * lifecycles that route through proxies or extensions).
     *
     * @param scope    reload policy
     * @param scopeKey stable key uniquely identifying the scope; e.g. the test
     *                 class's name. Used as part of the cache key.
     */
    public static Stream<DynamicTest> discoveredTests(LoadScope scope, String scopeKey) {
        return run(scope, scopeKey, discoverInstances());
    }

    /**
     * Explicit-scope variant for bespoke specs. See
     * {@link #discoveredTests(LoadScope, String)} for the scopeKey contract.
     */
    public static Stream<DynamicTest> dynamicTests(LoadScope scope, String scopeKey, CatalogTestSpec... specs) {
        return run(scope, scopeKey, toInstances(specs));
    }

    public static Stream<DynamicTest> discoveredTests(LoadScope scope) {
        return run(scope, scopeKey(scope), discoverInstances());
    }

    private static List<CatalogTestInstance> discoverInstances() {
        List<CatalogTestInstance> instances = new ArrayList<>();
        for (CatalogTestInstance instance : ServiceLoader.load(CatalogTestInstance.class)) {
            instances.add(instance);
        }
        return instances;
    }

    /**
     * Adapts a hand-built {@link CatalogTestSpec} to the {@link CatalogTestInstance} SPI so it
     * runs through the same pipeline as discovered instances. Neither
     * {@code databaseSupplier()} nor {@code dbCheckSupplier()} is overridden, so a spec always
     * falls back to the Phase-1 CSV path with no database-shape checks — matching
     * {@link CatalogTestSpec}'s original CSV-only contract.
     */
    private record SpecInstance(CatalogTestSpec spec) implements CatalogTestInstance {
        @Override public String name() { return spec.name(); }
        @Override public CatalogMappingSupplier mappingSupplier() { return spec.mappingSupplier(); }
        @Override public OlapCheckSuiteSupplier checkSuiteSupplier() { return spec.checkSuiteSupplier(); }
        @Override public Map<String, URL> csvResources() { return spec.csvResources(); }
    }

    private static List<CatalogTestInstance> toInstances(CatalogTestSpec... specs) {
        if (specs == null) {
            return List.of();
        }
        List<CatalogTestInstance> instances = new ArrayList<>(specs.length);
        for (CatalogTestSpec spec : specs) {
            instances.add(new SpecInstance(spec));
        }
        return instances;
    }

    /**
     * Phase-2 layered execution, shared by discovered instances and hand-built
     * {@link CatalogTestSpec}s (adapted via {@link SpecInstance}). Routes through
     * {@link DatabaseLayer} / {@link DataLayer} when the instance provides a
     * {@link DatabaseSupplier}; falls back to the Phase-1 {@link CsvLoader} path
     * for instances that only set {@code csvResources()}.
     */
    private static Stream<DynamicTest> run(LoadScope scope, String scopeKey,
            List<CatalogTestInstance> instances) {
        if (instances.isEmpty()) {
            return Stream.empty();
        }
        // One provider, per-catalog isolation via activate(isolationKey).
        // The provider is responsible for handing back a clean DB per key —
        // H2/SQLite use fresh memFS UUIDs; Postgres/MySQL/MariaDB/MSSQL/Oracle
        // use per-key SCHEMA or DATABASE or USER in the shared container.
        DatabaseProvider provider = providerCache.computeIfAbsent("__selected__", k -> DatabaseProvider.selected());
        String dbId = provider.id();

        List<DynamicTest> tests = new ArrayList<>();
        for (CatalogTestInstance instance : instances) {
            ActiveDatabase dbInfo = provider.activate(instance.name());
            DataSource dataSource = dbInfo.dataSource();
            Dialect dialect = dbInfo.dialect();
            String specKey = dbId + "::" + instance.name() + "::" + scopeKey;
            boolean shouldLoad = scope == LoadScope.PER_TEST || loadedSpecs.putIfAbsent(specKey, Boolean.TRUE) == null;
            if (shouldLoad) {
                try {
                    DatabaseSupplier dbSup = instance.databaseSupplier();
                    if (dbSup != null) {
                        // Phase-2 layered path: CWM Schema → DDL → CSV via etl pipeline.
                        DatabaseLayer.apply(dataSource, dialect, dbSup.schema());
                        DataSupplier dataSup = instance.dataSupplier();
                        if (dataSup != null) {
                            DataLayer.apply(dataSource, dialect, dbSup.schema(), dataSup);
                        }
                    } else {
                        // Phase-1 backwards-compat: CsvLoader with row-2 SQL-type CSVs.
                        Map<String, URL> csv = instance.csvResources();
                        if (csv != null && !csv.isEmpty()) {
                            CsvLoader.load(dataSource, new FixedDialectFactory(dialect), csv);
                        }
                    }
                } catch (Exception e) {
                    tests.add(DynamicTest.dynamicTest("[" + dbId + "] " + instance.name() + " » data load", () -> {
                        throw new AssertionError("Layer setup failed", e);
                    }));
                    continue;
                }
            }

            if (instance.mappingSupplier() != null && instance.checkSuiteSupplier() != null) {
                // The ActiveDatabase's pool, not a second one over the same data
                // source: one database per catalog, and their pools add up
                // against the server's connection limit.
                TestContext ctx = new TestContext(dbInfo.connectionPool(), dialect, instance.mappingSupplier());
                OlapCheckSuite suite = instance.checkSuiteSupplier().get();
                List<CheckExecutionResult> results = OlapCheckSuiteRunner.run(suite, (Context<?>) ctx);
                collect(dbId, instance.name(), results, tests);
            }
            DatabaseCheckSuiteSupplier dbCheckSup = instance.dbCheckSupplier();
            if (dbCheckSup != null) {
                tests.addAll(DatabaseCheckExecutor.execute("[" + dbId + "] " + instance.name(), dataSource,
                        dbCheckSup.get()));
            }
        }
        return tests.stream();
    }

    public static Stream<DynamicTest> dynamicTests(CatalogTestSpec... specs) {
        return dynamicTests(LoadScope.PER_CLASS, specs);
    }

    public static Stream<DynamicTest> dynamicTests(LoadScope scope, CatalogTestSpec... specs) {
        return run(scope, scopeKey(scope), toInstances(specs));
    }

    private static String scopeKey(LoadScope scope) {
        return switch (scope) {
        case PER_JVM -> "jvm";
        case PER_CLASS ->
            StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                    .walk(s -> s.map(StackWalker.StackFrame::getDeclaringClass)
                            .filter(c -> c != CatalogTestHarness.class).findFirst())
                    .map(Class::getName).orElse("unknown");
        case PER_TEST -> "test-" + System.nanoTime();
        case NAMED -> throw new IllegalArgumentException("LoadScope.NAMED requires an explicit scopeKey — call "
                + "discoveredTests(LoadScope.NAMED, scopeName) or "
                + "dynamicTests(LoadScope.NAMED, scopeName, specs).");
        };
    }

    /**
     * Evict every cached {@code (db, instance.name, scopeKey)} entry whose scopeKey
     * equals {@code namedScope}. After this call, the next test referring to the
     * same name will trigger a fresh CSV load.
     *
     * <p>
     * Designed to be called from {@code @AfterAll} (per-class) or
     * {@code @AfterEach} (per-test) hooks, or automatically by
     * {@link TestKitLifecycleExtension}.
     *
     * @return the number of cached entries that were evicted
     */
    public static int releaseScope(String namedScope) {
        if (namedScope == null) {
            return 0;
        }
        String suffix = "::" + namedScope;
        int[] count = { 0 };
        loadedSpecs.keySet().removeIf(k -> {
            if (k.endsWith(suffix)) {
                count[0]++;
                return true;
            }
            return false;
        });
        return count[0];
    }

    private static void collect(String dbId, String specName, List<CheckExecutionResult> results,
            List<DynamicTest> out) {
        for (CheckExecutionResult exec : results) {
            String head = "[" + dbId + "] " + specName + " » " + safe(exec.getName());
            // If the suite produced no detailed results, surface the failure as a single
            // rollup so it isn't silently lost.
            if (Boolean.FALSE.equals(exec.isSuccess()) && exec.getCheckResults().isEmpty()) {
                out.add(DynamicTest.dynamicTest(head + " (suite)", () -> {
                    throw new AssertionError("Check execution failed: " + exec.getFailureCount() + " failure(s)");
                }));
            }
            for (CheckResult r : exec.getCheckResults()) {
                collectResult(head, r, out);
            }
        }
    }

    private static void collectResult(String breadcrumb, CheckResult r, List<DynamicTest> out) {
        String here = breadcrumb + " » " + safe(r.getCheckName());
        out.add(DynamicTest.dynamicTest(here, () -> assertOk(breadcrumb, r)));
        switch (r) {
        case CatalogCheckResult cat -> {
            cat.getCubeResults().forEach(c -> collectResult(here, c, out));
            cat.getQueryResults().forEach(q -> collectResult(here, q, out));
        }
        case CubeCheckResult cube -> {
            cube.getDimensionResults().forEach(d -> collectResult(here, d, out));
            cube.getMeasureResults().forEach(m -> collectResult(here, m, out));
            cube.getKpiResults().forEach(k -> collectResult(here, k, out));
            cube.getNamedSetResults().forEach(n -> collectResult(here, n, out));
            cube.getDrillThroughActionResults().forEach(d -> collectResult(here, d, out));
        }
        case DimensionCheckResult dim -> dim.getHierarchyResults().forEach(h -> collectResult(here, h, out));
        case HierarchyCheckResult hier -> hier.getLevelResults().forEach(l -> collectResult(here, l, out));
        case LevelCheckResult lvl -> lvl.getMemberResults().forEach(m -> collectResult(here, m, out));
        case QueryCheckResult q -> {
            q.getCellResults().forEach(c -> collectResult(here, c, out));
            q.getAxisResults().forEach(a -> collectResult(here, a, out));
        }
        case MemberCheckResult mem -> {
            /* leaf */ }
        case CheckFailure cf -> {
            /* leaf */ }
        default -> {
            /* no children */ }
        }
    }

    /**
     * Throws a structured {@link AssertionFailedError} — expected/actual as real fields, not
     * baked into the message text — so IDEs and JUnit's own diff view can render a proper
     * comparison. Covers the fallback arm too: previously that was a bare
     * {@code "check failed: <name>"} with no values at all.
     */
    private static void assertOk(String breadcrumb, CheckResult r) {
        if (r.getStatus() != CheckStatus.FAILURE) {
            return;
        }
        String checkName = safe(r.getCheckName());
        if (r.isAbsent()) {
            throw new AssertionFailedError("check '%s' in %s".formatted(checkName, breadcrumb), "present", "absent");
        }
        throw switch (r) {
        case CheckFailure cf -> {
            String message = "check '%s' in %s".formatted(checkName, breadcrumb)
                    + (cf.getMessage() != null ? ": " + cf.getMessage() : "")
                    + (cf.getException() != null ? " (cause: " + cf.getException() + ")" : "");
            yield new AssertionFailedError(message, safe(cf.getExpectedValue()), safe(cf.getActualValue()));
        }
        case QueryCheckResult qr when !qr.isExecutedSuccessfully() -> {
            String message = "query check '%s' in %s".formatted(checkName, breadcrumb)
                    + (qr.getCheckDescription() != null && !qr.getCheckDescription().isBlank()
                            ? ": " + qr.getCheckDescription()
                            : "");
            yield new AssertionFailedError(message, "executed successfully", "did not execute successfully");
        }
        case org.eclipse.daanse.olap.check.model.check.CellCheckResult cr -> new AssertionFailedError(
                "cell check '%s' in %s".formatted(checkName, breadcrumb),
                renderExpected(cr), renderActual(cr));
        case org.eclipse.daanse.olap.check.model.check.AxisCheckResult ar -> new AssertionFailedError(
                "axis check '%s' in %s (axis=%d)".formatted(checkName, breadcrumb, ar.getAxisIndex()),
                renderExpected(ar), renderActual(ar));
        default -> new AssertionFailedError(
                "check '%s' in %s".formatted(checkName, breadcrumb), CheckStatus.SUCCESS, r.getStatus());
        };
    }

    private static String renderExpected(org.eclipse.daanse.olap.check.model.check.CellCheckResult cr) {
        return safe(cr.getExpectedValue());
    }

    private static String renderActual(org.eclipse.daanse.olap.check.model.check.CellCheckResult cr) {
        return safe(cr.getActualValue());
    }

    private static String renderExpected(org.eclipse.daanse.olap.check.model.check.AxisCheckResult ar) {
        return "positions=%d, firstUN=%s".formatted(ar.getExpectedPositionCount(),
                safe(ar.getExpectedFirstMemberUniqueName()));
    }

    private static String renderActual(org.eclipse.daanse.olap.check.model.check.AxisCheckResult ar) {
        return "positions=%d, firstUN=%s".formatted(ar.getActualPositionCount(),
                safe(ar.getActualFirstMemberUniqueName()));
    }

    private static String safe(String s) {
        return s == null ? "?" : s;
    }

    private record FixedDialectFactory(Dialect dialect) implements DialectFactory {
        @Override
        public Dialect createDialect(DialectInitData init) {
            return dialect;
        }
    }
}
