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

import java.util.Map;
import java.util.stream.Collectors;


public class TableUtil {
	public static Map<String, String> getHintMap(org.eclipse.daanse.rolap.mapping.model.TableQuery table) {

		if (table.getOptimizationHints() == null) {
			return Map.of();
		}
		return table.getOptimizationHints().stream()
				.collect(Collectors.toMap(org.eclipse.daanse.rolap.mapping.model.TableQueryOptimizationHint::getType, org.eclipse.daanse.rolap.mapping.model.TableQueryOptimizationHint::getValue));
	}

    public static String getFilter(org.eclipse.daanse.rolap.mapping.model.TableQuery table) {
        return (table.getSqlWhereExpression() == null) ? null : table.getSqlWhereExpression().getSql();
    }
}
