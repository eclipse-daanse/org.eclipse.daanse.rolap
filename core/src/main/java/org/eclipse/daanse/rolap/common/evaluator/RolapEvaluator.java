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
package org.eclipse.daanse.rolap.common.evaluator;

import java.util.List;
import java.util.Optional;

import org.eclipse.daanse.olap.api.calc.tuple.TupleList;
import org.eclipse.daanse.olap.api.element.Cube;
import org.eclipse.daanse.olap.api.element.Hierarchy;
import org.eclipse.daanse.olap.api.element.Level;
import org.eclipse.daanse.olap.api.element.Measure;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.evaluator.EvaluatorImpl;
import org.eclipse.daanse.rolap.api.element.RolapMember;
import org.eclipse.daanse.rolap.common.agg.CompoundPredicateInfo;
import org.eclipse.daanse.rolap.common.constraint.SlicerAnalyzer;
import org.eclipse.daanse.rolap.element.RolapCube;
import org.eclipse.daanse.rolap.element.RolapLevel;
import org.eclipse.daanse.rolap.element.RolapMeasure;

/**
 * The relational evaluator: {@link EvaluatorImpl} with the answers only the
 * relational provider can give (slicer analysis, the compound predicate, the
 * fact-count measure, the base-cube level), and the ROLAP types in its
 * signatures for the callers that need them.
 */
public class RolapEvaluator extends EvaluatorImpl {

    public RolapEvaluator(RolapEvaluatorRoot root) {
        super(root);
    }

    protected RolapEvaluator(RolapEvaluatorRoot root, RolapEvaluator parent, List<List<Member>> aggregationList) {
        super(root, parent, aggregationList);
    }

    @Override
    protected RolapEvaluator pushClone(List<List<Member>> aggregationList) {
        return new RolapEvaluator(getRoot(), this, aggregationList);
    }

    @Override
    public RolapEvaluator push() {
        return (RolapEvaluator) super.push();
    }

    @Override
    public RolapEvaluatorRoot getRoot() {
        return (RolapEvaluatorRoot) super.getRoot();
    }

    @Override
    public RolapCube getCube() {
        return (RolapCube) super.getCube();
    }

    @Override
    public RolapCube getMeasureCube() {
        return (RolapCube) super.getMeasureCube();
    }

    @Override
    public RolapMember getContext(Hierarchy hierarchy) {
        return (RolapMember) super.getContext(hierarchy);
    }

    @Override
    public CompoundPredicateInfo getSlicerPredicateInfo() {
        return (CompoundPredicateInfo) super.getSlicerPredicateInfo();
    }

    @Override
    protected boolean isDisjointSlicerTuple(TupleList tuples) {
        return SlicerAnalyzer.isDisjointTuple(tuples);
    }

    @Override
    protected boolean hasMultipleLevelSlicer() {
        return SlicerAnalyzer.hasMultipleLevelSlicer(this);
    }

    @Override
    protected Object describeSlicer(TupleList tuples, Measure measure) {
        return new CompoundPredicateInfo(tuples, (RolapMeasure) measure, this);
    }

    @Override
    protected Optional<Member> emptinessProbe(Cube cube) {
        return Optional.ofNullable(((RolapCube) cube).getFactCountMeasure());
    }

    @Override
    protected Level baseCubeLevel(Cube baseCube, Level level) {
        return ((RolapCube) baseCube).findBaseCubeLevel((RolapLevel) level);
    }
}
