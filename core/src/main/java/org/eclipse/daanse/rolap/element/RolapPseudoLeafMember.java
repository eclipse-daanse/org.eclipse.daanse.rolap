/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   SmartCity Jena - initial
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.rolap.element;

import org.eclipse.daanse.olap.api.element.PseudoLeafMember;

public class RolapPseudoLeafMember extends RolapCubeMember implements PseudoLeafMember{

    public RolapPseudoLeafMember(RolapCubeMember parent, RolapMember member, RolapCubeLevel cubeLevel) {
        super(parent, member, cubeLevel);
    }
}
