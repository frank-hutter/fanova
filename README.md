Fanova
======

Functional ANOVA: an implementation of the ICML 2014 paper "An Efficient Approach for Assessing Hyperparameter Importance" by Frank Hutter, Holger Hoos and Kevin Leyton-Brown.

Requirements
------------
Fanova requires [Java 7](https://jdk7.java.net/download.html).

Installation
------------

### Pip


```
pip install pyfanova
```


### Manually

```
python setup.py install
```
 
Example usage
-------------

To run the examples, just download the [data](fanova/example/online_lda.tar.gz) and start the python console.
We can then import Fanova and start it by typing
```python
>>> from pyfanova.fanova import Fanova
>>> f = Fanova("example/online_lda")
```
This creates a new Fanova object and fits the Random Forest on the specified data set. To compute now the marginal of the first parameter type:
```python
>>> f.get_marginal(0)
5.44551614362
```
Fanova also allows to specify parameters by their names.
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
If we want to compute the mean and standard deviation of a parameter for a certain value, we can use
```python
>>> f.get_marginal_for_value("Col0", 0.1)
(1956.6644432031385, 110.58740682895211)
```
To visualize the single and pairwise marginals, we have to create a visualizer object first
```python
>>> from pyfanova.visualizer import Visualizer
>>> vis = Visualizer(f)
```
We can then plot single marginals by 
```python
>>> plot = vis.plot_marginal("Col1")
>>> plot.show()
```
what should look like this
![example plots](https://raw.githubusercontent.com/aaronkl/fanova/master/fanova/example/online_lda/Col1.png)

The same can been done for pairwise marginals
```python
>>> vis.plot_pairwise_marginal("Col0", "Col2")
```

![example plots](https://raw.githubusercontent.com/aaronkl/fanova/master/fanova/example/online_lda/pairwise.png)


At last, all plots can be created together and stored in a directory with
```python
>>> vis.create_all_plots("./plots/")
```

If your data is stored in csv file, you can run Fanova with
```python
>>> from pyfanova.fanova_from_csv import FanovaFromCSV
>>> f = FanovaFromCSV("/path_to_data/data.csv")
```
Please make sure, that your csv file has the form

| X0     | X1     | ...    |  Y     |
|:-------|:------:|:------:| ------:|
| 0.1    | 0.2    | ...    | 0.3    |
| 0.3    | 0.4    | ...    | 0.6    |


It is also possible to run Fanova on data colleted by [HPOlib](https://github.com/automl/HPOlib)
```python
>>> from pyfanova.fanova_from_hpolib import FanovaFromHPOLib
>>> f = FanovaFromHPOLib("/path_to_hpolib_data/")
```
