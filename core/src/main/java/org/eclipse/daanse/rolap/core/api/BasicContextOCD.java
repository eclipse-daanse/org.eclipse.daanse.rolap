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
package org.eclipse.daanse.rolap.core.api;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(name = BasicContextOCD.L10N_OCD_NAME, description = BasicContextOCD.L10N_OCD_DESCRIPTION, localization = BasicContextOCD.OCD_LOCALIZATION)
public interface BasicContextOCD {

    public static final String TARGET_FILTER_DAANSE_IDENT = "(daanse.ident=*)";
    public static final String TARGET_FILTER_COMPONET_ANY = "(service.name=*)";

    String OCD_LOCALIZATION = "OSGI-INF/l10n/org.eclipse.daanse.rolap.core.ocd";

    String L10N_PREFIX = "%";
    String L10N_POSTFIX_DESCRIPTION = ".description";
    String L10N_POSTFIX_NAME = ".name";
    String L10N_POSTFIX_OPTION = ".option";
    String L10N_POSTFIX_LABEL = ".label";

    String L10N_OCD_NAME = L10N_PREFIX + "ocd" + L10N_POSTFIX_NAME;
    String L10N_OCD_DESCRIPTION = L10N_PREFIX + "ocd" + L10N_POSTFIX_DESCRIPTION;

    // Target filter localization constants
    String L10N_DATA_SOURCE_TARGET_NAME = L10N_PREFIX + Constants.BASIC_CONTEXT_TARGET_DATA_SOURCE + L10N_POSTFIX_NAME;
    String L10N_DATA_SOURCE_TARGET_DESCRIPTION = L10N_PREFIX + Constants.BASIC_CONTEXT_TARGET_DATA_SOURCE
            + L10N_POSTFIX_DESCRIPTION;

    String L10N_DIALECT_FACTORY_TARGET_NAME = L10N_PREFIX + Constants.BASIC_CONTEXT_TARGET_DIALECT_FACTORY
            + L10N_POSTFIX_NAME;
    String L10N_DIALECT_FACTORY_TARGET_DESCRIPTION = L10N_PREFIX + Constants.BASIC_CONTEXT_TARGET_DIALECT_FACTORY
            + L10N_POSTFIX_DESCRIPTION;

    String L10N_CATALOG_MAPPING_SUPPLIER_TARGET_NAME = L10N_PREFIX
            + Constants.BASIC_CONTEXT_TARGET_CATALOG_MAPPING_SUPPLIER + L10N_POSTFIX_NAME;
    String L10N_CATALOG_MAPPING_SUPPLIER_TARGET_DESCRIPTION = L10N_PREFIX
            + Constants.BASIC_CONTEXT_TARGET_CATALOG_MAPPING_SUPPLIER + L10N_POSTFIX_DESCRIPTION;

    String L10N_EXPRESSION_COMPILER_FACTORY_TARGET_NAME = L10N_PREFIX
            + Constants.BASIC_CONTEXT_TARGET_EXPRESSION_COMPILER_FACTORY + L10N_POSTFIX_NAME;
    String L10N_EXPRESSION_COMPILER_FACTORY_TARGET_DESCRIPTION = L10N_PREFIX
            + Constants.BASIC_CONTEXT_TARGET_EXPRESSION_COMPILER_FACTORY + L10N_POSTFIX_DESCRIPTION;

    String L10N_FUNCTION_SERVICE_TARGET_NAME = L10N_PREFIX + Constants.BASIC_CONTEXT_TARGET_FUNCTION_SERVICE
            + L10N_POSTFIX_NAME;
    String L10N_FUNCTION_SERVICE_TARGET_DESCRIPTION = L10N_PREFIX + Constants.BASIC_CONTEXT_TARGET_FUNCTION_SERVICE
            + L10N_POSTFIX_DESCRIPTION;

    String L10N_SQL_GUARD_FACTORY_TARGET_NAME = L10N_PREFIX + Constants.BASIC_CONTEXT_TARGET_SQL_GUARD_FACTORY
            + L10N_POSTFIX_NAME;
    String L10N_SQL_GUARD_FACTORY_TARGET_DESCRIPTION = L10N_PREFIX + Constants.BASIC_CONTEXT_TARGET_SQL_GUARD_FACTORY
            + L10N_POSTFIX_DESCRIPTION;

    String L10N_MDX_PARSER_PROVIDER_TARGET_NAME = L10N_PREFIX + Constants.BASIC_CONTEXT_TARGET_MDX_PARSER_PROVIDER
            + L10N_POSTFIX_NAME;
    String L10N_MDX_PARSER_PROVIDER_TARGET_DESCRIPTION = L10N_PREFIX
            + Constants.BASIC_CONTEXT_TARGET_MDX_PARSER_PROVIDER + L10N_POSTFIX_DESCRIPTION;

    String L10N_CUSTOM_AGGREGATOR_TARGET_NAME = L10N_PREFIX + Constants.BASIC_CONTEXT_TARGET_CUSTOM_AGGREGATOR
            + L10N_POSTFIX_NAME;
    String L10N_CUSTOM_AGGREGATOR_TARGET_DESCRIPTION = L10N_PREFIX + Constants.BASIC_CONTEXT_TARGET_CUSTOM_AGGREGATOR
            + L10N_POSTFIX_DESCRIPTION;

    // Target filter methods
    @AttributeDefinition(name = L10N_DATA_SOURCE_TARGET_NAME, description = L10N_DATA_SOURCE_TARGET_DESCRIPTION, defaultValue = TARGET_FILTER_DAANSE_IDENT)
    default String dataSource_target() {
        return TARGET_FILTER_DAANSE_IDENT;
    }

    @AttributeDefinition(name = L10N_DIALECT_FACTORY_TARGET_NAME, description = L10N_DIALECT_FACTORY_TARGET_DESCRIPTION, defaultValue = TARGET_FILTER_DAANSE_IDENT)
    default String dialectFactory_target() {
        return TARGET_FILTER_DAANSE_IDENT;
    }

    @AttributeDefinition(name = L10N_CATALOG_MAPPING_SUPPLIER_TARGET_NAME, description = L10N_CATALOG_MAPPING_SUPPLIER_TARGET_DESCRIPTION, defaultValue = TARGET_FILTER_DAANSE_IDENT)
    default String catalogMappingSuppier_target() {
        return TARGET_FILTER_DAANSE_IDENT;
    }

    @AttributeDefinition(name = L10N_EXPRESSION_COMPILER_FACTORY_TARGET_NAME, description = L10N_EXPRESSION_COMPILER_FACTORY_TARGET_DESCRIPTION, defaultValue = TARGET_FILTER_DAANSE_IDENT)
    default String expressionCompilerFactory_target() {
        return TARGET_FILTER_DAANSE_IDENT;
    }

    @AttributeDefinition(name = L10N_FUNCTION_SERVICE_TARGET_NAME, description = L10N_FUNCTION_SERVICE_TARGET_DESCRIPTION, defaultValue = TARGET_FILTER_DAANSE_IDENT)
    default String functionService_target() {
        return TARGET_FILTER_DAANSE_IDENT;
    }

    @AttributeDefinition(name = L10N_SQL_GUARD_FACTORY_TARGET_NAME, description = L10N_SQL_GUARD_FACTORY_TARGET_DESCRIPTION, defaultValue = TARGET_FILTER_DAANSE_IDENT)
    default String sqlGuardFactory_target() {
        return TARGET_FILTER_DAANSE_IDENT;
    }

    @AttributeDefinition(name = L10N_MDX_PARSER_PROVIDER_TARGET_NAME, description = L10N_MDX_PARSER_PROVIDER_TARGET_DESCRIPTION, defaultValue = TARGET_FILTER_DAANSE_IDENT)
    default String mdxParserProvider_target() {
        return TARGET_FILTER_DAANSE_IDENT;
    }

    @AttributeDefinition(name = L10N_CUSTOM_AGGREGATOR_TARGET_NAME, description = L10N_CUSTOM_AGGREGATOR_TARGET_DESCRIPTION, defaultValue = TARGET_FILTER_DAANSE_IDENT)
    default String customAggregator_target() {
        return TARGET_FILTER_DAANSE_IDENT;
    }

}
