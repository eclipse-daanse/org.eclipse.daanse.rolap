/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2005-2005 Julian Hyde
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
package org.eclipse.daanse.rolap.common.aggmatcher;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.daanse.rolap.api.aggmatch.AggregationFactCountMatch;
import org.eclipse.daanse.rolap.api.aggmatch.AggregationForeignKeyMatch;
import org.eclipse.daanse.rolap.api.aggmatch.AggregationMatchRule;
import org.eclipse.daanse.rolap.api.aggmatch.AggregationMatchRules;
import org.eclipse.daanse.rolap.api.aggmatch.AggregationTableMatch;
import org.eclipse.daanse.rolap.common.star.RolapStar;
import org.eclipse.daanse.rolap.recorder.MessageRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Container for aggregate recognition rules.
 * Wraps {@link AggregationMatchRules} and compiles them into
 * {@link Recognizer.Matcher} instances for efficient column matching.
 *
 * @author Richard M. Emberson
 */
public class PatternbasedRules {

    private static final Logger LOGGER = LoggerFactory.getLogger(PatternbasedRules.class);

    private final AggregationMatchRules rules;
    private final Map<String, Recognizer.Matcher> factToPattern;
    private final Map<String, Recognizer.Matcher> foreignKeyMatcherMap;
    private Recognizer.Matcher ignoreMatcherMap;
    private Recognizer.Matcher factCountMatcher;
    private String tag;

    PatternbasedRules(final AggregationMatchRules rules, String aggregateRuleTag) {
        this.rules = rules;
        this.factToPattern = new HashMap<>();
        this.foreignKeyMatcherMap = new HashMap<>();
        this.tag = aggregateRuleTag;
    }

    public String getTag() {
        return this.tag;
    }

    /**
     * Returns the {@link AggregationMatchRule} whose tag equals this rule's tag.
     */
    public AggregationMatchRule getAggRule() {
        return getAggRule(getTag());
    }

    /**
     * Returns the {@link AggregationMatchRule} whose tag equals the parameter tag,
     * or null if not found.
     */
    public AggregationMatchRule getAggRule(final String tag) {
        for (AggregationMatchRule rule : this.rules.getAggregationRules()) {
            if (rule.getEnabled().orElse(true) && tag.equals(rule.getTag())) {
                return rule;
            }
        }
        return null;
    }

    /**
     * Returns the active rule for this instance's tag, or throws if none found.
     */
    private AggregationMatchRule requireAggRule() {
        AggregationMatchRule rule = getAggRule();
        if (rule == null) {
            throw new IllegalStateException("No enabled rule found with tag: " + tag);
        }
        return rule;
    }

    /**
     * Gets the {@link Recognizer.Matcher} for this tableName.
     */
    public Recognizer.Matcher getTableMatcher(final String tableName) {
        Recognizer.Matcher matcher = factToPattern.get(tableName);
        if (matcher == null) {
            AggregationMatchRule rule = requireAggRule();
            AggregationTableMatch tableMatch = rule.getTableMatch();
            matcher = AggMatchService.createTableMatcher(tableMatch, tableName);
            factToPattern.put(tableName, matcher);
        }
        return matcher;
    }

    /**
     * Gets the {@link Recognizer.Matcher} for columns that should be ignored.
     */
    public Recognizer.Matcher getIgnoreMatcher() {
        if (ignoreMatcherMap == null) {
            AggregationMatchRule rule = requireAggRule();
            ignoreMatcherMap = rule.getIgnoreMap()
                .map(AggMatchService::createIgnoreMatcher)
                .orElse(name -> false);
        }
        return ignoreMatcherMap;
    }

    /**
     * Gets the {@link Recognizer.Matcher} for the fact count column.
     */
    public Recognizer.Matcher getFactCountMatcher() {
        if (factCountMatcher == null) {
            AggregationMatchRule rule = requireAggRule();
            AggregationFactCountMatch factCountMatch = rule.getFactCountMatch();
            factCountMatcher = AggMatchService.createFactCountMatcher(factCountMatch);
        }
        return factCountMatcher;
    }

    /**
     * Gets the {@link Recognizer.Matcher} for this foreign key column name.
     */
    public Recognizer.Matcher getForeignKeyMatcher(String foreignKeyName) {
        Recognizer.Matcher matcher = foreignKeyMatcherMap.get(foreignKeyName);
        if (matcher == null) {
            AggregationMatchRule rule = requireAggRule();
            AggregationForeignKeyMatch foreignKeyMatch = rule.getForeignKeyMatch();
            matcher = AggMatchService.createForeignKeyMatcher(foreignKeyMatch, foreignKeyName);
            foreignKeyMatcherMap.put(foreignKeyName, matcher);
        }
        return matcher;
    }

    /**
     * Returns true if this candidate aggregate table name "matches" the
     * factTableName.
     */
    public boolean matchesTableName(
        final String factTableName,
        final String name)
    {
        Recognizer.Matcher matcher = getTableMatcher(factTableName);
        return matcher.matches(name);
    }

    /**
     * Creates a {@link Recognizer.Matcher} for the given measure name
     * (symbolic name), column name and aggregate name (sum, count, etc.).
     */
    public Recognizer.Matcher getMeasureMatcher(
        final String measureName,
        final String measureColumnName,
        final String aggregateName)
    {
        AggregationMatchRule rule = requireAggRule();
        return AggMatchService.createMeasureMatcher(
            rule.getMeasureMap(),
            measureName,
            measureColumnName,
            aggregateName);
    }

    /**
     * Gets a {@link Recognizer.Matcher} for a given level's hierarchy's name,
     * level name and column name.
     */
    public Recognizer.Matcher getLevelMatcher(
        final String usagePrefix,
        final String hierarchyName,
        final String levelName,
        final String levelColumnName)
    {
        AggregationMatchRule rule = requireAggRule();
        return AggMatchService.createLevelMatcher(
            rule.getLevelMap(),
            usagePrefix,
            hierarchyName,
            levelName,
            levelColumnName);
    }

    /**
     * Uses the {@link PatternbasedRecognizer} Recognizer to determine if the
     * given aggTable's columns all match upto the dbFactTable's columns (where
     * present) making the column usages as a result.
     */
    boolean columnsOK(
        final RolapStar star,
        final JdbcSchema.Table dbFactTable,
        final JdbcSchema.Table aggTable,
        final MessageRecorder msgRecorder)
    {
        Recognizer cb = new PatternbasedRecognizer(
            this,
            star,
            dbFactTable,
            aggTable,
            msgRecorder);
        return cb.check();
    }
}
