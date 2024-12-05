##
## THIS FILE ACTS AS AN OVERRIDE FOR hadoop-env.sh FOR ALL
## WORK DONE BY THE mapred AND RELATED COMMANDS.
##
## Precedence rules:
##
## mapred-env.sh > hadoop-env.sh > hard-coded defaults
##
## MAPRED_xyz > HADOOP_xyz > hard-coded defaults
##

# Edit by EMR YARN stack
export HADOOP_LOG_DIR="/var/log/taihao-apps/yarn"
export MAPRED_GC_SETTINGS="-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=${HADOOP_LOG_DIR}/ -XX:+UseConcMarkSweepGC -XX:NewRatio=1 -XX:CMSInitiatingOccupancyFraction=75 -XX:+UseCMSCompactAtFullCollection -XX:CMSMaxAbortablePrecleanTime=1000 -XX:+CMSClassUnloadingEnabled -XX:+DisableExplicitGC -verbose:gc -XX:+PrintGCDetails -XX:+PrintGCTimeStamps -XX:+PrintGCDateStamps -XX:+UseGCLogFileRotation -XX:NumberOfGCLogFiles=5 -XX:GCLogFileSize=128M"
export HADOOP_OPTS="${HADOOP_OPTS} -Dlog4j.configuration=yarn-log4j.properties"

###
# Job History Server specific parameters
###

# Specify the max heapsize for the JobHistoryServer.  If no units are
# given, it will be assumed to be in MB.
# This value will be overridden by an Xmx setting specified in HADOOP_OPTS,
# and/or MAPRED_HISTORYSERVER_OPTS.
# Default is the same as HADOOP_HEAPSIZE_MAX.
# Edit by EMR YARN stack
export HADOOP_JOB_HISTORYSERVER_HEAPSIZE=640

# Specify the JVM options to be used when starting the HistoryServer.
# These options will be appended to the options specified as HADOOP_OPTS
# and therefore may override any similar flags set in HADOOP_OPTS
# Edit by EMR YARN stack, compatible for Hadoop 2.X
if [ "" != "" ]; then
  export HADOOP_JOB_HISTORYSERVER_OPTS="${MAPRED_GC_SETTINGS} "
  export HADOOP_MAPRED_ROOT_LOGGER=INFO,RFA
fi
if [ "" != "-Xloggc:${HADOOP_LOG_DIR}/jobhistory-gc.log" ]; then
  export MAPRED_HISTORYSERVER_OPTS="${MAPRED_GC_SETTINGS} -Xloggc:${HADOOP_LOG_DIR}/jobhistory-gc.log"
fi

# Edit by EMR YARN stack

