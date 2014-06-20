'''
Created on Jun 16, 2014

@author: Aaron Klein
'''

import logging
from fanova import Fanova
from fanova_from_csv import FanovaFromCSV
from visualizer import Visualizer

logging.getLogger().setLevel(logging.DEBUG)
#f = FanovaFromCSV("/home/kleinaa/devel/git/fanova/fanova/example/csv-example/test_data.csv")
f = Fanova("/home/kleinaa/devel/git/fanova/fanova/example/online_lda")
#logging.debug(f.get_marginal("Col0"))
logging.debug(f.print_all_marginals())
#
# vis = Visualizer(f)
# #vis.create_all_plots("/home/kleinaa/plots/")
# vis.plot_marginal(0)
# vis.plot_marginal(2)
#vis.plot_marginal_of_parameter(2)
