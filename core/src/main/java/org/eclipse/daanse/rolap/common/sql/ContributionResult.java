/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
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
package org.eclipse.daanse.rolap.common.sql;

/**
 * The outcome of a constraint's {@code toContribution(...)}: either the translated
 * {@link ConstraintContribution} or the PRODUCER-OWNED decline reason. Replaces the former
 * {@code Optional<ConstraintContribution>}, whose empty case forced every routing call site to
 * reconstruct WHY the constraint declined for its census logging — the constraint knew the
 * reason when it bailed, so it carries it.
 *
 * <p>{@code Supported(ConstraintContribution.EMPTY)} still means "translates and adds nothing"
 * (an unrestricted read); {@link Unsupported} means "not expressible on the builder" and routes
 * to the recorder (or the defensive throw where no recorder tail exists).
 */
public sealed interface ContributionResult {

    record Supported(ConstraintContribution contribution) implements ContributionResult {
        public Supported {
            java.util.Objects.requireNonNull(contribution, "contribution");
        }
    }

    /** @param reason grep-stable decline reason, logged verbatim by the routing census. */
    record Unsupported(String reason) implements ContributionResult {
        public Unsupported {
            java.util.Objects.requireNonNull(reason, "reason");
        }
    }

    static ContributionResult of(ConstraintContribution contribution) {
        return new Supported(contribution);
    }

    static ContributionResult unsupported(String reason) {
        return new Unsupported(reason);
    }

    default boolean isSupported() {
        return this instanceof Supported;
    }

    /** The contribution; throws for {@link Unsupported} — gate on {@link #isSupported()} first. */
    default ConstraintContribution contribution() {
        if (this instanceof Supported(ConstraintContribution c)) {
            return c;
        }
        throw new IllegalStateException("unsupported contribution: " + ((Unsupported) this).reason());
    }

    /** The decline reason; null for {@link Supported}. */
    default String reason() {
        return this instanceof Unsupported(String r) ? r : null;
    }
}
