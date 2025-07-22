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


package org.eclipse.daanse.rolap.common.agg;

import java.util.List;

import org.eclipse.daanse.rolap.common.RolapMember;
import org.eclipse.daanse.rolap.common.RolapStar;
import org.eclipse.daanse.rolap.common.StarColumnPredicate;

/**
 * Column constraint defined by a member.
 *
 * @author jhyde
 * @since Mar 16, 2006
 */
public class MemberColumnPredicate extends ValueColumnPredicate {
    private final RolapMember member;

    /**
     * Creates a MemberColumnPredicate
     *
     * @param column Constrained column
     * @param member Member to constrain column to; must not be null
     */
    public MemberColumnPredicate(RolapStar.Column column, RolapMember member) {
        super(column, member.getKey());
        this.member = member;
    }

    // for debug
    @Override
	public String toString() {
        return member.getUniqueName();
    }

    @Override
	public List<RolapStar.Column> getConstrainedColumnList() {
        return super.getConstrainedColumnList();
    }

    /**
     * Returns the Member.
     *
     * @return Returns the Member, not null.
     */
    public RolapMember getMember() {
        return member;
    }

    @Override
	public boolean equals(Object other) {
        if (!(other instanceof MemberColumnPredicate that)) {
            return false;
        }
        return member.equals(that.getMember());
    }

    @Override
	public int hashCode() {
        return member.hashCode();
    }

    @Override
	public void describe(StringBuilder buf) {
        buf.append(member.getUniqueName());
    }

    @Override
	public StarColumnPredicate cloneWithColumn(RolapStar.Column column) {
        return new MemberColumnPredicate(column, member);
    }
}
