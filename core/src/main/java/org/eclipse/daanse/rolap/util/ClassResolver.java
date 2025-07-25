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


package org.eclipse.daanse.rolap.util;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.Enumeration;

/**
 * Instantiates a class.
 *
 * Has same effect as calling {@link Class#forName(String)},  but uses the
 * appropriate {@link ClassLoader}.
 */
public interface ClassResolver {

    /** Equivalent of {@link Class#forName(String, boolean, ClassLoader)}. */
    <T> Class<T> forName(String className, boolean initialize)
        throws ClassNotFoundException;

    /**
     * Instantiates a class and constructs an instance using the given
     * arguments.
     *
     * @param className Class name
     * @param args Arguments
     * @param <T> Desired type
     * @throws ClassCastException if resulting object is not an instance of T
     */
    <T> T instantiateSafe(String className, Object... args);

    /** Equivalent of {@link ClassLoader#getResources(String)}. */
    Iterable<URL> getResources(String lookupName) throws IOException;

    /** Default resolver. */
    ClassResolver INSTANCE = new ThreadContextClassResolver();

    /** Implementation of {@link ClassResolver} that calls
     * {@link Thread#getContextClassLoader()} on the current thread. */
    class ThreadContextClassResolver extends AbstractClassResolver {
        @Override
		protected ClassLoader getClassLoader() {
            ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
            return contextClassLoader != null ? contextClassLoader : getClass().getClassLoader();
        }
    }

    /** Partial implementation of {@link ClassResolver}. Derived class just
     * needs to implement {@link #getClassLoader()}. */
    abstract class AbstractClassResolver implements ClassResolver {
        @Override
		public <T> T instantiateSafe(String className, Object... args) {
            try {
                final Class<T> clazz = forName(className, true);
                if (args.length == 0) {
                    return clazz.newInstance();
                } else {
                    Class[] types = new Class[args.length];
                    for (int i = 0; i < args.length; i++) {
                        types[i] = args[i].getClass();
                    }
                    final Constructor<T> constructor =
                        clazz.getConstructor(types);
                    return constructor.newInstance(args);
                }
            } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException | ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
		public <T> Class<T> forName(String className, boolean initialize)
            throws ClassNotFoundException
        {
            //noinspection unchecked
            return (Class<T>) Class.forName(
                className,
                initialize,
                getClassLoader());
        }

        /** Returns the class loader to use for the current operation. May be
         * null. */
        protected abstract ClassLoader getClassLoader();

        /** Returns the class loader to use for the current operation, never
         * null. */
        protected ClassLoader getClassLoaderNotNull() {
            final ClassLoader classLoader = getClassLoader();
            return classLoader != null
                ? classLoader
                : getClass().getClassLoader();
        }

        @Override
		public Iterable<URL> getResources(String name) throws IOException {
            final Enumeration<URL> resources =
                getClassLoaderNotNull().getResources(name);
            
            //noinspection unchecked
            return new IteratorIterable<URL>(resources.asIterator());
        }
    }
}
