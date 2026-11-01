/*
* Copyright (c) 2025 Contributors to the Eclipse Foundation.
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
*
* Contributors:
*   SmartCity Jena - initial
*/
package org.eclipse.daanse.rolap.aggregator;

import org.eclipse.daanse.olap.api.aggregator.Aggregator;

/** The OLAP count, rendered as SQL {@code count(operand)}; rolls up as the SQL-rendering sum. */
public class CountAggregator extends org.eclipse.daanse.olap.aggregator.CountAggregator implements SqlNodeAggregator {

    public static final CountAggregator INSTANCE = new CountAggregator();

    @Override
    public Aggregator getRollup() {
        return SumAggregator.INSTANCE;
    }
}
