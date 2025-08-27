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
*/
package org.eclipse.daanse.rolap.documentation.api;

import java.nio.file.Path;

import org.eclipse.daanse.olap.api.Context;

public interface ContextDocumentationProvider {

	void createDocumentation(Context<?> context, Path path) throws Exception;
}
