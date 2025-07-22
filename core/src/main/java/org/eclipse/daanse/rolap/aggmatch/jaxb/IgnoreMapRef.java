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

public class IgnoreMapRef extends Ref {

    public void validate(
        final AggRules rules,
        final org.eclipse.daanse.rolap.recorder.MessageRecorder msgRecorder
    ) {
        msgRecorder.pushContextName(getName());
        try {
            if (!rules.hasIgnoreMap(getRefId())) {
                String msg = "No IgnoreMap has id equal to refid \"" +
                    getRefId() +
                    "\"";
                msgRecorder.reportError(msg);
            }
        } finally {
            msgRecorder.popContextName();
        }
    }

    @Override
    protected String getName() {
        return "IgnoreMapRef";
    }
}
