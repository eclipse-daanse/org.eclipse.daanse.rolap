/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2004-2005 TONBELLER AG
 * Copyright (C) 2006-2017 Hitachi Vantara and others
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

package org.eclipse.daanse.rolap.common.sql;

import org.eclipse.daanse.rolap.api.element.RolapMember;
import org.eclipse.daanse.rolap.common.aggmatcher.AggStar;
import org.eclipse.daanse.rolap.element.RolapCube;

/**
 * Restricts the SQL result of a MembersChildren query in SqlMemberSource.
 *
 * @see org.eclipse.daanse.rolap.common.member.SqlMemberSource
 *
 * @author av
 * @since Nov 2, 2005
 */
public interface MemberChildrenConstraint extends SqlConstraint {

    /**
     * The parent restriction as a {@link ConstraintContribution} (builder {@code WHERE} predicate +
     * tables to join), so the {@code sqlbuild} mappers can build the member-children SELECT.
     * <p>
     * Default returns {@link java.util.Optional#empty()} — "not expressible on the builder; use the
     * {@code QueryRecorder} fallback path". Constraints that can translate override this.
     */
    default java.util.Optional<ConstraintContribution> toContribution(
        RolapCube baseCube,
        AggStar aggStar,
        RolapMember parent)
    {
        return java.util.Optional.empty();
    }

}
