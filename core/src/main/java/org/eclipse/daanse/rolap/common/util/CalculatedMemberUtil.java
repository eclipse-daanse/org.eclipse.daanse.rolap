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

import org.eclipse.daanse.olap.common.StandardProperty;
import org.eclipse.daanse.rolap.mapping.api.model.CalculatedMemberMapping;
import org.eclipse.daanse.rolap.mapping.api.model.CalculatedMemberPropertyMapping;

public class CalculatedMemberUtil {

    private CalculatedMemberUtil() {
    }

    public static String getFormula(CalculatedMemberMapping calculatedMember) {
    	return calculatedMember.getFormula();
    }

    /**
     * Returns the format string, looking for a property called
     * "FORMAT_STRING" first, then looking for an attribute called
     * "formatString".
     */
    public static String getFormatString(CalculatedMemberMapping calculatedMember) {
        for (CalculatedMemberPropertyMapping prop : calculatedMember.getCalculatedMemberProperties()) {
            if (prop.getName().equals(
                StandardProperty.FORMAT_STRING.getName()))
            {
                return prop.getValue();
            }
        }
        return calculatedMember.getFormatString();
    }
}
