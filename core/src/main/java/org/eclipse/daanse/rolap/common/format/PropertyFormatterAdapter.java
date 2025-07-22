/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2016-2017 Hitachi Vantara and others
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

import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.api.element.Property;
import org.eclipse.daanse.olap.api.formatter.MemberPropertyFormatter;

/**
 * Adapter to comply SPI {@link MemberPropertyFormatter}
 * using the default formatter implementation.
 */
public class PropertyFormatterAdapter implements MemberPropertyFormatter {
    private DefaultFormatter numberFormatter;

    PropertyFormatterAdapter(DefaultFormatter numberFormatter) {
        this.numberFormatter = numberFormatter;
    }

    @Override
    public String format(
        Member member,
        Property property,
        Object propertyValue)
    {
        return numberFormatter.format(propertyValue);
    }
}
