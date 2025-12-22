/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2005-2005 Julian Hyde
 * Copyright (C) 2005-2019 Hitachi Vantara and others
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
package org.eclipse.daanse.rolap.common.aggmatcher;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.JDBCType;
import java.sql.SQLException;
import java.sql.Types;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import org.eclipse.daanse.jdbc.db.dialect.api.type.Datatype;
import org.eclipse.daanse.olap.api.SqlExpression;
import org.eclipse.daanse.olap.api.aggregator.Aggregator;
import org.eclipse.daanse.olap.api.exception.OlapRuntimeException;
import org.eclipse.daanse.rolap.common.RolapSqlExpression;
import org.eclipse.daanse.rolap.common.RolapStar;
import org.eclipse.daanse.rolap.element.RolapLevel;
import org.eclipse.daanse.rolap.mapping.model.ColumnType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Metadata gleaned from JDBC about the tables and columns in the star schema.
 * This class is used to scrape a database and store information about its
 * tables and columnIter.
 *
 * The structure of this information is as follows: A database has tables. A
 * table has columnIter. A column has one or more usages.  A usage might be a
 * column being used as a foreign key or as part of a measure.
 *
 *  Tables are created when calling code requests the set of available
 * tables. This call getTables() causes all tables to be loaded.
 * But a table's columnIter are not loaded until, on a table-by-table basis,
 * a request is made to get the set of columnIter associated with the table.
 * Since, the AggTableManager first attempts table name matches (recognition)
 * most tables do not match, so why load their columnIter.
 * Of course, as a result, there are a host of methods that can throw an
 * {@link SQLException}, rats.
 *
 * @author Richard M. Emberson
 */
public class JdbcSchema {
    private static final Logger LOGGER =
        LoggerFactory.getLogger(JdbcSchema.class);

    /**
     * Returns the Logger.
     */
    public Logger getLogger() {
        return LOGGER;
    }

    //
    // Types of column usages.
    //
    public static final int UNKNOWN_COLUMN_USAGE         = 0x0001;
    public static final int FOREIGN_KEY_COLUMN_USAGE     = 0x0002;
    public static final int MEASURE_COLUMN_USAGE         = 0x0004;
    public static final int LEVEL_COLUMN_USAGE           = 0x0008;
    public static final int FACT_COUNT_COLUMN_USAGE      = 0x0010;
    public static final int IGNORE_COLUMN_USAGE          = 0x0020;

    public static final String UNKNOWN_COLUMN_NAME         = "UNKNOWN";
    public static final String FOREIGN_KEY_COLUMN_NAME     = "FOREIGN_KEY";
    public static final String MEASURE_COLUMN_NAME         = "MEASURE";
    public static final String LEVEL_COLUMN_NAME           = "LEVEL";
    public static final String FACT_COUNT_COLUMN_NAME      = "FACT_COUNT";
    public static final String IGNORE_COLUMN_NAME          = "IGNORE";

    /**
     * Enumeration of ways that an aggregate table can use a column.
     */
    public enum UsageType {
        UNKNOWN,
        FOREIGN_KEY,
        MEASURE,
        LEVEL,
        LEVEL_EXTRA,
        FACT_COUNT,
        MEASURE_FACT_COUNT,
        IGNORE
    }

    /**
     * Determine if the parameter represents a single column type, i.e., the
     * column only has one usage.
     *
     * @param columnType Column types
     * @return true if column has only one usage.
     */
    public static boolean isUniqueColumnType(Set<UsageType> columnType) {
        return columnType.size() == 1;
    }

    /**
     * Maps from column type enum to column type name or list of names if the
     * parameter represents more than on usage.
     */
    public static String convertColumnTypeToName(Set<UsageType> columnType) {
        if (columnType.size() == 1) {
            return columnType.iterator().next().name();
        }
        // it's a multi-purpose column
        StringBuilder buf = new StringBuilder();
        int k = 0;
        for (UsageType usage : columnType) {
            if (k++ > 0) {
                buf.append('|');
            }
            buf.append(usage.name());
        }
        return buf.toString();
    }

    /**
     * Converts a {@link java.sql.Types} value to a
     * {@link Datatype}.
     *
     * @param javaType JDBC type code, as per {@link java.sql.Types}
     * @return Datatype
     */
    public static Datatype getDatatype(int javaType) {
        switch (javaType) {
        case Types.TINYINT, Types.SMALLINT, Types.INTEGER:
            return Datatype.INTEGER;
        case Types.FLOAT, Types.REAL, Types.DOUBLE, Types.NUMERIC, Types.DECIMAL, Types.BIGINT:
            return Datatype.NUMERIC;
        case Types.BOOLEAN:
            return Datatype.BOOLEAN;
        case Types.DATE:
            return Datatype.DATE;
        case Types.TIME:
            return Datatype.TIME;
        case Types.TIMESTAMP:
            return Datatype.TIMESTAMP;
        case Types.CHAR, Types.VARCHAR:
        default:
            return Datatype.VARCHAR;
        }
    }

    /**
     * Returns true if the parameter is a java.sql.Type text type.
     */
    public static boolean isText(int javaType) {
        return javaType == Types.CHAR || javaType == Types.VARCHAR || javaType == Types.LONGVARCHAR;
    }

    enum TableUsageType {
        UNKNOWN,
        FACT,
        AGG
    }

    /**
     * A table in a database.
     */
    public class Table {

        private final static String attemptToChangeTableUsage =
            "JdbcSchema.Table ''{0}'' already set to usage ''{1}'' and can not be reset to usage ''{2}''.";

        /**
         * A column in a table.
         */
        public class Column {

            /**
             * A usage of a column.
             */
            public class Usage {
                private final UsageType usageType;
                private String symbolicName;
                private Aggregator aggregator;

                ////////////////////////////////////////////////////
                //
                // These instance variables are used to hold
                // stuff which is determines at one place and
                // then used somewhere else. Generally, a usage
                // is created before all of its "stuff" can be
                // determined, hence, usage is not a set of classes,
                // rather its one class with a bunch of instance
                // variables which may or may not be used.
                //

                // measure stuff
                public RolapStar.Measure rMeasure;

                // hierarchy stuff
                public org.eclipse.daanse.rolap.mapping.model.RelationalQuery relation;
                public SqlExpression joinExp;
                public String levelColumnName;

                // level stuff
                public RolapStar.Column rColumn;
                public Column ordinalColumn;
                public Column captionColumn;
                public Map<String, Column> properties;

                // agg stuff
                public boolean collapsed = false;
                public RolapLevel level = null;

                // for subtables
                public RolapStar.Table rTable;
                public String rightJoinConditionColumnName;

                /**
                 * The prefix (possibly null) to use during aggregate table
                 * generation (See AggGen).
                 */
                public String usagePrefix;


                /**
                 * Creates a Usage.
                 *
                 * @param usageType Usage type
                 */
                Usage(UsageType usageType) {
                    this.usageType = usageType;
                }

                /**
                 * Returns the column with which this usage is associated.
                 *
                 * @return the usage's column.
                 */
                public Column getColumn() {
                    return JdbcSchema.Table.Column.this;
                }

                /**
                 * Returns the column usage type.
                 */
                public UsageType getUsageType() {
                    return usageType;
                }

                /**
                 * Sets the symbolic (logical) name associated with this usage.
                 * For example, this might be the measure's name.
                 *
                 * @param symbolicName Symbolic name
                 */
                public void setSymbolicName(final String symbolicName) {
                    this.symbolicName = symbolicName;
                }

                /**
                 * Returns the usage's symbolic name.
                 */
                public String getSymbolicName() {
                    return symbolicName;
                }

                public RolapSqlExpression getOrdinalExp() {
                    RolapSqlExpression ordinalExp = null;
                    if (ordinalColumn != null) {
                        ordinalExp =
                            new org.eclipse.daanse.rolap.element.RolapColumn(
                                getTable().getName(), ordinalColumn.getName());
                    }
                    return ordinalExp;
                }

                public RolapSqlExpression getCaptionExp() {
                    RolapSqlExpression captionExp = null;
                    if (captionColumn != null) {
                        captionExp =
                            new org.eclipse.daanse.rolap.element.RolapColumn(
                                getTable().getName(), captionColumn.getName());
                    }
                    return captionExp;
                }

                public Map<String, RolapSqlExpression> getProperties() {
                    Map<String, RolapSqlExpression> map =
                        new HashMap<>();
                    if (properties == null) {
                        return map;
                    }
                    for (Map.Entry<String, Column> entry
                        : properties.entrySet())
                    {
                        map.put(
                            entry.getKey(),
                            new org.eclipse.daanse.rolap.element.RolapColumn(
                                getTable().getName(),
                                entry.getValue().getName()));
                    }
                    return Collections.unmodifiableMap(map);
                }

                /**
                 * Sets the aggregator associated with this usage (if it is a
                 * measure usage).
                 *
                 * @param aggregator Aggregator
                 */
                public void setAggregator(final Aggregator aggregator) {
                    this.aggregator = aggregator;
                }

                /**
                 * Returns the aggregator associated with this usage (if its a
                 * measure usage, otherwise null).
                 */
                public Aggregator getAggregator() {
                    return aggregator;
                }

                @Override
				public String toString() {
                    StringWriter sw = new StringWriter(64);
                    PrintWriter pw = new PrintWriter(sw);
                    print(pw);
                    pw.flush();
                    return sw.toString();
                }

                private void print(final PrintWriter pw) {
                    if (getSymbolicName() != null) {
                        pw.print("symbolicName=");
                        pw.print(getSymbolicName());
                    }
                    if (getAggregator() != null) {
                        pw.print(", aggregator=");
                        pw.print(getAggregator().getName());
                    }
                    pw.print(", columnType=");
                    pw.print(getUsageType().name());
                }
            }

            /** This is the name of the column. */
            private final String name;

            /** This is the java.sql.Type enum of the column in the database. */
            private int type;
            /**
             * This is the java.sql.Type name of the column in the database.
             */
            private String typeName;

            /** This is the size of the column in the database. */
            private int columnSize;

            /** The number of fractional digits. */
            private int decimalDigits;

            /** Radix (typically either 10 or 2). */
            private int numPrecRadix;

            /** For char types the maximum number of bytes in the column. */
            private int charOctetLength;

            /**
             * False means the column definitely does not allow NULL values.
             */
            private boolean isNullable;

            public final org.eclipse.daanse.rolap.element.RolapColumn column;

            private final List<JdbcSchema.Table.Column.Usage> usages;

            /**
             * This contains the enums of all of the column's usages.
             */
            private final Set<UsageType> usageTypes =
            		EnumSet.noneOf(UsageType.class);

            public Column(final String name) {
                this.name = name;
                this.column =
                    new org.eclipse.daanse.rolap.element.RolapColumn(
                        JdbcSchema.Table.this.getName(),
                        name);
                this.usages = new ArrayList<>();
            }

            /**
             * Returns the column's name in the database, not a symbolic name.
             */
            public String getName() {
                return name;
            }

            /**
             * Sets the columnIter java.sql.Type enun of the column.
             *
             * @param type Type
             */
            private void setType(final int type) {
                this.type = type;
            }

            /**
             * Returns the columnIter java.sql.Type enun of the column.
             */
            public int getType() {
                return type;
            }

            /**
             * Sets the columnIter java.sql.Type name.
             *
             * @param typeName Type name
             */
            private void setTypeName(final String typeName) {
                this.typeName = typeName;
            }

            /**
             * Returns the columnIter java.sql.Type name.
             */
            public String getTypeName() {
                return typeName;
            }

            /**
             * Returns this column's table.
             */
            public Table getTable() {
                return JdbcSchema.Table.this;
            }

            /**
             * Return true if this column is numeric.
             */
            public Datatype getDatatype() {
                return JdbcSchema.getDatatype(getType());
            }

            /**
             * Sets the size in bytes of the column in the database.
             *
             * @param columnSize Column size
             */
            private void setColumnSize(final int columnSize) {
                this.columnSize = columnSize;
            }

            /**
             * Returns the size in bytes of the column in the database.
             *
             */
            public int getColumnSize() {
                return columnSize;
            }

            /**
             * Sets number of fractional digits.
             *
             * @param decimalDigits Number of fractional digits
             */
            private void setDecimalDigits(final int decimalDigits) {
                this.decimalDigits = decimalDigits;
            }

            /**
             * Returns number of fractional digits.
             */
            public int getDecimalDigits() {
                return decimalDigits;
            }

            /**
             * Sets Radix (typically either 10 or 2).
             *
             * @param numPrecRadix Radix
             */
            private void setNumPrecRadix(final int numPrecRadix) {
                this.numPrecRadix = numPrecRadix;
            }

            /**
             * Returns Radix (typically either 10 or 2).
             */
            public int getNumPrecRadix() {
                return numPrecRadix;
            }

            /**
             * For char types the maximum number of bytes in the column.
             *
             * @param charOctetLength Octet length
             */
            private void setCharOctetLength(final int charOctetLength) {
                this.charOctetLength = charOctetLength;
            }

            /**
             * For char types the maximum number of bytes in the column.
             */
            public int getCharOctetLength() {
                return charOctetLength;
            }

            /**
             * False means the column definitely does not allow NULL values.
             *
             * @param isNullable Whether column is nullable
             */
            private void setIsNullable(final boolean isNullable) {
                this.isNullable = isNullable;
            }

            /**
             * False means the column definitely does not allow NULL values.
             */
            public boolean isNullable() {
                return isNullable;
            }

            /**
             * How many usages does this column have. A column has
             * between 0 and N usages. It has no usages if usages is some
             * administrative column. It has one usage if, for example, its
             * the fact_count column or a level column (for a collapsed
             * dimension aggregate). It might have 2 usages if its a foreign key
             * that is also used as a measure. If its a column used in N
             * measures, then usages will have N usages.
             */
            public int numberOfUsages() {
                return usages.size();
            }

            /**
             * flushes all star usage references
             */
            public void flushUsages() {
                usages.clear();
                usageTypes.clear();
            }

            /**
             * Return true if the column has at least one usage.
             */
            public boolean hasUsage() {
                return !usages.isEmpty();
            }

            /**
             * Return true if the column has at least one usage of the given
             * column type.
             */
            public boolean hasUsage(UsageType columnType) {
                return usageTypes.contains(columnType);
            }

            /**
             * Returns an iterator over all usages.
             */
            public List<Usage> getUsages() {
                return usages;
            }

            /**
             * Returns an iterator over all usages of the given column type.
             */
            public Iterator<Usage> getUsages(UsageType usageType) {
                // Yes, this is legal.
                class ColumnTypeIterator implements Iterator<Usage> {
                    private final Iterator<Usage> usageIter;
                    private final UsageType usageType;
                    private Usage nextUsage;

                    ColumnTypeIterator(
                        final List<Usage> usages,
                        final UsageType columnType)
                    {
                        this.usageIter = usages.iterator();
                        this.usageType = columnType;
                    }

                    @Override
					public boolean hasNext() {
                        while (usageIter.hasNext()) {
                            Usage usage = usageIter.next();
                            if (usage.getUsageType() == this.usageType) {
                                nextUsage = usage;
                                return true;
                            }
                        }
                        nextUsage = null;
                        return false;
                    }

                    @Override
                    @SuppressWarnings("java:S2272")
					public Usage next() {
                        return nextUsage;
                    }

                    @Override
					public void remove() {
                        usageIter.remove();
                    }
                }

                return new ColumnTypeIterator(getUsages(), usageType);
            }

            /**
             * Create a new usage of a given column type.
             */
            public Usage newUsage(UsageType usageType) {
                this.usageTypes.add(usageType);

                Usage usage = new Usage(usageType);
                usages.add(usage);
                return usage;
            }

            @Override
			public String toString() {
                StringWriter sw = new StringWriter(256);
                PrintWriter pw = new PrintWriter(sw);
                print(pw, "");
                pw.flush();
                return sw.toString();
            }

            public void print(final PrintWriter pw, final String prefix) {
                pw.print(prefix);
                pw.print("name=");
                pw.print(getName());
                pw.print(", typename=");
                pw.print(getTypeName());
                pw.print(", size=");
                pw.print(getColumnSize());

                switch (getType()) {
                case Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT, Types.FLOAT, Types.REAL, Types.DOUBLE:
                    break;
                case Types.NUMERIC, Types.DECIMAL:
                    pw.print(", decimalDigits=");
                    pw.print(getDecimalDigits());
                    pw.print(", numPrecRadix=");
                    pw.print(getNumPrecRadix());
                    break;
                case Types.CHAR, Types.VARCHAR:
                    pw.print(", charOctetLength=");
                    pw.print(getCharOctetLength());
                    break;
                case Types.LONGVARCHAR, Types.DATE, Types.TIME,
                    Types.TIMESTAMP, Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY:
                default:
                    break;
                }
                pw.print(", isNullable=");
                pw.print(isNullable());

                if (hasUsage()) {
                    pw.print(" Usages [");
                    for (Usage usage : getUsages()) {
                        pw.print('(');
                        usage.print(pw);
                        pw.print(')');
                    }
                    pw.println("]");
                }
            }
        }


        /** Name of table. */
        private final String name;

        /** Map from column name to column. */
        private Map<String, Column> columnMap;

        /** Sum of all of the table's column's column sizes. */
        private int totalColumnSize;

        /**
         * Whether the table is a fact, aggregate or other table type.
         * Note: this assumes that a table has only ONE usage.
         */
        private TableUsageType tableUsageType;

        /**
         * Typical table types are: "TABLE", "VIEW", "SYSTEM TABLE",
         * "GLOBAL TEMPORARY", "LOCAL TEMPORARY", "ALIAS", "SYNONYM".
         * (Depends what comes out of JDBC.)
         */
        private final String tableType;

        private final org.eclipse.daanse.rolap.mapping.model.Table modelTable; 
        
        // mondriandef stuff
        public org.eclipse.daanse.rolap.mapping.model.TableQuery table;

        private Table(final String name, String tableType, List<? extends org.eclipse.daanse.rolap.mapping.model.Column> list, org.eclipse.daanse.rolap.mapping.model.Table modelTable) {
            this.name = name;
            this.tableUsageType = TableUsageType.UNKNOWN;
            this.tableType = tableType;
            this.modelTable = modelTable;

			for (org.eclipse.daanse.rolap.mapping.model.Column rdbColumn : list) {

				String nameInner = rdbColumn.getName();
				int type = rdbColumn.getType() != null ? JDBCType.valueOf(rdbColumn.getType().name()).getVendorTypeNumber() : 0;
				ColumnType typeName = rdbColumn.getType();
				Integer columnSize = rdbColumn.getColumnSize() == null ? 0 : rdbColumn.getColumnSize();
				Integer decimalDigits = rdbColumn.getDecimalDigits() == null ? 0 : rdbColumn.getDecimalDigits();
				int numPrecRadix = rdbColumn.getNumPrecRadix() == null ? 0 : rdbColumn.getNumPrecRadix();
				int charOctetLength = rdbColumn.getCharOctetLength() == null ? 0 : rdbColumn.getCharOctetLength();
				boolean isNullable = Boolean.TRUE == rdbColumn.getNullable();

				Column column = new Column(nameInner);
				column.setType(type);
				column.setTypeName(typeName != null ? typeName.name() : null);
				column.setColumnSize(columnSize);
				column.setDecimalDigits(decimalDigits);
				column.setNumPrecRadix(numPrecRadix);
				column.setCharOctetLength(charOctetLength);
				column.setIsNullable(isNullable);

				getColumnMap().put(column.getName(), column);
				totalColumnSize += column.getColumnSize();
			}
        }

        /**
         * flushes all star usage references
         */
        public void flushUsages() {
            tableUsageType = TableUsageType.UNKNOWN;
            for (Table.Column col : getColumns()) {
                col.flushUsages();
            }
        }

        /**
         * Returns the name of the table.
         */
        public String getName() {
            return name;
        }

        /**
         * Returns the total size of a row (sum of the column sizes).
         */
        public int getTotalColumnSize() {
            return totalColumnSize;
        }

        /**
         * Returns the number of rows in the table.
         */
        public long getNumberOfRows() {
            return -1;
        }

        /**
         * Returns the collection of columns in this Table.
         */
        public Collection<Column> getColumns() {
            return getColumnMap().values();
        }

        /**
         * Returns an iterator over all column usages of a given type.
         */
        public Iterator<JdbcSchema.Table.Column.Usage> getColumnUsages(
            final UsageType usageType)
        {
            class CTIterator
                implements Iterator<JdbcSchema.Table.Column.Usage>
            {
                private final Iterator<Column> columnIter;
                private final UsageType columnType;
                private Iterator<JdbcSchema.Table.Column.Usage> usageIter;
                private JdbcSchema.Table.Column.Usage nextObject;

                CTIterator(Collection<Column> columns, UsageType columnType) {
                    this.columnIter = columns.iterator();
                    this.columnType = columnType;
                }

                @Override
				public boolean hasNext() {
                    while (true) {
                        while ((usageIter == null) || ! usageIter.hasNext()) {
                            if (! columnIter.hasNext()) {
                                nextObject = null;
                                return false;
                            }
                            Column c = columnIter.next();
                            usageIter = c.getUsages().iterator();
                        }
                        JdbcSchema.Table.Column.Usage usage = usageIter.next();
                        if (usage.getUsageType() == columnType) {
                            nextObject = usage;
                            return true;
                        }
                    }
                }
                @Override
                @SuppressWarnings("java:S2272")
				public JdbcSchema.Table.Column.Usage next() {
                    return nextObject;
                }
                @Override
				public void remove() {
                    usageIter.remove();
                }
            }
            return new CTIterator(getColumns(), usageType);
        }

        /**
         * Returns a column by its name.
         */
        public Column getColumn(final String columnName) {
            return getColumnMap().get(columnName);
        }

        /**
         * Return true if this table contains a column with the given name.
         */
        public boolean constainsColumn(final String columnName) {
            return getColumnMap().containsKey(columnName);
        }

        /**
         * Sets the table usage (fact, aggregate or other).
         *
         * @param tableUsageType Usage type
         */
        public void setTableUsageType(final TableUsageType tableUsageType) {
            // if usageIter has already been set, then usageIter can NOT be
            // reset
            if ((this.tableUsageType != TableUsageType.UNKNOWN)
                && (this.tableUsageType != tableUsageType))
            {
                throw new OlapRuntimeException(MessageFormat.format(attemptToChangeTableUsage,
                    getName(),
                    this.tableUsageType.name(),
                    tableUsageType.name()));
            }
            this.tableUsageType = tableUsageType;
        }

        /**
         * Returns the table's usage type.
         */
        public TableUsageType getTableUsageType() {
            return tableUsageType;
        }

        /**
         * Returns the table's type.
         */
        public String getTableType() {
            return tableType;
        }

        public org.eclipse.daanse.rolap.mapping.model.Table getModelTable() {
            return modelTable;
        }

        @Override
		public String toString() {
            StringWriter sw = new StringWriter(256);
            PrintWriter pw = new PrintWriter(sw);
            print(pw, "");
            pw.flush();
            return sw.toString();
        }
        public void print(final PrintWriter pw, final String prefix) {
            pw.print(prefix);
            pw.println("Table:");
            String subprefix = new StringBuilder(prefix).append("  ").toString();
            String subsubprefix = new StringBuilder(subprefix).append("  ").toString();

            pw.print(subprefix);
            pw.print("name=");
            pw.print(getName());
            pw.print(", type=");
            pw.print(getTableType());
            pw.print(", usage=");
            pw.println(getTableUsageType().name());

            pw.print(subprefix);
            pw.print("totalColumnSize=");
            pw.println(getTotalColumnSize());

            pw.print(subprefix);
            pw.println("Columns: [");
            for (Column column : getColumnMap().values()) {
                column.print(pw, subsubprefix);
                pw.println();
            }
            pw.print(subprefix);
            pw.println("]");
        }



        public Map<String, Column> getColumnMap() {
            if (columnMap == null) {
                columnMap = new HashMap<>();
            }
            return columnMap;
        }
    }

    private org.eclipse.daanse.rolap.mapping.model.DatabaseSchema databaseSchema;

    /**
     * Tables by name. We use a sorted map so {@link #getTables()}'s output
     * is in deterministic order.
     */
    private final SortedMap<String, Table> tables =
        new TreeMap<>();

	public JdbcSchema(final org.eclipse.daanse.rolap.mapping.model.DatabaseSchema databaseSchema) {
		this.databaseSchema = databaseSchema;
		loadTables();
	}



    /**
     * Returns the database's tables. The collection is sorted by table name.
     */
    public synchronized Collection<Table> getTables() {
        return getTablesMap().values();
    }

    /**
     * flushes all star usage references
     */
    public synchronized void flushUsages() {
        for (Table table : getTables()) {
            table.flushUsages();
        }
    }

    /**
     * Gets a table by name.
     */
    public synchronized Table getTable(final String tableName) {
        return getTablesMap().get(tableName);
    }

    @Override
	public String toString() {
        StringWriter sw = new StringWriter(256);
        PrintWriter pw = new PrintWriter(sw);
        print(pw, "");
        pw.flush();
        return sw.toString();
    }

    public void print(final PrintWriter pw, final String prefix) {
        pw.print(prefix);
        pw.println("JdbcSchema:");
        String subprefix = new StringBuilder(prefix).append("  ").toString();
        String subsubprefix = new StringBuilder(subprefix).append("  ").toString();

        pw.print(subprefix);
        pw.println("Tables: [");
        for (Table table : getTablesMap().values()) {
            table.print(pw, subsubprefix);
        }
        pw.print(subprefix);
        pw.println("]");
    }

    /**
     * Gets all of the tables (and views) in the database.
     */
	protected void loadTables() {

		for (org.eclipse.daanse.rolap.mapping.model.Table rdbTable : databaseSchema.getTables()) {
			if (rdbTable instanceof org.eclipse.daanse.rolap.mapping.model.PhysicalTable || rdbTable instanceof org.eclipse.daanse.rolap.mapping.model.ViewTable || rdbTable instanceof org.eclipse.daanse.rolap.mapping.model.SystemTable) {

			Table table = new Table(rdbTable.getName(), rdbTable.getClass().getSimpleName(),rdbTable.getColumns(), rdbTable);
				getLogger().debug("Adding table {}", rdbTable.getName());
				tables.put(table.getName(), table);
			}
		}
	}




    public SortedMap<String, Table> getTablesMap() {
        return tables;
    }

}
