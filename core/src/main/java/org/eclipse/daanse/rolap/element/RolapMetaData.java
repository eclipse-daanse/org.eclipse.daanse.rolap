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
package org.eclipse.daanse.rolap.element;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.daanse.olap.api.element.MetaData;
import org.eclipse.daanse.olap.element.OlapMetaData;

public class RolapMetaData extends OlapMetaData {

	public RolapMetaData() {
		super(Map.of());
	}

	public RolapMetaData(Map<String, Object> map) {
		super(map);
	}

	public static MetaData createMetaData(List<? extends org.eclipse.daanse.rolap.mapping.model.Annotation> annotationMappings) {
		if (annotationMappings == null || annotationMappings.isEmpty()) {
			return OlapMetaData.empty();
		}

		// Use linked hash map because it retains order.
		final Map<String, Object> map = new LinkedHashMap<>();
		for (org.eclipse.daanse.rolap.mapping.model.Annotation annotation : annotationMappings) {
			final String name = annotation.getName();
			final String value = annotation.getValue();
			map.put(name, value);
		}
		return new RolapMetaData(map);
	}
}
