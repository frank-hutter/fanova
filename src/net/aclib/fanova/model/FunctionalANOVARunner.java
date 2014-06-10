package net.aclib.fanova.model;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Vector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.aclib.fanova.eval.ModelEvaluation;
import ca.ubc.cs.beta.aclib.algorithmrun.AlgorithmRun;
import ca.ubc.cs.beta.aclib.configspace.NormalizedRange;
import ca.ubc.cs.beta.aclib.configspace.ParamConfiguration;
import ca.ubc.cs.beta.aclib.configspace.ParamConfigurationSpace;
import ca.ubc.cs.beta.models.fastrf.RandomForest;

// Sample input:   --restore-scenario /ubc/cs/home/h/hutter/orcinus/home/hutter/clasp_data/fanova-clasp/smac-output/RunGroup-2013-06-20--11-13-55-400/state-run4 --num-run 1 --algo-exec dummy --algo-exec-dir . --cutoff_time 120
// Sample input:   --restore-scenario /ubc/cs/home/h/hutter/orcinus/home/hutter/cssc/smac-output/CSSC-BMC08-300s-2day_riss3gExt_riss3gExt-params/state-run0 --num-run 1 --algo-exec dummy
public class FunctionalANOVARunner {

	private static final Logger log = LoggerFactory.getLogger(FunctionalANOVARunner.class);

	public static ArrayList<Map.Entry<HashSet, Double>> sortedKeysByValue(HashMap<HashSet<Integer>, Double> varianceContributions){
		//Transfer as List and sort it
		ArrayList<Map.Entry<HashSet, Double>> l = new ArrayList(varianceContributions.entrySet());
		Collections.sort(l, new Comparator<Map.Entry<HashSet, Double>>(){
			public int compare(Map.Entry<HashSet, Double> o1, Map.Entry<HashSet, Double> o2) {
				return o2.getValue().compareTo(o1.getValue());
			}
		});
		return l;
	}

	public static void decomposeVariance(RandomForest existingForest, List<AlgorithmRun> testRuns,ParamConfigurationSpace configSpace,Random rand, boolean compareToDef, double quantileToCompareTo, boolean computePairwiseInteraction, String outputDir, boolean logModel, boolean plotMarginals) throws IOException, InterruptedException {
		//=== Extract and preprocess forest.
		RandomForest forest = ModelEvaluation.extractMarginalForest(existingForest, testRuns, configSpace, rand, compareToDef, quantileToCompareTo);
		RandomForestPreprocessor.preprocessRandomForest(forest, configSpace);

		DecimalFormat decim = new DecimalFormat("#");
		DecimalFormat decim2 = new DecimalFormat("##.##");

		//=== Initialize variables to be incrementally updated.
		String s;
		double timeForComputingMainEffects = 0;
		double timeForComputingBinaryEffects = 0;
		HashMap<HashSet<Integer>,Double> totalFractionsExplained = new HashMap<HashSet<Integer>,Double>();
		
		//marginals[parameterList] -> [ instantiation of parameters -> double  ]
		//HashMap<int[], HashMap<double[],ArrayList<Double>>> marginals = new HashMap<int[], HashMap<double[],ArrayList<Double>>>();
		//singleMarginals[parameter] -> [ instantiation -> double  ]

// We don't need these after all...		
//		HashMap<Integer, HashMap<Integer, ArrayList<Double>>> singleMarginals = new HashMap<Integer, HashMap<Integer, ArrayList<Double>>>();
//		HashMap<Integer, HashMap<Integer, HashMap<Integer, HashMap<Integer, ArrayList<Double>>>>> doubleMarginals = new HashMap<Integer, HashMap<Integer, HashMap<Integer, HashMap<Integer, ArrayList<Double>>>>>();
				
		
		double sumOfFractionsOfBinaries = 0;
		int numDim = configSpace.getCategoricalSize().length;
		double[][] allObservations = new double[numDim][];
		double[][] allIntervalSizes = new double[numDim][];
		
		//=== Loop over trees.
		for(int numTree=0; numTree<forest.Trees.length; numTree++){
			HashSet<Integer> allVariableIndices = new HashSet<Integer>();
			for(int j=0; j<configSpace.getCategoricalSize().length; j++){
				allVariableIndices.add(new Integer(j));
			}

			//=== Get the tree's total variance (only works for marginal trees, i.e., in the absence of instance features).
			double thisTreeTotalVariance = RandomForestPreprocessor.computeTotalVarianceOfRegressionTree(forest.Trees[numTree], configSpace);
			if (thisTreeTotalVariance == 0.0){
				s = "Tree " + numTree + " has no variance -> skipping.";
				log.info(s);
				continue;
			}
			s = "Tree " + numTree + ": Total variance of predictor: " + thisTreeTotalVariance;
			log.info(s);

			//=== Initialize local housekeeping variables for this tree. 
			double thisTreeFractionOfVarianceExplainedByMarginals = 0;
			HashMap<HashSet<Integer>,Double> thisTreeVarianceContributions = new HashMap<HashSet<Integer>,Double>();
			HashMap<Integer,Double> singleVarianceContributions = new HashMap<Integer,Double>();  

			/*************************************************************
			 * Compute all the single marginals.
			 ************************************************************/
			double marg;
			long start = System.nanoTime(); 

			//=== Define all the values to try for each of the dimensions.
			for (int dim = 0; dim < numDim; dim++) {
				int numVals = configSpace.getCategoricalSize()[dim];
				if(numVals > 0){
					//=== For categorical dimensions.
					allObservations[dim] = new double[numVals];
					allIntervalSizes[dim] = new double[numVals];
					for(int valIndex=0; valIndex<numVals; valIndex++){
						allObservations[dim][valIndex] = valIndex;
						allIntervalSizes[dim][valIndex] = 1.0/numVals;
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
//					System.out.println(splitPoints);
//					System.exit(-1);
					
					//=== Set the observations to consider as the mid points between the split points.
					if( splitPoints.size() == 2 ){
						// The tree does not split on this dimension => dimension not important.
						allObservations[dim] = new double[0];
						allIntervalSizes[dim] = new double[0];
					} else {
						allObservations[dim] = new double[splitPoints.size()-1];
						allIntervalSizes[dim] = new double[splitPoints.size()-1];
						for(int lowerIntervalId=0; lowerIntervalId<splitPoints.size()-1; lowerIntervalId++){
							allObservations[dim][lowerIntervalId] = (splitPoints.get(lowerIntervalId) + splitPoints.get(lowerIntervalId+1))/2;
							allIntervalSizes[dim][lowerIntervalId] = splitPoints.get(lowerIntervalId+1) - splitPoints.get(lowerIntervalId);
//							System.out.print(allObservations[dim][lowerIntervalId] + " ");
//							System.out.println(allIntervalSizes[dim][lowerIntervalId] + "             ");
						}
					}
//					System.out.println();
//					System.exit(-1);
					
//					int numDiscretizedVals = 5;
//					allObservations[dim] = new double[numDiscretizedVals];
//					for(int valIndex=0; valIndex<numDiscretizedVals; valIndex++){
//						allObservations[dim][valIndex] = valIndex/(numDiscretizedVals-1+0.0);
//					}
				}
			}	
			
			//=== Loop over parameters to get the marginal for.
			for(int dim=0; dim<numDim; dim++){
				int[] indicesOfObservations = new int[1];
//				System.out.println(dim);
/*				if (configSpace.getCategoricalSize()[j] == 0){
					if(!haveWarnedAboutContinuousAlready){
						s = "WARNING: This version of fANOVA does not compute effects involving numerical parameters!";
						log.warn(s);

						haveWarnedAboutContinuousAlready = true;
					}
					continue;
				}
*/
				//=== Compute marginal predictions for each instantiation of this categorical parameter.
				indicesOfObservations[0] = dim;
				ArrayList<Double> as = new ArrayList<Double>();
				
				double weightedSum=0, weightedSumOfSquares=0;
				for(int valIndex=0; valIndex<allObservations[dim].length; valIndex++){
					double[] observations = new double[1];
//					observations[0] = k;
					observations[0] = allObservations[dim][valIndex];
					marg = forest.Trees[numTree].marginalPerformance(indicesOfObservations, observations);
					as.add(marg);

					double intervalSize = allIntervalSizes[dim][valIndex];
					weightedSum += marg*intervalSize;
					weightedSumOfSquares += marg*marg*intervalSize;
//									System.out.println("Marginal for parameter " + configSpace.getParameterNames().get(dim) + " set to value " + observations[0] + ": " + marg);			
					
/*
 * singleMarginals will have different indices in each tree, so don't use them.
					//=== Add marg to the list of saved marginals.
					if (!singleMarginals.containsKey(dim)){
						singleMarginals.put(dim, new HashMap<Integer, ArrayList<Double>>());
					}
					if (!singleMarginals.get(dim).containsKey(valIndex)){
						singleMarginals.get(dim).put(valIndex, new ArrayList<Double>());
					}
					singleMarginals.get(dim).get(valIndex).add(marg);
*/
				}

/*				//=== Compute average across instantiations, and marginal variance contribution.
				double avg = 0;
				for (Double entry :as) {
					avg += entry;
				}
				avg /= (as.size()+0.0);
				double thisMarginalVarianceContribution = 0;
				for (Double entry :as) {
					thisMarginalVarianceContribution += 1/(as.size()+0.0) * Math.pow(entry-avg,2);
				}
*/
				double thisMarginalVarianceContribution = weightedSumOfSquares - weightedSum*weightedSum;
				
				//=== Compute and log fraction of total variance this explains.
				double thisMarginalFractionOfVarianceExplained = thisMarginalVarianceContribution/thisTreeTotalVariance*100;
				if (Double.isNaN(thisMarginalFractionOfVarianceExplained)){
					throw new RuntimeException("ERROR - variance contributions is NaN.");
				}
				thisTreeFractionOfVarianceExplainedByMarginals += thisMarginalFractionOfVarianceExplained;
				s = "Tree " + numTree + ": " + decim.format(thisMarginalFractionOfVarianceExplained) + "% of variance explained by parameter " + configSpace.getParameterNames().get(dim);
				log.info(s);

				//=== Remember this marginal for the future.
				HashSet<Integer> set = new HashSet<Integer>();
				set.add(dim);
				thisTreeVarianceContributions.put(set, thisMarginalVarianceContribution);
				singleVarianceContributions.put(dim, thisMarginalVarianceContribution);
			}

			//=== Output stats about single marginals.
			double thisTreeTimeForComputingMainEffects = (System.nanoTime() - start) * 1.0e-9;
			timeForComputingMainEffects += thisTreeTimeForComputingMainEffects;
			s = "\nTree " + numTree + ": " + "Fraction of variance explained by main effects in this tree: " + thisTreeFractionOfVarianceExplainedByMarginals + "%. Took a total of " + thisTreeTimeForComputingMainEffects + " seconds.";
			log.info(s);


			/*************************************************************
			 * Compute all the binary marginals.
			 ************************************************************/
			double thisTreeFractionOfVarianceExplainedByBinaries = 0;
			double thisTreeTimeForComputingBinaryEffects = 0;

			if (computePairwiseInteraction)	{
				for(int dim1=0; dim1<numDim; dim1++){
//					if (configSpace.getCategoricalSize()[dim1] == 0) continue;
					for(int dim2=dim1+1; dim2<numDim; dim2++){
//						if (configSpace.getCategoricalSize()[dim2] == 0) continue;
						int[] indicesOfObservations = new int[2];
						indicesOfObservations[0] = dim1;
						ArrayList<Double> as = new ArrayList<Double>();
						indicesOfObservations[1] = dim2;
						double weightedSum=0, weightedSumOfSquares=0;
						
						//=== Compute marginal predictions for each instantiation of these two categorical parameters.
						for(int valIndex1=0; valIndex1<allObservations[dim1].length; valIndex1++){
							for(int valIndex2=0; valIndex2<allObservations[dim2].length; valIndex2++){
								double[] observations = new double[2];
								observations[0] = allObservations[dim1][valIndex1];
								observations[1] = allObservations[dim2][valIndex2];

								double intervalSize1 = allIntervalSizes[dim1][valIndex1];
								double intervalSize2 = allIntervalSizes[dim2][valIndex2];

								marg = forest.Trees[numTree].marginalPerformance(indicesOfObservations, observations);
								weightedSum += marg*intervalSize1*intervalSize2;
								weightedSumOfSquares += marg*marg*intervalSize1*intervalSize2;
								s = "Marginal for parameter " + configSpace.getParameterNames().get(dim1) + " set to value " + observations[0] + " and parameter " + configSpace.getParameterNames().get(dim2) + " set to value " + observations[1] + ": " + marg;
//								log.debug(s);
								as.add(marg);
								//							System.out.println("Marginal for parameters " + configSpace.getParameterNames().get(j) + "&" + configSpace.getParameterNames().get(j2) + " set to values " + k + "&" + k2 + ": " + marg);			

/* for continuous dims, doubleMarginals will be indexed differently in each tree, so don't use it.								
								//=== Add marg to the list of saved marginals.
								if (!doubleMarginals.containsKey(dim1)){
									doubleMarginals.put(dim1, new HashMap<Integer, HashMap<Integer, HashMap<Integer, ArrayList<Double>>>>());
								}
								if (!doubleMarginals.get(dim1).containsKey(dim2)){
									doubleMarginals.get(dim1).put(dim2, new HashMap<Integer, HashMap<Integer, ArrayList<Double>>>());
								}
								if (!doubleMarginals.get(dim1).get(dim2).containsKey(valIndex1)){
									doubleMarginals.get(dim1).get(dim2).put(valIndex1, new HashMap<Integer, ArrayList<Double>>());
								}
								if (!doubleMarginals.get(dim1).get(dim2).get(valIndex1).containsKey(valIndex2)){
									doubleMarginals.get(dim1).get(dim2).get(valIndex1).put(valIndex2, new ArrayList<Double>());
								}
								doubleMarginals.get(dim1).get(dim2).get(valIndex1).get(valIndex2).add(marg);
*/							
							}
						}

/*
						//=== Compute average across instantiations, and marginal variance contribution.
						double avg = 0;
						for (Double entry :as) {
							avg += entry;
						}
						avg /= (as.size()+0.0);
						double thisBinaryVarianceContribution = 0;
						for (Double entry :as) {
							thisBinaryVarianceContribution += 1/(as.size()+0.0) * Math.pow(entry-avg,2);
						}
*/
						double thisBinaryVarianceContribution = weightedSumOfSquares - weightedSum*weightedSum;

						thisBinaryVarianceContribution -= singleVarianceContributions.get(dim1);
						thisBinaryVarianceContribution -= singleVarianceContributions.get(dim2);

						//=== Compute and log fraction of total variance this explains.
						double thisBinaryFractionOfVarianceExplained = thisBinaryVarianceContribution/thisTreeTotalVariance*100;
						thisTreeFractionOfVarianceExplainedByBinaries += thisBinaryFractionOfVarianceExplained;
						s = decim.format(thisBinaryFractionOfVarianceExplained) + "% for contribution of parameters " + configSpace.getParameterNames().get(dim1) + " & " + configSpace.getParameterNames().get(dim2);
						log.debug(s);

						//=== Remember this marginal for the future.
						HashSet<Integer> set = new HashSet<Integer>();
						set.add(dim1);
						set.add(dim2);
						thisTreeVarianceContributions.put(set, thisBinaryVarianceContribution);

						//				System.out.println("avg = " + avg + "; Variance contribution of parameters " + configSpace.getParameterNames().get(j) + "&" + configSpace.getParameterNames().get(j2) + " = " + varianceContribution + ", or " + decim.format(varianceContribution/totalVariance*100) + "%");
						//				System.out.println();
						//				System.out.println();

					}
				}

				thisTreeTimeForComputingBinaryEffects = (System.nanoTime() - start) * 1.0e-9 - thisTreeTimeForComputingMainEffects;
				timeForComputingBinaryEffects += thisTreeTimeForComputingBinaryEffects;

				//=== Output at the end of getting all pairwise interactions for this tree.
				//				s = "Tree " + numTree + ": " + "Fraction of variance explained by main effects this tree: " + thisTreeFractionOfVarianceExplainedByMarginals + "%. Took " + thisTreeTimeForComputingMainEffects + " seconds.";
				//				log.info(s);

				s = "Tree " + numTree + ": " + "Fraction of variance explained by binary interaction effects this tree: " + thisTreeFractionOfVarianceExplainedByBinaries + "%. Took " + thisTreeTimeForComputingBinaryEffects + " seconds.";
				log.info(s);
			}


			/*			
			//=== Get all ternary average effects.
			double thisTreeFractionOfVarianceExplainedByTernaries = 0;
			indicesOfObservations = new int[3];
			observations = new double[3];
			for(int j=0; j<configSpace.getCategoricalSize().length; j++){
				indicesOfObservations[0] = j;
				for(int j2=j+1; j2<configSpace.getCategoricalSize().length; j2++){
					indicesOfObservations[1] = j2;
					for(int j3=j2+1; j3<configSpace.getCategoricalSize().length; j3++){
						indicesOfObservations[2] = j3;
						ArrayList<Double> as = new ArrayList<Double>();
						for(int k=0; k<configSpace.getCategoricalSize()[j]; k++){
							observations[0] = k;
							for(int k2=0; k2<configSpace.getCategoricalSize()[j2]; k2++){
								observations[1] = k2;
								for(int k3=0; k3<configSpace.getCategoricalSize()[j3]; k3++){
									observations[2] = k3;

									marg = forest.Trees[numTree].marginalPerformance(indicesOfObservations, observations);
									as.add(marg);
	//								System.out.println("Marginal for parameters " + configSpace.getParameterNames().get(j) + "&" + configSpace.getParameterNames().get(j2) + "&" + configSpace.getParameterNames().get(j3) + " set to values " + k + "&" + k2 + "&" + k3 + ": " + marg);

								}
							}
						}
						double avg = 0;
						for (Double entry :as) {
							avg += entry;
						}
						avg /= (as.size()+0.0);
						double thisTernaryVarianceContribution = 0;
						for (Double entry :as) {
							thisTernaryVarianceContribution += 1/(as.size()+0.0) * Math.pow(entry-avg,2);
						}

						HashSet<Integer> set = new HashSet<Integer>();
						set.add(j);
						thisTernaryVarianceContribution -= thisTreeVarianceContributions.get(set);

						set = new HashSet<Integer>();
						set.add(j2);
						thisTernaryVarianceContribution -= thisTreeVarianceContributions.get(set);

						set = new HashSet<Integer>();
						set.add(j3);
						thisTernaryVarianceContribution -= thisTreeVarianceContributions.get(set);

						set = new HashSet<Integer>();
						set.add(j);
						set.add(j2);
						thisTernaryVarianceContribution -= thisTreeVarianceContributions.get(set);

						set = new HashSet<Integer>();
						set.add(j);
						set.add(j3);
						thisTernaryVarianceContribution -= thisTreeVarianceContributions.get(set);

						set = new HashSet<Integer>();
						set.add(j2);
						set.add(j3);
						thisTernaryVarianceContribution -= thisTreeVarianceContributions.get(set);

						set = new HashSet<Integer>();
						set.add(j);
						set.add(j2);
						set.add(j3);
						thisTreeVarianceContributions.put(set, thisTernaryVarianceContribution);

						System.out.println("avg = " + avg + "; Variance contribution of parameters " + configSpace.getParameterNames().get(j) + "&" + configSpace.getParameterNames().get(j2) + "&" + configSpace.getParameterNames().get(j3) + " = " + thisTernaryVarianceContribution + ", or " + thisTernaryVarianceContribution/thisTreeTotalVariance*100 + "%");
						thisTreeFractionOfVarianceExplainedByTernaries += thisTernaryVarianceContribution/thisTreeTotalVariance*100;
					}
				}
			}

			double timeForComputingTernaryEffects = (System.nanoTime() - start) * 1.0e-9 - timeForComputingMainEffects - timeForComputingBinaryEffects;
			System.out.println("Tree " + numTree + ": " + "Fraction of variance explained by main effects: " + thisTreeFractionOfVarianceExplainedByMarginals + "%. Took " + timeForComputingMainEffects + " seconds.");
			System.out.println("Tree " + numTree + ": " + "Fraction of variance explained by binary interaction effects: " + thisTreeFractionOfVarianceExplainedByBinaries + "%. Took " + timeForComputingBinaryEffects + " seconds.");
			System.out.println("Tree " + numTree + ": " + "Fraction of variance explained by ternary interaction effects: " + thisTreeFractionOfVarianceExplainedByTernaries + "%. Took " + timeForComputingTernaryEffects + " seconds.");


			//=== Get all 4-ary average effects.
			double thisTreeFractionOfVarianceExplainedByQuadruples = 0;
			indicesOfObservations = new int[4];
			observations = new double[4];
			for(int j=0; j<configSpace.getCategoricalSize().length; j++){
				indicesOfObservations[0] = j;
				for(int j2=j+1; j2<configSpace.getCategoricalSize().length; j2++){
					indicesOfObservations[1] = j2;
					for(int j3=j2+1; j3<configSpace.getCategoricalSize().length; j3++){
						indicesOfObservations[2] = j3;
						for(int j4=j3+1; j4<configSpace.getCategoricalSize().length; j4++){
							indicesOfObservations[3] = j4;
							ArrayList<Double> as = new ArrayList<Double>();
							for(int k=0; k<configSpace.getCategoricalSize()[j]; k++){
								observations[0] = k;
								for(int k2=0; k2<configSpace.getCategoricalSize()[j2]; k2++){
									observations[1] = k2;
									for(int k3=0; k3<configSpace.getCategoricalSize()[j3]; k3++){
										observations[2] = k3;
										for(int k4=0; k4<configSpace.getCategoricalSize()[j4]; k4++){
											observations[3] = k4;

											marg = forest.Trees[numTree].marginalPerformance(indicesOfObservations, observations);
											as.add(marg);
	//										System.out.println("Marginal for parameters " + configSpace.getParameterNames().get(j) + "&" + configSpace.getParameterNames().get(j2) + "&" + configSpace.getParameterNames().get(j3) + "&" + configSpace.getParameterNames().get(j3) + " set to values " + k + "&" + k2 + "&" + k3 + "&" + k4 + ": " + marg);
										}
									}
								}
							}
							double avg = 0;
							for (Double entry :as) {
								avg += entry;
							}
							avg /= (as.size()+0.0);
							double thisQuadrupleVarianceContribution = 0;
							for (Double entry :as) {
								thisQuadrupleVarianceContribution += 1/(as.size()+0.0) * Math.pow(entry-avg,2);
							}

							HashSet<Integer> set = new HashSet<Integer>();
							set.add(j);
							thisQuadrupleVarianceContribution -= thisTreeVarianceContributions.get(set);

							set = new HashSet<Integer>();
							set.add(j2);
							thisQuadrupleVarianceContribution -= thisTreeVarianceContributions.get(set);

							set = new HashSet<Integer>();
							set.add(j3);
							thisQuadrupleVarianceContribution -= thisTreeVarianceContributions.get(set);

							set = new HashSet<Integer>();
							set.add(j4);
							thisQuadrupleVarianceContribution -= thisTreeVarianceContributions.get(set);

							set = new HashSet<Integer>();
							set.add(j);
							set.add(j2);
							thisQuadrupleVarianceContribution -= thisTreeVarianceContributions.get(set);

							set = new HashSet<Integer>();
							set.add(j);
							set.add(j3);
							thisQuadrupleVarianceContribution -= thisTreeVarianceContributions.get(set);

							set = new HashSet<Integer>();
							set.add(j);
							set.add(j4);
							thisQuadrupleVarianceContribution -= thisTreeVarianceContributions.get(set);

							set = new HashSet<Integer>();
							set.add(j2);
							set.add(j3);
							thisQuadrupleVarianceContribution -= thisTreeVarianceContributions.get(set);

							set = new HashSet<Integer>();
							set.add(j2);
							set.add(j4);
							thisQuadrupleVarianceContribution -= thisTreeVarianceContributions.get(set);

							set = new HashSet<Integer>();
							set.add(j3);
							set.add(j4);
							thisQuadrupleVarianceContribution -= thisTreeVarianceContributions.get(set);

							set = new HashSet<Integer>();
							set.add(j);
							set.add(j2);
							set.add(j3);
							thisQuadrupleVarianceContribution -= thisTreeVarianceContributions.get(set);

							set = new HashSet<Integer>();
							set.add(j);
							set.add(j2);
							set.add(j4);
							thisQuadrupleVarianceContribution -= thisTreeVarianceContributions.get(set);

							set = new HashSet<Integer>();
							set.add(j);
							set.add(j3);
							set.add(j4);
							thisQuadrupleVarianceContribution -= thisTreeVarianceContributions.get(set);

							set = new HashSet<Integer>();
							set.add(j2);
							set.add(j3);
							set.add(j4);
							thisQuadrupleVarianceContribution -= thisTreeVarianceContributions.get(set);

							set = new HashSet<Integer>();
							set.add(j);
							set.add(j2);
							set.add(j3);
							set.add(j4);
							thisTreeVarianceContributions.put(set, thisQuadrupleVarianceContribution);

							thisTreeFractionOfVarianceExplainedByQuadruples += thisQuadrupleVarianceContribution/thisTreeTotalVariance*100;
							System.out.println("avg = " + avg + "; Variance contribution of parameters " + configSpace.getParameterNames().get(j) + "&" + configSpace.getParameterNames().get(j2) + "&" + configSpace.getParameterNames().get(j3) + "&" + configSpace.getParameterNames().get(j4) + " = " + thisQuadrupleVarianceContribution + ", or " + thisTreeFractionOfVarianceExplainedByQuadruples + "%");
	//						System.out.println();
	//						System.out.println();
						}
					}
				}
			}
			double timeForComputingFouraryEffects = (System.nanoTime() - start) * 1.0e-9 - timeForComputingMainEffects - timeForComputingBinaryEffects - timeForComputingTernaryEffects;
			System.out.println("Tree " + numTree + ": " + "Fraction of variance explained by main effects: " + thisTreeFractionOfVarianceExplainedByMarginals + "%. Took " + timeForComputingMainEffects + " seconds.");
			System.out.println("Tree " + numTree + ": " + "Fraction of variance explained by binary interaction effects: " + thisTreeFractionOfVarianceExplainedByBinaries + "%. Took " + timeForComputingBinaryEffects + " seconds.");
			System.out.println("Tree " + numTree + ": " + "Fraction of variance explained by ternary interaction effects: " + thisTreeFractionOfVarianceExplainedByTernaries + "%. Took " + timeForComputingTernaryEffects + " seconds.");
			System.out.println("Tree " + numTree + ": " + "Fraction of variance explained by 4-ary interaction effects: " + thisTreeFractionOfVarianceExplainedByQuadruples + "%. Took " + timeForComputingFouraryEffects + " seconds.");

			System.out.println("Tree " + numTree + ": " + "Combined fraction of variance explained: " + (thisTreeFractionOfVarianceExplainedByMarginals + thisTreeFractionOfVarianceExplainedByBinaries + thisTreeFractionOfVarianceExplainedByTernaries + thisTreeFractionOfVarianceExplainedByQuadruples) + "%. Took " + (timeForComputingMainEffects + timeForComputingBinaryEffects + timeForComputingTernaryEffects + timeForComputingFouraryEffects) + " seconds.");

			 */			

			double tmpThisTreeFractionExplained = 0;
			//=== Sum up overall variance explained and keep track of it. 
			for (HashSet<Integer> indexSet : thisTreeVarianceContributions.keySet()) {
				double previousFractionExplained = 0;
				if(numTree > 0){
					previousFractionExplained = totalFractionsExplained.get(indexSet);
				}
				double thisFractionExplained = thisTreeVarianceContributions.get(indexSet)/thisTreeTotalVariance*100;
				tmpThisTreeFractionExplained += thisFractionExplained;
				totalFractionsExplained.put(indexSet, previousFractionExplained + 1.0/forest.Trees.length * thisFractionExplained);
			}	
			log.debug(tmpThisTreeFractionExplained + "%");

		} // end loop over trees.


		double tmpFractionExplained = 0;
		//=== Sum up overall variance explained. 
		for (HashSet<Integer> indexSet : totalFractionsExplained.keySet()) {
			tmpFractionExplained += totalFractionsExplained.get(indexSet);
		}	
		log.debug(tmpFractionExplained + "%");


		double sumOfFractionsOfMarginals = 0;
		//=== Sum up overall variance explained. 
		for (HashSet<Integer> indexSet : totalFractionsExplained.keySet()) {
			if(indexSet.size() == 1){
				sumOfFractionsOfMarginals += totalFractionsExplained.get(indexSet);
			}
		}	
		s = "\nSum of fractions of marginals: " + sumOfFractionsOfMarginals + "%";
		log.info(s);

		if (computePairwiseInteraction){
			sumOfFractionsOfBinaries = 0;
			//=== Sum up overall variance explained. 
			for (HashSet<Integer> indexSet : totalFractionsExplained.keySet()) {
				if(indexSet.size() == 2){
					sumOfFractionsOfBinaries += totalFractionsExplained.get(indexSet);
				}
			}	
			s = "Sum of fractions of binaries: " + sumOfFractionsOfBinaries + "%";
			log.info(s);
		}

		s = "Results for paper:   & " + decim.format(sumOfFractionsOfMarginals) + "\\% (" + decim.format(timeForComputingMainEffects)  + "s) & " + decim.format(sumOfFractionsOfBinaries) + "\\% (" + decim.format(timeForComputingBinaryEffects)  + "s)";
		log.info(s);



		//=== Sort params by marginal contribution.
		ArrayList<String> paramNamesOrderByMarginalVarianceExplained = new ArrayList<String>();
		int idx = 0;
		ArrayList<Map.Entry<HashSet, Double>> list = sortedKeysByValue(totalFractionsExplained);

		int numMaxEffectsToPrint = 30;
		s = "\n" + numMaxEffectsToPrint + " most important effects (out of main and binary interaction effects):";
		log.info(s);

		for (Map.Entry<HashSet, Double> entry : list) {
			HashSet<Integer> set = entry.getKey();
			numMaxEffectsToPrint--;
			if (set.size() == 1) {
				Integer varIndex = (Integer) set.toArray()[0];
				String parameterName = configSpace.getParameterNames().get(varIndex);
//				if (configSpace.getCategoricalValueMap().get(parameterName).size() > 0){
					paramNamesOrderByMarginalVarianceExplained.add(parameterName);
//				}
				if (numMaxEffectsToPrint > 0) {
					s = decim2.format(entry.getValue()) + "% due to main effect: " + parameterName;
					log.info(s);
				}
			} else if (set.size() == 2) {
				Integer varIndex1 = (Integer) set.toArray()[0];
				Integer varIndex2 = (Integer) set.toArray()[1];
				String varName1 = configSpace.getParameterNames().get(varIndex1);
				String varName2 = configSpace.getParameterNames().get(varIndex2);
				if (numMaxEffectsToPrint > 0) {
					s = decim2.format(entry.getValue()) + "% due to interaction: " + varName1 + " x " + varName2;
					log.info(s);
				}
			}
		}

		
		/**************************************************************** 
		 * Write out the marginals for all parameters.
		 ****************************************************************/
		String allSingleMarginalsOutputFile = outputDir + "/allSingleMarginals.txt"; 
		PrintWriter writer = null;
        try {
                writer = new PrintWriter(allSingleMarginalsOutputFile, "UTF-8");
        } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
        } 
        
        if (plotMarginals){
	        
	        log.info("Collecting and plotting marginals ...");
			for(int dim=0; dim<numDim; dim++){
				String parameterName = configSpace.getParameterNames().get(dim);
//				if (allObservations[dim].length <= 1) continue; // skip categoricals with a single value and continuous ones that weren't split on.
//				writer.println( parameterName );
	
				
				/**************************************************************** 
				 * Get the values to predict for.
				 ****************************************************************/
				double[] valuesToPredictFor;
				if (configSpace.getCategoricalSize()[dim] > 0){
					valuesToPredictFor = allObservations[dim];
				} else {
					int numVals = 11;
					valuesToPredictFor = new double[numVals];
					for(int valIndex=0; valIndex<numVals; valIndex++){
						valuesToPredictFor[valIndex] = (valIndex+0.0)/(numVals-1);	
					}
				}

				
				/**************************************************************** 
				 * Re-predict and write out marginals for these values.
				 ****************************************************************/
				//=== Open file writer.
				String singleMarginalOutputFile = outputDir + "/" + parameterName + ".marginals"; 
				PrintWriter singleWriter = null;
		        try {
		        	singleWriter = new PrintWriter(singleMarginalOutputFile, "UTF-8");
		        } catch (Exception e) {
		                // TODO Auto-generated catch block
		                e.printStackTrace();
		        }

//				String singleMarginalOutputFileCSV = outputDir + "/" + parameterName + "-marginals.csv"; 
//				PrintWriter singleWriterCSV = null;
//		        try {
//		        	singleWriterCSV = new PrintWriter(singleMarginalOutputFileCSV, "UTF-8");
//		        } catch (Exception e) {
//		                // TODO Auto-generated catch block
//		                e.printStackTrace();
//		        }

				int[] indicesOfObservations = new int[1];
				indicesOfObservations[0] = dim;
				for(int valIndex=0; valIndex < valuesToPredictFor.length; valIndex++){
					//=== Re-predict the marginals with each tree.
					double[] observations = new double[1];
					observations[0] = valuesToPredictFor[valIndex];

					ArrayList<Double> margs = new ArrayList<Double>();
					for(int numTree=0; numTree<forest.Trees.length; numTree++){
						margs.add(forest.Trees[numTree].marginalPerformance(indicesOfObservations, observations));
					}

					//=== Compute mean and standard deviation of the marginals across trees.
					double avg=0;
					for (Double marg : margs) {
						avg += marg / margs.size();
					}
					double std = 0;
					for (Double marg : margs) {
						std += Math.pow(marg-avg,2) / margs.size();
					}
					std = Math.sqrt(std);
				
					//=== Output the marginal.
					if (configSpace.getCategoricalSize()[dim] > 0){
						writer.println(configSpace.getValuesMap().get(parameterName).get(valIndex) + ": " + avg + " +/- " + std );
						singleWriter.println( valIndex + " " + configSpace.getValuesMap().get(parameterName).get(valIndex) + " " + avg + " " + std );
					} else {
						writer.println(valuesToPredictFor[valIndex] + ": " + avg + " +/- " + std );
						singleWriter.println( valIndex + " " + valuesToPredictFor[valIndex] + " " + avg + " " + std );					
//						singleWriterCSV.println( valIndex + ", " + valuesToPredictFor[valIndex] + ", " + avg + ", " + std );					
					}
					
	//				singleWriter.print(configSpace.getValuesMap().get(parameterName).get(k));
	//				for (Double marg : margs) {
	//					singleWriter.print(" " + marg);
	//				}
	//				singleWriter.println();
				}
				writer.println();
				singleWriter.close();
//				singleWriterCSV.close();
	
				
				
				
				/**************************************************************** 
				 * Generate a gnuplot script to plot the marginals.
				 ****************************************************************/
				
				String gnuplotFile = outputDir + "/" + parameterName + ".gnuplot"; 
				singleWriter = null;
		        try {
		        	singleWriter = new PrintWriter(gnuplotFile, "UTF-8");
		        } catch (Exception e) {
		                // TODO Auto-generated catch block
		                e.printStackTrace();
		        }
				singleWriter.println("set xtic rotate by -45");
				singleWriter.println("set terminal pdf");
				String pdfFilename = outputDir + "/" + parameterName + "-marginals.pdf";
				singleWriter.println("set output '" + pdfFilename + "'");
				singleWriter.println("set boxwidth 0.75");
				singleWriter.println("set style fill solid");
				singleWriter.println("plot '" + singleMarginalOutputFile + "' using 3:xtic(2) with boxes fillstyle solid notitle, \\");
				singleWriter.println("'" + singleMarginalOutputFile + "' using 1:3:4 with boxerrorbars fillstyle empty lc rgb 'black' notitle");
				singleWriter.close();
				
				String cmd = "gnuplot " + gnuplotFile;
				//log.info("Calling: " + cmd);
				Process p = Runtime.getRuntime().exec(cmd);
				p.waitFor();
	//			log.info("Outputted PDF file: " + pdfFilename);
			}
			
			writer.close();
			log.info("\nSingle marginal predictions written to file " + allSingleMarginalsOutputFile);
	
	/*		
	        log.info("Collect and plot important double marginals ...");
			for(int dim1=0; dim1<numDim; dim1++){
				String parameterName1 = configSpace.getParameterNames().get(dim1);
				for(int dim2=dim1+1; dim2<numDim; dim2++){
					String parameterName2 = configSpace.getParameterNames().get(dim2);
					
					HashSet set = new HashSet<Integer>();
					set.add(dim1);
					set.add(dim2);
					if (totalFractionsExplained.get(set) > 0.001){
						//===  Write out the marginals for this parameter combo.
						
						String doubleMarginalOutputFile = outputDir + "/" + parameterName1 + "__" + parameterName2 + ".marginals"; 
						PrintWriter doubleWriter = null;
				        try {
				        	doubleWriter = new PrintWriter(doubleMarginalOutputFile, "UTF-8");
				        } catch (Exception e) {
				                // TODO Auto-generated catch block
				                e.printStackTrace();
				        }
				
				        for(int valIndex1=0; valIndex1<allObservations[dim1].length; valIndex1++){
					        for(int valIndex2=0; valIndex2<allObservations[dim2].length; valIndex2++){
								ArrayList<Double> margs = doubleMarginals.get(dim1).get(dim2).get(valIndex1).get(valIndex2);
		
								double avg=0;
								for (Double marg : margs) {
									avg += marg / margs.size();
								}
								double std = 0;
								for (Double marg : margs) {
									std += Math.pow(marg-avg,2) / margs.size();
								}
								std = Math.sqrt(std);
					        	
								doubleWriter.println( valIndex1 + "," + valIndex2 + "," + allObservations[dim1][valIndex1] + "," + allObservations[dim2][valIndex2] + "," + avg + "," + std);
					        }
				        }
						doubleWriter.close();
					}
				}
			}
			*/
			
			
			
			
			
			/********************************************************************************** 
			 * Generate a .tex file combining all the .pdf files for the marginal predictions.
			 **********************************************************************************/
	        log.info("Generating .tex file ...");
			String texFile = outputDir + "/allSingleMarginals.tex"; 
			writer = null;
	        try {
	        	writer = new PrintWriter(texFile, "UTF-8");
	        } catch (Exception e) {
	                // TODO Auto-generated catch block
	                e.printStackTrace();
	        }
	        writer.println("\\documentclass[letterpaper]{article}");
	        writer.println("\\usepackage{times}");
	        writer.println("\\usepackage{graphicx}");
			writer.println("\\usepackage{epsfig}");
			writer.println("\\usepackage{subfigure}");
			writer.println("\\usepackage{lscape}");
			writer.println("\\begin{document}");
			writer.println("\\title{Functional ANOVA Analysis}");
			writer.println("\\maketitle");
			writer.println("\\textbf{When using parts of this document, please cite the functional ANOVA paper (the reference will be available soon; in the meantime please ask Frank for details).}");
	//		if (logModel){
	//			writer.println("\nNote: All marginals are given in log10 space.");
	//		}
			
			int count = 0;
			for(String parameterName: paramNamesOrderByMarginalVarianceExplained){
				int indexOfParameter = configSpace.getParameterNamesInAuthorativeOrder().indexOf(parameterName);
				HashSet<Integer> set = new HashSet<Integer>();
				set.add(indexOfParameter);
				double fractionExplainedByThisMarginal = totalFractionsExplained.get(set);
				writer.println("\\begin{figure}[tbp]");
				writer.println("\\begin{center}");
				String singleMarginalOutputFile = outputDir + "/" + parameterName + "-marginals.pdf";
				writer.println("\\includegraphics{" + singleMarginalOutputFile + "}");
				writer.println("\\caption{Marginal predictions for parameter " + parameterName.replace("_", "\\_") + ". This marginal explains " + decim2.format(fractionExplainedByThisMarginal) + "\\% of the predictor's total variance.  \\label{fig:" + parameterName + "}}");
				writer.println("\\end{center}");
				writer.println("\\end{figure}");
				if (count++ % 4==0) writer.println("\\clearpage");
			}
			
			/*
			%\bibliographystyle{theapa}
			%\footnotesize{\bibliography{abbrev,frankbib}}
			*/
	
			writer.println("\\end{document}");		
			writer.close();
	
			String cmd = "pdflatex " + texFile + " > pdflatex-output.txt";
			log.info("Need to call: " + cmd);
	/*
	 		try {
	
				Process p = Runtime.getRuntime().exec(cmd, null, new File(outputDir));
				p.waitFor();
				p = Runtime.getRuntime().exec(cmd);
				p.waitFor();			
				log.info("Wrote file visualizing all marginals: " + outputDir + "/allSingleMarginals.pdf");
			} catch (Exception e) {
				log.info("The command line call to pdflatex threw an exception - you'll have to manually compile the .tex file " + outputDir + "/allSingleMarginals.tex in order to see all the marginals.\n"
						+ "Probably pdflatex is not installed. Here's the exception message: " + e.getMessage());			
			}
	*/
        }
        log.info("Functional ANOVA finished successfully - exiting.");
	}
        
	/**
	 * Tests whether preprocessing runs through. 
	 */

	//	public void testVarianceFractions() {
	//		/*		String[] names = {				
	//				"AAAI_CPLEX12-CORLAT-2day-10000-discrete_combined_runs_surrogatezip___MEAN10.zip",
	//				"AAAI_CPLEX12-RCW2-2day-10000-discrete_combined_runs_surrogatezip___MEAN10.zip",
	//				"AAAI_CPLEX12-Regions200-2day-10000-discrete_combined_runs_surrogatezip___MEAN10.zip",
	//				"AAAI_CPLEX12-CLS-2day-10000-discrete_combined_runs_surrogatezip___MEAN10.zip",
	//
	//				"AAAI_Clasp-SWV-1day-300-discrete_combined_runs_surrogatezip___MEAN10.zip",
	//				"AAAI_Cryptominisat-SWV-1day-300-discrete_combined_runs_surrogatezip___MEAN10.zip",
	//				"AAAI_SPEAR-SWV-1day-300-discrete_combined_runs_surrogatezip___MEAN10.zip",
	//
	//				"AAAI_Clasp-IBM-2day-300-complete-discrete_combined_runs_surrogatezip___MEAN10.zip",
	//				"AAAI_Cryptominisat-IBM-2day-300-complete-discrete_combined_runs_surrogatezip___MEAN10.zip",
	//				"AAAI_SPEAR-IBM-2day-300-complete-discrete_combined_runs_surrogatezip___MEAN10.zip",
	//
	//				"AAAI_Captainjack-5SAT-500-discrete_combined_runs_surrogatezip___MEAN10.zip", 
	//				"AAAI_Captainjack-3SAT-1k-discrete_combined_runs_surrogatezip___MEAN10.zip",
	//				"AAAI_SATenstein-5SAT-500-discrete_combined_runs_surrogatezip___MEAN10.zip",
	//				"AAAI_SATenstein-3SAT-1k-discrete_combined_runs_surrogatezip___MEAN10.zip",
	//				"AAAI_Sparrowfixed-5SAT-500-discrete_combined_runs_surrogatezip___MEAN10.zip",
	//				"AAAI_Sparrowfixed-3SAT-1k-discrete_combined_runs_surrogatezip___MEAN10.zip",
	//
	//				"AAAI_LKH-RUE-1day-discrete_combined_runs_surrogatezip___MEAN10.zip",
	//				"AAAI_LKH-RCE-1day-discrete_combined_runs_surrogatezip___MEAN10.zip",
	//
	//				"AAAI_Clasp-ASP-Texas-Wseq-2day-discrete_combined_runs_surrogatezip___MEAN10.zip", // problematic.
	//				"AAAI_Clasp-ASP-Riposte-discrete_combined_runs_surrogatezip___MEAN10.zip"};*/
	//
	//		String[] names = {"Sparrowfixed-3SAT-1k-discrete",
	//				"Sparrowfixed-5SAT-500-discrete",
	//				"SPEAR-IBM-4day-300-complete-discrete",
	//				"SPEAR-SWV-1day-300-discrete",
	//				"Cryptominisat-SWV-1day-300-discrete",
	//				"Cryptominisat-IBM-4day-300-complete-discrete",
	//				"CPLEX12-RCW2-4day-10000-discrete",
	//				"SATenstein-3SAT-1k-discrete",
	//				"SATenstein-5SAT-500-discrete",
	//				"Captainjack-3SAT-1k-discrete",
	//				"Captainjack-5SAT-500-discrete",
	//				"Clasp-ASP-Texas-Wseq-4day-discrete",
	//				"Clasp-IBM-4day-300-complete-discrete",
	//				"Clasp-SWV-1day-300-discrete",
	//				"CPLEX12-CORLAT-4day-10000-discrete",
	//				"CPLEX12-Regions200-4day-10000-discrete",
	//				"CPLEX12-CLS-4day-10000-discrete",
	//				"Clasp-ASP-Riposte-discrete"
	//		};
	//
	//		//					"LKH-RUE-1day-discrete",
	//		//"LKH-RCE-1day-discrete"
	//
	//		for (int i = 0; i < names.length; i++) {
	//
	//			String saveName = names[i] + "_combined_runs_10SMAC_plus_random";
	//			//				String saveName = names[i] + "_combined_runs_10SMACcap";
	//			//				String saveName = names[i] + "_combined_runs_only_random";
	//			String name = "AAAI_" + saveName + "_surrogatezip___MEAN10.zip"; 
	//			//				testVarianceFractionsNamed(name, saveName, false, -1);
	//			testVarianceFractionsNamed(name, saveName, true, -1);
	//			testVarianceFractionsNamed(name, saveName, false, 0.25);
	//		}
	//
	//		for (int i = 0; i < names.length; i++) {
	//			String saveName = names[i] + "_combined_runs_10SMACcap";
	//			String name = "AAAI_" + saveName + "_surrogatezip___MEAN10.zip"; 
	//			testVarianceFractionsNamed(name, saveName, false, -1);
	//			//				testVarianceFractionsNamed(name, saveName, true, -1);
	//			testVarianceFractionsNamed(name, saveName, false, 0.25);
	//		}
	//
	//	}		
}
