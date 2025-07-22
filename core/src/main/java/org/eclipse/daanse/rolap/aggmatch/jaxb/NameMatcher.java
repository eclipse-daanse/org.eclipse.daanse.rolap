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
 * A NameMatcher is a CaseMatcher that prepends and appends
 * regular expressions to a given string as part of creating
 * the matching regular expression. Both the pre/post
 * regular expression can be null in which case matches are
 * applied simply against the name (modulo case considerations).
 * The purpose of this class is to allow aggregate tables to
 * be identified when their table names are formed by placing
 * text before and/or after the base fact table name.
 */
public abstract class NameMatcher extends CaseMatcher {

    /**
     * The regular expression to preppend to the table name.
     */
    private String pretemplate;

    /**
     * The regular expression to append to the table name.
     */
    String posttemplate;

    public void setBasename(String basename) {
        this.basename = basename;
    }

    /**
     * The regular expression used to extract the fact table's
     * base name from its full name. For instance, if the DBA
     * allways prepends "RF_" before each fact table name, i.e.,
     * "RF_SHIPPING", but you want only the base part ("SHIPPING")
     * to be used in recognizing aggregates, then one defines a
     * regular expression with ONE and ONLY one group, in this
     * case "RF_(.*)" with which the base name can be extracted
     * from the full fact table name.
     * In Sun terms, the "()" are a capture group.
     * Note, if a Matcher is requested from a NameMatcher which has
     * the basename attribute set, and the name used in the request
     * does not match the basename pattern, then the Matcher
     * return NEVER MATCHES ANYTHING.
     */
    String basename;

    java.util.regex.Pattern baseNamePattern = null;

    @Override
    public void validate(
        final AggRules rules,
        final org.eclipse.daanse.rolap.recorder.MessageRecorder msgRecorder
    ) {
        msgRecorder.pushContextName(getName());
        try {
            super.validate(rules, msgRecorder);

            if (basename != null) {
                baseNamePattern =
                    java.util.regex.Pattern.compile(basename);
            }
        } finally {
            msgRecorder.popContextName();
        }
    }

    /**
     * Generates a regular expression string by prepending and
     * appending regular expression to the parameter tableName.
     *
     * @param name Table name
     * @return regular expression
     */
    public String getRegex(final String name) {
        StringBuilder buf = new StringBuilder();
        if (getPretemplate() != null) {
            buf.append(getPretemplate());
        }
        if (name != null) {
            String n = name;
            if (baseNamePattern != null) {
                java.util.regex.Matcher matcher =
                    baseNamePattern.matcher(name);
                if (matcher.matches() && matcher.groupCount() > 0) {
                    n = matcher.group(1);

                } else {
                    if (AggRules.getLogger().isDebugEnabled()) {
                        StringBuilder bf = new StringBuilder(64);
                        bf.append(getName());
                        bf.append(".getRegex: for name \"");
                        bf.append(name);
                        bf.append("\" regex is null because basename \"");
                        bf.append(basename);
                        bf.append("\" is not matched.");

                        String msg = bf.toString();
                        AggRules.getLogger().debug(msg);
                    }
                    // If the table name does not match the basename
                    // pattern, then return null for regex.
                    return null;
                }
            }
            buf.append(n);
        }
        if (posttemplate != null) {
            buf.append(posttemplate);
        }

        String regex = buf.toString();

        if (AggRules.getLogger().isDebugEnabled()) {
            StringBuilder bf = new StringBuilder(64);
            bf.append(getName());
            bf.append(".getRegex: for name \"");
            bf.append(name);
            bf.append("\" regex is \"");
            bf.append(regex);
            bf.append('"');

            String msg = bf.toString();
            AggRules.getLogger().debug(msg);
        }
        return regex;
    }

    protected Recognizer.Matcher getMatcher(String name) {

        final CharCaseEnum charcase = getCharCase();
        final String regex;
        int flag = 0;

        if (CharCaseEnum.IGNORE.equals(charcase)) {
            // the case of name does not matter
            // since the Pattern will be create to ignore case
            regex = getRegex(name);

            flag = java.util.regex.Pattern.CASE_INSENSITIVE;

        } else if (CharCaseEnum.EXACT.equals(charcase)) {
            // the case of name is not changed
            // since we are interested in exact case matching
            regex = getRegex(name);

        } else if (CharCaseEnum.UPPER.equals(charcase)) {
            // convert name to upper case
            regex = getRegex(name.toUpperCase());

        } else {
            // lower
            // convert name to lower case
            regex = getRegex(name.toLowerCase());

        }
        // If regex is null, then return a matcher that matches nothing
        if (regex == null) {
            return new Recognizer.Matcher() {
                public boolean matches(String name) {
                    return false;
                }
            };
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
                bf.append(NameMatcher.this.getName());
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
        return "NameMatcher";
    }

	public String getPretemplate() {
		return pretemplate;
	}

	public void setPretemplate(String pretemplate) {
		this.pretemplate = pretemplate;
	}

    public void setPosttemplate(String posttemplate) {
        this.posttemplate = posttemplate;
    }

}
