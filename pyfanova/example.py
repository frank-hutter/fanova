'''
Created on Jun 16, 2014

@author: Aaron Klein
'''

from pyfanova.fanova import Fanova

f = Fanova("/home/kleinaa/Desktop/state-run0")
print f.get_marginal(0)