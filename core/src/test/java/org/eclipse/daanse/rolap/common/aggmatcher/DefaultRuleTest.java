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

package org.eclipse.daanse.rolap.common.aggmatcher;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Iterator;
import java.util.List;

import org.eclipse.daanse.rolap.aggmatch.jaxb.AggRule;
import org.eclipse.daanse.rolap.aggmatch.jaxb.AggRules;
import org.eclipse.daanse.rolap.aggmatch.jaxb.CharCaseEnum;
import org.eclipse.daanse.rolap.aggmatch.jaxb.FactCountMatch;
import org.eclipse.daanse.rolap.aggmatch.jaxb.FactCountMatchRef;
import org.eclipse.daanse.rolap.aggmatch.jaxb.ForeignKeyMatch;
import org.eclipse.daanse.rolap.aggmatch.jaxb.LevelMap;
import org.eclipse.daanse.rolap.aggmatch.jaxb.LevelMapRef;
import org.eclipse.daanse.rolap.aggmatch.jaxb.MeasureMap;
import org.eclipse.daanse.rolap.aggmatch.jaxb.MeasureMapRef;
import org.eclipse.daanse.rolap.aggmatch.jaxb.Regex;
import org.eclipse.daanse.rolap.aggmatch.jaxb.TableMatch;
import org.eclipse.daanse.rolap.aggmatch.jaxb.TableMatchRef;
import org.eclipse.daanse.rolap.recorder.ListRecorder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Testing the default aggregate table recognizer.
 *
 * @author Richard M. Emberson
 */
class DefaultRuleTest {
    private static final Logger LOGGER =
        LoggerFactory.getLogger(DefaultRuleTest.class);

    private static AggRules rules;

    private AggRule getAggRule(String tag) {
        return rules.getAggRule(tag);
    }

    @BeforeAll
    public static void beforeAll() throws Exception {

        rules = prepareRules();

       ListRecorder msgRecorder = new ListRecorder();
       rules.validate(msgRecorder);
        if (msgRecorder.hasErrors()) {
            LOGGER.error("HAS ERRORS");
            for (Iterator it = msgRecorder.getErrorEntries(); it.hasNext();) {
                ListRecorder.Entry e = (ListRecorder.Entry) it.next();
                LOGGER.error("context=" + e.context());
                LOGGER.error("message=" + e.message());
            }
        }
    }


    private static AggRules prepareRules() {
        AggRules aggrules = new AggRules();
        aggrules.setTag("default");

        TableMatch tm=new TableMatch();
        tm.setId("ta");
        tm.setPretemplate("agg_.+_");

        TableMatch tb=new TableMatch();
        tb.setId("tb");
        tb.setPosttemplate("_agg_.+");

        TableMatch tc=new TableMatch();
        tc.setId("tc");
        tc.setPretemplate("AGG_.+_");
        tc.setBasename("RF_(.*)_TABLE");

        aggrules.getTableMatches().add(tm);
        aggrules.getTableMatches().add(tb);
        aggrules.getTableMatches().add(tc);

        FactCountMatch fcm = new FactCountMatch();
        fcm.setId("fca");
        aggrules.getFactCountMatches().add(fcm);


        FactCountMatch fcb = new FactCountMatch();
        fcb.setId("fcb");
        fcb.setFactCountName("my_fact_count");
        aggrules.getFactCountMatches().add(fcb);

        FactCountMatch fcc = new FactCountMatch();
        fcc.setId("fcc");
        fcc.setFactCountName("my_fact_count");
        fcc.setCharCase(CharCaseEnum.UPPER);
        aggrules.getFactCountMatches().add(fcc);

        LevelMap lvlMap=new LevelMap();
        lvlMap.setId("lxx");
        aggrules.getLevelMaps().add(lvlMap);

        Regex regexLog=new Regex();
        regexLog.setId("logical");
        regexLog.setCharCase(CharCaseEnum.LOWER);
        regexLog.setTemplate("${hierarchy_name}_${level_name}");

        Regex regexMixed=new Regex();
        regexMixed.setId("mixed");
        regexMixed.setCharCase(CharCaseEnum.LOWER);
        regexMixed.setTemplate("${hierarchy_name}_${level_column_name}");

        Regex regexUsage=new Regex();
        regexUsage.setId("usage");
        regexUsage.setCharCase(CharCaseEnum.EXACT);
        regexUsage.setTemplate("${usage_prefix}${level_column_name}");

        Regex regexPhysical=new Regex();
        regexPhysical.setId("physical");
        regexPhysical.setCharCase(CharCaseEnum.EXACT);
        regexPhysical.setTemplate("${level_column_name}");

        lvlMap.setRegexs(List.of(regexLog,regexMixed,regexUsage,regexPhysical));

        MeasureMap measMap=new MeasureMap();
        measMap.setId("mxx");

        Regex mmRegexLogical=new Regex();
        mmRegexLogical.setId("logical");
        mmRegexLogical.setCharCase(CharCaseEnum.LOWER);
        mmRegexLogical.setTemplate("${measure_name}");

//        Sometimes a base fact table foreign key is also used in a
//        measure. This Regex is used to match such usages in
//        the aggregate table. Using such a match only makes sense
//        if one prior to attempting to match knows that the
//        column in question in the base fact table is indeed used
//        as a measure (for this matches any foreign key).

        Regex mmRegexForeignKey=new Regex();
        mmRegexForeignKey.setId("foreignkey");
        mmRegexForeignKey.setCharCase(CharCaseEnum.EXACT);
        mmRegexForeignKey.setTemplate("${measure_column_name}");

        Regex mmRegexPhysical=new Regex();
        mmRegexPhysical.setId("physical");
        mmRegexPhysical.setCharCase(CharCaseEnum.EXACT);
        mmRegexPhysical.setTemplate("${measure_column_name}_${aggregate_name}");

        measMap.setRegexs(List.of(mmRegexLogical,mmRegexForeignKey,mmRegexPhysical));

        aggrules.getMeasureMaps().add(measMap);

        AggRule aggRDefault=new AggRule();
        aggRDefault.setTag("default");

        FactCountMatchRef factCountMatchRef=new FactCountMatchRef();
        factCountMatchRef.setRefId("fca");
        aggRDefault.setFactCountMatchRef(factCountMatchRef);

        ForeignKeyMatch foreignKeyMatch=new ForeignKeyMatch();
        foreignKeyMatch.setId("fka");
        aggRDefault.setForeignKeyMatch(foreignKeyMatch);

        TableMatchRef tableMatchRef=new TableMatchRef();
        tableMatchRef.setRefId("ta");
        aggRDefault.setTableMatchRef(tableMatchRef);

        LevelMapRef levelMapRef=new LevelMapRef();
        levelMapRef.setRefId("lxx");
        aggRDefault.setLevelMapRef(levelMapRef);

        MeasureMapRef measureMapRef=new MeasureMapRef();
        measureMapRef.setRefId("mxx");
        aggRDefault.setMeasureMapRef(measureMapRef);


        aggrules.getAggRules().add(aggRDefault);


        AggRule aggRbbbb=new AggRule();
        aggRbbbb.setTag("bbbb");
        factCountMatchRef=new FactCountMatchRef();
        factCountMatchRef.setRefId("fcb");
        aggRbbbb.setFactCountMatchRef(factCountMatchRef);

        foreignKeyMatch=new ForeignKeyMatch();
        foreignKeyMatch.setId("fkb");
        foreignKeyMatch.setBasename("FK_(.*)");
        foreignKeyMatch.setPosttemplate("_FK");
        foreignKeyMatch.setCharCase(CharCaseEnum.UPPER);
        aggRbbbb.setForeignKeyMatch(foreignKeyMatch);

        tableMatchRef=new TableMatchRef();
        tableMatchRef.setRefId("tb");
        aggRbbbb.setTableMatchRef(tableMatchRef);

        LevelMap levelMap= new LevelMap();
        levelMap.setId("lb");

        regexUsage=new Regex();
        regexUsage.setId("usage");
        regexUsage.setCharCase(CharCaseEnum.LOWER);
        regexUsage.setSpace("_SP_");
        regexUsage.setDot("_DOT_");
        regexUsage.setTemplate("${usage_prefix}${hierarchy_name}_${level_name}_${level_column_name}");
        levelMap.setRegexs(List.of(regexUsage));

        aggRbbbb.setLevelMap(levelMap);

        measureMapRef=new MeasureMapRef();
        measureMapRef.setRefId("mxx");
        aggRbbbb.setMeasureMapRef(measureMapRef);

        aggrules.getAggRules().add(aggRbbbb);

        AggRule aggRcccc=new AggRule();
        aggRcccc.setTag("cccc");

        factCountMatchRef=new FactCountMatchRef();
        factCountMatchRef.setRefId("fcc");
        aggRcccc.setFactCountMatchRef(factCountMatchRef);

        foreignKeyMatch=new ForeignKeyMatch();
        foreignKeyMatch.setId("fkc");
        foreignKeyMatch.setBasename("(?:FK|fk)_(.*)");
        foreignKeyMatch.setPosttemplate("_[fF][kK]");
        foreignKeyMatch.setCharCase(CharCaseEnum.EXACT);
        aggRcccc.setForeignKeyMatch(foreignKeyMatch);

        tableMatchRef=new TableMatchRef();
        tableMatchRef.setRefId("tc");
        aggRcccc.setTableMatchRef(tableMatchRef);

        levelMapRef=new LevelMapRef();
        levelMapRef.setRefId("lxx");
        aggRcccc.setLevelMapRef(levelMapRef);

        measureMapRef=new MeasureMapRef();
        measureMapRef.setRefId("mxx");
        aggRcccc.setMeasureMapRef(measureMapRef);

        aggrules.getAggRules().add(aggRcccc);

        return aggrules;
    }

    private Recognizer.Matcher getTableMatcher(String tag, String tableName) {
        AggRule rule = getAggRule(tag);
        if (rule == null) {
            LOGGER.info("rule == null for tag=" + tag);
        }
        TableMatch tableMatch = rule.getTableMatch();
        if (tableMatch == null) {
            LOGGER.info(
                "tableMatch == null for tag="
                    + tag
                    + ", tableName="
                    + tableName);
        }
        return tableMatch.getMatcher(tableName);
    }

    private Recognizer.Matcher getFactCountMatcher(String tag) {
        AggRule rule = getAggRule(tag);
        FactCountMatch factTableName = rule.getFactCountMatch();
        return factTableName.getMatcher();
    }

    private Recognizer.Matcher getForeignKeyMatcher(
        String tag,
        String foreignKeyName)
    {
        AggRule rule = getAggRule(tag);
        ForeignKeyMatch foreignKeyMatch = rule.getForeignKeyMatch();
        return foreignKeyMatch.getMatcher(foreignKeyName);
    }


    private Recognizer.Matcher getLevelMatcher(
        String tag,
        String usagePrefix,
        String hierarchyName,
        String levelName,
        String levelColumnName)
    {
        AggRule rule = getAggRule(tag);
        Recognizer.Matcher matcher =
            rule.getLevelMap().getMatcher(
                usagePrefix,
                hierarchyName,
                levelName,
                levelColumnName);
        return matcher;
    }

    private Recognizer.Matcher getMeasureMatcher(
        String tag,
        String measureName,
        String measureColumnName,
        String aggregateName)
    {
        AggRule rule = getAggRule(tag);
        Recognizer.Matcher matcher =
            rule.getMeasureMap().getMatcher(
                measureName,
                measureColumnName,
                aggregateName);
        return matcher;
    }

    //////////////////////////////////////////////////////////////////////////
    //
    // tests
    //
    //

    @Test
    void testTableNameDefault() {
        final String tag = "default";
        final String factTableName = "FACT_TABLE";

        Recognizer.Matcher matcher = getTableMatcher(tag, factTableName);

        doMatch(matcher, "agg_10_" + factTableName);
        doMatch(matcher, "AGG_10_" + factTableName);
        doMatch(matcher, "agg_this_is_ok_" + factTableName);
        doMatch(matcher, "AGG_THIS_IS_OK_" + factTableName);
        doMatch(matcher, "agg_10_" + factTableName.toLowerCase());
        doMatch(matcher, "AGG_10_" + factTableName.toLowerCase());
        doMatch(matcher, "agg_this_is_ok_" + factTableName.toLowerCase());
        doMatch(matcher, "AGG_THIS_IS_OK_" + factTableName.toLowerCase());

        doNotMatch(matcher, factTableName);
        doNotMatch(matcher, "agg__" + factTableName);
        doNotMatch(matcher, "agg_" + factTableName);
        doNotMatch(matcher, factTableName + "_agg");
        doNotMatch(matcher, "agg_10_Mytable");
    }

    @Test
    void testTableNameBBBB() {
        final String tag = "bbbb";
        final String factTableName = "FACT_TABLE";

        Recognizer.Matcher matcher = getTableMatcher(tag, factTableName);

        doMatch(matcher, factTableName + "_agg_10");
        doMatch(matcher, factTableName + "_agg_this_is_ok");

        doNotMatch(matcher, factTableName);
        doNotMatch(matcher, factTableName + "_agg");
        doNotMatch(matcher, factTableName + "__agg");
        doNotMatch(matcher, "agg_" + factTableName);
        doNotMatch(matcher, "Mytable_agg_10");
    }

    @Test
    void testTableNameCCCCBAD() {
        final String tag = "cccc";
        final String basename = "WAREHOUSE";
        final String factTableName = "RF_" + basename + "_TABLE";

        // Note that the "basename" and not the fact table name is
        // being used. The Matcher that is return will not match anything
        // because the basename does not match the table basename pattern.
        Recognizer.Matcher matcher = getTableMatcher(tag, basename);

        doNotMatch(matcher, "AGG_10_" + basename);
        doNotMatch(matcher, "agg_this_is_ok_" + basename);

        doNotMatch(matcher, factTableName);
        doNotMatch(matcher, "agg__" + basename);
        doNotMatch(matcher, "agg_" + basename);
        doNotMatch(matcher, basename + "_agg");
        doNotMatch(matcher, "agg_10_Mytable");
    }

    @Test
    void testTableNameCCCCGOOD() {
        final String tag = "cccc";
        final String basename = "WAREHOUSE";
        final String factTableName = "RF_" + basename + "_TABLE";

        Recognizer.Matcher matcher = getTableMatcher(tag, factTableName);

        doMatch(matcher, "AGG_10_" + basename);
        doMatch(matcher, "agg_this_is_ok_" + basename);

        doNotMatch(matcher, factTableName);
        doNotMatch(matcher, "agg__" + basename);
        doNotMatch(matcher, "agg_" + basename);
        doNotMatch(matcher, basename + "_agg");
        doNotMatch(matcher, "agg_10_Mytable");
    }

    @Test
    void testFactCountDefault() {
        final String tag = "default";
        Recognizer.Matcher matcher = getFactCountMatcher(tag);

        doMatch(matcher, "fact_count");
        doMatch(matcher, "FACT_COUNT");

        doNotMatch(matcher, "my_fact_count");
        doNotMatch(matcher, "MY_FACT_COUNT");
        doNotMatch(matcher, "count");
        doNotMatch(matcher, "COUNT");
        doNotMatch(matcher, "fact_count_my");
        doNotMatch(matcher, "FACT_COUNT_MY");
    }

    @Test
    void testFactCountBBBB() {
        final String tag = "bbbb";
        Recognizer.Matcher matcher = getFactCountMatcher(tag);

        doMatch(matcher, "my_fact_count");
        doMatch(matcher, "MY_FACT_COUNT");

        doNotMatch(matcher, "fact_count");
        doNotMatch(matcher, "FACT_COUNT");
        doNotMatch(matcher, "count");
        doNotMatch(matcher, "COUNT");
        doNotMatch(matcher, "fact_count_my");
        doNotMatch(matcher, "FACT_COUNT_MY");
    }

    @Test
    void testFactCountCCCC() {
        final String tag = "cccc";
        Recognizer.Matcher matcher = getFactCountMatcher(tag);

        doMatch(matcher, "MY_FACT_COUNT");

        doNotMatch(matcher, "my_fact_count");
        doNotMatch(matcher, "fact_count");
        doNotMatch(matcher, "FACT_COUNT");
        doNotMatch(matcher, "count");
        doNotMatch(matcher, "COUNT");
        doNotMatch(matcher, "fact_count_my");
        doNotMatch(matcher, "FACT_COUNT_MY");
    }

    @Test
    void testForeignKeyDefault() {
        final String tag = "default";
        final String foreignKeyName = "foo_key";
        Recognizer.Matcher matcher = getForeignKeyMatcher(tag, foreignKeyName);

        doMatch(matcher, "foo_key");
        doMatch(matcher, "FOO_KEY");

        doNotMatch(matcher, "foo_key_my");
        doNotMatch(matcher, "my_foo_key");
    }

    @Test
    void testForeignKeyBBBB() {
        final String tag = "bbbb";
        final String foreignKeyName = "fk_ham_n_eggs";
        Recognizer.Matcher matcher = getForeignKeyMatcher(tag, foreignKeyName);

        doMatch(matcher, "HAM_N_EGGS_FK");

        doNotMatch(matcher, "ham_n_eggs_fk");
        doNotMatch(matcher, "ham_n_eggs");
        doNotMatch(matcher, "fk_ham_n_eggs");
        doNotMatch(matcher, "HAM_N_EGGS");
        doNotMatch(matcher, "FK_HAM_N_EGGS");
    }
/*
        <ForeignKeyMatch id="fkc" basename="(?:FK|fk)_(.*)"
                posttemplate="_[fF][kK]"
                charcase="exact" />
*/
    @Test
    void testForeignKeyCCCC() {
        final String tag = "cccc";
        final String foreignKeyName1 = "fk_toast";
        final String foreignKeyName2 = "FK_TOAST";
        final String foreignKeyName3 = "FK_ToAsT";
        Recognizer.Matcher matcher1 =
            getForeignKeyMatcher(tag, foreignKeyName1);
        Recognizer.Matcher matcher2 =
            getForeignKeyMatcher(tag, foreignKeyName2);
        Recognizer.Matcher matcher3 =
            getForeignKeyMatcher(tag, foreignKeyName3);

        doMatch(matcher1, "toast_fk");
        doNotMatch(matcher1, "TOAST_FK");

        doMatch(matcher2, "TOAST_FK");
        doNotMatch(matcher2, "toast_fk");

        doMatch(matcher3, "ToAsT_FK");
        doMatch(matcher3, "ToAsT_fk");
        doMatch(matcher3, "ToAsT_Fk");
        doNotMatch(matcher3, "toast_fk");
        doNotMatch(matcher3, "TOAST_FK");
    }

    @Test
    void testLevelDefaultOne() {
        final String tag = "default";
        final String usagePrefix = null;
        final String hierarchyName = "Time";
        final String levelName = "Day in Year";
        final String levelColumnName = "days";
        Recognizer.Matcher matcher = getLevelMatcher(
            tag, usagePrefix, hierarchyName, levelName, levelColumnName);

        doMatch(matcher, "days");
        doMatch(matcher, "time_day_in_year");
        doMatch(matcher, "time_days");

        doNotMatch(matcher, "DAYS");
        doNotMatch(matcher, "Time Day in Year");
    }

    @Test
    void testLevelDefaultTwo() {
        final String tag = "default";
        final String usagePrefix = "boo_";
        final String hierarchyName = "Time";
        final String levelName = "Day in Year";
        final String levelColumnName = "days";
        Recognizer.Matcher matcher = getLevelMatcher(
            tag, usagePrefix, hierarchyName, levelName, levelColumnName);

        doMatch(matcher, "days");
        doMatch(matcher, "boo_days");
        doMatch(matcher, "time_day_in_year");
        doMatch(matcher, "time_days");

        doNotMatch(matcher, "boo_time_day_in_year");
        doNotMatch(matcher, "boo_time_days");
        doNotMatch(matcher, "DAYS");
        doNotMatch(matcher, "Time Day in Year");
    }

    @Test
    void testLevelBBBB() {
        final String tag = "bbbb";
        final String usagePrefix = "boo_";
        final String hierarchyName = "Time.Period";
        final String levelName = "Day in Year";
        final String levelColumnName = "days";
        Recognizer.Matcher matcher = getLevelMatcher(
            tag, usagePrefix, hierarchyName, levelName, levelColumnName);

        doMatch(matcher, "boo_time_DOT_period_day_SP_in_SP_year_days");
    }

    @Test
    void testMeasureDefault() {
        final String tag = "default";
        final String measureName = "Total Sales";
        final String measureColumnName = "sales";
        final String aggregateName = "sum";
        Recognizer.Matcher matcher = getMeasureMatcher(
            tag, measureName, measureColumnName, aggregateName);

        doMatch(matcher, "total_sales");
        doMatch(matcher, "sales");
        doMatch(matcher, "sales_sum");

        doNotMatch(matcher, "Total Sales");
        doNotMatch(matcher, "Total_Sales");
        doNotMatch(matcher, "total_sales_sum");
    }

    //
    //////////////////////////////////////////////////////////////////////////

    //////////////////////////////////////////////////////////////////////////
    //
    // helpers
    //
    //
    private void doMatch(Recognizer.Matcher matcher, String s) {
        assertTrue(matcher.matches(s), "Recognizer.Matcher: " + s);
    }

    private void doNotMatch(Recognizer.Matcher matcher, String s) {
        assertTrue(!matcher.matches(s), "Recognizer.Matcher: " + s);
    }
    //
    //////////////////////////////////////////////////////////////////////////
}
