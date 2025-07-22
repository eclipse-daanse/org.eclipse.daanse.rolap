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

import java.util.List;

import org.eclipse.daanse.olap.api.CatalogReader;
import org.eclipse.daanse.olap.api.MatchType;
import org.eclipse.daanse.olap.api.Segment;
import org.eclipse.daanse.olap.api.element.Dimension;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.api.element.MetaData;
import org.eclipse.daanse.olap.api.element.OlapElement;
import org.eclipse.daanse.olap.api.element.Property;
import org.eclipse.daanse.olap.api.query.component.Expression;

/**
 * Implementation of {@link org.eclipse.daanse.rolap.common.RolapMember} that delegates all calls
 * to an underlying member.
 *
 * @author jhyde
 * @since Mar 16, 2010
 */
public class DelegatingRolapMember extends RolapMemberBase {
    public final RolapMember member;

    protected DelegatingRolapMember(RolapMember member) {
        super();
        this.member = member;
    }

    @Override
	public RolapLevel getLevel() {
        return member.getLevel();
    }

    @Override
	public Object getKey() {
        return member.getKey();
    }

    @Override
	public RolapMember getParentMember() {
        return member.getParentMember();
    }

    @Override
	public RolapHierarchy getHierarchy() {
        return member.getHierarchy();
    }

    @Override
	public String getParentUniqueName() {
        return member.getParentUniqueName();
    }

    @Override
	public MemberType getMemberType() {
        return member.getMemberType();
    }

    @Override
	public boolean isParentChildLeaf() {
        return member.isParentChildLeaf();
    }

    @Override
    public boolean isParentChildPhysicalMember() {
        return member.isParentChildPhysicalMember();
    }

    @Override
	public void setName(String name) {
        member.setName(name);
    }

    @Override
	public boolean isAll() {
        return member.isAll();
    }

    @Override
	public boolean isMeasure() {
        return member.isMeasure();
    }

    @Override
	public boolean isNull() {
        return member.isNull();
    }

    @Override
	public boolean isChildOrEqualTo(Member member2) {
        if (member2 instanceof DelegatingRolapMember delegatingRolapMember) {
            return member
                    .isChildOrEqualTo(delegatingRolapMember.member);
        } else {
            return member.isChildOrEqualTo(member2);
        }
    }

    @Override
	public boolean isCalculated() {
        return member.isCalculated();
    }

    @Override
	public boolean isEvaluated() {
        return member.isEvaluated();
    }

    @Override
	public int getSolveOrder() {
        return member.getSolveOrder();
    }

    @Override
	public Expression getExpression() {
        return member.getExpression();
    }

    @Override
	public List<Member> getAncestorMembers() {
        return member.getAncestorMembers();
    }

    @Override
	public boolean isCalculatedInQuery() {
        return member.isCalculatedInQuery();
    }

    @Override
	public Object getPropertyValue(String propertyName) {
        return member.getPropertyValue(propertyName);
    }

    @Override
	public Object getPropertyValue(String propertyName, boolean matchCase) {
        return member.getPropertyValue(propertyName, matchCase);
    }

    @Override
	public String getPropertyFormattedValue(String propertyName) {
        return member.getPropertyFormattedValue(propertyName);
    }

    @Override
	public synchronized void setProperty(String name, Object value) {
        member.setProperty(name, value);
    }

    @Override
	public Property[] getProperties() {
        return member.getProperties();
    }

    @Override
	public int getOrdinal() {
        return member.getOrdinal();
    }

    @Override
	public Comparable getOrderKey() {
        return member.getOrderKey();
    }

    @Override
	public boolean isHidden() {
        return member.isHidden();
    }

    @Override
	public int getDepth() {
        return member.getDepth();
    }

    @Override
	public Member getDataMember() {
        return member.getDataMember();
    }

    @Override
	@SuppressWarnings({"unchecked"})
    public int compareTo(Object o) {
        return member.compareTo(o);
    }

    @Override
	public String getUniqueName() {
        return member.getUniqueName();
    }

    @Override
	public String getName() {
        return member.getName();
    }

    @Override
	public String getDescription() {
        return member.getDescription();
    }

    @Override
	public OlapElement lookupChild(
        CatalogReader schemaReader, Segment s, MatchType matchType)
    {
        return member.lookupChild(schemaReader, s, matchType);
    }

    @Override
	public MetaData getMetaData()  {
        return member.getMetaData();
    }

    @Override
	public String getQualifiedName() {
        return member.getQualifiedName();
    }

    @Override
	public String getCaption() {
        return member.getCaption();
    }

    @Override
	public Dimension getDimension() {
        return member.getDimension();
    }

    @Override
	public boolean isAllMember() {
        return member.isAllMember();
    }
}
