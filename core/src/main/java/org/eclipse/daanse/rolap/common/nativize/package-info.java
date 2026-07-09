/*
* Copyright (c) 2023 Contributors to the Eclipse Foundation.
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
*
* Contributors:
*   SmartCity Jena - initial
*   Stefan Bischof (bipolis.org) - initial
*/
//TODO: RM EXPORT
/**
 * Native SQL evaluation of MDX set expressions. Given a set-valued function call
 * (crossjoin, {@code Filter}, {@code TopCount}/{@code BottomCount}, {@code level.members},
 * {@code member.children}, {@code member.descendants}), the classes here decide whether the set
 * can be computed in SQL and, if so, build the corresponding tuple query: a
 * {@link org.eclipse.daanse.rolap.common.nativize.RolapNativeSet.SetConstraint} contributes the
 * context and per-argument member restrictions, while
 * {@link org.eclipse.daanse.rolap.common.nativize.RolapNativeSql} compiles the filter condition
 * and top-count ordering expression into dialect-free SQL nodes.
 */
@org.osgi.annotation.bundle.Export
@org.osgi.annotation.versioning.Version("0.0.1")
package org.eclipse.daanse.rolap.common.nativize;