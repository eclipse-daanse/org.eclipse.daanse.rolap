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
package org.eclipse.daanse.rolap.core.api;

public class Constants {
    private Constants() {
    }

    public static final String BASIC_CONTEXT_PID = "org.eclipse.daanse.rolap.core.BasicContext";

    public static final String BASIC_CONTEXT_REF_NAME_DIALECT_FACTORY = "dialectFactory";
    public static final String BASIC_CONTEXT_REF_NAME_DATA_SOURCE = "dataSource";
    public static final String BASIC_CONTEXT_REF_NAME_CATALOG_MAPPING_SUPPLIER = "catalogMappingSuppier";
    public static final String BASIC_CONTEXT_REF_NAME_ROLAP_CONTEXT_MAPPING_SUPPLIER = "rolapContextMappingSuppliers";
    public static final String BASIC_CONTEXT_REF_NAME_MDX_PARSER_PROVIDER = "mdxParserProvider";
    public static final String BASIC_CONTEXT_REF_NAME_EXPRESSION_COMPILER_FACTORY = "expressionCompilerFactory";
    public static final String BASIC_CONTEXT_REF_NAME_CUSTOM_AGGREGATOR = "customAggregator";

}
