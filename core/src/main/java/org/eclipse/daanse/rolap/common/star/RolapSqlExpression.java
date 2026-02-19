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
package org.eclipse.daanse.rolap.common.star;

import java.util.List;

import org.eclipse.daanse.olap.api.sql.SortingDirection;
import org.eclipse.daanse.rolap.common.RolapSqlStatement;

public class RolapSqlExpression implements org.eclipse.daanse.olap.api.sql.SqlExpression{
	private List<org.eclipse.daanse.olap.api.SqlStatement> sqls;
    private SortingDirection sortingDirection;
    
    public RolapSqlExpression() {
    }
    public RolapSqlExpression(org.eclipse.daanse.rolap.mapping.model.SQLExpressionColumn scm) {
        this(scm, SortingDirection.ASC);
    }
    
    public RolapSqlExpression(org.eclipse.daanse.rolap.mapping.model.SQLExpressionColumn scm, SortingDirection sortingDirection) {
        if (scm.getSqls() != null) {
            this.sqls = scm.getSqls().stream().map(ex -> (org.eclipse.daanse.olap.api.SqlStatement)RolapSqlStatement.builder().withDialects(ex.getDialects()).withSql(ex.getSql()).build()).toList();
            this.sortingDirection = sortingDirection;
        }
        else {
            sqls = List.of();
        }
        
    }

    public List<org.eclipse.daanse.olap.api.SqlStatement> getSqls() {
        return sqls;
    }

    public void setSqls(List<org.eclipse.daanse.olap.api.SqlStatement> sqls) {
        this.sqls = sqls;
    }

    @Override
    public SortingDirection getSortingDirection() {
        return sortingDirection;
    }

    public void setSortingDirection(SortingDirection sortingDirection) {
        this.sortingDirection = sortingDirection;
    }
}
