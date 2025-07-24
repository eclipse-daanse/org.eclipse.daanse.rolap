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
import org.eclipse.daanse.olap.api.formatter.MemberFormatter;
import org.eclipse.daanse.rolap.element.RolapMemberBase;

/**
 * Default implementation of SPI {@link MemberFormatter}.
 * Used to make a minimum formatting in case no custom formatter is specified.
 *
 * Must be used for {@link RolapMemberBase} only.
 */
public class DefaultRolapMemberFormatter implements MemberFormatter {
    private DefaultFormatter numberFormatter;

    DefaultRolapMemberFormatter(DefaultFormatter numberFormatter) {
        this.numberFormatter = numberFormatter;
    }

    /**
     * Takes rolap member's caption object to format using number formatter
     * (eliminating scientific notations and unwanted decimal values)
     * and returns formatted string value.
     *
     * 
     *   We rely on that this formatter will only be used
     *   for {@link RolapMemberBase} objects formatting.
     *
     *   Because we can't simply fallback to a raw caption,
     *   if we are though in a context of {@link Member#getCaption()},
     *   because it would end up with a stack overflow.
     *
     *   So, now this fromatter set by default
     *   in {@link org.eclipse.daanse.rolap.element.RolapLevel} only,
     *   and IS only used for RolapMemberBase.
     * 
     */
    @Override
    public String format(Member member) {
        if (member instanceof RolapMemberBase rolapMember) {
            return doFormatMember(rolapMember);
        }
        throw new IllegalArgumentException(
            new StringBuilder("Rolap formatter must only be used ")
            .append("for RolapMemberBase formatting").toString());
    }

    private String doFormatMember(RolapMemberBase rolapMember) {
        Object captionValue = rolapMember.getCaptionValue();
        return numberFormatter.format(captionValue);
    }
}
