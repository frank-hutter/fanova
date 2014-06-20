package net.aclib.fanova.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import ca.ubc.cs.beta.aclib.configspace.ParamConfigurationSpace;
import ca.ubc.cs.beta.models.fastrf.RandomForest;
import ca.ubc.cs.beta.models.fastrf.Regtree;

public class RandomForestPreprocessor {
	public static void preprocessRandomForest(RandomForest forest, ParamConfigurationSpace configSpace){
		int[] categoricalSize = configSpace.getCategoricalSize(); 
		int dim = categoricalSize.length;
		
		//=== Determine categorical parameters.
		boolean[] isCat = new boolean[dim];
		for (int i=0; i<dim; i++){
			isCat[i] = (categoricalSize[i] != ParamConfigurationSpace.INVALID_CATEGORICAL_SIZE);
		}
		
		//=== Build array holdings categorical parameter domains (one set per categorical parameter).
		//=== Also build arrays of lower and upper bounds of continuous parameters.
		HashSet<Integer>[] catValues = new HashSet[dim];
		double[] contLB = new double[dim];
		double[] contUB = new double[dim];
		for(int i=0; i<dim; i++){
			if (isCat[i]){
				catValues[i] = new HashSet<Integer>();
				for(int j=0; j<categoricalSize[i]; j++){
					catValues[i].add(new Integer(j));
				}
			} else {
				contLB[i] = 0;
				contUB[i] = 1;
			}
		}
		
		Regtree[] trees = forest.Trees; 
		for(Regtree tree:trees){
			tree.precomputeLeafInfo(isCat, catValues, contLB, contUB);
		}
		
	}
	
	public static double computeTotalVarianceOfRegressionTree(Regtree regtree, ParamConfigurationSpace configSpace){
		HashSet<Integer> allVariableIndices = new HashSet<Integer>();
		for(int j=0; j<configSpace.getCategoricalSize().length; j++){
			allVariableIndices.add(new Integer(j));
		}
		double totalVariance = regtree.computeTotalVariance(null,null,allVariableIndices);
		System.out.println("Total variance of predictor (broken computation, only works in absence of instance features): " + totalVariance);
		return totalVariance;
	}
	
	public static HashMap<Integer,Double> getUpToKaryVarianceContributions(Regtree regtree, ParamConfigurationSpace configSpace, int k){
		if (k>4){
			throw new IllegalArgumentException("Computation of k-ary variance contributions only implemented for K up to 4.");
		}
		
		HashMap<Integer,Double> singleVarianceContributions = new HashMap<Integer,Double>();
		//=== Do another sample query.
		int[] indicesOfObservations = new int[1];
		indicesOfObservations[0] = 0;
		double[] observations = new double[1];
		observations[0] = 2;
			
		//=== Get all the single marginals.
		for(int j=0; j<configSpace.getCategoricalSize().length; j++){
			indicesOfObservations[0] = j;
			ArrayList<Double> as = new ArrayList<Double>();
			for(int l=0; l<configSpace.getCategoricalSize()[j]; l++){
				observations[0] = l;
				
				double marg = regtree.marginalPerformance(indicesOfObservations, observations);
				as.add(marg);
	//			System.out.println("Marginal for parameter " + configFile.getParameterNames().get(j) + " set to value " + l + ": " + marg);			
			}
			double avg = 0;
			for (Double entry :as) {
				avg += entry;
			}
			avg /= (as.size()+0.0);
			double varianceContribution = 0;
			for (Double entry :as) {
				varianceContribution += 1/(as.size()+0.0) * Math.pow(entry-avg,2);
			}
			singleVarianceContributions.put(j, varianceContribution);
//			totalVarianceExplained += varianceContribution;
//			System.out.println(decim.format(varianceContribution/totalVariance*100) + "% for contribution of parameter " + configFile.getParameterNames().get(j));
	//		System.out.println();
	//		System.out.println();
		}
		return singleVarianceContributions;
	}
}
