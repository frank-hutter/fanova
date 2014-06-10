package net.aclib.fanova.model;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Vector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.aclib.fanova.eval.ModelEvaluation;
import ca.ubc.cs.beta.aclib.algorithmrun.AlgorithmRun;
import ca.ubc.cs.beta.aclib.configspace.ParamConfigurationSpace;
import ca.ubc.cs.beta.models.fastrf.RandomForest;

public class FunctionalANOVAVarianceDecompose {

	private static final Logger log = LoggerFactory.getLogger(FunctionalANOVAVarianceDecompose.class);
	private double[][][] allObservations ;
	private double[][][] allIntervalSizes;
	private RandomForest forest;
	private double[] thisTreeTotalVariance;
	private Vector <HashMap<Integer,Double>> singleVarianceContributions;
	private HashMap<HashSet<Integer>,Double> thisTreeVarianceContributions = new HashMap<HashSet<Integer>,Double>(); 
	private HashMap<HashSet<Integer>,Double> totalFractionsExplained = new HashMap<HashSet<Integer>,Double>();

	public FunctionalANOVAVarianceDecompose(RandomForest existingForest, List<AlgorithmRun> testRuns,
			ParamConfigurationSpace configSpace, Random rand, 
			boolean compareToDef, double quantileToCompareTo, boolean logModel) throws IOException, InterruptedException 
	{
		forest = ModelEvaluation.extractMarginalForest(existingForest, testRuns, configSpace, rand, compareToDef, quantileToCompareTo);
		RandomForestPreprocessor.preprocessRandomForest(forest, configSpace);
		
		singleVarianceContributions = new Vector<HashMap<Integer,Double>>(); 
		for( int i = 0; i < forest.numTrees; i++)
			singleVarianceContributions.add(new HashMap<Integer, Double>());
	
		//=== Initialize variables to be incrementally updated.
		String s;

		int numDim = configSpace.getCategoricalSize().length;
		allObservations = new double[forest.Trees.length][numDim][];
		allIntervalSizes = new double[forest.Trees.length][numDim][];
		thisTreeTotalVariance = new double[forest.Trees.length];
		//=== Loop over trees.
		for(int numTree=0; numTree<forest.Trees.length; numTree++){
			HashSet<Integer> allVariableIndices = new HashSet<Integer>();
			for(int j=0; j<configSpace.getCategoricalSize().length; j++){
				allVariableIndices.add(new Integer(j));
			}

			//=== Get the tree's total variance (only works for marginal trees, i.e., in the absence of instance features).
			thisTreeTotalVariance[numTree] = RandomForestPreprocessor.computeTotalVarianceOfRegressionTree(forest.Trees[numTree], configSpace);
			if (thisTreeTotalVariance[numTree] == 0.0){
				s = "Tree " + numTree + " has no variance -> skipping.";
				log.info(s);
				continue;
			}
			s = "Tree " + numTree + ": Total variance of predictor: " + thisTreeTotalVariance[numTree];
			log.info(s);


			/*************************************************************
			 * Compute all the single marginals.
			 ************************************************************/
			
			//=== Define all the values to try for each of the dimensions.
			for (int dim = 0; dim < numDim; dim++) {
				int numVals = configSpace.getCategoricalSize()[dim];
				if(numVals > 0){
					//=== For categorical dimensions.
					allObservations[numTree][dim] = new double[numVals];
					allIntervalSizes[numTree][dim] = new double[numVals];
					for(int valIndex=0; valIndex<numVals; valIndex++){
						allObservations[numTree][dim][valIndex] = valIndex;
						allIntervalSizes[numTree][dim][valIndex] = 1.0/numVals;
					}
				} else {
					//=== For numerical dimensions, predict for each interval and remember size of interval.
					
					//=== Get split points for this dimension.
					List<Double> splitPoints = new ArrayList<Double>();
					for(int node_index=0; node_index<forest.Trees[numTree].var.length; node_index++){
						if(forest.Trees[numTree].var[node_index] == dim+1){
							splitPoints.add(forest.Trees[numTree].cut[node_index]);
						}
					}
					splitPoints.add(0.0);
					splitPoints.add(1.0);
					Collections.sort(splitPoints);
					
					//=== Set the observations to consider as the mid points between the split points.
					if( splitPoints.size() == 2 ){
						// The tree does not split on this dimension => dimension not important.
						allObservations[numTree][dim] = new double[0];
						allIntervalSizes[numTree][dim] = new double[0];
					} else {
						allObservations[numTree][dim] = new double[splitPoints.size()-1];
						allIntervalSizes[numTree][dim] = new double[splitPoints.size()-1];
						for(int lowerIntervalId=0; lowerIntervalId<splitPoints.size()-1; lowerIntervalId++){
							allObservations[numTree][dim][lowerIntervalId] = (splitPoints.get(lowerIntervalId) + splitPoints.get(lowerIntervalId+1))/2;
							allIntervalSizes[numTree][dim][lowerIntervalId] = splitPoints.get(lowerIntervalId+1) - splitPoints.get(lowerIntervalId);
						}
					}
				}
			}	
		}
	}
	
	public double getMarginal(int dim)
	{
		HashSet<Integer> set = new HashSet<Integer>();
		set.add(dim);

		if(totalFractionsExplained.containsKey(set))
			return totalFractionsExplained.get(set);
					
		for(int numTree=0; numTree<forest.Trees.length; numTree++){

			int[] indicesOfObservations = new int[1];
			//=== Compute marginal predictions for each instantiation of this categorical parameter.
			indicesOfObservations[0] = dim;
			ArrayList<Double> as = new ArrayList<Double>();
			double marg;
			
			double weightedSum=0, weightedSumOfSquares=0;
			for(int valIndex=0; valIndex<allObservations[numTree][dim].length; valIndex++){
				double[] observations = new double[1];
	//			observations[0] = k;
				observations[0] = allObservations[numTree][dim][valIndex];
				marg = forest.Trees[numTree].marginalPerformance(indicesOfObservations, observations);
				as.add(marg);
				double intervalSize = allIntervalSizes[numTree][dim][valIndex];
				weightedSum += marg*intervalSize;
				weightedSumOfSquares += marg*marg*intervalSize;
			}
	

			//=== Initialize local housekeeping variables for this tree. 

			double thisMarginalVarianceContribution = weightedSumOfSquares - weightedSum*weightedSum;
			//=== Compute and log fraction of total variance this explains.
			double thisMarginalFractionOfVarianceExplained = thisMarginalVarianceContribution/thisTreeTotalVariance[numTree]*100;
			if (Double.isNaN(thisMarginalFractionOfVarianceExplained)){
				throw new RuntimeException("ERROR - variance contributions is NaN.");
			}

			
			//=== Remember this marginal for the future.

			thisTreeVarianceContributions.put(set, thisMarginalVarianceContribution);
			
			singleVarianceContributions.get(numTree).put(dim, thisMarginalVarianceContribution);
			
			double previousFractionExplained = 0;
			if(numTree > 0){
				previousFractionExplained = totalFractionsExplained.get(set);
			}
			double thisFractionExplained = thisTreeVarianceContributions.get(set)/thisTreeTotalVariance[numTree]*100;
			totalFractionsExplained.put(set, previousFractionExplained + 1.0/forest.Trees.length * thisFractionExplained);
		}
		return totalFractionsExplained.get(set);
	}
	public double getPairwiseMarginal(int dim1, int dim2)
	{
		HashSet<Integer> set = new HashSet<Integer>();
		set.add(dim1);
		set.add(dim2);
		
		if(totalFractionsExplained.containsKey(set))
			return totalFractionsExplained.get(set);
		
		int indexOfFirstTree = 0;
		if(singleVarianceContributions.get(indexOfFirstTree).containsKey(dim1) == false)
			getMarginal(dim1);
		if(singleVarianceContributions.get(indexOfFirstTree).containsKey(dim2) == false)
			getMarginal(dim2);
		
		
		for(int numTree=0; numTree<forest.Trees.length; numTree++){

			int[] indicesOfObservations = new int[2];
			indicesOfObservations[0] = dim1;
			ArrayList<Double> as = new ArrayList<Double>();
			indicesOfObservations[1] = dim2;
			double weightedSum=0, weightedSumOfSquares=0;
			
			//=== Compute marginal predictions for each instantiation of these two categorical parameters.
			for(int valIndex1=0; valIndex1<allObservations[numTree][dim1].length; valIndex1++){
				for(int valIndex2=0; valIndex2<allObservations[numTree][dim2].length; valIndex2++){
					double[] observations = new double[2];
					observations[0] = allObservations[numTree][dim1][valIndex1];
					observations[1] = allObservations[numTree][dim2][valIndex2];
	
					double intervalSize1 = allIntervalSizes[numTree][dim1][valIndex1];
					double intervalSize2 = allIntervalSizes[numTree][dim2][valIndex2];
					double marg;

					marg = forest.Trees[numTree].marginalPerformance(indicesOfObservations, observations);
					weightedSum += marg*intervalSize1*intervalSize2;
					weightedSumOfSquares += marg*marg*intervalSize1*intervalSize2;
					

					as.add(marg);
				
				}
			}
	
			double thisBinaryVarianceContribution = weightedSumOfSquares - weightedSum*weightedSum;
	
			thisBinaryVarianceContribution -= singleVarianceContributions.get(numTree).get(dim1);
			thisBinaryVarianceContribution -= singleVarianceContributions.get(numTree).get(dim2);

			
			//=== Remember this marginal for the future.
			thisTreeVarianceContributions.put(set, thisBinaryVarianceContribution);
	
			double previousFractionExplained = 0;
			if(numTree > 0){
				previousFractionExplained = totalFractionsExplained.get(set);
			}
			double thisFractionExplained = thisTreeVarianceContributions.get(set)/thisTreeTotalVariance[numTree]*100;
			totalFractionsExplained.put(set, previousFractionExplained + 1.0/forest.Trees.length * thisFractionExplained);
			
		}
		return totalFractionsExplained.get(set);
	}
}
	