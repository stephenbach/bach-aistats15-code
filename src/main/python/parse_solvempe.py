#!/usr/bin/env python

import re

def main():

	data_dir = "results/"
	sizes = ["10k", "20k", "30k", "40k", "50k"]
	num_runs = 5

	vars = []
	clauses = []
	lp_opts = []
	expected_scores = []
	hlmrf_scores = []
	mplp_scores = []
	mplp_cycles_scores = []

	vars_pattern = re.compile("^Number of variables: (\\d+)")
	clauses_pattern = re.compile("^Number of clauses: (\\d+)")
	lp_opt_pattern = re.compile("^Current score: (\\d+\\.\\d+)")
	expected_score_pattern = re.compile("^Expected score: (\\d+\\.\\d+)")
	score_pattern = re.compile("^Final score: (\\d+\\.\\d+)")

	for size in sizes:
		for run in range(1, num_runs + 1):
			for line in open(data_dir + 'hlmrf-' + size + '-' + str(run) + '.out'):
				match = vars_pattern.match(line.strip())
				if match is not None:
					vars.append(match.group(1))
					continue

				match = clauses_pattern.match(line.strip())
				if match is not None:
					clauses.append(match.group(1))
					continue

				match = lp_opt_pattern.match(line.strip())
				if match is not None:
					lp_opts.append(match.group(1))
					continue

				match = expected_score_pattern.match(line.strip())
				if match is not None:
					expected_scores.append(match.group(1))
					continue

				match = score_pattern.match(line.strip())
				if match is not None:
					hlmrf_scores.append(match.group(1))
					continue

			for line in open(data_dir + 'mplp-' + size + '-' + str(run) + '.out'):
				match = score_pattern.match(line.strip())
				if match is not None:
					mplp_scores.append(match.group(1))

			for line in open(data_dir + 'mplp-cycles-' + size + '-' + str(run) + '.out'):
				match = score_pattern.match(line.strip())
				if match is not None:
					mplp_cycles_scores.append(match.group(1))

	print "Num variables"
	for i in range(len(sizes)):
		print ", ".join(vars[i*num_runs:(i+1)*num_runs])

	print
	print "Num clauses"
	for i in range(len(sizes)):
		print ", ".join(clauses[i*num_runs:(i+1)*num_runs])

	print
	print "LP Opt"
	for i in range(len(sizes)):
		print ", ".join(lp_opts[i*num_runs:(i+1)*num_runs])

	print
	print "HLMRF Expected"
	for i in range(len(sizes)):
		print ", ".join(expected_scores[i*num_runs:(i+1)*num_runs])

	print
	print "HLMRF Rounded"
	for i in range(len(sizes)):
		print ", ".join(hlmrf_scores[i*num_runs:(i+1)*num_runs])

	print
	print "MPLP No Cycles"
	for i in range(len(sizes)):
		print ", ".join(mplp_scores[i*num_runs:(i+1)*num_runs])

	print
	print "MPLP Cycles"
	for i in range(len(sizes)):
		print ", ".join(mplp_cycles_scores[i*num_runs:(i+1)*num_runs])

if __name__ == '__main__':
	main()
