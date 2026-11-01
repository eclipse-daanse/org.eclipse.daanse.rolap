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
package org.eclipse.daanse.rolap.aggregator.extra;

import org.eclipse.daanse.rolap.aggregator.SqlNodeAggregator;

/** The OLAP none-aggregator with the SQL rendering of rolap. */
public class NoneAggregator extends org.eclipse.daanse.olap.aggregator.NoneAggregator implements SqlNodeAggregator {

    public static final NoneAggregator INSTANCE = new NoneAggregator();
}
