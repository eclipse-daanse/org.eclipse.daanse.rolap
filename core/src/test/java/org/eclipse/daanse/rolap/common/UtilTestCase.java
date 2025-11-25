/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2004-2005 Julian Hyde
 * Copyright (C) 2005-2017 Hitachi Vantara and others
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

package org.eclipse.daanse.rolap.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Driver;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.daanse.rolap.util.Composite;
import org.eclipse.daanse.rolap.util.ServiceDiscovery;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Tests for methods in {@link org.eclipse.daanse.olap.common.Util} and, sometimes, classes in
 * the {@code mondrian.util} package.
 */
 class UtilTestCase{

    /**
     * Tests {@link org.eclipse.daanse.rolap.util.ServiceDiscovery}.
     */
    @Test
    @Disabled("Use Serviceloader directly - this is pre java 8 and non-osgi")
     void serviceDiscovery() {
        final ServiceDiscovery<Driver>
            serviceDiscovery = ServiceDiscovery.forClass(Driver.class);
        final List<Class<Driver>> list = serviceDiscovery.getImplementor();
        assertThat(list.isEmpty()).isFalse();

        // Check that discovered classes include AT LEAST:
        // JdbcOdbcDriver (in the JDK),
        // MondrianOlap4jDriver (in mondrian)
        List<String> expectedClassNames =
            new ArrayList<>(
                Arrays.asList(
                    // Usually on the list, but not guaranteed:
                    // "sun.jdbc.odbc.JdbcOdbcDriver",
                    "mondrian.olap4j.MondrianOlap4jDriver"));
        for (Class<Driver> driverClass : list) {
            expectedClassNames.remove(driverClass.getName());
        }
        assertThat(expectedClassNames.isEmpty()).as(expectedClassNames.toString()).isTrue();
    }


    /**
     * Unit test for {@link Composite#of(Iterable[])}.
     */
    @Test
     void compositeIterable() {
        final Iterable<String> beatles =
            Arrays.asList("john", "paul", "george", "ringo");
        final Iterable<String> stones =
            Arrays.asList("mick", "keef", "brian", "bill", "charlie");
        final List<String> empty = Collections.emptyList();

        final StringBuilder buf = new StringBuilder();
        for (String s : Composite.of(beatles, stones)) {
            buf.append(s).append(";");
        }
        assertThat(buf.toString()).isEqualTo("john;paul;george;ringo;mick;keef;brian;bill;charlie;");

        buf.setLength(0);
        for (String s : Composite.of(empty, stones)) {
            buf.append(s).append(";");
        }
        assertThat(buf.toString()).isEqualTo("mick;keef;brian;bill;charlie;");

        buf.setLength(0);
        for (String s : Composite.of(stones, empty)) {
            buf.append(s).append(";");
        }
        assertThat(buf.toString()).isEqualTo("mick;keef;brian;bill;charlie;");

        buf.setLength(0);
        for (String s : Composite.of(empty)) {
            buf.append(s).append(";");
        }
        assertThat(buf.toString()).isEqualTo("");

        buf.setLength(0);
        for (String s : Composite.of(empty, empty, beatles, empty, empty)) {
            buf.append(s).append(";");
        }
        assertThat(buf.toString()).isEqualTo("john;paul;george;ringo;");
    }
}
