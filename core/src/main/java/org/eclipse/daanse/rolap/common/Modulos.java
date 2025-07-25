/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2005-2005 Julian Hyde
 * Copyright (C) 2005-2017 Hitachi Vantara
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

import org.eclipse.daanse.olap.api.result.Axis;

/**
 * Modulos implementations encapsulate algorithms to map between integral
 * ordinals and position arrays. There are particular implementations for
 * the most likely cases where the number of axes is 1, 2 and 3
 * as well as a general implementation.
 * 
 * Suppose the result is 4 x 3 x 2, then modulo = {1, 4, 12, 24}.
 *
 * 
 * Then the ordinal of cell (3, 2, 1)
 *
 *  = (modulo[0] * 3) + (modulo[1] * 2) + (modulo[2] * 1)
 *  = (1 * 3) + (4 * 2) + (12 * 1)
 *  = 23
 *
 * 
 * Reverse calculation:
 *
 * p[0] = (23 % modulo[1]) / modulo[0] = (23 % 4) / 1 = 3
 * p[1] = (23 % modulo[2]) / modulo[1] = (23 % 12) / 4 = 2
 * p[2] = (23 % modulo[3]) / modulo[2] = (23 % 24) / 12 = 1
 *
 *
 * @author jhyde
 */
public interface Modulos {
    public class Generator {
        public static Modulos create(Axis[] axes) {
            return switch (axes.length) {
            case 0 -> new Modulos.Zero(axes);
            case 1 -> new Modulos.One(axes);
            case 2 -> new Modulos.Two(axes);
            case 3 -> new Modulos.Three(axes);
            default -> new Modulos.Many(axes);
            };
        }
        // Used for testing only
        public static Modulos createMany(Axis[] axes) {
            return new Modulos.Many(axes);
        }
        public static Modulos createMany(int[] lengths) {
            return new Modulos.Many(lengths);
        }
    }
    public abstract class Base implements Modulos {
        protected final int[] modulos;
        protected Base(final Axis[] axes) {
            this.modulos = new int[axes.length + 1];
            this.modulos[0] = 1;
        }
        protected Base(final int[] lengths) {
            this.modulos = new int[lengths.length + 1];
            this.modulos[0] = 1;
        }
        @Override
		public abstract int[] getCellPos(int cellOrdinal);
        @Override
		public abstract int getCellOrdinal(int[] pos);

        @Override
		public String toString() {
            StringBuilder buf = new StringBuilder();
            buf.append('(');
            for (int i = 0; i < modulos.length; i++) {
                if (i > 0) {
                    buf.append(',');
                }
                buf.append(modulos[i]);
            }
            buf.append(')');
            return buf.toString();
        }
    }
    public class Zero extends Base {
        private static final int[] pos = new int[0];
        private Zero(final Axis[] axes) {
            super(axes);
        }
        @Override
		public final int[] getCellPos(final int cellOrdinal) {
            return pos;
        }
        @Override
		public final int getCellOrdinal(final int[] pos) {
            return 0;
        }
    }
    public class One extends Base {
        private One(final Axis[] axes) {
            super(axes);

            this.modulos[1] = axes[0].getPositions().size();
        }
        @Override
		public final int[] getCellPos(final int cellOrdinal) {
            return new int[] {
                (cellOrdinal % this.modulos[1])
            };
        }
        @Override
		public final int getCellOrdinal(final int[] pos) {
            return (pos[0] * modulos[0]);
        }
    }
    public class Two extends Base {
        private Two(final Axis[] axes) {
            super(axes);

            int modulo = axes[0].getPositions().size();
            this.modulos[1] = modulo;
            modulo *= axes[1].getPositions().size();
            this.modulos[2] = modulo;
        }
        @Override
		public final int[] getCellPos(final int cellOrdinal) {
            final int[] modulos = this.modulos;
            return new int[] {
                (cellOrdinal % modulos[1]),
                (cellOrdinal % modulos[2]) / modulos[1]
            };
        }
        @Override
		public final int getCellOrdinal(final int[] pos) {
            final int[] modulos = this.modulos;
            return (pos[0] * modulos[0])
                + (pos[1] * modulos[1]);
        }
    }
    public class Three extends Base {
        private Three(final Axis[] axes) {
            super(axes);

            int modulo = axes[0].getPositions().size();
            this.modulos[1] = modulo;
            modulo *= axes[1].getPositions().size();
            this.modulos[2] = modulo;
            modulo *= axes[2].getPositions().size();
            this.modulos[3] = modulo;
        }
        @Override
		public final int[] getCellPos(final int cellOrdinal) {
            final int[] modulos = this.modulos;
            return new int[] {
                (cellOrdinal % modulos[1]),
                (cellOrdinal % modulos[2]) / modulos[1],
                (cellOrdinal % modulos[3]) / modulos[2]
            };
        }
        @Override
		public final int getCellOrdinal(final int[] pos) {
            final int[] modulos = this.modulos;
            return (pos[0] * modulos[0])
                + (pos[1] * modulos[1])
                + (pos[2] * modulos[2]);
        }
    }
    public class Many extends Base {
        private Many(final Axis[] axes) {
            super(axes);

            int modulo = 1;
            for (int i = 0; i < axes.length; i++) {
                modulo *= axes[i].getPositions().size();
                this.modulos[i + 1] = modulo;
            }
        }
        private Many(final int[] lengths) {
            super(lengths);

            int modulo = 1;
            for (int i = 0; i < lengths.length; i++) {
                modulo *= lengths[i];
                this.modulos[i + 1] = modulo;
            }
        }
        @Override
		public int[] getCellPos(final int cellOrdinal) {
            final int[] modulos = this.modulos;
            final int size = modulos.length - 1;
            final int[] pos = new int[size];
            for (int i = 0; i < size; i++) {
                pos[i] = (cellOrdinal % modulos[i + 1]) / modulos[i];
            }
            return pos;
        }
        @Override
		public int getCellOrdinal(final int[] pos) {
            final int[] modulos = this.modulos;
            final int size = modulos.length - 1;
            int ordinal = 0;
            for (int i = 0; i < size; i++) {
                ordinal += pos[i] * modulos[i];
            }
            return ordinal;
        }
    }

    /**
     * Converts a cell ordinal to a set of cell coordinates. Converse of
     * {@link #getCellOrdinal}. For example, if this result is 10 x 10 x 10,
     * then cell ordinal 537 has coordinates (5, 3, 7).
     *
     * @param cellOrdinal Cell ordinal
     * @return cell coordinates
     */
    int[] getCellPos(int cellOrdinal);

    /**
     * Converts a set of cell coordinates to a cell ordinal. Converse of
     * {@link #getCellPos}.
     *
     * @param pos Cell coordinates
     * @return cell ordinal
     */
    int getCellOrdinal(int[] pos);
}
