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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.Quoting;
import org.eclipse.daanse.olap.api.element.Cube;
import org.eclipse.daanse.olap.api.element.Hierarchy;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.api.element.VirtualCube;
import org.eclipse.daanse.olap.api.query.component.Formula;
import org.eclipse.daanse.olap.api.query.component.Query;
import org.eclipse.daanse.olap.common.StandardProperty;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.olap.query.component.IdImpl;
import org.eclipse.daanse.rolap.element.RolapMetaData;
import org.eclipse.daanse.rolap.mapping.api.model.CalculatedMemberMapping;
import org.eclipse.daanse.rolap.mapping.api.model.CatalogMapping;
import org.eclipse.daanse.rolap.mapping.api.model.MeasureMapping;
import org.eclipse.daanse.rolap.mapping.api.model.MemberMapping;
import org.eclipse.daanse.rolap.mapping.api.model.NamedSetMapping;
import org.eclipse.daanse.rolap.mapping.api.model.VirtualCubeMapping;

/**
 * RolapCube implements {@link Cube} for a ROLAP database.
 *
 * @author jhyde
 * @since 10 August, 2001
 */
public class RolapVirtualCube extends RolapCube implements VirtualCube {

    /**
     * Refers {@link RolapCubeUsages} if this is a virtual cube
     */
    private RolapCubeUsages cubeUsages;

    /**
     * Creates a RolapCube from a virtual cube.
     */
    RolapVirtualCube(RolapCatalog catalog, CatalogMapping catalogMapping, VirtualCubeMapping virtualCubeMapping,
            Context context) {
        super(catalog, catalogMapping, virtualCubeMapping, true, null, context);
        // Since Measure and VirtualCubeMeasure cannot
        // be treated as the same, measure creation cannot be done in a common
        // constructor.
        RolapLevel measuresLevel = this.getMeasuresHierarchy().newMeasuresLevel();

        // Recreate CalculatedMembers, as the original members point to
        // incorrect dimensional ordinals for the virtual cube.

        this.setCubeUsages(new RolapCubeUsages(virtualCubeMapping.getCubeUsages()));

        HashMap<String, MemberMapping> measureHash = new HashMap<>();

        List<RolapVirtualCubeMeasure> origMeasureList = getOriginMeasureList(catalog, virtualCubeMapping,
            measuresLevel, measureHash);
        Map<RolapPhysicalCube, List<CalculatedMemberMapping>> calculatedMembersMap = getOriginCalculatedMemberMap(catalog,
            virtualCubeMapping, measureHash);
        List<? extends CalculatedMemberMapping> origCalcMeasureList = calculatedMembersMap.entrySet().stream().map(Map.Entry::getValue).flatMap(Collection::stream).toList();
        // Must init the dimensions before dealing with calculated members
        init(virtualCubeMapping.getDimensionConnectors());

        // Loop through the base cubes containing calculated members
        // referenced by this virtual cube. Resolve those members relative
        // to their base cubes first, then resolve them relative to this
        // cube so the correct dimension ordinals are used
        List<RolapVirtualCubeMeasure> modifiedMeasureList = new ArrayList<>(origMeasureList);
        //add measures from calculated members
        modifiedMeasureList.addAll(getMesuresFromCalculatedmembers(calculatedMembersMap, measuresLevel));

        // Add the original calculated members from the base cubes to our
        // list of calculated members
        List<CalculatedMemberMapping> mappingCalculatedMemberList = new ArrayList<>(origCalcMeasureList);
        mappingCalculatedMemberList.addAll(virtualCubeMapping.getCalculatedMembers());

        // Resolve all calculated members relative to this virtual cube,
        // whose measureHierarchy member reader now contains all base
        // measures referenced in those calculated members
        setMeasuresHierarchyMemberReader(new CacheMemberReader(
                new MeasureMemberSource(this.getMeasuresHierarchy(), Util.<RolapMember>cast(modifiedMeasureList))));

        createCalcMembersAndNamedSets(mappingCalculatedMemberList, virtualCubeMapping.getNamedSets(), new ArrayList<>(),
                this, false);

        // iterate through a calculated member definitions in a virtual cube
        // retrieve calculated member source cube
        // set it appropriate rolap calculated measure
        Map<String, RolapHierarchy.RolapCalculatedMeasure> calcMeasuresWithBaseCube = new HashMap<>();
        for (Map.Entry<RolapPhysicalCube, List<CalculatedMemberMapping>> entry : calculatedMembersMap.entrySet()) {
            RolapCube rolapCube = entry.getKey();
            List<CalculatedMemberMapping> calculatedMembers = entry.getValue();
            for (CalculatedMemberMapping calculatedMember : calculatedMembers) {
                List<Member> measures = rolapCube.getMeasures();
                for (Member measure : measures) {
                    if (measure instanceof RolapHierarchy.RolapCalculatedMeasure calculatedMeasure
                            && calculatedMember.getName().equals(calculatedMeasure.getKey())) {
                        calculatedMeasure.setBaseCube(rolapCube);
                        calcMeasuresWithBaseCube.put(calculatedMeasure.getUniqueName(), calculatedMeasure);
                    }
                }
            }
        }

        // reset the measureHierarchy member reader back to the list of
        // measures that are only defined on this virtual cube
        setMeasuresHierarchyMemberReader(new CacheMemberReader(
                new MeasureMemberSource(this.getMeasuresHierarchy(), Util.<RolapMember>cast(origMeasureList))));


        List<? extends CalculatedMemberMapping> mappingVirtualCubeCalculatedMemberList = virtualCubeMapping
                .getCalculatedMembers();
        if (!vcHasAllCalcMembers(origCalcMeasureList, mappingVirtualCubeCalculatedMemberList)) {
            // Remove from the calculated members array
            // those members that weren't originally defined
            // on this virtual cube.
            List<Formula> calculatedMemberListCopy = new ArrayList<>(getCalculatedMemberList());
            getCalculatedMemberList().clear();
            for (Formula calculatedMember : calculatedMemberListCopy) {
                if (findOriginalMembers(calculatedMember, origCalcMeasureList, getCalculatedMemberList())) {
                    continue;
                }
                findOriginalMembers(calculatedMember, mappingVirtualCubeCalculatedMemberList,
                        getCalculatedMemberList());
            }
        }

        for (Formula calcMember : getCalculatedMemberList()) {
            if (virtualCubeMapping.getDefaultMeasure() != null
                    && calcMember.getName().equalsIgnoreCase(virtualCubeMapping.getDefaultMeasure().getName())) {
                this.getMeasuresHierarchy().setDefaultMember(calcMember.getMdxMember());
                break;
            }
        }

        // We modify the measures schema reader one last time with a version
        // which includes all calculated members as well.
        final List<RolapMember> finalMeasureMembers = new ArrayList<>(origMeasureList);
        for (Formula formula : getCalculatedMemberList()) {
            final RolapMember calcMeasure = (RolapMember) formula.getMdxMember();
            if (calcMeasure instanceof RolapHierarchy.RolapCalculatedMeasure rolapCalculatedMeasure
                    && calcMeasuresWithBaseCube.containsKey(calcMeasure.getUniqueName())) {
                rolapCalculatedMeasure
                        .setBaseCube(calcMeasuresWithBaseCube.get(calcMeasure.getUniqueName()).getBaseCube());
            }

            MemberMapping mappingMeasure = measureHash.get(calcMeasure.getUniqueName());
            if (mappingMeasure != null) {
                Boolean visible = mappingMeasure.isVisible();
                if (visible != null) {
                    calcMeasure.setProperty(StandardProperty.VISIBLE.getName(), visible);
                }
            }

            finalMeasureMembers.add(calcMeasure);
        }
        setMeasuresHierarchyMemberReader(new CacheMemberReader(
                new MeasureMemberSource(this.getMeasuresHierarchy(), Util.<RolapMember>cast(finalMeasureMembers))));
        // Note: virtual cubes do not get aggregate
    }

    protected void logMessage() {
        if (getLogger().isDebugEnabled()) {
            String msg = new StringBuilder("RolapCube<init>: virtual cube=").append(this.name).toString();
            getLogger().debug(msg);
        }
    }

    private List<RolapVirtualCubeMeasure> getMesuresFromCalculatedmembers(
            Map<RolapPhysicalCube, List<CalculatedMemberMapping>> calculatedMembersMap, RolapLevel measuresLevel) {
        List<RolapVirtualCubeMeasure> measureList = new ArrayList<RolapVirtualCubeMeasure>();
        for (Map.Entry<RolapPhysicalCube, List<CalculatedMemberMapping>> entry : calculatedMembersMap.entrySet()) {
            RolapPhysicalCube baseCube = entry.getKey();
            List<CalculatedMemberMapping> mappingCalculatedMemberList = calculatedMembersMap.get(baseCube);
            Query queryExp = resolveCalcMembers(mappingCalculatedMemberList, Collections.<NamedSetMapping>emptyList(),
                    baseCube, false);
            MeasureFinder measureFinder = new MeasureFinder(this, baseCube, measuresLevel, getCatalog());
            queryExp.accept(measureFinder);
            measureList.addAll(measureFinder.getMeasuresFound());
        }
        return measureList;
    }

    private Map<RolapPhysicalCube, List<CalculatedMemberMapping>> getOriginCalculatedMemberMap(final RolapCatalog catalog,
            final VirtualCubeMapping virtualCubeMapping, HashMap<String, MemberMapping> measureHash) {
        Map<RolapPhysicalCube, List<CalculatedMemberMapping>> calculatedMembersMap = new TreeMap<>(new RolapCubeComparator());
        List<? extends CalculatedMemberMapping> cm = virtualCubeMapping.getReferencedCalculatedMembers();
        if (cm != null) {
            for (CalculatedMemberMapping calculatedMember : cm) {
                measureHash.put(calculatedMember.getName(), calculatedMember);
                if (calculatedMember.getPhysicalCube() != null) {
                    RolapCube cube = catalog.lookupCube(calculatedMember.getPhysicalCube());
                    if (cube == null) {
                        throw Util.newError(new StringBuilder("Cube '")
                                .append(calculatedMember.getPhysicalCube().getName()).append("' not found").toString());
                    }
                    if (cube instanceof RolapPhysicalCube physicalCube) {
                        List<Member> cubeMeasures = cube.getMeasures();
                        boolean found = false;
                        for (Member cubeMeasure : cubeMeasures) {
                            if (cubeMeasure.getName().equals(calculatedMember.getName())
                                    && cubeMeasure instanceof RolapCalculatedMember) {
                                if (cubeMeasure.getName()
                                        .equalsIgnoreCase(virtualCubeMapping.getDefaultMeasure() != null
                                        ? virtualCubeMapping.getDefaultMeasure().getName()
                                                : null)) {
                                    this.getMeasuresHierarchy().setDefaultMember(cubeMeasure);
                                }
                                found = true;
                                List<CalculatedMemberMapping> memberList = calculatedMembersMap.get(cube);
                                if (memberList == null) {
                                    memberList = new ArrayList<>();
                                }
                                memberList.add(calculatedMember);
                                calculatedMembersMap.put(physicalCube, memberList);
                                break;
                            }
                        }
                        if (!found) {
                            throw Util.newInternal(new StringBuilder("could not find calculated member '")
                                .append(calculatedMember.getName()).append("' in cube '")
                                .append(calculatedMember.getPhysicalCube().getName()).append("'").toString());
                        }
                    } else {
                        throw Util.newInternal(new StringBuilder("Cube '")
                                .append(calculatedMember.getPhysicalCube().getName()).append("' is not physical cube").toString());
                    }

                } else {
                    throw Util.newInternal("calculated member not found in cube usages");
                }
            }
        }
        return calculatedMembersMap;
    }

    private List<RolapVirtualCubeMeasure> getOriginMeasureList(RolapCatalog catalog, VirtualCubeMapping virtualCubeMapping, RolapLevel measuresLevel,
            HashMap<String, MemberMapping> measureHash) {
        List<RolapVirtualCubeMeasure> origMeasureList = new ArrayList<>();
        List<? extends MeasureMapping> ms = virtualCubeMapping.getReferencedMeasures();
        for (MeasureMapping mappingMeasure : ms) {
            measureHash.put(mappingMeasure.getName(), mappingMeasure);
            if (mappingMeasure.getMeasureGroup() != null
                    && mappingMeasure.getMeasureGroup().getPhysicalCube() != null) {
                RolapCube cube = catalog.lookupCube(mappingMeasure.getMeasureGroup().getPhysicalCube());
                if (cube == null) {
                    throw Util.newError(new StringBuilder("Cube '")
                            .append(mappingMeasure.getMeasureGroup().getPhysicalCube().getName()).append("' not found")
                            .toString());
                }

                List<Member> cubeMeasures = cube.getMeasures();
                boolean found = false;
                boolean isDefaultMeasureFound = false;
                for (Member cubeMeasure : cubeMeasures) {
                    if (cubeMeasure.getName().equals(mappingMeasure.getName())) {
                        if (cubeMeasure.getName()
                                .equalsIgnoreCase(virtualCubeMapping.getDefaultMeasure() != null
                                        ? virtualCubeMapping.getDefaultMeasure().getName()
                                        : null)) {
                            isDefaultMeasureFound = true;
                        }
                        found = true;
                        // This is the a standard measure. (Don't know
                        // whether it will confuse things that this
                        // measure still points to its 'real' cube.)
                        RolapVirtualCubeMeasure virtualCubeMeasure = new RolapVirtualCubeMeasure(null, measuresLevel,
                                (RolapStoredMeasure) cubeMeasure,
                                RolapMetaData.createMetaData(mappingMeasure.getAnnotations()));

                        // Set member's visibility, default true.
                        Boolean visible = mappingMeasure.isVisible();
                        virtualCubeMeasure.setProperty(StandardProperty.VISIBLE.getName(), visible);
                        // Inherit caption from the "real" measure
                        virtualCubeMeasure.setProperty(StandardProperty.CAPTION.getName(), cubeMeasure.getCaption());
                        origMeasureList.add(virtualCubeMeasure);
                        // Set the actual virtual cube measure
                        // to the default measure
                        if (isDefaultMeasureFound) {
                            this.getMeasuresHierarchy().setDefaultMember(virtualCubeMeasure);
                        }
                        break;
                    }
                }
                if (!found) {
                    throw Util.newInternal(new StringBuilder("could not find measure '")
                            .append(mappingMeasure.getName()).append("' in cube '")
                            .append(mappingMeasure.getMeasureGroup().getPhysicalCube().getName()).append("'")
                            .toString());
                }
            } else {
                throw Util.newInternal("measure not found in cube usages");
            }
        }
        return origMeasureList;
    }

    private boolean vcHasAllCalcMembers(List<? extends CalculatedMemberMapping> origCalcMeasureList,
            List<? extends CalculatedMemberMapping> mappingVirtualCubeCalculatedMemberList) {
        return getCalculatedMemberList()
                .size() == (origCalcMeasureList.size() + mappingVirtualCubeCalculatedMemberList.size());
    }

    protected boolean findOriginalMembers(Formula formula, List<? extends CalculatedMemberMapping> mappingCalcMembers,
            List<Formula> calcMembers) {
        for (CalculatedMemberMapping mappingCalcMember : mappingCalcMembers) {
            Hierarchy hierarchy = null;
            if (mappingCalcMember.getHierarchy() != null) {
                hierarchy = lookupHierarchy(
                        new IdImpl.NameSegmentImpl(mappingCalcMember.getHierarchy().getName(), Quoting.UNQUOTED), true);
            } else {
                hierarchy = lookupHierarchy(new IdImpl.NameSegmentImpl("[Measures]", Quoting.UNQUOTED), true);
            }
            if (formula.getName().equals(mappingCalcMember.getName())
                    && formula.getMdxMember().getHierarchy().equals(hierarchy)) {
                calcMembers.add(formula);
                return true;
            }
        }
        return false;
    }
    

    /**
     * This method tells us if unrelated dimensions to measures from
     * the input base cube should be pushed to default member or not
     * during aggregation.
     * @param baseCubeName name of the base cube for which we want
     * to check this property
     * @return boolean
     */
    @Override
    public boolean shouldIgnoreUnrelatedDimensions(String baseCubeName) {
        return cubeUsages != null
            && cubeUsages.shouldIgnoreUnrelatedDimensions(baseCubeName);
    }

    public RolapCubeUsages getCubeUsages() {
        return cubeUsages;
    }

    public void setCubeUsages(RolapCubeUsages cubeUsages) {
        this.cubeUsages = cubeUsages;
    }

}
