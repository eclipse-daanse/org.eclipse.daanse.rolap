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
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.rolap.common.format;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.api.formatter.MemberFormatter;

public class TimeMemberFormatter implements MemberFormatter {

    private final DateTimeFormatter inFormatter;
    private final DateTimeFormatter outFormatter;
    private final ZoneId zone;

    public TimeMemberFormatter() {

        inFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm:ss.S");
        outFormatter = DateTimeFormatter.ofPattern("dd-MMM-yyyy");

        zone = ZoneId.of("Europe/Berlin");
    }

    public String format(Member member) {

        String value = member.getName();

        LocalDateTime local = LocalDateTime.parse(value, inFormatter);
        ZonedDateTime zoned = local.atZone(zone);
        return zoned.format(outFormatter);

    }

}
