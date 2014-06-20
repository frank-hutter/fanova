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
logging.debug(f._get_marginal_for_value_pair(0, 1, 0.1, 0.1))


vis = Visualizer(f)
vis.plot_pairwise_marginal(0, 2)
# #vis.create_all_plots("/home/kleinaa/plots/")
# vis.plot_marginal(0)
# vis.plot_marginal(2)
#vis.plot_marginal_of_parameter(2)
