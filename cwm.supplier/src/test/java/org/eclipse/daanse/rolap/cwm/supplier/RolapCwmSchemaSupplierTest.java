/*
* Copyright (c) 2026 Contributors to the Eclipse Foundation.
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
*
* Contributors:
*   Stefan Bischof (bipolis.org) - initial
*/
package org.eclipse.daanse.rolap.cwm.supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.rolap.api.RolapContext;
import org.eclipse.daanse.rolap.mapping.model.catalog.Catalog;
import org.eclipse.emf.common.util.BasicEList;
import org.eclipse.emf.common.util.EList;
import org.junit.jupiter.api.Test;

class RolapCwmSchemaSupplierTest {

    @Test
    void deliversSchemasOfMatchingRolapContext() {
        Schema schema = RelationalFactory.eINSTANCE.createSchema();
        schema.setName("SALES");
        EList<Schema> dbschemas = new BasicEList<>();
        dbschemas.add(schema);

        Catalog mapping = mock(Catalog.class);
        when(mapping.getName()).thenReturn("MyCatalog");
        when(mapping.getDbschemas()).thenReturn(dbschemas);

        RolapContext rolapContext = mock(RolapContext.class);
        when(rolapContext.getCatalogMapping()).thenReturn(mapping);

        Context<?> plainContext = mock(Context.class);

        RolapCwmSchemaSupplier supplier = new RolapCwmSchemaSupplier();
        supplier.bindContext(plainContext);
        supplier.bindContext(rolapContext);

        assertThat(supplier.schemasFor("MyCatalog")).containsExactly(schema);
        assertThat(supplier.schemasFor("Unknown")).isEmpty();
    }

    @Test
    void unbindRemovesContext() {
        Catalog mapping = mock(Catalog.class);
        when(mapping.getName()).thenReturn("MyCatalog");

        RolapContext rolapContext = mock(RolapContext.class);
        when(rolapContext.getCatalogMapping()).thenReturn(mapping);

        RolapCwmSchemaSupplier supplier = new RolapCwmSchemaSupplier();
        supplier.bindContext(rolapContext);
        supplier.unbindContext(rolapContext);

        assertThat(supplier.schemasFor("MyCatalog")).isEmpty();
    }
}
