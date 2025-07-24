/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   SmartCity Jena, Stefan Bischof - initial
 *
 */
package org.eclipse.daanse.rolap.common;

import java.util.Comparator;

import org.eclipse.daanse.rolap.element.RolapCube;

public class RolapCubeComparator implements Comparator<RolapCube>
{
    @Override
    public int compare(RolapCube c1, RolapCube c2)
    {
        return c1.getName().compareTo(c2.getName());
    }
}
