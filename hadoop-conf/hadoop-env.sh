##
## THIS FILE ACTS AS THE MASTER FILE FOR ALL HADOOP PROJECTS.
## SETTINGS HERE WILL BE READ BY ALL HADOOP COMMANDS.  THEREFORE,
## ONE CAN USE THIS FILE TO SET YARN, HDFS, AND MAPREDUCE
## CONFIGURATION OPTIONS INSTEAD OF xxx-env.sh.
##
## Precedence rules:
##
## {yarn-env.sh|hdfs-env.sh} > hadoop-env.sh > hard-coded defaults
##
## {YARN_xyz|HDFS_xyz} > HADOOP_xyz > hard-coded defaults
##

# Some parts of the shell code may do special things dependent upon
# the operating system.  We have to set this here. See the next
# section as to why....
export HADOOP_OS_TYPE=${HADOOP_OS_TYPE:-$(uname -s)}

if [[ -f /etc/taihao-apps/hadoop-conf/hdfs-env.sh ]]; then
  source /etc/taihao-apps/hadoop-conf/hdfs-env.sh
fi

# The following applies to multiple commands (fs, dfs, fsck, distcp etc)
export HADOOP_CLIENT_OPTS="$HADOOP_CLIENT_OPTS"


# Edit by EMR Hadoop-Common: hadoop-env.sh => HADOOP_CLIENT_BUILDIN_OPTS
# Hadoop 3.x: Only add HEAPSIZE to $HADOOP_CLIENT_OPTS when $HADOOP_HEAPSIZE and $HADOOP_HEAPSIZE_MAX are empty
if [[ -z "${HADOOP_HEAPSIZE}" ]] && [[ -z "${HADOOP_HEAPSIZE_MAX}" ]]; then
  if [[ -n "${EMR_HADOOP_CLIENT_HEAPSIZE_DEFAULT}" ]]; then
    export HADOOP_CLIENT_OPTS=" -Xmx1g $HADOOP_CLIENT_OPTS"
  fi
fi
export JAVA_HOME=/usr/lib/jvm/java-11-openjdk
export PATH=$JAVA_HOME/bin:$PATH

# Edit by EMR Hadoop-Common: hadoop-env.sh => EXTRA_ENVS
export HADOOP_CLASSPATH=$HADOOP_CLASSPATH:/opt/apps/JINDOSDK/jindosdk-current/lib/*
