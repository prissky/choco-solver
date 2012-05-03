/**
 *  Copyright (c) 1999-2011, Ecole des Mines de Nantes
 *  All rights reserved.
 *  Redistribution and use in source and binary forms, with or without
 *  modification, are permitted provided that the following conditions are met:
 *
 *      * Redistributions of source code must retain the above copyright
 *        notice, this list of conditions and the following disclaimer.
 *      * Redistributions in binary form must reproduce the above copyright
 *        notice, this list of conditions and the following disclaimer in the
 *        documentation and/or other materials provided with the distribution.
 *      * Neither the name of the Ecole des Mines de Nantes nor the
 *        names of its contributors may be used to endorse or promote products
 *        derived from this software without specific prior written permission.
 *
 *  THIS SOFTWARE IS PROVIDED BY THE REGENTS AND CONTRIBUTORS ``AS IS'' AND ANY
 *  EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 *  WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 *  DISCLAIMED. IN NO EVENT SHALL THE REGENTS AND CONTRIBUTORS BE LIABLE FOR ANY
 *  DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 *  (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 *  LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 *  ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 *  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 *  SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package solver.constraints.propagators.gary.trees;

import choco.kernel.ESat;
import solver.Solver;
import solver.constraints.Constraint;
import solver.constraints.propagators.GraphPropagator;
import solver.constraints.propagators.Propagator;
import solver.constraints.propagators.PropagatorPriority;
import solver.exception.ContradictionException;
import solver.recorders.fine.AbstractFineEventRecorder;
import solver.variables.EventType;
import solver.variables.IntVar;
import solver.variables.Variable;
import solver.variables.graph.INeighbors;
import solver.variables.graph.undirectedGraph.UndirectedGraphVar;

/**
 * Compute the cost of the graph by summing edge costs
 * - For minimization problem
 * */
public class PropTreeEvalObj<V extends Variable> extends GraphPropagator<V> {

	//***********************************************************************************
	// VARIABLES
	//***********************************************************************************

	protected UndirectedGraphVar g;
	protected int n;
	protected IntVar sum;
	protected int[][] distMatrix;
	protected int[] lowestUnused;

	//***********************************************************************************
	// CONSTRUCTORS
	//***********************************************************************************

	public PropTreeEvalObj(UndirectedGraphVar graph, IntVar obj, int[][] costMatrix, Constraint<V, Propagator<V>> constraint, Solver solver) {
		super((V[]) new Variable[]{graph, obj}, solver, constraint, PropagatorPriority.LINEAR);
		g = graph;
		sum = obj;
		n = g.getEnvelopGraph().getNbNodes();
		distMatrix = costMatrix;
		lowestUnused = new int[n];
	}

	//***********************************************************************************
	// METHODS
	//***********************************************************************************

	@Override
	public void propagate(int evtmask) throws ContradictionException {
		int minSum =0;
		INeighbors nei;
		for (int i = 0; i < n; i++) {
			lowestUnused[i] = 0;
			nei = g.getEnvelopGraph().getNeighborsOf(i);
			for(int j=nei.getFirstElement();j>=0;j=nei.getNextElement()){
				if(!g.getKernelGraph().arcExists(i,j)){
					if( lowestUnused[i] > distMatrix[i][j]){
						lowestUnused[i] = distMatrix[i][j];
					}
				}
			}
			nei = g.getKernelGraph().getNeighborsOf(i);
			if(nei.neighborhoodSize()>0){
				for(int j=nei.getFirstElement();j>=0;j=nei.getNextElement()){
					minSum += distMatrix[i][j];
				}
			}else{
				minSum += lowestUnused[i];
			}
		}
		minSum/=2;
		sum.updateLowerBound(minSum, this);
		filter(minSum);
	}
	
	@Override
	public void propagate(AbstractFineEventRecorder eventRecorder, int idxVarInProp, int mask) throws ContradictionException {
		propagate(0);
	}

	@Override
	public int getPropagationConditions(int vIdx) {
		return EventType.REMOVEARC.mask + EventType.ENFORCEARC.mask + EventType.DECUPP.mask;
	}

	@Override
	public ESat isEntailed() {
		return ESat.UNDEFINED;
	}

	protected void filter(int minSum) throws ContradictionException {
		INeighbors succs;
		int delta = sum.getUB()-minSum;
		for (int i = 0; i < n; i++) {
			succs = g.getEnvelopGraph().getSuccessorsOf(i);
			for (int j = succs.getFirstElement(); j >= 0; j = succs.getNextElement()) {
				if(i<j && !g.getKernelGraph().edgeExists(i,j)){
					if(distMatrix[i][j]-lowestUnused[i]>delta){
						g.removeArc(i,j,this);
					}
				}
			}
		}
	}
}