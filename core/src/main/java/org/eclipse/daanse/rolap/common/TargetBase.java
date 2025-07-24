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

import java.sql.SQLException;
import java.util.List;
import java.util.RandomAccess;

import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.rolap.common.sql.TupleConstraint;
import org.eclipse.daanse.rolap.element.RolapLevel;
import org.eclipse.daanse.rolap.element.RolapMember;

/**
 * Base helper class for the SQL tuple readers
 * org.eclipse.daanse.rolap.HighCardSqlTupleReader and
 * org.eclipse.daanse.rolap.common.SqlTupleReader.
 *
 * Keeps track of target levels and constraints for adding to the SQL query.
 * The real work is done in the extending classes,
 * Target and
 * org.eclipse.daanse.rolap.common.SqlTupleReader.Target.
 *
 * @author Kurtis Walker
 * @since July 23, 2009
 */
public abstract class TargetBase {
    final List<RolapMember> srcMembers;
    final RolapLevel level;
    private RolapMember currMember;
    private List<RolapMember> list;
    final Object cacheLock;
    final TupleReader.MemberBuilder memberBuilder;

    public TargetBase(
        List<RolapMember> srcMembers,
        RolapLevel level,
        TupleReader.MemberBuilder memberBuilder)
    {
        this.srcMembers = srcMembers;
        this.level = level;
        cacheLock = memberBuilder.getMemberCacheLock();
        this.memberBuilder = memberBuilder;
    }

    public void setList(final List<RolapMember> list) {
        assert list instanceof RandomAccess;
        this.list = list;
    }

    public List<RolapMember> getSrcMembers() {
        return srcMembers;
    }

    public RolapLevel getLevel() {
        return level;
    }

    public RolapMember getCurrMember() {
        return this.currMember;
    }

    public void removeCurrMember() {
        this.currMember = null;
    }

    public void setCurrMember(final RolapMember m) {
        this.currMember = m;
    }

    public List<RolapMember> getList() {
        return list;
    }

    @Override
	public String toString() {
        return level.getUniqueName();
    }

    /**
     * Adds a row to the collection.
     *
     * @param stmt Statement
     * @param column Column ordinal (0-based)
     * @return Ordinal of next unconsumed column
     * @throws SQLException On error
     */
    public final int addRow(SqlStatement stmt, int column) throws SQLException {
        synchronized (cacheLock) {
            return internalAddRow(stmt, column);
        }
    }

    public abstract void open();

    public abstract List<Member> close();

    abstract int internalAddRow(SqlStatement stmt, int column)
        throws SQLException;

    public void add(final RolapMember member) {
        this.getList().add(member);
    }

    RolapNativeCrossJoin.NonEmptyCrossJoinConstraint
    castToNonEmptyCJConstraint(TupleConstraint constraint) {
        return (RolapNativeCrossJoin.NonEmptyCrossJoinConstraint) constraint;
    }
}
