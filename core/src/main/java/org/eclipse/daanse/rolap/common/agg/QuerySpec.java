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
 * jhyde, 30 August, 2001
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


package org.eclipse.daanse.rolap.common.agg;

import java.util.List;

import org.eclipse.daanse.jdbc.db.dialect.api.BestFitColumnType;
import  org.eclipse.daanse.olap.util.Pair;
import org.eclipse.daanse.rolap.common.RolapStar;
import org.eclipse.daanse.rolap.common.StarColumnPredicate;

/**
 * Contains the information necessary to generate a SQL statement to
 * retrieve a set of cells.
 *
 * @author jhyde
 * @author Richard M. Emberson
 */
public interface QuerySpec {
    RolapStar getStar();
    int getMeasureCount();
    RolapStar.Measure getMeasure(int i);
    String getMeasureAlias(int i);
    RolapStar.Column[] getColumns();
    String getColumnAlias(int i);

    /**
     * Returns the predicate on the ith column.
     *
     * If the column is unconstrained, returns
     * {@link LiteralStarPredicate}(true).
     *
     * @param i Column ordinal
     * @return Constraint on column
     */
    StarColumnPredicate getColumnPredicate(int i);

    Pair<String, List<BestFitColumnType>> generateSqlQuery();
}
