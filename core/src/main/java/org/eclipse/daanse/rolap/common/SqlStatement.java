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
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import org.eclipse.daanse.jdbc.db.dialect.api.BestFitColumnType;
import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.Execution;
import org.eclipse.daanse.olap.api.ISqlStatement;
import org.eclipse.daanse.olap.api.monitor.event.EventCommon;
import org.eclipse.daanse.olap.api.monitor.event.SqlStatementEndEvent;
import org.eclipse.daanse.olap.api.monitor.event.SqlStatementEvent;
import org.eclipse.daanse.olap.api.monitor.event.SqlStatementEvent.Purpose;
import org.eclipse.daanse.olap.api.monitor.event.SqlStatementEventCommon;
import org.eclipse.daanse.olap.api.monitor.event.SqlStatementExecuteEvent;
import org.eclipse.daanse.olap.api.monitor.event.SqlStatementStartEvent;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.olap.server.LocusImpl;
import org.eclipse.daanse.rolap.util.Counters;
import org.eclipse.daanse.rolap.util.DelegatingInvocationHandler;

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
public class SqlStatement implements ISqlStatement {
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
  private final LocusImpl locus;
  private final int resultSetType;
  private final int resultSetConcurrency;
  private boolean haveSemaphore;
  public int rowCount;
  private Instant startTime=null;
  private final List<Accessor> accessors = new ArrayList<>();
  private State state = State.FRESH;
  private final long id;
  private Consumer<Statement> callback;
  public final static String javaDoubleOverflow = "Big decimal value in ''{0}'' exceeds double size.";

    /**
   * Creates a SqlStatement.
   *
   * @param context              Context
   * @param sql                  SQL
   * @param types                Suggested types of columns, or null; if present, must have one element for each SQL
   *                             column; each not-null entry overrides deduced JDBC type of the column
   * @param maxRows              Maximum rows; less or = 0 means no maximum
   * @param firstRowOrdinal      Ordinal of first row to skip to; less or = 0 do not skip
   * @param locus                Execution context of this statement
   * @param resultSetType        Result set type
   * @param resultSetConcurrency Result set concurrency
   */
  public SqlStatement(
    Context context,
    String sql,
    List<BestFitColumnType> types,
    int maxRows,
    int firstRowOrdinal,
    LocusImpl locus,
    int resultSetType,
    int resultSetConcurrency,
    Consumer<Statement>  callback ) {
    this.callback = callback;
    this.id = ID_GENERATOR.getAndIncrement();
    this.context = context;
    this.sql = sql;
    this.types = types;
    this.maxRows = maxRows;
    this.firstRowOrdinal = firstRowOrdinal;
    this.locus = locus;
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
    state = State.ACTIVE;
    Counters.SQL_STATEMENT_EXECUTE_COUNT.incrementAndGet();
    Counters.SQL_STATEMENT_EXECUTING_IDS.add( id );
    String status = "failed";
    Statement statement = null;
    try {
      // Check execution state
      locus.getExecution().checkCancelOrTimeout();

      this.jdbcConnection = context.getDataSource().getConnection();
      context.getQueryLimitSemaphore().acquire();
      haveSemaphore = true;
      // Trace start of execution.
      if ( RolapUtil.SQL_LOGGER.isDebugEnabled() ) {
        StringBuilder sqllog = new StringBuilder();
        sqllog.append( id )
          .append( ": " )
          .append( locus.component )
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
      RolapUtil.ExecuteQueryHook hook = RolapUtil.getHook();
      if ( hook != null ) {
        hook.onExecuteQuery( sql );
      }

      // Check execution state
      locus.getExecution().checkCancelOrTimeout();

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

      // First make sure to register with the execution instance.
      if ( getPurpose() != Purpose.CELL_SEGMENT ) {
        locus.getExecution().registerStatement( locus, statement );
      } else {
        if ( callback != null ) {
          callback.accept(statement);
        }
      }

    long mdxStatementId = mdxStatementIdOf(locus);
    SqlStatementStartEvent event = new SqlStatementStartEvent(//
        new SqlStatementEventCommon(new EventCommon(startTime), id, mdxStatementId, sql, getPurpose()),
        getCellRequestCount());
    locus.getContext().getMonitor().accept(event);

//        new SqlStatementStartEvent(
//          startTimeMillis,
//          id,
//          locus,
//          sql,
//          getPurpose(),
//          getCellRequestCount() )


      this.resultSet = statement.executeQuery( sql );

      // skip to first row specified in request
      this.state = State.ACTIVE;
      if ( firstRowOrdinal > 0 ) {
        if ( resultSetType == ResultSet.TYPE_FORWARD_ONLY ) {
          for ( int i = 0; i < firstRowOrdinal; ++i ) {
            if ( !this.resultSet.next() ) {
              this.state = State.DONE;
              break;
            }
          }
        } else {
          if ( !this.resultSet.absolute( firstRowOrdinal ) ) {
            this.state = State.DONE;
          }
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

    locus.getContext().getMonitor().accept(execEvent);

//      new SqlStatementExecuteEvent(
//          timeMillis,
//          id,
//          locus,
//          sql,
//          getPurpose(),
//          executeNanos )

      // Compute accessors. They ensure that we use the most efficient
      // method (e.g. getInt, getDouble, getObject) for the type of the
      // column. Even if you are going to box the result into an object,
      // it is better to use getInt than getObject; the latter might
      // return something daft like a BigDecimal (does, on the Oracle JDBC
      // driver).
      accessors.clear();
      for ( BestFitColumnType type : guessTypes() ) {
        accessors.add( createAccessor( accessors.size(), type ) );
      }
    } catch ( Throwable e ) {
      status = new StringBuilder(", failed (").append(e).append(")").toString();

      // This statement was leaked to us. It is our responsibility
      // to dispose of it.
      Util.close( null, statement, null );

      // Now handle this exception.
      throw handle( e );
    } finally {
      String msg =  new StringBuilder().append(id)
          .append(": ").append(status).toString();
      RolapUtil.SQL_LOGGER.debug( msg );

      if ( RolapUtil.LOGGER.isDebugEnabled() ) {
        RolapUtil.LOGGER.debug(
            new StringBuilder(locus.component).append(": executing sql [")
                .append(sql).append("]").append(status).toString() );
      }
    }
  }

  /**
   * Closes all resources (statement, result set) held by this SqlStatement.
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
          new StringBuilder(locus.message).append("; sql=[").append(sql).append("]").toString() );
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

    locus.getExecution().getQueryTiming().markFull(
      TIMING_NAME + locus.component, duration );
    String msg  = new StringBuilder().append(id).append(": ").append(status).toString();
    RolapUtil.SQL_LOGGER.debug( msg );

    Counters.SQL_STATEMENT_CLOSE_COUNT.incrementAndGet();
    boolean remove = Counters.SQL_STATEMENT_EXECUTING_IDS.remove( id );
    status =new StringBuilder(status).append(", ex=").append(Counters.SQL_STATEMENT_EXECUTE_COUNT.get())
      .append(", close=").append(Counters.SQL_STATEMENT_CLOSE_COUNT.get())
      .append(", open=").append(Counters.SQL_STATEMENT_EXECUTING_IDS).toString();

    if ( RolapUtil.LOGGER.isDebugEnabled() ) {
      RolapUtil.LOGGER.debug(
          new StringBuilder(locus.component).append(": done executing sql [").append(sql).append("]")
          .append(status).toString() );
    }

    if ( !remove ) {
      throw new AssertionError(
        "SqlStatement closed that was never executed: " + id );
    }

  long mdxStatementId = mdxStatementIdOf(locus);
  SqlStatementEndEvent endEvent = new SqlStatementEndEvent(//
      new SqlStatementEventCommon(new EventCommon(endTime), id, mdxStatementId, sql, getPurpose()), rowCount,
      false, null);

  locus.getContext().getMonitor().accept(endEvent);

//      new SqlStatementEndEvent(
//        endTime,
//        id,
//        locus,
//        sql,
//        getPurpose(),
//        rowCount,
//        false,
//        null )
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
    RuntimeException runtimeException =
      Util.newError( e, new StringBuilder(locus.message).append("; sql=[").append(sql).append("]").toString() );
    try {
      close();
    } catch ( Exception t ) {
      // ignore
    }
    return runtimeException;
  }

  // warning suppressed because breaking this method up would reduce readability
  @SuppressWarnings( "squid:S3776" )
  private Accessor createAccessor( int column, BestFitColumnType type ) {
    final int columnPlusOne = column + 1;
    switch ( type ) {
      case OBJECT:
        return new Accessor() {
          @Override
    public Object get() throws SQLException {
            return resultSet.getObject( columnPlusOne );
          }
        };
      case STRING:
        return new Accessor() {
          @Override
    public Object get() throws SQLException {
            return resultSet.getString( columnPlusOne );
          }
        };
      case INT:
        return new Accessor() {
          @Override
    public Object get() throws SQLException {
            final int val = resultSet.getInt( columnPlusOne );
            if ( val == 0 && resultSet.wasNull() ) {
              return null;
            }
            return val;
          }
        };
      case LONG:
        return new Accessor() {
          @Override
    public Object get() throws SQLException {
            final long val = resultSet.getLong( columnPlusOne );
            if ( val == 0 && resultSet.wasNull() ) {
              return null;
            }
            return val;
          }
        };
      case DOUBLE:
        return new Accessor() {
          @Override
    public Object get() throws SQLException {
            final double val = resultSet.getDouble( columnPlusOne );
            if ( val == 0 && resultSet.wasNull() ) {
              return null;
            }
            return val;
          }
        };
      case DECIMAL:
        // this type is only present to work around a defect in the Snowflake jdbc driver.
        // there is currently no plan to support the DECIMAL/BigDecimal type internally
        return new Accessor() {
          @Override
    public Object get() throws SQLException {
            final BigDecimal decimal = resultSet.getBigDecimal( columnPlusOne );
            if ( decimal == null && resultSet.wasNull() ) {
              return null;
            }
            final double val = resultSet.getBigDecimal( columnPlusOne ).doubleValue();
            if ( val == Double.NEGATIVE_INFINITY || val == Double.POSITIVE_INFINITY ) {
              throw new SQLDataException(
                  MessageFormat.format(javaDoubleOverflow, resultSet.getMetaData().getColumnName( columnPlusOne ) ));
            }
            return val;
          }
        };
      default:
        throw Util.unexpected( type );
    }
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

  private SqlStatementEvent.Purpose getPurpose() {
    if ( locus instanceof StatementLocus statementLocus) {
      return statementLocus.purpose;
    } else {
      return SqlStatementEvent.Purpose.OTHER;
    }
  }

  private int getCellRequestCount() {
    if ( locus instanceof StatementLocus statementLocus) {
      return statementLocus.cellRequestCount;
    } else {
      return 0;
    }
  }

  /**
   * The approximate JDBC type of a column.
   *
   * This type affects which {@link ResultSet} method we use to get values
   * of this column: the default is {@link java.sql.ResultSet#getObject(int)}, but we'd prefer to use native values
   * {@code getInt} and {@code getDouble} if possible.
   * Note that the DECIMAL type was added to provide a workaround for a bug
   * in the Snowflake JDBC driver.  There is no plan to support it further than that.
   */
  public enum Type {
    OBJECT,
    DOUBLE,
    INT,
    LONG,
    STRING,
    DECIMAL;

    public Object get( ResultSet resultSet, int column ) throws SQLException {
      switch ( this ) {
        case OBJECT:
          return resultSet.getObject( column + 1 );
        case STRING:
          return resultSet.getString( column + 1 );
        case INT:
          return resultSet.getInt( column + 1 );
        case LONG:
          return resultSet.getLong( column + 1 );
        case DOUBLE:
          return resultSet.getDouble( column + 1 );
        case DECIMAL:
          // this lacks the range checking done in the createAccessor method above, but nothing seems
          // to call this method anyway.
          BigDecimal decimal = resultSet.getBigDecimal( column + 1 );
          return decimal == null ? null : decimal.doubleValue();
        default:
          throw Util.unexpected( this );
      }
    }
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
    ACTIVE,
    DONE,
    CLOSED
  }

  public static class StatementLocus extends LocusImpl {
    private final SqlStatementEvent.Purpose purpose;
    private final int cellRequestCount;

    public StatementLocus(
      Execution execution,
      String component,
      String message,
      SqlStatementEvent.Purpose purpose,
      int cellRequestCount ) {
      super(
        execution,
        component,
        message );
      this.purpose = purpose;
      this.cellRequestCount = cellRequestCount;
    }
  }

  public Context getContext() {
        return context;
  }

  public static long mdxStatementIdOf(LocusImpl locus) {
    if (locus.getExecution() != null) {
      final org.eclipse.daanse.olap.api.Statement statement = locus.getExecution().getMondrianStatement();
      if (statement != null) {
        return statement.getId();
      }
    }
    return -1;
  }
}
