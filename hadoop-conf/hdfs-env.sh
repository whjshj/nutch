# Where log files are stored in the secure data environment.
export HADOOP_LOG_DIR=/var/log/taihao-apps/hadoop-hdfs

# Edit by EMR HDFS: hdfs-env.sh => HDFS_BUILDIN_OPTS
SHARED_HADOOP_HDFS_OPTS="-XX:ErrorFile=${HADOOP_LOG_DIR}/hs_err_pid%p.log"
export HADOOP_NAMENODE_OPTS="${HADOOP_NAMENODE_OPTS} -server -XX:ParallelGCThreads=8 -XX:MetaspaceSize=512m -XX:+UseConcMarkSweepGC -XX:NewRatio=3 -verbose:gc -XX:+PrintGCDetails -XX:+PrintGCTimeStamps -XX:+PrintGCDateStamps -XX:+UseGCLogFileRotation -XX:NumberOfGCLogFiles=5 -XX:GCLogFileSize=100M -XX:CMSInitiatingOccupancyFraction=80 -XX:+UseCMSInitiatingOccupancyOnly -XX:+DisableExplicitGC $SHARED_HADOOP_HDFS_OPTS -Xloggc:${HADOOP_LOG_DIR}/namenode-gc.log -Dhdfs.audit.logger=INFO,RFAAUDIT"
export HADOOP_DATANODE_OPTS="${HADOOP_DATANODE_OPTS} -server -XX:ParallelGCThreads=6 -XX:MetaspaceSize=512m -XX:+UseConcMarkSweepGC -XX:NewRatio=4 -verbose:gc -XX:+PrintGCDetails -XX:+PrintGCTimeStamps -XX:+PrintGCDateStamps -XX:+UseGCLogFileRotation -XX:NumberOfGCLogFiles=5 -XX:GCLogFileSize=100M -XX:CMSInitiatingOccupancyFraction=80 -XX:+UseCMSInitiatingOccupancyOnly -XX:+DisableExplicitGC -XX:+ExitOnOutOfMemoryError  $SHARED_HADOOP_HDFS_OPTS -Xloggc:$HADOOP_LOG_DIR/datanode-gc.log "
export HADOOP_SECONDARYNAMENODE_OPTS="${HADOOP_SECONDARYNAMENODE_OPTS} -server -XX:ParallelGCThreads=8 -XX:MetaspaceSize=512m -XX:+UseConcMarkSweepGC -XX:NewRatio=3 -verbose:gc -XX:+PrintGCDetails -XX:+PrintGCTimeStamps -XX:+PrintGCDateStamps  -XX:+UseGCLogFileRotation -XX:NumberOfGCLogFiles=5 -XX:GCLogFileSize=100M -XX:CMSInitiatingOccupancyFraction=80 -XX:+UseCMSInitiatingOccupancyOnly -XX:+DisableExplicitGC ${SHARED_HADOOP_HDFS_OPTS}  -Xloggc:${HADOOP_LOG_DIR}/secondary-gc.log -Dhdfs.audit.logger=${HDFS_AUDIT_LOGGER:-INFO,NullAppender}"

export HADOOP_JOURNALNODE_OPTS="-Xmx1g -Xms1g -server -XX:ParallelGCThreads=8 -XX:+UseConcMarkSweepGC -XX:NewRatio=3 -verbose:gc -XX:+PrintGCDetails -XX:+PrintGCTimeStamps -XX:+PrintGCDateStamps -XX:+UseGCLogFileRotation -XX:NumberOfGCLogFiles=3 -XX:GCLogFileSize=100M -XX:CMSInitiatingOccupancyFraction=80 -XX:+UseCMSInitiatingOccupancyOnly -XX:+DisableExplicitGC -XX:ErrorFile=${HADOOP_LOG_DIR}/hs_err_pid%p.log -Xloggc:${HADOOP_LOG_DIR}/journalnode-gc.log -Dhdfs.audit.logger=INFO,RFAAUDIT"
export HADOOP_ZKFC_OPTS="-Xmx1g -Xms1g -server -XX:ParallelGCThreads=8 -XX:+UseConcMarkSweepGC -XX:NewRatio=3 -verbose:gc -XX:+PrintGCDetails -XX:+PrintGCTimeStamps -XX:+PrintGCDateStamps -XX:+UseGCLogFileRotation -XX:NumberOfGCLogFiles=3 -XX:GCLogFileSize=100M -XX:CMSInitiatingOccupancyFraction=80 -XX:+UseCMSInitiatingOccupancyOnly -XX:+DisableExplicitGC -XX:ErrorFile=${HADOOP_LOG_DIR}/hs_err_pid%p.log -Xloggc:${HADOOP_LOG_DIR}/zkfc-gc.log -Dhdfs.audit.logger=INFO,RFAAUDIT"

export HADOOP_NAMENODE_HEAPSIZE=6403
export HADOOP_DATANODE_HEAPSIZE=3797
export HADOOP_SECONDARY_NAMENODE_HEAPSIZE=1024

# Keep compatible with older verion stack.
if [ "$HADOOP_HEAPSIZE" == "HADOOP_NAMENODE_HEAPSIZE" ]; then
  export HADOOP_HEAPSIZE=$HADOOP_NAMENODE_HEAPSIZE
fi
if [ "$HADOOP_HEAPSIZE" == "HADOOP_SECONDARY_NAMENODE_HEAPSIZE" ]; then
  export HADOOP_HEAPSIZE=$HADOOP_SECONDARY_NAMENODE_HEAPSIZE
fi
if [ "$HADOOP_HEAPSIZE" == "HADOOP_DATANODE_HEAPSIZE" ]; then
  export HADOOP_HEAPSIZE=$HADOOP_DATANODE_HEAPSIZE
fi

if [ "$HADOOP_HEAPSIZE" == "$HADOOP_NAMENODE_HEAPSIZE" ]; then
  export HADOOP_NAMENODE_OPTS=$HADOOP_NAMENODE_OPTS" -Xms6403m "
fi
if [ "$HADOOP_HEAPSIZE" == "$HADOOP_SECONDARY_NAMENODE_HEAPSIZE" ]; then
  export HADOOP_SECONDARYNAMENODE_OPTS=$HADOOP_SECONDARYNAMENODE_OPTS" -Xms1024m "
fi
if [ "$HADOOP_HEAPSIZE" == "$HADOOP_DATANODE_HEAPSIZE" ]; then
  export HADOOP_DATANODE_OPTS=$HADOOP_DATANODE_OPTS" -Xms3797m "
fi

# Edit by EMR HDFS: hdfs-env.sh => HDFS_EXTRA_ENVS

