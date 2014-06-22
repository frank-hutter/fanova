'''
Created on Jun 17, 2014

@author: Aaron Klein
'''

import logging


class ConfigSpace(object):

    def __init__(self, remote):
        self._remote = remote

    def get_parameter_names(self):

        self._remote.send("get_parameter_names")
        result = self._remote.receive().strip()
        if len(result) > 0:
            names = result.split(';')
        else:
            names = []
            logging.error("No parameters found")
        return names

    def get_categorical_parameters(self):
        self._remote.send("get_categorical_parameters")
        result = self._remote.receive().strip()
        if len(result) > 0:
            names = result.split(';')
        else:
            names = []
            logging.error("No categorical parameters found")
        return names

    def get_continuous_parameters(self):
        self._remote.send("get_continuous_parameters")
        result = self._remote.receive().strip()
        if len(result) > 0:
            names = result.split(';')
        else:
            names = []
            logging.error("No categorical parameters found")
        return names

    def get_integer_parameters(self):
        self._remote.send("get_integer_parameters")
        result = self._remote.receive().strip()
        if len(result) > 0:
            names = result.split(';')
        else:
            names = []
            logging.error("No categorical parameters found")
        return names

    def get_categorical_size(self, parameter):
        self._remote.send("get_categorical_size:%s" % parameter)
        result = self._remote.receive().strip()
        if len(result) > 0:
            return int(result)
        else:
            return 0

    def get_categorical_values(self, param):
        self._remote.send("get_categorical_values:%s" % param)
        result = self._remote.receive().strip()
        if len(result) > 0:
            values = result.split(';')
        else:
            values = []
            logging.error("No categorical values found for parameter %s" % param)
        return values

    def get_upper_bound(self, parameter):
        return self.unormalize_value(parameter, 1)

    def get_lower_bound(self, parameter):
        return self.unormalize_value(parameter, 0)

    def unormalize_value(self, parameter, value):
        assert value <= 1 and value >= 0

        self._remote.send("unormalize_value:" + str(parameter) + ":" + str(value))
        value = self._remote.receive()
        if value != "":
            return float(value)
        else:
            logging.error("Parameter not found")
            raise ValueError("Parameter not found")
