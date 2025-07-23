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

import org.eclipse.daanse.olap.api.CatalogReader;
import org.eclipse.daanse.olap.api.CubeMember;
import org.eclipse.daanse.olap.api.MatchType;
import org.eclipse.daanse.olap.api.Segment;
import org.eclipse.daanse.olap.api.element.Level;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.api.element.OlapElement;
import org.eclipse.daanse.olap.api.query.component.Expression;
import org.eclipse.daanse.olap.common.StandardProperty;
import org.eclipse.daanse.olap.query.component.HierarchyExpressionImpl;
import org.eclipse.daanse.olap.query.component.ResolvedFunCallImpl;
import  org.eclipse.daanse.olap.util.Bug;
import org.eclipse.daanse.rolap.function.def.visualtotals.VisualTotalMember;

/**
 * RolapCubeMember wraps RolapMembers and binds them to a specific cube.
 * RolapCubeMember wraps or overrides RolapMember methods that directly
 * reference the wrapped Member.  Methods that only contain calls to other
 * methods do not need wrapped.
 *
 * @author Will Gorman, 19 October 2007
 */
public class RolapCubeMember
    extends DelegatingRolapMember
    implements RolapMemberInCube, CubeMember
{
    protected RolapCubeLevel cubeLevel;
    protected final RolapCubeMember parentCubeMember;

    /**
     * Creates a RolapCubeMember.
     *
     * @param parent Parent member
     * @param member Member of underlying (non-cube) hierarchy
     * @param cubeLevel Level
     */
    public RolapCubeMember(
        RolapCubeMember parent, RolapMember member, RolapCubeLevel cubeLevel)
    {
        super(member);
        this.parentCubeMember = parent;
        this.cubeLevel = cubeLevel;
        if (member.isAll() && getClass() == RolapCubeMember.class) {
            throw new IllegalArgumentException("RolapCubeMember wrong member argument");
        }
    }

    @Override
    public String getUniqueName() {
        // We are making a hard design decision to compute uniqueName every
        // time it is requested rather than storing it. RolapCubeMember is thin
        // wrapper, so cheap to construct that we don't need to cache instances.
        //
        // Storing uniqueName would make creation of RolapCubeMember more
        // expensive and use significantly more memory, so we don't do that.
        // That meakes each call to getUniqueName more expensive, so we try to
        // minimize the number of calls to this method.
        return cubeLevel.getHierarchy().convertMemberName(
            member.getUniqueName());
    }

    /**
     * Returns the underlying member. This is a member of a shared dimension and
     * does not belong to a cube.
     *
     * @return Underlying member
     */
    public final RolapMember getRolapMember() {
        return member;
    }

    // final is important for performance
    @Override
	public final RolapCube getCube() {
        return cubeLevel.getCube();
    }

    @Override
	public final RolapCubeMember getDataMember() {
        RolapMember member = (RolapMember) super.getDataMember();
        if (member == null) {
            return null;
        }
        return new RolapCubeMember(parentCubeMember, member, cubeLevel);
    }

    @Override
	public int compareTo(Object o) {
        // light wrapper around rolap member compareTo
        RolapCubeMember other = null;
        if (o instanceof VisualTotalMember) {
            // REVIEW: Maybe VisualTotalMember should extend/implement
            // RolapCubeMember. Then we can remove special-cases such as this.
            other = (RolapCubeMember) ((VisualTotalMember) o).getMember();
        } else {
            other = (RolapCubeMember) o;
        }
        return member.compareTo(other.member);
    }

    @Override
	public String toString() {
        return getUniqueName();
    }

    @Override
	public int hashCode() {
        return member.hashCode();
    }

    @Override
	public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (o instanceof RolapCubeMember rolapCubeMember) {
            return equalsOlapElement(rolapCubeMember);
        }
        if (o instanceof Member member) {
            assert !Bug.BugSegregateRolapCubeMemberFixed;
            return getUniqueName().equals(member.getUniqueName());
        }
        return false;
    }

    @Override
	public boolean equalsOlapElement(OlapElement o) {
        return o.getClass() == RolapCubeMember.class
            && equalsOlapElement((RolapCubeMember) o);
    }

    private boolean equalsOlapElement(RolapCubeMember that) {
        assert that != null; // public method should have checked
        // Assume that RolapCubeLevel is canonical. (Besides, its equals method
        // is very slow.)
        return this.cubeLevel == that.cubeLevel
               && this.member.equals(that.member);
    }

    // override with stricter return type; final important for performance
    @Override
	public final RolapCubeHierarchy getHierarchy() {
        return cubeLevel.getHierarchy();
    }

    // override with stricter return type; final important for performance
    @Override
	public final RolapCubeDimension getDimension() {
        return cubeLevel.getDimension();
    }

    /**
     * {@inheritDoc}
     *
     * This method is central to how RolapCubeMember works. It allows
     * a member from the cache to be used within different usages of the same
     * shared dimension. The cache member is the same, but the RolapCubeMembers
     * wrapping the cache member report that they belong to different levels,
     * and hence different hierarchies, dimensions, and cubes.
     */
    // override with stricter return type; final important for performance
    @Override
	public final RolapCubeLevel getLevel() {
        return cubeLevel;
    }

    @Override
    public final void setLevel(Level level) {
        super.setLevel(level);
        this.cubeLevel = (RolapCubeLevel)level;
    }

    @Override
	public synchronized void setProperty(String name, Object value) {
        synchronized (this) {
            super.setProperty(name, value);
        }
    }

    @Override
	public Object getPropertyValue(String propertyName, boolean matchCase) {
        // we need to wrap these children as rolap cube members
        StandardProperty property = StandardProperty.lookup(propertyName, matchCase);
        if (property != null) {
             if( property == StandardProperty.DIMENSION_UNIQUE_NAME) {
                return getDimension().getUniqueName();

            } else if( property == StandardProperty.HIERARCHY_UNIQUE_NAME){
                return getHierarchy().getUniqueName();

            } else if( property == StandardProperty.LEVEL_UNIQUE_NAME){
                return getLevel().getUniqueName();

            } else if( property == StandardProperty.MEMBER_UNIQUE_NAME){
                return getUniqueName();

            } else if( property == StandardProperty.MEMBER_NAME){
                return getName();

            } else if( property == StandardProperty.MEMBER_CAPTION){
                return getCaption();

            } else if( property == StandardProperty.PARENT_UNIQUE_NAME){
                return parentCubeMember == null
                    ? null
                    : parentCubeMember.getUniqueName();

            } else if( property == StandardProperty.MEMBER_KEY|| property== StandardProperty.KEY){
                return this == this.getHierarchy().getAllMember() ? 0
                    : getKey();}
            else {
                return member.getPropertyValue(propertyName, matchCase);
            }
        }
        // fall through to rolap member
        return member.getPropertyValue(propertyName, matchCase);
    }

    @Override
	public Object getPropertyValue(String propertyName) {
        return this.getPropertyValue(propertyName, true);
    }

    @Override
	public final RolapCubeMember getParentMember() {
        return parentCubeMember;
    }

    // this method is overridden to make sure that any HierarchyExpr returns
    // the cube hierarchy vs. shared hierarchy.  this is the case for
    // SqlMemberSource.RolapParentChildMemberNoClosure
    @Override
	public Expression getExpression() {
        Expression exp = member.getExpression();
        if (exp instanceof ResolvedFunCallImpl fcall) {
            for (int i = 0; i < fcall.getArgCount(); i++) {
                if (fcall.getArg(i) instanceof HierarchyExpressionImpl expr && expr.getHierarchy().equals(
                    member.getHierarchy())) {
                    fcall.getArgs()[i] =
                        new HierarchyExpressionImpl(this.getHierarchy());
                }
            }
        }
        return exp;
    }

    @Override
	public OlapElement lookupChild(
        CatalogReader schemaReader,
        Segment childName,
        MatchType matchType)
    {
        return
            schemaReader.lookupMemberChildByName(this, childName, matchType);
    }

    @Override
    public String getMemberUniqueName() {
        return member.getUniqueName();
    }

}
