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

To run the examples, just start Python and type the following commands
```python
>>> from fanova import Fanova
>>> f = Fanova("/home/kleinaa/devel/git/fanova/fanova/example/online_lda")
```

To compute the marginal of the first parameter type:
```python
>>> f.get_marginal(0)
5.44551614362
```

