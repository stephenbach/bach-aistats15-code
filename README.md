# bach-aistats15-code
Code for "Unifying Local Consistency and MAX SAT Relaxations for Scalable Inference with Rounding Guarantees." Stephen H. Bach, Bert Huang, and Lise Getoor. Artificial Intelligence and Statistics (AISTATS) 2015

## Requirements
* [Maven 3](http://maven.apache.org/) - Used to install PSL and manage dependencies.
* [MPLP](http://cs.nyu.edu/~dsontag/code/mplp_ver2.tgz) - Dual decomposition implementation, see the [MPLP README](http://cs.nyu.edu/~dsontag/code/README_v2.html).
    * Compile and place a copy of `solver` on the executable path.
    * Edit line 27 of `cycle_tighten_main.cpp` to define `MAX_TIGHT_ITERS` as 0. Recompile and place of copy of `solver` on the executable path with the name `solver-nocycles`.

## Running the experiments

Execute `run.sh`.