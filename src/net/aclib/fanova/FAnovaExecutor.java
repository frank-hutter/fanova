package net.aclib.fanova;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;

import ca.ubc.cs.beta.aclib.algorithmrun.AlgorithmRun;
import ca.ubc.cs.beta.aclib.configspace.ParamConfigurationSpace;
import ca.ubc.cs.beta.aclib.execconfig.AlgorithmExecutionConfig;
import ca.ubc.cs.beta.aclib.misc.jcommander.JCommanderHelper;
import ca.ubc.cs.beta.aclib.misc.version.VersionTracker;
import ca.ubc.cs.beta.aclib.options.scenario.ScenarioOptions;
import ca.ubc.cs.beta.aclib.probleminstance.ProblemInstance;
import ca.ubc.cs.beta.aclib.random.SeedableRandomPool;
import ca.ubc.cs.beta.aclib.runhistory.NewRunHistory;
import ca.ubc.cs.beta.aclib.runhistory.RunHistory;
import ca.ubc.cs.beta.aclib.runhistory.ThreadSafeRunHistoryWrapper;
import ca.ubc.cs.beta.aclib.state.StateDeserializer;
import ca.ubc.cs.beta.aclib.state.StateFactory;
import ca.ubc.cs.beta.aclib.state.StateFactoryOptions;
import ca.ubc.cs.beta.aclib.state.legacy.LegacyStateFactory;
import net.aclib.fanova.model.FunctionalANOVARunner;
import net.aclib.fanova.model.FunctionalANOVAModelBuilder;
import net.aclib.fanova.options.FAnovaOptions;
import net.aclib.fanova.options.FAnovaOptions.Improvements;

public class FAnovaExecutor {
	private static Logger log;

	public static void main(String[] args) {
		String outputDir = "";
		try {
			SeedableRandomPool pool = null;

			try {
				FAnovaOptions fanovaOpts = new FAnovaOptions();
				JCommander jcom;

				//Manhandle the options to support --restoreScenario
				args = StateFactoryOptions.processScenarioStateRestore(args);
				ScenarioOptions scenarioOptions = fanovaOpts.scenOpts;
				try {
					jcom = JCommanderHelper.parseCheckingForHelpAndVersion(args, fanovaOpts);
					String runGroupName = fanovaOpts.getRunGroupName();
					scenarioOptions.makeOutputDirectory(runGroupName);
					//File outputDir = new File(options.scenarioConfig.outputDirectory);
					outputDir = scenarioOptions.outputDirectory + File.separator + runGroupName;
					fanovaOpts.loggingOptions.initializeLogging(outputDir, fanovaOpts.seedOptions.numRun);
				} finally {
					log = LoggerFactory.getLogger(FAnovaExecutor.class);
				}

				//Displays version information
				//See the TargetAlgorithmEvaluatorRunnerVersionInfo class for how to manage your own versions.
				VersionTracker.logVersions();

				for(String name : jcom.getParameterFilesToRead()) {
					log.info("Parsing (default) options from file: {} ", name);
				}

				if(fanovaOpts.rfOptions.logModel == null) {
					switch(fanovaOpts.scenOpts.runObj) {
						case RUNTIME:
							fanovaOpts.rfOptions.logModel = true;
							break;
						case QUALITY:
							fanovaOpts.rfOptions.logModel = false;
					}
				}

				//=== Load the runhistory.
				StateFactory sf = new LegacyStateFactory(null, fanovaOpts.stateFactoryOptions.restoreStateFrom);
				AlgorithmExecutionConfig execConfig = scenarioOptions.algoExecOptions.getAlgorithmExecutionConfigSkipDirCheck();
				ParamConfigurationSpace configSpace = execConfig.getParamFile();
				List<ProblemInstance> instances = scenarioOptions.getTrainingAndTestProblemInstances(new File(".").getAbsolutePath(), 0, 0, true, false, false, false).getTrainingInstances().getInstances();
				RunHistory rh = new ThreadSafeRunHistoryWrapper(new NewRunHistory(scenarioOptions.intraInstanceObj, scenarioOptions.interInstanceObj, scenarioOptions.runObj));
				
				if(fanovaOpts.stateFactoryOptions.restoreIteration == null)	{
					throw new ParameterException("You must specify an iteration to restore");
				}
				StateDeserializer sd = sf.getStateDeserializer("it", fanovaOpts.stateFactoryOptions.restoreIteration, configSpace, instances, execConfig, rh); // FH: counter-intuitively, this is where rh actually gets filled.
				pool = fanovaOpts.seedOptions.getSeedableRandomPool();

				//=== Subsample the runs.
				if (fanovaOpts.numTrainingSamples > 0){
					List<AlgorithmRun> algorithmRuns = rh.getAlgorithmRuns();
					Integer[] indices = new Integer[algorithmRuns.size()];
					for(int i=0; i<algorithmRuns.size(); i++){
						indices[i]=i;
					}
					List<Integer> shuffledIndices = Arrays.asList( indices );
				    Collections.shuffle( shuffledIndices, pool.getRandom("inputSubsample"));
				    RunHistory subsampledRH = new ThreadSafeRunHistoryWrapper(new NewRunHistory(scenarioOptions.intraInstanceObj, scenarioOptions.interInstanceObj, scenarioOptions.runObj));
				    for(int i=0; i<fanovaOpts.numTrainingSamples; i++){
				    	subsampledRH.append(algorithmRuns.get(shuffledIndices.get(i)));
				    }
				    rh = subsampledRH;
				}
				
				FunctionalANOVAModelBuilder famb = new FunctionalANOVAModelBuilder();
				famb.learnModel(instances, rh, configSpace, fanovaOpts.rfOptions, fanovaOpts.mbOptions, scenarioOptions, true, pool);
				//RandomForest rf = famb. 

				//=== Handle fANOVA options.
				boolean compareToDef = fanovaOpts.compare.equals(Improvements.DEFAULT) ? true : false;
				double quantile = fanovaOpts.compare.equals(Improvements.QUANTILE) ? fanovaOpts.quantileToCompare : -1;

				
				/*
				 * Old code loading forest from a file.

		//String prefix = "/ubc/cs/home/h/hutter/orcinus/home/hutter/experiments/surrogates/otherData/";
		//String surrogateZipFilename = prefix + name;

		//PrintWriter writer = null;
		//			extension = "_1tree_" + extension;

		//QuickZip surrogateZip = new QuickZip(surrogateZipFilename);

		
		//ParamConfigurationSpace configSpace = (ParamConfigurationSpace) surrogateZip.getObject(ZipFileName.CONFIG_SPACE_FILE);
		//LinkedHashSet<ParamConfiguration> paramList = (LinkedHashSet<ParamConfiguration>) surrogateZip.getObject(ZipFileName.PARAMS_OBJECTS);
		//for (ParamConfiguration paramConfiguration : paramList) {
		//	paramConfiguration.toValueArray();
		//}
		
		
		
		//=== Get the number of instance features.
		Iterator iterator = instanceList.iterator(); 
		ProblemInstance problemInstance = (ProblemInstance) iterator.next();
		int numFeatures = problemInstance.getFeaturesDouble().length;

		//=== Get the features X and preprocess the random forest with them.
		double[][] X = new double[instanceList.size()][numFeatures];
		int count = 0;
		for (ProblemInstance instance: instanceList){
			X[count++] = instance.getFeaturesDouble();
		}
		forest = RandomForest.preprocessForest(forest, X);
				 * 
				 */
				
				FunctionalANOVARunner.decomposeVariance(famb.getRandomForest(), rh.getAlgorithmRuns(), configSpace,pool.getRandom("FANOVA_BUILDER"), compareToDef, quantile, fanovaOpts.computePairwiseInteration, outputDir,fanovaOpts.rfOptions.logModel, fanovaOpts.plotMarginals);

			} finally {
				if(pool != null) {
					pool.logUsage();
				}
			}

		}  catch(ParameterException e) {	
			System.out.println(e);
			log.error(e.getMessage());
			if(log.isDebugEnabled()) {
				log.error("Stack trace:",e);
			}

		} catch(Exception e) {
			e.printStackTrace();
		}
	}
}
