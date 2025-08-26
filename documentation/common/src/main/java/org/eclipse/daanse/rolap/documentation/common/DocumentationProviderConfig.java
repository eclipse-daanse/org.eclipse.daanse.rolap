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
package org.eclipse.daanse.rolap.documentation.common;

import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.osgi.service.metatype.annotations.AttributeDefinition;


@ObjectClassDefinition
public interface DocumentationProviderConfig {

    @AttributeDefinition(name = "writeSchemasDescribing", description = "write schemas describing property", required = false)
    default boolean writeSchemasDescribing() {
        return true;
    }

    @AttributeDefinition(name = "writeCubsDiagrams", description = "write cubs diagrams property", required = false)
    default boolean writeCubsDiagrams() {
        return true;
    }

    @AttributeDefinition(name = "writeCubeMatrixDiagram", description = "write cube matrix diagram property", required = false)
    default boolean writeCubeMatrixDiagram() {
        return true;

    }

    @AttributeDefinition(name = "writeDatabaseInfoDiagrams", description = "write database info diagrams property", required = false)
    default boolean writeDatabaseInfoDiagrams() {
        return true;
    }

    @AttributeDefinition(name = "writeVerifierResult", description = "write verifier result property", required = false)
    default boolean writeVerifierResult() {
        return true;
    }

    @AttributeDefinition(name = "writeSchemasAsXML", description = "write schemas as XML property", required = false)
    default boolean writeSchemasAsXML() {
        return true;
    }
}
