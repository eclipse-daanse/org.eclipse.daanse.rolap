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
 * Compiles MDX filter and function expressions into dialect-free SQL nodes for native
 * evaluation. Each compiler answers over {@link SqlCompiler#compileNodeExpr} (value
 * expressions) / {@link SqlCompiler#compileNodePredicate} (boolean expressions), emitting a
 * builder node ({@code org.eclipse.daanse.sql.statement.api.expression.SqlExpression} /
 * {@link Predicate}) that the statement renderer spells against the target dialect at render
 * time. AND/OR/NOT map to {@code Predicates} connective / negation nodes, comparisons and
 * {@code IsEmpty} to comparison / IS NULL predicates, arithmetic to {@code Expressions}
 * operators, and {@code IIF} to a {@code SqlExpression.Case} node.
 *
 * <p>{@link #generateFilterPredicate} / {@link #generateTopCountOrderByNode} return the node
 * or {@code null}; a null routes the caller to non-native evaluation (correct, at most
 * slower). No {@code Dialect} is held; the only upstream capability read is the
 * {@code regexInWhereSupported} boolean (the MATCHES routing gate).
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

    /** The one routing capability the native compiler needs upstream: whether the dialect
     *  can express MATCHES as a WHERE/HAVING regex at all (the Predicate.Regexp emission gate).
     *  Captured as a plain boolean so no Dialect is held. */
    private final boolean regexInWhereSupported;

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
         * Returns the builder node for a value expression, or null if this compiler cannot
         * emit a node for it.
         */
        default org.eclipse.daanse.sql.statement.api.expression.SqlExpression compileNodeExpr(Expression exp) {
            return null;
        }

        /**
         * Returns the builder predicate for a boolean expression, or null if this compiler
         * cannot emit a node for it.
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


        /**
         * Chain of responsibility: the first child emitting a node wins. The children's match
         * guards are mutually exclusive (each matches a distinct expression shape), so a null
         * answer means the one matching child cannot emit a node, never that a later child was
         * skipped.
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

        /** The predicate-channel counterpart of {@link #compileNodeExpr(Expression)}. */
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

        /**
         * Compiles a numeric literal: skips non-Literal / INTEGER-category / non-decimal
         * inputs, and emits the decimal as a builder literal node the renderer quotes via
         * {@code dialect.quote(value, NUMERIC)}.
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
                // User-error contract: a non-decimal literal is rejected.
                throw new DaanseEvaluationException(
                    "Expected to get decimal, but got " + expr);
            }
            return org.eclipse.daanse.sql.statement.api.Expressions.literal(
                literal.getValue(), org.eclipse.daanse.jdbc.db.api.type.Datatype.NUMERIC);
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


        /**
         * Compiles a stored measure into its aggregate node. Skips calculated or non-stored
         * measures, and records the measure via {@link #saveStoredMeasure} (returning null when
         * measures span different stars). The inner node is the agg-table column
         * ({@code aggColumn.toSqlExpression()}) under an aggStar — swapping in the agg table's
         * rollup aggregator — otherwise the measure's own definition expression
         * ({@code expressionFor(defExp)}) or {@code star()} for a count. The aggregator wraps the
         * inner node via {@code aggregator.toNode}. Emits no node for a column-less agg column or
         * an aggregator with no node form (composite / dialect-generator).
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
                    // A column-less agg column has no node form; emit no node for this case.
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

            return org.eclipse.daanse.rolap.aggregator.SqlNodeAggregator.toNodeOrNull(
                aggregator, innerNode);
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


        /**
         * Compiles a MATCHES call into a regex predicate. Requires a regex-capable dialect and a
         * {@code Name|Caption} of {@code CurrentMember} of the filtered dimension, emitting a
         * {@link Predicate.Regexp} over the source column node and the evaluated pattern
         * (caption-&gt;name-&gt;key source resolution, joining the referenced table into the FROM
         * via {@code ctx.addToFrom}). The renderer's Regexp branch calls
         * {@code dialect.regexGenerator().generateRegularExpression(source, pattern)}, and on a
         * {@code requiresHavingAlias()} dialect (e.g. MySQL) resolves the source to its SELECT
         * alias from the HAVING-scoped projections ({@code DialectSqlRenderer.aliasForHavingSource},
         * reproducing {@code ctx.getAlias(expression)}). The pattern is validated directly as a Java
         * regex — an invalid pattern makes {@code generateRegularExpression} empty on every dialect,
         * so the compiler returns null. Emits no node for a column-less agg column.
         */
        @Override
        public Predicate compileNodePredicate(Expression exp) {
            if (!match(exp)) {
                return null;
            }
            if (!regexInWhereSupported
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
                        // A column-less agg column has no node form; emit no node.
                        return null;
                    }
                } else {
                    // Make sure the level table is part of the query.
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
            // Validate the pattern directly: every dialect's generateRegularExpression is empty
            // exactly for an invalid Java regex (never source-dependent), so a bad pattern is
            // rejected here without rendering the source against the dialect.
            try {
                java.util.regex.Pattern.compile(pattern);
            } catch (java.util.regex.PatternSyntaxException invalid) {
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


        /**
         * Compiles the underlying expression of a calculated member: skips non-MemberExpression
         * / non-calculated members and a null resolved expression, then delegates the resolved
         * expression to {@link CompositeSqlCompiler#compileNodeExpr}.
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
         * Compiles the boolean {@code NOT} operator: emits {@code Predicates.not(operand)},
         * which the renderer's Not branch spells {@code not (x)}.
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
         * Compiles a parenthesised value (numeric) expression: matches {@code "()"} with one
         * argument, compiles the operand, and wraps it in a name-less {@code Function} — the
         * renderer's Call branch emits {@code name + "(" + args + ")"}, so the empty name renders
         * exactly {@code (arg)}. (A dedicated Paren expression node would be cleaner; the statement
         * API has none yet.)
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
         * Compiles a parenthesised boolean (LOGICAL) expression: compiles the operand and wraps
         * it in a single-operand {@code and} — the paren-preserver whose Connective branch
         * renders exactly {@code (inner)}.
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
         * Compiles an infix value (numeric) operator (+ - * /): compiles both operands and emits
         * {@code Expressions.arithmetic} — a parenthesised {@code Binary} the renderer spells
         * {@code (left op right)}. Null when either operand emits no node; the boolean (LOGICAL)
         * instances answer on the predicate channel instead.
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
         * Compiles an infix boolean (LOGICAL) operator. Comparisons
         * (&lt; &lt;= &gt; &gt;= = &lt;&gt;, numeric operands): the renderer's Comparison branch
         * emits {@code l op r} without parens, so the comparison is wrapped in a single-operand
         * {@code and} — the paren-preserver whose Connective branch adds the outer "( ... )".
         * AND / OR emit Connective nodes the renderer spells {@code (a and b)}.
         */
        @Override
        public Predicate compileNodePredicate(Expression exp) {
            if (category != DataType.LOGICAL) {
                return null;
            }
            // AND / OR emit Connective nodes; the renderer's Connective branch renders "(a and b)".
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


        /**
         * Compiles {@code IsEmpty(measure)}: compiles the operand and emits {@code isNull}
         * wrapped in a single-operand {@code and} — the renderer's IsNull branch emits
         * {@code x is null} and the Connective wrapper adds the outer parens, rendering
         * {@code (x is null)}.
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


        /**
         * Compiles {@code IIF(cond, val1, val2)} into a single-when {@code SqlExpression.Case} —
         * the renderer's Case branch delegates to
         * {@code dialect.functionGenerator().wrapIntoSqlIfThenElseFunction}, so it renders per
         * dialect (incl. the Access {@code IIF(c,a,b)} override). Any null operand emits no node.
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
         * The boolean (LOGICAL) instance emits no node: the predicate channel has no CASE form
         * (a LOGICAL IIF would need a boolean-valued CASE predicate node).
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
        this.regexInWhereSupported = ctx.getDialect().allowsRegularExpressionInWhereClause();
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
     * clause. Returns null if the expression cannot be converted to SQL.
     */
    public org.eclipse.daanse.sql.statement.api.expression.SqlExpression generateTopCountOrderByNode(Expression exp) {
        return numericCompiler.compileNodeExpr(exp);
    }

    /**
     * Generates a native filter condition as a builder predicate. Returns null if the
     * expression cannot be converted to SQL (e.g. a LOGICAL {@code IIF}, a column-less agg
     * column, or a non-node aggregator).
     */
    public Predicate generateFilterPredicate(Expression exp) {
        return booleanCompiler.compileNodePredicate(exp);
    }

    public RolapStoredMeasure getStoredMeasure() {
        return storedMeasure;
    }

}
