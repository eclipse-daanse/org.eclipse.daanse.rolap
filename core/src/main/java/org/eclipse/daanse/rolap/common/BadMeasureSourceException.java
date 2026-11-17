/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation.
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

import java.text.MessageFormat;

import org.eclipse.daanse.olap.api.exception.OlapRuntimeException;

@SuppressWarnings("serial")
public class BadMeasureSourceException extends OlapRuntimeException {

	private final static String message = "Cube ''{0}'': Measure ''{1}'' must contain either a source column or a source expression, but not both";

	public BadMeasureSourceException(String cubeName, String measureName) {
		super(MessageFormat.format(message, cubeName, measureName));
	}
}
