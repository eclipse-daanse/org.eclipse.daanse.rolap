/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.rolap.aggmatch.emf;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.eclipse.daanse.rolap.aggmatch.aggmatch.AggMatchFactory;
import org.eclipse.daanse.rolap.aggmatch.aggmatch.AggMatchPackage;
import org.eclipse.daanse.rolap.aggmatch.aggmatch.AggregationCharacterCase;
import org.eclipse.daanse.rolap.aggmatch.aggmatch.AggregationFactCountMatch;
import org.eclipse.daanse.rolap.aggmatch.aggmatch.AggregationForeignKeyMatch;
import org.eclipse.daanse.rolap.aggmatch.aggmatch.AggregationIgnoreMap;
import org.eclipse.daanse.rolap.aggmatch.aggmatch.AggregationLevelMap;
import org.eclipse.daanse.rolap.aggmatch.aggmatch.AggregationMatchRegex;
import org.eclipse.daanse.rolap.aggmatch.aggmatch.AggregationMatchRule;
import org.eclipse.daanse.rolap.aggmatch.aggmatch.AggregationMatchRules;
import org.eclipse.daanse.rolap.aggmatch.aggmatch.AggregationMeasureMap;
import org.eclipse.daanse.rolap.aggmatch.aggmatch.AggregationTableMatch;
import org.eclipse.daanse.rolap.aggmatch.instance.basic.BasicAggMatchRulesSupplier;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EmfAggMatchXmiRoundtripTest {

    private static final AggMatchFactory F = AggMatchFactory.eINSTANCE;

    @BeforeAll
    static void registerEmf() {
        // Registration mutates the shared EPackage.Registry/Resource.Factory.Registry
        // singletons, but both puts are idempotent (same key -> same value each time),
        // so re-running this across test classes in the same JVM is harmless.
        org.eclipse.emf.ecore.EPackage.Registry.INSTANCE.put(AggMatchPackage.eNS_URI, AggMatchPackage.eINSTANCE);
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("xmi", new XMIResourceFactoryImpl());
    }

    // --- Each test generates its own XMI file, then loads it back and verifies ---

    @Test
    void minimalRulesRoundtrip(@TempDir Path tempDir) throws IOException {
        Path minimalRulesXmi = tempDir.resolve("minimal-rules.xmi");
        saveXmi(buildMinimalEmfRules(), minimalRulesXmi);

        assertThat(minimalRulesXmi).exists();
        assertThat(Files.size(minimalRulesXmi)).isGreaterThan(0);

        var emfRules = loadXmi(minimalRulesXmi);
        var result = EmfAggMatchConverter.convert(emfRules);

        assertThat(result.getAggregationRules()).hasSize(1);
        var rule = result.getAggregationRules().get(0);
        assertThat(rule.getTag()).isEqualTo("minimal-rule");
        assertThat(rule.getTableMatch().getId()).isEqualTo("tm1");
        assertThat(rule.getTableMatch().getPrefixRegex()).hasValue("agg_.+_");
        assertThat(rule.getFactCountMatch().getId()).isEqualTo("fc1");
        assertThat(rule.getForeignKeyMatch().getId()).isEqualTo("fk1");
        assertThat(rule.getLevelMap().getId()).isEqualTo("lm1");
        assertThat(rule.getLevelMap().getRegexes()).hasSize(1);
        assertThat(rule.getLevelMap().getRegexes().get(0).getTemplate()).isEqualTo("${level_column_name}");
        assertThat(rule.getMeasureMap().getId()).isEqualTo("mm1");
        assertThat(rule.getMeasureMap().getRegexes()).hasSize(1);
        assertThat(rule.getIgnoreMap()).isEmpty();
    }

    @Test
    void fullRulesRoundtrip(@TempDir Path tempDir) throws IOException {
        Path fullRulesXmi = tempDir.resolve("full-rules.xmi");
        saveXmi(buildFullEmfRules(), fullRulesXmi);

        assertThat(fullRulesXmi).exists();
        assertThat(Files.size(fullRulesXmi)).isGreaterThan(0);

        var emfRules = loadXmi(fullRulesXmi);
        var result = EmfAggMatchConverter.convert(emfRules);

        assertThat(result.getAggregationRules()).hasSize(1);
        var rule = result.getAggregationRules().get(0);
        assertThat(rule.getTag()).isEqualTo("full-rule");
        assertThat(rule.getEnabled()).hasValue(true);

        // TableMatch with basename
        assertThat(rule.getTableMatch().getId()).isEqualTo("tc");
        assertThat(rule.getTableMatch().getCharCase())
                .hasValue(org.eclipse.daanse.rolap.api.aggmatch.AggregationCharacterCase.EXACT);
        assertThat(rule.getTableMatch().getPrefixRegex()).hasValue("AGG_.+_");
        assertThat(rule.getTableMatch().getNameExtractRegex()).hasValue("RF_(.*)_TABLE");

        // FactCountMatch with custom name
        assertThat(rule.getFactCountMatch().getCharCase())
                .hasValue(org.eclipse.daanse.rolap.api.aggmatch.AggregationCharacterCase.UPPER);
        assertThat(rule.getFactCountMatch().getFactCountName()).hasValue("my_fact_count");

        // ForeignKeyMatch with posttemplate and basename
        assertThat(rule.getForeignKeyMatch().getSuffixRegex()).hasValue("_FK");
        assertThat(rule.getForeignKeyMatch().getNameExtractRegex()).hasValue("FK_(.*)");

        // LevelMap with custom space/dot
        assertThat(rule.getLevelMap().getRegexes()).hasSize(1);
        var lvlRegex = rule.getLevelMap().getRegexes().get(0);
        assertThat(lvlRegex.getSpace()).hasValue("_SP_");
        assertThat(lvlRegex.getDot()).hasValue("_DOT_");

        // MeasureMap
        assertThat(rule.getMeasureMap().getRegexes()).hasSize(2);

        // IgnoreMap
        assertThat(rule.getIgnoreMap()).isPresent();
        assertThat(rule.getIgnoreMap().get().getId()).isEqualTo("ig");
        assertThat(rule.getIgnoreMap().get().getRegexes()).hasSize(1);
        assertThat(rule.getIgnoreMap().get().getRegexes().get(0).getTemplate()).isEqualTo("tmp_.*");
    }

    @Test
    void defaultRulesRoundtripMatchesRecordDefault(@TempDir Path tempDir) throws IOException {
        Path defaultRulesXmi = tempDir.resolve("default-rules.xmi");
        saveXmi(buildDefaultEmfRules(), defaultRulesXmi);

        assertThat(defaultRulesXmi).exists();
        assertThat(Files.size(defaultRulesXmi)).isGreaterThan(0);

        var emfRules = loadXmi(defaultRulesXmi);
        var fromXmi = EmfAggMatchConverter.convert(emfRules);
        var fromRecord = new BasicAggMatchRulesSupplier().get();

        assertThat(fromXmi.getAggregationRules()).hasSize(1);
        var xRule = fromXmi.getAggregationRules().get(0);
        var rRule = fromRecord.getAggregationRules().get(0);

        // Same tag
        assertThat(xRule.getTag()).isEqualTo(rRule.getTag());

        // TableMatch
        assertThat(xRule.getTableMatch().getId()).isEqualTo(rRule.getTableMatch().getId());
        assertThat(xRule.getTableMatch().getPrefixRegex()).isEqualTo(rRule.getTableMatch().getPrefixRegex());
        assertThat(xRule.getTableMatch().getSuffixRegex()).isEqualTo(rRule.getTableMatch().getSuffixRegex());
        assertThat(xRule.getTableMatch().getNameExtractRegex()).isEqualTo(rRule.getTableMatch().getNameExtractRegex());

        // FactCountMatch
        assertThat(xRule.getFactCountMatch().getId()).isEqualTo(rRule.getFactCountMatch().getId());

        // ForeignKeyMatch
        assertThat(xRule.getForeignKeyMatch().getId()).isEqualTo(rRule.getForeignKeyMatch().getId());

        // LevelMap: same id, same regex count, same id/template per regex
        assertThat(xRule.getLevelMap().getId()).isEqualTo(rRule.getLevelMap().getId());
        assertThat(xRule.getLevelMap().getRegexes()).hasSameSizeAs(rRule.getLevelMap().getRegexes());
        for (int i = 0; i < xRule.getLevelMap().getRegexes().size(); i++) {
            assertThat(xRule.getLevelMap().getRegexes().get(i).getId())
                    .isEqualTo(rRule.getLevelMap().getRegexes().get(i).getId());
            assertThat(xRule.getLevelMap().getRegexes().get(i).getTemplate())
                    .isEqualTo(rRule.getLevelMap().getRegexes().get(i).getTemplate());
        }

        // MeasureMap: same id, same regex count, same id/template per regex
        assertThat(xRule.getMeasureMap().getId()).isEqualTo(rRule.getMeasureMap().getId());
        assertThat(xRule.getMeasureMap().getRegexes()).hasSameSizeAs(rRule.getMeasureMap().getRegexes());
        for (int i = 0; i < xRule.getMeasureMap().getRegexes().size(); i++) {
            assertThat(xRule.getMeasureMap().getRegexes().get(i).getId())
                    .isEqualTo(rRule.getMeasureMap().getRegexes().get(i).getId());
            assertThat(xRule.getMeasureMap().getRegexes().get(i).getTemplate())
                    .isEqualTo(rRule.getMeasureMap().getRegexes().get(i).getTemplate());
        }

        // Both have no ignoreMap
        assertThat(xRule.getIgnoreMap()).isEmpty();
        assertThat(rRule.getIgnoreMap()).isEmpty();
    }

    // --- XMI I/O helpers ---

    private static void saveXmi(AggregationMatchRules emfRules, Path file) throws IOException {
        ResourceSet rs = new ResourceSetImpl();
        URI uri = URI.createFileURI(file.toAbsolutePath().toString());
        Resource resource = rs.createResource(uri);
        resource.getContents().add(emfRules);
        resource.save(Map.of());
    }

    private static AggregationMatchRules loadXmi(Path file) throws IOException {
        ResourceSet rs = new ResourceSetImpl();
        URI uri = URI.createFileURI(file.toAbsolutePath().toString());
        Resource resource = rs.getResource(uri, true);
        resource.load(Map.of());
        return (AggregationMatchRules) resource.getContents().get(0);
    }

    // --- EMF model builders ---

    private static AggregationMatchRegex regex(String id, AggregationCharacterCase charCase, String template) {
        AggregationMatchRegex r = F.createAggregationMatchRegex();
        r.setId(id);
        r.setCharCase(charCase);
        r.setTemplate(template);
        return r;
    }

    private static AggregationMatchRegex regex(String id, AggregationCharacterCase charCase, String template,
            String space, String dot) {
        AggregationMatchRegex r = regex(id, charCase, template);
        r.setSpace(space);
        r.setDot(dot);
        return r;
    }

    private static AggregationMatchRules buildDefaultEmfRules() {
        AggregationTableMatch tm = F.createAggregationTableMatch();
        tm.setId("ta");
        tm.setPretemplate("agg_.+_");

        AggregationFactCountMatch fcm = F.createAggregationFactCountMatch();
        fcm.setId("fca");

        AggregationForeignKeyMatch fkm = F.createAggregationForeignKeyMatch();
        fkm.setId("fka");

        AggregationLevelMap lm = F.createAggregationLevelMap();
        lm.setId("lxx");
        lm.getRegexes()
                .addAll(List.of(regex("logical", AggregationCharacterCase.LOWER, "${hierarchy_name}_${level_name}"),
                        regex("mixed", AggregationCharacterCase.LOWER, "${hierarchy_name}_${level_column_name}"),
                        regex("usage", AggregationCharacterCase.EXACT, "${usage_prefix}${level_column_name}"),
                        regex("physical", AggregationCharacterCase.EXACT, "${level_column_name}")));

        AggregationMeasureMap mm = F.createAggregationMeasureMap();
        mm.setId("mxx");
        mm.getRegexes()
                .addAll(List.of(regex("logical", AggregationCharacterCase.LOWER, "${measure_name}"),
                        regex("foreignkey", AggregationCharacterCase.EXACT, "${measure_column_name}"),
                        regex("physical", AggregationCharacterCase.EXACT, "${measure_column_name}_${aggregate_name}")));

        AggregationMatchRule rule = F.createAggregationMatchRule();
        rule.setTag("default");
        rule.setTableMatch(tm);
        rule.setFactCountMatch(fcm);
        rule.setForeignKeyMatch(fkm);
        rule.setLevelMap(lm);
        rule.setMeasureMap(mm);

        AggregationMatchRules rules = F.createAggregationMatchRules();
        rules.setTag("default-config");
        rules.getAggregationRules().add(rule);
        return rules;
    }

    private static AggregationMatchRules buildMinimalEmfRules() {
        AggregationTableMatch tm = F.createAggregationTableMatch();
        tm.setId("tm1");
        tm.setPretemplate("agg_.+_");

        AggregationFactCountMatch fcm = F.createAggregationFactCountMatch();
        fcm.setId("fc1");

        AggregationForeignKeyMatch fkm = F.createAggregationForeignKeyMatch();
        fkm.setId("fk1");

        AggregationLevelMap lm = F.createAggregationLevelMap();
        lm.setId("lm1");
        lm.getRegexes().add(regex("r1", AggregationCharacterCase.EXACT, "${level_column_name}"));

        AggregationMeasureMap mm = F.createAggregationMeasureMap();
        mm.setId("mm1");
        mm.getRegexes().add(regex("r2", AggregationCharacterCase.EXACT, "${measure_column_name}"));

        AggregationMatchRule rule = F.createAggregationMatchRule();
        rule.setTag("minimal-rule");
        rule.setTableMatch(tm);
        rule.setFactCountMatch(fcm);
        rule.setForeignKeyMatch(fkm);
        rule.setLevelMap(lm);
        rule.setMeasureMap(mm);

        AggregationMatchRules rules = F.createAggregationMatchRules();
        rules.setTag("minimal");
        rules.getAggregationRules().add(rule);
        return rules;
    }

    private static AggregationMatchRules buildFullEmfRules() {
        AggregationTableMatch tm = F.createAggregationTableMatch();
        tm.setId("tc");
        tm.setCharCase(AggregationCharacterCase.EXACT);
        tm.setPretemplate("AGG_.+_");
        tm.setBasename("RF_(.*)_TABLE");

        AggregationFactCountMatch fcm = F.createAggregationFactCountMatch();
        fcm.setId("fcc");
        fcm.setCharCase(AggregationCharacterCase.UPPER);
        fcm.setFactCountName("my_fact_count");

        AggregationForeignKeyMatch fkm = F.createAggregationForeignKeyMatch();
        fkm.setId("fkc");
        fkm.setCharCase(AggregationCharacterCase.EXACT);
        fkm.setPosttemplate("_FK");
        fkm.setBasename("FK_(.*)");

        AggregationLevelMap lm = F.createAggregationLevelMap();
        lm.setId("lc");
        lm.getRegexes().add(regex("usage", AggregationCharacterCase.LOWER,
                "${usage_prefix}${hierarchy_name}_${level_name}_${level_column_name}", "_SP_", "_DOT_"));

        AggregationMeasureMap mm = F.createAggregationMeasureMap();
        mm.setId("mc");
        mm.getRegexes().addAll(List.of(regex("logical", AggregationCharacterCase.LOWER, "${measure_name}"),
                regex("physical", AggregationCharacterCase.EXACT, "${measure_column_name}_${aggregate_name}")));

        AggregationIgnoreMap im = F.createAggregationIgnoreMap();
        im.setId("ig");
        im.getRegexes().add(regex("skip", AggregationCharacterCase.IGNORE, "tmp_.*"));

        AggregationMatchRule rule = F.createAggregationMatchRule();
        rule.setTag("full-rule");
        rule.setEnabled(true);
        rule.setTableMatch(tm);
        rule.setFactCountMatch(fcm);
        rule.setForeignKeyMatch(fkm);
        rule.setLevelMap(lm);
        rule.setMeasureMap(mm);
        rule.setIgnoreMap(im);

        AggregationMatchRules rules = F.createAggregationMatchRules();
        rules.setTag("full-config");
        rules.getAggregationRules().add(rule);
        return rules;
    }
}
