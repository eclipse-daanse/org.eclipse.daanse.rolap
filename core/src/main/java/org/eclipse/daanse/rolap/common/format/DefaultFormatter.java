/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2005-2017 Hitachi Vantara and others
 * All Rights Reserved.
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

package org.eclipse.daanse.rolap.common.format;

import java.math.BigDecimal;

/**
 * Default formatter which can be used for different rolap member properties.
 */
public class DefaultFormatter {

    /**
     * Numbers are a special case. We don't want any
     * scientific notations, as well as inaccurate decimal values.
     * So we wrap in a BigDecimal, and format before calling toString.
     *
     * @param value generic value to be formatted
     * @return formatted value
     */
    public String format(Object value) {
        if (value != null) {
            if (value instanceof Number) {
                BigDecimal numberValue =
                        new BigDecimal(value.toString()).stripTrailingZeros();
                return numberValue.toPlainString();
            }
            return value.toString();
        }
        return null;
    }
}
