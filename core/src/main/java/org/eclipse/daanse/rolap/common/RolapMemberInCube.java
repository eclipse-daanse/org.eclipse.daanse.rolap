/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (c) 2002-2017 Hitachi Vantara..  All rights reserved.
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

/**
 * Extension to {@link RolapMember} that knows the current cube.
 *
 * This is typical of members that occur in queries (where there is always a
 * current cube) as opposed to members that belong to caches. Members of shared
 * dimensions might occur in several different cubes, or even several times in
 * the same virtual cube.
 *
 * @author jhyde
 * @since 20 March, 2010
 */
public interface RolapMemberInCube extends RolapMember {
    /**
     * Returns the cube this cube member belongs to.
     *
     * This method is not in the {@link RolapMember} interface, because
     * regular members may be shared, and therefore do not belong to a specific
     * cube.
     *
     * @return Cube this cube member belongs to, never null
     */
    RolapCube getCube();
}
