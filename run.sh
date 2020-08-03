#!/bin/bash --login
#$ -l h_rt=86400
#$ -o ./logfile_$JOB_NAME_$TASK_ID.log
#$ -j y
#$ -m a
#$ -cwd
#$ -pe mp 4
#$ -l mem_free=16G

date
hostname

classpath="matsim-duesseldorf-1.0-SNAPSHOT.jar"

echo "***"
echo "classpath: $classpath"
echo "***"

# main
main="org.matsim.run.RunDuesseldorfScenario"

let workerId=$((${SGE_TASK_ID:-1} - 1))
let numWorker=${SGE_TASK_LAST:-1}

# arguments
arguments=""

command="java -cp $classpath $JAVA_OPTS $main $arguments"


echo ""
echo "command is $command"

echo ""
module add java/11
java -version

$command