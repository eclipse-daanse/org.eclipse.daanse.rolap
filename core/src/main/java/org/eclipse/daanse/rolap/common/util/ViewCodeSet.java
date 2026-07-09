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
package org.eclipse.daanse.rolap.common.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds the per-dialect SQL variants of a view or inline source as a {@code dialect-name -> SQL}
 * map. The live dialect is picked at render time by {@code DialectSqlRenderer} (via a
 * {@code FromVariant}/{@code RawVariant} node resolved with {@code chooseVariant}), not here. Used by
 * view/inline rendering.
 */
public class ViewCodeSet {

    /** The per-dialect variants of a mapping-model view/table SQL definition. */
    public static ViewCodeSet fromMappingSqlStatement(
            List<? extends org.eclipse.daanse.rolap.mapping.model.database.source.SqlStatement> sqls) {
        ViewCodeSet codeSet = new ViewCodeSet();
        for (org.eclipse.daanse.rolap.mapping.model.database.source.SqlStatement sql : sqls) {
            for (String dialect : sql.getDialects()) {
                codeSet.put(dialect, sql.getSql());
            }
        }
        return codeSet;
    }

    /** The per-dialect variants of an olap-api expression's SQL statements. */
    public static ViewCodeSet fromOlapSqlStatement(List<org.eclipse.daanse.olap.api.SqlStatement> sqls) {
        ViewCodeSet codeSet = new ViewCodeSet();
        for (org.eclipse.daanse.olap.api.SqlStatement sql : sqls) {
            for (String dialect : sql.getDialects()) {
                codeSet.put(dialect, sql.getSql());
            }
        }
        return codeSet;
    }

    private final Map<String, String> dialectCodes = new HashMap<>();

    public String put(String dialect, String code) {
        return dialectCodes.put(dialect, code);
    }

    /**
     * Exposes the per-dialect {@code dialect-name -> SQL} map for renderer-resolved variant nodes
     * ({@code FromVariant}/{@code RawVariant}) — the dialect pick then happens in {@code DialectSqlRenderer}.
     */
    public Map<String, String> asMap() {
        return Map.copyOf(dialectCodes);
    }
}
