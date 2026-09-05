/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *
 */
package org.eclipse.daanse.rolap;


import java.util.Optional;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.rolap.itests.utils.EmfUtil;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.CatalogSupplier;
import org.eclipse.daanse.rolap.mapping.model.access.common.AccessCatalogGrant;
import org.eclipse.daanse.rolap.mapping.model.access.common.AccessRole;
import org.eclipse.daanse.rolap.mapping.model.access.common.CatalogAccess;
import org.eclipse.daanse.rolap.mapping.model.access.common.CommonFactory;
import org.eclipse.daanse.rolap.mapping.model.access.database.AccessColumnGrant;
import org.eclipse.daanse.rolap.mapping.model.access.database.AccessDatabaseSchemaGrant;
import org.eclipse.daanse.rolap.mapping.model.access.database.AccessTableGrant;
import org.eclipse.daanse.rolap.mapping.model.access.database.ColumnAccess;
import org.eclipse.daanse.rolap.mapping.model.access.database.DatabaseFactory;
import org.eclipse.daanse.rolap.mapping.model.access.database.DatabaseSchemaAccess;
import org.eclipse.daanse.rolap.mapping.model.access.database.TableAccess;
import org.eclipse.daanse.rolap.mapping.model.catalog.Catalog;
import org.eclipse.daanse.rolap.mapping.model.catalog.impl.CatalogImpl;
import org.eclipse.daanse.rolap.mapping.model.provider.CatalogMappingSupplier;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Packages;
/*
public class RoleTestModifier  extends PojoMappingModifier {

    public RoleTestModifier(CatalogMapping catalog) {
        super(catalog);
    }

    @Override
    protected List<? extends AccessRoleMapping> catalogAccessRoles(CatalogMapping schema) {
        List<AccessRoleMapping> result = new ArrayList<>();
        result.addAll(super.catalogAccessRoles(schema));
        AccessColumnGrantMappingImpl columnGrant1 = AccessColumnGrantMappingImpl.builder().withColumn(look(FoodmartMappingSupplier.PAY_DATE_COLUMN_IN_SALARY)).withAccess(AccessColumn.ALL).build();
        AccessColumnGrantMappingImpl columnGrant2 = AccessColumnGrantMappingImpl.builder().withColumn(look(FoodmartMappingSupplier.EMPLOYEE_ID_COLUMN_IN_SALARY)).withAccess(AccessColumn.ALL).build();
        AccessColumnGrantMappingImpl columnGrant3 = AccessColumnGrantMappingImpl.builder().withColumn(look(FoodmartMappingSupplier.DEPARTMENT_ID_COLUMN_IN_SALARY)).withAccess(AccessColumn.NONE).build();

        AccessTableGrantMappingImpl tableGrant1 = AccessTableGrantMappingImpl.builder().withAccess(AccessTable.ALL).withTable(look(FoodmartMappingSupplier.SALES_FACT_1997_TABLE)).build();
        AccessTableGrantMappingImpl tableGrant2 = AccessTableGrantMappingImpl.builder().withAccess(AccessTable.ALL).withTable(look(FoodmartMappingSupplier.PRODUCT_TABLE)).build();
        AccessTableGrantMappingImpl tableGrant3 = AccessTableGrantMappingImpl.builder().withAccess(AccessTable.CUSTOM).withTable(look(FoodmartMappingSupplier.SALARY_TABLE))
                .withColumnGrants(List.of(columnGrant1, columnGrant2, columnGrant3)).build();

        AccessDatabaseSchemaGrantMappingImpl schemaGrant = AccessDatabaseSchemaGrantMappingImpl.builder().withAccess(AccessDatabaseSchema.CUSTOM)
                .withDatabaseSchema((DatabaseSchemaMappingImpl) look(FoodmartMappingSupplier.DATABASE_SCHEMA)).withTableGrants(List.of(tableGrant1, tableGrant2, tableGrant3)).build();

        result.add(AccessRoleMappingImpl.builder()
            .withName("Test")
            .withAccessCatalogGrants(List.of(
                AccessCatalogGrantMappingImpl.builder()
                    .withAccess(AccessCatalog.CUSTOM)
                    .withDatabaseSchemaGrants(List.of(schemaGrant))
                    .build()
            ))
            .build());
        return result;
    }
}
*/
public class RoleTestModifier implements CatalogMappingSupplier {

    private final CatalogImpl catalog;

    public RoleTestModifier(Catalog cat) {
        EcoreUtil.Copier copier = EmfUtil.copier((CatalogImpl) cat);
        this.catalog = (CatalogImpl) copier.get(cat);

        // Create column grants using RolapMappingFactory
        AccessColumnGrant columnGrant1 = DatabaseFactory.eINSTANCE.createAccessColumnGrant();
        columnGrant1.setColumn((Column) copier.get(CatalogSupplier.COLUMN_PAY_DATE_SALARY));
        columnGrant1.setColumnAccess(ColumnAccess.ALL);

        AccessColumnGrant columnGrant2 = DatabaseFactory.eINSTANCE.createAccessColumnGrant();
        columnGrant2.setColumn((Column) copier.get(CatalogSupplier.COLUMN_EMPLOYEE_ID_SALARY));
        columnGrant2.setColumnAccess(ColumnAccess.ALL);

        AccessColumnGrant columnGrant3 = DatabaseFactory.eINSTANCE.createAccessColumnGrant();
        columnGrant3.setColumn((Column) copier.get(CatalogSupplier.COLUMN_DEPARTMENT_ID_SALARY));
        columnGrant3.setColumnAccess(ColumnAccess.NONE);

        Optional<Table> oTable = Packages.available(catalog, Schema.class).get(0).getOwnedElement().stream().filter(Table.class::isInstance).map(Table.class::cast).filter(t -> "sales_fact_1997".equals(t.getName())).findAny();
        // Create table grants using RolapMappingFactory
        AccessTableGrant tableGrant1 = DatabaseFactory.eINSTANCE.createAccessTableGrant();
        tableGrant1.setTableAccess(TableAccess.ALL);
        //tableGrant1.setTable((Table) copier.get(CatalogSupplier.TABLE_SALES_FACT));
        tableGrant1.setTable(oTable.get());

        AccessTableGrant tableGrant2 = DatabaseFactory.eINSTANCE.createAccessTableGrant();
        tableGrant2.setTableAccess(TableAccess.ALL);
        tableGrant2.setTable((Table) copier.get(CatalogSupplier.TABLE_PRODUCT));

        AccessTableGrant tableGrant3 = DatabaseFactory.eINSTANCE.createAccessTableGrant();
        tableGrant3.setTableAccess(TableAccess.CUSTOM);
        tableGrant3.setTable((Table) copier.get(CatalogSupplier.TABLE_SALARY));
        tableGrant3.getColumnGrants().add(columnGrant1);
        tableGrant3.getColumnGrants().add(columnGrant2);
        tableGrant3.getColumnGrants().add(columnGrant3);

        // Create database schema grant using RolapMappingFactory
        AccessDatabaseSchemaGrant schemaGrant = DatabaseFactory.eINSTANCE.createAccessDatabaseSchemaGrant();
        schemaGrant.setDatabaseSchemaAccess(DatabaseSchemaAccess.CUSTOM);
        schemaGrant.setDatabaseSchema((Schema) copier.get(CatalogSupplier.DATABASE_SCHEMA_FOODMART));
        schemaGrant.getTableGrants().add(tableGrant1);
        schemaGrant.getTableGrants().add(tableGrant2);
        schemaGrant.getTableGrants().add(tableGrant3);

        // Create catalog grant using RolapMappingFactory
        AccessCatalogGrant catalogGrant = CommonFactory.eINSTANCE.createAccessCatalogGrant();
        catalogGrant.setCatalogAccess(CatalogAccess.CUSTOM);
        catalogGrant.getDatabaseSchemaGrants().add(schemaGrant);

        // Create access role using RolapMappingFactory
        AccessRole role = CommonFactory.eINSTANCE.createAccessRole();
        role.setName("Test");
        role.getAccessCatalogGrants().add(catalogGrant);

        // Add the role to the catalog copy
        this.catalog.getImportedElement().add(role);
    }

    @Override
    public Catalog get() {
        return catalog;
    }
}

