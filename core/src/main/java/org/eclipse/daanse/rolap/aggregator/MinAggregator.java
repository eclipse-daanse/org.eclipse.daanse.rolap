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

/** The OLAP minimum, rendered as SQL {@code min(operand)}. */
public class MinAggregator extends org.eclipse.daanse.olap.aggregator.MinAggregator implements SqlNodeAggregator {

    public static final MinAggregator INSTANCE = new MinAggregator();
}
