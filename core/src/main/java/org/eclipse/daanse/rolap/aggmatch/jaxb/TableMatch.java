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

import org.eclipse.daanse.rolap.common.aggmatcher.Recognizer;

/**
 * This is used to identify which tables in the database might
 * be aggregate table of a given fact table.
 * It is expected that aggregate table names will include the
 * base fact table name with additional text before and/or
 * after.
 * It is not allow for both the prepending and appending
 * regular expression text to be null (if it were, then only
 * aggregate tables who names were the same as (modulo case)
 * would match - which is surely not allowed).
 */
public class TableMatch extends NameMatcher {

    @Override
    public void validate(
        final AggRules rules,
        final org.eclipse.daanse.rolap.recorder.MessageRecorder msgRecorder
    ) {
        msgRecorder.pushContextName(getName());
        try {
            if ((getPretemplate() == null) && (posttemplate == null)) {
                String msg = "Must have at least one template non-null";
                msgRecorder.reportError(msg);
            }
            super.validate(rules, msgRecorder);
        } finally {
            msgRecorder.popContextName();
        }
    }

    @Override
    public Recognizer.Matcher getMatcher(final String name) {
        return super.getMatcher(name);
    }

    @Override
    protected String getName() {
        return "TableMatch";
    }
}
