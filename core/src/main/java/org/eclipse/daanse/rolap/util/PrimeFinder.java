/*
* This software is subject to the terms of the Eclipse Public License v1.0
* Agreement, available at the following URL:
* http://www.eclipse.org/legal/epl-v10.html.
* You must accept the terms of that agreement to use this software.
*
* Copyright (c) 2002-2017 Hitachi Vantara..  All rights reserved.
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


//   Copyright (c) 1999-2007 CERN - European Organization for Nuclear Research.
//   Permission to use, copy, modify, distribute and sell this software
//   and its documentation for any purpose is hereby granted without fee,
//   provided that the above copyright notice appear in all copies and
//   that both that copyright notice and this permission notice appear in
//   supporting documentation. CERN makes no representations about the
//   suitability of this software for any purpose. It is provided "as is"
//   without expressed or implied warranty.

// Created from package cern.colt.map by Richard Emberson, 2007/1/23.
// For the source to the Colt project, go to:
// http://dsd.lbl.gov/~hoschek/colt/
package org.eclipse.daanse.rolap.util;

import java.io.PrintWriter;
import java.util.Arrays;

/**
 * Not of interest for users; only for implementors of hashtables.
 * Used to keep hash table capacities prime numbers.
 *
 * 
 * Choosing prime numbers as hash table capacities is a good idea to keep
 * them working fast, particularly under hash table expansions.
 * 
 * However, JDK 1.2, JGL 3.1 and many other toolkits do nothing to
 * keep capacities prime.
 * This class provides efficient means to choose prime capacities.
 * 
 * Choosing a prime is O(log 300) (binary search in a list of
 * 300 int's).
 * Memory requirements: 1 KB static memory.
 *
 * @author wolfgang.hoschek@cern.ch
 * @version 1.0, 09/24/99
 */
final public class PrimeFinder {
    /**
     * The largest prime this class can generate; currently equal to
     * Integer.MAX_VALUE.
     * yes, it is prime.
     */
    public static final int largestPrime = Integer.MAX_VALUE;

    /**
     * The prime number list consists of 11 chunks.
     * Each chunk contains prime numbers.
     * A chunk starts with a prime P1. The next element is a prime P2. P2
     * is the smallest prime for which holds: P2 >= 2*P1.
     * The next element is P3, for which the same holds with respect to P2,
     * and so on.
     *
     * Chunks are chosen such that for any desired capacity >= 1000
     * the list includes a prime number <= desired capacity * 1.11 (11%).
     * For any desired capacity >= 200
     * the list includes a prime number <= desired capacity * 1.16 (16%).
     * For any desired capacity >= 16
     * the list includes a prime number <= desired capacity * 1.21 (21%).
     *
     * Therefore, primes can be retrieved which are quite close to any desired
     * capacity, which in turn avoids wasting memory.
     * For example, the list includes
     * 1039,1117,1201,1277,1361,1439,1523,1597,1759,1907,2081.
     * So if you need a prime >= 1040, you will find a prime <= 1040*1.11=1154.
     *
     * Chunks are chosen such that they are optimized for a hashtable
     * growthfactor of 2.0;
     * If your hashtable has such a growthfactor then,
     * after initially "rounding to a prime" upon hashtable construction,
     * it will later expand to prime capacities such that there exist no
     * better primes.
     *
     * In total these are about 32*10=320 numbers -> 1 KB of static memory
     * needed.
     * If you are stingy, then delete every second or fourth chunk.
     */

    private static final int[] primeCapacities = {
        //chunk #0
        largestPrime,

        //chunk #1
        5, 11, 23, 47, 97, 197, 397, 797, 1597, 3203, 6421, 12853, 25717, 51437,
        102877, 205759, 411527, 823117, 1646237, 3292489, 6584983, 13169977,
        26339969, 52679969, 105359939, 210719881, 421439783, 842879579,
        1685759167,

        //chunk #2
        433, 877, 1759, 3527, 7057, 14143, 28289, 56591, 113189, 226379, 452759,
        905551, 1811107, 3622219, 7244441, 14488931, 28977863, 57955739,
        115911563, 231823147, 463646329, 927292699, 1854585413,

        //chunk #3
        953, 1907, 3821, 7643, 15287, 30577, 61169, 122347, 244703, 489407,
        978821, 1957651, 3915341, 7830701, 15661423, 31322867, 62645741,
        125291483, 250582987, 501165979, 1002331963, 2004663929,

        //chunk #4
        1039, 2081, 4177, 8363, 16729, 33461, 66923, 133853, 267713, 535481,
        1070981, 2141977, 4283963, 8567929, 17135863, 34271747, 68543509,
        137087021, 274174111, 548348231, 1096696463,

        //chunk #5
        31, 67, 137, 277, 557, 1117, 2237, 4481, 8963, 17929, 35863, 71741,
        143483, 286973, 573953, 1147921, 2295859, 4591721, 9183457, 18366923,
        36733847, 73467739, 146935499, 293871013, 587742049, 1175484103,

        //chunk #6
        599, 1201, 2411, 4831, 9677, 19373, 38747, 77509, 155027, 310081,
        620171, 1240361, 2480729, 4961459, 9922933, 19845871, 39691759,
        79383533, 158767069, 317534141, 635068283, 1270136683,

        //chunk #7
        311, 631, 1277, 2557, 5119, 10243, 20507, 41017, 82037, 164089, 328213,
        656429, 1312867, 2625761, 5251529, 10503061, 21006137, 42012281,
        84024581, 168049163, 336098327, 672196673, 1344393353,

        //chunk #8
        3, 7, 17, 37, 79, 163, 331, 673, 1361, 2729, 5471, 10949, 21911, 43853,
        87719, 175447, 350899, 701819, 1403641, 2807303, 5614657, 11229331,
        22458671, 44917381, 89834777, 179669557, 359339171, 718678369,
        1437356741,

        //chunk #9
        43, 89, 179, 359, 719, 1439, 2879, 5779, 11579, 23159, 46327, 92657,
        185323, 370661, 741337, 1482707, 2965421, 5930887, 11861791, 23723597,
        47447201, 94894427, 189788857, 379577741, 759155483, 1518310967,

        //chunk #10
        379, 761, 1523, 3049, 6101, 12203, 24407, 48817, 97649, 195311, 390647,
        781301, 1562611, 3125257, 6250537, 12501169, 25002389, 50004791,
        100009607, 200019221, 400038451, 800076929, 1600153859

        /*
        // some more chunks for the low range [3..1000]
        //chunk #11
        13, 29, 59, 127, 257, 521, 1049, 2099, 4201, 8419, 16843, 33703, 67409,
        134837, 269683, 539389, 1078787, 2157587, 4315183, 8630387, 17260781,
        34521589, 69043189, 138086407, 276172823, 552345671, 1104691373,

        //chunk #12
        19, 41, 83, 167, 337, 677,

        //1361, 2729, 5471, 10949, 21911, 43853, 87719, 175447, 350899, 701819,
        //1403641, 2807303, 5614657, 11229331, 22458671, 44917381, 89834777,
        //179669557, 359339171, 718678369, 1437356741,

        //chunk #13
        53, 107, 223, 449, 907, 1823, 3659, 7321, 14653, 29311, 58631, 117269,
        234539, 469099, 938207, 1876417, 3752839, 7505681, 15011389, 30022781,
        60045577, 120091177, 240182359, 480364727, 960729461, 1921458943
        */
        };


    static { //initializer
        // The above prime numbers are formatted for human readability.
        // To find numbers fast, we sort them once and for all.
        Arrays.sort(primeCapacities);
    }

    /**
     * Makes this class non instantiable.
     */
    private PrimeFinder() {
    }

    /**
     * Returns a prime number which is &gt;= desiredCapacity and
     * very close to desiredCapacity (within 11% if
     * desiredCapacity &gt;= 1000).
     * @param desiredCapacity the capacity desired by the user.
     * @return the capacity which should be used for a hashtable.
     */
    public static int nextPrime(int desiredCapacity) {
        int i = Arrays.binarySearch(primeCapacities, desiredCapacity);
        if (i < 0) {
            // desired capacity not found, choose next prime greater
            // than desired capacity
            i = -i -1; // remember the semantics of binarySearch...
        }
        return primeCapacities[i];
    }

    /**
     * Tests correctness. Try
     * from=1000, to=10000
     * from=200,  to=1000
     * from=16,   to=1000
     * from=1000, to=Integer.MAX_VALUE
     */
    protected static void main(String args[]) {
        int from = Integer.parseInt(args[0]);
        int to = Integer.parseInt(args[1]);

        statistics(from, to, new PrintWriter(System.out));
    }

    /**
     * Tests correctness.
     */
    public static void statistics(int from, int to, PrintWriter pw) {
        // check that primes contain no accidental errors
        for (int i = 0; i < primeCapacities.length - 1; i++) {
            if (primeCapacities[i] >= primeCapacities[i + 1]) {
                throw new RuntimeException(
                    new StringBuilder("primes are unsorted or contain duplicates; detected at ")
                    .append(i).append("@").append(primeCapacities[i]).toString());
            }
        }

        double accDeviation = 0.0;
        double maxDeviation = - 1.0;

        for (int i = from; i <= to; i++) {
            int primeCapacity = nextPrime(i);
            //System.out.println(primeCapacity);
            double deviation = (primeCapacity - i) / (double)i;

            if (deviation > maxDeviation) {
                maxDeviation = deviation;
                pw.println(new StringBuilder("new maxdev @").append(i).append("@dev=").append(maxDeviation).toString());
            }

            accDeviation += deviation;
        }
        long width = 1 + (long)to - (long)from;

        double meanDeviation = accDeviation / width;
        pw.println(new StringBuilder("Statistics for [").append(from).append(",")
            .append(to).append("] are as follows").toString());
        pw.println(new StringBuilder("meanDeviation = ").append((float)meanDeviation * 100).append(" %").toString());
        pw.println(new StringBuilder("maxDeviation = ").append((float)maxDeviation * 100).append(" %").toString());
    }
}
