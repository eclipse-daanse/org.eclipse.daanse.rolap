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

import org.eclipse.daanse.olap.common.Util;

/**
 * The 'All' member of a {@link org.eclipse.daanse.rolap.common.RolapCubeHierarchy}.
 *
 * A minor extension to {@link org.eclipse.daanse.rolap.common.RolapCubeMember} because the
 * naming rules are different.
 *
 * @author Will Gorman, 19 October 2007
 */
class RolapAllCubeMember
    extends RolapCubeMember
{
    protected final String name;
    private final String uniqueName;

    /**
     * Creates a RolapAllCubeMember.
     *
     * @param member Member of underlying (non-cube) hierarchy
     * @param cubeLevel Level
     */
    public RolapAllCubeMember(RolapMember member, RolapCubeLevel cubeLevel)
    {
        super(null, member, cubeLevel);
        assert member.isAll();

        // replace hierarchy name portion of all member with new name
        if (member.getHierarchy().getName().equals(getHierarchy().getName())) {
            name = member.getName();
        } else {
            // special case if we're dealing with a closure
            String replacement =
                getHierarchy().getName().replaceAll("\\$", "\\\\\\$");

            // convert string to regular expression
            String memberLevelName =
                member.getHierarchy().getName().replaceAll("\\.", "\\\\.");

            name = member.getName().replaceAll(memberLevelName, replacement);
        }

        // Assign unique name. We use a kludge to ensure that calc members are
        // called [Measures].[Foo] not [Measures].[Measures].[Foo]. We can
        // remove this code when we revisit the scheme to generate member unique
        // names.
        this.uniqueName = Util.makeFqName(getHierarchy(), name);
    }

    @Override
	public String getName() {
        return name;
    }

    @Override
    public String getUniqueName() {
        return uniqueName;
    }
}
