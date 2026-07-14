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
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.rolap.common.sqlbuild;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Pins the Dialect confinement: producers see only the narrow {@link SqlQueryCapabilities} view;
 * the full {@code Dialect} may be imported ONLY at the render/executor seam and the few
 * documented residuals below. A new import outside the allowlist fails this test — route the
 * need through {@link SqlQueryCapabilities} (planner probes) or the renderer (SQL spelling)
 * instead.
 */
class DialectConfinementInvariantTest {

    private static final String DIALECT_IMPORT = "import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;";

    /** Repo-relative to core/src/main/java/org/eclipse/daanse/rolap/. */
    private static final Set<String> ALLOWED = Set.of(
        // --- render/executor seam (permanent) ---
        "common/SqlRender.java",                          // the ONE DialectSqlRenderer construction site
        "common/SqlStatement.java",                       // result-read type mapping (dialect.getType)
        "common/result/RolapCell.java",                   // JDBC resultset-concurrency probe at execution
        "common/sqlbuild/CommentedSqlLog.java",           // diagnostic renderer copy
        "common/writeback/BatchInsertEmitter.java",       // InsertStatement render seam
        "core/internal/BasicContext.java",                // dialect creation (DialectFactory)
        // --- the narrow-view adapter itself (permanent) ---
        "common/sqlbuild/QueryBuildContext.java",
        "common/sql/SqlQueryCapabilities.java",
        "common/sql/DialectSqlQueryCapabilities.java",
        "common/result/FastBatchingCellReader.java",      // getDialect() accessor feeding seam + adapter
        // --- measure/expression render channel: Aggregator.getExpression needs the dialect until
        //     every aggregator has a node form (retires with the distinct-recorder teardown) ---
        "common/sqlbuild/AggregateSqlMapper.java",
        "common/sqlbuild/JoinPlanner.java",
        "common/agg/AbstractQuerySpec.java",
        "common/agg/DrillThroughQuerySpec.java",
        "common/SqlTupleReader.java",
        // --- scheduled removals (tracked in docs/analysis/dialect-constraint-sql-api.md) ---
        "common/aggmatcher/AggStar.java",                 // P1.3: quoteIdentifier -> column node
        "common/star/RolapStar.java",                     // P1.3: generateExprString debug shim
        "common/util/SqlExpressionResolver.java",         // P1.3: legacy render shim
        "element/RolapCube.java",                         // writeback render seam (fact view rendered once)
        // --- permanent out-of-scope: DDL/DBA suggestion-text tool, not on the query pipeline ---
        "common/aggmatcher/AggGen.java");

    @Test
    void dialectImportsAreConfinedToTheAllowlist() throws IOException {
        Path root = Path.of("src/main/java/org/eclipse/daanse/rolap");
        assertTrue(Files.isDirectory(root), "run from the core module root; missing " + root);
        List<String> violations;
        try (Stream<Path> files = Files.walk(root)) {
            violations = files
                .filter(p -> p.toString().endsWith(".java"))
                .filter(p -> {
                    try {
                        return Files.readString(p).contains(DIALECT_IMPORT);
                    } catch (IOException e) {
                        throw new java.io.UncheckedIOException(e);
                    }
                })
                .map(p -> root.relativize(p).toString().replace('\\', '/'))
                .filter(rel -> !ALLOWED.contains(rel))
                .sorted()
                .toList();
        }
        assertTrue(violations.isEmpty(),
            "Dialect imported outside the confinement allowlist (use SqlQueryCapabilities for planner"
                + " probes, the renderer for spelling): " + violations);
    }

    @Test
    void allowlistCarriesNoStaleEntries() throws IOException {
        Path root = Path.of("src/main/java/org/eclipse/daanse/rolap");
        assertTrue(Files.isDirectory(root), "run from the core module root; missing " + root);
        List<String> stale = ALLOWED.stream()
            .filter(rel -> {
                try {
                    Path p = root.resolve(rel);
                    return !Files.exists(p) || !Files.readString(p).contains(DIALECT_IMPORT);
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            })
            .sorted()
            .toList();
        assertTrue(stale.isEmpty(),
            "allowlist entries without a live Dialect import (remove them so the confinement keeps"
                + " ratcheting): " + stale);
    }
}
