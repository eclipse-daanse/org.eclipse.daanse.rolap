/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2021 Hitachi Vantara and others
 * All Rights Reserved.
 *
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
package org.eclipse.daanse.test;

/**
 * TestAppender captures log4j events for unit testing.
 * 
 * @author benny
 */
//public class TestAppender extends AbstractAppender {
public class TestAppender {
  //private final List<LogEvent> logEvents = new ArrayList<>();

  //TODO need slf4j implementation
  public TestAppender() {
    //super( "TestAppender", null, PatternLayout.createDefaultLayout(), true, Property.EMPTY_ARRAY );
  }

  /*
  @Override
  public void append( final LogEvent loggingEvent ) {
    logEvents.add( loggingEvent.toImmutable() );
  }

  public List<LogEvent> getLogEvents() {
    return logEvents;
  }

  public void clear() {
    logEvents.clear();
  }

   */
}
