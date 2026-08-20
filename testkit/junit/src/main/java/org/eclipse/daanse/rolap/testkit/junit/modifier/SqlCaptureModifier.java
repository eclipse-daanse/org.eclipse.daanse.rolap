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
package org.eclipse.daanse.rolap.testkit.junit.modifier;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Predicate;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.rolap.common.RolapUtil;
import org.eclipse.daanse.rolap.testkit.junit.api.ContextModifier;

/**
 * Captures every SQL statement the engine emits for the test's context.
 *
 * <p>Declare it and inject it:
 *
 * <pre>{@code
 * @RolapContextTest(value = SchoolInstance.class, modifiers = SqlCaptureModifier.class)
 * class MemberSqlTest {
 *     @Test
 *     void capturesMemberSql(Connection conn, SqlCaptureModifier sql) {
 *         conn.execute(conn.parseQuery("SELECT ..."));
 *         assertThat(sql.captured()).isNotEmpty();
 *     }
 * }
 * }</pre>
 *
 * <p>Uses the per-context {@code RolapUtil.setHook} seam (keyed by context,
 * so parallel tests don't cross-talk); the backing queue is thread-safe.
 * Deregistered when the extension calls {@link #close()}.
 */
public final class SqlCaptureModifier implements ContextModifier {

    private final ConcurrentLinkedQueue<String> statements = new ConcurrentLinkedQueue<>();
    private Context<?> context;

    @Override
    public void apply(Context<?> context) {
        this.context = context;
        RolapUtil.setHook(context, statements::add);
    }

    @Override
    public void close() {
        RolapUtil.setHook(context, null);
    }

    /** All statements captured since the context was built (or {@link #clear()}). */
    public List<String> captured() {
        return List.copyOf(statements);
    }

    /** Drops everything captured so far; the hook stays registered. */
    public void clear() {
        statements.clear();
    }

    /** The most recent statement matching the predicate, if any. */
    public Optional<String> lastMatching(Predicate<String> predicate) {
        return statements.stream().filter(predicate).reduce((first, second) -> second);
    }
}
