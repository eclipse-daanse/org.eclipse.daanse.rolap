/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (c) 2002-2018 Hitachi Vantara..  All rights reserved.
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

package org.eclipse.daanse.rolap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.rolap.common.star.RolapStar;
import org.eclipse.daanse.rolap.element.RolapCatalog;
import org.eclipse.daanse.rolap.element.RolapCube;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource;
import org.eclipse.daanse.rolap.mapping.model.database.source.SourceFactory;
import org.eclipse.daanse.rolap.mapping.model.database.source.SqlStatement;
import org.eclipse.daanse.rolap.mapping.model.database.source.TableSource;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;
/**
 * Unit test for {@link RolapStar}.
 *
 * @author pedrovale
 */
@RolapContextTest(FoodmartTestInstance.class)
class RolapStarTest {

    static class RolapStarForTests extends RolapStar {
        public RolapStarForTests(
            final RolapCatalog schema,
            final Context<?> context,
            final RelationalSource fact)
        {
            super(schema, context, fact);
        }

        public RelationalSource cloneRelationForTests(
            RelationalSource rel,
            String possibleName)
        {
            return cloneRelation(rel, possibleName);
        }
    }

    RolapStar getRolapStar(Connection con, String starName) {
        RolapCube cube =
            (RolapCube) con.getCatalog().lookupCube(starName).orElseThrow();
        return cube.getStar();
    }

    RolapStarForTests getStar(Connection connection, String starName) {
        RolapStar rs =  getRolapStar(connection, starName);

        return new RolapStarForTests(
            rs.getCatalog(),
            rs.getContext(),
            rs.getFactTable().getRelation());
    }

    /**
     * Tests that given a {@link MappingTableQuery}, cloneRelation
     * respects the existing filters.
     */
    @Test
    void testCloneRelationWithFilteredTable(Context<?> context) {
      RolapStarForTests rs = getStar(context.getConnectionWithDefaultRole(), "sales");
      Schema ds = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createSchema();
      ds.setName("Sechema");
      Table pt = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createTable();
      pt.setName("TestTable");
      ds.getOwnedElement().add(pt);
      SqlStatement ss = SourceFactory.eINSTANCE.createSqlStatement();
      ss.setBody("Alias.clicked = 'true'");
      ss.getDialects().add("generic");
      TableSource original = SourceFactory.eINSTANCE.createTableSource();
      original.setTable(pt);
      original.setAlias("Alias");
      original.setSqlWhereExpression(ss);


      TableSource cloned = (TableSource)rs.cloneRelationForTests(
          original,
          "NewAlias");

      assertEquals("NewAlias", cloned.getAlias());
      assertEquals("TestTable", cloned.getTable().getName());
      assertNotNull(cloned.getSqlWhereExpression());
      assertEquals("NewAlias.clicked = 'true'", cloned.getSqlWhereExpression().getBody());
  }

}
