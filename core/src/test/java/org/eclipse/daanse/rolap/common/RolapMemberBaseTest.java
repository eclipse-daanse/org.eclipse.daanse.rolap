 /*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (c) 2016-2017 Hitachi Vantara.
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

package org.eclipse.daanse.rolap.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.api.formatter.MemberFormatter;
import org.eclipse.daanse.olap.api.formatter.MemberPropertyFormatter;
import org.eclipse.daanse.olap.common.StandardProperty;
import org.eclipse.daanse.rolap.common.RolapDimension;
import org.eclipse.daanse.rolap.common.RolapHierarchy;
import org.eclipse.daanse.rolap.common.RolapLevel;
import org.eclipse.daanse.rolap.common.RolapMember;
import org.eclipse.daanse.rolap.common.RolapMemberBase;
import org.eclipse.daanse.rolap.common.RolapProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit Test for {@link RolapMemberBase}.
 */
class RolapMemberBaseTest {

    private static final String PROPERTY_NAME_1 = "property1";
    private static final String PROPERTY_NAME_2 = "property2";
    private static final String PROPERTY_NAME_3 = "property3";
    private static final Object PROPERTY_VALUE_TO_FORMAT =
            "propertyValueToFormat";
    private static final String FORMATTED_PROPERTY_VALUE =
            "formattedPropertyValue";
    private static final Object MEMBER_NAME = "memberName";
    private static final String FORMATTED_CAPTION = "formattedCaption";
    private static final String ROLAP_FORMATTED_CAPTION =
            "rolapFormattedCaption";

    private RolapMemberBase rolapMemberBase;

    // mocks
    private Object memberKey;
    private RolapLevel level;
    private MemberPropertyFormatter propertyFormatter;

    @BeforeEach
    public void beforeEach() {
        level = mock(RolapLevel.class);
        propertyFormatter = mock(MemberPropertyFormatter.class);
        memberKey = Integer.MAX_VALUE;
        RolapHierarchy hierarchy = mock(RolapHierarchy.class);
        RolapDimension dimension = mock(TestPublicRolapDimension.class);

        when(level.getHierarchy()).thenReturn(hierarchy);
        when(hierarchy.getDimension()).thenReturn(dimension);

        rolapMemberBase = new RolapMemberBase(
            mock(RolapMember.class),
            level,
            memberKey,
            null,
            Member.MemberType.REGULAR);
    }

    /**
     * <p>
     * Given rolap member with properties.
     * </p>
     * When the property formatted value is requested,
     * then property formatter should be used to return the value.
     */
    @Test
    void testShouldUsePropertyFormatterWhenPropertyValuesAreRequested() {
        RolapProperty property1 = mock(TestPublicRolapProperty.class);
        RolapProperty property2 = mock(TestPublicRolapProperty.class);
        when(property1.getName()).thenReturn(PROPERTY_NAME_1);
        when(property2.getName()).thenReturn(PROPERTY_NAME_2);
        when(property1.getFormatter()).thenReturn(propertyFormatter);
        RolapProperty[] properties = {property1, property2};
        when(level.getProperties()).thenReturn(properties);
        when(propertyFormatter.format(
            any(Member.class),
            any(),
            eq(PROPERTY_VALUE_TO_FORMAT)))
            .thenReturn(FORMATTED_PROPERTY_VALUE);
        rolapMemberBase.setProperty(PROPERTY_NAME_1, PROPERTY_VALUE_TO_FORMAT);
        rolapMemberBase.setProperty(PROPERTY_NAME_2, PROPERTY_VALUE_TO_FORMAT);

        String formatted1 =
                rolapMemberBase.getPropertyFormattedValue(PROPERTY_NAME_1);
        String formatted2 =
                rolapMemberBase.getPropertyFormattedValue(PROPERTY_NAME_2);
        String formatted3 =
                rolapMemberBase.getPropertyFormattedValue(PROPERTY_NAME_3);

        assertEquals(FORMATTED_PROPERTY_VALUE, formatted1); // formatted
        assertEquals(PROPERTY_VALUE_TO_FORMAT, formatted2); // unformatted
        assertEquals(null, formatted3);                     // not found
    }

    /**
     * <p>
     * Given rolap member.
     * </p>
     * When caption is requested,
     * then member formatter should be used
     * to return the formatted caption value.
     */
    @Test
    void testShouldUseMemberFormatterForCaption() {
        MemberFormatter memberFormatter = mock(MemberFormatter.class);
        when(level.getMemberFormatter()).thenReturn(memberFormatter);
        when(memberFormatter.format(rolapMemberBase))
            .thenReturn(FORMATTED_CAPTION);

        String caption = rolapMemberBase.getCaption();

        assertEquals(FORMATTED_CAPTION, caption);
    }

    /**
     * <p>
     * Given rolap member with no member formatter
     * (This shouldn't happen actually, but just in case).
     * </p>
     * When caption is requested,
     * then member key should be returned.
     */
    @Test
    void testShouldNotFailIfMemberFormatterIsNotPresent() {
        String caption = rolapMemberBase.getCaption();

        assertEquals(String.valueOf(Integer.MAX_VALUE), caption);
    }

    /**
     * <p>
     * Given rolap member with neither caption value nor name specified.
     * </p>
     * When caption raw value is requested,
     * then member key should be returned.
     */
    @Test
    void testShouldReturnMemberKeyIfNoCaptionValueAndNoNamePresent() {
        Object captionValue = rolapMemberBase.getCaptionValue();

        assertNotNull(captionValue);
        assertEquals(memberKey, captionValue);
    }

    /**
     * <p>
     * Given rolap member with no caption value, but with name specified.
     * </p>
     * When caption raw value is requested,
     * then member name should be returned.
     */
    @Test
    void testShouldReturnMemberNameIfCaptionValueIsNotPresent() {
        rolapMemberBase.setProperty(StandardProperty.NAME.getName(), MEMBER_NAME);

        Object captionValue = rolapMemberBase.getCaptionValue();

        assertNotNull(captionValue);
        assertEquals(MEMBER_NAME, captionValue);
    }

    /**
     * <p>
     * Given rolap member with caption value specified.
     * </p>
     * When caption raw value is requested,
     * then the caption value should be returned.
     */
    @Test
    void testShouldReturnCaptionValueIfPresent() {
        rolapMemberBase.setCaptionValue(Integer.MIN_VALUE);

        Object captionValue = rolapMemberBase.getCaptionValue();

        assertNotNull(captionValue);
        assertEquals(Integer.MIN_VALUE, captionValue);
    }
}
