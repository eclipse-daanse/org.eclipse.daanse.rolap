/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2004-2005 TONBELLER AG
 * Copyright (C) 2006-2017 Hitachi Vantara and others
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


package org.eclipse.daanse.rolap.common.sql;

/**
 * Restricts the members that are fetched by SqlMemberSource.
 *
 * @see org.eclipse.daanse.rolap.common.SqlMemberSource
 *
 * @author av
 * @since Nov 2, 2005
 */
public interface SqlConstraint {

   /**
    * Returns a key that becomes part of the key for caching the
    * result of the SQL query. So SqlConstraint instances that
    * produce the same SQL resultset must return equal keys
    * in terms of equal() and hashCode().
    * @return valid key or null to prevent the result from being cached
    */
    Object getCacheKey();
}
