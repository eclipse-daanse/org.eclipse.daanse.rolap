/*
* This software is subject to the terms of the Eclipse Public License v1.0
* Agreement, available at the following URL:
* http://www.eclipse.org/legal/epl-v10.html.
* You must accept the terms of that agreement to use this software.
*
* Copyright (c) 2002-2017 Hitachi Vantara..  All rights reserved.
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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility functions to discover Java services.
 *
 * Java services are described in
 * <a href="http://java.sun.com/j2se/1.5.0/docs/guide/jar/jar.html#Service%20Provider">
 * the JAR File Specification</a>.
 *
 * Based on the suggested file format, this class reads the service
 * entries in a JAR file and discovers implementors of an interface.
 *
 * @author Marc Batchelor
 * @deprecated use ServiceLoader
 */
@Deprecated()
public class ServiceDiscovery<T> {

    private static final Logger logger = LoggerFactory.getLogger(ServiceDiscovery.class);

    private final Class<T> theInterface;

    /**
     * Creates a ServiceDiscovery.
     *
     * @param theInterface Interface for service
     *
     * @return ServiceDiscovery for finding instances of the given interface
     */
    public static <T> ServiceDiscovery<T> forClass(Class<T> theInterface) {
        return new ServiceDiscovery<>(theInterface);
    }

    /**
     * Creates a ServiceDiscovery.
     *
     * @param theInterface Interface for service
     * @deprecated use ServiceLoader
     */
    @Deprecated
    private ServiceDiscovery(Class<T> theInterface) {
        assert theInterface != null;
        this.theInterface = theInterface;
    }

    /**
     * Returns a list of classes that implement the service.
     *
     * @return List of classes that implement the service
     * @deprecated use ServiceLoaderU
     */
    @Deprecated
    public List<Class<T>> getImplementor() {
        // Use linked hash set to eliminate duplicates but still return results
        // in the order they were added.
        Set<Class<T>> uniqueClasses = new LinkedHashSet<>();
        ServiceLoader.load(theInterface).forEach(s->uniqueClasses.add((Class<T>) s.getClass()));
        List<Class<T>> rtn = new ArrayList<>(uniqueClasses);
        return rtn;
    }

}
