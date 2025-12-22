/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (c) 2002-2017 Hitachi Vantara..  All rights reserved.
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


package org.eclipse.daanse.rolap.element;

import org.eclipse.daanse.jdbc.db.dialect.api.type.Datatype;
import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.aggregator.Aggregator;
import org.eclipse.daanse.olap.api.element.MetaData;
import org.eclipse.daanse.olap.api.element.PhisicalCubeMeasure;
import org.eclipse.daanse.olap.api.formatter.CellFormatter;
import org.eclipse.daanse.olap.common.StandardProperty;
import org.eclipse.daanse.olap.exceptions.CastInvalidTypeException;
import org.eclipse.daanse.olap.query.component.StringLiteralImpl;
import org.eclipse.daanse.rolap.aggregator.CountAggregator;
import org.eclipse.daanse.rolap.aggregator.DistinctCountAggregator;
import org.eclipse.daanse.rolap.common.RolapResult;
import org.eclipse.daanse.rolap.common.RolapSqlExpression;
import org.eclipse.daanse.rolap.mapping.model.ColumnInternalDataType;

/**
 * Measure which is computed from a SQL column (or expression) and which is
 * defined in a non-virtual cube.
 *
 * @see RolapVirtualCubeMeasure
 *
 * @author jhyde
 * @since 24 August, 2006
 */
public class RolapBaseCubeMeasure
    extends RolapMemberBase
    implements PhisicalCubeMeasure, RolapStoredMeasure
{

    private final static String unknownAggregator = "Unknown aggregator ''{0}''";

    static enum DataType {
        Integer,
        Numeric,
        String
    }

    /**
     * For SQL generator. Column which holds the value of the measure.
     */
    private final RolapSqlExpression expression;

    /**
     * For SQL generator. Has values "SUM", "COUNT", etc.
     */
    private final Aggregator aggregator;

    private final RolapCube cube;
    private final MetaData metadata;

    /**
     * Holds the {@link org.eclipse.daanse.rolap.common.RolapStar.Measure} from which this
     * member is computed. Untyped, because another implementation might store
     * it somewhere else.
     */
    private Object starMeasure;

    private RolapResult.ValueFormatter formatter;

    /**
     * Creates a RolapBaseCubeMeasure.
     *
     * @param cube Cube
     * @param parentMember Parent member
     * @param level Level this member belongs to
     * @param name Name of this member
     * @param caption Caption
     * @param description Description
     * @param formatString Format string
     * @param expression Expression
     * @param aggregator Aggregator
     * @param datatype Data type
     * @param metadata metadata
     */
    public RolapBaseCubeMeasure(
        RolapCube cube,
        RolapMember parentMember,
        RolapLevel level,
        String name,
        String caption,
        String description,
        String formatString,
        RolapSqlExpression expression,
        Aggregator aggregator,
        ColumnInternalDataType datatype,
        MetaData metadata)
    {
        super(parentMember, level, name, null, MemberType.MEASURE);
        assert metadata != null;
        this.cube = cube;
        this.metadata = metadata;
        this.caption = caption;
        this.expression = expression;
        if (description != null) {
            setProperty(
                StandardProperty.DESCRIPTION_PROPERTY.getName(),
                description);
        }
        if (formatString == null) {
            formatString = "";
        } else {
            setProperty(
                StandardProperty.FORMAT_STRING.getName(),
                formatString);
        }
        setProperty(
            StandardProperty.FORMAT_EXP_PARSED.getName(),
            StringLiteralImpl.create(formatString));
        setProperty(
            StandardProperty.FORMAT_EXP.getName(),
            formatString);

        Context context=getCube().getContext();

        // Validate aggregator.
        this.aggregator = aggregator;

        setProperty(StandardProperty.AGGREGATION_TYPE.getName(), aggregator);
        if (datatype == null || ColumnInternalDataType.UNDEFINED.equals(datatype)) {
            if (aggregator == CountAggregator.INSTANCE
                || aggregator == DistinctCountAggregator.INSTANCE)
            {
                datatype = ColumnInternalDataType.INTEGER;
            } else {
                datatype = ColumnInternalDataType.NUMERIC;
            }
        }
        if (RolapBaseCubeMeasure.DataType.valueOf(datatype.getLiteral()) == null) {
            throw new CastInvalidTypeException(datatype.getLiteral());
        }
        setProperty(StandardProperty.DATATYPE.getName(), datatype.getLiteral());
    }

    @Override
	public RolapSqlExpression getDaanseDefExpression() {
        return expression;
    }

    @Override
	public Aggregator getAggregator() {
        return aggregator;
    }

    @Override
	public RolapCube getCube() {
        return cube;
    }

    @Override
	public RolapResult.ValueFormatter getFormatter() {
        return formatter;
    }

    public void setFormatter(CellFormatter cellFormatter) {
        this.formatter =
            new RolapResult.CellFormatterValueFormatter(cellFormatter);
    }

    @Override
	public Object getStarMeasure() {
        return starMeasure;
    }

    public void setStarMeasure(Object starMeasure) {
        this.starMeasure = starMeasure;
    }

    @Override
    public MetaData getMetaData() {
        return metadata;
    }

    public Datatype getDatatype() {
        Object datatype = getPropertyValue(StandardProperty.DATATYPE.getName());
        try {
            return Datatype.fromValue((String) datatype);
        } catch (ClassCastException | IllegalArgumentException e) {
            return Datatype.VARCHAR;
        }
    }

    @Override
    public String getAggregateFunction() {
        return getAggregator().getName();
    }
}
