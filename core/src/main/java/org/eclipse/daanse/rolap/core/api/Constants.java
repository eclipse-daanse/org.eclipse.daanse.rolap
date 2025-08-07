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

    public static final String BASIC_CONTEXT_PID = "daanse.rolap.core.BasicContext";

    public static final String BASIC_CONTEXT_REF_NAME_DIALECT_FACTORY = "dialectFactory";
    public static final String BASIC_CONTEXT_REF_NAME_DATA_SOURCE = "dataSource";
    public static final String BASIC_CONTEXT_REF_NAME_CATALOG_MAPPING_SUPPLIER = "catalogMappingSuppier";
    public static final String BASIC_CONTEXT_REF_NAME_ROLAP_CONTEXT_MAPPING_SUPPLIER = "rolapContextMappingSuppliers";
    public static final String BASIC_CONTEXT_REF_NAME_MDX_PARSER_PROVIDER = "mdxParserProvider";
    public static final String BASIC_CONTEXT_REF_NAME_EXPRESSION_COMPILER_FACTORY = "expressionCompilerFactory";
    public static final String BASIC_CONTEXT_REF_NAME_FUNCTION_SERVICE = "functionService";
    public static final String BASIC_CONTEXT_REF_NAME_SQL_GUARD_FACTORY = "sqlGuardFactory";
    public static final String BASIC_CONTEXT_REF_NAME_CUSTOM_AGGREGATOR = "customAggregator";

    // OCD related constants
    public static final String OCD_LOCALIZATION_PATH = "OSGI-INF/l10n/org.eclipse.daanse.rolap.core.ocd";

    // Target filter property names for OCD
    public static final String BASIC_CONTEXT_TARGET_DATA_SOURCE = BASIC_CONTEXT_REF_NAME_DATA_SOURCE + "_target";
    public static final String BASIC_CONTEXT_TARGET_DIALECT_FACTORY = BASIC_CONTEXT_REF_NAME_DIALECT_FACTORY
            + "_target";
    public static final String BASIC_CONTEXT_TARGET_CATALOG_MAPPING_SUPPLIER = BASIC_CONTEXT_REF_NAME_CATALOG_MAPPING_SUPPLIER
            + "_target";
    public static final String BASIC_CONTEXT_TARGET_EXPRESSION_COMPILER_FACTORY = BASIC_CONTEXT_REF_NAME_EXPRESSION_COMPILER_FACTORY
            + "_target";
    public static final String BASIC_CONTEXT_TARGET_FUNCTION_SERVICE = BASIC_CONTEXT_REF_NAME_FUNCTION_SERVICE
            + "_target";
    public static final String BASIC_CONTEXT_TARGET_SQL_GUARD_FACTORY = BASIC_CONTEXT_REF_NAME_SQL_GUARD_FACTORY
            + "_target";
    public static final String BASIC_CONTEXT_TARGET_MDX_PARSER_PROVIDER = BASIC_CONTEXT_REF_NAME_MDX_PARSER_PROVIDER
            + "_target";
    public static final String BASIC_CONTEXT_TARGET_CUSTOM_AGGREGATOR = BASIC_CONTEXT_REF_NAME_CUSTOM_AGGREGATOR
            + "_target";

}
