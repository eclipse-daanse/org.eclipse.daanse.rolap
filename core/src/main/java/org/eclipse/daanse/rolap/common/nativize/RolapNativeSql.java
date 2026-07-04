/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2004-2005 TONBELLER AG
 * Copyright (C) 2006-2017 Hitachi Vantara
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
package org.eclipse.daanse.rolap.common.nativize;

import static org.eclipse.daanse.rolap.common.util.SqlExpressionResolver.render;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.eclipse.daanse.olap.api.DataType;
import org.eclipse.daanse.olap.api.aggregator.Aggregator;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.api.evaluator.Evaluator;
import org.eclipse.daanse.olap.api.query.component.DimensionExpression;
import org.eclipse.daanse.olap.api.query.component.Expression;
import org.eclipse.daanse.olap.api.query.component.FunctionCall;
import org.eclipse.daanse.olap.api.query.component.LevelExpression;
import org.eclipse.daanse.olap.api.query.component.Literal;
import org.eclipse.daanse.olap.api.query.component.MemberExpression;
import org.eclipse.daanse.olap.api.sql.SqlExpression;
import org.eclipse.daanse.olap.api.type.MemberType;
import org.eclipse.daanse.olap.api.type.StringType;
import org.eclipse.daanse.olap.common.ExpCacheDescriptorImpl;
import org.eclipse.daanse.olap.fun.DaanseEvaluationException;
import org.eclipse.daanse.olap.query.component.HierarchyExpressionImpl;
import org.eclipse.daanse.olap.query.component.ResolvedFunCallImpl;
import org.eclipse.daanse.rolap.common.RolapAggregationManager;
import org.eclipse.daanse.rolap.common.aggmatcher.AggStar;
import org.eclipse.daanse.rolap.common.evaluator.RolapEvaluator;
import org.eclipse.daanse.rolap.common.star.RolapSqlExpression;
import org.eclipse.daanse.rolap.common.star.RolapStar;
import org.eclipse.daanse.rolap.element.RolapCalculatedMember;
import org.eclipse.daanse.rolap.element.RolapCubeDimension;
import org.eclipse.daanse.rolap.element.RolapCubeHierarchy;
import org.eclipse.daanse.rolap.element.RolapCubeLevel;
import org.eclipse.daanse.rolap.element.RolapLevel;
import org.eclipse.daanse.rolap.element.RolapStoredMeasure;
import org.eclipse.daanse.sql.statement.api.expression.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates SQL from parse tree nodes. Currently it creates the SQL that
 * accesses a measure for the ORDER BY that is generated for a TopCount.
 *
 * <p>P5-N4c flip: the node channel is the SOURCE. Every compiler answers over
 * {@link SqlCompiler#compileNodeExpr} (value expressions) /
 * {@link SqlCompiler#compileNodePredicate} (boolean expressions), emitting a builder node
 * ({@code org.eclipse.daanse.sql.statement.api.expression.SqlExpression} / {@link Predicate})
 * that the statement renderer resolves against the live dialect at render time. The node
 * twins were byte-verified against the retired executed-string channel by the N1-N4b
 * DUALNATIVE dual-emit gate (271 byte-identical + 36 keyword-case-only, ledger L002).
 *
 * <p>The string channel ({@link SqlCompiler#compile}) survives ONLY as the guarded fallback
 * for the documented node null-emit shapes — a LOGICAL {@code IIF} (no boolean CASE
 * predicate node), a column-less agg-table column ({@code toSqlExpression() == null} →
 * legacy {@code generateExprString}), and a non-{@code AbstractAggregator} aggregator —
 * where {@link #generateFilterPredicate} / {@link #generateTopCountOrderByNode} wrap the
 * legacy string compile in a {@code raw} node so no shape the string compiler handled is
 * dropped. Those fallback shapes (plus the MATCHES capability gates) PIN the
 * {@link #dialect} field of this class: it cannot be deleted until the fallback burns down
 * (doc-07 exception table).
 *
 * @author av
 * @since Nov 17, 2005
  */
public class RolapNativeSql {

	private static final Logger LOGGER =
	        LoggerFactory.getLogger(RolapNativeSql.class);
    private static final Pattern DECIMAL =
        Pattern.compile("[+-]?((\\d+(\\.\\d*)?)|(\\.\\d+))");

    private final NativeSqlContext ctx;

    /** PINNED by the string-fallback shapes (LOGICAL IIF / column-less agg column /
     *  non-node aggregator — see the class javadoc) and the MATCHES capability gates
     *  ({@code allowsRegularExpressionInWhereClause}, the regex-generator pattern probe).
     *  Deleted once the fallback burns down (doc-07 exception table). */
    private final Dialect dialect;

    CompositeSqlCompiler numericCompiler;
    CompositeSqlCompiler booleanCompiler;

    RolapStoredMeasure storedMeasure;
    final AggStar aggStar;
    final Evaluator evaluator;
    final RolapLevel rolapLevel;

    /**
     * We remember one of the measures so we can generate
     * the constraints from RolapAggregationManager. Also
     * make sure all measures live in the same star.
     *
     * @return false if one or more saved measures are not
     * from the same star (or aggStar if defined), true otherwise.
     *
     * @see RolapAggregationManager#makeRequest(RolapEvaluator)
     */
    private boolean saveStoredMeasure(RolapStoredMeasure m) {
        if (aggStar != null && !storedMeasureIsPresentOnAggStar(m)) {
            return false;
        }
        if (storedMeasure != null) {
            RolapStar star1 = getStar(storedMeasure);
            RolapStar star2 = getStar(m);
            if (star1 != star2) {
                return false;
            }
        }
        this.storedMeasure = m;
        return true;
    }

    private boolean storedMeasureIsPresentOnAggStar(RolapStoredMeasure m) {
        RolapStar.Column column =
            (RolapStar.Column) m.getStarMeasure();
        int bitPos = column.getBitPosition();
        return  aggStar.lookupColumn(bitPos) != null;
    }

    private RolapStar getStar(RolapStoredMeasure m) {
        return ((RolapStar.Measure) m.getStarMeasure()).getStar();
    }

    /**
     * Translates an expression into SQL
     */
    interface SqlCompiler {
        /**
         * Legacy string channel, kept ONLY as the guarded fallback for the node
         * null-emit shapes (see the class javadoc): returns the dialect-rendered SQL
         * string, or null if exp cannot be converted.
         *
         * @param exp Expression
         * @return SQL, or null if cannot be converted into SQL
         */
        StringBuilder compile(Expression exp);

        /**
         * The authoritative node channel (P5-N4c flip): returns the builder node for a
         * value expression, or null if this compiler cannot emit a node for it.
         * Applies exactly the same match conditions as {@link #compile}.
         */
        default org.eclipse.daanse.sql.statement.api.expression.SqlExpression compileNodeExpr(Expression exp) {
            return null;
        }

        /**
         * The authoritative node channel (P5-N4c flip): returns the builder predicate for
         * a boolean expression, or null if this compiler cannot emit a node for it.
         * Applies exactly the same match conditions as {@link #compile}.
         */
        default Predicate compileNodePredicate(Expression exp) {
            return null;
        }
    }

    /**
     * Implementation of {@link SqlCompiler} that uses chain of responsibility
     * to find a matching sql compiler.
     */
    static class CompositeSqlCompiler implements SqlCompiler {
        List<SqlCompiler> compilers = new ArrayList<>();

        public void add(SqlCompiler compiler) {
            compilers.add(compiler);
        }

        @Override
		public StringBuilder compile(Expression exp) {
            for (SqlCompiler compiler : compilers) {
                StringBuilder s = compiler.compile(exp);
                if (s != null) {
                    return s;
                }
            }
            return null;
        }

        /**
         * Node-channel chain of responsibility: the first child emitting a node wins. The
         * children's match guards are mutually exclusive (each matches a distinct expression
         * shape), so the child that answers here is exactly the child whose {@link #compile}
         * would have produced the string — a null answer means the ONE matching child
         * null-emits (a documented fallback shape), never that a later child was skipped.
         */
        @Override
        public org.eclipse.daanse.sql.statement.api.expression.SqlExpression compileNodeExpr(Expression exp) {
            for (SqlCompiler compiler : compilers) {
                org.eclipse.daanse.sql.statement.api.expression.SqlExpression node = compiler.compileNodeExpr(exp);
                if (node != null) {
                    return node;
                }
            }
            return null;
        }

        /** Predicate twin of {@link #compileNodeExpr(Expression)}. */
        @Override
        public Predicate compileNodePredicate(Expression exp) {
            for (SqlCompiler compiler : compilers) {
                Predicate node = compiler.compileNodePredicate(exp);
                if (node != null) {
                    return node;
                }
            }
            return null;
        }

        @Override
		public String toString() {
            return compilers.toString();
        }
    }

    /**
     * Compiles a numeric literal to SQL.
     */
    public class NumberSqlCompiler implements SqlCompiler {
        @Override
		public StringBuilder compile(Expression exp) {
            if (!(exp instanceof Literal)) {
                return null;
            }
            LOGGER.debug("Expression category: {}", exp.getCategory());
            if (exp.getCategory() == DataType.INTEGER) {//TODO: REVIEW BITWISE
                return null;
            }
            Literal literal = (Literal) exp;
            String expr = String.valueOf(literal.getValue());
            if (!DECIMAL.matcher(expr).matches()) {
                throw new DaanseEvaluationException(
                    "Expected to get decimal, but got " + expr);
            }

            return dialect.quoteDecimalLiteral(expr);
        }

        /**
         * Node twin of {@link #compile}: same guards (non-Literal / INTEGER category /
         * non-decimal early-outs), emitting the decimal as a builder literal node the
         * renderer quotes via {@code dialect.quote(value, NUMERIC)} — the node-side
         * counterpart of {@code dialect.quoteDecimalLiteral(String.valueOf(value))}
         * (byte-verified by the retired DUALNATIVE gate).
         */
        @Override
        public org.eclipse.daanse.sql.statement.api.expression.SqlExpression compileNodeExpr(Expression exp) {
            if (!(exp instanceof Literal)) {
                return null;
            }
            if (exp.getCategory() == DataType.INTEGER) {//TODO: REVIEW BITWISE
                return null;
            }
            Literal literal = (Literal) exp;
            String expr = String.valueOf(literal.getValue());
            if (!DECIMAL.matcher(expr).matches()) {
                // Emit no node; the string-fallback compile then runs and throws the legacy
                // DaanseEvaluationException for a non-decimal literal.
                return null;
            }
            return org.eclipse.daanse.sql.statement.api.Expressions.literal(
                literal.getValue(), org.eclipse.daanse.jdbc.db.dialect.api.type.Datatype.NUMERIC);
        }

        @Override
		public String toString() {
            return "NumberSqlCompiler";
        }
    }

    /**
     * Base class to remove MemberScalarExp.
     */
    abstract class MemberSqlCompiler implements SqlCompiler {
        protected Expression unwind(Expression exp) {
            return exp;
        }
    }

    /**
     * Compiles a measure into SQL, the measure will be aggregated
     * like sum(measure).
     */
    class StoredMeasureSqlCompiler extends MemberSqlCompiler {

        @Override
		public StringBuilder compile(Expression exp) {
            exp = unwind(exp);
            if (!(exp instanceof MemberExpression)) {
                return null;
            }
            final Member member = ((MemberExpression) exp).getMember();
            if (!(member instanceof RolapStoredMeasure measure)) {
                return null;
            }
            if (measure.isCalculated()) {
                return null; // ??
            }
            if (!saveStoredMeasure(measure)) {
                return null;
            }

            Aggregator aggregator = measure.getAggregator();
            String exprInner;
            // Use aggregate table to create condition if available
            if (aggStar != null
                && measure.getStarMeasure() instanceof RolapStar.Column)
            {
                RolapStar.Column column =
                    (RolapStar.Column) measure.getStarMeasure();
                int bitPos = column.getBitPosition();
                AggStar.Table.Column aggColumn = aggStar.lookupColumn(bitPos);
                // Render the agg-table measure column through the renderer (Column / computed RawVariant),
                // not the legacy generateExprString that throws for a computed column. Byte-identical for a
                // plain column. Bail to the legacy string only for a column-less agg column.
                org.eclipse.daanse.sql.statement.api.expression.SqlExpression aggNode = aggColumn.toSqlExpression();
                exprInner = aggNode != null
                    ? org.eclipse.daanse.rolap.common.SqlRender.renderExpression(aggNode, dialect)
                    : aggColumn.generateExprString(dialect);
                if (aggColumn instanceof AggStar.FactTable.Measure) {
                    Aggregator aggTableAggregator =
                        ((AggStar.FactTable.Measure) aggColumn)
                            .getAggregator();
                    // aggregating data that has already been aggregated
                    // should be done with another aggregators
                    // e.g., counting facts should be proceeded via computing
                    // sum, as a row can aggregate several facts
                    aggregator = (Aggregator) aggTableAggregator
                        .getRollup();
                }
            } else {
            	RolapSqlExpression defExp =
                    measure.getDaanseDefExpression();
                // Render the measure definition column as a node (Column / computed RawVariant) via the
                // renderer instead of getExpression(dialect) (which throws for a computed expression).
                exprInner = (defExp == null)
                    ? "*"
                    : org.eclipse.daanse.rolap.common.SqlRender.renderExpression(
                        org.eclipse.daanse.rolap.common.sqlbuild.JoinPlanner.expressionFor(defExp), dialect);
            }

            StringBuilder expr = aggregator.getExpression(exprInner);
            return dialect.quoteDecimalLiteral(expr);
        }

        /**
         * Node twin of {@link #compile}: same guards (including {@link #saveStoredMeasure} —
         * the node channel now runs FIRST, so this save is the authoritative one; the string
         * fallback's re-save of the same measure is idempotent), same aggStar/definition
         * branch, same rollup-aggregator swap. The inner node is exactly the node the string
         * body renders ({@code aggColumn.toSqlExpression()} resp.
         * {@code expressionFor(defExp)} / {@code raw("*")}); the aggregator wrap is
         * {@code AbstractAggregator.getExpression(node)}. Emits no node when the string body
         * falls back to {@code generateExprString} (column-less agg column) or the aggregator
         * has no node form (composite/dialect-generator) — those shapes reach the string
         * fallback of {@link #generateTopCountOrderByNode} / {@link #generateFilterPredicate}.
         * The string body's final {@code dialect.quoteDecimalLiteral} wrap has no node
         * counterpart (identity on every measured dialect, byte-verified by the retired
         * DUALNATIVE gate).
         */
        @Override
        public org.eclipse.daanse.sql.statement.api.expression.SqlExpression compileNodeExpr(Expression exp) {
            exp = unwind(exp);
            if (!(exp instanceof MemberExpression)) {
                return null;
            }
            final Member member = ((MemberExpression) exp).getMember();
            if (!(member instanceof RolapStoredMeasure measure)) {
                return null;
            }
            if (measure.isCalculated()) {
                return null; // ??
            }
            if (!saveStoredMeasure(measure)) {
                return null;
            }

            Aggregator aggregator = measure.getAggregator();
            org.eclipse.daanse.sql.statement.api.expression.SqlExpression innerNode;
            if (aggStar != null
                && measure.getStarMeasure() instanceof RolapStar.Column)
            {
                RolapStar.Column column =
                    (RolapStar.Column) measure.getStarMeasure();
                int bitPos = column.getBitPosition();
                AggStar.Table.Column aggColumn = aggStar.lookupColumn(bitPos);
                innerNode = aggColumn.toSqlExpression();
                if (innerNode == null) {
                    // The string body falls back to the legacy generateExprString here;
                    // that has no node form, so no node is emitted for this case.
                    return null;
                }
                if (aggColumn instanceof AggStar.FactTable.Measure) {
                    Aggregator aggTableAggregator =
                        ((AggStar.FactTable.Measure) aggColumn)
                            .getAggregator();
                    aggregator = (Aggregator) aggTableAggregator
                        .getRollup();
                }
            } else {
                RolapSqlExpression defExp =
                    measure.getDaanseDefExpression();
                innerNode = (defExp == null)
                    ? org.eclipse.daanse.sql.statement.api.Expressions.star()
                    : org.eclipse.daanse.rolap.common.sqlbuild.JoinPlanner.expressionFor(defExp);
            }

            return (aggregator instanceof org.eclipse.daanse.rolap.aggregator.AbstractAggregator agg)
                ? agg.getExpression(innerNode)
                : null;
        }

        @Override
		public String toString() {
            return "StoredMeasureSqlCompiler";
        }
    }

    /**
     * Compiles a MATCHES MDX operator into SQL regular
     * expression match.
     */
    class MatchingSqlCompiler extends FunCallSqlCompilerBase {

        protected MatchingSqlCompiler()
        {
            super(DataType.LOGICAL, "MATCHES", 2);
        }

        @Override
		public StringBuilder compile(Expression exp) {
            if (!match(exp)) {
                return null;
            }
            if (!dialect.allowsRegularExpressionInWhereClause()
                || !(exp instanceof ResolvedFunCallImpl)
                || evaluator == null)
            {
                return null;
            }

            final Expression arg0 = ((ResolvedFunCallImpl)exp).getArg(0);
            final Expression arg1 = ((ResolvedFunCallImpl)exp).getArg(1);

            // Must finish by ".Caption" or ".Name"
            if (!(arg0 instanceof ResolvedFunCallImpl rfci)
                || rfci.getArgCount() != 1
                || !(arg0.getType() instanceof StringType)
                || (!rfci.getOperationAtom().name().equals("Name")
                    && !rfci.getOperationAtom().name().equals("Caption")))
            {
                return null;
            }

            final boolean useCaption;
            if (((ResolvedFunCallImpl)arg0).getOperationAtom().name().equals("Name")) {
                useCaption = false;
            } else {
                useCaption = true;
            }

            // Must be ".CurrentMember"
            final Expression currMemberExpr = ((ResolvedFunCallImpl)arg0).getArg(0);
            if (!(currMemberExpr instanceof ResolvedFunCallImpl rfci2)
                || rfci2.getArgCount() != 1
                || !(currMemberExpr.getType() instanceof MemberType)
                || !rfci2.getOperationAtom().name().equals("CurrentMember"))
            {
                return null;
            }

            // Must be a dimension, a hierarchy or a level.
            final RolapCubeDimension dimension;
            final Expression dimExpr = ((ResolvedFunCallImpl)currMemberExpr).getArg(0);
            if (dimExpr instanceof DimensionExpression) {
                dimension =
                    (RolapCubeDimension) evaluator.getCachedResult(
                        new ExpCacheDescriptorImpl(dimExpr, evaluator));
            } else if (dimExpr instanceof HierarchyExpressionImpl) {
                final RolapCubeHierarchy hierarchy =
                    (RolapCubeHierarchy) evaluator.getCachedResult(
                        new ExpCacheDescriptorImpl(dimExpr, evaluator));
                dimension = (RolapCubeDimension) hierarchy.getDimension();
            } else if (dimExpr instanceof LevelExpression) {
                final RolapCubeLevel level =
                    (RolapCubeLevel) evaluator.getCachedResult(
                        new ExpCacheDescriptorImpl(dimExpr, evaluator));
                dimension = level.getDimension();
            } else {
                return null;
            }

            if (rolapLevel != null
                && dimension.equalsOlapElement(rolapLevel.getDimension()))
            {
                // We can't use the evaluator because the filter is filtering
                // a set which is uses same dimension as the predicate.
                // We must use, in order of priority,
                //  - caption requested: caption->name->key
                //  - name requested: name->key
                SqlExpression expression = useCaption
                ? rolapLevel.getCaptionExp() == null
                        ? rolapLevel.getNameExp() == null
                            ? rolapLevel.getKeyExp()
                            : rolapLevel.getNameExp()
                        : rolapLevel.getCaptionExp()
                    : rolapLevel.getNameExp() == null
                        ? rolapLevel.getKeyExp()
                        : rolapLevel.getNameExp();
                 // If an aggregation table is used, it might be more efficient
                 // to use only the aggregate table and not the hierarchy table.
                 // Try to lookup the column bit key. If that fails, we will
                 // link the aggregate table to the hierarchy table. If no
                 // aggregate table is used, we can use the column expression
                 // directly.
                String sourceExp;
                if (aggStar != null
                    && rolapLevel instanceof RolapCubeLevel
                    && expression == rolapLevel.getKeyExp())
                {
                    int bitPos =
                        ((RolapCubeLevel)rolapLevel).getStarKeyColumn()
                            .getBitPosition();
                    org.eclipse.daanse.rolap.common.aggmatcher.AggStar.Table.Column col =
                        aggStar.lookupColumn(bitPos);
                    if (col != null) {
                        org.eclipse.daanse.sql.statement.api.expression.SqlExpression colNode = col.toSqlExpression();
                        sourceExp = colNode != null
                            ? org.eclipse.daanse.rolap.common.SqlRender.renderExpression(colNode, dialect)
                            : col.generateExprString(dialect);
                    } else {
                        // Make sure the level table is part of the query.
                        ctx.addToFrom(
                            rolapLevel.getHierarchy(),
                            expression);
                        sourceExp = org.eclipse.daanse.rolap.common.SqlRender.renderExpression(
                        org.eclipse.daanse.rolap.common.sqlbuild.JoinPlanner.expressionFor(expression), dialect);
                    }
                } else if (aggStar != null) {
                    // Make sure the level table is part of the query.
                    ctx.addToFrom(rolapLevel.getHierarchy(), expression);
                    sourceExp = org.eclipse.daanse.rolap.common.SqlRender.renderExpression(
                        org.eclipse.daanse.rolap.common.sqlbuild.JoinPlanner.expressionFor(expression), dialect);
                } else {
                    sourceExp = org.eclipse.daanse.rolap.common.SqlRender.renderExpression(
                        org.eclipse.daanse.rolap.common.sqlbuild.JoinPlanner.expressionFor(expression), dialect);
                }

                // The dialect might require the use of the alias rather
                // then the column exp. Resolve the SELECT alias dialect-free, by the (builder node of the)
                // expression rather than its rendered string.
                if (dialect.requiresHavingAlias()) {
                    sourceExp = ctx.getAlias(expression);
                }
                return
                    dialect.regexGenerator().generateRegularExpression(
                        sourceExp,
                        String.valueOf(
                            evaluator.getCachedResult(
                                new ExpCacheDescriptorImpl(arg1, evaluator))))
                        .map(StringBuilder::new)
                        .orElse(null);
            } else {
                return null;
            }
        }

        /**
         * Node twin of {@link #compile}: same guards (MATCHES shape, regex-capable dialect,
         * {@code Name|Caption} of {@code CurrentMember} of the filtered dimension), emitting a
         * {@link Predicate.Regexp} over the source column node and the evaluated pattern —
         * the {@code RolapNativeFilter.tryRegexHaving} form, with the string body's
         * caption-&gt;name-&gt;key source resolution and the string body's
         * {@code ctx.addToFrom} side effects (the node channel runs FIRST now, so it owns
         * them; the string fallback's repeats are idempotent). The renderer's Regexp branch
         * calls the SAME {@code dialect.regexGenerator().generateRegularExpression(source,
         * pattern)} — and on a {@code requiresHavingAlias()} dialect (e.g. MySQL) it resolves
         * the source to its SELECT alias from the HAVING-scoped projections
         * ({@code DialectSqlRenderer.aliasForHavingSource}), reproducing the string body's
         * {@code ctx.getAlias(expression)} swap ({@code c5 IS NOT NULL AND ...}) — so the
         * whole fragment (null-guard + optional UPPER + regex operator) matches on every
         * dialect. Present-ness stays equal to the string channel via the pattern probe:
         * every dialect's {@code generateRegularExpression} is empty exactly for an invalid
         * Java regex (source-independent), which made the string body return null. One
         * documented null-emit remains: an agg-table column without a node form
         * ({@code col.toSqlExpression() == null}) — the string fallback then runs the legacy
         * {@code generateExprString} path.
         */
        @Override
        public Predicate compileNodePredicate(Expression exp) {
            if (!match(exp)) {
                return null;
            }
            if (!dialect.allowsRegularExpressionInWhereClause()
                || !(exp instanceof ResolvedFunCallImpl)
                || evaluator == null)
            {
                return null;
            }

            final Expression arg0 = ((ResolvedFunCallImpl)exp).getArg(0);
            final Expression arg1 = ((ResolvedFunCallImpl)exp).getArg(1);

            // Must finish by ".Caption" or ".Name"
            if (!(arg0 instanceof ResolvedFunCallImpl rfci)
                || rfci.getArgCount() != 1
                || !(arg0.getType() instanceof StringType)
                || (!rfci.getOperationAtom().name().equals("Name")
                    && !rfci.getOperationAtom().name().equals("Caption")))
            {
                return null;
            }

            final boolean useCaption =
                !((ResolvedFunCallImpl)arg0).getOperationAtom().name().equals("Name");

            // Must be ".CurrentMember"
            final Expression currMemberExpr = ((ResolvedFunCallImpl)arg0).getArg(0);
            if (!(currMemberExpr instanceof ResolvedFunCallImpl rfci2)
                || rfci2.getArgCount() != 1
                || !(currMemberExpr.getType() instanceof MemberType)
                || !rfci2.getOperationAtom().name().equals("CurrentMember"))
            {
                return null;
            }

            // Must be a dimension, a hierarchy or a level.
            final RolapCubeDimension dimension;
            final Expression dimExpr = ((ResolvedFunCallImpl)currMemberExpr).getArg(0);
            if (dimExpr instanceof DimensionExpression) {
                dimension =
                    (RolapCubeDimension) evaluator.getCachedResult(
                        new ExpCacheDescriptorImpl(dimExpr, evaluator));
            } else if (dimExpr instanceof HierarchyExpressionImpl) {
                final RolapCubeHierarchy hierarchy =
                    (RolapCubeHierarchy) evaluator.getCachedResult(
                        new ExpCacheDescriptorImpl(dimExpr, evaluator));
                dimension = (RolapCubeDimension) hierarchy.getDimension();
            } else if (dimExpr instanceof LevelExpression) {
                final RolapCubeLevel level =
                    (RolapCubeLevel) evaluator.getCachedResult(
                        new ExpCacheDescriptorImpl(dimExpr, evaluator));
                dimension = level.getDimension();
            } else {
                return null;
            }

            if (rolapLevel == null
                || !dimension.equalsOlapElement(rolapLevel.getDimension()))
            {
                return null;
            }
            SqlExpression expression = useCaption
            ? rolapLevel.getCaptionExp() == null
                    ? rolapLevel.getNameExp() == null
                        ? rolapLevel.getKeyExp()
                        : rolapLevel.getNameExp()
                    : rolapLevel.getCaptionExp()
                : rolapLevel.getNameExp() == null
                    ? rolapLevel.getKeyExp()
                    : rolapLevel.getNameExp();
            org.eclipse.daanse.sql.statement.api.expression.SqlExpression sourceNode;
            if (aggStar != null
                && rolapLevel instanceof RolapCubeLevel
                && expression == rolapLevel.getKeyExp())
            {
                int bitPos =
                    ((RolapCubeLevel)rolapLevel).getStarKeyColumn()
                        .getBitPosition();
                AggStar.Table.Column col = aggStar.lookupColumn(bitPos);
                if (col != null) {
                    sourceNode = col.toSqlExpression();
                    if (sourceNode == null) {
                        // Documented null-emit: the string fallback runs the legacy
                        // generateExprString path for this column-less agg column.
                        return null;
                    }
                } else {
                    // Make sure the level table is part of the query (string-body side effect,
                    // owned by the node channel since the flip).
                    ctx.addToFrom(rolapLevel.getHierarchy(), expression);
                    sourceNode = org.eclipse.daanse.rolap.common.sqlbuild.JoinPlanner
                        .expressionFor(expression);
                }
            } else if (aggStar != null) {
                // Make sure the level table is part of the query.
                ctx.addToFrom(rolapLevel.getHierarchy(), expression);
                sourceNode = org.eclipse.daanse.rolap.common.sqlbuild.JoinPlanner
                    .expressionFor(expression);
            } else {
                sourceNode = org.eclipse.daanse.rolap.common.sqlbuild.JoinPlanner
                    .expressionFor(expression);
            }
            String pattern = String.valueOf(
                evaluator.getCachedResult(
                    new ExpCacheDescriptorImpl(arg1, evaluator)));
            // Present-ness probe: every dialect's generateRegularExpression is empty exactly for
            // an invalid Java regex (never source-dependent) — the shape where the string body
            // returned null. Probe with the rendered source; the render-time call (which may swap
            // the source for its SELECT alias) then cannot come up empty.
            if (dialect.regexGenerator().generateRegularExpression(
                    org.eclipse.daanse.rolap.common.SqlRender.renderExpression(sourceNode, dialect),
                    pattern).isEmpty()) {
                return null;
            }
            return new Predicate.Regexp(sourceNode, pattern, false);
        }

        @Override
		public String toString() {
            return "MatchingSqlCompiler";
        }
    }

    /**
     * Compiles the underlying expression of a calculated member.
     */
    class CalculatedMemberSqlCompiler extends MemberSqlCompiler {
        SqlCompiler compiler;

        CalculatedMemberSqlCompiler(SqlCompiler argumentCompiler) {
            this.compiler = argumentCompiler;
        }

        @Override
		public StringBuilder compile(Expression exp) {
            exp = unwind(exp);
            if (!(exp instanceof MemberExpression)) {
                return null;
            }
            final Member member = ((MemberExpression) exp).getMember();
            if (!(member instanceof RolapCalculatedMember)) {
                return null;
            }
            exp = member.getExpression();
            if (exp == null) {
                return null;
            }
            return compiler.compile(exp);
        }

        /**
         * Node twin of {@link #compile}: same guards (non-MemberExpression / non-calculated /
         * null resolved expression early-outs), then pure delegation of the resolved
         * expression to the delegate's node channel (the composite walks its children over
         * {@link CompositeSqlCompiler#compileNodeExpr}).
         */
        @Override
        public org.eclipse.daanse.sql.statement.api.expression.SqlExpression compileNodeExpr(Expression exp) {
            exp = unwind(exp);
            if (!(exp instanceof MemberExpression)) {
                return null;
            }
            final Member member = ((MemberExpression) exp).getMember();
            if (!(member instanceof RolapCalculatedMember)) {
                return null;
            }
            exp = member.getExpression();
            if (exp == null) {
                return null;
            }
            return compiler.compileNodeExpr(exp);
        }

        @Override
		public String toString() {
            return "CalculatedMemberSqlCompiler";
        }
    }

    /**
     * Contains utility methods to compile FunCall expressions into SQL.
     */
    abstract class FunCallSqlCompilerBase implements SqlCompiler {
    	DataType category;
        String mdx;
        int argCount;

        FunCallSqlCompilerBase(DataType category, String mdx, int argCount) {
            this.category = category;
            this.mdx = mdx;
            this.argCount = argCount;
        }

        /**
         * @return true if exp is a matching FunCall
         */
        protected boolean match(Expression exp) {
            if (exp.getCategory() != category) {//TODO: REVIEW BITWISE
                return false;
            }
            if (!(exp instanceof FunctionCall fc)) {
                return false;
            }
            if (!mdx.equalsIgnoreCase(fc.getOperationAtom().name())) {
                return false;
            }
            Expression[] args = fc.getArgs();
            if (args.length != argCount) {
                return false;
            }
            return true;
        }

        /**
         * compiles the arguments of a FunCall
         *
         * @return array of expressions or null if either exp does not match or
         * any argument could not be compiled.
         */
        protected StringBuilder[] compileArgs(Expression exp, SqlCompiler compiler) {
            if (!match(exp)) {
                return null;
            }
            Expression[] args = ((FunctionCall) exp).getArgs();
            StringBuilder[] sqls = new StringBuilder[args.length];
            for (int i = 0; i < args.length; i++) {
                sqls[i] = compiler.compile(args[i]);
                if (sqls[i] == null) {
                    return null;
                }
            }
            return sqls;
        }

    }

    /**
     * Compiles a funcall, e.g. foo(a, b, c).
     */
    class FunCallSqlCompiler extends FunCallSqlCompilerBase {
        SqlCompiler compiler;
        String sql;

        protected FunCallSqlCompiler(
        		DataType category, String mdx, String sql,
            int argCount, SqlCompiler argumentCompiler)
        {
            super(category, mdx, argCount);
            this.sql = sql;
            this.compiler = argumentCompiler;
        }

        @Override
		public StringBuilder compile(Expression exp) {
            StringBuilder[] args = compileArgs(exp, compiler);
            if (args == null) {
                return null;
            }
            StringBuilder buf = new StringBuilder();
            buf.append(sql);
            buf.append("(");
            for (int i = 0; i < args.length; i++) {
                if (i > 0) {
                    buf.append(", ");
                }
                buf.append(args[i]);
            }
            buf.append(") ");
            return buf;
        }

        @Override
		public String toString() {
            return new StringBuilder("FunCallSqlCompiler[").append(mdx).append("]").toString();
        }
    }

    /**
     * Shortcut for an unary operator like NOT(a).
     */
    class UnaryOpSqlCompiler extends FunCallSqlCompiler {
        protected UnaryOpSqlCompiler(
            DataType category,
            String mdx,
            String sql,
            SqlCompiler argumentCompiler)
        {
            super(category, mdx, sql, 1, argumentCompiler);
        }

        /**
         * Node twin of the inherited {@link FunCallSqlCompiler#compile} (N4b): the only instance is
         * boolean {@code NOT}; emits {@code Predicates.not(operand)}. The renderer's Not branch
         * renders lowercase {@code not (x)} vs the retired string body's {@code NOT(x)} — the
         * accepted keyword-case divergence of ledger L002 (see InfixOp AND/OR).
         */
        @Override
        public Predicate compileNodePredicate(Expression exp) {
            if (!match(exp)) {
                return null;
            }
            Expression[] args = ((FunctionCall) exp).getArgs();
            Predicate operand = compiler.compileNodePredicate(args[0]);
            if (operand == null) {
                return null;
            }
            return org.eclipse.daanse.sql.statement.api.Predicates.not(operand);
        }
    }

    /**
     * Shortcut for ().
     */
    class ParenthesisSqlCompiler extends FunCallSqlCompiler {
        protected ParenthesisSqlCompiler(
        		DataType category,
            SqlCompiler argumentCompiler)
        {
            super(category, "()", "", 1, argumentCompiler);
        }

        /**
         * Node twin of the inherited {@link FunCallSqlCompiler#compile} for the value
         * (numeric) instance: same match guard ({@code "()"}, one argument), operand through
         * the delegate node channel, wrapped in a name-less {@code Function} — the renderer's
         * Call branch emits {@code name + "(" + args + ")"}, so the empty name renders exactly
         * {@code (arg)}, byte-identical to the string body {@code "" + "(" + arg + ") "}. (A
         * dedicated Paren expression node would be the cleaner endgame form; the statement
         * API has none yet, and this batch is restricted to this file.)
         */
        @Override
        public org.eclipse.daanse.sql.statement.api.expression.SqlExpression compileNodeExpr(Expression exp) {
            if (category == DataType.LOGICAL) {
                return null;
            }
            if (!match(exp)) {
                return null;
            }
            org.eclipse.daanse.sql.statement.api.expression.SqlExpression inner =
                compiler.compileNodeExpr(((FunctionCall) exp).getArgs()[0]);
            if (inner == null) {
                return null;
            }
            return org.eclipse.daanse.sql.statement.api.Expressions.function("", inner);
        }

        /**
         * Node twin for the boolean (LOGICAL) instance: operand through the delegate node
         * channel, wrapped in a single-operand {@code and} — the established paren-preserver,
         * whose Connective branch renders exactly {@code (inner)}, byte-identical to the
         * string body.
         */
        @Override
        public Predicate compileNodePredicate(Expression exp) {
            if (category != DataType.LOGICAL) {
                return null;
            }
            if (!match(exp)) {
                return null;
            }
            Predicate inner = compiler.compileNodePredicate(((FunctionCall) exp).getArgs()[0]);
            if (inner == null) {
                return null;
            }
            return org.eclipse.daanse.sql.statement.api.Predicates.and(List.of(inner));
        }

        @Override
		public String toString() {
            return "ParenthesisSqlCompiler";
        }
    }

    /**
     * Compiles an infix operator like addition into SQL like (a
     * + b).
     */
    class InfixOpSqlCompiler extends FunCallSqlCompilerBase {
        private final String sql;
        private final SqlCompiler compiler;

        protected InfixOpSqlCompiler(
        		DataType category,
            String mdx,
            String sql,
            SqlCompiler argumentCompiler)
        {
            super(category, mdx, 2);
            this.sql = sql;
            this.compiler = argumentCompiler;
        }

        @Override
		public StringBuilder compile(Expression exp) {
            StringBuilder[] args = compileArgs(exp, compiler);
            if (args == null) {
                return null;
            }
            return new StringBuilder("(").append(args[0]).append(" ").append(sql).append(" ").append(args[1]).append(")");
        }

        /** The builder arithmetic operator for {@link #sql}, or null if not arithmetic. */
        private org.eclipse.daanse.sql.statement.api.expression.ArithmeticOperator arithmeticOperator() {
            return switch (sql) {
                case "+" -> org.eclipse.daanse.sql.statement.api.expression.ArithmeticOperator.ADD;
                case "-" -> org.eclipse.daanse.sql.statement.api.expression.ArithmeticOperator.SUBTRACT;
                case "*" -> org.eclipse.daanse.sql.statement.api.expression.ArithmeticOperator.MULTIPLY;
                case "/" -> org.eclipse.daanse.sql.statement.api.expression.ArithmeticOperator.DIVIDE;
                default -> null;
            };
        }

        /** The builder comparison operator for {@link #sql}, or null if not a comparison. */
        private org.eclipse.daanse.sql.statement.api.expression.ComparisonOperator comparisonOperator() {
            return switch (sql) {
                case "=" -> org.eclipse.daanse.sql.statement.api.expression.ComparisonOperator.EQ;
                case "<>" -> org.eclipse.daanse.sql.statement.api.expression.ComparisonOperator.NE;
                case "<" -> org.eclipse.daanse.sql.statement.api.expression.ComparisonOperator.LT;
                case "<=" -> org.eclipse.daanse.sql.statement.api.expression.ComparisonOperator.LE;
                case ">" -> org.eclipse.daanse.sql.statement.api.expression.ComparisonOperator.GT;
                case ">=" -> org.eclipse.daanse.sql.statement.api.expression.ComparisonOperator.GE;
                default -> null;
            };
        }

        /**
         * Node twin of {@link #compile} for the value (numeric) instances (+ - * /): same
         * match guard, both operands through the delegate node channel, emitted as
         * {@code Expressions.arithmetic} — a {@code Binary} with {@code parenthesized=true},
         * which the renderer emits as {@code (left op right)}, byte-identical to the string
         * body's explicit parens. Null when either operand emits no node; the boolean
         * (LOGICAL) instances answer on the predicate channel instead.
         */
        @Override
        public org.eclipse.daanse.sql.statement.api.expression.SqlExpression compileNodeExpr(Expression exp) {
            if (category == DataType.LOGICAL) {
                return null;
            }
            org.eclipse.daanse.sql.statement.api.expression.ArithmeticOperator op = arithmeticOperator();
            if (op == null || !match(exp)) {
                return null;
            }
            Expression[] args = ((FunctionCall) exp).getArgs();
            org.eclipse.daanse.sql.statement.api.expression.SqlExpression left =
                compiler.compileNodeExpr(args[0]);
            if (left == null) {
                return null;
            }
            org.eclipse.daanse.sql.statement.api.expression.SqlExpression right =
                compiler.compileNodeExpr(args[1]);
            if (right == null) {
                return null;
            }
            return org.eclipse.daanse.sql.statement.api.Expressions.arithmetic(left, op, right);
        }

        /**
         * Node twin of {@link #compile} for the boolean (LOGICAL) instances. Comparisons
         * (&lt; &lt;= &gt; &gt;= = &lt;&gt;, numeric operands): the renderer's Comparison
         * branch emits {@code l op r} without parens, so the comparison is wrapped in a
         * single-operand {@code and} — the established paren-preserver, whose Connective
         * branch adds exactly the string body's outer "( ... )". AND / OR emit Connective
         * nodes; the renderer's lowercase {@code (a and b)} vs the retired string body's
         * configured uppercase "AND"/"OR" is the accepted keyword-case divergence of ledger
         * L002.
         */
        @Override
        public Predicate compileNodePredicate(Expression exp) {
            if (category != DataType.LOGICAL) {
                return null;
            }
            // AND / OR emit Connective nodes. The renderer's Connective branch renders lowercase
            // "(a and b)" where the retired string body emitted the configured uppercase
            // "AND"/"OR" — the accepted keyword-case divergence of ledger L002 (result-neutral).
            if (("AND".equals(sql) || "OR".equals(sql)) && match(exp)) {
                Expression[] cArgs = ((FunctionCall) exp).getArgs();
                Predicate l = compiler.compileNodePredicate(cArgs[0]);
                if (l == null) {
                    return null;
                }
                Predicate r = compiler.compileNodePredicate(cArgs[1]);
                if (r == null) {
                    return null;
                }
                return "AND".equals(sql)
                    ? org.eclipse.daanse.sql.statement.api.Predicates.and(List.of(l, r))
                    : org.eclipse.daanse.sql.statement.api.Predicates.or(List.of(l, r));
            }
            org.eclipse.daanse.sql.statement.api.expression.ComparisonOperator op = comparisonOperator();
            if (op == null || !match(exp)) {
                return null;
            }
            Expression[] args = ((FunctionCall) exp).getArgs();
            org.eclipse.daanse.sql.statement.api.expression.SqlExpression left =
                compiler.compileNodeExpr(args[0]);
            if (left == null) {
                return null;
            }
            org.eclipse.daanse.sql.statement.api.expression.SqlExpression right =
                compiler.compileNodeExpr(args[1]);
            if (right == null) {
                return null;
            }
            return org.eclipse.daanse.sql.statement.api.Predicates.and(List.of(
                org.eclipse.daanse.sql.statement.api.Predicates.comparison(left, op, right)));
        }

        @Override
		public String toString() {
            return new StringBuilder("InfixSqlCompiler[").append(mdx).append("]").toString();
        }
    }

    /**
     * Compiles an IsEmpty(measure)
     * expression into SQL measure is null.
     */
    class IsEmptySqlCompiler extends FunCallSqlCompilerBase {
        private final SqlCompiler compiler;

        protected IsEmptySqlCompiler(
        		DataType category, String mdx,
            SqlCompiler argumentCompiler)
        {
            super(category, mdx, 1);
            this.compiler = argumentCompiler;
        }

        @Override
		public StringBuilder compile(Expression exp) {
            StringBuilder[] args = compileArgs(exp, compiler);
            if (args == null) {
                return null;
            }
            return new StringBuilder("(").append(args[0]).append(" is null").append(")");
        }

        /**
         * Node twin of {@link #compile}: same match guard, operand through the (numeric)
         * delegate node channel, emitted as {@code isNull} wrapped in a single-operand
         * {@code and} — the renderer's IsNull branch emits {@code x is null} without parens
         * and the Connective wrapper adds exactly the string body's outer "( ... )", so the
         * pair renders byte-identical {@code (x is null)}.
         */
        @Override
        public Predicate compileNodePredicate(Expression exp) {
            if (!match(exp)) {
                return null;
            }
            org.eclipse.daanse.sql.statement.api.expression.SqlExpression inner =
                compiler.compileNodeExpr(((FunctionCall) exp).getArgs()[0]);
            if (inner == null) {
                return null;
            }
            return org.eclipse.daanse.sql.statement.api.Predicates.and(List.of(
                org.eclipse.daanse.sql.statement.api.Predicates.isNull(inner)));
        }

        @Override
		public String toString() {
            return new StringBuilder("IsEmptySqlCompiler[").append(mdx).append("]").toString();
        }
    }

    /**
     * Compiles an IIF(cond, val1, val2) expression into SQL
     * CASE WHEN cond THEN val1 ELSE val2 END.
     */
    class IifSqlCompiler extends FunCallSqlCompilerBase {

        SqlCompiler valueCompiler;

        IifSqlCompiler(DataType category, SqlCompiler valueCompiler) {
            super(category, "iif", 3);
            this.valueCompiler = valueCompiler;
        }

        @Override
		public StringBuilder compile(Expression exp) {
            if (!match(exp)) {
                return null;
            }
            Expression[] args = ((FunctionCall) exp).getArgs();
            StringBuilder cond = booleanCompiler.compile(args[0]);
            StringBuilder val1 = valueCompiler.compile(args[1]);
            StringBuilder val2 = valueCompiler.compile(args[2]);
            if (cond == null || val1 == null || val2 == null) {
                return null;
            }
            return dialect.functionGenerator().wrapIntoSqlIfThenElseFunction(cond, val1, val2);
        }

        /**
         * Node twin of {@link #compile} (N4a): a single-when {@code SqlExpression.Case} — the
         * renderer's Case branch now DELEGATES to the same
         * {@code dialect.functionGenerator().wrapIntoSqlIfThenElseFunction} the string body calls
         * (sql repo 7672d9c), so the node renders byte-identically on every dialect (incl. the
         * Access {@code IIF(c,a,b)} override). Operand nodes via the same delegate channels as the
         * string body; any null operand null-emits.
         */
        @Override
        public org.eclipse.daanse.sql.statement.api.expression.SqlExpression compileNodeExpr(Expression exp) {
            if (!match(exp)) {
                return null;
            }
            Expression[] args = ((FunctionCall) exp).getArgs();
            Predicate condNode = booleanCompiler.compileNodePredicate(args[0]);
            org.eclipse.daanse.sql.statement.api.expression.SqlExpression v1 =
                valueCompiler.compileNodeExpr(args[1]);
            org.eclipse.daanse.sql.statement.api.expression.SqlExpression v2 =
                valueCompiler.compileNodeExpr(args[2]);
            if (condNode == null || v1 == null || v2 == null) {
                return null;
            }
            return new org.eclipse.daanse.sql.statement.api.expression.SqlExpression.Case(
                java.util.List.of(new org.eclipse.daanse.sql.statement.api.expression.SqlExpression.Case.WhenClause(
                    condNode, v1)),
                java.util.Optional.of(v2));
        }

        /**
         * Node twin for the boolean (LOGICAL) instance: the predicate channel has no CASE form —
         * documented null-emit (a LOGICAL IIF would need a boolean-valued CASE predicate node).
         */
        @Override
        public Predicate compileNodePredicate(Expression exp) {
            return null;
        }
    }

    /**
     * Creates a RolapNativeSql against the given query seam (see {@link NativeSqlContext}) —
     * the query is needed for different SQL dialects; it is not modified.
     */
    public RolapNativeSql(
        NativeSqlContext ctx,
        AggStar aggStar,
        Evaluator evaluator,
        RolapLevel rolapLevel)
    {
        this.ctx = ctx;
        this.rolapLevel = rolapLevel;
        this.evaluator = evaluator;
        this.dialect = ctx.getDialect();
        this.aggStar = aggStar;

        numericCompiler = new CompositeSqlCompiler();
        booleanCompiler = new CompositeSqlCompiler();

        numericCompiler.add(new NumberSqlCompiler());
        numericCompiler.add(new StoredMeasureSqlCompiler());
        numericCompiler.add(new CalculatedMemberSqlCompiler(numericCompiler));
        numericCompiler.add(
            new ParenthesisSqlCompiler(DataType.NUMERIC, numericCompiler));
        numericCompiler.add(
            new InfixOpSqlCompiler(
                DataType.NUMERIC, "+", "+", numericCompiler));
        numericCompiler.add(
            new InfixOpSqlCompiler(
                DataType.NUMERIC, "-", "-", numericCompiler));
        numericCompiler.add(
            new InfixOpSqlCompiler(
                DataType.NUMERIC, "/", "/", numericCompiler));
        numericCompiler.add(
            new InfixOpSqlCompiler(
                DataType.NUMERIC, "*", "*", numericCompiler));
        numericCompiler.add(
            new IifSqlCompiler(DataType.NUMERIC, numericCompiler));

        booleanCompiler.add(
            new InfixOpSqlCompiler(
                DataType.LOGICAL, "<", "<", numericCompiler));
        booleanCompiler.add(
            new InfixOpSqlCompiler(
                DataType.LOGICAL, "<=", "<=", numericCompiler));
        booleanCompiler.add(
            new InfixOpSqlCompiler(
                DataType.LOGICAL, ">", ">", numericCompiler));
        booleanCompiler.add(
            new InfixOpSqlCompiler(
                DataType.LOGICAL, ">=", ">=", numericCompiler));
        booleanCompiler.add(
            new InfixOpSqlCompiler(
                DataType.LOGICAL, "=", "=", numericCompiler));
        booleanCompiler.add(
            new InfixOpSqlCompiler(
                DataType.LOGICAL, "<>", "<>", numericCompiler));
        booleanCompiler.add(
            new IsEmptySqlCompiler(
                DataType.LOGICAL, "IsEmpty", numericCompiler));

        booleanCompiler.add(
            new InfixOpSqlCompiler(
                DataType.LOGICAL, "and", "AND", booleanCompiler));
        booleanCompiler.add(
            new InfixOpSqlCompiler(
                DataType.LOGICAL, "or", "OR", booleanCompiler));
        booleanCompiler.add(
            new UnaryOpSqlCompiler(
                DataType.LOGICAL, "not", "NOT", booleanCompiler));
        booleanCompiler.add(
            new MatchingSqlCompiler());
        booleanCompiler.add(
            new ParenthesisSqlCompiler(DataType.LOGICAL, booleanCompiler));
        booleanCompiler.add(
            new IifSqlCompiler(DataType.LOGICAL, booleanCompiler));
    }

    /**
     * Generates an aggregate of a measure as a builder node, e.g. {@code sum(Store_Sales)}
     * for TopCount. The returned node is added to the select list and to the order by
     * clause. Node channel first (the flip); a null-emit falls back to the legacy string
     * compile wrapped as a {@code raw} node, so every shape the string compiler handled
     * still produces SQL. Returns null iff the expression cannot be converted at all.
     */
    public org.eclipse.daanse.sql.statement.api.expression.SqlExpression generateTopCountOrderByNode(Expression exp) {
        org.eclipse.daanse.sql.statement.api.expression.SqlExpression node = numericCompiler.compileNodeExpr(exp);
        if (node != null) {
            return node;
        }
        StringBuilder sql = numericCompiler.compile(exp);
        return sql == null ? null : org.eclipse.daanse.sql.statement.api.Expressions.raw(sql.toString());
    }

    /**
     * Generates a native filter condition as a builder predicate. Node channel first (the
     * flip); a null-emit (LOGICAL IIF / column-less agg column / non-node aggregator) falls
     * back to the legacy string compile wrapped as a {@code raw} predicate. Returns null iff
     * the expression cannot be converted at all — present-ness is identical to the retired
     * string form.
     */
    public Predicate generateFilterPredicate(Expression exp) {
        Predicate node = booleanCompiler.compileNodePredicate(exp);
        if (node != null) {
            return node;
        }
        StringBuilder sql = booleanCompiler.compile(exp);
        return sql == null ? null : org.eclipse.daanse.sql.statement.api.Predicates.raw(sql.toString());
    }

    public RolapStoredMeasure getStoredMeasure() {
        return storedMeasure;
    }

}
