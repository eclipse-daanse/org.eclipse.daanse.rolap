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
import java.util.List;
import java.util.stream.Collectors;

/**
 * This element is used in a vector of child elements when
 * one wishes to have one or more regular expressions associated
 * with matching a given string. The parent element must
 * initialize Regex object by calling its validate method
 * passing in an array of template names.
 * The cdata content is a regular expression with embedded
 * template names. Each name must be surrounded by "${" and "}".
 * Each time this is used for a new set of names, the names
 * replace the template names in the regular expression.
 * For example, if the charcase="lower", the attribute
 * dot="-" (the default dot value is "_"), the template names are:
 * "city", "state", and "country"
 * and the cdata is:
 * .*_${country}_.*_${city}
 * Then when the names:
 * "San Francisco", "California", and "U.S.A"
 * are passed in, the regular expression becomes:
 * .*_u-s-a_.*_san_francisco
 * 
 * Note that a given template name can only appear ONCE in the
 * template content, the cdata content. As an example, the
 * following cdata template is not supported:
 * .*_${country}_.*_${city}_${country}
 */
public class Regex extends CaseMatcher {

    private static final String BAD_TEMPLATE = "Bad template \"";

    /**
     * How to translate the space character.
     * For example, if the space=='_' and
     * the source string is "Product Family",
     * then the target string is "Product_Family".
     */
    private String space = "_";

    /**
     * How to translate the dot character.
     * For example, if the dot=='_' and
     * the source string is "Time.Time Weekly",
     * then the target string is "Time_Time Weekly".
     */
    private String dot = "_";

	private String template;


    public String getSpace() {
        return space;
    }

    public void setSpace(String space) {
        this.space = space;
    }

    public String getDot() {
        return dot;
    }

    public void setDot(String dot) {
        this.dot = dot;
    }

    public String getTemplate() {
        return template;
    }

    protected static final int BAD_ID = -1;

    protected List<String> templateParts;

    /**
     * This is a one-to-one mapping, each template name can appear
     * at most once.
     */
    protected List<Integer> templateNamePos;

    /**
     * It is hoped that no one will need to match more than 50 names
     * in a template. Currently, this implementation, requires only 3.
     */
    private static final int MAX_SIZE = 50;

    public void validate(
        final AggRules rules,
        final List<String> templateNames,
        final org.eclipse.daanse.rolap.recorder.MessageRecorder msgRecorder
    ) {
        msgRecorder.pushContextName(getName());
        try {
            super.validate(rules, msgRecorder);

            String[] ss = new String[MAX_SIZE + 1];
            Integer[] poss = new Integer[MAX_SIZE];

            String template = getTemplate();
            int count = 0;

            int end = 0;
            int previousEnd = 0;
            int start = template.indexOf("${", end);
            // if no templateNames, then there better not be
            // any ${}s
            if (templateNames.isEmpty()) {
                if (start == -1) {
                    // everything is ok
                    templateParts = List.of(template);
                    templateNamePos = List.of();
                } else {
                    String msg = BAD_TEMPLATE +
                        template +
                        "\", no ${} entries but there are " +
                        "template names";
                    msgRecorder.reportError(msg);
                }
                return;
            }
            while (count < MAX_SIZE) {
                if (start == -1) {
                    if (count == 0) {
                        // there are no ${} in template which
                        // is an error
                        String msg = BAD_TEMPLATE +
                            template +
                            "\", no ${} entries";
                        msgRecorder.reportError(msg);
                        return;
                    }
                    // its OK, there are "count" ${}
                    templateNamePos = Arrays.stream(poss, 0, count).toList();

                    ss[count++] =
                        template.substring(end, template.length());
                    templateParts = Arrays.stream(ss, 0, count).toList();

                    return;
                }

                previousEnd = end;
                end = template.indexOf('}', start);
                if (end == -1) {
                    // there was a "${" but not '}' in template
                    String msg = BAD_TEMPLATE +
                        template +
                        "\", it had a \"${\", but no '}'";
                    msgRecorder.reportError(msg);
                    return;
                }

                String name = template.substring(start + 2, end);
                int pos = convertNameToID(name,
                    templateNames,
                    msgRecorder);
                if (pos == BAD_ID) {
                    return;
                }

                poss[count] = pos;
                ss[count] = template.substring(previousEnd, start);

                start = template.indexOf("${", end);
                end++;

                count++;
            }

        } finally {
            msgRecorder.popContextName();
        }
    }

    private int convertNameToID(
        final String name,
        final List<String> templateNames,
        final org.eclipse.daanse.rolap.recorder.MessageRecorder msgRecorder
    ) {

        if (name == null) {
            String msg = "Template name is null";
            msgRecorder.reportError(msg);
            return BAD_ID;
        }

        for (int i = 0; i < templateNames.size(); i++) {
            if (templateNames.get(i).equals(name)) {
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
        buf.append(templateParts.getFirst());
        for (int i = 0; i < templateNamePos.size(); i++) {
            String n = names[templateNamePos.get(i)];
            if (n == null) {
                // its ok for a name to be null, it just
                // eliminates the current regex from consideration
                return null;
            }

            if (spaceInner != null) {
                n = n.replace(" ", spaceInner);
            }
            if (dotInner != null) {
                n = n.replace(".", dotInner);
            }

            buf.append(n);
            buf.append(templateParts.get(i + 1));
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

    protected java.util.regex.Pattern getPattern(final String[] names) {

        final CharCaseEnum charcase = getCharCase();

        if (CharCaseEnum.IGNORE.equals(charcase)) {
            // the case of name does not matter
            // since the Pattern will be create to ignore case
            final String regex = getRegex(names);
            if (regex == null) {
                return null;
            }


            return java.util.regex.Pattern.compile(regex,
                    java.util.regex.Pattern.CASE_INSENSITIVE);

        } else if (CharCaseEnum.EXACT.equals(charcase)) {
            // the case of name is not changed
            // since we are interested in exact case matching
            final String regex = getRegex(names);
            if (regex == null) {
                return null;
            }


            return java.util.regex.Pattern.compile(regex);

        } else if (CharCaseEnum.UPPER.equals(charcase)) {
            // convert name to upper case

            String[] ucNames = Arrays.stream(names)
                .map(s -> s != null ? s.toUpperCase() : null).toArray(String[]::new);

            final String regex = getRegex(ucNames);
            if (regex == null) {
                return null;
            }


            return java.util.regex.Pattern.compile(regex);

        } else {
            // lower
            // convert name to lower case
            String[] lcNames = Arrays.stream(names)
                .map(s -> s != null ? s.toLowerCase() : null).toArray(String[]::new);

            final String regex = getRegex(lcNames);
            if (regex == null) {
                return null;
            }


            return java.util.regex.Pattern.compile(regex);

        }
    }

    @Override
    protected String getName() {
        return "Regex";
    }

	public void setTemplate(String template) {
		this.template = template;
	}
}
