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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.daanse.olap.api.formatter.CellFormatter;
import org.eclipse.daanse.olap.api.formatter.MemberFormatter;
import org.eclipse.daanse.olap.api.formatter.MemberPropertyFormatter;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class FormatterFactoryTest {

    private FormatterFactory factory = FormatterFactory.instance();

    /**
     * Given that custom formatter class name is specified.
     * <p>
     * When formatter creating is requested,
     * factory should instantiate an object of specified class.
     * </p>
     */
    @Test
    void testShouldCreateFormatterByClassName() {
        FormatterCreateContext cellFormatterContext =
            new FormatterCreateContext.Builder("name")
                .formatterAttr("org.eclipse.daanse.rolap.common.format.CellFormatterTestImpl")
                .build();
        FormatterCreateContext memberFormatterContext =
            new FormatterCreateContext.Builder("name")
                .formatterAttr("org.eclipse.daanse.rolap.common.format.MemberFormatterTestImpl")
                .build();
        FormatterCreateContext propertyFormatterContext =
            new FormatterCreateContext.Builder("name")
                .formatterAttr(
                    "org.eclipse.daanse.rolap.common.format.PropertyFormatterTestImpl")
                .build();

        CellFormatter cellFormatter =
            factory.createCellFormatter(cellFormatterContext);
        MemberFormatter memberFormatter =
            factory.createRolapMemberFormatter(memberFormatterContext);
        MemberPropertyFormatter propertyFormatter =
            factory.createPropertyFormatter(propertyFormatterContext);

        assertNotNull(cellFormatter);
        assertNotNull(memberFormatter);
        assertNotNull(propertyFormatter);
        assertTrue(cellFormatter instanceof CellFormatterTestImpl);
        assertTrue(memberFormatter instanceof MemberFormatterTestImpl);
        assertTrue(propertyFormatter instanceof PropertyFormatterTestImpl);
    }

    /**
     * Given that custom formatter script is specified.
     * <p>
     * When formatter creating is requested,
     * factory should instantiate an object of script based implementation.
     * </p>
     */
    @Test
    @Disabled //has not been fixed during creating Daanse project
    void testShouldCreateFormatterByScript() {
        FormatterCreateContext context =
            new FormatterCreateContext.Builder("name")
                .script("return null;", "JavaScript")
                .build();

        CellFormatter cellFormatter =
            factory.createCellFormatter(context);
        MemberFormatter memberFormatter =
            factory.createRolapMemberFormatter(context);
        MemberPropertyFormatter propertyFormatter =
            factory.createPropertyFormatter(context);

        assertNotNull(cellFormatter);
        assertNotNull(memberFormatter);
        assertNotNull(propertyFormatter);
    }

    /**
     * Given that custom formatter's both class name and script are specified.
     * <p>
     * When formatter creating is requested,
     * factory should instantiate an object of <b>specified class</b>.
     * </p>
     */
    @Test
    void testShouldCreateFormatterByClassNameIfBothSpecified() {
        FormatterCreateContext cellFormatterContext =
            new FormatterCreateContext.Builder("name")
                .formatterAttr("org.eclipse.daanse.rolap.common.format.CellFormatterTestImpl")
                .script("return null;", "JavaScript")
                .build();
        FormatterCreateContext memberFormatterContext =
            new FormatterCreateContext.Builder("name")
                .formatterAttr("org.eclipse.daanse.rolap.common.format.MemberFormatterTestImpl")
                .script("return null;", "JavaScript")
                .build();
        FormatterCreateContext propertyFormatterContext =
            new FormatterCreateContext.Builder("name")
                .formatterAttr(
                    "org.eclipse.daanse.rolap.common.format.PropertyFormatterTestImpl")
                .script("return null;", "JavaScript")
                .build();

        CellFormatter cellFormatter =
            factory.createCellFormatter(cellFormatterContext);
        MemberFormatter memberFormatter =
            factory.createRolapMemberFormatter(memberFormatterContext);
        MemberPropertyFormatter propertyFormatter =
            factory.createPropertyFormatter(propertyFormatterContext);

        assertNotNull(cellFormatter);
        assertNotNull(memberFormatter);
        assertNotNull(propertyFormatter);
        assertTrue(cellFormatter instanceof CellFormatterTestImpl);
        assertTrue(memberFormatter instanceof MemberFormatterTestImpl);
        assertTrue(propertyFormatter instanceof PropertyFormatterTestImpl);
    }

    /**
     * Given that no custom formatter is specified.
     * <p>
     * When formatter creating is requested,
     * factory should return NULL for:
     * <li>{@link CellFormatter}</li>
     * </p>
     */
    @Test
    void testShouldReturnNullIfEmptyContext() {
        FormatterCreateContext context =
            new FormatterCreateContext.Builder("name").build();

        CellFormatter cellFormatter =
            factory.createCellFormatter(context);

        assertNull(cellFormatter);
    }

    /**
     * Given that no custom formatter is specified.
     * <p>
     * When formatter creating is requested,
     * factory should return a default implementation
     * for:
     * <li>{@link MemberPropertyFormatter}</li>
     * <li>{@link MemberFormatter}</li>
     * </p>
     */
    @Test
    void testShouldReturnDefaultFormatterIfEmptyContext() {
        FormatterCreateContext context =
            new FormatterCreateContext.Builder("name").build();

        MemberPropertyFormatter propertyFormatter =
            factory.createPropertyFormatter(context);
        MemberFormatter memberFormatter =
            factory.createRolapMemberFormatter(context);

        assertNotNull(propertyFormatter);
        assertNotNull(memberFormatter);
        assertTrue(propertyFormatter instanceof PropertyFormatterAdapter);
        assertTrue(memberFormatter instanceof DefaultRolapMemberFormatter);
    }
}
