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
 *   SmartCity Jena, Stefan Bischof - initial
 */
package org.eclipse.daanse.rolap.testkit.member;

import org.eclipse.daanse.olap.cache.CachePolicy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;



import org.eclipse.daanse.cwm.testkit.database.DatabaseLayer;
import org.eclipse.daanse.jdbc.datasource.testkit.api.ActiveDatabase;
import org.eclipse.daanse.jdbc.datasource.testkit.api.DatabaseProvider;
import org.eclipse.daanse.olap.api.cache.CacheControl;
import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.api.result.Result;
import org.eclipse.daanse.rolap.common.member.CachingMemberReader;
import org.eclipse.daanse.rolap.common.member.NoCacheMemberReader;
import org.eclipse.daanse.rolap.element.RolapCubeMember;
import org.eclipse.daanse.rolap.testkit.core.TestContext;
import org.junit.jupiter.api.Test;

/**
 * {@code daanse:cache.members} down to the SHARED member reader: a shared
 * hierarchy whose using cubes all run members=off gets a
 * {@link NoCacheMemberReader} after catalog load, and member cache edits are
 * refused only while a cube USING the affected hierarchy caches members —
 * unrelated cubes with members=on do not block them.
 */
class SharedMemberCachePolicyTest {

    @Test
    void allUsingCubesOffSwapsTheSharedReaderAndAllowsEdits() throws Exception {
        Connection connection = connect("PolicyAllOff", new SharedOrdinalCatalogSupplier("off", "off", false));

        RolapCubeMember member = anyMember(connection, SharedOrdinalCatalogSupplier.CUBE_A,
                "[SharedDim].[KeyHierarchy].[KeyLevel].Members");
        assertThat(member.getHierarchy().getRolapHierarchy().getMemberReader())
                .isInstanceOf(NoCacheMemberReader.class);

        CacheControl cacheControl = connection.getCacheControl(null);
        // no using cube caches members: the edit must be accepted
        cacheControl.execute(cacheControl.createDeleteCommand(member));
    }

    @Test
    void oneCachingUsingCubeKeepsTheSharedReaderAndRefusesEdits() throws Exception {
        Connection connection = connect("PolicyMixed", new SharedOrdinalCatalogSupplier("on", "off", false));

        RolapCubeMember member = anyMember(connection, SharedOrdinalCatalogSupplier.CUBE_B,
                "[SharedDim].[KeyHierarchy].[KeyLevel].Members");
        assertThat(member.getHierarchy().getRolapHierarchy().getMemberReader())
                .isInstanceOf(CachingMemberReader.class);

        CacheControl cacheControl = connection.getCacheControl(null);
        assertThatThrownBy(() -> cacheControl.execute(cacheControl.createDeleteCommand(member)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(SharedOrdinalCatalogSupplier.CUBE_A)
                .hasMessageContaining("KeyHierarchy");
    }

    @Test
    void unrelatedCachingCubeDoesNotBlockEditsOnOtherHierarchies() throws Exception {
        // CubeA caches members but does not use SoloDim (only CubeB does, with members=off)
        Connection connection = connect("PolicyUnrelated", new SharedOrdinalCatalogSupplier("on", "off", true));

        RolapCubeMember soloMember = anyMember(connection, SharedOrdinalCatalogSupplier.CUBE_B,
                "[SoloDim].[SoloHierarchy].[SoloLevel].Members");
        assertThat(soloMember.getHierarchy().getRolapHierarchy().getMemberReader())
                .isInstanceOf(NoCacheMemberReader.class);

        CacheControl cacheControl = connection.getCacheControl(null);
        // the affected hierarchy has no caching user; CubeA's members=on is unrelated
        cacheControl.execute(cacheControl.createDeleteCommand(soloMember));

        RolapCubeMember sharedMember = anyMember(connection, SharedOrdinalCatalogSupplier.CUBE_B,
                "[SharedDim].[KeyHierarchy].[KeyLevel].Members");
        assertThatThrownBy(() -> cacheControl.execute(cacheControl.createDeleteCommand(sharedMember)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(SharedOrdinalCatalogSupplier.CUBE_A);
    }

    @Test
    void hierarchyTagOffOverridesCachingCubes() throws Exception {
        // both cubes cache members, but the shared hierarchy itself is tagged off
        Connection connection = connect("PolicyHierOff",
                new SharedOrdinalCatalogSupplier("on", "on", true, "off"));

        RolapCubeMember sharedMember = anyMember(connection, SharedOrdinalCatalogSupplier.CUBE_B,
                "[SharedDim].[KeyHierarchy].[KeyLevel].Members");
        assertThat(sharedMember.getHierarchy().getRolapHierarchy().getMemberReader())
                .isInstanceOf(NoCacheMemberReader.class);

        // the untagged solo hierarchy keeps the cube policy: caching stays
        RolapCubeMember soloMember = anyMember(connection, SharedOrdinalCatalogSupplier.CUBE_B,
                "[SoloDim].[SoloHierarchy].[SoloLevel].Members");
        assertThat(soloMember.getHierarchy().getRolapHierarchy().getMemberReader())
                .isInstanceOf(CachingMemberReader.class);

        CacheControl cacheControl = connection.getCacheControl(null);
        // effective members=off on the tagged hierarchy: the edit is accepted
        cacheControl.execute(cacheControl.createDeleteCommand(sharedMember));
    }

    /** First row-axis member of the given member set in the given cube. */
    private static RolapCubeMember anyMember(Connection connection, String cube, String memberSet) {
        String mdx = """
                SELECT {[Measures].[Val]} ON COLUMNS,
                       %s ON ROWS
                FROM [%s]
                """.formatted(memberSet, cube);
        Result result = connection.execute(connection.parseQuery(mdx));
        Member member = result.getAxes()[1].getPositions().get(0).get(0);
        return (RolapCubeMember) member;
    }

    private static Connection connect(String databaseName, SharedOrdinalCatalogSupplier supplier)
            throws Exception {
        ActiveDatabase db = DatabaseProvider.selected().activate(databaseName);
        DatabaseLayer.apply(db.dataSource(), db.dialect(), supplier.schema());
        SharedOrdinalCatalogSupplier.insertRows(db.dataSource());
        TestContext ctx = new TestContext(db.dataSource(), db.dialect(), supplier);
        return ((Context<?>) ctx).getConnectionWithDefaultRole();
    }
}
