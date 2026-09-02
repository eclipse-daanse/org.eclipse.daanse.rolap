/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * History:
 *  This files came from the mondrian project. Some of the Flies
 *  (mostly the Tests) did not have License Header.
 *  But the Project is EPL Header. 2002-2022 Hitachi Vantara.
 *
 * Contributors:
 *   Hitachi Vantara.
 *   SmartCity Jena - initial  Java 8, Junit5
 */

package org.eclipse.daanse.olap;

import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.DataType;
import org.eclipse.daanse.olap.api.NameResolver;
import org.eclipse.daanse.olap.api.Parameter;
import org.eclipse.daanse.olap.api.access.AccessMember;
import org.eclipse.daanse.olap.api.access.Role;
import org.eclipse.daanse.olap.api.agg.Segment;
import org.eclipse.daanse.olap.api.calc.Calc;
import org.eclipse.daanse.olap.api.catalog.CatalogReader;
import org.eclipse.daanse.olap.api.element.Catalog;
import org.eclipse.daanse.olap.api.element.Cube;
import org.eclipse.daanse.olap.api.element.Dimension;
import org.eclipse.daanse.olap.api.element.Hierarchy;
import org.eclipse.daanse.olap.api.element.Level;
import org.eclipse.daanse.olap.api.element.MatchType;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.api.element.NamedSet;
import org.eclipse.daanse.olap.api.element.OlapElement;
import org.eclipse.daanse.olap.api.element.db.DatabaseSchema;
import org.eclipse.daanse.olap.api.evaluator.Evaluator;
import org.eclipse.daanse.olap.api.evaluator.NativeEvaluator;
import org.eclipse.daanse.olap.api.function.FunctionDefinition;
import org.eclipse.daanse.olap.api.query.NameSegment;
import org.eclipse.daanse.olap.api.query.component.Expression;

public class SpyCatalogReader implements CatalogReader {

	private CatalogReader delegate;

	public SpyCatalogReader(CatalogReader schemaReader) {
		this.delegate=schemaReader;
	}

	@Override
	public Catalog getCatalog() {
		return delegate.getCatalog();
	}

	@Override
	public Role getRole() {
		return delegate.getRole();
	}

	@Override
	public List<Dimension> getCubeDimensions(Cube cube) {
		return delegate.getCubeDimensions(cube);
	}

	@Override
	public List<Hierarchy> getDimensionHierarchies(Dimension dimension) {
		return delegate.getDimensionHierarchies(dimension);
	}

	@Override
	public List<Member> getHierarchyRootMembers(Hierarchy hierarchy) {
		return delegate.getHierarchyRootMembers(hierarchy);
	}

	@Override
	public int getChildrenCountFromCache(Member member) {
		return delegate.getChildrenCountFromCache(member);
	}

	@Override
	public int getLevelCardinality(Level level, boolean approximate, boolean materialize) {
		return delegate.getLevelCardinality(level, approximate, materialize);
	}

	@Override
	public Member substitute(Member member) {
		return delegate.substitute(member);
	}

	@Override
	public List<Member> getMemberChildren(Member member) {
		return delegate.getMemberChildren(member);
	}

	@Override
	public List<Member> getMemberChildren(Member member, Evaluator context) {
		return delegate.getMemberChildren(member, context);
	}

	@Override
	public List<Member> getMemberChildren(List<Member> members) {
		return delegate.getMemberChildren(members);
	}

	@Override
	public List<Member> getMemberChildren(List<Member> members, Evaluator context) {
		return delegate.getMemberChildren(members, context);
	}

	@Override
	public void getParentChildContributingChildren(Member dataMember, Hierarchy hierarchy, List<Member> list) {
		delegate.getParentChildContributingChildren(dataMember, hierarchy, list);
	}

	@Override
	public Member getMemberParent(Member member) {
		return delegate.getMemberParent(member);
	}

	@Override
	public void getMemberAncestors(Member member, List<Member> ancestorList) {
		delegate.getMemberAncestors(member, ancestorList);
	}

	@Override
	public int getMemberDepth(Member member) {
		return delegate.getMemberDepth(member);
	}

	@Override
	public Member getMemberByUniqueName(List<Segment> uniqueNameParts, boolean failIfNotFound, MatchType matchType) {
		return delegate.getMemberByUniqueName(uniqueNameParts, failIfNotFound, matchType);
	}

	@Override
	public Member getMemberByUniqueName(List<Segment> uniqueNameParts, boolean failIfNotFound) {
		return delegate.getMemberByUniqueName(uniqueNameParts, failIfNotFound);
	}

	@Override
	public OlapElement lookupCompound(OlapElement parent, List<Segment> names, boolean failIfNotFound, DataType category,
                                      MatchType matchType) {
		return delegate.lookupCompound(parent, names, failIfNotFound, category, matchType);
	}

	@Override
	public OlapElement lookupCompound(OlapElement parent, List<Segment> names, boolean failIfNotFound, DataType category) {
		return delegate.lookupCompound(parent, names, failIfNotFound, category);
	}

	@Override
	public Member getCalculatedMember(List<Segment> nameParts) {
		return delegate.getCalculatedMember(nameParts);
	}

	@Override
	public NamedSet getNamedSet(List<Segment> nameParts) {
		return delegate.getNamedSet(nameParts);
	}

	@Override
	public void getMemberRange(Level level, Member startMember, Member endMember, List<Member> list) {
		delegate.getMemberRange(level, startMember, endMember, list);
	}

	@Override
	public Member getLeadMember(Member member, int n) {
		return delegate.getLeadMember(member, n);
	}

	@Override
	public int compareMembersHierarchically(Member m1, Member m2) {
		return delegate.compareMembersHierarchically(m1, m2);
	}

	@Override
	public OlapElement getElementChild(OlapElement parent, Segment name, MatchType matchType) {
		return delegate.getElementChild(parent, name, matchType);
	}

	@Override
	public OlapElement getElementChild(OlapElement parent, Segment name) {
		return delegate.getElementChild(parent, name);
	}

	@Override
	public List<Member> getLevelMembers(Level level, boolean includeCalculated) {
		return delegate.getLevelMembers(level, includeCalculated);
	}

	@Override
	public List<Member> getLevelMembers(Level level, boolean includeCalculated, Evaluator context) {
		return delegate.getLevelMembers(level, includeCalculated, context);
	}

	@Override
	public List<Member> getLevelMembers(Level level, Evaluator context) {
		return delegate.getLevelMembers(level, context);
	}

	@Override
	public List<Level> getHierarchyLevels(Hierarchy hierarchy) {
		return delegate.getHierarchyLevels(hierarchy);
	}

	@Override
	public Member getHierarchyDefaultMember(Hierarchy hierarchy) {
		return delegate.getHierarchyDefaultMember(hierarchy);
	}

	@Override
	public boolean isDrillable(Member member) {
		return delegate.isDrillable(member);
	}

	@Override
	public boolean isVisible(Member member) {
		return delegate.isVisible(member);
	}

	@Override
	public List<Cube> getCubes() {
		return delegate.getCubes();
	}

	@Override
	public List<Member> getCalculatedMembers(Hierarchy hierarchy) {
		return delegate.getCalculatedMembers(hierarchy);
	}

	@Override
	public List<Member> getCalculatedMembers(Level level) {
		return delegate.getCalculatedMembers(level);
	}

	@Override
	public List<Member> getCalculatedMembers() {
		return delegate.getCalculatedMembers();
	}

	@Override
	public Member lookupMemberChildByName(Member parent, Segment childName, MatchType matchType) {
		return delegate.lookupMemberChildByName(parent, childName, matchType);
	}

	@Override
	public List<Member> lookupMemberChildrenByNames(Member parent, List<NameSegment> childNames, MatchType matchType) {
		return delegate.lookupMemberChildrenByNames(parent, childNames, matchType);
	}

	@Override
	public NativeEvaluator getNativeSetEvaluator(FunctionDefinition fun, Expression[] args, Evaluator evaluator, Calc calc) {
		return delegate.getNativeSetEvaluator(fun, args, evaluator, calc);
	}

	@Override
	public Parameter getParameter(String name) {
		return delegate.getParameter(name);
	}

	@Override
    @Deprecated
	public DataSource getDataSource() {
		return delegate.getDataSource();
	}

	@Override
	public CatalogReader withoutAccessControl() {
		return delegate.withoutAccessControl();
	}

	@Override
	public CatalogReader withLocus() {
		return delegate.withLocus();
	}

	@Override
	public List<NameResolver.Namespace> getNamespaces() {
		return delegate.getNamespaces();
	}

	@Override
	public Map<? extends Member, AccessMember> getMemberChildrenWithDetails(Member member, Evaluator evaluator) {
		return delegate.getMemberChildrenWithDetails(member, evaluator);
	}

    @Override
    public Context<?> getContext() {
        return delegate.getContext();
    }

    @Override
    public List<? extends DatabaseSchema> getDatabaseSchemas() {
        return delegate.getDatabaseSchemas();
    }


}
