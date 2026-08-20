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
package org.eclipse.daanse.rolap.testkit.junit.internal;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.eclipse.daanse.jdbc.datasource.testkit.api.ActiveDatabase;
import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.rolap.testkit.junit.api.ContextModifier;
import org.eclipse.daanse.rolap.testkit.junit.api.InjectRolap;
import org.eclipse.daanse.rolap.testkit.junit.api.Roles;
import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;

/**
 * Shared type matching and value resolution for parameter and
 * {@link InjectRolap} field injection.
 */
public final class InjectionSupport {

    private InjectionSupport() {
    }

    /** True if the declared type is one this extension owns. */
    public static boolean supportsType(Class<?> type, ManagedContext managed) {
        if (type == Connection.class || type == ActiveDatabase.class || type == Dialect.class
                || type == DataSource.class) {
            return true;
        }
        if (Context.class.isAssignableFrom(type) && type.isInstance(managed.context())) {
            return true;
        }
        return ContextModifier.class.isAssignableFrom(type) && managed.hasModifier(type);
    }

    /** Resolves a supported type; {@code roles} may be null (default role). */
    public static Object resolve(Class<?> type, Roles roles, ManagedContext managed, String describedLocation) {
        if (roles != null && type != Connection.class) {
            throw new ExtensionConfigurationException(
                    "@Roles at " + describedLocation + " is only allowed on Connection, not " + type.getName());
        }
        if (type == Connection.class) {
            List<String> roleNames = roles == null ? List.of() : List.of(roles.value());
            return managed.connection(roleNames);
        }
        if (type == ActiveDatabase.class) {
            return managed.database();
        }
        if (type == Dialect.class) {
            return managed.database().dialect();
        }
        if (type == DataSource.class) {
            return managed.database().dataSource();
        }
        if (Context.class.isAssignableFrom(type)) {
            return managed.context();
        }
        if (ContextModifier.class.isAssignableFrom(type)) {
            return managed.modifier(type);
        }
        throw new ExtensionConfigurationException(
                "Unsupported injection type " + type.getName() + " at " + describedLocation);
    }

    /** All {@link InjectRolap} fields of the class hierarchy, static or instance. */
    public static List<Field> injectableFields(Class<?> testClass, boolean staticFields) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> c = testClass; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (field.isAnnotationPresent(InjectRolap.class)
                        && Modifier.isStatic(field.getModifiers()) == staticFields) {
                    fields.add(field);
                }
            }
        }
        return fields;
    }

    /**
     * Fills the {@link InjectRolap} fields; {@code instance} null = static
     * fields. Fields must be non-private and non-final ({@code @TempDir} rule).
     */
    public static void injectFields(Class<?> testClass, Object instance, ManagedContext managed) {
        for (Field field : injectableFields(testClass, instance == null)) {
            String where = field.getDeclaringClass().getName() + "." + field.getName();
            if (Modifier.isPrivate(field.getModifiers())) {
                throw new ExtensionConfigurationException("@InjectRolap field must not be private: " + where);
            }
            if (Modifier.isFinal(field.getModifiers())) {
                throw new ExtensionConfigurationException("@InjectRolap field must not be final: " + where);
            }
            if (!supportsType(field.getType(), managed)) {
                throw new ExtensionConfigurationException(
                        "@InjectRolap field has unsupported type " + field.getType().getName() + ": " + where);
            }
            Object value = resolve(field.getType(), field.getAnnotation(Roles.class), managed, where);
            try {
                field.setAccessible(true);
                field.set(instance, value);
            } catch (ReflectiveOperationException e) {
                throw new ExtensionConfigurationException("Cannot inject @InjectRolap field " + where, e);
            }
        }
    }
}
