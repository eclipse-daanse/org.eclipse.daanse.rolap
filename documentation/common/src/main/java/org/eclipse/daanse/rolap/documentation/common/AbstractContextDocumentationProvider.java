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
package org.eclipse.daanse.rolap.documentation.common;

import org.eclipse.daanse.rolap.documentation.api.ConntextDocumentationProvider;

import java.sql.Types;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public abstract class AbstractContextDocumentationProvider implements ConntextDocumentationProvider {

    protected static final Map<Integer, String> TYPE_MAP;
    static {
        Map<Integer, String> typeMapInitial = new HashMap<>();
        typeMapInitial.put(Types.SMALLINT, "int");
        typeMapInitial.put(Types.INTEGER, "int");
        typeMapInitial.put(Types.BOOLEAN, "boolean");
        typeMapInitial.put(Types.DOUBLE, "double");
        typeMapInitial.put(Types.FLOAT, "double");
        typeMapInitial.put(Types.BIGINT, "long");
        typeMapInitial.put(Types.DATE, "date");
        typeMapInitial.put(Types.TIMESTAMP, "timestamp");
        typeMapInitial.put(Types.TIME, "time");
        typeMapInitial.put(Types.VARCHAR, "string");
        typeMapInitial.put(Types.DECIMAL, "double");
        typeMapInitial.put(Types.NUMERIC, "double");
        typeMapInitial.put(Types.REAL, "double");

        TYPE_MAP = Collections.unmodifiableMap(typeMapInitial);
    }
}
