package net.aclib.fanova.options;

import ca.ubc.cs.beta.aclib.misc.options.NoArgumentHandler;

public class FAnovaNoArgumentHandler implements NoArgumentHandler {

	@Override
	public boolean handleNoArguments() {
		StringBuilder sb = new StringBuilder();
		
		System.out.println("Functional ANOVA Tool for Computing Parameter Importance.\n");
		System.out.println("USAGE example: ./fanova --restoreScenario ./example/spear-smacout/state-run1 --seed 1\n");

		System.out.println("Example command for merging state files: ./state-merge --directories ./example/spear-smacout/ --scenario-file ./example/spear-scenario/spear-scenario.txt --outdir tmp_merged --repair false");
		System.out.println("Example to work on merged files: ./fanova --restoreScenario ./tmp_merged/ --seed 1");
		
		System.out.println("\nQuick script for merging files and running fanova on the result:");
		System.out.println("ruby merge_states_and_run_fanova.rb <parent directory of state run folders> <scenario file> <seed>"); 
		System.out.println("Example: ruby merge_states_and_run_fanova.rb example/spear-smacout example/spear-scenario/spear-scenario.txt 1"); 
	
		return true;
	}

}
