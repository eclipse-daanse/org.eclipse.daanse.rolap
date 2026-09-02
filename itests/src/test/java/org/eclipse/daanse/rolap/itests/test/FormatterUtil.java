/*
* This software is subject to the terms of the Eclipse Public License v1.0
* Agreement, available at the following URL:
* http://www.eclipse.org/legal/epl-v10.html.
* You must accept the terms of that agreement to use this software.
*
* Copyright (C) 2005-2005 Julian Hyde
* Copyright (C) 2005-2017 Hitachi Vantara
* All Rights Reserved.
*
* Shishir, 08 May, 2007
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


package org.eclipse.daanse.rolap.itests.test;

import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.api.element.Property;
import org.eclipse.daanse.olap.api.formatter.CellFormatter;
import org.eclipse.daanse.olap.api.formatter.MemberFormatter;
import org.eclipse.daanse.olap.api.formatter.MemberPropertyFormatter;


public class FormatterUtil {

    /**
     * Member formatter for test purposes. Returns name of the member prefixed
     * with "foo" and suffixed with "bar".
     */
    public static class FooBarMemberFormatter implements MemberFormatter {
        @Override
		public String format(Member member) {
            return "foo" + member.getName() + "bar";
        }
    }

    /**
     * Cell formatter for test purposes. Returns value of the cell prefixed
     * with "foo" and suffixed with "bar".
     */
    public static class FooBarCellFormatter implements CellFormatter {
        @Override
		public String format(Object value) {
            return "foo" + value + "bar";
        }
    }

    /**
     * Property formatter for test purposes. Returns name of the member and
     * property, then the value, prefixed with "foo" and suffixed with "bar".
     */
    public static class FooBarPropertyFormatter implements MemberPropertyFormatter {
        @Override
		public String format(
            Member member, Property property, Object propertyValue)
        {
            return "foo" + member.getName() + "/" + property.getName() + "/"
                   + propertyValue + "bar";
        }
    }
}
