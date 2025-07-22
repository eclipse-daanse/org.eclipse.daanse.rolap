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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * A class derived from DelegatingInvocationHandler handles a
 * method call by looking for a method in itself with identical parameters. If
 * no such method is found, it forwards the call to a fallback object, which
 * must implement all of the interfaces which this proxy implements.
 *
 * It is useful in creating a wrapper class around an interface which may
 * change over time.
 *
 * Example:
 *
 * 
 * import java.sql.Connection;
 * Connection connection = ...;
 * Connection tracingConnection = (Connection) Proxy.newProxyInstance(
 *     null,
 *     new Class[] {Connection.class},
 *     new DelegatingInvocationHandler() {
 *         protected Object getTarget() {
 *             return connection;
 *         }
 *         Statement createStatement() {
 *             System.out.println("statement created");
 *             return connection.createStatement();
 *         }
 *     });
 * 
 * 
 *
 * @author jhyde
 */
public abstract class DelegatingInvocationHandler
    implements InvocationHandler
{
    @Override
	public Object invoke(
        Object proxy,
        Method method,
        Object [] args)
        throws Throwable
    {
        Class clazz = getClass();
        Method matchingMethod;
        try {
            matchingMethod =
                clazz.getMethod(
                    method.getName(),
                    method.getParameterTypes());
        } catch (NoSuchMethodException | SecurityException e) {
            matchingMethod = null;
        }
        try {
            if (matchingMethod != null) {
                // Invoke the method in the derived class.
                return matchingMethod.invoke(this, args);
            }
            final Object target = getTarget();
            if (target == null) {
                throw new UnsupportedOperationException(
                    "method: " + method);
            }
            return method.invoke(
                target,
                args);
        } catch (InvocationTargetException e) {
            throw e.getTargetException();
        }
    }

    /**
     * Returns the object to forward method calls to, should the derived class
     * not implement the method. Generally, this object will be a member of the
     * derived class, supplied as a parameter to its constructor.
     *
     * The default implementation returns null, which will cause the
     * {@link #invoke(Object, java.lang.reflect.Method, Object[])} method
     * to throw an {@link UnsupportedOperationException} if the derived class
     * does not have the required method.
     *
     * @return object to forward method calls to
     *
     * @throws InvocationTargetException if there is an error acquiring the
     * target
     */
    protected Object getTarget() throws InvocationTargetException {
        return null;
    }
}
