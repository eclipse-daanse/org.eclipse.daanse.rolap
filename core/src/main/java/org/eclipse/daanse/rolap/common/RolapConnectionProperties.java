/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2003-2005 Julian Hyde
 * Copyright (C) 2005-2019 Hitachi Vantara
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

package org.eclipse.daanse.rolap.common;

import java.lang.ref.SoftReference;

/**
 * RolapConnectionProperties enumerates the allowable values of
 * keywords in a Mondrian connect string.
 *
 * Note to developers: If you add or modify a connection-string
 * property, you must also modify the
 * <a target="_top"
 * href="{@docRoot}/../configuration.html#Connect_string_properties">
 * Configuration Specification</a>.
 *
 * @author jhyde, Mar 18, 2003
 */
@Deprecated
public enum RolapConnectionProperties {


  /**
   * The "Role" property is the name of the mondrian.olap.Role role
   * to adopt. If not specified, the connection uses a role which has access
   * to every object in the schema.
   */
  Role,


  /**
   * The "UseSchemaPool" property disables the schema cache. If false, the
   * schema is not shared with connections which have a textually identical
   * schema. Default is "true".
   */
  UseSchemaPool,




  /**
   * The "Locale" property is the requested Locale for the
   * LocalizingDynamicSchemaProcessor.  Example values are "en",
   * "en_US", "hu". If Locale is not specified, then the name of system's
   * default will be used, as per {@link java.util.Locale#getDefault()}.
   */
  Locale,

  /**
   * The "Ignore" property is a boolean value. If true, mondrian ignores
   * warnings and non-fatal errors while loading the schema. The resulting
   * errors can be obtained by calling
   * org.eclipse.daanse.olap.api.Schema#getWarnings.
   */
  Ignore,


  /**
   * The "PinSchemaTimeout" defines how much time must Mondrian
   * keep a hard reference to schema objects within the pool of schemas.
   *
   * After the timeout is reached, the hard reference will be cleared
   * and the schema will be made a candidate for garbage collection. If the
   * timeout wasn't reached yet and a second query requires the same schema,
   * the timeout will be re-computed from the time of the second access and
   * a new hard reference is established until the new timer reaches its end.
   *
   * If the timeout is equal to zero, the schema will get
   * pinned permanently. It is inadvisable to use this mode when using
   * a DynamicSchemaProcessor at the risk of filling up the memory.
   *
   * If the timeout is a negative value, the reference will behave
   * the same as a {@link SoftReference}. This is the default behavior.
   *
   * The timeout value must be provided as a String representing
   * both the time value and the time unit. For example, 1 second is
   * represented as "1s". Valid time units are [d, h, m, s, ms],
   * representing respectively days, hours, minutes, seconds and
   * milliseconds.
   *
   * Defaults to "-1s".
   */
  PinSchemaTimeout,

  /**
   * The "AggregateScanSchema" property is the name of the database schema
   * to scan when looking for aggregate tables.  If defined, Mondrian will
   * only look for aggregate tables within this schema.  This means that
   * all aggregate tables, including explicitly defined tables must be in
   * this schema.  If not defined, Mondrian will scan every schema that
   * the database connection has access to when looking for aggregate
   * tables.
   */
  AggregateScanSchema,

  /**
   * The "AggregateScanCatalog" property is the name of the database
   * catalog to scan when looking for aggregate tables.  If defined,
   * Mondrian will only look for aggregate tables within this catalog.
   * This means that all aggregate tables, including explicitly defined
   * tables must be in this catalog.  If not defined, Mondrian will
   * scan every catalog the database connection has access to when
   * looking for aggregate tables.
   */
  AggregateScanCatalog;


}
