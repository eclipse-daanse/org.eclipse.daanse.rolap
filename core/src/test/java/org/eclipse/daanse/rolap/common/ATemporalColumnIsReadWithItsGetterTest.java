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
package org.eclipse.daanse.rolap.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.Timestamp;

import org.eclipse.daanse.sql.model.type.BestFitColumnType;
import org.junit.jupiter.api.Test;

/**
 * A date or timestamp column is read with its own getter: getObject hands over the driver's class, and Oracle's
 * {@code oracle.sql.TIMESTAMP} is neither formatted nor compared by the engine.
 */
class ATemporalColumnIsReadWithItsGetterTest {

    /** What a driver's own timestamp class looks like to the engine: an object it does not know. */
    private static final Object DRIVER_TIMESTAMP = new Object() {
        @Override
        public String toString() {
            return "2005-05-29 00:00:00.0";
        }
    };

    @Test
    void aTimestampColumnGivesATimestamp() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        Timestamp timestamp = Timestamp.valueOf("2005-05-29 00:00:00");
        when(rs.getObject(1)).thenReturn(DRIVER_TIMESTAMP);
        when(rs.getTimestamp(1)).thenReturn(timestamp);

        assertThat(SqlStatement.accessor(rs, 0, BestFitColumnType.TIMESTAMP).get()).isEqualTo(timestamp);
        assertThat(SqlStatement.temporal(rs, 1, BestFitColumnType.TIMESTAMP)).isEqualTo(timestamp);
    }

    @Test
    void aDateColumnGivesADate() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        Date date = Date.valueOf("2008-04-29");
        when(rs.getObject(1)).thenReturn(date.toLocalDate());
        when(rs.getDate(1)).thenReturn(date);

        assertThat(SqlStatement.accessor(rs, 0, BestFitColumnType.DATE).get()).isEqualTo(date);
    }

    @Test
    void aNullTemporalValueStaysNull() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        assertThat(SqlStatement.accessor(rs, 0, BestFitColumnType.TIMESTAMP).get()).isNull();
        assertThat(SqlStatement.accessor(rs, 0, BestFitColumnType.DATE).get()).isNull();
    }
}
