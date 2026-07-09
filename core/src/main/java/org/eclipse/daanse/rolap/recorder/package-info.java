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
/**
 * Message recorders ({@link MessageRecorder} and its {@link ListRecorder} implementation) that
 * accumulate error, warning and info messages while a model is validated. Exported only so the
 * core test fragment can reference {@code MessageRecorder}; it has no other external consumers.
 */
@org.osgi.annotation.bundle.Export
@org.osgi.annotation.versioning.Version("0.0.1")
package org.eclipse.daanse.rolap.recorder;