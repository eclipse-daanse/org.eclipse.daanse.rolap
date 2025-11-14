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
import org.eclipse.daanse.rolap.mapping.model.Query;
import org.eclipse.daanse.rolap.mapping.model.RolapMappingFactory;

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

    private static org.eclipse.daanse.rolap.mapping.model.SqlStatement getSql(org.eclipse.daanse.rolap.mapping.model.SqlStatement s) {
        if (s != null) {
            //return SqlStatementMappingImpl.builder()
            //    .withDialects(s.getDialects())
            //    .withSql(s.getSql())
            //    .build();
        	return s;
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
     * Copies a {@link Query}.
     *
     * @param relation A table or a join
     */
    public static org.eclipse.daanse.rolap.mapping.model.Query copy(
         org.eclipse.daanse.rolap.mapping.model.Query relation)
    {
        return switch (relation) {
        case org.eclipse.daanse.rolap.mapping.model.TableQuery table -> {
        	org.eclipse.daanse.rolap.mapping.model.SqlStatement sqlMappingImpl = getSql(table.getSqlWhereExpression());
            List<? extends org.eclipse.daanse.rolap.mapping.model.AggregationExclude> aggregationExcludes = getAggregationExcludes(table.getAggregationExcludes());
            List<? extends org.eclipse.daanse.rolap.mapping.model.TableQueryOptimizationHint> optimizationHints = getOptimizationHints(table.getOptimizationHints());
            List<? extends org.eclipse.daanse.rolap.mapping.model.AggregationTable> aggregationTables = getAggregationTables(table.getAggregationTables());
            org.eclipse.daanse.rolap.mapping.model.TableQuery q = RolapMappingFactory.eINSTANCE.createTableQuery();
            q.setAlias(table.getAlias());
            q.setTable(getPhysicalTable(table.getTable()));
            q.setSqlWhereExpression(sqlMappingImpl);
            q.getAggregationExcludes().addAll(aggregationExcludes);
            q.getOptimizationHints().addAll(optimizationHints);
            q.getAggregationTables().addAll(aggregationTables);
            yield q;
        }
        case org.eclipse.daanse.rolap.mapping.model.InlineTableQuery table -> {
            org.eclipse.daanse.rolap.mapping.model.InlineTableQuery inlineTableQuery = RolapMappingFactory.eINSTANCE.createInlineTableQuery();
            inlineTableQuery.setAlias(table.getAlias());
            inlineTableQuery.setTable(getInlineTable(table.getTable()));
            yield inlineTableQuery;
        }
        case org.eclipse.daanse.rolap.mapping.model.JoinQuery join -> {
            org.eclipse.daanse.rolap.mapping.model.Query left = copy(left(join));
            org.eclipse.daanse.rolap.mapping.model.Query right = copy(right(join));
            org.eclipse.daanse.rolap.mapping.model.JoinQuery joinQuery = RolapMappingFactory.eINSTANCE.createJoinQuery();
            
            org.eclipse.daanse.rolap.mapping.model.JoinedQueryElement leftElement = RolapMappingFactory.eINSTANCE.createJoinedQueryElement();
            leftElement.setAlias(getLeftAlias(join));
            leftElement.setKey(getColumn(join.getLeft().getKey()));
            leftElement.setQuery(left);
            
            org.eclipse.daanse.rolap.mapping.model.JoinedQueryElement rightElement = RolapMappingFactory.eINSTANCE.createJoinedQueryElement();
            rightElement.setAlias(getRightAlias(join));
            rightElement.setKey(getColumn(join.getRight().getKey()));
            rightElement.setQuery(right);
            
            joinQuery.setLeft(leftElement);
            joinQuery.setRight(rightElement);
            yield joinQuery;
        }
        case null, default -> throw Util.newInternal(BAD_RELATION_TYPE + relation);
        };
    }

	public static org.eclipse.daanse.rolap.mapping.model.InlineTable getInlineTable(org.eclipse.daanse.rolap.mapping.model.InlineTable table) {
    	//List<? extends org.eclipse.daanse.rolap.mapping.model.Row> rows = getRows(table.getRows());
    	//List<? extends org.eclipse.daanse.rolap.mapping.model.Column> columns = getColumns(table.getColumns());
    	//org.eclipse.daanse.rolap.mapping.model.DatabaseSchema schema = getDatabaseSchema(table.getSchema());
    	//org.eclipse.daanse.rolap.mapping.model.InlineTable inlineTable = InlineTableMappingImpl.builder().build();
        //inlineTable.setName(table.getName());
        //inlineTable.setColumns(columns);
        //inlineTable.setSchema(schema);
        //inlineTable.setDescription(table.getDescription());
        //inlineTable.setRows(rows);
    	//return inlineTable;
		return table; //TODO temove method
	}

	private static List<? extends org.eclipse.daanse.rolap.mapping.model.AggregationTable> getAggregationTables(
			List<? extends org.eclipse.daanse.rolap.mapping.model.AggregationTable> aggregationTables) {
		if (aggregationTables != null) {
			//return aggregationTables.stream().map(c -> getAggregationTable(c)).toList();
			return aggregationTables; //TODO remove
		}
		return List.of();
	}

	private static org.eclipse.daanse.rolap.mapping.model.AggregationTable getAggregationTable(org.eclipse.daanse.rolap.mapping.model.AggregationTable a) {
		if (a instanceof org.eclipse.daanse.rolap.mapping.model.AggregationName anm) {
			org.eclipse.daanse.rolap.mapping.model.AggregationName aggregationName = RolapMappingFactory.eINSTANCE.createAggregationName();
			aggregationName.setApproxRowCount(anm.getApproxRowCount());
			return aggregationName;
		}
		if (a instanceof org.eclipse.daanse.rolap.mapping.model.AggregationPattern apm) {
			org.eclipse.daanse.rolap.mapping.model.AggregationPattern aggregationPattern = RolapMappingFactory.eINSTANCE.createAggregationPattern();
			aggregationPattern.setPattern(apm.getPattern());
			aggregationPattern.getAggregationMeasures().addAll(getAggregationMeasures(apm.getAggregationMeasures()));
			return aggregationPattern;
		}
		return null;
	}

	private static List<org.eclipse.daanse.rolap.mapping.model.AggregationMeasure> getAggregationMeasures(
			List<? extends org.eclipse.daanse.rolap.mapping.model.AggregationMeasure> aggregationMeasures) {
		if (aggregationMeasures != null) {
			return aggregationMeasures.stream().map(c -> {
				org.eclipse.daanse.rolap.mapping.model.AggregationMeasure aggregationMeasure = RolapMappingFactory.eINSTANCE.createAggregationMeasure();
				aggregationMeasure.setColumn(c.getColumn());
				aggregationMeasure.setName(c.getName());
				aggregationMeasure.setRollupType(c.getRollupType());
				return aggregationMeasure;
			}).toList();
		}
		return List.of();
	}

	public static List<? extends org.eclipse.daanse.rolap.mapping.model.TableQueryOptimizationHint> getOptimizationHints(
			List<? extends org.eclipse.daanse.rolap.mapping.model.TableQueryOptimizationHint> optimizationHints) {
		if (optimizationHints != null) {
			//return optimizationHints.stream().map(c -> TableQueryOptimizationHintMappingImpl.builder()
			//		.withValue(c.getValue())
			//		.withType(c.getType())
			//		.build()).toList();
			return optimizationHints;
		}
		return List.of();
	}


	private static List<? extends org.eclipse.daanse.rolap.mapping.model.AggregationExclude> getAggregationExcludes(
			List<? extends org.eclipse.daanse.rolap.mapping.model.AggregationExclude> aggregationExcludes) {
    	if (aggregationExcludes != null) {
    		//return aggregationExcludes.stream().map(a ->
    		//AggregationExcludeMappingImpl.builder()
    		//.withIgnorecase(a.isIgnorecase())
    		//.withName(a.getName())
    		//.withPattern(a.getPattern())
    		//.withId(a.getId())
    		//.build()).toList();
    		return aggregationExcludes; //TODO remove 
    	}
    	return List.of();
	}

	public static List<? extends org.eclipse.daanse.rolap.mapping.model.Row> getRows(List<? extends org.eclipse.daanse.rolap.mapping.model.Row> rows) {
    	if (rows != null) {
    		//return rows.stream().map(r -> ((RowMapping)(RowMappingImpl.builder().withRowValues(getRowValues(r.getRowValues())).build()))).toList();
    		return rows; 
    	}
    	return List.of();
	}

	private static List<? extends org.eclipse.daanse.rolap.mapping.model.RowValue> getRowValues(List<? extends org.eclipse.daanse.rolap.mapping.model.RowValue> list) {
		if (list != null) {
			//return list.stream().map(c -> RowValueMappingImpl.builder().withValue(c.getValue()).withColumn((PhysicalColumnMappingImpl)getColumn(c.getColumn())).build()).toList();
			return list; //TODO remove
		}
		return List.of();	}

	private static org.eclipse.daanse.rolap.mapping.model.DatabaseSchema getDatabaseSchema(org.eclipse.daanse.rolap.mapping.model.DatabaseSchema schema) {
        if (schema != null) {
            return schema; //TODO remove
        }
        return null;
	}

	private static List<org.eclipse.daanse.rolap.mapping.model.Column> getColumns(List<? extends org.eclipse.daanse.rolap.mapping.model.Column> columns) {
		if (columns != null) {
            return columns.stream().map(c -> getColumn(c)).toList();
        }
        return List.of();
	}

	// TODO: migrate to util.Converter
	public static org.eclipse.daanse.rolap.mapping.model.Column getColumn(org.eclipse.daanse.rolap.mapping.model.Column column) {
        if (column != null) {
            /*
            String name = column.getName();
            ColumnType type = column.getType();
            Integer columnSize = column.getColumnSize();
            Integer decimalDigits = column.getDecimalDigits();
            Integer numPrecRadix = column.getNumPrecRadix();
            Integer charOctetLength = column.getCharOctetLength();
            Boolean nullable = column.getNullable();
            String description = column.getDescription();
            org.eclipse.daanse.rolap.mapping.model.PhysicalColumn c = PhysicalColumnMappingImpl.builder().withName(name).withDataType(type)
                    .withColumnSize(orZero(columnSize))
                    .withDecimalDigits(orZero(decimalDigits))
                    .withNumPrecRadix(orZero(numPrecRadix))
                    .withCharOctetLength(orZero(charOctetLength ))
                    .withNullable(nullable == null ? false : nullable)
                    .build();
            c.setDescription(description);
            return c; */
            return column;
        }
        return null;
	}
	private static int orZero(Integer val) {
        return val == null ? 0 : val;
    }

    // TODO: migrate to util.Converter
	public static org.eclipse.daanse.rolap.mapping.model.Table getPhysicalTable(org.eclipse.daanse.rolap.mapping.model.Table table) {
		if (table != null) {
            //String name = table.getName();
            //List<org.eclipse.daanse.rolap.mapping.model.Column> columns = getColumns(table.getColumns());
            //org.eclipse.daanse.rolap.mapping.model.DatabaseSchema schema = getDatabaseSchema(table.getSchema());
            //String description = table.getDescription();
            //org.eclipse.daanse.rolap.mapping.model.PhysicalTable t = ((PhysicalTableMappingImpl.Builder) PhysicalTableMappingImpl.builder()
            //        .withName(name).withColumns(columns).withsSchema(schema).withsDdescription(description)).build();
            //if (t.getColumns() != null) {
            //    t.getColumns().forEach(c -> ((org.eclipse.daanse.rolap.mapping.model.PhysicalColumn)c).setTable(table));
            //}
            //return t;
			return table;
        }
        return null;
	}
}
