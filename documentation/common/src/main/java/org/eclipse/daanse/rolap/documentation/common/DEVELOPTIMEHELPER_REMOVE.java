/*
* Copyright (c) 2024 Contributors to the Eclipse Foundation.
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
*
* Contributors:
*   SmartCity Jena - initial
*/
package org.eclipse.daanse.rolap.documentation.common;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.rolap.documentation.api.ConntextDocumentationProvider;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;

@Component(immediate = true)
public class DEVELOPTIMEHELPER_REMOVE {

	ConntextDocumentationProvider cdp;

	Map<Context, Path> map = new ConcurrentHashMap<>();

	@Activate

	public DEVELOPTIMEHELPER_REMOVE(
			@Reference(cardinality = ReferenceCardinality.MANDATORY) ConntextDocumentationProvider cdp)
			throws Exception {
		this.cdp = cdp;
	}

	public void unbindContext(ConntextDocumentationProvider cdp, Map<String, Object> props) throws Exception {
		cdp = null;
	}

	@Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
	public void bindContext(Context context, Map<String, Object> props) throws Exception {
		System.out.println(props);

		String catPath = props.get("catalog.path").toString();

		System.out.println("---" + catPath);
		Path path = Paths.get(catPath);
		System.out.println("---" + path);

		cdp.createDocumentation(context, path);
		System.out.println(path.toAbsolutePath());
		map.put(context, path);
	}

	public void unbindContext(Context context) throws Exception {
		Path path = map.remove(context);

	}

}
