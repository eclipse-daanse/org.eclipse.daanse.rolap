/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2001-2005 Julian Hyde
 * Copyright (C) 2005-2017 Hitachi Vantara and others
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

package org.eclipse.daanse.rolap.element;

import static org.eclipse.daanse.olap.common.Util.makeFqName;

import java.text.MessageFormat;
import java.util.Locale;

import org.eclipse.daanse.olap.api.CatalogReader;
import org.eclipse.daanse.olap.api.LevelProperty;
import org.eclipse.daanse.olap.api.MatchType;
import org.eclipse.daanse.olap.api.Segment;
import org.eclipse.daanse.olap.api.SqlExpression;
import org.eclipse.daanse.olap.api.element.Dimension;
import org.eclipse.daanse.olap.api.element.Hierarchy;
import org.eclipse.daanse.olap.api.element.OlapElement;
import org.eclipse.daanse.olap.api.formatter.MemberPropertyFormatter;
import org.eclipse.daanse.olap.element.AbstractProperty;
import org.eclipse.daanse.rolap.common.RolapStar;
import org.eclipse.daanse.rolap.common.RolapStar.Column;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RolapProperty is the definition of a member property.
 *
 * @author jhyde
 */
public class RolapProperty extends AbstractProperty implements LevelProperty, OlapElement {

    private static final Logger LOGGER = LoggerFactory.getLogger(RolapProperty.class);
    private final static String mdxPropertyName = "property ''{0}''";

    /** Array of RolapProperty of length 0. */
    public static final RolapProperty[] emptyArray = new RolapProperty[0];

    private final MemberPropertyFormatter formatter;
    private final String caption;
    private final boolean dependsOnLevelValue;
    private RolapLevel level;

    /** The column or expression which yields the property's value. */
    private final SqlExpression exp;

    private RolapStar.Column column = null;


    /**
     * Creates a RolapProperty.
     *
     * @param name Name of property
     * @param type Datatype
     * @param exp Expression for property's value; often a literal
     * @param formatter A property formatter, or null
     * @param caption Caption
     * @param dependsOnLevelValue Whether the property is functionally dependent
     *     on the level with which it is associated
     * @param internal Whether property is internal
     */
    protected RolapProperty(
        String name,
        Datatype type,
        SqlExpression exp,
        MemberPropertyFormatter formatter,
        String caption,
        Boolean dependsOnLevelValue,
        boolean internal,
        String description,
        RolapLevel level)
    {
        super(name, type, internal, false, false, description);
        this.exp = exp;
        this.caption = caption;
        this.formatter = formatter;
        this.dependsOnLevelValue =
            dependsOnLevelValue != null && dependsOnLevelValue;
        this.level = level;
    }

    public SqlExpression getExp() {
        return exp;
    }

    @Override
	public MemberPropertyFormatter getFormatter() {
        return formatter;
    }

    /**
     * @return Returns the caption.
     */
    @Override
	public String getCaption() {
        if (caption == null) {
            return getName();
        }
        return caption;
    }

    /**
     * @return Returns the dependsOnLevelValue setting (if unset,
     * returns false).  This indicates whether the property is
     * functionally dependent on the level with which it is
     * associated.
     *
     * If true, then the property column can be eliminated from
     * the GROUP BY clause for queries on certain databases such
     * as MySQL.
     */
    public boolean dependsOnLevelValue() {
        return dependsOnLevelValue;
    }

    public RolapStar.Column getColumn() {
        return column;
    }

    public void setColumn(RolapStar.Column column) {
        this.column = column;
    }

    @Override
    public String getUniqueName() {
        if (level != null) {
            return makeFqName(level.getUniqueName(), getName());
        }
        return null;
    }

    @Override
    public OlapElement lookupChild(CatalogReader schemaReader, Segment s, MatchType matchType) {
        return null;
    }

    @Override
    public String getQualifiedName() {
        if (getUniqueName() != null) {
            return MessageFormat.format(mdxPropertyName, getUniqueName());
        }
        return MessageFormat.format(mdxPropertyName, getName());
    }

    @Override
    public String getLocalized(LocalizedProperty prop, Locale locale) {
        if (level != null) {
            return level.getLocalized(prop, locale);
        }
        return null;
    }

    @Override
    public Hierarchy getHierarchy() {
        if (level != null) {
            return level.getHierarchy();
        }
        return null;
    }

    @Override
    public Dimension getDimension() {
        if (level != null) {
            return level.getDimension();
        }
        return null;
    }

    @Override
    public boolean isVisible() {
        return level != null && level.isVisible();
    }

    public RolapLevel getLevel() {
        return this.level;
    }

    public void setLevel(RolapLevel level) {
        this.level = level;
    }
}
