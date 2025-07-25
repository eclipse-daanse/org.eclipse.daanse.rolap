/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2002-2005 Julian Hyde
 * Copyright (C) 2005-2021 Hitachi Vantara and others
 * All Rights Reserved.
 *
 * jhyde, 21 March, 2002
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


package org.eclipse.daanse.rolap.common.agg;

import static org.eclipse.daanse.rolap.common.util.ExpressionUtil.genericExpression;

import java.io.PrintWriter;
import java.util.AbstractList;
import java.util.List;

import org.eclipse.daanse.jdbc.db.dialect.api.BestFitColumnType;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.olap.key.BitKey;
import org.eclipse.daanse.olap.spi.SegmentHeader;
import org.eclipse.daanse.rolap.common.RolapStar;
import org.eclipse.daanse.rolap.common.StarColumnPredicate;
import org.eclipse.daanse.rolap.common.StarPredicate;

/**
 * A Segment is a collection of cell values parameterized by a measure, and a set of (column, value) pairs.
 * An example of a segment is
 *
 *
 *
 *
 * (Unit sales, Gender = 'F', State in {'CA','OR'}, Marital Status = <i> anything</i>)
 *
 *
 *
 *
 * All segments over the same set of columns belong to an Aggregation, in this case:
 *
 *
 *
 *
 * ('Sales' Star, Gender, State, Marital Status)
 *
 *
 *
 *
 * Note that different measures (in the same Star) occupy the same Aggregation. Aggregations belong to the
 * AggregationManager, a singleton.
 *
 *
 *
 * Segments are pinned during the evaluation of a single MDX query. The query evaluates the expressions twice. The first
 * pass, it finds which cell values it needs, pins the segments containing the ones which are already present (one
 * pin-count for each cell value used), and builds a {@link CellRequest cell request} for those which are not present.
 * It executes the cell request to bring the required cell values into the cache, again, pinned. Then it evalutes the
 * query a second time, knowing that all cell values are available. Finally, it releases the pins.
 *
 *
 *
 * A Segment may have a list of {@link ExcludedRegion} objects. These are caused by cache flushing. Usually a segment is
 * a hypercube: it is defined by a set of values on each of its axes. But after a cache flush request, a segment may
 * have a rectangular 'hole', and therefore not be a hypercube anymore.
 *
 *
 * For example, the segment defined by {CA, OR, WA} * {F, M} is a 2-dimensional hyper-rectangle with 6 cells. After
 * flushing {CA, OR, TX} * {F}, the result is 4 cells:
 *
 *
 *     F     M
 * CA  out   in
 * OR  out   in
 * WA  in    in
 *
 *
 * defined by the original segment minus the region ({CA, OR} * {F}).
 *
 * @author jhyde
 * @since 21 March, 2002
 */
public class Segment {
  private static int nextId = 0; // generator for "id"

  final int id; // for debug
  private String desc;

  /**
   * This is set in the load method and is used during the processing of a particular aggregate load.
   */
  protected final RolapStar.Column[] columns;

  public final RolapStar.Measure measure;

  /**
   * An array of axes, one for each constraining column, containing the values returned for that constraining column.
   */
  public final StarColumnPredicate[] predicates;

  protected final RolapStar star;
  protected final BitKey constrainedColumnsBitKey;

  /**
   * List of regions to ignore when reading this segment. This list is populated when a region is flushed. The cells for
   * these regions may be physically in the segment, because trimming segments can be expensive, but should still be
   * ignored.
   */
  protected final List<ExcludedRegion> excludedRegions;

  private final int aggregationKeyHashCode;
  protected final List<StarPredicate> compoundPredicateList;

  private final SegmentHeader segmentHeader;

  /**
   * Creates a Segment; it's not loaded yet.
   *
   * @param star
   *          Star that this Segment belongs to
   * @param measure
   *          Measure whose values this Segment contains
   * @param predicates
   *          List of predicates constraining each axis
   * @param excludedRegions
   *          List of regions which are not in this segment.
   */
  public Segment( RolapStar star, BitKey constrainedColumnsBitKey, RolapStar.Column[] columns,
      RolapStar.Measure measure, StarColumnPredicate[] predicates, List<ExcludedRegion> excludedRegions,
      final List<StarPredicate> compoundPredicateList ) {
    this.id = nextId++;
    this.star = star;
    this.constrainedColumnsBitKey = constrainedColumnsBitKey;
    this.columns = columns;
    this.measure = measure;
    this.predicates = predicates;
    this.excludedRegions = excludedRegions;
    this.compoundPredicateList = compoundPredicateList;
    final List<BitKey> compoundPredicateBitKeys = compoundPredicateList == null ? null : new AbstractList<>() {
      @Override
	public BitKey get( int index ) {
        return compoundPredicateList.get( index ).getConstrainedColumnBitKey();
      }

      @Override
	public int size() {
        return compoundPredicateList.size();
      }
    };
    this.aggregationKeyHashCode =
        AggregationKey.computeHashCode( constrainedColumnsBitKey, star, compoundPredicateBitKeys );
    this.segmentHeader = SegmentBuilder.toHeader( this );
  }

  /**
   * Returns the constrained columns.
   */
  public RolapStar.Column[] getColumns() {
    return columns;
  }

  /**
   * Returns the star.
   */
  public RolapStar getStar() {
    return star;
  }

  /**
   * Returns the list of compound predicates.
   */
  public List<StarPredicate> getCompoundPredicateList() {
    return compoundPredicateList;
  }

  /**
   * Returns the BitKey for ALL columns (Measures and Levels) involved in the query.
   */
  public BitKey getConstrainedColumnsBitKey() {
    return constrainedColumnsBitKey;
  }

  private void describe( StringBuilder buf, boolean values ) {
    final String sep = new StringBuilder(Util.NL).append("    ").toString();
    buf.append( printSegmentHeaderInfo( sep ) );

    for ( int i = 0; i < columns.length; i++ ) {
      buf.append( sep );
      buf.append( genericExpression(columns[i].getExpression()) );
      describeAxes( buf, i, values );
    }
    if ( !excludedRegions.isEmpty() ) {
      buf.append( sep );
      buf.append( "excluded={" );
      int k = 0;
      for ( ExcludedRegion excludedRegion : excludedRegions ) {
        if ( k++ > 0 ) {
          buf.append( ", " );
        }
        excludedRegion.describe( buf );
      }
      buf.append( '}' );
    }
    buf.append( '}' );
  }

  protected void describeAxes( StringBuilder buf, int i, boolean values ) {
    predicates[i].describe( buf );
  }

  private String printSegmentHeaderInfo( String sep ) {
    StringBuilder buf = new StringBuilder();
    buf.append( "Segment #" );
    buf.append( id );
    buf.append( " {" );
    buf.append( sep );
    buf.append( "measure=" );
    buf.append( measure.getExpression() == null ? measure.getAggregator().getExpression( "*" ) : measure
        .getAggregator().getExpression( genericExpression(measure.getExpression())) );
    return buf.toString();
  }

  @Override
public String toString() {
    if ( this.desc == null ) {
      StringBuilder buf = new StringBuilder( 64 );
      describe( buf, false );
      this.desc = buf.toString();
    }
    return this.desc;
  }

  /**
   * Returns whether a cell value is excluded from this segment.
   */
  protected final boolean isExcluded( Object[] keys ) {
    // Performance critical: cannot use foreach
    final int n = excludedRegions.size();
    // noinspection ForLoopReplaceableByForEach
    for ( int i = 0; i < n; i++ ) {
      ExcludedRegion excludedRegion = excludedRegions.get( i );
      if ( excludedRegion.wouldContain( keys ) ) {
        return true;
      }
    }
    return false;
  }

  /**
   * Prints the state of this Segment, including constraints and values. Blocks the current thread until
   * the segment is loaded.
   *
   * @param pw
   *          Writer
   */
  public void print( PrintWriter pw ) {
    final StringBuilder buf = new StringBuilder();
    describe( buf, true );
    pw.print( buf.toString() );
    pw.println();
  }

  public List<ExcludedRegion> getExcludedRegions() {
    return excludedRegions;
  }

  SegmentDataset createDataset(SegmentAxis[] axes, boolean sparse, BestFitColumnType type, int size ) {
    if ( sparse ) {
      return new SparseSegmentDataset();
    } else {
      return switch (type) {
      case OBJECT, LONG, STRING -> new DenseObjectSegmentDataset( axes, size );
      case INT -> new DenseIntSegmentDataset( axes, size );
      case DOUBLE, DECIMAL -> new DenseDoubleSegmentDataset( axes, size );
      default -> throw Util.unexpected( type );
      };
    }
  }

  public boolean matches( AggregationKey aggregationKey, RolapStar.Measure measure ) {
    // Perform high-selectivity comparisons first.
    return aggregationKeyHashCode == aggregationKey.hashCode() && this.measure == measure && matchesInternal(
        aggregationKey );
  }

  private boolean matchesInternal( AggregationKey aggKey ) {
    return constrainedColumnsBitKey.equals( aggKey.getConstrainedColumnsBitKey() ) && star.equals( aggKey.getStar() )
        && AggregationKey.equalAggregationKey( compoundPredicateList, aggKey.compoundPredicateList );
  }

  /**
   * Definition of a region of values which are not in a segment.
   */
  public static interface ExcludedRegion {
    /**
     * Tells whether this exclusion region would contain the cell corresponding to the keys.
     */
    public boolean wouldContain( Object[] keys );

    /**
     * Returns the arity of this region.
     */
    public int getArity();

    /**
     * Describes this exclusion region in a human readable way.
     */
    public void describe( StringBuilder buf );

    /**
     * Returns an approximation of the number of cells exceluded in this region.
     */
    public int getCellCount();
  }

  public SegmentHeader getHeader() {
    return this.segmentHeader;
  }
}
