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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.daanse.rolap.common.aggmatcher.Recognizer;

public abstract class RegexMapper extends Base {

    /**
     * The unique identifier for this Matcher.
     */
    private String id;
    /**
     * This is an array of Regex. A match occurs if any one of
     * the Regex matches; it is the equivalent of or-ing the
     * regular expressions together. A candidate string is processed
     * sequentially by each Regex in their document order until
     * one matches. In none match, well, none match.
     */
    private List<Regex> regexs;

    protected String getTag() {
        return getId();
    }

    public void validate(
        final AggRules rules,
        final org.eclipse.daanse.rolap.recorder.MessageRecorder msgRecorder
    ) {
        msgRecorder.pushContextName(getName());
        try {

            List<String> templateNames = getTemplateNames();

            for (Regex regex : getRegexs()) {
                regex.validate(rules, templateNames, msgRecorder);
            }

        } finally {
            msgRecorder.popContextName();
        }
    }

    /**
     * This must be defined in derived classes. It returns an array of
     * symbolic names that are the symbolic names allowed to appear
     * in the regular expression templates.
     *
     * @return array of symbol names
     */
    protected abstract List<String> getTemplateNames();

    protected Recognizer.Matcher getMatcher(final String[] names) {

        final List<java.util.regex.Pattern> patterns =
            new ArrayList<>();

        for (Regex regex : getRegexs()) {
            patterns.add(regex.getPattern(names));
        }

        return new Recognizer.Matcher() {
            public boolean matches(String name) {
                for (java.util.regex.Pattern pattern : patterns) {
                    if ((pattern != null) &&
                        pattern.matcher(name).matches()) {

                        if (AggRules.getLogger().isDebugEnabled()) {
                            debug(name, pattern);
                        }

                        return true;
                    }
                }
                return false;
            }

            private void debug(String name, java.util.regex.Pattern p) {
                StringBuilder bf = new StringBuilder(64);
                bf.append("DefaultDef.RegexMapper");
                bf.append(".Matcher.matches:");
                bf.append(" name \"");
                bf.append(name);
                bf.append("\" matches regex \"");
                bf.append(p.pattern());
                bf.append("\"");
                if ((p.flags() &
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
        return "RegexMapper";
    }

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public List<Regex> getRegexs() {
		return regexs;
	}

	public void setRegexs(List<Regex> regexs) {
		this.regexs = regexs;
	}
}
