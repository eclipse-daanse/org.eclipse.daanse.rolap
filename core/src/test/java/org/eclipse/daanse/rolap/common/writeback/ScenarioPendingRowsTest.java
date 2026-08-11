/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
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
package org.eclipse.daanse.rolap.common.writeback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.AbstractMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.daanse.olap.api.DataTypeJdbc;
import org.eclipse.daanse.olap.api.element.Cube;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * That a pending writeback row stays with the cube it was produced for.
 * <p>
 * The rows used to be kept in one flat list. Two things went wrong with that: a
 * commit handed every cube in the catalog the whole list, so a second writeback
 * cube received rows meant for the first; and the fact rewrite that makes
 * uncommitted values visible fed a cube rows describing columns it does not
 * have, which corrupted what-if answers before anything was committed.
 */
class ScenarioPendingRowsTest {

    private ScenarioImpl scenario;
    private Cube budget;
    private Cube forecast;

    @BeforeEach
    void wire() {
        scenario = new ScenarioImpl();
        budget = mock(Cube.class);
        forecast = mock(Cube.class);
    }

    private static List<Map<String, Map.Entry<DataTypeJdbc, Object>>> row(String column, Object value) {
        Map<String, Map.Entry<DataTypeJdbc, Object>> row = new LinkedHashMap<>();
        row.put(column, new AbstractMap.SimpleEntry<>(DataTypeJdbc.VARCHAR, value));
        return List.of(row);
    }

    @Test
    void aRowIsPendingOnlyForItsOwnCube() {
        scenario.addPendingRows(budget, row("AMOUNT", "10"));

        assertThat(scenario.pendingRows(budget)).hasSize(1);
        assertThat(scenario.pendingRows(forecast)).isEmpty();
    }

    @Test
    void aCubeNothingIsPendingForAnswersEmptyRatherThanNull() {
        assertThat(scenario.pendingRows(forecast)).isEmpty();
    }

    @Test
    void everyCubeWithSomethingPendingIsNamedOnce() {
        scenario.addPendingRows(budget, row("AMOUNT", "10"));
        scenario.addPendingRows(budget, row("AMOUNT", "20"));
        scenario.addPendingRows(forecast, row("AMOUNT", "30"));

        assertThat(scenario.pendingCubes()).containsExactlyInAnyOrder(budget, forecast);
        assertThat(scenario.pendingRows(budget)).hasSize(2);
        assertThat(scenario.pendingRows(forecast)).hasSize(1);
    }

    @Test
    void rowsKeepTheOrderTheyWereProducedIn() {
        scenario.addPendingRows(budget, row("AMOUNT", "first"));
        scenario.addPendingRows(budget, row("AMOUNT", "second"));

        assertThat(scenario.pendingRows(budget)).extracting(each -> each.get("AMOUNT").getValue())
                .containsExactly("first", "second");
    }

    @Test
    void addingNothingLeavesTheCubeUnmentioned() {
        scenario.addPendingRows(budget, List.of());
        scenario.addPendingRows(forecast, null);

        assertThat(scenario.pendingCubes()).isEmpty();
    }

    @Test
    void clearingForgetsEveryCube() {
        scenario.addPendingRows(budget, row("AMOUNT", "10"));
        scenario.addPendingRows(forecast, row("AMOUNT", "20"));

        scenario.clear();

        assertThat(scenario.pendingCubes()).isEmpty();
        assertThat(scenario.pendingRows(budget)).isEmpty();
        assertThat(scenario.getWritebackCells()).isEmpty();
    }
}
