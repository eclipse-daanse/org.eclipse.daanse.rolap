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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.api.execution.Statement;
import org.eclipse.daanse.olap.api.query.component.Query;
import org.eclipse.daanse.olap.api.result.Result;
import org.eclipse.daanse.olap.common.ConfigConstants;
import org.eclipse.daanse.olap.execution.ExecutionImpl;
import org.opentest4j.AssertionFailedError;

/**
 * Verifies that native evaluation doesn't change a query's result -
 * {@code NativeVerify.assertSameNativeAndNot(context, mdx, message)} replaces the legacy
 * {@code TestUtil.verifySameNativeAndNot(connection, query, message)}.
 *
 * <p>
 * Runs {@code mdx} once with {@code ENABLE_NATIVE_CROSS_JOIN}, {@code ENABLE_NATIVE_FILTER},
 * {@code ENABLE_NATIVE_NON_EMPTY} and {@code ENABLE_NATIVE_TOP_COUNT} all forced on, and once
 * with all four forced off, and fails unless the two renderings are character-for-character
 * identical - i.e. the native SQL-pushdown code path agrees with the calc-engine one.
 * </p>
 *
 * <p>
 * Unlike the legacy version, which left the four switches at {@code false} once it returned,
 * each run is scoped with {@link ConfigOverride}: {@code context}'s configuration is back to
 * whatever it was before this call by the time it returns.
 * </p>
 */
public final class NativeVerify {

    private NativeVerify() {
    }

    /**
     * @param context the context whose native-evaluation switches are flipped for the two runs
     * @param mdx     the query to run both ways
     * @param message included in the failure text if the two results differ; may be null
     */
    public static void assertSameNativeAndNot(Context<?> context, String mdx, String message) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(mdx, "mdx");
        Connection connection = context.getConnectionWithDefaultRole();

        String nativeResult;
        try (ConfigOverride override = ConfigOverride.of(context)
                .set(ConfigConstants.ENABLE_NATIVE_CROSS_JOIN, true)
                .set(ConfigConstants.ENABLE_NATIVE_FILTER, true)
                .set(ConfigConstants.ENABLE_NATIVE_NON_EMPTY, true)
                .set(ConfigConstants.ENABLE_NATIVE_TOP_COUNT, true)) {
            nativeResult = renderGrid(execute(connection, mdx));
        }

        String nonNativeResult;
        try (ConfigOverride override = ConfigOverride.of(context)
                .set(ConfigConstants.ENABLE_NATIVE_CROSS_JOIN, false)
                .set(ConfigConstants.ENABLE_NATIVE_FILTER, false)
                .set(ConfigConstants.ENABLE_NATIVE_NON_EMPTY, false)
                .set(ConfigConstants.ENABLE_NATIVE_TOP_COUNT, false)) {
            nonNativeResult = renderGrid(execute(connection, mdx));
        }

        if (!nativeResult.equals(nonNativeResult)) {
            throw mismatch(message, mdx, nativeResult, nonNativeResult);
        }
    }

    private static Result execute(Connection connection, String mdx) {
        Query query = connection.parseQuery(mdx);
        Statement statement = query.getStatement();
        return statement.getDaanseConnection().execute(new ExecutionImpl(statement, Optional.empty()));
    }

    private static String renderGrid(Result result) {
        StringWriter sw = new StringWriter();
        result.print(new PrintWriter(sw));
        return sw.toString();
    }

    private static AssertionFailedError mismatch(String message, String mdx, String nativeResult,
            String nonNativeResult) {
        String diff = GridDiff.render(nativeResult, nonNativeResult);
        StringBuilder text = new StringBuilder();
        if (message != null && !message.isEmpty()) {
            text.append(message).append(System.lineSeparator());
        }
        text.append("native and non-native results did not match").append(System.lineSeparator())
                .append("MDX:").append(System.lineSeparator()).append(mdx).append(System.lineSeparator())
                .append(System.lineSeparator()).append(diff);
        return new AssertionFailedError(text.toString(), nativeResult, nonNativeResult);
    }
}
