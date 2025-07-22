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

public class RolapSqlStatement implements org.eclipse.daanse.olap.api.SqlStatement {

    private List<String> dialects;
    private String sql;

    private RolapSqlStatement(Builder builder) {
        this.dialects = builder.dialects;
        this.sql = builder.sql;
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<String> getDialects() {
        return dialects;
    }

    public String getSql() {
        return sql;
    }

    public void setDialects(List<String> dialects) {
        this.dialects = dialects;
    }

    public void setSql(String sql) {
        this.sql = sql;
    }

    public static final class Builder {

        private List<String> dialects;
        private String sql;

        public Builder withDialects(List<String> dialects) {
            this.dialects = dialects;
            return this;
        }

        public Builder withSql(String sql) {
            this.sql = sql;
            return this;
        }

        public RolapSqlStatement build() {
            return new RolapSqlStatement(this);
        }
    }


}
