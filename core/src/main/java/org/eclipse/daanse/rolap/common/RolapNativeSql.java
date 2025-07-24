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
package org.eclipse.daanse.rolap.common;

import static org.eclipse.daanse.rolap.common.util.ExpressionUtil.getExpression;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.eclipse.daanse.olap.api.DataType;
import org.eclipse.daanse.olap.api.Evaluator;
import org.eclipse.daanse.olap.api.SqlExpression;
import org.eclipse.daanse.olap.api.aggregator.Aggregator;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.api.query.component.DimensionExpression;
import org.eclipse.daanse.olap.api.query.component.Expression;
import org.eclipse.daanse.olap.api.query.component.FunctionCall;
import org.eclipse.daanse.olap.api.query.component.LevelExpression;
import org.eclipse.daanse.olap.api.query.component.Literal;
import org.eclipse.daanse.olap.api.query.component.MemberExpression;
import org.eclipse.daanse.olap.api.type.MemberType;
import org.eclipse.daanse.olap.api.type.StringType;
import org.eclipse.daanse.olap.common.ExpCacheDescriptorImpl;
import org.eclipse.daanse.olap.fun.MondrianEvaluationException;
import org.eclipse.daanse.olap.query.component.HierarchyExpressionImpl;
import org.eclipse.daanse.olap.query.component.ResolvedFunCallImpl;
import org.eclipse.daanse.rolap.common.aggmatcher.AggStar;
import org.eclipse.daanse.rolap.common.sql.SqlQuery;
import org.eclipse.daanse.rolap.element.RolapCalculatedMember;
import org.eclipse.daanse.rolap.element.RolapCubeDimension;
import org.eclipse.daanse.rolap.element.RolapCubeHierarchy;
import org.eclipse.daanse.rolap.element.RolapCubeLevel;
import org.eclipse.daanse.rolap.element.RolapLevel;
import org.eclipse.daanse.rolap.element.RolapStoredMeasure;

/**
 * Creates SQL from parse tree nodes. Currently it creates the SQL that
 * accesses a measure for the ORDER BY that is generated for a TopCount.
 *
 * @author av
 * @since Nov 17, 2005
  */
public class RolapNativeSql {

    private static final Pattern DECIMAL =
        Pattern.compile("[+-]?((\\d+(\\.\\d*)?)|(\\.\\d+))");

    private SqlQuery sqlQuery;
    private Dialect dialect;

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
         * Returns SQL. If exp can not be compiled into SQL,
         * returns null.
         *
         * @param exp Expression
         * @return SQL, or null if cannot be converted into SQL
         */
        StringBuilder compile(Expression exp);
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
            System.out.println(exp.getCategory());
            if (exp.getCategory() == DataType.INTEGER) {//TODO: REVIEW BITWISE
                return null;
            }
            Literal literal = (Literal) exp;
            String expr = String.valueOf(literal.getValue());
            if (!DECIMAL.matcher(expr).matches()) {
                throw new MondrianEvaluationException(
                    new StringBuilder("Expected to get decimal, but got ").append(expr).toString());
            }

            return dialect.quoteDecimalLiteral(expr);
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
                exprInner = aggColumn.generateExprString(sqlQuery);
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
                    measure.getMondrianDefExpression();
                exprInner = (defExp == null)
                    ? "*" : getExpression(defExp, sqlQuery);
            }

            StringBuilder expr = aggregator.getExpression(exprInner);
            return dialect.quoteDecimalLiteral(expr);
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
                        sourceExp = col.generateExprString(sqlQuery);
                    } else {
                        // Make sure the level table is part of the query.
                        rolapLevel.getHierarchy().addToFrom(
                            sqlQuery,
                            expression);
                        sourceExp = getExpression(expression, sqlQuery);
                    }
                } else if (aggStar != null) {
                    // Make sure the level table is part of the query.
                    rolapLevel.getHierarchy().addToFrom(sqlQuery, expression);
                    sourceExp = getExpression(expression, sqlQuery);
                } else {
                    sourceExp = getExpression(expression, sqlQuery);
                }

                // The dialect might require the use of the alias rather
                // then the column exp.
                if (dialect.requiresHavingAlias()) {
                    sourceExp = sqlQuery.getAlias(sourceExp);
                }
                return
                    dialect.generateRegularExpression(
                        sourceExp,
                        String.valueOf(
                            evaluator.getCachedResult(
                                new ExpCacheDescriptorImpl(arg1, evaluator))));
            } else {
                return null;
            }
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
            return sqlQuery.getDialect().wrapIntoSqlIfThenElseFunction(cond, val1, val2);
        }
    }

    /**
     * Creates a RolapNativeSql.
     *
     * @param sqlQuery the query which is needed for different SQL dialects -
     * it is not modified
     */
    public RolapNativeSql(
        SqlQuery sqlQuery,
        AggStar aggStar,
        Evaluator evaluator,
        RolapLevel rolapLevel)
    {
        this.sqlQuery = sqlQuery;
        this.rolapLevel = rolapLevel;
        this.evaluator = evaluator;
        this.dialect = sqlQuery.getDialect();
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
     * Generates an aggregate of a measure, e.g. "sum(Store_Sales)" for
     * TopCount. The returned expr will be added to the select list and to the
     * order by clause.
     */
    public StringBuilder generateTopCountOrderBy(Expression exp) {
        return numericCompiler.compile(exp);
    }

    public StringBuilder generateFilterCondition(Expression exp) {
    	return booleanCompiler.compile(exp);
    }

    public RolapStoredMeasure getStoredMeasure() {
        return storedMeasure;
    }

}
