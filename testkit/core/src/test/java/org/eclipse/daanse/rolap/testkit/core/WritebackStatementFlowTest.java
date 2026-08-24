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
 *   SmartCity Jena - initial
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.rolap.testkit.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.eclipse.daanse.cwm.testkit.database.DatabaseLayer;
import org.eclipse.daanse.jdbc.datasource.testkit.api.ActiveDatabase;
import org.eclipse.daanse.jdbc.datasource.testkit.api.DatabaseProvider;
import org.eclipse.daanse.olap.api.Command;
import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.api.element.Cube;
import org.eclipse.daanse.olap.api.query.component.QueryComponent;
import org.eclipse.daanse.olap.api.query.component.TransactionCommand;
import org.eclipse.daanse.olap.api.query.component.Update;
import org.eclipse.daanse.rolap.mapping.instance.emf.tutorial.writeback.table.CatalogSupplier;
import org.eclipse.daanse.rolap.mapping.instance.emf.tutorial.writeback.table.TableDatabaseSupplier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The statements a writeback conversation is made of, against a catalog that
 * actually declares a writeback table.
 * <p>
 * Every client that writes back - Excel, ADOMD, Flexmonster - sends the same
 * four: {@code BEGIN TRANSACTION}, one or more {@code UPDATE CUBE}, and
 * {@code COMMIT} or {@code ROLLBACK}, each as the text of an XMLA
 * {@code Statement}. They have to survive the whole way from the parser to a
 * {@link QueryComponent} the connector can act on, because the transaction is
 * what opens the scenario the updates accumulate into: without it the very
 * first request of a writeback fails and nothing else is ever reached.
 */
class WritebackStatementFlowTest {

    private static Connection connection;
    private static Cube cube;

    @BeforeAll
    static void openCatalog() throws Exception {
        ActiveDatabase db = DatabaseProvider.selected().activate();
        TableDatabaseSupplier database = new TableDatabaseSupplier();
        DatabaseLayer.apply(db.dataSource(), db.dialect(), database.schema());

        TestContext context = new TestContext(db.dataSource(), db.dialect(), new CatalogSupplier());
        connection = ((Context<?>) context).getConnectionWithDefaultRole();
        cube = connection.getCatalog().lookupCube("C").orElseThrow();
    }

    @Test
    void beginBecomesATransactionCommand() {
        QueryComponent component = connection.parseStatement("BEGIN TRANSACTION");

        assertThat(component).isInstanceOf(TransactionCommand.class);
        assertThat(((TransactionCommand) component).getCommand()).isEqualTo(Command.BEGIN);
    }

    @Test
    void commitAndRollbackDoToo() {
        assertThat(((TransactionCommand) connection.parseStatement("COMMIT TRANSACTION")).getCommand())
                .isEqualTo(Command.COMMIT);
        assertThat(((TransactionCommand) connection.parseStatement("ROLLBACK TRANSACTION")).getCommand())
                .isEqualTo(Command.ROLLBACK);
    }

    @Test
    void updateCubeBecomesAnUpdate() {
        QueryComponent component = connection
                .parseStatement("UPDATE CUBE [C] SET ([Measures].[Measure1]) = 42 USE_EQUAL_ALLOCATION");

        assertThat(component).isInstanceOf(Update.class);
        assertThat(((Update) component).getCubeName()).isEqualTo("C");
        assertThat(((Update) component).getUpdateClauses()).hasSize(1);
    }

    /**
     * The {@code BY} weight is optional, and the statement every real client sends
     * leaves it out. Taking it unconditionally made the ordinary form fail to
     * convert after it had parsed - which is the form in every recorded writeback.
     */
    @Test
    void theAllocationWeightIsOptionalAndReadWhenGiven() {
        Update without = (Update) connection
                .parseStatement("UPDATE CUBE [C] SET ([Measures].[Measure1]) = 42 USE_EQUAL_ALLOCATION");
        assertThat(without.getUpdateClauses().get(0).getWeight()).isNull();

        Update with = (Update) connection.parseStatement(
                "UPDATE CUBE [C] SET ([Measures].[Measure1]) = 42 USE_WEIGHTED_ALLOCATION BY [Measures].[Measure2]");
        assertThat(with.getUpdateClauses().get(0).getWeight()).isNotNull();
    }

    /** The column a client reads before it offers writeback at all. */
    @Test
    void theCubeSaysItCanBeWrittenTo() {
        assertThat(cube.isWriteEnabled()).isTrue();
    }

    /**
     * The bracket every query of this cube runs in: the fact is rewritten to carry
     * the writeback table and the session's rows, and is the same object again
     * afterwards. A cube left rewritten would answer the next session with values
     * it never wrote.
     */
    @Test
    void pendingRowsAreBracketedAndTheFactComesBack() {
        Object before = ((org.eclipse.daanse.rolap.element.RolapCube) cube).getFact();

        Object during = cube.withPendingRows(List.of(),
                () -> ((org.eclipse.daanse.rolap.element.RolapCube) cube).getFact());

        assertThat(during).isNotSameAs(before);
        assertThat(((org.eclipse.daanse.rolap.element.RolapCube) cube).getFact()).isSameAs(before);
    }
}
