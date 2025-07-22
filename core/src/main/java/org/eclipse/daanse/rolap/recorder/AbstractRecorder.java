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

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;

/**
 * Abstract implemention of the {@link MessageRecorder} interface.
 *
 * @author Richard M. Emberson
 */
public abstract class AbstractRecorder implements MessageRecorder {

    private final static String tooManyMessageRecorderErrors = "Context ''{0}'': Exceeded number of allowed errors ''{1,number}''";
    private final static String forceMessageRecorderError = "Context ''{0}'': Client forcing return with errors ''{1,number}''";

    /**
     * Helper method to format a message and write to logger.
     */
    public static void logMessage(final String context, final String msg, final MessageType msgType,
            final Logger logger) {
        StringBuilder buf = new StringBuilder(64);
        buf.append(context);
        buf.append(": ");
        buf.append(msg);
        String logMsg = buf.toString();
        switch (msgType) {
        case INFO:
            logger.info(logMsg);
            break;
        case WARN:
            logger.warn(logMsg);
            break;
        case ERROR:
            logger.error(logMsg);
            break;
        default:
            logger.warn("Unknown message type enum \"{}\" for message: {}", msgType, logMsg);
        }
    }

    enum MessageType {
        INFO, WARN, ERROR
    }

    public static final int DEFAULT_MSG_LIMIT = 10;

    private final int errorMsgLimit;
    private final List<String> contexts;
    private int errorMsgCount;
    private int warningMsgCount;
    private int infoMsgCount;
    private String contextMsgCache;
    private long startTime;

    protected AbstractRecorder() {
        this(DEFAULT_MSG_LIMIT);
    }

    protected AbstractRecorder(final int errorMsgLimit) {
        this.errorMsgLimit = errorMsgLimit;
        this.contexts = new ArrayList<>();
        this.startTime = System.currentTimeMillis();
    }

    /**
     * Resets this MessageRecorder.
     */
    @Override
    public void clear() {
        errorMsgCount = 0;
        warningMsgCount = 0;
        infoMsgCount = 0;
        contextMsgCache = null;
        contexts.clear();
        this.startTime = System.currentTimeMillis();
    }

    @Override
    public long getStartTimeMillis() {
        return this.startTime;
    }

    @Override
    public long getRunTimeMillis() {
        return (System.currentTimeMillis() - this.startTime);
    }

    @Override
    public boolean hasInformation() {
        return (infoMsgCount > 0);
    }

    @Override
    public boolean hasWarnings() {
        return (warningMsgCount > 0);
    }

    @Override
    public boolean hasErrors() {
        return (errorMsgCount > 0);
    }

    public int getInfoCount() {
        return infoMsgCount;
    }

    public int getWarningCount() {
        return warningMsgCount;
    }

    public int getErrorCount() {
        return errorMsgCount;
    }

    @Override
    public String getContext() {
        // heavy weight
        if (contextMsgCache == null) {
            final StringBuilder buf = new StringBuilder();
            int k = 0;
            for (String name : contexts) {
                if (k++ > 0) {
                    buf.append(':');
                }
                buf.append(name);
            }
            contextMsgCache = buf.toString();
        }
        return contextMsgCache;
    }

    @Override
    public void pushContextName(final String name) {
        // light weight
        contexts.add(name);
        contextMsgCache = null;
    }

    @Override
    public void popContextName() {
        // light weight
        contexts.remove(contexts.size() - 1);
        contextMsgCache = null;
    }

    @Override
    public void throwRTException() throws RecorderException {
        if (hasErrors()) {
            final String errorMsg = MessageFormat.format(forceMessageRecorderError, getContext(), errorMsgCount);
            throw new RecorderException(errorMsg);
        }
    }

    @Override
    public void reportError(final Exception ex) throws RecorderException {
        reportError(ex, null);
    }

    @Override
    public void reportError(final Exception ex, final Object info) throws RecorderException {
        reportError(ex.toString(), info);
    }

    @Override
    public void reportError(final String msg) throws RecorderException {
        reportError(msg, null);
    }

    @Override
    public void reportError(final String msg, final Object info) throws RecorderException {
        errorMsgCount++;
        recordMessage(msg, info, MessageType.ERROR);

        if (errorMsgCount >= errorMsgLimit) {
            final String errorMsg = MessageFormat.format(tooManyMessageRecorderErrors, getContext(),
                    String.valueOf(errorMsgCount));
            throw new RecorderException(errorMsg);
        }
    }

    @Override
    public void reportWarning(final String msg) {
        reportWarning(msg, null);
    }

    @Override
    public void reportWarning(final String msg, final Object info) {
        warningMsgCount++;
        recordMessage(msg, info, MessageType.WARN);
    }

    @Override
    public void reportInfo(final String msg) {
        reportInfo(msg, null);
    }

    @Override
    public void reportInfo(final String msg, final Object info) {
        infoMsgCount++;
        recordMessage(msg, info, MessageType.INFO);
    }

    /**
     * Handles a message. Classes implementing this abstract class must provide an
     * implemention of this method; it receives all warning/error messages.
     *
     * @param msg     the error or warning message.
     * @param info    the information Object which might be null.
     * @param msgType one of the message type enum values
     */
    protected abstract void recordMessage(String msg, Object info, MessageType msgType);
}
