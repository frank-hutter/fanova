package net.aclib.fanova.options;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParametersDelegate;


import ca.ubc.cs.beta.aclib.help.HelpOptions;
import ca.ubc.cs.beta.aclib.logging.ComplexLoggingOptions;
import ca.ubc.cs.beta.aclib.logging.LoggingOptions;
import ca.ubc.cs.beta.aclib.misc.jcommander.validator.ZeroOneHalfOpenLeftDouble;
import ca.ubc.cs.beta.aclib.misc.options.UsageTextField;
import ca.ubc.cs.beta.aclib.model.ModelBuildingOptions;
import ca.ubc.cs.beta.aclib.options.AbstractOptions;
import ca.ubc.cs.beta.aclib.options.RandomForestOptions;
import ca.ubc.cs.beta.aclib.options.RunGroupOptions;
import ca.ubc.cs.beta.aclib.options.scenario.ScenarioOptions;
import ca.ubc.cs.beta.aclib.random.SeedOptions;
import ca.ubc.cs.beta.aclib.state.StateFactoryOptions;

@UsageTextField(title="Functional Anova Options", noarg = FAnovaNoArgumentHandler.class)
public class FAnovaOptions extends AbstractOptions {
	
	@ParametersDelegate
	public StateFactoryOptions stateFactoryOptions = new StateFactoryOptions();
	
	@ParametersDelegate
	public RandomForestOptions rfOptions = new RandomForestOptions();
	
	
	@ParametersDelegate
	public ScenarioOptions scenOpts = new ScenarioOptions();
	
	@ParametersDelegate
	public LoggingOptions loggingOptions = new ComplexLoggingOptions();
//	
//	@ParametersDelegate
//	public AlgorithmExecutionOptions algoExecOptions = new AlgorithmExecutionOptions();
//	
//	@ParametersDelegate
//	public ProblemInstanceOptions piOptions = new ProblemInstanceOptions();

	@ParametersDelegate
	public HelpOptions helpOptions = new HelpOptions();
	
	@Parameter(names="--compute-pairwise-interactions", description="If true compute and report the pairwise interaction effects between parameters")
	public boolean computePairwiseInteration = true;
	
	@Parameter(names="--plot-marginals", description="If true a plot is generated for each predictive marginal (this will generate a data file, a gnuplot file, and a PDF for each parameter)")
	public boolean plotMarginals = true;
	
	
	@ParametersDelegate
	public ModelBuildingOptions mbOptions = new ModelBuildingOptions();
	
	@ParametersDelegate
	public SeedOptions seedOptions = new SeedOptions();

	@Parameter(names="--quantile-to-compare", description="Quantile to compare to (if using QUANTILE --improvements-over)", validateWith=ZeroOneHalfOpenLeftDouble.class)
	public double quantileToCompare = 0.25;
	
	@Parameter(names="--improvements-over", description="Compute improvements with respect to (this setting)")	
	public Improvements compare = Improvements.NOTHING;

	@Parameter(names="--num-training-samples", description="Number of training examples to use for fANOVA (-1 = all)")	
	public int numTrainingSamples = -1;
	
	@ParametersDelegate
	public RunGroupOptions runGroupOptions = new RunGroupOptions("%SCENARIO_NAME-improvOver%improvementsOver-quant%quantileToCompare-n%rfNumTrees-sm%rfSplitMin-it%restoreIteration");
	
	public enum Improvements
	{
		DEFAULT,
		QUANTILE,
		NOTHING
	}
	
	public FAnovaOptions()
	{
		scenOpts.outputDirectory= System.getProperty("user.dir") + File.separator + "fanova-output";
	}

	public String getRunGroupName()
	{	
		Collection<AbstractOptions> opts = new HashSet<AbstractOptions>();
		opts.add(this);
		return runGroupOptions.getRunGroupName(opts);	
	}
	
}
