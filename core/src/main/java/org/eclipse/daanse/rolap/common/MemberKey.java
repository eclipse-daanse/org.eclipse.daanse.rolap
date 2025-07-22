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

import java.util.Objects;

/**
 * MemberKey todo:
 *
 * @author jhyde
 * @since 21 March, 2002
 */
public class MemberKey {
    private final RolapMember parent;
    private final Object value;

    protected MemberKey(RolapMember parent, Object value) {
        this.parent = parent;
        this.value = value;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof MemberKey other)) {
            return false;
        }
        return Objects.equals(this.parent, other.parent)
            && Objects.equals(this.value, other.value);
    }

    @Override
    public int hashCode() {
        int h = 0;
        if (value != null) {
            h = value.hashCode();
        }
        if (parent != null) {
            h = (h * 31) + parent.hashCode();
        }
        return h;
    }

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
