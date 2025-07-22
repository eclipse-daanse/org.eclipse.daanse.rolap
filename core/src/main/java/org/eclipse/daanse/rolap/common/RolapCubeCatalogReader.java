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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.daanse.olap.api.CatalogReader;
import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.DataType;
import org.eclipse.daanse.olap.api.Evaluator;
import org.eclipse.daanse.olap.api.IdentifierSegment;
import org.eclipse.daanse.olap.api.MatchType;
import org.eclipse.daanse.olap.api.NameResolver;
import org.eclipse.daanse.olap.api.Segment;
import org.eclipse.daanse.olap.api.access.AccessHierarchy;
import org.eclipse.daanse.olap.api.access.AccessMember;
import org.eclipse.daanse.olap.api.access.Role;
import org.eclipse.daanse.olap.api.element.Hierarchy;
import org.eclipse.daanse.olap.api.element.Level;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.api.element.NamedSet;
import org.eclipse.daanse.olap.api.element.OlapElement;
import org.eclipse.daanse.olap.api.query.component.Formula;
import org.eclipse.daanse.olap.common.NameResolverImpl;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.olap.impl.IdentifierNode;

/**
 * Schema reader which works from the perspective of a particular cube
 * (and hence includes calculated members defined in that cube) and also
 * applies the access-rights of a given role.
 */

public class RolapCubeCatalogReader extends RolapCatalogReader
        implements NameResolver.Namespace
    {
        private final RolapCube rolapCube;

        public RolapCubeCatalogReader(Context context,Role role, RolapCube rolapCube) {
            super(context,role, rolapCube.getCatalog());
            this.rolapCube = rolapCube;
            assert role != null : "precondition: role != null";
        }

        @Override
        public List<Member> getLevelMembers(
                Level level,
                boolean includeCalculated)
        {
            return getLevelMembers(level, includeCalculated, null);
        }

        @Override
        public List<Member> getLevelMembers(
            Level level,
            boolean includeCalculated,
            Evaluator context)
        {
            List<Member> members = super.getLevelMembers(level, false, context);
            if (includeCalculated) {
                members = Util.addLevelCalculatedMembers(this, level, members);
            }
            return members;
        }

        @Override
        public Member getCalculatedMember(List<Segment> nameParts) {
            final String uniqueName = Util.implode(nameParts);
            for (Formula formula : rolapCube.getCalculatedMemberList()) {
                final String formulaUniqueName =
                    formula.getMdxMember().getUniqueName();
                if (formulaUniqueName.equals(uniqueName)
                    && getRole().canAccess(formula.getMdxMember()))
                {
                    return formula.getMdxMember();
                }
            }
            return null;
        }

        @Override
        public NamedSet getNamedSet(List<Segment> segments) {
            if (segments.size() == 1) {
                Segment segment = segments.get(0);
                for (Formula namedSet : rolapCube.getNamedSetList()) {
                    if (segment.matches(namedSet.getName())) {
                        return namedSet.getNamedSet();
                    }
                }
            }
            return super.getNamedSet(segments);
        }

        @Override
        public List<Member> getCalculatedMembers(Hierarchy hierarchy) {
            ArrayList<Member> list = new ArrayList<>();

            if (getRole().getAccess(hierarchy) == AccessHierarchy.NONE) {
                return list;
            }

            for (Member member : getCalculatedMembers()) {
                if (member.getHierarchy().equals(hierarchy)) {
                    list.add(member);
                }
            }
            return list;
        }

        @Override
        public List<Member> getCalculatedMembers(Level level) {
            List<Member> list = new ArrayList<>();

            if (getRole().getAccess(level) == AccessMember.NONE) {
                return list;
            }

            for (Member member : getCalculatedMembers()) {
                if (member.getLevel().equals(level)) {
                    list.add(member);
                }
            }
            return list;
        }

        @Override
        public List<Member> getCalculatedMembers() {
//            List<Member> list =
//                roleToAccessibleCalculatedMembers.get(getRole());
//            if (list == null) {
//                list = new ArrayList<Member>();
//
//                for (Formula formula : calculatedMemberList) {
//                    Member member = formula.getMdxMember();
//                    if (getRole().canAccess(member)) {
//                        list.add(member);
//                    }
//                }
//                //  calculatedMembers array may not have been initialized
//                if (list.size() > 0) {
//                    roleToAccessibleCalculatedMembers.put(getRole(), list);
//                }
//            }

            //Without roleToAccessibleCalculatedMembers
            //Issues with session objects
            List<Member> list = new ArrayList<>();

            for (Formula formula : rolapCube.getCalculatedMemberList()) {
                Member member = formula.getMdxMember();
                if (getRole().canAccess(member)) {
                    list.add(member);
                }
            }
            return list;
        }

        @Override
        public CatalogReader withoutAccessControl() {
            assert getClass() == RolapCubeCatalogReader.class
                : new StringBuilder("Derived class ").append(getClass()).append(" must override method").toString();
            return rolapCube.getCatalogReader();
        }

        @Override
        public Member getMemberByUniqueName(
            List<Segment> uniqueNameParts,
            boolean failIfNotFound,
            MatchType matchType)
        {
            Member member =
                (Member) lookupCompound(
                    rolapCube,
                    uniqueNameParts,
                    failIfNotFound,
                    DataType.MEMBER,
                    matchType);
            if (member == null) {
                assert !failIfNotFound;
                return null;
            }
            if (getRole().canAccess(member)) {
                return member;
            } else {
                if (failIfNotFound) {
                    throw Util.newElementNotFoundException(
                        DataType.MEMBER,
                        new IdentifierNode(
                            Util.toOlap4j(uniqueNameParts)));
                }
                return null;
            }
        }

        @Override
        public List<NameResolver.Namespace> getNamespaces() {
            final List<NameResolver.Namespace> list =
                new ArrayList<>();
            list.add(this);
            list.addAll(catalog.getCatalogReaderWithDefaultRole().getNamespaces());
            return list;
        }

        @Override
        public OlapElement lookupChild(
            OlapElement parent,
            IdentifierSegment segment,
            MatchType matchType)
        {
            // ignore matchType
            return lookupChild(parent, segment);
        }

        @Override
        public OlapElement lookupChild(
            OlapElement parent,
            IdentifierSegment segment)
        {
            // Don't look for stored members, or look for dimensions,
            // hierarchies, levels at all. Only look for calculated members
            // and named sets defined against this cube.

            // Look up calc member.
            for (Formula formula : rolapCube.getCalculatedMemberList()) {
                if (NameResolverImpl.matches(formula, parent, segment)) {
                    return formula.getMdxMember();
                }
            }

            // Look up named set.
            if (parent == rolapCube) {
                for (Formula formula : rolapCube.getNamedSetList()) {
                    if (Util.matches(segment, formula.getName())) {
                        return formula.getNamedSet();
                    }
                }
            }

            return null;
        }

}
