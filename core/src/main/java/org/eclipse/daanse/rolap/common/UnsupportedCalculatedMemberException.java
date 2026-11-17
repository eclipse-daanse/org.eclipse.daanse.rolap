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
public class UnsupportedCalculatedMemberException extends OlapRuntimeException {
	private final static String unsupportedCalculatedMember = "Calculated member ''{0}'' is not supported within a compound predicate";

	public UnsupportedCalculatedMemberException(String name) {
		super(MessageFormat.format(unsupportedCalculatedMember, name));
	}
}
