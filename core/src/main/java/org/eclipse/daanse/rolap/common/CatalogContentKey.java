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
 *   SmartCity Jena - initial
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.rolap.common;

import org.eclipse.daanse.rolap.mapping.api.model.CatalogMapping;

public record CatalogContentKey(String schemaName, int schemaMappingHash) {
	static CatalogContentKey create(CatalogMapping catalogMapping) {

		int hash = System.identityHashCode(catalogMapping);
		return new CatalogContentKey(catalogMapping.getName(), hash);
	}
}
