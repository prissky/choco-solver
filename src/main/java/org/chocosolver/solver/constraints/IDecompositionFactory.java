/**
 * This file is part of choco-solver, http://choco-solver.org/
 *
 * Copyright (c) 2018, IMT Atlantique. All rights reserved.
 *
 * Licensed under the BSD 4-clause license.
 * See LICENSE file in the project root for full license information.
 */
package org.chocosolver.solver.constraints;

import gnu.trove.iterator.TIntIterator;
import gnu.trove.set.hash.TIntHashSet;

import org.chocosolver.solver.ISelf;
import org.chocosolver.solver.Model;
import org.chocosolver.solver.constraints.extension.Tuples;
import org.chocosolver.solver.constraints.nary.automata.FA.IAutomaton;
import org.chocosolver.solver.variables.BoolVar;
import org.chocosolver.solver.variables.IntVar;

import java.util.stream.IntStream;

import static java.lang.Integer.MAX_VALUE;
import static java.lang.Integer.MIN_VALUE;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.String.format;

/**
 * An interface dedicated to list decomposition of some constraints.
 * <p>
 * Project: choco-solver.
 * @author Charles Prud'homme
 * @since 12/06/2018.
 */
public interface IDecompositionFactory extends ISelf<Model> {

    /**
     * Creates and <b>posts</b> a decomposition of a cumulative constraint:
     * associates a boolean variable to each task and each point of time sich that
     * the scalar product of boolean variables per heights for each time never exceed capacity.
     *
     * @param starts   starting time of each task
     * @param durations processing time of each task
     * @param heights  resource consumption of each task
     * @param capacity resource capacity
     * @see org.chocosolver.solver.constraints.IIntConstraintFactory#cumulative(IntVar[], int[], int[], int)
     */
    default void cumulativeTimeDecomp(IntVar[] starts, int[] durations, int[] heights, int capacity) {
        int n = starts.length;
        // 1. find range of 't' parameters while creating variables
        int min_t = MAX_VALUE, max_t = MIN_VALUE;
        for (int i = 0; i < n; i++) {
            min_t = min(min_t, starts[i].getLB());
            max_t = max(max_t, starts[i].getUB() + durations[i]);
        }
        for (int t = min_t; t <= max_t; t++) {
            BoolVar[] bit = ref().boolVarArray(format("b_%s_", t), n);
            for (int i = 0; i < n; i++) {
                BoolVar[] bits = ref().boolVarArray(format("b_%s_%s_", t, i), 2);
                ref().reifyXltC(starts[i], t + 1, bits[0]);
                ref().reifyXgtC(starts[i], t - durations[i], bits[1]);
                ref().addClausesBoolAndArrayEqVar(bits, bit[i]);
            }
            ref().scalar(
                    bit,
                    IntStream.range(0, n).map(i -> heights[i]).toArray(),
                    "<=",
                    capacity
            ).post();
        }
    }

    /**
     * Creates and <b>posts</b> a decomposition of a regular constraint.
     * Enforces the sequence of vars to be a word
     * recognized by the deterministic finite automaton.
     * For example regexp = "(1|2)(3*)(4|5)";
     * The same dfa can be used for different propagators.
     *
     * @param vars      sequence of variables
     * @param automaton a deterministic finite automaton defining the regular language
     * @return array of variables that encodes the states, which can optionally be constrained too.
     */
    default IntVar[] regularDec(IntVar[] vars, IAutomaton automaton) {
        int n = vars.length;
        IntVar[] states = new IntVar[n + 1];
        TIntHashSet[] layer = new TIntHashSet[n + 1];
        for (int i = 0; i <= n; i++) {
            layer[i] = new TIntHashSet();
        }
        layer[0].add(automaton.getInitialState());
        states[0] = ref().intVar("Q_0", layer[0].toArray());
        TIntHashSet nexts = new TIntHashSet();
        for (int i = 0; i < n; i++) {
            int ub = vars[i].getUB();
            Tuples tuples = new Tuples(true);
            for (int j = vars[i].getLB(); j <= ub; j = vars[i].nextValue(j)) {
                TIntIterator layerIter = layer[i].iterator();
                while (layerIter.hasNext()) {
                    int k = layerIter.next();
                    nexts.clear();
                    automaton.delta(k, j, nexts);
                    for (TIntIterator it = nexts.iterator(); it.hasNext(); ) {
                        int succ = it.next();
                        if(i + 1 < n || automaton.isFinal(succ)) {
                            layer[i + 1].add(succ);
                            tuples.add(k, succ, j);
                        }
                    }
                }
            }
            states[i + 1] = ref().intVar("Q_" + (i+1), layer[i + 1].toArray());
            ref().table(new IntVar[]{states[i], states[i + 1], vars[i]}, tuples).post();
        }
        return states;
    }
}
