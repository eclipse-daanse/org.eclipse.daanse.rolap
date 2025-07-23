 /*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (c) 2016-2017 Hitachi Vantara.
 * All Rights Reserved.
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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.eclipse.daanse.rolap.mapping.pojo.MemberFormatterMappingImpl;
import org.junit.jupiter.api.Test;

class FormatterCreateContextTest {

	@Test
    void testElementDataShouldSupersedeAttributeData() {
		MemberFormatterMappingImpl elementData =
				MemberFormatterMappingImpl.builder().build();
        elementData.setRef("elementClassName");

        FormatterCreateContext context1 =
            new FormatterCreateContext.Builder("elementName")
            .formatterDef(elementData)
            .formatterAttr("attributeClassName")
            .build();
        FormatterCreateContext context2 =
            new FormatterCreateContext.Builder("elementName")
                .formatterAttr("attributeClassName")
                .build();
        FormatterCreateContext context3 =
            new FormatterCreateContext.Builder("elementName")
                .formatterDef(null)
                .formatterAttr("attributeClassName")
                .build();

        assertEquals("elementClassName", context1.getFormatterClassName());
        assertEquals("attributeClassName", context2.getFormatterClassName());
        assertEquals("attributeClassName", context3.getFormatterClassName());
    }
}
