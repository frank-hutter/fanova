input_file = ARGV[0]
output_dir = ARGV[1] + "/"

data = Hash.new
num_feat = -1
File.open(input_file){|file|
	while line = file.gets do
		entries = line.split(",").map{|x| x.strip}
		len = entries.length
		y = entries[len-1].to_f
		x = entries[0...len-1].map{|s|s.to_f}
		data[x] = [] unless data.has_key?(x)
		data[x] << y
		if num_feat != x.length
			raise "number of features not matching" unless num_feat == -1
			num_feat = x.length
		end
#		puts data[x].join(" ")
	end
}

#=== Determine lower and upper bounds.
low = []
upp = []
for i in 0...num_feat
	low[i] = Float::MAX
	upp[i] = Float::MIN
end



for x in data.keys
	for i in 0...num_feat
		low[i] = [low[i], x[i]].min
		upp[i] = [upp[i], x[i]].max
	end
end

Dir.mkdir(output_dir) unless File.exists?(output_dir)

#=== Empty instance file
File.open(output_dir + "instances.txt", "w"){|file|
	file.puts "0"
}

#=== Empty instance feature file
File.open(output_dir + "instance-features.txt", "w"){|file|
        file.puts "instance_name"
        file.puts "0"
}

#=== Scenario file
File.open(output_dir + "scenario.txt", "w"){|file|
        file.puts "algo = ."
	file.puts "execdir = ."
	file.puts "deterministic = 0"
	file.puts "run_obj = qual"
	file.puts "overall_obj = mean"
	file.puts "cutoff_time = 10000000"
	file.puts "cutoff_length = max"
	file.puts "tunerTimeout = 10000000"
	file.puts "paramfile = #{output_dir}param-file.txt"
	file.puts "instance_file = #{output_dir}instances.txt"
	file.puts "feature_file = #{output_dir}instance-features.txt"
}

#=== Param file
File.open(output_dir + "param-file.txt", "w"){|file|
	for i in 0...num_feat
		file.puts "Col#{i} [#{low[i]}, #{upp[i]}] [#{(low[i]+upp[i])/2}]" 
	end
}

#=== Discretized param file
File.open(output_dir + "param-file-disc.txt", "w"){|file|
	for i in 0...num_feat
		require 'set'
		vals = Set.new
		for x in data.keys
	                vals.add x[i]
        	end
		vals_vec = []
		vals.each{|x| vals_vec << x}
		vals_vec.sort!
		file.puts "Col#{i} {#{vals_vec.join(",")}}[#{vals_vec[0]}]"
	end
}

#=== paramstrings
File.open(output_dir + "runs_and_results-it2.csv", "w"){|runs|
	runs.puts "Run Number,Run History Configuration ID,Instance ID,Response Value (y),Censored?,Cutoff Time Used,Seed,Runtime,Run Length,Run Result Code,Run Quality,SMAC Iteration,SMAC Cumulative Runtime,Run Result,Additional Algorithm Run Data,Wall Clock Time"

	File.open(output_dir + "uniq_configurations-it2.csv", "w"){|file|
		count = 1
		inside_count = 1
		for x in data.keys
			file.print "#{count},"
			for i in 0...num_feat-1
				file.print "#{(x[i]-low[i])/(upp[i]-low[i])},"
			end
			i = num_feat-1
			file.puts "#{(x[i]-low[i])/(upp[i]-low[i])}"	

			ys = data[x]
			if ys.length > 1
				puts x
				puts
				puts ys
			end
			for y in ys
				runs.puts "#{inside_count},#{count},1,#{y},0,10000000,-1,1.0,0,2,#{y},0,0,SAT,fromCSV,0"
				inside_count = inside_count + 1
			end
			count = count + 1
		end
	}
}
system "cp #{output_dir}runs_and_results-it2.csv #{output_dir}paramstrings-it2.txt"

puts "unique values of x: #{data.keys.length}"
puts "Conversion from csv to SMAC state-run folder complete. Output folder: #{output_dir}"
