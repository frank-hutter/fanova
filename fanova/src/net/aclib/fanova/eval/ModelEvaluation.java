package net.aclib.fanova.eval;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.apache.commons.math.stat.descriptive.AbstractUnivariateStatistic;
import org.apache.commons.math.stat.descriptive.rank.Percentile;

import ca.ubc.cs.beta.aclib.algorithmrun.AlgorithmRun;
import ca.ubc.cs.beta.aclib.configspace.ParamConfiguration;
import ca.ubc.cs.beta.aclib.configspace.ParamConfigurationSpace;
import ca.ubc.cs.beta.aclib.probleminstance.ProblemInstance;
import ca.ubc.cs.beta.models.fastrf.RandomForest;
import ca.ubc.cs.beta.models.fastrf.RegtreeBuildParams;


public class ModelEvaluation {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		//evaluateModel("/ubc/cs/home/h/hutter/orcinus/home/hutter/experiments/surrogates/smacData/v2.04.00-master-393_AAAI_CPLEX12-RCW2-4day-10000-discrete-adaptiveCappingtrue_surrogatezip_1000.zip","/ubc/cs/home/h/hutter/orcinus/home/hutter/experiments/surrogates/smacData/v2.04.00-master-393_AAAI_CPLEX12-RCW2-4day-10000-discrete-adaptiveCappingtrue_surrogatezip_1000.zip");
		
//		extractMarginalForest(new QuickZip("/ubc/cs/home/h/hutter/orcinus/home/hutter/experiments/surrogates/otherData/AAAI_Sparrowfixed-7SAT-60-discrete-nofeat_fullmatrix_testinst_surrogatezip_700000_-splitMin1-fullTreeBootstrapfalse-logModeltrue.zip"));
		//extractMarginalForest(new QuickZip("/ubc/cs/home/h/hutter/orcinus/home/hutter/experiments/surrogates/jan28_old_otherData/AAAI_Sparrowfixed-7SAT-60-discrete-nofeat_fullmatrix_testinst_surrogatezip_700000_-splitMin1-fullTreeBootstrapfalse-logModeltrue.zip"), true, 0.10);
	}


	
	

	public static RandomForest extractMarginalForest(RandomForest forest, List<AlgorithmRun> testRuns, ParamConfigurationSpace configFile, Random rand,  boolean compareToDefault, double quantileToCompareTo){
//	public static RandomForest extractMarginalForest(String surrogateZipFilename){
//		QuickZip surrogateZip = new QuickZip(surrogateZipFilename);
		System.out.println("Extracing marginal forest from surrogateZip file.");
		//RandomForest forest = (RandomForest) surrogateZip.getObject(ZipFileName.RANDOM_FOREST);
		//surrogateZip.close();
		//List<AlgorithmRun> testRuns = (List<AlgorithmRun>) surrogateZip.getObject(ZipFileName.RUN_OBJECTS);

		/* Get all features, X, in double[][] format. */
		Set<ProblemInstance> uniqInstances = new HashSet<ProblemInstance>();
		for(AlgorithmRun run : testRuns){
			uniqInstances.add( run.getRunConfig().getProblemInstanceSeedPair().getInstance() );
			//System.out.println(run.getRuntime());
		}
		double[][] X = new double[uniqInstances.size()][];
		int i=0;
		for(ProblemInstance pi: uniqInstances){
			double[] featureArray = pi.getFeaturesDouble();
			X[i] = new double[featureArray.length];
			System.arraycopy(featureArray, 0, X[i], 0, featureArray.length);
			i++;
		}

		/* Get all unique configurations, uniqTheta, in double[][] format. */
		int numParams = -1;
		Set<ParamConfiguration> uniqConfigurations = new HashSet<ParamConfiguration>();
		for(AlgorithmRun run : testRuns){
			uniqConfigurations.add( run.getRunConfig().getParamConfiguration() );
		}
		double[][] uniqTheta = new double[uniqConfigurations.size()][];
		i=0;
		for(ParamConfiguration config: uniqConfigurations){
			double[] valueArray = config.toValueArray();
			numParams = valueArray.length;
			uniqTheta[i] = valueArray;
			//System.arraycopy(valueArray, 0, uniqTheta[i], 0, valueArray.length);
			i++;
		}

		/************************************************************** 
		 * For each tree in the forest, predict marginals for uniqTheta, 
		 * and then fit a new tree on the marginals. 
		 **************************************************************/
		RandomForest preparedForest = RandomForest.preprocessForest(forest, X);
        RegtreeBuildParams regtreeBuildParamsNoFeatures = RegtreeBuildParams.copy(forest.getBuildParams(), numParams);
       
//        ParamConfigurationSpace configFile = (ParamConfigurationSpace) surrogateZip.getObject(ZipFileName.CONFIG_SPACE_FILE);
        Random r = new Random();
        r.setSeed(1234); // for deterministic sampling of random configurations to compute the quantile
		double[][] defaultThetaAsArray = new double[1][];
		defaultThetaAsArray[0] = configFile.getDefaultConfiguration().toValueArray();
        
        double[][] newX = new double[1][0];
        
        int[][] theta_inst_idxs = new int[uniqTheta.length][2];
        for(int k = 0; k < uniqTheta.length; k++)
        {
        	theta_inst_idxs[k][0] = k;
        	theta_inst_idxs[k][1] = 0;	
        }
        
        //=== Get 10,000 random param configs, using fixed seed.
        int numRandomConfigs = 1000; // was:10000
        ParamConfiguration[] randConfigs = new ParamConfiguration[numRandomConfigs];
        for(int num=0; num<numRandomConfigs; num++){
        	randConfigs[num] = configFile.getRandomConfiguration(r);	
        }

        //=== Compute quantile marginal prediction over the random configs.
        int[] treesToUse = new int[preparedForest.numTrees];
        for(int numTree = 0; numTree<preparedForest.numTrees; numTree++){
        	treesToUse[numTree] = numTree;
        }        		
    	double[][] forestMarginalPredictions = RandomForest.applyMarginal(preparedForest, treesToUse, uniqTheta);
    	double[] forestMarginalPredMean = new double[forestMarginalPredictions.length];
    	for(int k = 0; k < forestMarginalPredMean.length; k++) {
    		forestMarginalPredMean[k] = forestMarginalPredictions[k][0];
//    		System.out.println(forestMarginalPredMean[k]);
		}
    	
    	double quantileToBeat=0;
    	if (!compareToDefault && (quantileToCompareTo > 0)) {
	    	Percentile p = new Percentile();
	    	p.setData(forestMarginalPredMean);
	    	quantileToBeat = p.evaluate(quantileToCompareTo * 100);
			System.out.println("Quantile to beat: " + quantileToBeat);
    	}

    	//=== Prepare the y values for the marginal forest and construct it. 
        RandomForest franksForest = new RandomForest(preparedForest.numTrees, regtreeBuildParamsNoFeatures);
        for( int b = 0; b < preparedForest.numTrees; b++ )
        {
        	int[] treeToUse = { b}; 
        	double[][] predictions = RandomForest.applyMarginal(preparedForest, treeToUse, uniqTheta);
            double[][] defaultPerformanceArray = RandomForest.applyMarginal(preparedForest, treeToUse, defaultThetaAsArray);
            double defaultPerformance = defaultPerformanceArray[0][0];
                	
        	double[] actualMarginalPred = new double[predictions.length];
        	for(int k = 0; k < predictions.length; k++)
        	{
//                	regtreeBuildParamsNoFeatures.logModel = 0;
 //       			//yMarginalPred[k] = Math.log10(predictions[k][0]);
            	actualMarginalPred[k] = Math.pow(predictions[k][0], 10);
    		}
        	
        	double[] myMarginalPred = new double[predictions.length];
        	for(int k = 0; k < predictions.length; k++)
        	{
        		if (compareToDefault){
	    			myMarginalPred[k] = Math.min(defaultPerformance, predictions[k][0]);	    			
        		} else {
        			if (quantileToCompareTo > 0) {
        				myMarginalPred[k] = Math.min(quantileToBeat, predictions[k][0]);
   		    		} else {
		    			myMarginalPred[k] = predictions[k][0];
		    		}
    			}
//        		System.out.println(myMarginalPred[k]);
        	}
        	RandomForest marginalForest = RandomForest.learnModel(1, uniqTheta, newX, theta_inst_idxs, myMarginalPred, regtreeBuildParamsNoFeatures);
        	franksForest.Trees[b] = marginalForest.Trees[0];
        }
        
    	franksForest = RandomForest.preprocessForest(franksForest, newX);
    	return franksForest;
    	
        //RandomForest marginalForest = new RandomForest(forest.numTrees, regtreeBuildParamsNoFeatures);

        
        
        
        /*
        double[][] newX = new double[1][0];
		//int b=0;
		for(int b=0; b<preparedForest.numTrees; b++){
			// Predict marginals.
			Object[] result = RegtreeFwd.marginalFwd(preparedForest.Trees[b], uniqTheta, null);
            double[] marginalTreePreds = (double[])result[0];
//            double[] marginalTreeVars = (double[])result[1];

            for(int j=0; j<marginalTreePreds.length; j++){
            	System.out.println(((float)marginalTreePreds[j]) + ",...");
            }

            
            if (forest.logModel>0) {
            	for(int j=0; j<marginalTreePreds.length; j++){
                	marginalTreePreds[j] = Math.log10(marginalTreePreds[j]);
                }
            }
            
            // Build tree with marginals.
            marginalForest.Trees[b] = RegtreeFit.fit(uniqTheta, newX, marginalTreePreds, regtreeBuildParamsNoFeatures);
		}
		RandomForest preprocessedMarginalForest = RandomForest.preprocessForest(marginalForest, newX); 
		return preprocessedMarginalForest;
         */
        
        
    }
					
	
	/*public static Object[] getVarianceContributionsAndMarginals(String surrogateZipFilename, boolean compareToDefault, double quantileToCompareTo){
		QuickZip surrogateZip = new QuickZip(surrogateZipFilename);
		ParamConfigurationSpace configFile = (ParamConfigurationSpace) surrogateZip.getObject(ZipFileName.CONFIG_SPACE_FILE);
		
		RandomForest forest = ModelEvaluation.extractMarginalForest(surrogateZip, compareToDefault, quantileToCompareTo);
		RandomForestPreprocessor.preprocessRandomForest(forest, configFile);

		HashSet<Integer> allVariableIndices = new HashSet<Integer>();
		for(int j=0; j<configFile.getCategoricalSize().length; j++){
			allVariableIndices.add(new Integer(j));
		}

		int i=0;
		double totalVariance = RandomForestPreprocessor.computeTotalVarianceOfRegressionTree(forest.Trees[i], configFile);
		System.out.println("Total variance of predictor (broken computation, only works in absence of instance features): " + totalVariance);

		DecimalFormat decim = new DecimalFormat("#.##");

		HashMap<String,Double> singleVarianceFractions = new HashMap<String,Double>();
		HashMap<String,HashMap<Integer,Double>> singleMarginals = new HashMap<String,HashMap<Integer,Double>>();

		int[] indicesOfObservations = new int[1];
		double[] observations = new double[1];
		double fractionOfTotalVarianceExplained = 0;
		double marg;

		long start = System.nanoTime();
		
		//=== Get all the single marginals.
		for(int j=0; j<configFile.getCategoricalSize().length; j++){
			String parameterName_j = configFile.getParameterNames().get(j);
			indicesOfObservations[0] = j;
			ArrayList<Double> as = new ArrayList<Double>();
			for(int k=0; k<configFile.getCategoricalSize()[j]; k++){
				observations[0] = k;
				
				marg = forest.Trees[i].marginalPerformance(indicesOfObservations, observations);
				if(!singleMarginals.containsKey(parameterName_j)){
					singleMarginals.put(parameterName_j, new HashMap<Integer, Double>());
				}
				singleMarginals.get(parameterName_j).put(k, marg);
				as.add(marg);
		//		System.out.println("Marginal for parameter " + configFile.getParameterNames().get(j) + " set to value " + k + ": " + marg);			
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
			singleVarianceFractions.put(parameterName_j, varianceContribution/totalVariance);
			fractionOfTotalVarianceExplained += varianceContribution/totalVariance;
			System.out.println(decim.format(varianceContribution/totalVariance*100) + "% for contribution of parameter " + parameterName_j);
		}

		double fractionOfTotalVarianceExplainedByMainEffects = fractionOfTotalVarianceExplained;
		double timeForComputingMainEffects = (System.nanoTime() - start) * 1.0e-9;
		System.out.println("Fraction of variance explained by main effects: " + 100*fractionOfTotalVarianceExplainedByMainEffects + "%. Took " + timeForComputingMainEffects + " seconds.");

		Object[] result = new Object[4];
		result[0] = singleVarianceFractions;
		result[1] = singleMarginals;
		result[2] = configFile;
		result[3] = timeForComputingMainEffects;
//		return result;

		
		 * SOMETHING BROKEN HERE - use the one in FunctionalANOVATester instead.
//		HashMap<String,HashMap<String, Double>> binaryInteractions = new HashMap<String,HashMap<Integer,Double>>();
		//=== Get all binary average effects.
		indicesOfObservations = new int[2];
		observations = new double[2];
		for(int j=0; j<configFile.getCategoricalSize().length; j++){
			String parameterName_j = configFile.getParameterNames().get(j);
			indicesOfObservations[0] = j;
			for(int j2=j+1; j2<configFile.getCategoricalSize().length; j2++){
				String parameterName_j2 = configFile.getParameterNames().get(j2);
				ArrayList<Double> as = new ArrayList<Double>();
				indicesOfObservations[1] = j2;
				for(int k=0; k<configFile.getCategoricalSize()[j]; k++){
					observations[0] = k;
					for(int k2=0; k2<configFile.getCategoricalSize()[j2]; k2++){
						observations[1] = k2;
				
						marg = forest.Trees[i].marginalPerformance(indicesOfObservations, observations);
						as.add(marg);
		//					System.out.println("Marginal for parameters " + configFile.getParameterNames().get(j) + "&" + configFile.getParameterNames().get(j2) + " set to values " + k + "&" + k2 + ": " + marg);			
					}
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
				varianceContribution -= (singleVarianceFractions.get(parameterName_j)*totalVariance);
				varianceContribution -= (singleVarianceFractions.get(parameterName_j)*totalVariance);
				fractionOfTotalVarianceExplained += varianceContribution/totalVariance;
				
		//		System.out.println("avg = " + avg + "; Variance contribution of parameters " + configFile.getParameterNames().get(j) + "&" + configFile.getParameterNames().get(j2) + " = " + varianceContribution + ", or " + decim.format(varianceContribution/totalVariance*100) + "%");
				System.out.println(decim.format(varianceContribution/totalVariance*100) + "% for contribution of parameters " + configFile.getParameterNames().get(j) + " & " + configFile.getParameterNames().get(j2));
		//		System.out.println("avg = " + avg + "; Variance contribution of parameters " + configFile.getParameterNames().get(j) + "&" + configFile.getParameterNames().get(j2) + " = " + varianceContribution + ", or " + decim.format(varianceContribution/totalVariance*100) + "%");
		//		System.out.println();
		//		System.out.println();
				
			}
		}
		double timeForComputingBinaryEffects = (System.nanoTime() - start) * 1.0e-9 - timeForComputingMainEffects;
		double fractionOfTotalVarianceExplainedByBinaryInteractionEffects = fractionOfTotalVarianceExplained - fractionOfTotalVarianceExplainedByMainEffects;
		System.out.println("Fraction of variance explained by main effects: " + 100 * fractionOfTotalVarianceExplainedByMainEffects+ "%. Took " + timeForComputingMainEffects + " seconds.");
		System.out.println("Fraction of variance explained by binary interaction effects: " + 100 * fractionOfTotalVarianceExplainedByBinaryInteractionEffects + "%. Took " + timeForComputingBinaryEffects + " seconds.");

		result[4] = fractionOfTotalVarianceExplainedByBinaryInteractionEffects;
		
		return result;
	}*/
	
//	public static int countDataPoints(String surrogateZipFile)
//	{
//		QuickZip surrogateZip = new QuickZip(surrogateZipFile);		
//		List<AlgorithmRun> trainRuns = (List<AlgorithmRun>) surrogateZip.getObject(ZipFileName.RUN_OBJECTS);
//		return trainRuns.size();
//	}
//
//	public static int countConfigurations(String surrogateZipFile)
//	{
//		QuickZip surrogateZip = new QuickZip(surrogateZipFile);		
//		List<AlgorithmRun> trainRuns = (List<AlgorithmRun>) surrogateZip.getObject(ZipFileName.RUN_OBJECTS);
//		HashSet<ParamConfiguration> configs = new HashSet<ParamConfiguration>();
//		for(AlgorithmRun run : trainRuns)
//		{
//			ParamConfiguration config = run.getRunConfig().getParamConfiguration();
//			configs.add(config);
//		}
//		return configs.size();
//	}
//
//	
//	public static double[][] evaluateModel(RandomForest forest, List<AlgorithmRun> testRuns)
//	{
//		System.out.println("Loading surrogateZipFile " + surrogateZipFile);
//		QuickZip surrogateZip = new QuickZip(surrogateZipFile);		
//		
//		RandomForest model = (RandomForest) surrogateZip.getObject(ZipFileName.RANDOM_FOREST);
//
//		System.out.println("Loading runZipFile " + runZipFile);
//		QuickZip runZip = new QuickZip(runZipFile);
//		
//		System.out.println("Getting runs ... ");
//		List<AlgorithmRun> testRuns = (List<AlgorithmRun>) runZip.getObject(ZipFileName.RUN_OBJECTS);
//		testRuns = new ArrayList<AlgorithmRun>(testRuns);
//		/*
//		Collections.sort(testRuns,  new Comparator<AlgorithmRun>(){
//
//			@Override
//			public int compare(AlgorithmRun o1, AlgorithmRun o2) {
//				// TODO Auto-generated method stub
////				int diff =  o1.getRunConfig().getProblemInstanceSeedPair().getInstance().getInstanceName().compareTo( o2.getRunConfig().getProblemInstanceSeedPair().getInstance().getInstanceName());
////				int diff = o2.getRunConfig().getParamConfiguration().getFormattedParamString().compareTo(o2.getRunConfig().getParamConfiguration().getFormattedParamString());
//				int diff = o2.getRunConfig().getParamConfiguration().hashCode() - o2.getRunConfig().getParamConfiguration().hashCode();
//				if (diff != 0) 
//				{
//					return diff;
//				} else
//				{
//					return o1.getRunConfig().getProblemInstanceSeedPair().getInstance().getInstanceName().compareTo( o2.getRunConfig().getProblemInstanceSeedPair().getInstance().getInstanceName());
//				}
//				
//			}
//			
//		});
//		*/
//		
//		
//		double[][] allX = new double[testRuns.size()][];
//		
//		System.out.println("Getting double[][] arrays ... ");
//		int i=0;
//		double[] y = new double[testRuns.size()];
//		for(AlgorithmRun run : testRuns)
//		{
//			ProblemInstance pi = run.getRunConfig().getProblemInstanceSeedPair().getInstance();
//			ParamConfiguration config = run.getRunConfig().getParamConfiguration();
//			
//			double[] valueArray = config.toValueArray();
//			double[] featureArray = pi.getFeaturesDouble();
//			allX[i]=new double[valueArray.length + featureArray.length];
//			
//			System.arraycopy(valueArray, 0,allX[i], 0, valueArray.length);
//			System.arraycopy(featureArray, 0,allX[i],valueArray.length, featureArray.length);
//			
//			y[i] = RunObjective.RUNTIME.getObjective(run);
//			//y[i] = run.getRuntime();
//			i++;
//			if(i % 10000==0){
//				System.out.println(i);				
//			}
//		}
//		
//		System.out.println("Applying ... ");
//		double[][] results =RandomForest.apply(model, allX);
//		
//		System.out.println("Building results ... ");
//		double[][] allResults = new double[results.length][4];
//		for(i=0; i < results.length; i++)	
//		{
//			allResults[i][0] = results[i][0];
//			allResults[i][1] = results[i][1];
//			allResults[i][2] = y[i];
//			allResults[i][3] = (testRuns.get(i).getRunConfig().hasCutoffLessThanMax() && testRuns.get(i).getRunResult().equals(RunResult.TIMEOUT)) ? 1 : 0;
//			if(i % 10000==0){
//				System.out.println(i);				
//			}
//		}		
//		return allResults;
//	}
//	
//	
	
}	


	


	
/*	public static double[][] computeMarginals(String surrogateZipFile, String runZipFile)
	{
		QuickZip surrogateZip = new QuickZip(surrogateZipFile);
		RandomForest forest = (RandomForest) surrogateZip.getObject(ZipFileName.RANDOM_FOREST);
		forest.preprocessForest(forest, X)
		surrogateZip.close();
		QuickZip runZip = new QuickZip(runZipFile);
		
		List<AlgorithmRun> testRuns = (List<AlgorithmRun>) runZip.getObject(ZipFileName.RUN_OBJECTS);
		double[][] allX = new double[testRuns.size()][];
		
		int i=0;
		double[] y = new double[testRuns.size()];
		for(AlgorithmRun run : testRuns)
		{
			ParamConfiguration config = run.getRunConfig().getParamConfiguration();
			
			double[] valueArray = config.toValueArray();
			allX[i]=new double[valueArray.length];
			System.arraycopy(valueArray, 0,allX[i], 0, valueArray.length);
			
			y[i] = RunObjective.RUNTIME.getObjective(run);
			i++;
		}
		
		int[] tree_idxs = {0,1,2,3,4,5,6,7,8,9};
		double[][] results = RandomForest.applyMarginal(forest, tree_idxs, allX);
		
		double[][] allResults = new double[results.length][3];
		for(i=0; i < results.length; i++)	
		{
			allResults[i][0] = results[i][0];
			allResults[i][1] = results[i][1];
			allResults[i][2] = y[i];

		}		
		return allResults;
	}
*/