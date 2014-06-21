import os
import logging
import sys
import socket
import subprocess
from subprocess import Popen
from pkg_resources import resource_filename
from pyfanova.fanova_remote import FanovaRemote
from pyfanova.config_space import ConfigSpace


def check_java_version():
    import re
    from subprocess import STDOUT, check_output
    out = check_output(["java", "-version"], stderr=STDOUT).split("\n")
    if len(out) < 1:
        print "Failed checking Java version. Make sure Java version 7 or greater is installed."
        return False
    m = re.match('java version "\d+.(\d+)..*', out[0])
    if m is None or len(m.groups()) < 1:
        print "Failed checking Java version. Make sure Java version 7 or greater is installed."
        return False
    java_version = int(m.group(1))
    if java_version < 7:
        error_msg = "Found Java version %d, but Java version 7 or greater is required." % java_version
 
        raise RuntimeError(error_msg)
check_java_version()


class Fanova(object):

    def __init__(self, scenario_dir, num_trees=30, split_min=10, seed=42,
        fanova_lib_folder=None,
        fanova_class_folder=None):
        """

            fanova_class_folder: used for development purposes only.
        """

        self._remote = FanovaRemote()

        if fanova_lib_folder is None:
            self._fanova_lib_folder = resource_filename("pyfanova", 'fanova')
        else:
            self._fanova_lib_folder = fanova_lib_folder
        #"/home/domhant/Projects/automl-fanova/fanova/bin"
        self._fanova_class_folder = fanova_class_folder
        self._num_trees = num_trees
        self._split_min = split_min
        self._seed = seed
        self._scenario_dir = scenario_dir

        self._start_fanova()
        logging.debug("Now connecting to fanova...")
        if self._start_connection():
            self._config_space = ConfigSpace(self._remote)

            param_names = self._config_space.get_parameter_names()
            self.param_name2dmin = dict(zip(param_names,range(len(param_names))))
        else:
            stdout, stderr = self._process.communicate()
            error_msg = "failed starting fanova "
            if stdout is not None:
                error_msg += stdout
            if stderr is not None:
                error_msg += stderr
            raise RuntimeError(error_msg)

    def __del__(self):
        if self._remote.connected:
            self._remote.send("die")
            self._remote.disconnect()

    def get_marginal(self, param):
        #TODO: Write doc string
        dim = -1
        if type(param) == int:
            dim = param
        else:
            assert param in self.param_name2dmin, "param %s not known" % param
            dim = self.param_name2dmin[param]
        if dim == -1:
            logging.error("Parameter not found")

        self._remote.send("get_marginal:" + str(dim))
        result = float(self._remote.receive())

        return result

    def get_pairwise_marginal(self, param1, param2):
        #TODO: Write doc string

        dim1 = -1
        dim2 = -1
        if type(param1) == int and type(param2) == int:
            dim1 = param1
            dim2 = param2
        else:
            assert param1 in self.param_name2dmin, "param %s not known" % param1
            assert param2 in self.param_name2dmin, "param %s not known" % param2
            dim1 = self.param_name2dmin[param1]
            dim2 = self.param_name2dmin[param2]
        if dim1 == -1 or dim2 == -1:
            logging.error("Parameters not found")

        self._remote.send("get_pairwise_marginal:" + str(dim1) + ":" + str(dim2))
        result = float(self._remote.receive())
        return result

    def get_marginal_for_value(self, param, value):
        assert value >= 0 and value <= 1
        return self._get_marginal_for_value(param, value)

    def get_categorical_marginal_for_value(self, param, value):
        """
            param: parameter name
            value: 0-indexed categorical value
        """
        #TODO: check value to be in the bounds
        return self._get_marginal_for_value(param, value)

    def _get_marginal_for_value(self, param, value):
        dim = self._convert_param2dim(param)

        self._remote.send("get_marginal_for_value:%d:%f" % (dim, value))
        result = self._remote.receive().split(';')
        return (float(result[0]), float(result[1]))

    def _get_marginal_for_value_pair(self, param1, param2, value1, value2):
        dim1 = self._convert_param2dim(param1)
        dim2 = self._convert_param2dim(param2)

        self._remote.send("get_marginal_for_value_pair:%d:%d:%f:%f" % (dim1, dim2, value1, value2))
        result = self._remote.receive().split(';')
        return (float(result[0]), float(result[1]))

    def _convert_param2dim(self, param):
        dim = -1
        if type(param) == int:
            dim = param
        else:
            assert param in self.param_name2dmin, "param %s not known" % param
            dim = self.param_name2dmin[param]
        return dim

    def get_config_space(self):
        return self._config_space

    def get_all_pairwise_marginals(self):
        param_names = self._config_space.get_parameter_names()
        pairwise_marginals = []
        for i, param_name1 in enumerate(param_names):
            for j, param_name2 in enumerate(param_names):
                if i == j:
                    continue
                pairwise_marginal_performance = self.get_pairwise_marginal(i, j)
                pairwise_marginals.append((pairwise_marginal_performance, param_name1, param_name2))
        return pairwise_marginals

    def get_most_important_pairwise_marginals(self, n=10):
        pairwise_marginal_performance = self.get_all_pairwise_marginals()
        pairwise_marginal_performance = sorted(pairwise_marginal_performance, reverse=True)
        important_pairwise_marginals = [(p1, p2) for marginal, p1, p2  in pairwise_marginal_performance[:n]]
        return important_pairwise_marginals

    def print_all_marginals(self, max_num=20, pairwise=True):
        """
        """
        param_names = self._config_space.get_parameter_names()
        num_params = len(param_names)
        
        main_marginal_performances = [self.get_marginal(i) for i in range(num_params)]
        labelled_performances = []
        for marginal, param_name in zip(main_marginal_performances, param_names):
            labelled_performances.append((marginal, "%.2f%% due to main effect: %s" % (marginal, param_name)))
        print "Sum of fractions for main effects %.2f%%" % (sum(main_marginal_performances))

        if pairwise:
            pairwise_marginal_performance = self.get_all_pairwise_marginals()
            sum_of_pairwise_marginals = 0
            for pairwise_marginal_performance, param_name1, param_name2 in pairwise_marginal_performance:
                    sum_of_pairwise_marginals += pairwise_marginal_performance
                    label = "%.2f%% due to interaction: %s x %s" % (pairwise_marginal_performance, param_name1, param_name2)
                    labelled_performances.append((pairwise_marginal_performance, label))
            print "Sum of fractions for pairwise interaction effects %.2f%%" % (pairwise_marginal_performance)

        sorted_performances = sorted(labelled_performances)
        if max_num is not None:
            sorted_performances = sorted_performances[-max_num:]
        for marginal, label in sorted_performances:
            print label

    def _start_fanova(self):
        cmds = ["java",
            "-Xmx1024m",
            "-cp",
            ":".join(self._fanova_classpath()),
            "net.aclib.fanova.FAnovaExecutor",
            "--restoreScenario", self._scenario_dir,
            "--seed", str(self._seed),
            "--rf-num-trees", str(self._num_trees),
            "--split-min", str(self._split_min),
            "--ipc-port", str(self._remote.port)
            ]
        #TODO: check that fanova was started successfully and wasn't killed
        with open(os.devnull, "w") as fnull:
            #logging.debug(" ".join(cmds))
            if logging.getLogger().level <= logging.DEBUG:
                self._process = Popen(cmds, stdout=sys.stdout, stderr=sys.stdout)
            else:
                self._process = Popen(cmds, stdout=fnull, stderr=sys.stdout)#stdout=fnull, stderr=fnull)

    def _start_connection(self):
        logging.debug("starting connection...")
        while self._process.poll() is None:
            #while the process is still running we keep on trying to accept the connection
            TIMEOUT = 5
            try:
                self._remote.connect(TIMEOUT)
                logging.debug("connected")
                return True
            except socket.timeout:
                logging.debug("timeout")
                pass
        logging.debug("failed starting fanova")
        #the process terminated without ever instantiating a connection...something went wrong
        return False

    def _fanova_classpath(self):
        classpath = [fname for fname in os.listdir(self._fanova_lib_folder) if fname.endswith(".jar")]
        classpath = [os.path.join(self._fanova_lib_folder, fname) for fname in classpath]
        classpath = [os.path.abspath(fname) for fname in classpath]
        if self._fanova_class_folder is not None:
            classpath.append(os.path.abspath(self._fanova_class_folder))
        logging.debug(classpath)
        return classpath
