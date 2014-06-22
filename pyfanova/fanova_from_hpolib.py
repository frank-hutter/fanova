import logging
import csv
import os
import shutil
import numpy as np
import pickle
from pyfanova.fanova import Fanova
from pyfanova.fanova_remote import FanovaRemote


class FanovaFromHPOLib(Fanova):

    def __init__(self, param_file, pkls, **kwargs):
        """
            param_file: parameters in smac format
            pkls: HPOLib result pickle files.
        """
        assert isinstance(pkls, list), "pkls needs to be a list of pickle files."
        assert os.path.exists(param_file), "param_file %s does not exist" % param_file

        self.trials = []
        for pkl in pkls:
            self._load_pkl(pkl)
        self._param_file = param_file

        self._clean_trials()

        #TODO: use python tempdirs for being platform independent
        self._scenario_dir = "tmp_smac_files"
        if not os.path.isdir(self._scenario_dir):
            os.mkdir(self._scenario_dir)

        self._write_instances_file()
        self._write_runs_and_results_file()
        self._write_param_file()
        self._write_paramstrings_file()
        self._write_scenario_file()

        logging.info("# data points: %d" % len(self.trials))

        #logging.debug("Write temporary smac files in " + self._scenario_dir)
        super(FanovaFromHPOLib, self).__init__(self._scenario_dir, **kwargs)

    def __del__(self):
        shutil.rmtree(self._scenario_dir)
        super(FanovaFromHPOLib, self).__del__()

    def _load_pkl(self, pkl):
        result = pickle.load(open(pkl))
        self.trials.extend(result["trials"])
        """
            each trial has a result and params field
        """

    def _clean_trials(self):
        trials = []
        for i in xrange(0, len(self.trials)):
            result = self.trials[i]["result"]
            if not np.isfinite(result):
                logging.warning("skipping result, that's not finite...")
                continue
            else:
                trials.append(self.trials[i])
        self.trials = trials

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

    def _write_runs_and_results_file(self):

        fh = open(os.path.join(self._scenario_dir, "runs_and_results.csv"), "wb")
        writer = csv.writer(fh)
        writer.writerow(("Run Number", "Run History Configuration ID", "Instance ID", "Response Value (y)", "Censored?", "Cutoff Time Used",
                                      "Seed", "Runtime", "Run Length", "Run Result Code", "Run Quality", "SMAC Iteration", "SMAC Cumulative Runtime", "Run Result"))

        for i in xrange(0, len(self.trials)):
            result = self.trials[i]["result"]
            line = (i, i, 1, 0, 0, 0, 1, 0, 0, 0, result, 0, 0, "SAT")
            writer.writerow(line)

        fh.close()

    def _write_param_file(self):

        fh = open(os.path.join(self._scenario_dir, "param-file.txt"), "w")
        shutil.copyfile(self._param_file,
            os.path.join(self._scenario_dir, "param-file.txt"))


    def _write_paramstrings_file(self):
        fh = open(os.path.join(self._scenario_dir, "paramstrings.txt"), "w")
        for i in xrange(0, len(self.trials)):
            params = self.trials[i]["params"]
            clean_params = {}
            for param_name, param_value in params.iteritems():
                #FIX of a hpolib bug, where the parameter names in the pkl contain a - infront of their name
                if param_name[0] == '-':
                    param_name = param_name[1:]
                clean_params[param_name] = param_value
            param_list = ["%s='%s'" % (key, value) for key, value in clean_params.iteritems()]
            line = "%d: %s\n" % (i, ", ".join(param_list))
            fh.write(line)
        fh.close()



