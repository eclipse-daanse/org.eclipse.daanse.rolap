/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (c) 2002-2020 Hitachi Vantara..  All rights reserved.
 * Copyright (c) 2021 Sergei Semenkov.  All rights reserved.
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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLDataException;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.MessageFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.eclipse.daanse.sql.model.type.BestFitColumnType;
import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.execution.Execution.Purpose;
import org.eclipse.daanse.olap.api.execution.ExecutionContext;
import org.eclipse.daanse.olap.api.execution.GuardedStatement;
import org.eclipse.daanse.olap.api.monitor.event.EventCommon;
import org.eclipse.daanse.olap.api.monitor.event.SqlStatementEndEvent;
import org.eclipse.daanse.olap.api.monitor.event.SqlStatementEventCommon;
import org.eclipse.daanse.olap.api.monitor.event.SqlStatementExecuteEvent;
import org.eclipse.daanse.olap.api.monitor.event.SqlStatementStartEvent;
import org.eclipse.daanse.olap.api.sql.SqlStatementI;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.olap.util.Counters;
import org.eclipse.daanse.olap.util.DelegatingInvocationHandler;

/**
 * SqlStatement contains a SQL statement and associated resources throughout its lifetime.
 *
 * The goal of SqlStatement is to make tracing, error-handling and
 * resource-management easier. None of the methods throws a SQLException; if an error occurs in one of the methods, the
 * method wraps the exception in a {@link RuntimeException} describing the high-level operation, logs that the operation
 * failed, and throws that RuntimeException.
 *
 * If methods succeed, the method generates lifecycle logging such as
 * the elapsed time and number of rows fetched.
 *
 * There are a few obligations on the caller. The caller must:
 * call the {@link #handle(Throwable)} method if one of the contained
 * objects (say the {@link java.sql.ResultSet}) gives an error;
 * call the {@link #close()} method if all operations complete
 * successfully.
 * increment the {@link #rowCount} field each time a row is fetched.
 *
 *
 * The {@link #close()} method is idempotent. You are welcome to call it
 * more than once.
 *
 * SqlStatement is not thread-safe.
 *
 * @author jhyde
 * @since 2.3
 */
public class SqlStatement implements SqlStatementI {
  private static final String TIMING_NAME = "SqlStatement-";

  // used for SQL logging, allows for a SQL Statement UID
  private static final AtomicLong ID_GENERATOR = new AtomicLong();

  private final Context context;
  private Connection jdbcConnection;
  private ResultSet resultSet;
  private final String sql;
  private final List<BestFitColumnType> types;
  private final int maxRows;
  private final int firstRowOrdinal;
  private final ExecutionContext executionContext;
  private final int resultSetType;
  private final int resultSetConcurrency;
  private boolean haveSemaphore;
  public int rowCount;
  private Instant startTime=null;
  private final List<Accessor> accessors = new ArrayList<>();
  private State state = State.FRESH;
  private final long id;
  private final Consumer<GuardedStatement> callback;
  // ownership handshake between this (running/closing) thread and any
  // canceling thread; null until the JDBC statement exists
  private GuardedStatement guard;
  public static final String JAVA_DOUBLE_OVERFLOW = "Big decimal value in ''{0}'' exceeds double size.";

    /**
   * Creates a SqlStatement.
   *
   * @param context              Context
   * @param sql                  SQL
   * @param types                Suggested types of columns, or null; if present, must have one element for each SQL
   *                             column; each not-null entry overrides deduced JDBC type of the column
   * @param maxRows              Maximum rows; less or = 0 means no maximum
   * @param firstRowOrdinal      Ordinal of first row to skip to; less or = 0 do not skip
   * @param executionContext                Execution context of this statement
   * @param resultSetType        Result set type
   * @param resultSetConcurrency Result set concurrency
   * @param callback             Receives the {@link GuardedStatement} once the JDBC statement exists
   */
  public SqlStatement(
    Context context,
    String sql,
    List<BestFitColumnType> types,
    int maxRows,
    int firstRowOrdinal,
    ExecutionContext executionContext,
    int resultSetType,
    int resultSetConcurrency,
    Consumer<GuardedStatement>  callback ) {
    this.callback = callback;
    this.id = ID_GENERATOR.getAndIncrement();
    this.context = context;
    this.sql = sql;
    this.types = types;
    this.maxRows = maxRows;
    this.firstRowOrdinal = firstRowOrdinal;
    this.executionContext = executionContext;
    this.resultSetType = resultSetType;
    this.resultSetConcurrency = resultSetConcurrency;
  }

  /**
   * Executes the current statement, and handles any SQLException.
   */
  @Override
  public void execute() {
    long startTimeNanos;
    assert state == State.FRESH : "cannot re-execute";
    Counters.SQL_STATEMENT_EXECUTE_COUNT.incrementAndGet();
    Counters.SQL_STATEMENT_EXECUTING_IDS.add( id );
    String status = "failed";
    Statement statement = null;
    try {
      // Check execution state
      executionContext.getExecution().checkCancelOrTimeout();

      // Slot first, connection second. The other way round a thread blocked on the
      // semaphore is already holding a physical connection, so queryLimit throttles
      // execution while the connections it was meant to bound pile up regardless.
      //
      // Acquire in slices with a cancel check between them: a canceled query
      // queued behind a saturated query limit has no statement to cancel and
      // nothing else can wake it - it pinned its shepherd pool slot forever.
      while ( !context.getQueryLimitSemaphore().tryAcquire( 1, TimeUnit.SECONDS ) ) {
        executionContext.getExecution().checkCancelOrTimeout();
      }
      haveSemaphore = true;
      this.jdbcConnection = context.getDataSource().getConnection();
      // Trace start of execution.
      if ( RolapUtil.SQL_LOGGER.isDebugEnabled() ) {
        StringBuilder sqllog = new StringBuilder();
        sqllog.append( id )
          .append( ": " )
          .append( "SqlStatement" )
          .append( ": executing sql [" );
        if ( sql.indexOf( '\n' ) >= 0 ) {
          // SQL appears to be formatted as multiple lines. Make it
          // start on its own line.
          sqllog.append( "\n" );
        }
        sqllog.append( sql );
        sqllog.append( ']' );
        RolapUtil.SQL_LOGGER.debug( sqllog.toString() );
      }

      // Execute hook.
      RolapUtil.ExecuteQueryHook hook = RolapUtil.getHook( context );
      if ( hook != null ) {
        hook.onExecuteQuery( sql );
      }

      // Check execution state
      executionContext.getExecution().checkCancelOrTimeout();

      startTimeNanos = System.nanoTime();
      startTime = Instant.now();

      if ( resultSetType < 0 || resultSetConcurrency < 0 ) {
        statement = jdbcConnection.createStatement();
      } else {
        statement = jdbcConnection.createStatement(
          resultSetType,
          resultSetConcurrency );
      }
      if ( maxRows > 0 ) {
        statement.setMaxRows( maxRows );
      }
      guard = new GuardedStatement( statement );

      // First make sure to register with the execution instance.
      if ( getPurpose() != Purpose.CELL_SEGMENT ) {
        executionContext.registerStatement(guard);
      } else {
        if ( callback != null ) {
          callback.accept(guard);
        }
      }

    long mdxStatementId = mdxStatementIdOf(executionContext);
    SqlStatementStartEvent event = new SqlStatementStartEvent(//
        new SqlStatementEventCommon(new EventCommon(startTime), id, mdxStatementId, sql, getPurpose()),
        getCellRequestCount());
    executionContext.getExecution().getDaanseStatement().getDaanseConnection().getContext().getMonitor().accept(event);


      this.resultSet = statement.executeQuery( sql );

      // Compute accessors BEFORE skipping rows: some drivers (sqlite-jdbc 3.36) invalidate
      // ResultSet metadata once next() has returned false, so a skip past the last row would
      // make guessTypes() fail ("SQLite JDBC: inconsistent internal state"). They ensure that
      // we use the most efficient method (e.g. getInt, getDouble, getObject) for the type of
      // the column. Even if you are going to box the result into an object, it is better to
      // use getInt than getObject; the latter might return something daft like a BigDecimal
      // (does, on the Oracle JDBC driver).
      accessors.clear();
      for ( BestFitColumnType type : guessTypes() ) {
        accessors.add( accessor( resultSet, accessors.size(), type ) );
      }

      // skip to first row specified in request
      if ( firstRowOrdinal > 0 ) {
        if ( resultSetType == ResultSet.TYPE_FORWARD_ONLY ) {
          for ( int i = 0; i < firstRowOrdinal; ++i ) {
            if ( !this.resultSet.next() ) {
              break;
            }
          }
        } else {
          // side-effecting positioning; a short result set is fine
          this.resultSet.absolute( firstRowOrdinal );
        }
      }

      Instant timeMillis = Instant.now();
      long timeNanos = System.nanoTime();
      final long executeNanos = timeNanos - startTimeNanos;
      final long executeMillis = executeNanos / 1000000;
      status = new StringBuilder(", exec ").append(executeMillis).append(" ms").toString();


    SqlStatementExecuteEvent execEvent = new SqlStatementExecuteEvent(//
        new SqlStatementEventCommon(new EventCommon(timeMillis), id, mdxStatementId, sql, getPurpose()),
        executeNanos);

    executionContext.getExecution().getDaanseStatement().getDaanseConnection().getContext().getMonitor().accept(execEvent);

    } catch ( Throwable e ) {
      if ( e instanceof InterruptedException ) {
        // keep the interrupt visible to the caller's cleanup instead of
        // silently downgrading it to a SQL error
        Thread.currentThread().interrupt();
      }
      status = new StringBuilder(", failed (").append(e).append(")").toString();

      // This statement was leaked to us. It is our responsibility
      // to dispose of it. The guard is marked closed FIRST: the callback
      // above may already have published it (segment index link), and a
      // cancel arriving after this close must be a no-op - the pooled
      // connection is about to be recycled.
      if ( guard != null ) {
        guard.markClosed();
      }
      Util.close( null, statement, null );

      // Now handle this exception.
      throw handle( e );
    } finally {
      String msg =  new StringBuilder().append(id)
          .append(": ").append(status).toString();
      RolapUtil.SQL_LOGGER.debug( msg );

      if ( RolapUtil.LOGGER.isDebugEnabled() ) {
        RolapUtil.LOGGER.debug(
            new StringBuilder("SqlStatement").append(": executing sql [")
                .append(sql).append("]").append(status).toString() );
      }
    }
  }

  /**
   * Closes the result set and the JDBC connection; the statement is closed
   * implicitly with the connection - only its guard is marked closed here,
   * BEFORE the pooled connection is recycled.
   *
   * If any of them fails, wraps them in a
   * {@link RuntimeException} describing the high-level operation which this statement was performing. No further
   * error-handling is required to produce a descriptive stack trace, unless you want to absorb the error.
   *
   * This method is idempotent.
   */
  @Override
  public void close() {
    if ( state == State.CLOSED ) {
      return;
    }
    state = State.CLOSED;

    if ( haveSemaphore ) {
      haveSemaphore = false;
      context.getQueryLimitSemaphore().release();
    }

    if ( guard != null ) {
      // closed BEFORE the connection returns to the pool: a cancel that
      // arrives later is a guaranteed no-op (never reaches a recycled
      // connection). markClosed releases the guard monitor before the
      // unregister below - no nested locks.
      guard.markClosed();
      executionContext.unregisterStatement( guard );
    }

    // According to the JDBC spec, closing a statement automatically closes
    // its result sets, and closing a connection automatically closes its
    // statements. But let's be conservative and close everything
    // explicitly.
    SQLException ex = Util.close( resultSet, null, jdbcConnection );
    resultSet = null;
    jdbcConnection = null;

    if ( ex != null ) {
      throw Util.newError(
        ex,
          new StringBuilder("executing SQL").append("; sql=[").append(sql).append("]").toString() );
    }

  Instant endTime = Instant.now();
  Duration duration;
  if (startTime == null) {
    // execution didn't start at all
    duration = Duration.ZERO;
  } else {
    duration = Duration.between(startTime, endTime);
  }
    String status = formatTimingStatus( duration, rowCount );

    // Tag the timing marker with the execution's component name (e.g. "SqlTupleReader.readTuples")
    // so it identifies which operation ran the SQL; fall back to "SqlStatement" when absent.
    String component = executionContext.metadata() != null
            && executionContext.metadata().component() != null
        ? executionContext.metadata().component()
        : "SqlStatement";
    executionContext.getExecution().getQueryTiming().markFull(
      TIMING_NAME + component, duration );
    String msg  = new StringBuilder().append(id).append(": ").append(status).toString();
    RolapUtil.SQL_LOGGER.debug( msg );

    Counters.SQL_STATEMENT_CLOSE_COUNT.incrementAndGet();
    boolean remove = Counters.SQL_STATEMENT_EXECUTING_IDS.remove( id );
    status =new StringBuilder(status).append(", ex=").append(Counters.SQL_STATEMENT_EXECUTE_COUNT.get())
      .append(", close=").append(Counters.SQL_STATEMENT_CLOSE_COUNT.get())
      .append(", open=").append(Counters.SQL_STATEMENT_EXECUTING_IDS).toString();

    if ( RolapUtil.LOGGER.isDebugEnabled() ) {
      RolapUtil.LOGGER.debug(
          new StringBuilder("SqlStatement").append(": done executing sql [").append(sql).append("]")
          .append(status).toString() );
    }

    if ( !remove ) {
      throw new AssertionError(
        "SqlStatement closed that was never executed: " + id );
    }

  long mdxStatementId = mdxStatementIdOf(executionContext);
  SqlStatementEndEvent endEvent = new SqlStatementEndEvent(//
      new SqlStatementEventCommon(new EventCommon(endTime), id, mdxStatementId, sql, getPurpose()), rowCount,
      false, null);

  executionContext.getExecution().getDaanseStatement().getDaanseConnection().getContext().getMonitor().accept(endEvent);
  }

  public String formatTimingStatus( Duration duration, int rowCount ) {
    return new StringBuilder(", exec+fetch ").append(duration.toMillis()).append(" ms, ")
        .append(rowCount).append(" rows").toString();
  }

  @Override
  public ResultSet getResultSet() {
    return resultSet;
  }

  /**
   * Handles an exception thrown from the ResultSet, implicitly calls {@link #close}, and returns an exception which
   * includes the full stack, including a description of the high-level operation.
   *
   * @param e Exception
   * @return Runtime exception
   */
  public RuntimeException handle( Throwable e ) {
    // Use the high-level operation description from the execution metadata
    // when available (e.g. "while building member cache"), falling back to a
    // generic "executing SQL" otherwise. Tests like
    // SchemaTest#testHierarchyTableNotFound match against the metadata phrase.
    String description = (executionContext != null
            && executionContext.metadata() != null
            && executionContext.metadata().message() != null)
        ? executionContext.metadata().message()
        : "executing SQL";
    RuntimeException runtimeException =
      Util.newError( e, new StringBuilder(description).append("; sql=[").append(sql).append("]").toString() );
    try {
      close();
    } catch (RuntimeException ignored) {
      // Ignoring cleanup exception during error handling
    }
    return runtimeException;
  }

  // warning suppressed because breaking this method up would reduce readability
  @SuppressWarnings( "squid:S3776" )
  static Accessor accessor( ResultSet resultSet, int column, BestFitColumnType type ) {
    final int columnPlusOne = column + 1;
    return switch (type) {
    case DATE, TIMESTAMP -> new Accessor() {
              @Override
        public Object get() throws SQLException {
                return temporal( resultSet, columnPlusOne, type );
              }
            };
    case OBJECT -> new Accessor() {
              @Override
        public Object get() throws SQLException {
                return resultSet.getObject( columnPlusOne );
              }
            };
    case STRING -> new Accessor() {
              @Override
        public Object get() throws SQLException {
                return resultSet.getString( columnPlusOne );
              }
            };
    case INT -> new Accessor() {
              @Override
        public Object get() throws SQLException {
                final int val = resultSet.getInt( columnPlusOne );
                if ( val == 0 && resultSet.wasNull() ) {
                  return null;
                }
                return val;
              }
            };
    case LONG -> new Accessor() {
              @Override
        public Object get() throws SQLException {
                final long val = resultSet.getLong( columnPlusOne );
                if ( val == 0 && resultSet.wasNull() ) {
                  return null;
                }
                return val;
              }
            };
    case DOUBLE -> new Accessor() {
              @Override
        public Object get() throws SQLException {
                final double val = resultSet.getDouble( columnPlusOne );
                if ( val == 0 && resultSet.wasNull() ) {
                  return null;
                }
                return val;
              }
            };
    case DECIMAL -> /* this type is only present to work around a defect in the Snowflake jdbc driver. */ /* there is currently no plan to support the DECIMAL/BigDecimal type internally */ new Accessor() {
              @Override
        public Object get() throws SQLException {
                final BigDecimal decimal = resultSet.getBigDecimal( columnPlusOne );
                if ( decimal == null ) {
                  // null-check the value itself: coupling it to wasNull()
                  // left an NPE path, and the second getBigDecimal doubled
                  // the JDBC access per cell
                  return null;
                }
                final double val = decimal.doubleValue();
                if ( val == Double.NEGATIVE_INFINITY || val == Double.POSITIVE_INFINITY ) {
                  throw new SQLDataException(
                      MessageFormat.format(JAVA_DOUBLE_OVERFLOW, resultSet.getMetaData().getColumnName( columnPlusOne ) ));
                }
                return val;
              }
            };
    default -> throw Util.unexpected( type );
    };
  }

  /**
   * The value of a date or timestamp column, read with its own getter. {@code getObject} hands over the class the
   * driver picks: Oracle gives its TIMESTAMP as {@code oracle.sql.TIMESTAMP}, which neither a format nor a comparison of
   * the engine knows. A member key and the segment axis of the same column read it here alike, so the key finds its
   * segment.
   */
  public static Object temporal( ResultSet resultSet, int oneBasedColumn, BestFitColumnType type ) throws SQLException {
    return type == BestFitColumnType.DATE ? resultSet.getDate( oneBasedColumn )
        : resultSet.getTimestamp( oneBasedColumn );
  }

  public List<BestFitColumnType> guessTypes() throws SQLException {
    final ResultSetMetaData metaData = resultSet.getMetaData();
    final int columnCount = metaData.getColumnCount();
    assert this.types == null || this.types.size() == columnCount;
    List<BestFitColumnType> typeList = new ArrayList<>();

    for ( int i = 0; i < columnCount; i++ ) {
      final BestFitColumnType suggestedType =
        this.types == null ? null : this.types.get( i );
      // There might not be a schema constructed yet,
      // so watch out here for NPEs.

      Dialect dialect = context.getDialect();

      if ( suggestedType != null ) {
        typeList.add( suggestedType );
      } else if ( dialect != null ) {
        typeList.add( dialect.getType( metaData, i ) );
      } else {
        typeList.add( BestFitColumnType.OBJECT );
      }
    }
    return typeList;
  }

  public List<Accessor> getAccessors() {
    return accessors;
  }

  /**
   * Returns the result set in a proxy which automatically closes this SqlStatement (and hence also the statement and
   * result set) when the result set is closed.
   *
   * This helps to prevent connection leaks. The caller still has to
   * remember to call ResultSet.close(), of course.
   *
   * @return Wrapped result set
   */
  @Override
  public ResultSet getWrappedResultSet() {
    return (ResultSet) Proxy.newProxyInstance(
      this.getClass().getClassLoader(),
      new Class<?>[] { ResultSet.class },
      new MyDelegatingInvocationHandler( this ) );
  }

  private Purpose getPurpose() {
      Purpose purpose = executionContext.metadata().purpose();
      return purpose != null ? purpose : Purpose.OTHER;
  }

  private int getCellRequestCount() {
    return executionContext.metadata().cellRequestCount();
  }

  public interface Accessor {
    Object get() throws SQLException;
  }

  /**
   * Reflectively implements the {@link ResultSet} interface by routing method calls to the result set inside a {@link
   * org.eclipse.daanse.rolap.common.SqlStatement}. When the result set is closed, so is the SqlStatement, and hence the JDBC connection
   * and statement also.
   */
  // must be public for reflection to work
  public static class MyDelegatingInvocationHandler
    extends DelegatingInvocationHandler {
    private final SqlStatement sqlStatement;

    /**
     * Creates a MyDelegatingInvocationHandler.
     *
     * @param sqlStatement SQL statement
     */
    MyDelegatingInvocationHandler( SqlStatement sqlStatement ) {
      this.sqlStatement = sqlStatement;
    }

    @Override
    protected Object getTarget() throws InvocationTargetException {
      final ResultSet resultSet = sqlStatement.getResultSet();
      if ( resultSet == null ) {
        throw new InvocationTargetException(
          new SQLException(
            "Invalid operation. Statement is closed." ) );
      }
      return resultSet;
    }

    /**
     * Helper method to implement {@link java.sql.ResultSet#close()}.
     *
     * @throws SQLException on error
     */
    public void close() throws SQLException {
      sqlStatement.close();
    }
  }

  private enum State {
    FRESH,
    CLOSED
  }

  public Context getContext() {
        return context;
  }

  public static long mdxStatementIdOf(ExecutionContext executionContext) {
    if (executionContext.getExecution() != null) {
      final org.eclipse.daanse.olap.api.execution.Statement statement = executionContext.getExecution().getDaanseStatement();
      if (statement != null) {
        return statement.getId();
      }
    }
    return -1;
  }
}
