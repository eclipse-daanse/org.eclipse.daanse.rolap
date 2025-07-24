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

import org.eclipse.daanse.olap.api.element.Member;

/**
 * Member of a parent-child dimension which has a closure table.
 *
 * When looking up cells, this member will automatically be converted
 * to a corresponding member of the auxiliary dimension which maps onto
 * the closure table.
 */
public class RolapParentChildMember extends RolapMemberBase {
    private final RolapMember dataMember;
    private int depth = 0;

    public RolapParentChildMember(
        RolapMember parentMember,
        RolapLevel childLevel,
        Object value,
        RolapMember dataMember)
    {
        super(parentMember, childLevel, value);
        this.dataMember = dataMember;
        this.depth = (parentMember != null)
            ? parentMember.getDepth() + 1
            : 0;
    }

    @Override
	public Member getDataMember() {
        return dataMember;
    }

    /**
     * @return the members's depth
     * @see org.eclipse.daanse.olap.api.element.Member#getDepth()
     */
    @Override
	public int getDepth() {
        return depth;
    }

    @Override
	public int getOrdinal() {
        return dataMember.getOrdinal();
    }
}

