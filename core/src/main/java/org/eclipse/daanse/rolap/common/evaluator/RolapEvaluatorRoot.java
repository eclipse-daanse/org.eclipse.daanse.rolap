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

import org.eclipse.daanse.olap.api.calc.Calc;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.api.element.Cube;
import org.eclipse.daanse.olap.api.element.Hierarchy;
import org.eclipse.daanse.olap.api.evaluator.Evaluator;
import org.eclipse.daanse.olap.api.execution.Execution;
import org.eclipse.daanse.olap.api.execution.Statement;
import org.eclipse.daanse.olap.evaluator.CalculableMember;
import org.eclipse.daanse.olap.evaluator.EvaluatorImpl;
import org.eclipse.daanse.olap.evaluator.EvaluatorRoot;
import org.eclipse.daanse.rolap.api.element.RolapMember;
import org.eclipse.daanse.rolap.common.star.HierarchyUsage;
import org.eclipse.daanse.rolap.common.writeback.ScenarioImpl;
import org.eclipse.daanse.rolap.element.RolapCube;
import org.eclipse.daanse.rolap.element.RolapMemberBase;

/**
 * The relational evaluator root: {@link EvaluatorRoot} with the scenario
 * member and the hierarchy-usage naming of default members. Without a result
 * behind it (a statement's own evaluator) it evaluates expressions directly
 * and is never dirty; {@code RolapResult.RolapResultEvaluatorRoot} routes
 * both through the result being built.
 */
public class RolapEvaluatorRoot extends EvaluatorRoot {

    /** @deprecated use {@link #RolapEvaluatorRoot(Execution)} */
    @Deprecated
    public RolapEvaluatorRoot(Statement statement) {
        super(statement);
    }

    public RolapEvaluatorRoot(Execution execution) {
        super(execution);
    }

    @Override
    protected CalculableMember scenarioMemberFor(Hierarchy hierarchy) {
        return scenarioMember(connection, hierarchy);
    }

    @Override
    protected void nameDefaultMember(Cube cube, Hierarchy hierarchy, CalculableMember defaultMember) {
        nameByUsage(cube, hierarchy, defaultMember);
    }

    /** The member of the active writeback scenario on {@code hierarchy}, or null. */
    public static CalculableMember scenarioMember(Connection connection, Hierarchy hierarchy) {
        if (ScenarioImpl.isScenario(hierarchy) && connection.getScenario() != null) {
            return (RolapMember) ((ScenarioImpl) connection.getScenario()).getMember();
        }
        return null;
    }

    /** Names a default member after the usage that joins its hierarchy into the cube. */
    public static void nameByUsage(Cube cube, Hierarchy hierarchy, CalculableMember defaultMember) {
        // a concurrency bottleneck, hence the cube's cache of hierarchy usages
        final HierarchyUsage hierarchyUsage = ((RolapCube) cube).getFirstUsage(hierarchy);
        if (hierarchyUsage != null && defaultMember instanceof RolapMemberBase base) {
            base.makeUniqueName(hierarchyUsage);
        }
    }

    @Override
    public EvaluatorImpl slicerEvaluator() {
        return null;
    }

    @Override
    public Object evaluateExpression(Calc<?> calc, EvaluatorImpl slicerEvaluator, Evaluator contextEvaluator) {
        return calc.evaluate(contextEvaluator != null ? contextEvaluator : slicerEvaluator);
    }

    @Override
    public boolean isDirty() {
        return false;
    }
}
