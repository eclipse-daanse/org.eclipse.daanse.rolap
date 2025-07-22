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

/**
 * This is a base class for all elements that can match strings
 * where the case of the string is important. In addition,
 * it has an id which services as its tag.
 */
//@XmlType(name = "")
public abstract class CaseMatcher extends Base {

    /**
     * The unique identifier for this Matcher.
     */
    protected String id;

    /**
     * How should the case of the item being matched be treated.
     * If "ignore" then any combination of the source string
     * where the characters are upper or lower case will match
     * a target string.
     * If "exact" then the exact match is made.
     * If "upper" then all characters must be upper-case.
     * If "lower" then all characters must be lower-case.
     */
    protected CharCaseEnum charCase = CharCaseEnum.IGNORE;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public CharCaseEnum getCharCase() {
        return charCase;
    }

    public void setCharCase(CharCaseEnum charCase) {
        this.charCase = charCase;
    }

    public void validate(
        final AggRules rules,
        final org.eclipse.daanse.rolap.recorder.MessageRecorder msgRecorder
    ) {
        // empty
    }

    protected String getTag() {
        return getId();
    }

    @Override
    protected String getName() {
        return "CaseMatcher";
    }
}
