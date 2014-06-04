#  ../spear-scenario-SMAC-ac-true-cores1-cutoff5.0-2013-08-04/ 
# ../example_scenarios/spear/spear-scenario.txt
# ruby merge_states_and_run_fanova.rb example/spear-smacout example/spear-scenario/spear-scenario.txt 1 

dir = ARGV[0]
scenario = ARGV[1]
seed = ARGV[2]

cmd = "./state-merge --directories #{dir} --scenario-file #{scenario} --outdir tmp_merged --repair false"
puts "Calling cmd: #{cmd}"
system cmd

cmd = "./fanova --restoreScenario ./tmp_merged/ --seed #{seed}"
puts "Calling cmd: #{cmd}"
system cmd
