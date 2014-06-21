'''
Created on Jun 16, 2014

@author: Aaron Klein
'''

from pyfanova.fanova import Fanova
from pyfanova.visualizer import Visualizer

f = Fanova("/home/kleinaa/devel/git/fanova/fanova/example/online_lda")

print f.get_marginal(0)
print f.get_marginal("Col0")
f.get_pairwise_marginal(0, 1)
f.get_marginal_for_value(param, value)
vis = Visualizer(f)
vis.plot_pairwise_marginal(0, 2)
vis.create_all_plots("/home/kleinaa/plots/")
p = vis.plot_marginal(0)

vis.plot_marginal(2)
