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

import java.util.List;

import org.eclipse.daanse.rolap.mapping.api.model.SQLExpressionColumnMapping;

public class RolapSqlExpression implements org.eclipse.daanse.olap.api.SqlExpression{
    private List<org.eclipse.daanse.olap.api.SqlStatement> sqls;

    public RolapSqlExpression() {
    }
    
    public RolapSqlExpression(SQLExpressionColumnMapping scm) {
        if (scm.getSqls() != null) {
            sqls = scm.getSqls().stream().map(ex -> (org.eclipse.daanse.olap.api.SqlStatement)RolapSqlStatement.builder().withDialects(ex.getDialects()).withSql(ex.getSql()).build()).toList();
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
}
