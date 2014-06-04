# $Id$
#
# ParamILS wrapper for Spear theorem prover.

# Deal with inputs.
if ARGV.length < 5
    puts "spear_wrapper.rb is a wrapper for the Spear theorem prover."
    puts "Usage: ruby spear_wrapper.rb <instance_relname>
<instance_specifics (string in \"quotes\"> <cutoff_time> <cutoff_length> <seed> <params to be
passed on>."
    exit -1
end

input_file = ARGV[0]
#=== Here instance_specifics are not used - but you can put any information into this string you wish ...
instance_specifics = ARGV[1]
timeout = ARGV[2].to_i
cutoff_length = ARGV[3].to_i
seed = ARGV[4].to_i

# By default, ParamILS builds parameters as -param, but Spear requires
# --param. The following line fixes that.
tmpparams = ARGV[5...ARGV.length].collect{|x| x.sub(/^-sp/, "--sp")}

# Concatenate all params.
paramstring = tmpparams.join(" ")

# Build algorithm command and execute it.
#
# Change --dimacs according to your input (--sf for modular arithmetic)
cmd = "./Spear-32_1.2.1 --nosplash --time #{paramstring} --dimacs #{input_file} --tmout #{timeout} --seed #{seed}"

tmp_file = "spear_output#{rand}.txt"
exec_cmd = "#{cmd} > #{tmp_file}"

STDERR.puts "Calling: #{exec_cmd}"
system exec_cmd

#=== Parse algorithm output to extract relevant information for ParamILS.

runtime = timeout

solved = "CRASHED"
File.open(tmp_file){|file|
    while line = file.gets
        if line =~ /s UNSATISFIABLE/
            solved = "UNSAT"
	end
        if line =~ /s SATISFIABLE/
            solved = "SAT"
        end
        if line =~ /s UNKNOWN/
		solved = "TIMEOUT"
        end
        if line =~ /runtime (\d+\.\d+)/
            runtime = $1.to_f
        end
    end
}
File.delete(tmp_file)
puts "Result for ParamILS: #{solved}, #{runtime}, 0, 0, #{seed}"
