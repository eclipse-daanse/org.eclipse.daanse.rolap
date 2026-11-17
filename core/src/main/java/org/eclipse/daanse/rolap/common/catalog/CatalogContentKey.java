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
package org.eclipse.daanse.rolap.common.catalog;

import java.util.HexFormat;

/**
 * Content part of the catalog cache key: name plus the supplier's SHA-256
 * content identity, so content-equal mappings share one pool entry and a
 * changed mapping misses it.
 */
public record CatalogContentKey(String catalogName, String contentFingerprint) {
	public static CatalogContentKey of(String catalogName, byte[] sha256) {
		return new CatalogContentKey(catalogName, HexFormat.of().formatHex(sha256));
	}
}
