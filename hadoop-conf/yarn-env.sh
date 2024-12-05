##
## THIS FILE ACTS AS AN OVERRIDE FOR hadoop-env.sh FOR ALL
## WORK DONE BY THE yarn AND RELATED COMMANDS.
##
## Precedence rules:
##
## yarn-env.sh > hadoop-env.sh > hard-coded defaults
##
## YARN_xyz > HADOOP_xyz > hard-coded defaults
##

# Edit by EMR YARN stack
export HADOOP_LOG_DIR="/var/log/taihao-apps/yarn"
export YARN_GC_SETTINGS="-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=${HADOOP_LOG_DIR}/ -XX:+UseConcMarkSweepGC -XX:NewRatio=1 -XX:CMSInitiatingOccupancyFraction=75 -XX:+UseCMSCompactAtFullCollection -XX:CMSMaxAbortablePrecleanTime=1000 -XX:+CMSClassUnloadingEnabled -XX:+DisableExplicitGC -verbose:gc -XX:+PrintGCDetails -XX:+PrintGCTimeStamps -XX:+PrintGCDateStamps -XX:+UseGCLogFileRotation -XX:NumberOfGCLogFiles=5 -XX:GCLogFileSize=128M"
export HADOOP_OPTS="${HADOOP_OPTS} -Dlog4j.configuration=yarn-log4j.properties"

###
# Resource Manager specific parameters
###

# Specify the max heapsize for the ResourceManager.  If no units are
# given, it will be assumed to be in MB.
# This value will be overridden by an Xmx setting specified in either
# HADOOP_OPTS and/or YARN_RESOURCEMANAGER_OPTS.
# Default is the same as HADOOP_HEAPSIZE_MAX
# Edit by EMR YARN stack
export YARN_RESOURCEMANAGER_HEAPSIZE=4247
export YARN_RESOURCEMANAGER_HEAPSIZE_MIN=4247

# Specify the JVM options to be used when starting the ResourceManager.
# These options will be appended to the options specified as HADOOP_OPTS
# and therefore may override any similar flags set in HADOOP_OPTS
#
# Examples for a Sun/Oracle JDK:
# a) override the appsummary log file:
# export YARN_RESOURCEMANAGER_OPTS="-Dyarn.server.resourcemanager.appsummary.log.file=rm-appsummary.log -Dyarn.server.resourcemanager.appsummary.logger=INFO,RMSUMMARY"
#
# b) Set JMX options
# export YARN_RESOURCEMANAGER_OPTS="-Dcom.sun.management.jmxremote=true -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -Dcom.sun.management.jmxremote.port=1026"
#
# c) Set garbage collection logs from hadoop-env.sh
# export YARN_RESOURCE_MANAGER_OPTS="${HADOOP_GC_SETTINGS} -Xloggc:${HADOOP_LOG_DIR}/gc-rm.log-$(date +'%Y%m%d%H%M')"
#
# d) ... or set them directly
# export YARN_RESOURCEMANAGER_OPTS="-verbose:gc -XX:+PrintGCDetails -XX:+PrintGCTimeStamps -XX:+PrintGCDateStamps -Xloggc:${HADOOP_LOG_DIR}/gc-rm.log-$(date +'%Y%m%d%H%M')"
#
# e) Enable ResourceManager audit logging
# export YARN_RESOURCEMANAGER_OPTS="-Drm.audit.logger=INFO,RMAUDIT"
#
#
# Edit by EMR YARN stack
export YARN_RESOURCEMANAGER_OPTS="${YARN_GC_SETTINGS} -Xloggc:${HADOOP_LOG_DIR}/resourcemanager-gc.log"
if [[ -n "${YARN_RESOURCEMANAGER_HEAPSIZE_MIN}" ]]; then
  if [[ "${YARN_RESOURCEMANAGER_HEAPSIZE_MIN}" =~ ^[0-9]+$ ]]; then
    YARN_RESOURCEMANAGER_HEAPSIZE_MIN="${YARN_RESOURCEMANAGER_HEAPSIZE_MIN}m"
  fi
  export YARN_RESOURCEMANAGER_OPTS="-Xms${YARN_RESOURCEMANAGER_HEAPSIZE_MIN} ${YARN_RESOURCEMANAGER_OPTS}"
fi

###
# Node Manager specific parameters
###

# Specify the max heapsize for the NodeManager.  If no units are
# given, it will be assumed to be in MB.
# This value will be overridden by an Xmx setting specified in either
# HADOOP_OPTS and/or YARN_NODEMANAGER_OPTS.
# Default is the same as HADOOP_HEAPSIZE_MAX.
# Edit by EMR YARN stack
export YARN_NODEMANAGER_HEAPSIZE=1638
export YARN_NODEMANAGER_HEAPSIZE_MIN=1638

# Specify the JVM options to be used when starting the NodeManager.
# These options will be appended to the options specified as HADOOP_OPTS
# and therefore may override any similar flags set in HADOOP_OPTS
#
# a) Enable NodeManager audit logging
# export YARN_NODEMANAGER_OPTS="-Dnm.audit.logger=INFO,NMAUDIT"
#
# See ResourceManager for some examples
#
# Edit by EMR YARN stack
export YARN_NODEMANAGER_OPTS="${YARN_GC_SETTINGS} -Xloggc:${HADOOP_LOG_DIR}/nodemanager-gc.log"
if [[ -n "${YARN_NODEMANAGER_HEAPSIZE_MIN}" ]]; then
  if [[ "${YARN_NODEMANAGER_HEAPSIZE_MIN}" =~ ^[0-9]+$ ]]; then
    YARN_NODEMANAGER_HEAPSIZE_MIN="${YARN_NODEMANAGER_HEAPSIZE_MIN}m"
  fi
  export YARN_NODEMANAGER_OPTS="-Xms${YARN_NODEMANAGER_HEAPSIZE_MIN} ${YARN_NODEMANAGER_OPTS}"
fi

###
# TimeLineServer specific parameters
###

# Specify the max heapsize for the timelineserver.  If no units are
# given, it will be assumed to be in MB.
# This value will be overridden by an Xmx setting specified in either
# HADOOP_OPTS and/or YARN_TIMELINESERVER_OPTS.
# Default is the same as HADOOP_HEAPSIZE_MAX.
# Edit by EMR YARN stack
export YARN_TIMELINESERVER_HEAPSIZE=512
export YARN_TIMELINESERVER_HEAPSIZE_MIN=512

# Specify the JVM options to be used when starting the TimeLineServer.
# These options will be appended to the options specified as HADOOP_OPTS
# and therefore may override any similar flags set in HADOOP_OPTS
#
# See ResourceManager for some examples
#
# Edit by EMR YARN stack
export YARN_TIMELINESERVER_OPTS="${YARN_GC_SETTINGS} -Xloggc:${HADOOP_LOG_DIR}/timelineserver-gc.log -XX:MaxDirectMemorySize=1g -noverify -javaagent:/opt/apps/TAIHAODOCTOR/taihaodoctor-current/emr-agent/btrace-agent.jar=libs=mr,config=history"
if [[ -n "${YARN_TIMELINESERVER_HEAPSIZE_MIN}" ]]; then
  if [[ "${YARN_TIMELINESERVER_HEAPSIZE_MIN}" =~ ^[0-9]+$ ]]; then
    YARN_TIMELINESERVER_HEAPSIZE_MIN="${YARN_TIMELINESERVER_HEAPSIZE_MIN}m"
  fi
  export YARN_TIMELINESERVER_OPTS="-Xms${YARN_TIMELINESERVER_HEAPSIZE_MIN} ${YARN_TIMELINESERVER_OPTS}"
fi

###
# Web App Proxy Server specifc parameters
###

# Specify the max heapsize for the web app proxy server.  If no units are
# given, it will be assumed to be in MB.
# This value will be overridden by an Xmx setting specified in either
# HADOOP_OPTS and/or YARN_PROXYSERVER_OPTS.
# Default is the same as HADOOP_HEAPSIZE_MAX.
# Edit by EMR YARN stack
export YARN_PROXYSERVER_HEAPSIZE=640
export YARN_PROXYSERVER_HEAPSIZE_MIN=640

# Specify the JVM options to be used when starting the proxy server.
# These options will be appended to the options specified as HADOOP_OPTS
# and therefore may override any similar flags set in HADOOP_OPTS
#
# See ResourceManager for some examples
#
# Edit by EMR YARN stack
export YARN_PROXYSERVER_OPTS="${YARN_GC_SETTINGS} -Xloggc:${HADOOP_LOG_DIR}/proxyserver-gc.log"
if [[ -n "${YARN_PROXYSERVER_HEAPSIZE_MIN}" ]]; then
  if [[ "${YARN_PROXYSERVER_HEAPSIZE_MIN}" =~ ^[0-9]+$ ]]; then
    YARN_PROXYSERVER_HEAPSIZE_MIN="${YARN_PROXYSERVER_HEAPSIZE_MIN}m"
  fi
  export YARN_PROXYSERVER_OPTS="-Xms${YARN_PROXYSERVER_HEAPSIZE_MIN} ${YARN_PROXYSERVER_OPTS}"
fi

# Edit by EMR YARN stack

