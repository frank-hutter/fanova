import csv
import logging
import os
import shutil
import numpy as np
from pyfanova.fanova import Fanova


class FanovaFromCSV(Fanova):

    def __init__(self, csv_file, **kwargs):

        #TODO: use python tmpdir 
        self._scenario_dir = "tmp_smac_files"
        if not os.path.isdir(self._scenario_dir):
            os.mkdir(self._scenario_dir)

        X, y = self._read_csv_file(csv_file)
        self._write_instances_file()
        self._write_runs_and_results_file(y)
        self._write_param_file()
        self._write_paramstrings_file(X)
        self._write_scenario_file()

        logging.debug("Write temporary smac files in " + self._scenario_dir)
        super(FanovaFromCSV, self).__init__(self._scenario_dir)

    def __del__(self):
        shutil.rmtree(self._scenario_dir)
        super(FanovaFromCSV, self).__del__()

    def _write_scenario_file(self):

        fh = open(os.path.join(self._scenario_dir, "scenario.txt"), "w")

        fh.write("algo = .\n")
        fh.write("execdir = .\n")
        fh.write("deterministic = 0\n")
        fh.write("run_obj = qual\n")
        fh.write("overall_obj = mean\n")
        fh.write("cutoff_time = 1\n")
        fh.write("cutoff_length = 0\n")
        fh.write("tunerTimeout = 0\n")
        fh.write("paramfile = .\n")
        fh.write("instance_file = .\n")
        fh.write("test_instance_file = .\n")

        fh.close()

    def _write_instances_file(self):

        fh = open(os.path.join(self._scenario_dir, "instances.txt"), "w")
        fh.write(".")
        fh.close()

    def _write_runs_and_results_file(self, values):

        fh = open(os.path.join(self._scenario_dir, "runs_and_results.csv"), "wb")
        writer = csv.writer(fh)
        writer.writerow(("Run Number", "Run History Configuration ID", "Instance ID", "Response Value (y)", "Censored?", "Cutoff Time Used",
                                      "Seed", "Runtime", "Run Length", "Run Result Code", "Run Quality", "SMAC Iteration", "SMAC Cumulative Runtime", "Run Result"))

        for i in xrange(0, len(values)):
            line = (i, i, 1, 0, 0, 0, 1, 0, 0, 0, values[i], 0, 0, "SAT")
            writer.writerow(line)

        fh.close()

    def _write_param_file(self):

        fh = open(os.path.join(self._scenario_dir, "param-file.txt"), "w")
        for i in xrange(0, self._num_of_params):
            param_string = "X" + str(i) + " [0, 1] [0.1]\n"
            fh.write(param_string)

        fh.close()

    def _write_paramstrings_file(self, params):

        fh = open(os.path.join(self._scenario_dir, "paramstrings.txt"), "w")
        for i in xrange(0, params.shape[0]):
            line = str(i) + ": " + "X0='" + str(params[i][0]) + "', X1='" + str(params[i][1]) + "'\n"
            fh.write(line)
        fh.close()

    def _read_csv_file(self, filename):

        fh = open(filename, "r")
        reader = csv.reader(fh)

        #Count how many data points are in the csv file
        number_of_points = 0
        for line in reader:
            number_of_points += 1

        fh.seek(0)
        #Count the dimension of the the data points
        line = fh.readline()
        s = line.split(',')
        self._num_of_params = len(s) - 1
        logging.debug("number of parameters: " + str(self._num_of_params))

        X = np.zeros([number_of_points, self._num_of_params])
        y = np.zeros([number_of_points])

        fh.seek(0)
        rownum = 0
        for line in reader:
            X[rownum][0] = line[0]
            X[rownum][1] = line[1]
            y[rownum] = line[2]
            rownum += 1

        fh.close()

        return X, y
