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
package org.eclipse.daanse.rolap.aggmatch.jaxb;

import java.util.List;

import org.eclipse.daanse.rolap.common.aggmatcher.Recognizer;

/**
 * This is the template that maps from a combination of level
 * usage_prefix
 * hierarchy_name
 * level_name
 * level_column_name
 */
public class LevelMap extends RegexMapper {

    private static final List<String> TEMPLATE_NAMES = List.of(
        "usage_prefix",
        "hierarchy_name",
        "level_name",
        "level_column_name"
    );

    protected List<String> getTemplateNames() {
        return TEMPLATE_NAMES;
    }

    public Recognizer.Matcher getMatcher(
        final String usagePrefix,
        final String hierarchyName,
        final String levelName,
        final String levelColumnName
    ) {
        return getMatcher(new String[]{
            usagePrefix,
            hierarchyName,
            levelName,
            levelColumnName
        });
    }

    @Override
    protected String getName() {
        return "LevelMap";
    }
}
