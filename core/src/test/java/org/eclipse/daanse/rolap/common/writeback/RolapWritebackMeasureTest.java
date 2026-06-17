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
import static org.mockito.Mockito.when;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.util.resource.relational.SqlSimpleTypes;
import org.eclipse.daanse.jdbc.db.dialect.api.type.Datatype;
import org.eclipse.daanse.olap.api.element.Member;
import org.junit.jupiter.api.Test;

/** Unit tests for the datatype plumbing on {@link RolapWritebackMeasure}. */
class RolapWritebackMeasureTest {

    @Test void carriesNumericDatatype() {
        Column column = mock(Column.class);
        when(column.getName()).thenReturn("AMOUNT");
        when(column.getType()).thenReturn(SqlSimpleTypes.Sql99.integerType());

        Member measure = mock(Member.class);

        RolapWritebackMeasure m = new RolapWritebackMeasure(measure, column, Datatype.NUMERIC, true);

        assertThat(m.getDatatype()).isEqualTo(Datatype.NUMERIC);
        assertThat(m.getMeasure()).isSameAs(measure);
        assertThat(m.getColumn().getName()).isEqualTo("AMOUNT");
    }

    @Test void carriesVarcharDatatypeForTextMeasures() {
        Column column = mock(Column.class);
        when(column.getName()).thenReturn("COMMENT");
        when(column.getType()).thenReturn(SqlSimpleTypes.Sql99.varcharType());
        Member measure = mock(Member.class);

        RolapWritebackMeasure m = new RolapWritebackMeasure(measure, column, Datatype.VARCHAR, true);

        assertThat(m.getDatatype()).isEqualTo(Datatype.VARCHAR);
    }
}
