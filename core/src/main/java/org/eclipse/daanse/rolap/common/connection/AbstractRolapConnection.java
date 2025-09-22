/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2001-2005 Julian Hyde
 * Copyright (C) 2005-2020 Hitachi Vantara and others
 * All Rights Reserved.
 *
 * For more information please visit the Project: Hitachi Vantara - Mondrian
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
 *   Stefan Bischof (bipolis.org) - initial
 */

package org.eclipse.daanse.rolap.common.connection;

import java.io.PrintWriter;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;

import javax.sql.DataSource;

import org.eclipse.daanse.mdx.model.api.expression.MdxExpression;
import org.eclipse.daanse.mdx.parser.api.MdxParser;
import org.eclipse.daanse.olap.api.CacheControl;
import org.eclipse.daanse.olap.api.CatalogReader;
import org.eclipse.daanse.olap.api.Command;
import org.eclipse.daanse.olap.api.ConfigConstants;
import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.Execution;
import org.eclipse.daanse.olap.api.Locus;
import org.eclipse.daanse.olap.api.Statement;
import org.eclipse.daanse.olap.api.access.Role;
import org.eclipse.daanse.olap.api.calc.todo.TupleCursor;
import org.eclipse.daanse.olap.api.calc.todo.TupleList;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.api.connection.ConnectionProps;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.api.query.component.Expression;
import org.eclipse.daanse.olap.api.query.component.Query;
import org.eclipse.daanse.olap.api.query.component.QueryAxis;
import org.eclipse.daanse.olap.api.query.component.QueryComponent;
import org.eclipse.daanse.olap.api.result.Cell;
import org.eclipse.daanse.olap.api.result.Position;
import org.eclipse.daanse.olap.api.result.Result;
import org.eclipse.daanse.olap.api.result.Scenario;
import org.eclipse.daanse.olap.calc.base.type.tuplebase.TupleCollections;
import org.eclipse.daanse.olap.common.ExecuteDurationUtil;
import org.eclipse.daanse.olap.common.QueryCanceledException;
import org.eclipse.daanse.olap.common.QueryTimeoutException;
import org.eclipse.daanse.olap.common.ResourceLimitExceededException;
import org.eclipse.daanse.olap.common.ResultBase;
import org.eclipse.daanse.olap.common.ResultLimitExceededException;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.olap.connection.ConnectionBase;
import org.eclipse.daanse.olap.core.AbstractBasicContext;
import org.eclipse.daanse.olap.exceptions.FailedToParseQueryException;
import org.eclipse.daanse.olap.query.component.QueryImpl;
import org.eclipse.daanse.olap.query.component.TransactionCommandImpl;
import  org.eclipse.daanse.olap.server.ExecutionImpl;
import  org.eclipse.daanse.olap.server.LocusImpl;
import org.eclipse.daanse.rolap.api.RolapContext;
import org.eclipse.daanse.rolap.common.RolapAxis;
import org.eclipse.daanse.rolap.common.RolapCatalogCache;
import org.eclipse.daanse.rolap.common.RolapCatalogReader;
import org.eclipse.daanse.rolap.common.RolapCell;
import org.eclipse.daanse.rolap.common.RolapResult;
import org.eclipse.daanse.rolap.common.RolapUtil;
import org.eclipse.daanse.rolap.common.ScenarioImpl;
import org.eclipse.daanse.rolap.common.statement.InternalStatement;
import org.eclipse.daanse.rolap.common.statement.ReentrantInternalStatement;
import org.eclipse.daanse.rolap.element.RolapCatalog;
import org.eclipse.daanse.rolap.element.RolapCube;
import org.eclipse.daanse.rolap.mapping.api.model.CatalogMapping;
import org.eclipse.daanse.rolap.util.FauxMemoryMonitor;
import org.eclipse.daanse.rolap.util.MemoryMonitor;
import org.eclipse.daanse.rolap.util.NotificationMemoryMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractRolapConnection extends ConnectionBase {
  private static final Logger LOGGER =
    LoggerFactory.getLogger( AbstractRolapConnection.class );
  private static final AtomicLong ID_GENERATOR = new AtomicLong();


  protected final ConnectionProps connectionProps;

  private RolapContext context = null;
  protected final RolapCatalog catalog;
  private CatalogReader schemaReader;
  private Role role;
  private Locale locale = Locale.getDefault(); //TODO need take locale from LcidService
  private Scenario scenario;
  private boolean closed = false;
  private final long id;
  private final Statement internalStatement;


  /**
   * Creates a RolapConnection.
   *
   * Only RolapCatalogCache calls this with
   * schema != null (to create a schema's internal connection).
   * Other uses retrieve a schema from the cache based upon
   * the Catalog property.
   *
   * @param context      context
   *
   * @param connectionProps  ConnectionProps
   *
   */
    protected AbstractRolapConnection(RolapContext context, RolapCatalog catalog, ConnectionProps connectionProps) {
        this.context = context;
        this.id = ID_GENERATOR.getAndIncrement();

        assert connectionProps != null;

        this.connectionProps = connectionProps;

        // Register this connection before we register its internal statement.
        context.addConnection( this );
        if ( catalog == null ) {
        Statement bootstrapStatement = createInternalStatement( false, this);
        final Locus locus =
          new LocusImpl(
            new ExecutionImpl( bootstrapStatement, ExecuteDurationUtil.executeDurationValue(context) ),
            null,
            "Initializing connection" );
        LocusImpl.push( locus );
        try {
            // TODO: switch from schemareader to catalogreader;
            CatalogMapping catalogMapping = context.getCatalogMapping();
            catalog = ((RolapCatalogCache) context.getCatalogCache()).getOrCreateCatalog(catalogMapping, connectionProps);

        } finally {
          LocusImpl.pop( locus );
          bootstrapStatement.close();
        }
        internalStatement =
          catalog.getInternalConnection().getInternalStatement();
        } else {
            this.internalStatement = createInternalStatement( true,this);
        }
        this.catalog = catalog;
        // Set the locale.
        this.locale = connectionProps.locale();
    }
 
   /**
   * Returns the identifier of this connection. Unique within the lifetime of
   * this JVM.
   *
   * @return Identifier of this connection
   */
	@Override
  public long getId() {
    return id;
  }

  @Override
protected Logger getLogger() {
    return LOGGER;
  }


  @Override
public void close() {
    if ( !closed ) {
      closed = true;
      context.removeConnection( this );
    }
    if ( internalStatement != null ) {
      internalStatement.close();
    }
  }

  @Override
public RolapCatalog getCatalog() {
    return catalog;
  }

@Override
public Locale getLocale() {
    return locale;
  }

  public void setLocale( Locale locale ) {
    if ( locale == null ) {
      throw new IllegalArgumentException( "locale must not be null" );
    }
    this.locale = locale;
  }

  @Override
public CatalogReader getCatalogReader() {
    return schemaReader;
  }

  @Override
public CacheControl getCacheControl( PrintWriter pw ) {
	  AbstractBasicContext abc = (AbstractBasicContext) context;
    return abc.getAggregationManager().getCacheControl( this, pw );
  }

  /**
   * Executes a Query.
   *
   * @param query Query parse tree
   * @throws ResourceLimitExceededException if some resource limit specified
   *                                        in the property file was exceeded
   * @throws QueryCanceledException         if query was canceled during execution
   * @throws QueryTimeoutException          if query exceeded timeout specified in
   *                                        the property file
   * @deprecated Use {@link #execute(mondrian.server.ExecutionImpl)}; this method
   * will be removed in mondrian-4.0
   */
  @Deprecated(since = "this method will be removed in mondrian-4.0")
@Override
public Result execute( Query query ) {
    final Statement statement = query.getStatement();
    ExecutionImpl execution =
      new ExecutionImpl( statement, Optional.of(Duration.ofMillis(statement.getQueryTimeoutMillis())) );
    return execute( execution );
  }

  /**
   * Executes a statement.
   *
   * @param execution Execution context (includes statement, query)
   * @throws ResourceLimitExceededException if some resource limit specified
   *                                        in the property file was exceeded
   * @throws QueryCanceledException         if query was canceled during execution
   * @throws QueryTimeoutException          if query exceeded timeout specified in
   *                                        the property file
   */
  @Override
  public Result execute( final Execution execution ) {
    return
      context.getResultShepherd()
        .shepherdExecution(
          execution,
          new Callable<Result>() {
            @Override
			public Result call() throws Exception {
              return executeInternal( execution );
            }
          } );
  }

  private Result executeInternal( final Execution execution ) {
    final Statement statement = execution.getMondrianStatement();
    // Cleanup any previous executions still running
    synchronized ( statement ) {
      final Execution previousExecution =
        statement.getCurrentExecution();
      if ( previousExecution != null ) {
        statement.end( previousExecution );
      }
    }
    final Query query = statement.getQuery();
    final MemoryMonitor.Listener listener = new MemoryMonitor.Listener() {
      @Override
	public void memoryUsageNotification( long used, long max ) {
        execution.setOutOfMemory(
          new StringBuilder("OutOfMemory used=")
              .append(used)
              .append(", max=")
              .append(max)
              .append(" for query: ")
              .append(query.toString())
              .toString()
            // connection string can contain user name and password
            //+ " for connection: "
            //+ getConnectString()
        );
      }
    };

	MemoryMonitor mm = context.getConfigValue(ConfigConstants.MEMORY_MONITOR, ConfigConstants.MEMORY_MONITOR_DEFAULT_VALUE, Boolean.class) ? new NotificationMemoryMonitor() : new FauxMemoryMonitor();

    final long currId = execution.getId();
    try {
      mm.addListener( listener, context.getConfigValue(ConfigConstants.MEMORY_MONITOR_THRESHOLD, ConfigConstants.MEMORY_MONITOR_THRESHOLD_DEFAULT_VALUE, Integer.class) );
      // Check to see if we must punt
      execution.checkCancelOrTimeout();

      if ( LOGGER.isDebugEnabled() ) {
        LOGGER.debug( Util.unparse( query ) );
      }

      if ( RolapUtil.MDX_LOGGER.isDebugEnabled() ) {
        RolapUtil.MDX_LOGGER.debug( new StringBuilder().append(currId)
            .append(": ").append(Util.unparse( query )).toString() );
      }

      final Locus locus = new LocusImpl( execution, null, "Loading cells" );
      LocusImpl.push( locus );
      Result result;
      try {
        statement.start( execution );
        ( (RolapCube) query.getCube() ).clearCachedAggregations( true );
        RolapResult  rolapResult = new RolapResult( execution, true );
        result = rolapResult;
        int i = 0;
        for ( QueryAxis axis : query.getAxes() ) {
          if ( axis.isNonEmpty() ) {
              result = new NonEmptyResult( result, execution, i );
          }
          ++i;
        }
      } finally {
        LocusImpl.pop( locus );
        ( (RolapCube) query.getCube() ).clearCachedAggregations( true );
      }
      statement.end( execution );
      return result;
    } catch ( ResultLimitExceededException e ) {
      // query has been punted
      throw e;
    } catch ( Exception e ) {
      try {
        if ( !execution.isCancelOrTimeout() ) {
          statement.end( execution );
        }
      } catch ( Exception e1 ) {
        // We can safely ignore that cleanup exception.
        // If an error is encountered here, it means that
        // one was already encountered at statement.start()
        // above and the exception we will throw after the
        // cleanup is the same as the original one.
      }
      String queryString;
      try {
        queryString = Util.unparse( query );
      } catch ( Exception e1 ) {
        queryString = "?";
      }
      throw Util.newError(
        e,
        new StringBuilder("Error while executing query [").append(queryString).append("]").toString() );
    } finally {
      mm.removeListener( listener );
      if ( RolapUtil.MDX_LOGGER.isDebugEnabled() ) {
        final Duration elapsed = execution.getElapsedMillis();
        RolapUtil.MDX_LOGGER.debug(
          new StringBuilder().append(currId).append(": exec: ").append(elapsed.toMillis()).append(" ms").toString() );
      }
    }
  }

  @Override
public void setRole( Role role ) {
    assert role != null;

    this.role = role;
    this.schemaReader = new RolapCatalogReader( context,role, catalog );
  }

  @Override
public Role getRole() {
    Util.assertPostcondition( role != null, "role != null" );

    return role;
  }

  public void setScenario( Scenario scenario ) {
    this.scenario = scenario;
  }

  @Override
  public Scenario getScenario() {
    return scenario;
  }


  @Override
public QueryComponent parseStatement(String query ) {
    Statement statement = createInternalStatement( false ,this);
    final Locus locus =
      new LocusImpl(
        new ExecutionImpl( statement, ExecuteDurationUtil.executeDurationValue(statement.getConnection().getContext()) ),
        "Parse/validate MDX statement",
        null );
    LocusImpl.push( locus );
    try {
      QueryComponent queryPart;
        //TODO migrate to parser
        if ("BEGIN TRANSACTION".equalsIgnoreCase(query)) {
            queryPart = new TransactionCommandImpl(Command.BEGIN);
        } else if ("COMMIT TRANSACTION".equalsIgnoreCase(query)) {
            queryPart = new TransactionCommandImpl(Command.COMMIT);
        } else if ("ROLLBACK TRANSACTION".equalsIgnoreCase(query)) {
            queryPart = new TransactionCommandImpl(Command.ROLLBACK);
        } else{
            queryPart =
                parseStatement(statement, query, context.getFunctionService(), false);
        }
      if ( queryPart instanceof QueryImpl q) {
          q.setOwnStatement( true );
        statement = null;
      }
      return queryPart;
    } finally {
      LocusImpl.pop( locus );
      if ( statement != null ) {
        statement.close();
      }
    }
  }

  @Override
public Expression parseExpression( String expr ) {
    if ( getLogger().isDebugEnabled() ) {
        String msg  = Util.NL + expr;
        getLogger().debug(msg);
    }
    try {
      MdxParser mdxParser = context.getMdxParserProvider().newParser(expr, context.getFunctionService().getPropertyWords());
      MdxExpression expression = mdxParser.parseExpression();
      return getExpressionProvider().createExpression(expression);
    } catch ( Throwable exception ) {
      throw new FailedToParseQueryException( expr, exception );
    }
  }

  @Override
public Statement getInternalStatement() {
    if ( internalStatement == null ) {
      return catalog.getInternalConnection().getInternalStatement();
    } else {
      return internalStatement;
    }
  }

  protected Statement createInternalStatement( boolean reentrant, Connection connection ) {
    final Statement statement =
      reentrant
        ? new ReentrantInternalStatement(connection)
        : new InternalStatement(connection);
    context.addStatement( statement );
    return statement;
  }


  @Deprecated() //finf a better way for agg manager.
  @Override
public DataSource getDataSource() {
      return getContext().getDataSource();
    }
  @Override
public Context<?> getContext() {
      return context;
    }

  /**
   * Helper method to allow olap4j wrappers to implement
   * org.olap4j.OlapConnection#createScenario().
   *
   * @return new Scenario
   */
  public Scenario createScenario() {
    final ScenarioImpl scenarioInner = new ScenarioImpl();
    scenarioInner.register( catalog );
    return scenarioInner;
  }

  /**
   * A NonEmptyResult filters a result by removing empty rows
   * on a particular axis.
   */
  public static class NonEmptyResult extends ResultBase {

    public final Result underlying;
    private final int axis;
    private final Map<Integer, Integer> map;
    /**
     * workspace. Synchronized access only.
     */
    private final int[] pos;

    /**
     * Creates a NonEmptyResult.
     *
     * @param result    Result set
     * @param execution Execution context
     * @param axis      Which axis to make non-empty
     */
    NonEmptyResult( Result result, Execution execution, int axis ) {
      super( execution, result.getAxes().clone() );

      this.underlying = result;
      this.axis = axis;
      this.map = new HashMap<>();
      int axisCount = underlying.getAxes().length;
      this.pos = new int[ axisCount ];
      this.slicerAxis = underlying.getSlicerAxis();
      TupleList tupleList =
        ( (RolapAxis) underlying.getAxes()[ axis ] ).getTupleList();

      final TupleList filteredTupleList;


		filteredTupleList = TupleCollections.createList(tupleList.getArity());
		int i = -1;
		TupleCursor tupleCursor = tupleList.tupleCursor();
		while (tupleCursor.forward()) {
			++i;
			if (!isEmpty(i, axis)) {
				map.put(filteredTupleList.size(), i);
				filteredTupleList.addCurrent(tupleCursor);
			}
		}

      this.axes[ axis ] = new RolapAxis( filteredTupleList );
    }

    @Override
	protected Logger getLogger() {
      return LOGGER;
    }

    /**
     * Returns true if all cells at a given offset on a given axis are
     * empty. For example, in a 2x2x2 dataset, isEmpty(1,0)
     * returns true if cells {(1,0,0), (1,0,1), (1,1,0),
     * (1,1,1)} are all empty. As you can see, we hold the 0th
     * coordinate fixed at 1, and vary all other coordinates over all
     * possible values.
     */
    private boolean isEmpty( int offset, int fixedAxis ) {
      int axisCount = getAxes().length;
      pos[ fixedAxis ] = offset;
      return isEmptyRecurse( fixedAxis, axisCount - 1 );
    }

    private boolean isEmptyRecurse( int fixedAxis, int axis ) {
      if ( axis < 0 ) {
        RolapCell cell = (RolapCell) underlying.getCell( pos );
        return cell.isNull();
      } else if ( axis == fixedAxis ) {
        return isEmptyRecurse( fixedAxis, axis - 1 );
      } else {
        List<Position> positions = getAxes()[ axis ].getPositions();
        final int positionCount = positions.size();
        for ( int i = 0; i < positionCount; i++ ) {
          pos[ axis ] = i;
          if ( !isEmptyRecurse( fixedAxis, axis - 1 ) ) {
            return false;
          }
        }
        return true;
      }
    }

    // synchronized because we use 'pos'
    @Override
	public synchronized Cell getCell( int[] externalPos ) {
      try {
        System.arraycopy(
          externalPos, 0, this.pos, 0, externalPos.length );
        int offset = externalPos[ axis ];
        int mappedOffset = mapOffsetToUnderlying( offset );
        this.pos[ axis ] = mappedOffset;
        return underlying.getCell( this.pos );
      } catch ( NullPointerException npe ) {
        return underlying.getCell( externalPos );
      }
    }

    private int mapOffsetToUnderlying( int offset ) {
      return map.get( offset );
    }

    @Override
	public void close() {
      underlying.close();
    }

      @Override
      public Member[] getCellMembers(int[] coordinates) {
          return underlying.getCellMembers(coordinates);
      }
  }


  //TODO: Extract a statement between connection and Resuolt without the query
  @Override
  public org.eclipse.daanse.olap.api.Statement createStatement() {
    return new org.eclipse.daanse.olap.impl.StatementImpl(this);
  }
}
