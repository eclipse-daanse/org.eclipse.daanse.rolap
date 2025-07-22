/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation.
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
package org.eclipse.daanse.rolap.aggmatch.jaxb;

import java.util.ArrayList;
import java.util.List;

/**
 * The set of "named" rules for matching aggregate tables.
 * Only one rule can be applied to a given connection. In
 * addition, one rule must be set as the default - this rule
 * is always the choice when not selecting by name.
 * It is very important that the AggRules validate method is called
 * prior to using any of the object.
 */

public class AggRules {

    public static final String NAME = "AggRules";
    /**
     * The identifying tag for a schema.
     */
    String tag;

    /**
     * All shared TableMatches.
     */
    List<TableMatch> tableMatches = new ArrayList<>();

    /**
     * All shared FactCountMatches.
     */
    List<FactCountMatch> factCountMatches = new ArrayList<>();

    /**
     * All shared ForeignKeyMatches.
     */
    List<ForeignKeyMatch> foreignKeyMatches = new ArrayList<>();

    /**
     * All shared LevelMap.
     */
    List<LevelMap> levelMaps = new ArrayList<>();

    /**
     * All shared MeasureMap.
     */
    List<MeasureMap> measureMaps = new ArrayList<>();

    /**
     * All shared IgnoreMap.
     */
    List<IgnoreMap> ignoreMaps = new ArrayList<>();

    /**
     * All AggRules (at least one).
     * Also, one of them must be marked with default=true.
     */
    List<AggRule> aggRules = new ArrayList<>();

    private static final org.slf4j.Logger LOGGER =
        org.slf4j.LoggerFactory.getLogger(AggRules.class);

    protected static org.slf4j.Logger getLogger() {
        return LOGGER;
    }

    public String getTag() {
        return tag;
    }

    public AggRule getAggRule(String tag) {
        for (AggRule aggRule : aggRules) {
            if (Boolean.TRUE.equals(aggRule.isEnabled()) && aggRule.getTag().equals(tag)) {
                return aggRule;
            }
        }
        return null;
    }

    public void validate(final org.eclipse.daanse.rolap.recorder.MessageRecorder msgRecorder) {
        msgRecorder.pushContextName(getName());
        try {
            validate(factCountMatches, msgRecorder);
            validate(tableMatches, msgRecorder);
            validate(levelMaps, msgRecorder);
            validate(measureMaps, msgRecorder);
            validate(ignoreMaps, msgRecorder);
            validate(aggRules, msgRecorder);
        } finally {
            msgRecorder.popContextName();
        }
    }

    private String getName() {
        return NAME;
    }

    private void validate(
        final List<? extends Base> bases,
        final org.eclipse.daanse.rolap.recorder.MessageRecorder msgRecorder
    ) {
        for (Base base : bases) {
            if (Boolean.TRUE.equals(base.isEnabled())) {
                base.validate(this, msgRecorder);
            }
        }
    }

    public boolean hasIgnoreMap(String id) {
        return (lookupIgnoreMap(id) != null);
    }

    public IgnoreMap lookupIgnoreMap(String id) {
        return (IgnoreMap) lookupBase(id, ignoreMaps);
    }

    public boolean hasFactCountMatch(String id) {
        return (lookupFactCountMatch(id) != null);
    }

    public FactCountMatch lookupFactCountMatch(String id) {
        return (FactCountMatch) lookupBase(id, factCountMatches);
    }

    public boolean hasForeignKeyMatch(String id) {
        return (lookupForeignKeyMatch(id) != null);
    }

    public ForeignKeyMatch lookupForeignKeyMatch(String id) {
        return (ForeignKeyMatch) lookupBase(id, foreignKeyMatches);
    }

    public boolean hasTableMatch(String id) {
        return (lookupTableMatch(id) != null);
    }

    public TableMatch lookupTableMatch(String id) {
        return (TableMatch) lookupBase(id, tableMatches);
    }

    public boolean hasLevelMap(String id) {
        return (lookupLevelMap(id) != null);
    }

    public LevelMap lookupLevelMap(String id) {
        return (LevelMap) lookupBase(id, levelMaps);
    }

    public boolean hasMeasureMap(String id) {
        return (lookupMeasureMap(id) != null);
    }

    public MeasureMap lookupMeasureMap(String id) {
        return (MeasureMap) lookupBase(id, measureMaps);
    }

    public boolean hasAggRule(String id) {
        return (lookupAggRule(id) != null);
    }

    public AggRule lookupAggRule(String id) {
        return (AggRule) lookupBase(id, aggRules);
    }

    private Base lookupBase(String tag, List<? extends Base> bases) {
        for (Base base : bases) {
            if (Boolean.TRUE.equals(base.isEnabled()) && base.getTag().equals(tag)) {
                return base;
            }
        }
        return null;
    }

    public List<IgnoreMap> getIgnoreMaps() {
        return ignoreMaps;
    }

    public List<FactCountMatch> getFactCountMatches() {
        return factCountMatches;
    }

    public List<ForeignKeyMatch> getForeignKeyMatches() {
        return foreignKeyMatches;
    }

    public List<TableMatch> getTableMatches() {
        return tableMatches;
    }

    public List<LevelMap> getLevelMaps() {
        return levelMaps;
    }

    public List<MeasureMap> getMeasureMaps() {
        return measureMaps;
    }

    public List<AggRule> getAggRules() {
        return aggRules;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }
}
