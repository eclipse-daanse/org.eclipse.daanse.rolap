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
package org.eclipse.daanse.rolap.testkit.core;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * JUnit 5 extension that automatically releases named-scope caches in
 * {@link CatalogTestHarness}. Attach via {@code @ExtendWith} or a JUnit
 * platform service-loader registration.
 *
 * <p>
 * Two release modes are recognised:
 * <ul>
 * <li>A class-level scope name read from
 * {@code @org.junit.jupiter.api.Tag("testkit:scope=NAME")} on the test class is
 * released in {@link AfterAllCallback#afterAll(ExtensionContext)}.
 * <li>A method-level scope name read from the same tag pattern on a test method
 * is released in {@link AfterEachCallback#afterEach(ExtensionContext)}.
 * </ul>
 *
 * <p>
 * If no matching tag is present, the extension is a no-op — safe to attach to
 * any test class.
 */
public class TestKitLifecycleExtension implements AfterAllCallback, AfterEachCallback {

    private static final String TAG_PREFIX = "testkit:scope=";

    @Override
    public void afterAll(ExtensionContext context) {
        scopeFromTags(context).ifPresent(CatalogTestHarness::releaseScope);
    }

    @Override
    public void afterEach(ExtensionContext context) {
        scopeFromTags(context).ifPresent(CatalogTestHarness::releaseScope);
    }

    private static java.util.Optional<String> scopeFromTags(ExtensionContext context) {
        for (String tag : context.getTags()) {
            if (tag.startsWith(TAG_PREFIX)) {
                String name = tag.substring(TAG_PREFIX.length());
                if (!name.isBlank()) {
                    return java.util.Optional.of(name);
                }
            }
        }
        return java.util.Optional.empty();
    }
}
