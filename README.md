Fanova
======

Functional ANOVA: an implementation of the ICML 2014 paper "An Efficient Approach for Assessing Hyperparameter Importance" by Frank Hutter, Holger Hoos and Kevin Leyton-Brown.

Requirements
------------


Installation
------------

### Pip


```
pip install fanova
```


### Manually

```
python setup.py install
```
 
Example usage
-------------

To run the examples, just start Python console
Fanova can be importet and startet with the command
```python
>>> from fanova import Fanova
>>> f = Fanova("example/online_lda")
```
this creates a new Fanova object. To compute now the marginal of the first parameter type:
```python
>>> f.get_marginal(0)
5.44551614362
```
Parameters can also be specified by their names:
```python
>>> f.get_marginal("Col0")
5.44551614362
```
Pairwise marginals of two parameters can be computed with the command
```python
>>>  f.get_pairwise_marginal(0, 1)
0.9370525790628655
```
Again the same can been done by specifing names instead of indices
```python
>>> f.get_pairwise_marginal("Col0","Col1")
0.9370525790628655
```
