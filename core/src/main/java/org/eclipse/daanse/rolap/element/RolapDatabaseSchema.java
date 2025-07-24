/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation.
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
package org.eclipse.daanse.rolap.element;

import java.util.List;

import org.eclipse.daanse.olap.api.element.DatabaseSchema;
import org.eclipse.daanse.olap.api.element.DatabaseTable;

public class RolapDatabaseSchema implements DatabaseSchema {
	private String name;

	private List<DatabaseTable> dbTables;

	@Override
	public List<DatabaseTable> getDbTables() {
		return dbTables;
	}

	public void setDbTables(List<DatabaseTable> dbTables) {
		this.dbTables = dbTables;
	}

	@Override
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

}
