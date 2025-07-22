/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2021 Sergei Semenkov
 * All Rights Reserved.
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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.daanse.olap.api.DrillThroughAction;
import org.eclipse.daanse.olap.api.DrillThroughColumn;
import org.eclipse.daanse.olap.api.element.OlapElement;

public class RolapDrillThroughAction extends AbstractRolapAction implements DrillThroughAction {
	private final boolean isDefault;
	private List<DrillThroughColumn> columnList;

	public RolapDrillThroughAction(String name, String caption, String description, boolean isDefault,
			List<DrillThroughColumn> columnList) {
		super(name, caption, description);
		this.isDefault = isDefault;
		this.columnList = columnList;
		if (this.columnList == null) {
			this.columnList = new ArrayList<>();
		}
	}

	@Override
	public boolean getIsDefault() {
		return this.isDefault;
	}

	public List<DrillThroughColumn> getColumns() {
		return this.columnList;
	}

	@Override
	public List<OlapElement> getOlapElements() {
		List<OlapElement> olapElementList = new ArrayList<>();
		for (DrillThroughColumn rolapDrillThroughColumn : this.columnList) {
			olapElementList.add(rolapDrillThroughColumn.getOlapElement());
		}
		return olapElementList;
	}

}
