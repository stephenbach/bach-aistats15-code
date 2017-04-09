#!/bin/sh

echo "Compiling..."
mvn compile > /dev/null
mvn dependency:build-classpath -Dmdep.outputFile=classpath.out > /dev/null
mkdir results > /dev/null

echo "Starting experiment..."
for method in "hlmrf" "mplp" "mplp-cycles";
do
	for size in "10k" "20k" "30k" "40k" "50k"
	do
		for i in `seq 1 5`;
		do
			java -Xmx8g -cp ./target/classes:`cat classpath.out` edu.umd.cs.bachaistats15a.SolveMPE $method $size $i &> results/$method-$size-$i.out
			echo "Completed $method $size #$i"
		done
	done
done

