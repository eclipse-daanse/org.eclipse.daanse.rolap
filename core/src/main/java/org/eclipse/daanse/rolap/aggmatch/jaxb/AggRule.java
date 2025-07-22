/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation.
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
package org.eclipse.daanse.rolap.aggmatch.jaxb;

/**
 * A RolapConnection uses one AggRule. If no name is specified, then
 * the AggRule which is marked as default==true is used (validation
 * fails if one and only one AggRule is not marked as the default).
 * An AggRule has manditory child elements for matching the
 * aggregate table names, aggregate table fact count column,
 * foreign key columns, the measure columns, and the hierarchy level
 * columns. These child elements can be specified as direct children
 * of an AggRule element or by reference to elements defined as a
 * pier to the AggRule (using references allows reuse of the child
 * elements and with one quick edit the reference to use can be
 * changed by changing the refid attribute value).
 */

public class AggRule extends Base {

    /**
     * Name of this AggRule
     */
    private String tag;

    /**
     * Name of the aggregate column containing the count for
     * the row.
     */
    String countColumn = "fact_count";

    private IgnoreMap ignoreMap;

    public FactCountMatchRef getFactCountMatchRef() {
		return factCountMatchRef;
	}

	public void setFactCountMatchRef(FactCountMatchRef factCountMatchRef) {
		this.factCountMatchRef = factCountMatchRef;
	}

	public ForeignKeyMatchRef getForeignKeyMatchRef() {
		return foreignKeyMatchRef;
	}

	public void setForeignKeyMatchRef(ForeignKeyMatchRef foreignKeyMatchRef) {
		this.foreignKeyMatchRef = foreignKeyMatchRef;
	}

	public TableMatchRef getTableMatchRef() {
		return tableMatchRef;
	}

	public void setTableMatchRef(TableMatchRef tableMatchRef) {
		this.tableMatchRef = tableMatchRef;
	}

	public LevelMapRef getLevelMapRef() {
		return levelMapRef;
	}

	public void setLevelMapRef(LevelMapRef levelMapRef) {
		this.levelMapRef = levelMapRef;
	}

	public MeasureMapRef getMeasureMapRef() {
		return measureMapRef;
	}

	public void setMeasureMapRef(MeasureMapRef measureMapRef) {
		this.measureMapRef = measureMapRef;
	}

	public void setCountColumn(String countColumn) {
		this.countColumn = countColumn;
	}

	public void setForeignKeyMatch(ForeignKeyMatch foreignKeyMatch) {
		this.foreignKeyMatch = foreignKeyMatch;
	}

	public void setTableMatch(TableMatch tableMatch) {
		this.tableMatch = tableMatch;
	}

	public void setLevelMap(LevelMap levelMap) {
		this.levelMap = levelMap;
	}

	public void setMeasureMap(MeasureMap measureMap) {
		this.measureMap = measureMap;
	}

	private IgnoreMapRef ignoreMapRef;

    private FactCountMatch factCountMatch;

    FactCountMatchRef factCountMatchRef;

    ForeignKeyMatch foreignKeyMatch;

    ForeignKeyMatchRef foreignKeyMatchRef;

    TableMatch tableMatch;

    TableMatchRef tableMatchRef;

    LevelMap levelMap;

    LevelMapRef levelMapRef;

    MeasureMap measureMap;

    MeasureMapRef measureMapRef;

    private boolean isOk(final Base base) {
        return ((base != null) && base.isEnabled());
    }

    private boolean isRef(
        final AggRules rules,
        final org.eclipse.daanse.rolap.recorder.MessageRecorder msgRecorder,
        final Base base,
        final Base baseRef,
        final String baseName
    ) {
        if (!isOk(base)) {
            if (isOk(baseRef)) {
                baseRef.validate(rules, msgRecorder);
                return true;
            } else {
                String msg = "Neither base " +
                    baseName +
                    " or baseref " +
                    baseName +
                    "Ref is ok";
                msgRecorder.reportError(msg);
                return false;
            }
        } else if (isOk(baseRef)) {
            String msg = "Both base " +
                base.getName() +
                " and baseref " +
                baseRef.getName() +
                " are ok";
            msgRecorder.reportError(msg);
            return false;
        } else {
            base.validate(rules, msgRecorder);
            return false;
        }
    }

    // called after a constructor is called
    public void validate(
        final AggRules rules,
        final org.eclipse.daanse.rolap.recorder.MessageRecorder msgRecorder
    ) {
        msgRecorder.pushContextName(getName());
        try {
            // IgnoreMap is optional
            if (getIgnoreMap() != null) {
                getIgnoreMap().validate(rules, msgRecorder);
            } else if (getIgnoreMapRef() != null) {
                getIgnoreMapRef().validate(rules, msgRecorder);
                setIgnoreMap(rules.lookupIgnoreMap(getIgnoreMapRef().getRefId()));
            }
            if (isRef(rules, msgRecorder, getFactCountMatch(),
                factCountMatchRef, "FactCountMatch")) {
                setFactCountMatch(rules.lookupFactCountMatch(
                    factCountMatchRef.getRefId()));
            }
            if (isRef(rules, msgRecorder, foreignKeyMatch,
                foreignKeyMatchRef, "ForeignKeyMatch")) {
                foreignKeyMatch = rules.lookupForeignKeyMatch(
                    foreignKeyMatchRef.getRefId());
            }
            if (isRef(rules, msgRecorder, tableMatch,
                tableMatchRef, "TableMatch")) {
                tableMatch =
                    rules.lookupTableMatch(tableMatchRef.getRefId());
            }
            if (isRef(rules, msgRecorder, levelMap,
                levelMapRef, "LevelMap")) {
                levelMap = rules.lookupLevelMap(levelMapRef.getRefId());
            }
            if (isRef(rules, msgRecorder, measureMap,
                measureMapRef, "MeasureMap")) {
                measureMap =
                    rules.lookupMeasureMap(measureMapRef.getRefId());
            }
        } finally {
            msgRecorder.popContextName();
        }
    }

    public String getTag() {
        return tag;
    }

    public String getCountColumn() {
        return countColumn;
    }

    public FactCountMatch getFactCountMatch() {
        return factCountMatch;
    }

    public ForeignKeyMatch getForeignKeyMatch() {
        return foreignKeyMatch;
    }

    public TableMatch getTableMatch() {
        return tableMatch;
    }

    public LevelMap getLevelMap() {
        return levelMap;
    }

    public MeasureMap getMeasureMap() {
        return measureMap;
    }

    public IgnoreMap getIgnoreMap() {
        return ignoreMap;
    }

    @Override
    protected String getName() {
        return "AggRule";
    }

	public void setTag(String tag) {
		this.tag = tag;
	}

	public void setFactCountMatch(FactCountMatch factCountMatch) {
		this.factCountMatch = factCountMatch;
	}

	public void setIgnoreMap(IgnoreMap ignoreMap) {
		this.ignoreMap = ignoreMap;
	}

	public IgnoreMapRef getIgnoreMapRef() {
		return ignoreMapRef;
	}

	public void setIgnoreMapRef(IgnoreMapRef ignoreMapRef) {
		this.ignoreMapRef = ignoreMapRef;
	}
}
