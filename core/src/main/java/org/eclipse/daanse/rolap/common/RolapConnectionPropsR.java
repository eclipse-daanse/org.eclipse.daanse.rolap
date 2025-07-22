/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation.
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

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.eclipse.daanse.olap.api.ConnectionProps;

public record RolapConnectionPropsR(List<String> roles, boolean useSchemaPool, Locale locale, Duration pinSchemaTimeout,
		 Optional<String> aggregateScanSchema, Optional<String> aggregateScanCatalog)
		implements ConnectionProps {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(-1);

	public RolapConnectionPropsR() {
		this(List.of(), true, Locale.getDefault(), DEFAULT_TIMEOUT, Optional.empty(), Optional.empty());
	}

    public RolapConnectionPropsR(List<String> roles) {
        this(roles, true, Locale.getDefault(), DEFAULT_TIMEOUT, Optional.empty(), Optional.empty());
    }
}
