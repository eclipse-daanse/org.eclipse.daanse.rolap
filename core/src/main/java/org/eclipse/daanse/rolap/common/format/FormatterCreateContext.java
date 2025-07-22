/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2016-2017 Hitachi Vantara and others
 * All Rights Reserved.
 *
 * ---- All changes after Fork in 2023 ------------------------
 *
 * Project: Eclipse daanse
 *
 * Copyright (c) 2023 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors after Fork in 2023:
 *   SmartCity Jena - initial
 */

package org.eclipse.daanse.rolap.common.format;

import org.eclipse.daanse.rolap.mapping.api.model.FormatterMapping;

/**
 * Context data to create a formatter for the element.
 * Note:
 * Use nested builder class to instantiate.
 */
public class FormatterCreateContext {

    private String formatterClassName;
    private String scriptText;
    private String scriptLanguage;
    private String elementName;

    private FormatterCreateContext(Builder builder) {
        this.formatterClassName = builder.formatterClassName;
        this.scriptText = builder.scriptText;
        this.scriptLanguage = builder.scriptLanguage;
        this.elementName = builder.elementName;
    }

    public String getFormatterClassName() {
        return formatterClassName;
    }

    public String getScriptText() {
        return scriptText;
    }

    public String getScriptLanguage() {
        return scriptLanguage;
    }

    public String getElementName() {
        return elementName;
    }

    /**
     * Builder to create an instance of FormatterCreateContext.
     */
    public static class Builder {

        private String formatterClassName;
        private String scriptText;
        private String scriptLanguage;
        private String elementName;

        private boolean formatterAsElement;

        /**
         * Mondrian schema element name formatter is being created for.
         * Element name itself is used just to properly log errors if any.
         */
        public Builder(String elementName) {
            this.elementName = elementName;
        }

        /**
         * Data from Mondrian xml schema file to create
         * a custom implementation of a requested formatter.
         */
        public Builder formatterDef(FormatterMapping cellFormatterMapping) {
            if (cellFormatterMapping != null) {

                formatterAsElement = true;
                formatterClassName = cellFormatterMapping.getRef();
            }
            return this;
        }

        /**
         * In order to support previous version's configurations,
         * a custom formatter can be specified by only specifying
         * its class name in attribute "formatter".
         *
         * Note: formatter as an element will supersede this class.
         */
        public Builder formatterAttr(String formatterClassName) {
            if (!formatterAsElement) {
                this.formatterClassName = formatterClassName;
            }
            return this;
        }

        /**
         * A script data used to create
         * a script based implementation of a requested formatter.
         */
        public Builder script(String scriptText, String scriptLanguage) {
            this.scriptText = scriptText;
            this.scriptLanguage = scriptLanguage;
            return this;
        }


        public FormatterCreateContext build() {
            return new FormatterCreateContext(this);
        }


    }
}
