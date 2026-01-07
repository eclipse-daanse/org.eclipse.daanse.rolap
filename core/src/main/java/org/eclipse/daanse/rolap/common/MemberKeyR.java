/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2002-2005 Julian Hyde
 * Copyright (C) 2005-2017 Hitachi Vantara and others
 * All Rights Reserved.
 *
 * jhyde, 21 March, 2002
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

import org.eclipse.daanse.rolap.element.RolapLevel;
import org.eclipse.daanse.rolap.element.RolapMember;

/**
 * Immutable key for a member, consisting of its parent member and value.
 *
 * @param parent the parent member, or null for root members
 * @param value the key value for this member
 * @author jhyde
 * @since 21 March, 2002
 */
public record MemberKeyR(RolapMember parent, Object value) {

    /**
     * Returns the level of the member that this key represents.
     *
     * @return Member level, or null if is root member
     */
    public RolapLevel getLevel() {
        if (parent == null) {
            return null;
        }
        final RolapLevel level = parent.getLevel();
        if (level.isParentChild()) {
            return level;
        }
        return (RolapLevel) level.getChildLevel();
    }
}
