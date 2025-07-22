/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2001-2005 Julian Hyde
 * Copyright (C) 2005-2017 Hitachi Vantara and others
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

import org.eclipse.daanse.olap.api.element.Level;
import org.eclipse.daanse.olap.api.element.ParentChildMember;

/**
 * A RolapMember is a member of a {@link RolapHierarchy}. There are
 * sub-classes for {@link RolapStoredMeasure}, {@link RolapCalculatedMember}.
 *
 * @author jhyde
 * @since 10 August, 2001
 */
public interface RolapMember extends ParentChildMember, RolapCalculation {
    @Override
	RolapMember getParentMember();
    @Override
	RolapHierarchy getHierarchy();
    @Override
	RolapLevel getLevel();

    /** @deprecated will be removed in mondrian-4.0 */
    @Deprecated
	boolean isAllMember();
    
    void setLevel(Level level);
}
