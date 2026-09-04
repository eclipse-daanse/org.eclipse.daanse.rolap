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

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.eclipse.daanse.rolap.testkit.junit.api.ContextScope;
import org.eclipse.daanse.rolap.testkit.junit.api.DbScope;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapConfig;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.eclipse.daanse.rolap.testkit.junit.api.Roles;
import org.eclipse.daanse.rolap.testkit.junit.internal.InjectionSupport;
import org.eclipse.daanse.rolap.testkit.junit.internal.ManagedContext;
import org.eclipse.daanse.rolap.testkit.junit.internal.Provisioning;
import org.eclipse.daanse.rolap.testkit.junit.internal.RolapFixture;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.platform.commons.support.AnnotationSupport;

/**
 * The extension behind {@link RolapContextTest}: provisions database,
 * catalog and context, resolves parameters and {@code @InjectRolap} fields by
 * type, and guarantees teardown via the {@link ExtensionContext.Store}.
 *
 * <p>{@code supportsParameter} claims only this extension's types, so it
 * coexists with MockitoExtension and other parameter resolvers.
 */
public class RolapContextExtension implements BeforeAllCallback, BeforeEachCallback, AfterAllCallback,
        ParameterResolver {

    private static final Namespace NAMESPACE = Namespace.create(RolapContextExtension.class);
    private static final String MANAGED = "managedContext";

    @Override
    public void beforeAll(ExtensionContext extensionContext) {
        Class<?> testClass = extensionContext.getRequiredTestClass();
        Optional<RolapContextTest> classAnnotation =
                AnnotationSupport.findAnnotation(testClass, RolapContextTest.class);
        boolean hasStaticFields = !InjectionSupport.injectableFields(testClass, true).isEmpty();
        if (classAnnotation.isEmpty()) {
            if (hasStaticFields) {
                throw new ExtensionConfigurationException("static @InjectRolap fields in " + testClass.getName()
                        + " require a class-level @RolapContextTest");
            }
            return;
        }
        RolapFixture fixture = RolapFixture.resolve(classAnnotation.get(), testClass.getName());
        if (fixture.contextScope() == ContextScope.PER_CLASS) {
            ManagedContext managed = Provisioning.openManaged(fixture,
                    isolationKey(fixture, extensionContext, testClass.getName()), configsOf(testClass));
            extensionContext.getStore(NAMESPACE).put(MANAGED, managed);
            InjectionSupport.injectFields(testClass, null, managed);
        } else if (hasStaticFields) {
            throw new ExtensionConfigurationException("static @InjectRolap fields in " + testClass.getName()
                    + " require ContextScope.PER_CLASS (a static field over a per-test context would go stale)");
        }
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) {
        Class<?> testClass = extensionContext.getRequiredTestClass();
        Method method = extensionContext.getRequiredTestMethod();
        String where = testClass.getName() + "#" + method.getName();

        Optional<RolapContextTest> methodAnnotation =
                AnnotationSupport.findAnnotation(method, RolapContextTest.class);
        Optional<RolapContextTest> classAnnotation =
                AnnotationSupport.findAnnotation(testClass, RolapContextTest.class);
        RolapContextTest effective = methodAnnotation.or(() -> classAnnotation)
                .orElseThrow(() -> new ExtensionConfigurationException(
                        "No @RolapContextTest found for " + where));
        RolapFixture fixture = RolapFixture.resolve(effective, where);

        if (methodAnnotation.isPresent() && fixture.contextScope() == ContextScope.PER_CLASS) {
            throw new ExtensionConfigurationException("Method-level @RolapContextTest at " + where
                    + " must use ContextScope.PER_TEST — a per-class context cannot belong to one method");
        }

        Map<String, Object> methodConfigs = configsOf(method);
        ManagedContext current;
        if (methodAnnotation.isPresent() || fixture.contextScope() == ContextScope.PER_TEST) {
            // Fresh context for this test. A method-level annotation REPLACES the
            // class configuration entirely, including class-level @RolapConfig.
            Map<String, Object> config = new LinkedHashMap<>();
            if (methodAnnotation.isEmpty()) {
                config.putAll(configsOf(testClass));
            }
            config.putAll(methodConfigs);
            current = Provisioning.openManaged(fixture,
                    isolationKey(fixture, extensionContext, testClass.getName()), config);
            extensionContext.getStore(NAMESPACE).put(MANAGED, current);
        } else {
            if (!methodConfigs.isEmpty()) {
                throw new ExtensionConfigurationException("Method-level @RolapConfig at " + where
                        + " requires ContextScope.PER_TEST — it would leak into sibling tests");
            }
            current = currentManaged(extensionContext)
                    .orElseThrow(() -> new ExtensionConfigurationException(
                            "No managed context available for " + where));
        }
        InjectionSupport.injectFields(testClass, extensionContext.getRequiredTestInstance(), current);
    }

    /**
     * Releases a {@link DbScope#PER_CLASS} database once the class is done with
     * it. Keyed off the class-level {@code @RolapContextTest} only — a
     * method-level override to a different scope keeps its own database key and
     * is unaffected. {@code Provisioning.release} is idempotent, so this is
     * harmless if a {@code PER_TEST}-scoped {@link ManagedContext} (a different
     * scope, a different key) already released its own database on close.
     */
    @Override
    public void afterAll(ExtensionContext extensionContext) {
        Class<?> testClass = extensionContext.getRequiredTestClass();
        Optional<RolapContextTest> classAnnotation =
                AnnotationSupport.findAnnotation(testClass, RolapContextTest.class);
        if (classAnnotation.isEmpty()) {
            return;
        }
        RolapFixture fixture = RolapFixture.resolve(classAnnotation.get(), testClass.getName());
        if (fixture.dbScope() == DbScope.PER_CLASS) {
            Provisioning.release(isolationKey(fixture, extensionContext, testClass.getName()));
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        Optional<ManagedContext> managed = currentManaged(extensionContext);
        return managed.isPresent()
                && InjectionSupport.supportsType(parameterContext.getParameter().getType(), managed.get());
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        ManagedContext managed = currentManaged(extensionContext)
                .orElseThrow(() -> new ParameterResolutionException("No managed rolap context in scope"));
        Roles roles = parameterContext.findAnnotation(Roles.class).orElse(null);
        return InjectionSupport.resolve(parameterContext.getParameter().getType(), roles, managed,
                parameterContext.getDeclaringExecutable().toGenericString());
    }

    /** Method store shadows class store — Jupiter store lookup is hierarchical. */
    private static Optional<ManagedContext> currentManaged(ExtensionContext extensionContext) {
        return Optional.ofNullable(
                extensionContext.getStore(NAMESPACE).get(MANAGED, ManagedContext.class));
    }

    private static String isolationKey(RolapFixture fixture, ExtensionContext extensionContext, String className) {
        return switch (fixture.dbScope()) {
        case PER_TEST -> "test:" + extensionContext.getUniqueId();
        case PER_CLASS -> "class:" + className;
        case PER_RUNTIME -> "fixture:" + fixture.fixtureKey();
        case NAMED -> "named:" + fixture.scopeName();
        };
    }

    private static Map<String, Object> configsOf(AnnotatedElement element) {
        Map<String, Object> config = new LinkedHashMap<>();
        for (RolapConfig entry : AnnotationSupport.findRepeatableAnnotations(element, RolapConfig.class)) {
            config.put(entry.key(), Provisioning.coerce(entry.key(), entry.value(), entry.type()));
        }
        return config;
    }
}
