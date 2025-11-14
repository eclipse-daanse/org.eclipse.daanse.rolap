/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   SmartCity Jena, Stefan Bischof - initial
 *
 */
package org.eclipse.daanse.rolap.common;

import static org.eclipse.daanse.rolap.common.util.RelationUtil.getAlias;

import java.util.Map;

public class RelNode {
    /**
     * Finds a RelNode by table name or, if that fails, by table alias
     * from a map of RelNodes.
     *
     * @param table Is supposed a MappingTableQuery
     * @param map Names of tables and RelNode pairs
     */
    public static RelNode lookup(
        org.eclipse.daanse.rolap.mapping.model.RelationalQuery table,
        Map<String, RelNode> map)
    {
        RelNode relNode;
        if (table instanceof org.eclipse.daanse.rolap.mapping.model.TableQuery t) {
            relNode = map.get(t.getTable().getName());
            if (relNode != null) {
                return relNode;
            }
        }
        return map.get(getAlias(table));
    }

    private int depth;
    private String alias;
    private org.eclipse.daanse.rolap.mapping.model.RelationalQuery table;

    public RelNode(String alias, int depth) {
        this.alias = alias;
        this.depth = depth;
    }

    public void setTable(org.eclipse.daanse.rolap.mapping.model.RelationalQuery table) {
        this.table = table;
    }

    public int getDepth() {
        return depth;
    }
}
