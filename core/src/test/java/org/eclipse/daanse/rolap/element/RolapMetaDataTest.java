/*
* Copyright (c) 2026 Contributors to the Eclipse Foundation.
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
*/
package org.eclipse.daanse.rolap.element;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;

import org.eclipse.daanse.olap.api.element.MetaData;
import org.eclipse.daanse.olap.api.element.OlapElement.LocalizedProperty;
import org.eclipse.daanse.rolap.mapping.model.catalog.Catalog;
import org.eclipse.daanse.rolap.mapping.model.catalog.CatalogFactory;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.CubeFactory;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.PhysicalCube;


import org.junit.jupiter.api.Test;

import org.eclipse.daanse.rolap.mapping.model.provider.util.CwmHelper;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.util.Descriptions;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.TaggedValues;
class RolapMetaDataTest {

    private static PhysicalCube attachedCube() {
        Catalog catalog = CatalogFactory.eINSTANCE.createCatalog();
        catalog.setName("Sales");
        PhysicalCube cube = CubeFactory.eINSTANCE.createPhysicalCube();
        cube.setName("SalesCube");
        catalog.getOwnedElement().add(cube);
        return cube;
    }

    @Test
    void foldsDescriptionsToLocalizedKeys() {
        PhysicalCube cube = attachedCube();
        Descriptions.describe(cube, CwmHelper.TYPE_CAPTION, "de-DE", "Verkaeufe");
        Descriptions.describe(cube, CwmHelper.TYPE_DOCUMENTATION, "en", "Sales cube");

        MetaData metaData = RolapMetaData.createMetaData(cube);
        assertEquals("Verkaeufe",
                metaData.getLocalized(LocalizedProperty.CAPTION, Locale.GERMANY).orElse(null));
        assertEquals("Sales cube",
                metaData.getLocalized(LocalizedProperty.DESCRIPTION, Locale.ENGLISH).orElse(null));
    }

    @Test
    void taggedValuesWinOverFoldedDescriptions() {
        PhysicalCube cube = attachedCube();
        Descriptions.describe(cube, CwmHelper.TYPE_CAPTION, "de-DE", "Aus der Description");
        TaggedValues.set(cube, "caption.de_DE", "Aus der Annotation");

        MetaData metaData = RolapMetaData.createMetaData(cube);
        assertEquals("Aus der Annotation",
                metaData.getLocalized(LocalizedProperty.CAPTION, Locale.GERMANY).orElse(null));
    }

    @Test
    void neutralTextsAreNotFolded() {
        PhysicalCube cube = attachedCube();
        Descriptions.describe(cube, CwmHelper.TYPE_DOCUMENTATION, null, "Neutral text");

        MetaData metaData = RolapMetaData.createMetaData(cube);
        assertTrue(metaData.getLocalized(LocalizedProperty.DESCRIPTION, Locale.ENGLISH).isEmpty());
    }
}
