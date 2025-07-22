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
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.rolap.common;

import javax.sql.DataSource;

public record ConnectionKey(int dataSourceIdentityHashCode, String sessionId) {

	static ConnectionKey of(DataSource dataSource, String sessionId) {
		return new ConnectionKey(System.identityHashCode(dataSource), sessionId);
	}

}
