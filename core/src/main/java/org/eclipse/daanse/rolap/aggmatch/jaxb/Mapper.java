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

import java.util.Arrays;

import org.eclipse.daanse.rolap.common.aggmatcher.Recognizer;

/**
 * This allows one to create an element that matches against a
 * single template, where the template is an attribute.
 * While much loved, this is currently not used.
 */
public abstract class Mapper extends CaseMatcher {

    /**
     * This is used by Elements to create a regex string.
     */
    String template;

    /**
     * How to translate the space character.
     * For example, if the space=='_' and
     * the source string is "Product Family",
     * then the target string is "Product_Family".
     */
    String space = "_";

    /**
     * How to
     * translate the
     * dot character.
     * For example, if
     * the dot=='_'and
     * the source
     * string is "Time.Time Weekly",
     * then the
     * target string
     * is "Time_Time Weekly".
     */
    String dot = "_";

    public String getTemplate() {
        return template;
    }

    public String getSpace() {
        return space;
    }

    public String getDot() {
        return dot;
    }

    protected static final int BAD_ID = -1;

    protected String[] templateParts;
    protected int[] templateNamePos;

    /**
     * It is hoped that no one will need to match more than 50 names
     * in a template. Currently, this implementation, requires only 3.
     */
    private static final int MAX_SIZE = 50;

    @Override
    public void validate(
        final AggRules rules,
        final org.eclipse.daanse.rolap.recorder.MessageRecorder msgRecorder
    ) {
        msgRecorder.pushContextName(getName());
        try {
            super.validate(rules, msgRecorder);

            String[] ss = new String[MAX_SIZE + 1];
            int[] poss = new int[MAX_SIZE];

            String templateInner = getTemplate();
            int count = 0;

            int end = 0;
            int previousEnd = 0;
            int start = templateInner.indexOf("${", end);
            while (count < MAX_SIZE) {
                if (start == -1) {
                    if (count == 0) {
                        // there are no ${} in template which
                        // is an error
                        String msg = "Bad template \"" +
                            templateInner +
                            "\", no ${} entries";
                        msgRecorder.reportError(msg);
                        return;
                    }
                    // its OK, there are "count" ${}
                    templateNamePos = new int[count];
                    System.arraycopy(poss, 0, templateNamePos, 0, count);

                    ss[count++] =
                        templateInner.substring(end, templateInner.length());
                    templateParts = new String[count];
                    System.arraycopy(ss, 0, templateParts, 0, count);

                    return;
                }

                previousEnd = end;
                end = templateInner.indexOf('}', start);
                if (end == -1) {
                    // there was a "${" but not '}' in template
                    String msg = "Bad template \"" +
                        templateInner +
                        "\", it had a \"${\", but no '}'";
                    msgRecorder.reportError(msg);
                    return;
                }

                String name = templateInner.substring(start + 2, end);
                int pos = convertNameToID(name, msgRecorder);
                if (pos == BAD_ID) {
                    return;
                }

                poss[count] = pos;
                ss[count] = templateInner.substring(previousEnd, start);

                start = templateInner.indexOf("${", end);
                end++;

                count++;
            }

        } finally {
            msgRecorder.popContextName();
        }
    }

    protected abstract String[] getTemplateNames();

    private int convertNameToID(
        final String name,
        final org.eclipse.daanse.rolap.recorder.MessageRecorder msgRecorder
    ) {

        if (name == null) {
            String msg = "Template name is null";
            msgRecorder.reportError(msg);
            return BAD_ID;
        }

        String[] names = getTemplateNames();
        for (int i = 0; i < names.length; i++) {
            if (names[i].equals(name)) {
                return i;
            }
        }

        String msg = "Bad template name \"" +
            name +
            "\"";
        msgRecorder.reportError(msg);
        return BAD_ID;
    }

    public String getRegex(final String[] names) {
        final String spaceInner = getSpace();
        final String dotInner = getDot();

        final StringBuilder buf = new StringBuilder();

        //
        // Remember that:
        //      templateParts.length == templateNamePos.length+1
        //
        buf.append(templateParts[0]);
        for (int i = 0; i < templateNamePos.length; i++) {
            String n = names[templateNamePos[i]];

            if (spaceInner != null) {
                n = n.replace(" ", spaceInner);
            }
            if (dotInner != null) {
                n = n.replace("\\.", dotInner);
            }

            buf.append(n);
            buf.append(templateParts[i + 1]);
        }

        String regex = buf.toString();

        if (AggRules.getLogger().isDebugEnabled()) {
            StringBuilder bf = new StringBuilder(64);
            bf.append(getName());
            bf.append(".getRegex:");
            bf.append(" for names ");
            for (int i = 0; i < names.length; i++) {
                bf.append('"');
                bf.append(names[i]);
                bf.append('"');
                if (i + 1 < names.length) {
                    bf.append(", ");
                }
            }
            bf.append(" regex is \"");
            bf.append(regex);
            bf.append('"');

            String msg = bf.toString();
            AggRules.getLogger().debug(msg);
        }
        return regex;
    }

    protected Recognizer.Matcher getMatcher(final String[] names) {

        final CharCaseEnum charcase = getCharCase();
        final String regex;
        int flag = 0;

        if (CharCaseEnum.IGNORE.equals(charcase)) {
            // the case of name does not matter
            // since the Pattern will be create to ignore case
            regex = getRegex(names);

            flag = java.util.regex.Pattern.CASE_INSENSITIVE;

        } else if (CharCaseEnum.EXACT.equals(charcase)) {
            // the case of name is not changed
            // since we are interested in exact case matching
            regex = getRegex(names);

        } else if (CharCaseEnum.UPPER.equals(charcase)) {
            // convert name to upper case
            String[] ucNames = Arrays.stream(names).map(String::toUpperCase).toArray(String[]::new);

            regex = getRegex(ucNames);

        } else {
            // lower
            // convert name to lower case
            String[] lcNames = Arrays.stream(names).map(String::toLowerCase).toArray(String[]::new);

            regex = getRegex(lcNames);

        }
        final java.util.regex.Pattern pattern =
            java.util.regex.Pattern.compile(regex, flag);

        return new Recognizer.Matcher() {
            public boolean matches(String name) {
                boolean b = pattern.matcher(name).matches();
                if (AggRules.getLogger().isDebugEnabled()) {
                    debug(name);
                }
                return b;
            }

            private void debug(String name) {
                StringBuilder bf = new StringBuilder(64);
                bf.append(Mapper.this.getName());
                bf.append(".Matcher.matches:");
                bf.append(" name \"");
                bf.append(name);
                bf.append("\" pattern \"");
                bf.append(pattern.pattern());
                bf.append("\"");
                if ((pattern.flags() &
                    java.util.regex.Pattern.CASE_INSENSITIVE) != 0) {
                    bf.append(" case_insensitive");
                }

                String msg = bf.toString();
                AggRules.getLogger().debug(msg);
            }
        };

    }

    @Override
    protected String getName() {
        return "Mapper";
    }
}
