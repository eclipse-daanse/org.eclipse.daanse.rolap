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
package org.eclipse.daanse.rolap.element;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.Description;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.olap.api.element.MetaData;
import org.eclipse.daanse.olap.element.OlapMetaDataBase;

import org.eclipse.daanse.rolap.mapping.model.provider.util.CwmHelper;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.util.Descriptions;
public class RolapMetaData extends OlapMetaDataBase {

	public RolapMetaData() {
		super(Map.of());
	}

	public RolapMetaData(Map<String, Object> map) {
		super(map);
	}

	public static MetaData createMetaData(List<? extends org.eclipse.daanse.cwm.model.cwm.objectmodel.core.TaggedValue> taggedValues) {
		if (taggedValues == null || taggedValues.isEmpty()) {
			return OlapMetaDataBase.empty();
		}
		return new RolapMetaData(fold(taggedValues, null));
	}

	/**
	 * Metadata of a mapping element: its localized businessinformation
	 * Descriptions folded to the annotation keys getLocalized expects
	 * (caption.de_DE / description.de_DE), then its tagged values — explicit
	 * annotations win over folded descriptions. Language-neutral texts (und)
	 * are not folded; they are the mapping-level default served by the
	 * Documentation helper, not a locale variant.
	 */
	public static MetaData createMetaData(ModelElement element) {
		if (element == null) {
			return OlapMetaDataBase.empty();
		}
		Map<String, Object> map = fold(element.getTaggedValue(), element);
		return map.isEmpty() ? OlapMetaDataBase.empty() : new RolapMetaData(map);
	}

	private static Map<String, Object> fold(
			List<? extends org.eclipse.daanse.cwm.model.cwm.objectmodel.core.TaggedValue> taggedValues,
			ModelElement element) {
		// Use linked hash map because it retains order.
		final Map<String, Object> map = new LinkedHashMap<>();
		if (element != null) {
			for (Description description : Descriptions.all(element)) {
				String language = description.getLanguage();
				if (language == null || CwmHelper.LANGUAGE_NEUTRAL.equals(language)
						|| description.getBody() == null) {
					continue;
				}
				String prop = CwmHelper.TYPE_CAPTION.equals(description.getType()) ? "caption"
						: CwmHelper.TYPE_DOCUMENTATION.equals(description.getType()) ? "description" : null;
				if (prop != null) {
					map.put(prop + "." + Locale.forLanguageTag(language), description.getBody());
				}
			}
		}
		if (taggedValues != null) {
			for (org.eclipse.daanse.cwm.model.cwm.objectmodel.core.TaggedValue taggedValue : taggedValues) {
				map.put(taggedValue.getTag(), taggedValue.getValue());
			}
		}
		return map;
	}
}
