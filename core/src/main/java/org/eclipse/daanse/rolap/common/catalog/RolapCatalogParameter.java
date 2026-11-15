/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
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


package org.eclipse.daanse.rolap.common.catalog;

import org.eclipse.daanse.olap.api.type.Type;
import org.eclipse.daanse.olap.common.CatalogParameterBase;
import org.eclipse.daanse.rolap.element.RolapCatalog;

/**
 * A parameter a relational catalog declares. What such a parameter is comes from the shared base; what
 * this one adds is that it belongs to a catalog and puts itself on that catalog's list when it is made.
 */
public class RolapCatalogParameter extends CatalogParameterBase {

    private final RolapCatalog catalog;

    public RolapCatalogParameter(RolapCatalog catalog, String name, String defaultExpString, String description,
            Type type, boolean modifiable) {
        super(name, defaultExpString, description, type, modifiable);
        this.catalog = catalog;
        catalog.parameterList.add(this);
    }

    RolapCatalog getCatalog() {
        return catalog;
    }
}
