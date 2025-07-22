/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation.
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
package org.eclipse.daanse.rolap.common.util;

import static org.eclipse.daanse.rolap.common.util.JoinUtil.getLeftAlias;
import static org.eclipse.daanse.rolap.common.util.JoinUtil.getRightAlias;
import static org.eclipse.daanse.rolap.common.util.JoinUtil.left;
import static org.eclipse.daanse.rolap.common.util.JoinUtil.right;

import java.util.List;

import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.rolap.mapping.api.model.AggregationExcludeMapping;
import org.eclipse.daanse.rolap.mapping.api.model.AggregationMeasureMapping;
import org.eclipse.daanse.rolap.mapping.api.model.AggregationNameMapping;
import org.eclipse.daanse.rolap.mapping.api.model.AggregationPatternMapping;
import org.eclipse.daanse.rolap.mapping.api.model.AggregationTableMapping;
import org.eclipse.daanse.rolap.mapping.api.model.ColumnMapping;
import org.eclipse.daanse.rolap.mapping.api.model.DatabaseSchemaMapping;
import org.eclipse.daanse.rolap.mapping.api.model.InlineTableMapping;
import org.eclipse.daanse.rolap.mapping.api.model.InlineTableQueryMapping;
import org.eclipse.daanse.rolap.mapping.api.model.JoinQueryMapping;
import org.eclipse.daanse.rolap.mapping.api.model.QueryMapping;
import org.eclipse.daanse.rolap.mapping.api.model.RowMapping;
import org.eclipse.daanse.rolap.mapping.api.model.RowValueMapping;
import org.eclipse.daanse.rolap.mapping.api.model.SqlStatementMapping;
import org.eclipse.daanse.rolap.mapping.api.model.TableMapping;
import org.eclipse.daanse.rolap.mapping.api.model.TableQueryMapping;
import org.eclipse.daanse.rolap.mapping.api.model.TableQueryOptimizationHintMapping;
import org.eclipse.daanse.rolap.mapping.api.model.enums.ColumnDataType;
import org.eclipse.daanse.rolap.mapping.pojo.AggregationExcludeMappingImpl;
import org.eclipse.daanse.rolap.mapping.pojo.AggregationMeasureMappingImpl;
import org.eclipse.daanse.rolap.mapping.pojo.AggregationNameMappingImpl;
import org.eclipse.daanse.rolap.mapping.pojo.AggregationPatternMappingImpl;
import org.eclipse.daanse.rolap.mapping.pojo.AggregationTableMappingImpl;
import org.eclipse.daanse.rolap.mapping.pojo.PhysicalColumnMappingImpl;
import org.eclipse.daanse.rolap.mapping.pojo.DatabaseSchemaMappingImpl;
import org.eclipse.daanse.rolap.mapping.pojo.InlineTableMappingImpl;
import org.eclipse.daanse.rolap.mapping.pojo.InlineTableQueryMappingImpl;
import org.eclipse.daanse.rolap.mapping.pojo.JoinQueryMappingImpl;
import org.eclipse.daanse.rolap.mapping.pojo.JoinedQueryElementMappingImpl;
import org.eclipse.daanse.rolap.mapping.pojo.PhysicalTableMappingImpl;
import org.eclipse.daanse.rolap.mapping.pojo.QueryMappingImpl;
import org.eclipse.daanse.rolap.mapping.pojo.RowMappingImpl;
import org.eclipse.daanse.rolap.mapping.pojo.RowValueMappingImpl;
import org.eclipse.daanse.rolap.mapping.pojo.SqlStatementMappingImpl;
import org.eclipse.daanse.rolap.mapping.pojo.TableQueryMappingImpl;
import org.eclipse.daanse.rolap.mapping.pojo.TableQueryOptimizationHintMappingImpl;

public class PojoUtil {
	public static final String BAD_RELATION_TYPE = "bad relation type ";
	private PojoUtil() {
        // constructor
    }

//	public static SqlViewMapping getSql(SqlViewMapping s) {
//		if (s != null) {
//			return ((SqlViewMappingImpl.Builder) SqlViewMappingImpl.builder()
//					.withsSchema(getDatabaseSchema(s.getSchema()))
//					.withColumns(getColumns(s.getColumns()))
//					.withName(s.getName()))
//					.withSqlStatements(getSqlStatements(s.getSqlStatements()))
//					.build();
//		}
//		return null;
//	}

    private static SqlStatementMappingImpl getSql(SqlStatementMapping s) {
        if (s != null) {
            return SqlStatementMappingImpl.builder()
                .withDialects(s.getDialects())
                .withSql(s.getSql())
                .build();
        }
        return null;
    }

//    private static List<SqlStatementMappingImpl> getSqlStatements(List<? extends SqlStatementMapping> sqlStatements) {
//   		if (sqlStatements != null) {
//   			sqlStatements.stream().map(s -> getSqlStatement(s)).toList();
//   		}
//   		return List.of();
//	}

//    public static SqlStatementMappingImpl getSqlStatement(SqlStatementMapping s) {
//    	List<String> dialects = getDialects(s.getDialects());
//    	return SqlStatementMappingImpl.builder().withDialects(dialects).withSql(s.getSql()).build();
//    }

//	private static List<String> getDialects(List<String> dialects) {
//		if (dialects != null) {
//			dialects.stream().map(String::new).toList();
//		}
//		return List.of();
//	}

	/**
     * Copies a {@link QueryMapping}.
     *
     * @param relation A table or a join
     */
    public static QueryMappingImpl copy(
        QueryMapping relation)
    {
        if (relation instanceof TableQueryMapping table) {
        	SqlStatementMappingImpl sqlMappingImpl = getSql(table.getSqlWhereExpression());
        	List<AggregationExcludeMappingImpl> aggregationExcludes = getAggregationExcludes(table.getAggregationExcludes());
            List<TableQueryOptimizationHintMappingImpl> optimizationHints = getOptimizationHints(table.getOptimizationHints());
            List<AggregationTableMappingImpl> aggregationTables = getAggregationTables(table.getAggregationTables());

            return TableQueryMappingImpl.builder()
            		.withAlias(table.getAlias())
            		.withTable(getPhysicalTable(table.getTable()))
            		.withSqlWhereExpression(sqlMappingImpl)
            		.withAggregationExcludes(aggregationExcludes)
            		.withOptimizationHints(optimizationHints)
            		.withAggregationTables(aggregationTables)
            		.build();

        } else if (relation instanceof InlineTableQueryMapping table) {
            return InlineTableQueryMappingImpl.builder()
            		.withAlias(table.getAlias())
            		.withTable(getInlineTable(table.getTable()))
            		.build();

        } else if (relation instanceof JoinQueryMapping join) {
            QueryMappingImpl left = copy(left(join));
            QueryMappingImpl right = copy(right(join));
            return JoinQueryMappingImpl.builder()
            		.withLeft(JoinedQueryElementMappingImpl.builder().withAlias(getLeftAlias(join)).withKey((PhysicalColumnMappingImpl) getColumn(join.getLeft().getKey())).withQuery(left).build())
            		.withRight(JoinedQueryElementMappingImpl.builder().withAlias(getRightAlias(join)).withKey((PhysicalColumnMappingImpl) getColumn(join.getRight().getKey())).withQuery(right).build())
            		.build();
        } else {
            throw Util.newInternal(BAD_RELATION_TYPE + relation);
        }
    }

	public static InlineTableMappingImpl getInlineTable(InlineTableMapping table) {
    	List<RowMapping> rows = getRows(table.getRows());
    	List<ColumnMapping> columns = getColumns(table.getColumns());
    	DatabaseSchemaMappingImpl schema = getDatabaseSchema(table.getSchema());
    	InlineTableMappingImpl inlineTable = InlineTableMappingImpl.builder().build();
        inlineTable.setName(table.getName());
        inlineTable.setColumns(columns);
        inlineTable.setSchema(schema);
        inlineTable.setDescription(table.getDescription());
        inlineTable.setRows(rows);
    	return inlineTable;
	}

	private static List<AggregationTableMappingImpl> getAggregationTables(
			List<? extends AggregationTableMapping> aggregationTables) {
		if (aggregationTables != null) {
			return aggregationTables.stream().map(c -> getAggregationTable(c)).toList();
		}
		return List.of();
	}

	private static AggregationTableMappingImpl getAggregationTable(AggregationTableMapping a) {
		if (a instanceof AggregationNameMapping anm) {
			return AggregationNameMappingImpl.builder()
					.withApproxRowCount(anm.getApproxRowCount())
					.withName(anm.getName())
					.build();
		}
		if (a instanceof AggregationPatternMapping apm) {
			return AggregationPatternMappingImpl.builder()
					.withPattern(apm.getPattern())
					.withAggregationMeasures(getAggregationMeasures(apm.getAggregationMeasures()))
					.build();
		}
		return null;
	}

	private static List<AggregationMeasureMappingImpl> getAggregationMeasures(
			List<? extends AggregationMeasureMapping> aggregationMeasures) {
		if (aggregationMeasures != null) {
			return aggregationMeasures.stream().map(c -> AggregationMeasureMappingImpl.builder()
					.withColumn((PhysicalColumnMappingImpl)c.getColumn())
					.withName(c.getName())
					.withRollupType(c.getRollupType())
					.build()).toList();
		}
		return List.of();
	}

	public static List<TableQueryOptimizationHintMappingImpl> getOptimizationHints(
			List<? extends TableQueryOptimizationHintMapping> optimizationHints) {
		if (optimizationHints != null) {
			return optimizationHints.stream().map(c -> TableQueryOptimizationHintMappingImpl.builder()
					.withValue(c.getValue())
					.withType(c.getType())
					.build()).toList();
		}
		return List.of();
	}


	private static List<AggregationExcludeMappingImpl> getAggregationExcludes(
			List<? extends AggregationExcludeMapping> aggregationExcludes) {
    	if (aggregationExcludes != null) {
    		return aggregationExcludes.stream().map(a ->
    		AggregationExcludeMappingImpl.builder()
    		.withIgnorecase(a.isIgnorecase())
    		.withName(a.getName())
    		.withPattern(a.getPattern())
    		.withId(a.getId())
    		.build()).toList();
    	}
    	return List.of();
	}

	public static List<RowMapping> getRows(List<? extends RowMapping> rows) {
    	if (rows != null) {
    		return rows.stream().map(r -> ((RowMapping)(RowMappingImpl.builder().withRowValues(getRowValues(r.getRowValues())).build()))).toList();
    	}
    	return List.of();
	}

	private static List<? extends RowValueMapping> getRowValues(List<? extends RowValueMapping> list) {
		if (list != null) {
			return list.stream().map(c -> RowValueMappingImpl.builder().withValue(c.getValue()).withColumn((PhysicalColumnMappingImpl)getColumn(c.getColumn())).build()).toList();
		}
		return List.of();	}

	private static DatabaseSchemaMappingImpl getDatabaseSchema(DatabaseSchemaMapping schema) {
        if (schema != null) {
            return DatabaseSchemaMappingImpl.builder().withName(schema.getName()).build();
        }
        return null;
	}

	private static List<ColumnMapping> getColumns(List<? extends ColumnMapping> columns) {
		if (columns != null) {
            return columns.stream().map(c -> getColumn(c)).toList();
        }
        return List.of();
	}

	// TODO: migrate to util.Converter
	public static ColumnMapping getColumn(ColumnMapping column) {
        if (column != null) {
            String name = column.getName();
            ColumnDataType type = column.getDataType();
            Integer columnSize = column.getColumnSize();
            Integer decimalDigits = column.getDecimalDigits();
            Integer numPrecRadix = column.getNumPrecRadix();
            Integer charOctetLength = column.getCharOctetLength();
            Boolean nullable = column.getNullable();
            String description = column.getDescription();
            PhysicalColumnMappingImpl c = PhysicalColumnMappingImpl.builder().withName(name).withDataType(type)
                    .withColumnSize(orZero(columnSize))
                    .withDecimalDigits(orZero(decimalDigits))
                    .withNumPrecRadix(orZero(numPrecRadix))
                    .withCharOctetLength(orZero(charOctetLength ))
                    .withNullable(nullable == null ? false : nullable)
                    .build();
            c.setDescription(description);
            return c;
        }
        return null;
	}
	private static int orZero(Integer val) {
        return val == null ? 0 : val;
    }

    // TODO: migrate to util.Converter
	public static PhysicalTableMappingImpl getPhysicalTable(TableMapping table) {
		if (table != null) {
            String name = table.getName();
            List<ColumnMapping> columns = getColumns(table.getColumns());
            DatabaseSchemaMappingImpl schema = getDatabaseSchema(table.getSchema());
            String description = table.getDescription();
            PhysicalTableMappingImpl t = ((PhysicalTableMappingImpl.Builder) PhysicalTableMappingImpl.builder()
                    .withName(name).withColumns(columns).withsSchema(schema).withsDdescription(description)).build();
            if (t.getColumns() != null) {
                t.getColumns().forEach(c -> ((PhysicalColumnMappingImpl)c).setTable(table));
            }
            return t;
        }
        return null;
	}
}
