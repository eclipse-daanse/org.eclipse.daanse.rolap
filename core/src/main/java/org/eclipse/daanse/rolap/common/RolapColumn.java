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
 * ---- All changes after Fork in 2023 ------------------------
 *
 * Project: Eclipse daanse
 *
 * Copyright (c) 2023 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors after Fork in 2023:
 *   SmartCity Jena - initial
 */

package org.eclipse.daanse.rolap.common;

import java.util.List;
import java.util.Objects;

public class RolapColumn extends RolapSqlExpression {

    private String table;
    private String name;


	public RolapColumn(String table, String name) {
        this.table = table;
        this.name = name;
    }

    public String getTable() {
	    return table;
    }

    public String getName() {
	    return name;
    }

    @Override
	public List<org.eclipse.daanse.olap.api.SqlStatement> getSqls() {
		return List.of(RolapSqlStatement.builder()
				.withSql( table == null ? name : new StringBuilder(table).append(".").append(name).toString())
				.withDialects(List.of("generic"))
				.build());
	}

	public void setTable(String table) {
		 this.table =  table;
	}

	@Override
	public boolean equals(Object obj) {
        if (!(obj instanceof RolapColumn that)) {
            return false;
        }
        return getName().equals(that.getName()) &&
            Objects.equals(getTable(), that.getTable());
    }

    @Override
	public int hashCode() {
        return getName().hashCode() ^ (getTable()==null ? 0 : getTable().hashCode());
    }
}
