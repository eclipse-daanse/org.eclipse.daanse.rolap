/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2004-2005 TONBELLER AG
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


package org.eclipse.daanse.rolap.common.sql;

import java.util.List;

import org.eclipse.daanse.rolap.common.RolapCube;
import org.eclipse.daanse.rolap.common.RolapLevel;
import org.eclipse.daanse.rolap.common.RolapMember;
import org.eclipse.daanse.rolap.common.aggmatcher.AggStar;

/**
 * "Light version" of a {@link org.eclipse.daanse.rolap.common.sql.TupleConstraint},
 * represents one of
 * member.children, level.members, member.descendants, {enumeration}.
 */
public interface CrossJoinArg {
    CrossJoinArg[] EMPTY_ARRAY = new CrossJoinArg[0];

    RolapLevel getLevel();

    List<RolapMember> getMembers();

    void addConstraint(
        SqlQuery sqlQuery,
        RolapCube baseCube,
        AggStar aggStar);

    boolean isPreferInterpreter(boolean joinArg);
}
