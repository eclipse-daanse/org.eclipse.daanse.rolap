/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation.
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
package org.eclipse.daanse.rolap.common.statement;

import org.eclipse.daanse.olap.api.Statement;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.api.execution.Execution;
import org.eclipse.daanse.olap.execution.ExecutionImpl;

/**
 * A statement that can be used for all of the various internal
 * operations, such as resolving MDX identifiers, that require a
 * {@link Statement} and an {@link ExecutionImpl}.
 *
 * The statement needs to be reentrant because there are many such
 * operations; several of these operations might be active at one time. We
 * don't want to create a new statement for each, but just one internal
 * statement for each connection. The statement shouldn't have a unique
 * execution. For this reason, we don't use the inherited {execution}
 * field.
 *
 * But there is a drawback. If we can't find the unique execution, the
 * statement cannot be canceled or time out. If you want that behavior
 * from an internal statement, use the base class: create a new
 * {@link InternalStatement} for each operation.
 */
public class ReentrantInternalStatement extends InternalStatement {

    /**
     * Creates a StatementImpl.
     *
     * @param connection
     */
    public ReentrantInternalStatement(Connection connection) {
        super(connection);
    }

    @Override
  public synchronized void start( Execution execution ) {
    // Unlike StatementImpl, there is not a unique execution. An
    // internal statement can execute several at the same time. So,
    // we don't set this.execution.
    execution.start();
  }

  @Override
  public synchronized void end( Execution execution ) {
    execution.end();
  }

  @Override
  public void close() {
    // do not close
  }
}

