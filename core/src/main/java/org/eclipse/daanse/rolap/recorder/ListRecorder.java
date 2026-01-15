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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;

/**
 * Implementation of {@link MessageRecorder} that records each message in a
 * {@link List}. The calling code can then access the list and take actions as
 * needed.
 */
public class ListRecorder extends AbstractRecorder {

    private final List<Entry> errorList;
    private final List<Entry> warnList;
    private final List<Entry> infoList;

    public ListRecorder() {
        errorList = new ArrayList<>();
        warnList = new ArrayList<>();
        infoList = new ArrayList<>();
    }

    @Override
    public void clear() {
        super.clear();
        errorList.clear();
        warnList.clear();
        infoList.clear();
    }

    public Iterator<Entry> getErrorEntries() {
        return errorList.iterator();
    }

    public Iterator<Entry> getWarnEntries() {
        return warnList.iterator();
    }

    public Iterator<Entry> getInfoEntries() {
        return infoList.iterator();
    }

    @Override
    protected void recordMessage(final String msg, final Object info, final MessageType msgType) {
        String context = getContext();

        Entry e = new Entry(context, msg, msgType, info);
        switch (msgType) {
            case INFO -> infoList.add(e);
            case WARN -> warnList.add(e);
            case ERROR -> errorList.add(e);
        }
    }

    public void logInfoMessage(final Logger logger) {
        if (hasInformation()) {
            logMessage(getInfoEntries(), logger);
        }
    }

    public void logWarningMessage(final Logger logger) {
        if (hasWarnings()) {
            logMessage(getWarnEntries(), logger);
        }
    }

    public void logErrorMessage(final Logger logger) {
        if (hasErrors()) {
            logMessage(getErrorEntries(), logger);
        }
    }

    static void logMessage(Iterator<Entry> it, Logger logger) {
        while (it.hasNext()) {
            Entry e = it.next();
            logMessage(e, logger);
        }
    }

    static void logMessage(final Entry e, final Logger logger) {
        logMessage(e.context(), e.message(), e.msgType(), logger);
    }

    /**
     * Entry is a Info, Warning or Error message. This is the object stored in the
     * Lists MessageRecorder's info, warning and error message lists.
     */
    public static record Entry(String context, String message, MessageType msgType, Object info) {

    }
}
