/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
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
package org.eclipse.daanse.rolap.common.updateable;

import org.eclipse.daanse.olap.api.access.Role;
import org.eclipse.daanse.olap.api.access.AccessCube;
import org.eclipse.daanse.olap.api.element.Dimension;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.rolap.aggregator.SumAggregator;
import org.eclipse.daanse.rolap.aggregator.extra.ListAggAggregator;
import org.eclipse.daanse.rolap.element.RolapCube;
import org.eclipse.daanse.rolap.element.RolapVirtualCube;
import org.eclipse.daanse.rolap.element.RolapVirtualCubeMeasure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.eclipse.daanse.rolap.element.RolapStoredMeasure;
import org.eclipse.daanse.rolap.common.result.RolapCell;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class UpdateabilityResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(UpdateabilityResolver.class);
    private static final Set<Class<?>> UPDATEABLE_AGGREGATORS = Set.of(
        SumAggregator.class,
        ListAggAggregator.class
    );

    public static Updateable resolve(RolapCell cell, Optional<Role> oRole, RolapCube cube) {
        List<Member> coords = cell.getMembersInCell();
        LOGGER.info("UpdateabilityResolver: cell='{}' cube={}", 
            cell.getValue() , cube.getName());

        // 1. cube doesn't have Writeback
        if (cube.getWritebackTable().isEmpty()) {
            LOGGER.info("UpdateabilityResolver: CELL_UPDATE_NOT_ENABLED_CANNOTUPDATE");
            return Updateable.CELL_UPDATE_NOT_ENABLED_CANNOTUPDATE;
        }

        // 2. Calc-member from coords?
        if (containsCalculatedMember(coords)) {
            LOGGER.info("UpdateabilityResolver: CELL_UPDATE_NOT_ENABLED_FORMULA");
            return Updateable.CELL_UPDATE_NOT_ENABLED_FORMULA;
        }

        // 3. Aggregator ≠ SUM-like?
        if (!isSumLikeAggregator(measureOf(coords))) {
            LOGGER.info("UpdateabilityResolver: CELL_UPDATE_NOT_ENABLED_NONSUM_MEASURE");
            return Updateable.CELL_UPDATE_NOT_ENABLED_NONSUM_MEASURE;
        }

        // 4. Virtual cube N/A intersection?
        if (cube instanceof RolapVirtualCube vc
                && isUnrelatedDimensionIntersection(vc, coords)) {
            LOGGER.info("UpdateabilityResolver: CELL_UPDATE_NOT_ENABLED_NACELL_VIRTUALCUBE");
            return Updateable.CELL_UPDATE_NOT_ENABLED_NACELL_VIRTUALCUBE;
        }

        // 5. Mining/Indirect dim? 
        if (hasInvalidDimensionType(coords)) {
            LOGGER.info("UpdateabilityResolver: CELL_UPDATE_NOT_ENABLED_INVALIDDIMENSIONTYPE");
            return Updateable.CELL_UPDATE_NOT_ENABLED_INVALIDDIMENSIONTYPE;
        }

        // 6. role allow update?
        if (!isCellWriteableForRole(cell, oRole, cube)) {
            LOGGER.info("UpdateabilityResolver: CELL_UPDATE_NOT_ENABLED_SECURE");
            return Updateable.CELL_UPDATE_NOT_ENABLED_SECURE;
        }

        // 7. Leaf vs. rollup
        if (allCoordinatesAreLeafs(coords)) {
        	LOGGER.info("UpdateabilityResolver: CELL_UPDATE_ENABLED");
            return Updateable.CELL_UPDATE_ENABLED;
        } else {
            LOGGER.info("UpdateabilityResolver: CELL_UPDATE_ENABLED_WITH_UPDATE");
            return Updateable.CELL_UPDATE_ENABLED_WITH_UPDATE;
        }
    }

    private static boolean containsCalculatedMember(List<Member> coords) {
        return coords.stream().anyMatch(Member::isCalculated);
    }

    private static RolapStoredMeasure measureOf(List<Member> coords) {
        return coords.stream()
            .filter(m -> m instanceof RolapStoredMeasure)
            .map(m -> (RolapStoredMeasure) m)
            .findFirst().orElse(null);
    }

    private static boolean isSumLikeAggregator(RolapStoredMeasure m) {
        if (m == null) return false;
        LOGGER.info("UpdateabilityResolver: Aggregator {}", m.getAggregator().getName());
        return UPDATEABLE_AGGREGATORS.contains(m.getAggregator().getClass());
    }

    private static boolean isUnrelatedDimensionIntersection(
            RolapVirtualCube vc, List<Member> coords) {
        RolapStoredMeasure measure = measureOf(coords);
        if (!(measure instanceof RolapVirtualCubeMeasure vcm)) return false;
        RolapCube baseCube = vcm.getCube();
        for (Member m : coords) {
            if (m.getDimension().isMeasures()) continue;
            if (!baseCubeContainsDimension(baseCube, m.getDimension())) {
                return true;
            }
        }
        return false;
    }

    private static boolean baseCubeContainsDimension(
            RolapCube baseCube, Dimension dim) {
        // walk baseCube.getDimensionList() and compare by identity / name
        return baseCube.getDimensions().stream()
            .anyMatch(d -> d.getUniqueName().equals(dim.getUniqueName()));
    }

    private static boolean hasInvalidDimensionType(List<Member> coords) {
        // Mining/Indirect — not support in Daanse 
        return false;
    }

    private static boolean isCellWriteableForRole(RolapCell cell, Optional<Role> oRole, RolapCube cube) {
        if (oRole.isEmpty() ||  oRole.get().getAccess(cube) == AccessCube.NONE) return false;
        //TODO Daanse doesn't have Access for cell update now. we receive data according role. in this case if we have data we have Access for update
        
        return true;
    }

    private static boolean allCoordinatesAreLeafs(List<Member> coords) {
        for (Member m : coords) {
            if (m.isAll()) return false;
            // Member doesn't have getChildMemberCount()
            if (m.getLevel().getChildLevel() != null) return false;
        }
        return true;
    }
}
