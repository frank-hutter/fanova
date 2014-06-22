package net.aclib.fanova.model;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.ubc.cs.beta.aclib.configspace.ParamConfiguration;
import ca.ubc.cs.beta.aclib.configspace.ParamConfigurationSpace;
import ca.ubc.cs.beta.aclib.model.ModelBuildingOptions;
import ca.ubc.cs.beta.aclib.model.builder.AdaptiveCappingModelBuilder;
import ca.ubc.cs.beta.aclib.model.builder.BasicModelBuilder;
import ca.ubc.cs.beta.aclib.model.builder.ModelBuilder;
import ca.ubc.cs.beta.aclib.model.data.DefaultValueForConditionalsMDS;
import ca.ubc.cs.beta.aclib.model.data.MaskCensoredDataAsUncensored;
import ca.ubc.cs.beta.aclib.model.data.MaskInactiveConditionalParametersWithDefaults;
import ca.ubc.cs.beta.aclib.model.data.SanitizedModelData;
import ca.ubc.cs.beta.aclib.options.RandomForestOptions;
import ca.ubc.cs.beta.aclib.options.scenario.ScenarioOptions;
import ca.ubc.cs.beta.aclib.probleminstance.ProblemInstance;
import ca.ubc.cs.beta.aclib.random.SeedableRandomPool;
import ca.ubc.cs.beta.aclib.runhistory.RunHistory;
import ca.ubc.cs.beta.models.fastrf.RandomForest;

/**
 * Awful code that was ripped from SMAC that builds a model
 * @author Steve Ramage <seramage@cs.ubc.ca>
 *
 */
public class FunctionalANOVAModelBuilder {

	
	/**
	 * Most recent forest built
	 */
	private RandomForest forest;
	
	/**
	 * Most recent prepared forest built (may be NULL but always corresponds to the last forest built)
	 */
	private RandomForest preparedForest;
	
	/**
	 * Last build of sanitized data
	 */
	private SanitizedModelData sanitizedData;
	
	private final Logger log = LoggerFactory.getLogger(getClass());
	
	public void learnModel(List<ProblemInstance> instances, RunHistory runHistory, ParamConfigurationSpace configSpace, RandomForestOptions rfOptions, ModelBuildingOptions mbOptions, ScenarioOptions scenarioOptions, boolean adaptiveCapping, SeedableRandomPool pool) 
	{
		
		/*
		if(rfOptions.subsampleValuesWhenLowMemory)
		{
			
			double freeMemory = freeMemoryAfterGC();
			if(freeMemory < options.randomForestOptions.freeMemoryPercentageToSubsample)
			{
				subsamplePercentage *= options.randomForestOptions.subsamplePercentage;
				Object[] args = { getIteration(), freeMemory, subsamplePercentage};
				log.info("Iteration {} : Free memory too low ({}) subsample percentage now {} ", args);
			}

		} else
		{
			subsamplePercentage = 1;
		}
		*/
		
		
		
		
		
		
		
		
		
		//=== The following two sets are required to be sorted by instance and paramConfig ID.
		Set<ProblemInstance> all_instances = new LinkedHashSet<ProblemInstance>(instances);
		Set<ParamConfiguration> paramConfigs = runHistory.getUniqueParamConfigurations();
		
		Set<ProblemInstance> runInstances=runHistory.getUniqueInstancesRan();
		ArrayList<Integer> runInstancesIdx = new ArrayList<Integer>(all_instances.size());
		
		//=== Get the instance feature matrix (X).
		int i=0; 
		double[][] instanceFeatureMatrix = new double[all_instances.size()][];
		for(ProblemInstance pi : all_instances)
		{
			if(runInstances.contains(pi))
			{
				runInstancesIdx.add(i);
			}
			instanceFeatureMatrix[i] = pi.getFeaturesDouble();
			i++;
		}

		//=== Get the parameter configuration matrix (Theta).
		double[][] thetaMatrix = new double[paramConfigs.size()][];
		i = 0;
		for(ParamConfiguration pc : paramConfigs)
		{
			if(mbOptions.maskInactiveConditionalParametersAsDefaultValue)
			{
				thetaMatrix[i++] = pc.toComparisonValueArray();
			} else
			{
				thetaMatrix[i++] = pc.toValueArray();
			}
		}

		//=== Get an array of the order in which instances were used (TODO: same for Theta, from ModelBuilder) 
		int[] usedInstanceIdxs = new int[runInstancesIdx.size()]; 
		for(int j=0; j <  runInstancesIdx.size(); j++)
		{
			usedInstanceIdxs[j] = runInstancesIdx.get(j);
		}
		
		double[] runResponseValues = runHistory.getRunResponseValues();
		boolean[] censored = runHistory.getCensoredFlagForRuns();
		
		
		if(mbOptions.maskCensoredDataAsKappaMax)
		{
			for(int j=0; j < runResponseValues.length; j++)
			{
				if(censored[j])
				{
					runResponseValues[j] = scenarioOptions.algoExecOptions.cutoffTime;
				}
			}
		}
		
		
		
		
		for(int j=0; j < runResponseValues.length; j++)
		{ //=== Not sure if I Should be penalizing runs prior to the model
			// but matlab sure does
			if(runResponseValues[j] >= scenarioOptions.algoExecOptions.cutoffTime)
			{	
				runResponseValues[j] = scenarioOptions.algoExecOptions.cutoffTime * scenarioOptions.intraInstanceObj.getPenaltyFactor();
			}
			
		}
	
		//=== Sanitize the data.
		//sanitizedData = new PCAModelDataSanitizer(instanceFeatureMatrix, thetaMatrix, numPCA, runResponseValues, usedInstanceIdxs, logModel, runHistory.getParameterConfigurationInstancesRanByIndex(), runHistory.getCensoredFlagForRuns(), configSpace);
		
		
		
		
		
		SanitizedModelData sanitizedData = new DefaultValueForConditionalsMDS(instanceFeatureMatrix, thetaMatrix, runResponseValues, usedInstanceIdxs, rfOptions.logModel,runHistory.getParameterConfigurationInstancesRanByIndex(), runHistory.getCensoredFlagForRuns(), configSpace);
		
		if(mbOptions.maskCensoredDataAsUncensored)
		{
			sanitizedData = new MaskCensoredDataAsUncensored(sanitizedData);
		}
		
		
		if(mbOptions.maskInactiveConditionalParametersAsDefaultValue)
		{
			sanitizedData = new MaskInactiveConditionalParametersWithDefaults(sanitizedData, configSpace);
		}
		
		
		//=== Actually build the model.
		ModelBuilder mb;
		//TODO: always go through AdaptiveCappingModelBuilder
		forest = null;
		preparedForest = null;
		if(adaptiveCapping)
		{
			mb = new AdaptiveCappingModelBuilder(sanitizedData, rfOptions, pool.getRandom("RANDOM_FOREST_BUILDING_PRNG"), mbOptions.imputationIterations, scenarioOptions.algoExecOptions.cutoffTime, scenarioOptions.intraInstanceObj.getPenaltyFactor(), 1);
		} else
		{
			//mb = new HashCodeVerifyingModelBuilder(sanitizedData,smacConfig.randomForestOptions, runHistory);
			mb = new BasicModelBuilder(sanitizedData, rfOptions,1, pool.getRandom("RANDOM_FOREST_BUILDING_PRNG")); 
		}
		
		 /*= */
		forest = mb.getRandomForest();
		preparedForest = mb.getPreparedRandomForest();
	
		log.info("Random Forest Built");
	}
	
	
	
	public RandomForest getRandomForest()
	{
		return forest;
	}
	
	public RandomForest getPreparedForest()
	{
		return preparedForest;
	}

}
