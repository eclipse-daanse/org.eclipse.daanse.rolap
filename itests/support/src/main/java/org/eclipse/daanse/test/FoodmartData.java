/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.test;

import java.net.URL;
import java.util.Map;

import org.eclipse.daanse.cwm.testkit.api.DataSupplier;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;

/**
 * The one Foodmart {@link DataSupplier} for the suite. Every test class that
 * declares this class in {@code @RolapContextTest(data = ...)} shares the same
 * loaded database for the run — the data-supplier class is the database's
 * identity, so a per-class copy of this delegate would cost a full Foodmart
 * load per class.
 */
public class FoodmartData implements DataSupplier {

    @Override
    public Map<String, URL> csvResources() {
        return new FoodmartTestInstance().dataSupplier().csvResources();
    }
}
