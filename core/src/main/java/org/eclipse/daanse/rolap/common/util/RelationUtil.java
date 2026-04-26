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

public class RelationUtil {

    private RelationUtil() {
        // constructor
    }

    public static org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource find(org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource relationOrJoin, String tableName) {
        switch (relationOrJoin) {
        case org.eclipse.daanse.rolap.mapping.model.database.source.InlineTableSource inlineTable -> {
            return tableName.equals(inlineTable.getAlias()) ? (org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource) relationOrJoin : null;
        }
        case org.eclipse.daanse.rolap.mapping.model.database.source.TableSource table -> {
            if (tableName.equals(table.getTable().getName())) {
                return (org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource) relationOrJoin;
            } else {
                    return null; //old version of code had wrong condition with equals
            }
        }
        case org.eclipse.daanse.rolap.mapping.model.database.source.SqlSelectSource view -> {
            if (tableName.equals(view.getAlias())) {
                return (org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource) relationOrJoin;
            } else {
                return null;
            }
        }
        case org.eclipse.daanse.rolap.mapping.model.database.source.JoinSource join -> {
        	org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource relation = find(left(join), tableName);
            if (relation == null) {
                relation = find(right(join), tableName);
            }
            return (org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource) relation;
        }
        case null, default -> {
        }
        }

        throw new RolapRuntimeException("Rlation: find error");
    }

    public static String getAlias(org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource relation) {
        if (relation instanceof org.eclipse.daanse.rolap.mapping.model.database.source.TableSource table) {
            return (table.getAlias() != null) ? table.getAlias() : table.getTable() != null ? table.getTable().getName() : null;
        }
        else {
            return relation.getAlias();
        }
    }

    public static String getTableName(org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource relation) {
        if (relation instanceof org.eclipse.daanse.rolap.mapping.model.database.source.TableSource table) {
            return table.getTable() != null ? table.getTable().getName() : null;
        }
        else {
            return relation.getAlias();
        }
    }

    public static boolean equals(org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource relation, Object o) {
        if (relation instanceof org.eclipse.daanse.rolap.mapping.model.database.source.SqlSelectSource view) {
            if (o instanceof org.eclipse.daanse.rolap.mapping.model.database.source.SqlSelectSource that) {
                if (!Objects.equals(relation.getAlias(), that.getAlias())) {
                    return false;
                }
                if (
                    view.getSql() == null || that.getSql() == null
                    || view.getSql().getDialectStatements() == null || that.getSql().getDialectStatements() == null
                    || view.getSql().getDialectStatements().size() != that.getSql().getDialectStatements().size()) {
                    return false;
                }
                for (int i = 0; i < view.getSql().getDialectStatements().size(); i++) {
                    if (view.getSql().getDialectStatements().get(i).getSql() == null || that.getSql().getDialectStatements().get(i).getSql() == null || 
                    		!Objects.equals(view.getSql().getDialectStatements().get(i).getSql(), that.getSql().getDialectStatements().get(i).getSql()))
                    {
                        return false;
                    }
                    if (view.getSql().getDialectStatements().get(i).getDialects() == null || that.getSql().getDialectStatements().get(i).getDialects() == null
                        || view.getSql().getDialectStatements().get(i).getDialects().size() != that.getSql().getDialectStatements().get(i).getDialects().size()) {
                        return false;
                    }
                    for (int j = 0; j< view.getSql().getDialectStatements().get(i).getDialects().size(); j++) {
                        if (!view.getSql().getDialectStatements().get(i).getDialects().get(j).equals(that.getSql().getDialectStatements().get(i).getDialects().get(j))) {
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
        if (relation instanceof org.eclipse.daanse.rolap.mapping.model.database.source.TableSource table) {
            if (o instanceof org.eclipse.daanse.rolap.mapping.model.database.source.TableSource that) {
                return table.getTable() != null &&  that.getTable() != null && 
                	table.getTable().getName().equals(that.getTable().getName()) &&
                    Objects.equals(relation.getAlias(), that.getAlias()) &&
                    Objects.equals(table.getTable().getNamespace(), that.getTable().getNamespace());
            } else {
                return false;
            }
        }
        if (relation instanceof org.eclipse.daanse.rolap.mapping.model.database.source.InlineTableSource) {
            if (o instanceof org.eclipse.daanse.rolap.mapping.model.database.source.InlineTableSource that) {
                return relation.getAlias().equals(that.getAlias());
            } else {
                return false;
            }

        }
        return relation == o;
    }

    public static int hashCode(org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource relation) {
        if (relation instanceof org.eclipse.daanse.rolap.mapping.model.database.source.TableSource) {
            return toString(relation).hashCode();
        }
        if (relation instanceof org.eclipse.daanse.rolap.mapping.model.database.source.InlineTableSource) {
            return toString(relation).hashCode();
        }
        return System.identityHashCode(relation);
    }

    private static Object toString(org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource relation) {
        if (relation instanceof org.eclipse.daanse.rolap.mapping.model.database.source.TableSource table) {
            return (table.getTable().getNamespace() == null || table.getTable().getNamespace().getName() == null) ?
                table.getTable().getName() :
                new StringBuilder(table.getTable().getNamespace().getName()).append(".").append(table.getTable().getName()).toString();
        }
        if (relation instanceof org.eclipse.daanse.rolap.mapping.model.database.source.JoinSource join) {
            return new StringBuilder("(").append(left(join)).append(") join (").append(right(join)).append(") on ")
                .append(join.getLeft().getAlias()).append(".").append(join.getLeft().getKey()).append(" = ")
                .append(join.getRight().getAlias()).append(".").append(join.getRight().getKey()).toString();
        }
        if (relation instanceof org.eclipse.daanse.rolap.mapping.model.database.source.InlineTableSource) {
            return "<inline data>";
        }
        return relation.toString();
    }

}
