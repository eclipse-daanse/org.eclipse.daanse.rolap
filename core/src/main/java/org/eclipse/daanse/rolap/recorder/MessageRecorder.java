/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2005-2005 Julian Hyde
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

package org.eclipse.daanse.rolap.recorder;

/**
 * Records warnings and errors during the processing of a task. Contexts can be
 * added and removed. This allows one to collect more than one warning/error,
 * keep processing, and then the code that initiated the processing can
 * determine what to do with the warnings/errors if they exist.
 * 
 * A typical usage might be:
 * 
 *
 *
 *    void process(MessageRecorder msgRecorder) {
 *        msgRecorder.pushContextName(getName());
 *        try {
 *              // prcess task
 *              ....
 *              // need to generate warning message
 *              String msg = ...
 *              msgRecorder.reportWarning(msg);
 *              ....
 *        } finally {
 *              msgRecorder.popContextName();
 *        }
 *    }
 *
 *
 * 
 * Implementations must provide the means for extracting the error/warning
 * messages.
 * 
 * Code that is processing should not catch the MessageRecorder.RTException.
 * This Exception is thrown by the MessageRecorder when too many errors have
 * been seen. Throwing this Exception is the mechanism used to stop processing
 * and return to the initiating code. The initiating code should expect to catch
 * the MessageRecorder.RTException Exception.
 * 
 *
 *
 *    void initiatingCode(MessageRecorder msgRecorder) {
 *      // get MessageRecorder implementation
 *      MessageRecorder msgRecorder = ....
 *      try {
 *          processingCode(msgRecorder);
 *      } catch (MessageRecorder.RTException mrex) {
 *          // empty
 *      }
 *      if (msgRecorder.hasErrors()) {
 *          // handle errors
 *      } else if (msgRecorder.hasWarnings()) {
 *          // handle warnings
 *      }
 *    }
 *
 *
 * 
 * The reporting methods all have variations that take an "info" Object. This
 * can be used to pass something, beyond a text message, from the point of
 * warning/error to the initiating code.
 * 
 * Concerning logging, it is a rule that a message, if logged by the code
 * creating the MessageRecorder implementation, is logged at is reporting level,
 * errors are logged at the error log level, warnings at the warning level and
 * info at the info level. This allows the client code to "know" what log level
 * their messages might appear at.
 *
 * @author Richard M. Emberson
 */
public interface MessageRecorder {

    /**
     * Clear all context, warnings and errors from the MessageRecorder. After
     * calling this method the MessageRecorder implemenation should be in the same
     * state as if it were just constructed.
     */
    void clear();

    /**
     * Get the time when the MessageRecorder was created or the last time that the
     * clear method was called.
     *
     * @return the start time
     */
    long getStartTimeMillis();

    /**
     * How long the MessageRecorder has been running since it was created or the
     * last time clear was called.
     */
    long getRunTimeMillis();

    /**
     * Returns true if there are one or more informational messages.
     *
     * @return true if there are one or more infos.
     */
    boolean hasInformation();

    /**
     * Returns true if there are one or more warning messages.
     *
     * @return true if there are one or more warnings.
     */
    boolean hasWarnings();

    /**
     * Returns true if there are one or more error messages.
     *
     * @return true if there are one or more errors.
     */
    boolean hasErrors();

    /**
     * Get the current context string.
     *
     * @return the context string.
     */
    String getContext();

    /**
     * Add the name parameter to the current context.
     *
     * @param name
     */
    void pushContextName(final String name);

    /**
     * Remove the last context name added.
     */
    void popContextName();

    /**
     * This simply throws a RTException. A client calls this if 1) there is one or
     * more error messages reported and 2) the client wishes to stop processing.
     * Implementations of this method should only throw the RTException if there
     * have been errors reported - if there are no errors, then this method does
     * nothing.
     *
     * @throws RecorderException
     */
    void throwRTException() throws RecorderException;

    /**
     * Add an Exception.
     *
     * @param ex the Exception added.
     * @throws RecorderException if too many error messages have been added.
     */
    void reportError(final Exception ex) throws RecorderException;

    /**
     * Add an Exception and extra informaton.
     *
     * @param ex   the Exception added.
     * @param info extra information (not meant to be part of printed message)
     * @throws RecorderException if too many error messages have been added.
     */
    void reportError(final Exception ex, final Object info) throws RecorderException;

    /**
     * Add an error message.
     *
     * @param msg the text of the error message.
     * @throws RecorderException if too many error messages have been added.
     */
    void reportError(final String msg) throws RecorderException;

    /**
     * Add an error message and extra information.
     *
     * @param msg  the text of the error message.
     * @param info extra information (not meant to be part of printed message)
     * @throws RecorderException if too many error messages have been added.
     */
    void reportError(final String msg, final Object info) throws RecorderException;

    /**
     * Add a warning message.
     *
     * @param msg the text of the warning message.
     */
    void reportWarning(final String msg);

    /**
     * Add a warning message and extra information.
     *
     * @param msg  the text of the warning message.
     * @param info extra information (not meant to be part of printed message)
     */
    void reportWarning(final String msg, final Object info);

    /**
     * Add an informational message.
     *
     * @param msg the text of the info message.
     */
    void reportInfo(final String msg);

    /**
     * Add an informational message and extra information.
     *
     * @param msg  the text of the info message.
     * @param info extra information (not meant to be part of printed message)
     */
    void reportInfo(final String msg, final Object info);
}
