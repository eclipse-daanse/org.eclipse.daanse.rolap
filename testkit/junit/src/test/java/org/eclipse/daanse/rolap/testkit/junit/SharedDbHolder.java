/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   SmartCity Jena, Stefan Bischof - initial
 */
package org.eclipse.daanse.rolap.testkit.junit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.sql.DataSource;

/**
 * Order-independent identity check for the NAMED-scope tests: every
 * participating test records the DataSource identity; whoever runs second
 * proves both classes talk to the same database instance.
 */
final class SharedDbHolder {

    static final String SCOPE_NAME = "junit-selftest-shared";

    private static final List<Integer> IDENTITIES = new CopyOnWriteArrayList<>();

    private SharedDbHolder() {
    }

    static void recordAndAssert(DataSource dataSource) {
        int identity = System.identityHashCode(dataSource);
        IDENTITIES.add(identity);
        assertThat(IDENTITIES)
                .as("All classes in the NAMED scope '%s' share the same DataSource", SCOPE_NAME)
                .allMatch(recorded -> recorded == identity);
    }
}
