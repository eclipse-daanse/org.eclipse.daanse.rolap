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
import java.util.Collections;
import java.util.List;

import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.api.query.component.Formula;
import org.eclipse.daanse.olap.api.query.component.MemberExpression;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.olap.element.OlapMetaData;
import org.eclipse.daanse.olap.query.component.MdxVisitorImpl;
import org.eclipse.daanse.rolap.mapping.api.model.CalculatedMemberMapping;
import org.eclipse.daanse.rolap.mapping.api.model.NamedSetMapping;

/**
 * Visitor that walks an MDX parse tree containing formulas
 * associated with calculated members defined in a base cube but
 * referenced from a virtual cube.  When walking the tree, look
 * for other calculated members as well as stored measures.  Keep
 * track of all stored measures found, and for the calculated members,
 * once the formula of that calculated member has been visited, resolve
 * the calculated member relative to the virtual cube.
 */
public class MeasureFinder extends MdxVisitorImpl
{
    /**
     * The virtual cube where the original calculated member was
     * referenced from
     */
    private RolapCube virtualCube;

    /**
     * The base cube where the original calculated member is defined
     */
    private RolapCube baseCube;

    /**
     * The measures level corresponding to the virtual cube
     */
    private RolapLevel measuresLevel;

    /**
     * List of measures found
     */
    private List<RolapVirtualCubeMeasure> measuresFound;

    /**
     * List of calculated members found
     */
    private List<RolapCalculatedMember> calcMembersSeen;

    private RolapCatalog schema;

    public MeasureFinder(
        RolapVirtualCube virtualCube,
        RolapPhysicalCube baseCube,
        RolapLevel measuresLevel,
        RolapCatalog schema)
    {
        this.virtualCube = virtualCube;
        this.baseCube = baseCube;
        this.measuresLevel = measuresLevel;
        this.measuresFound = new ArrayList<>();
        this.calcMembersSeen = new ArrayList<>();
        this.schema = schema;
    }

    @Override
    public Object visitMemberExpression(MemberExpression memberExpr)
    {
        Member member = memberExpr.getMember();
        if (member instanceof RolapCalculatedMember calcMember) {
            // ignore the calculated member if we've already processed
            // it in another reference
            if (calcMembersSeen.contains(member)) {
                return null;
            }
            Formula formula = calcMember.getFormula();
            if (!calcMembersSeen.contains(calcMember)) {
              calcMembersSeen.add(calcMember);
            }
            formula.accept(this);

            // now that we've located all measures referenced in the
            // calculated member's formula, resolve the calculated
            // member relative to the virtual cube
            virtualCube.setMeasuresHierarchyMemberReader(
                new CacheMemberReader(
                    new MeasureMemberSource(
                        virtualCube.getMeasuresHierarchy(),
                        Util.<RolapMember>cast(measuresFound))));

            CalculatedMemberMapping mappingCalcMember =
                schema.lookupMappingCalculatedMember(
                    calcMember.getName(),
                    baseCube.getName());
            virtualCube.createCalcMembersAndNamedSets(
                Collections.singletonList(mappingCalcMember),
                Collections.<NamedSetMapping>emptyList(),
                new ArrayList<>(),
                virtualCube,
                false);

        } else if (member instanceof RolapBaseCubeMeasure baseMeasure) {
            RolapVirtualCubeMeasure virtualCubeMeasure =
                new RolapVirtualCubeMeasure(
                    null,
                    measuresLevel,
                    baseMeasure,
                    OlapMetaData.empty());
            if (!measuresFound.contains(virtualCubeMeasure)) {
                measuresFound.add(virtualCubeMeasure);
            }
        }

        return null;
    }

    public List<RolapVirtualCubeMeasure> getMeasuresFound()
    {
        return measuresFound;
    }
}

