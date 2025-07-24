/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2001-2005 Julian Hyde
 * Copyright (C) 2005-2017 Hitachi Vantara and others
 * All Rights Reserved.
 *
 * jhyde, 21 December, 2001
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

import java.util.List;

import org.eclipse.daanse.rolap.element.RolapHierarchy;
import org.eclipse.daanse.rolap.element.RolapMember;

/**
 * A MeasureMemberSource implements the {@link MemberReader}
 * interface for the special Measures dimension.
 *
 * Usually when a member is added to the context, the resulting SQL
 * statement has extra filters in its WHERE clause, but for members from this
 * source, but this implementation columns are added to the SELECT list.
 *
 * @author jhyde
 * @since 21 December, 2001
 */
public class MeasureMemberSource extends ArrayMemberSource {
    public MeasureMemberSource(
        RolapHierarchy hierarchy,
        List<RolapMember> members)
    {
        super(hierarchy, members);
    }
}
