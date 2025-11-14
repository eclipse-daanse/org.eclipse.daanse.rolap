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

import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.rolap.common.RolapRuntimeException;

public class JoinUtil {

    private JoinUtil() {
        // constructor
    }

    public static org.eclipse.daanse.rolap.mapping.model.Query left(org.eclipse.daanse.rolap.mapping.model.JoinQuery join) {
        if (join != null && join.getLeft() != null) {
            return join.getLeft().getQuery();
        }
        throw new RolapRuntimeException("Join left error");
    }

    public static org.eclipse.daanse.rolap.mapping.model.Query right(org.eclipse.daanse.rolap.mapping.model.JoinQuery join) {
        if (join != null && join.getRight() != null) {
            return join.getRight().getQuery();
        }
        throw new RolapRuntimeException("Join right error");
    }

    public static void changeLeftRight(org.eclipse.daanse.rolap.mapping.model.JoinQuery join, org.eclipse.daanse.rolap.mapping.model.Query left, org.eclipse.daanse.rolap.mapping.model.Query right) {
        join.getLeft().setQuery(left);
        join.getRight().setQuery(right);
    }

    /**
     * Returns the alias of the left join key, defaulting to left's
     * alias if left is a table.
     */
    public static String getLeftAlias(org.eclipse.daanse.rolap.mapping.model.JoinQuery join) {
        if (join.getLeft() != null && join.getLeft().getAlias() != null) {
            return join.getLeft().getAlias();
        }
        org.eclipse.daanse.rolap.mapping.model.Query left = left(join);
        if (left instanceof org.eclipse.daanse.rolap.mapping.model.RelationalQuery relation) {
            return RelationUtil.getAlias(relation);
        }
        throw Util.newInternal(
            new StringBuilder("alias is required because ").append(left).append(" is not a table").toString());
    }

    /**
     * Returns the alias of the right join key, defaulting to right's
     * alias if right is a table.
     */
    public static String getRightAlias(org.eclipse.daanse.rolap.mapping.model.JoinQuery join) {
        if (join.getRight() != null && join.getRight().getAlias() != null) {
            return join.getRight().getAlias();
        }
        org.eclipse.daanse.rolap.mapping.model.Query right = right(join);
        if (right instanceof org.eclipse.daanse.rolap.mapping.model.RelationalQuery relation) {
            return RelationUtil.getAlias(relation);
        }
        if (right instanceof org.eclipse.daanse.rolap.mapping.model.JoinQuery j) {
            return getLeftAlias(j);
        }
        throw Util.newInternal(
            new StringBuilder("alias is required because ").append(right).append(" is not a table").toString());
    }

}
