/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation.
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
package org.eclipse.daanse.rolap.common.util;

import static org.eclipse.daanse.rolap.common.util.JoinUtil.left;
import static org.eclipse.daanse.rolap.common.util.JoinUtil.right;

import java.util.Objects;

import org.eclipse.daanse.rolap.common.RolapRuntimeException;
import org.eclipse.daanse.rolap.mapping.api.model.InlineTableQueryMapping;
import org.eclipse.daanse.rolap.mapping.api.model.JoinQueryMapping;
import org.eclipse.daanse.rolap.mapping.api.model.QueryMapping;
import org.eclipse.daanse.rolap.mapping.api.model.RelationalQueryMapping;
import org.eclipse.daanse.rolap.mapping.api.model.SqlSelectQueryMapping;
import org.eclipse.daanse.rolap.mapping.api.model.TableQueryMapping;

public class RelationUtil {

    private RelationUtil() {
        // constructor
    }

    public static RelationalQueryMapping find(QueryMapping relationOrJoin, String tableName) {
        if (relationOrJoin instanceof InlineTableQueryMapping inlineTable) {
            return tableName.equals(inlineTable.getAlias()) ? (RelationalQueryMapping) relationOrJoin : null;
        }
        if (relationOrJoin instanceof TableQueryMapping table) {
            if (tableName.equals(table.getTable().getName())) {
                return (RelationalQueryMapping) relationOrJoin;
            } else {
                    return null; //old version of code had wrong condition with equals
            }
        }
        if (relationOrJoin instanceof SqlSelectQueryMapping view) {
            if (tableName.equals(view.getAlias())) {
                return (RelationalQueryMapping) relationOrJoin;
            } else {
                return null;
            }
        }
        if (relationOrJoin instanceof JoinQueryMapping join) {
            QueryMapping relation = find(left(join), tableName);
            if (relation == null) {
                relation = find(right(join), tableName);
            }
            return (RelationalQueryMapping) relation;

        }

        throw new RolapRuntimeException("Rlation: find error");
    }

    public static String getAlias(RelationalQueryMapping relation) {
        if (relation instanceof TableQueryMapping table) {
            return (table.getAlias() != null) ? table.getAlias() : table.getTable() != null ? table.getTable().getName() : null;
        }
        else {
            return relation.getAlias();
        }
    }

    public static String getTableName(RelationalQueryMapping relation) {
        if (relation instanceof TableQueryMapping table) {
            return table.getTable() != null ? table.getTable().getName() : null;
        }
        else {
            return relation.getAlias();
        }
    }

    public static boolean equals(RelationalQueryMapping relation, Object o) {
        if (relation instanceof SqlSelectQueryMapping view) {
            if (o instanceof SqlSelectQueryMapping that) {
                if (!Objects.equals(relation.getAlias(), that.getAlias())) {
                    return false;
                }
                if (
                    view.getSql() == null || that.getSql() == null
                    || view.getSql().getSqlStatements() == null || that.getSql().getSqlStatements() == null
                    || view.getSql().getSqlStatements().size() != that.getSql().getSqlStatements().size()) {
                    return false;
                }
                for (int i = 0; i < view.getSql().getSqlStatements().size(); i++) {
                    if (view.getSql().getSqlStatements().get(i).getSql() == null || that.getSql().getSqlStatements().get(i).getSql() == null || 
                    		!Objects.equals(view.getSql().getSqlStatements().get(i).getSql(), that.getSql().getSqlStatements().get(i).getSql()))
                    {
                        return false;
                    }
                    if (view.getSql().getSqlStatements().get(i).getDialects() == null || that.getSql().getSqlStatements().get(i).getDialects() == null
                        || view.getSql().getSqlStatements().get(i).getDialects().size() != that.getSql().getSqlStatements().get(i).getDialects().size()) {
                        return false;
                    }
                    for (int j = 0; j< view.getSql().getSqlStatements().get(i).getDialects().size(); j++) {
                        if (!view.getSql().getSqlStatements().get(i).getDialects().get(j).equals(that.getSql().getSqlStatements().get(i).getDialects().get(j))) {
                            return false;
                        }
                    }
                    return true;
                }
                return true;
            } else {
                return false;
            }
        }
        if (relation instanceof TableQueryMapping table) {
            if (o instanceof TableQueryMapping that) {
                return table.getTable() != null &&  that.getTable() != null && 
                	table.getTable().getName().equals(that.getTable().getName()) &&
                    Objects.equals(relation.getAlias(), that.getAlias()) &&
                    Objects.equals(table.getTable().getSchema(), that.getTable().getSchema());
            } else {
                return false;
            }
        }
        if (relation instanceof InlineTableQueryMapping) {
            if (o instanceof InlineTableQueryMapping that) {
                return relation.getAlias().equals(that.getAlias());
            } else {
                return false;
            }

        }
        return relation == o;
    }

    public static int hashCode(RelationalQueryMapping relation) {
        if (relation instanceof TableQueryMapping) {
            return toString(relation).hashCode();
        }
        if (relation instanceof InlineTableQueryMapping) {
            return toString(relation).hashCode();
        }
        return System.identityHashCode(relation);
    }

    private static Object toString(RelationalQueryMapping relation) {
        if (relation instanceof TableQueryMapping table) {
            return (table.getTable().getSchema() == null || table.getTable().getSchema().getName() == null) ?
                table.getTable().getName() :
                new StringBuilder(table.getTable().getSchema().getName()).append(".").append(table.getTable().getName()).toString();
        }
        if (relation instanceof JoinQueryMapping join) {
            return new StringBuilder("(").append(left(join)).append(") join (").append(right(join)).append(") on ")
                .append(join.getLeft().getAlias()).append(".").append(join.getLeft().getKey()).append(" = ")
                .append(join.getRight().getAlias()).append(".").append(join.getRight().getKey()).toString();
        }
        if (relation instanceof InlineTableQueryMapping) {
            return "<inline data>";
        }
        return relation.toString();
    }

}
