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

import org.eclipse.daanse.sql.statement.api.expression.SqlExpression;

public abstract class AbstractAggregator extends org.eclipse.daanse.olap.aggregator.AbstractAggregator
        implements SqlNodeAggregator {

    protected AbstractAggregator(String name, boolean distinct) {
        super(name, distinct);
    }

    protected static SqlExpression nodeOperand(SqlExpression inner) {
        return SqlNodeAggregator.nodeOperand(inner);
    }
}
