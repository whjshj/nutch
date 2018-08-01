/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.nutch.fetcher;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.JobContext;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.lib.input.SequenceFileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapred.FileSplit;
import org.apache.hadoop.util.StringUtils;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.apache.nutch.crawl.CrawlDatum;
import org.apache.nutch.crawl.NutchWritable;
import org.apache.nutch.metadata.Nutch;
import org.apache.nutch.util.NutchConfiguration;
import org.apache.nutch.util.NutchJob;
import org.apache.nutch.util.NutchTool;
import org.apache.nutch.util.TimingUtil;
import org.commoncrawl.util.NullOutputCommitter;

/**
 * A queue-based fetcher.
 * 
 * <p>
 * This fetcher uses a well-known model of one producer (a QueueFeeder) and many
 * consumers (FetcherThread-s).
 * 
 * <p>
 * QueueFeeder reads input fetchlists and populates a set of FetchItemQueue-s,
 * which hold FetchItem-s that describe the items to be fetched. There are as
 * many queues as there are unique hosts, but at any given time the total number
 * of fetch items in all queues is less than a fixed number (currently set to a
 * multiple of the number of threads).
 * 
 * <p>
 * As items are consumed from the queues, the QueueFeeder continues to add new
 * input items, so that their total count stays fixed (FetcherThread-s may also
 * add new items to the queues e.g. as a results of redirection) - until all
 * input items are exhausted, at which point the number of items in the queues
 * begins to decrease. When this number reaches 0 fetcher will finish.
 * 
 * <p>
 * This fetcher implementation handles per-host blocking itself, instead of
 * delegating this work to protocol-specific plugins. Each per-host queue
 * handles its own "politeness" settings, such as the maximum number of
 * concurrent requests and crawl delay between consecutive requests - and also a
 * list of requests in progress, and the time the last request was finished. As
 * FetcherThread-s ask for new items to be fetched, queues may return eligible
 * items or null if for "politeness" reasons this host's queue is not yet ready.
 * 
 * <p>
 * If there are still unfetched items in the queues, but none of the items are
 * ready, FetcherThread-s will spin-wait until either some items become
 * available, or a timeout is reached (at which point the Fetcher will abort,
 * assuming the task is hung).
 * 
 * @author Andrzej Bialecki
 */
public class Fetcher extends NutchTool implements Tool {

  public static final int PERM_REFRESH_TIME = 5;

  public static final String CONTENT_REDIR = "content";

  public static final String PROTOCOL_REDIR = "protocol";

  private static final Logger LOG = LoggerFactory
      .getLogger(MethodHandles.lookup().lookupClass());

  public static class InputFormat extends
  SequenceFileInputFormat<Text, CrawlDatum> {
    /** Don't split inputs, to keep things polite. */
    public InputSplit[] getSplits(JobContext job, int nSplits) throws IOException {
      List<FileStatus> files = listStatus(job);
      FileSplit[] splits = new FileSplit[files.size()];
      Iterator<FileStatus> iterator= files.listIterator();
      int index = 0;
      while(iterator.hasNext()) {
        index++;
        FileStatus cur = iterator.next();
        splits[index] = new FileSplit(cur.getPath(), 0, cur.getLen(),
            (String[]) null);
      }
      return splits;
    }
  }

  public Fetcher() {
    super(null);
  }

  public Fetcher(Configuration conf) {
    super(conf);
  }

  public static boolean isParsing(Configuration conf) {
    return conf.getBoolean("fetcher.parse", true);
  }

  public static boolean isStoringContent(Configuration conf) {
    return conf.getBoolean("fetcher.store.content", true);
  }

  public static boolean isStoringWarc(Configuration conf) {
    return conf.getBoolean("fetcher.store.warc", false);
  }

  public static class FetcherRun extends
     Mapper<Text, CrawlDatum, Text, NutchWritable> {

    private String segmentName;
    private AtomicInteger activeThreads = new AtomicInteger(0);
    private AtomicInteger spinWaiting = new AtomicInteger(0);
    private long start = System.currentTimeMillis();
    private AtomicLong lastRequestStart = new AtomicLong(start); 
    private AtomicLong bytes = new AtomicLong(0); // total bytes fetched
    private AtomicInteger pages = new AtomicInteger(0); // total pages fetched
    private AtomicInteger errors = new AtomicInteger(0); // total pages errored
    private boolean storingContent;
    private boolean parsing;

    private AtomicInteger getActiveThreads() {
      return activeThreads;
    }
 
    private void reportStatus(Context context, FetchItemQueues fetchQueues, int pagesLastSec, int bytesLastSec)
        throws IOException {
      StringBuilder status = new StringBuilder();
      Long elapsed = new Long((System.currentTimeMillis() - start) / 1000);

      float avgPagesSec = (float) pages.get() / elapsed.floatValue();
      long avgBytesSec = (bytes.get() / 128l) / elapsed.longValue();

      status.append(activeThreads).append(" threads (").append(spinWaiting.get())
      .append(" waiting), ");
      status.append(fetchQueues.getQueueCount()).append(" queues, ");
      status.append(fetchQueues.getTotalSize()).append(" URLs queued, ");
      status.append(pages).append(" pages, ").append(errors).append(" errors, ");
      status.append(String.format("%.2f", avgPagesSec)).append(" pages/s (");
      status.append(pagesLastSec).append(" last sec), ");
      status.append(avgBytesSec).append(" kbits/s (")
      .append((bytesLastSec / 128)).append(" last sec)");

      context.setStatus(status.toString());
    }

    @Override 
    public void setup(Mapper<Text, CrawlDatum, Text, NutchWritable>.Context context) {
      Configuration conf = context.getConfiguration();
      segmentName = conf.get(Nutch.SEGMENT_NAME_KEY);
      storingContent = isStoringContent(conf);
      parsing = isParsing(conf);
    }	  

    @Override
    public void run(Context innerContext) throws IOException {
   
      setup(innerContext); 
      Configuration conf = innerContext.getConfiguration(); 
      LinkedList<FetcherThread> fetcherThreads = new LinkedList<>();
      FetchItemQueues fetchQueues = new FetchItemQueues(conf);
      QueueFeeder feeder; 

      int threadCount = conf.getInt("fetcher.threads.fetch", 10);
      if (LOG.isInfoEnabled()) {
        LOG.info("Fetcher: threads: {}", threadCount);
      }

      int timeoutDivisor = conf.getInt("fetcher.threads.timeout.divisor", 2);
      if (LOG.isInfoEnabled()) {
        LOG.info("Fetcher: time-out divisor: {}", timeoutDivisor);
      }

      int queueDepthMuliplier = conf.getInt(
          "fetcher.queue.depth.multiplier", 50);

      feeder = new QueueFeeder(innerContext, fetchQueues, threadCount
          * queueDepthMuliplier);

      // the value of the time limit is either -1 or the time where it should
      // finish
      long timelimit = conf.getLong("fetcher.timelimit", -1);
      if (timelimit != -1)
        feeder.setTimeLimit(timelimit);
      feeder.start();

      for (int i = 0; i < threadCount; i++) { // spawn threads
        FetcherThread t = new FetcherThread(conf, getActiveThreads(), fetchQueues, 
            feeder, spinWaiting, lastRequestStart, innerContext, errors, segmentName,
            parsing, storingContent, pages, bytes);
        fetcherThreads.add(t);
        t.start();
      }

      // select a timeout that avoids a task timeout
      long timeout = conf.getInt("mapreduce.task.timeout", 10 * 60 * 1000)
          / timeoutDivisor;

      // Used for threshold check, holds pages and bytes processed in the last
      // second
      int pagesLastSec;
      int bytesLastSec;

      int throughputThresholdNumRetries = 0;

      int throughputThresholdPages = conf.getInt(
          "fetcher.throughput.threshold.pages", -1);
      if (LOG.isInfoEnabled()) {
        LOG.info("Fetcher: throughput threshold: {}", throughputThresholdPages);
      }
      int throughputThresholdMaxRetries = conf.getInt(
          "fetcher.throughput.threshold.retries", 5);
      if (LOG.isInfoEnabled()) {
        LOG.info("Fetcher: throughput threshold retries: {}",
            throughputThresholdMaxRetries);
      }
      long throughputThresholdTimeLimit = conf.getLong(
          "fetcher.throughput.threshold.check.after", -1);

      int targetBandwidth = conf.getInt("fetcher.bandwidth.target", -1) * 1000;
      int maxNumThreads = conf.getInt("fetcher.maxNum.threads", threadCount);
      if (maxNumThreads < threadCount) {
        LOG.info("fetcher.maxNum.threads can't be < than {} : using {} instead",
            threadCount, threadCount);
        maxNumThreads = threadCount;
      }
      int bandwidthTargetCheckEveryNSecs = conf.getInt(
          "fetcher.bandwidth.target.check.everyNSecs", 30);
      if (bandwidthTargetCheckEveryNSecs < 1) {
        LOG.info("fetcher.bandwidth.target.check.everyNSecs can't be < to 1 : using 1 instead");
        bandwidthTargetCheckEveryNSecs = 1;
      }

      int maxThreadsPerQueue = conf.getInt("fetcher.threads.per.queue", 1);

      int bandwidthTargetCheckCounter = 0;
      long bytesAtLastBWTCheck = 0l;

      do { // wait for threads to exit
        pagesLastSec = pages.get();
        bytesLastSec = (int) bytes.get();

        try {
          Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        pagesLastSec = pages.get() - pagesLastSec;
        bytesLastSec = (int) bytes.get() - bytesLastSec;

        innerContext.getCounter("FetcherStatus", "bytes_downloaded").increment(bytesLastSec);

        reportStatus(innerContext, fetchQueues, pagesLastSec, bytesLastSec);

        LOG.info("-activeThreads=" + activeThreads + ", spinWaiting="
            + spinWaiting.get() + ", fetchQueues.totalSize="
            + fetchQueues.getTotalSize() + ", fetchQueues.getQueueCount="
            + fetchQueues.getQueueCount());

        if (!feeder.isAlive() && fetchQueues.getTotalSize() < 5) {
          fetchQueues.dump();
        }

        // if throughput threshold is enabled
        if (throughputThresholdTimeLimit < System.currentTimeMillis()
            && throughputThresholdPages != -1) {
          // Check if we're dropping below the threshold
          if (pagesLastSec < throughputThresholdPages) {
            throughputThresholdNumRetries++;
            LOG.warn("{}: dropping below configured threshold of {} pages per second",
                Integer.toString(throughputThresholdNumRetries), Integer.toString(throughputThresholdPages));

            // Quit if we dropped below threshold too many times
            if (throughputThresholdNumRetries == throughputThresholdMaxRetries) {
              LOG.warn("Dropped below threshold too many times, killing!");

              // Disable the threshold checker
              throughputThresholdPages = -1;

              // Empty the queues cleanly and get number of items that were
              // dropped
              int hitByThrougputThreshold = fetchQueues.emptyQueues();

              if (hitByThrougputThreshold != 0)
                innerContext.getCounter("FetcherStatus", "hitByThrougputThreshold").increment(
                    hitByThrougputThreshold);
            }
          }
        }

        // adjust the number of threads if a target bandwidth has been set
        if (targetBandwidth > 0) {
          if (bandwidthTargetCheckCounter < bandwidthTargetCheckEveryNSecs)
            bandwidthTargetCheckCounter++;
          else if (bandwidthTargetCheckCounter == bandwidthTargetCheckEveryNSecs) {
            long bpsSinceLastCheck = ((bytes.get() - bytesAtLastBWTCheck) * 8)
                / bandwidthTargetCheckEveryNSecs;

            bytesAtLastBWTCheck = bytes.get();
            bandwidthTargetCheckCounter = 0;

            int averageBdwPerThread = 0;
            if (activeThreads.get() > 0)
              averageBdwPerThread = Math.round(bpsSinceLastCheck
                  / activeThreads.get());

            LOG.info("averageBdwPerThread : {} kbps", (averageBdwPerThread / 1000));

            if (bpsSinceLastCheck < targetBandwidth && averageBdwPerThread > 0) {
              // check whether it is worth doing e.g. more queues than threads

              if ((fetchQueues.getQueueCount() * maxThreadsPerQueue) > activeThreads
                  .get()) {

                long remainingBdw = targetBandwidth - bpsSinceLastCheck;
                int additionalThreads = Math.round(remainingBdw
                    / averageBdwPerThread);
                int availableThreads = maxNumThreads - activeThreads.get();

                // determine the number of available threads (min between
                // availableThreads and additionalThreads)
                additionalThreads = (availableThreads < additionalThreads ? availableThreads
                    : additionalThreads);
                LOG.info("Has space for more threads ({} vs {} kbps) \t=> adding {} new threads",
                    new Object[]{(bpsSinceLastCheck / 1000), (targetBandwidth / 1000), additionalThreads});
                // activate new threads
                for (int i = 0; i < additionalThreads; i++) {
                  FetcherThread thread = new FetcherThread(conf, getActiveThreads(), fetchQueues, 
                      feeder, spinWaiting, lastRequestStart, innerContext, errors, segmentName, parsing,
                      storingContent, pages, bytes);
                  fetcherThreads.add(thread);
                  thread.start();
                }
              }
            } else if (bpsSinceLastCheck > targetBandwidth
                && averageBdwPerThread > 0) {
              // if the bandwidth we're using is greater then the expected
              // bandwidth, we have to stop some threads
              long excessBdw = bpsSinceLastCheck - targetBandwidth;
              int excessThreads = Math.round(excessBdw / averageBdwPerThread);
              LOG.info("Exceeding target bandwidth ({} vs {} kbps). \t=> excessThreads = {}",
                  new Object[]{bpsSinceLastCheck / 1000, (targetBandwidth / 1000), excessThreads});
              // keep at least one
              if (excessThreads >= fetcherThreads.size())
                excessThreads = 0;
              // de-activates threads
              for (int i = 0; i < excessThreads; i++) {
                FetcherThread thread = fetcherThreads.removeLast();
                thread.setHalted(true);
              }
            }
          }
        }

        // check timelimit
        if (!feeder.isAlive()) {
          int hitByTimeLimit = fetchQueues.checkTimelimit();
          if (hitByTimeLimit != 0)
            innerContext.getCounter("FetcherStatus", "hitByTimeLimit").increment(
                hitByTimeLimit);
        }

        // some requests seem to hang, despite all intentions
        if ((System.currentTimeMillis() - lastRequestStart.get()) > timeout) {
          if (LOG.isWarnEnabled()) {
            LOG.warn("Aborting with {} hung threads.", activeThreads);
            for (int i = 0; i < fetcherThreads.size(); i++) {
              FetcherThread thread = fetcherThreads.get(i);
              if (thread.isAlive()) {
                LOG.warn("Thread #{} hung while processing {}", i, thread.getReprUrl());
                if (LOG.isDebugEnabled()) {
                  StackTraceElement[] stack = thread.getStackTrace();
                  StringBuilder sb = new StringBuilder();
                  sb.append("Stack of thread #").append(i).append(":\n");
                  for (StackTraceElement s : stack) {
                    sb.append(s.toString()).append('\n');
                  }
                  LOG.debug(sb.toString());
                }
              }
            }
          }
          return;
        }

      } while (activeThreads.get() > 0);
      LOG.info("-activeThreads={}", activeThreads);

    }
  }

  public void fetch(Path segment, int threads) throws IOException, 
    InterruptedException, ClassNotFoundException {

    checkConfiguration();

    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    long start = System.currentTimeMillis();
    if (LOG.isInfoEnabled()) {
      LOG.info("Fetcher: starting at {}", sdf.format(start));
      LOG.info("Fetcher: segment: {}", segment);
    }

    // set the actual time for the timelimit relative
    // to the beginning of the whole job and not of a specific task
    // otherwise it keeps trying again if a task fails
    long timelimit = getConf().getLong("fetcher.timelimit.mins", -1);
    if (timelimit != -1) {
      timelimit = System.currentTimeMillis() + (timelimit * 60 * 1000);
      LOG.info("Fetcher Timelimit set for : {}", timelimit);
      getConf().setLong("fetcher.timelimit", timelimit);
    }

    // Set the time limit after which the throughput threshold feature is
    // enabled
    timelimit = getConf().getLong("fetcher.throughput.threshold.check.after",
        10);
    timelimit = System.currentTimeMillis() + (timelimit * 60 * 1000);
    getConf().setLong("fetcher.throughput.threshold.check.after", timelimit);

    int maxOutlinkDepth = getConf().getInt("fetcher.follow.outlinks.depth", -1);
    if (maxOutlinkDepth > 0) {
      LOG.info("Fetcher: following outlinks up to depth: {}",
          Integer.toString(maxOutlinkDepth));

      int maxOutlinkDepthNumLinks = getConf().getInt(
          "fetcher.follow.outlinks.num.links", 4);
      int outlinksDepthDivisor = getConf().getInt(
          "fetcher.follow.outlinks.depth.divisor", 2);

      int totalOutlinksToFollow = 0;
      for (int i = 0; i < maxOutlinkDepth; i++) {
        totalOutlinksToFollow += (int) Math.floor(outlinksDepthDivisor
            / (i + 1) * maxOutlinkDepthNumLinks);
      }

      LOG.info("Fetcher: maximum outlinks to follow: {}",
          Integer.toString(totalOutlinksToFollow));
    }

    Job job = NutchJob.getInstance(getConf());
    job.setJobName("FetchData");
    Configuration conf = job.getConfiguration();

    conf.setInt("fetcher.threads.fetch", threads);
    conf.set(Nutch.SEGMENT_NAME_KEY, segment.getName());

    // for politeness, don't permit parallel execution of a single task
    conf.set("mapreduce.map.speculative","false");

    FileInputFormat.addInputPath(job, new Path(segment,
        CrawlDatum.GENERATE_DIR_NAME));
    job.setInputFormatClass(InputFormat.class);
    job.setJarByClass(Fetcher.class);
    job.setMapperClass(Fetcher.FetcherRun.class);

    FileOutputFormat.setOutputPath(job, segment);
    job.setOutputFormatClass(FetcherOutputFormat.class);
    job.setOutputKeyClass(Text.class);
    job.setOutputValueClass(NutchWritable.class);

    try {
      boolean success = job.waitForCompletion(true);
      if (!success) {
        String message = "Fetcher job did not succeed, job status:"
            + job.getStatus().getState() + ", reason: "
            + job.getStatus().getFailureInfo();
        LOG.error(message);
        throw new RuntimeException(message);
      }
    } catch (InterruptedException | ClassNotFoundException e) {
      LOG.error(StringUtils.stringifyException(e));
      throw e;
    }

    long end = System.currentTimeMillis();
    LOG.info("Fetcher: finished at {}, elapsed: {}", sdf.format(end),
        TimingUtil.elapsedTime(start, end));
  }

  /** Run the fetcher. */
  public static void main(String[] args) throws Exception {
    int res = ToolRunner.run(NutchConfiguration.create(), new Fetcher(), args);
    System.exit(res);
  }

  public int run(String[] args) throws Exception {

    String usage = "Usage: Fetcher <segment> [-threads n]";

    if (args.length < 1) {
      System.err.println(usage);
      return -1;
    }

    Path segment = new Path(args[0]);

    int threads = getConf().getInt("fetcher.threads.fetch", 10);

    for (int i = 1; i < args.length; i++) { // parse command line
      if (args[i].equals("-threads")) { // found -threads option
        threads = Integer.parseInt(args[++i]);
      }
    }

    getConf().setInt("fetcher.threads.fetch", threads);

    try {
      fetch(segment, threads);
      return 0;
    } catch (Exception e) {
      LOG.error("Fetcher: {}", StringUtils.stringifyException(e));
      return -1;
    }

  }

  private void checkConfiguration() {
    // ensure that a value has been set for the agent name
    String agentName = getConf().get("http.agent.name");
    if (agentName == null || agentName.trim().length() == 0) {
      String message = "Fetcher: No agents listed in 'http.agent.name'"
          + " property.";
      if (LOG.isErrorEnabled()) {
        LOG.error(message);
      }
      throw new IllegalArgumentException(message);
    }
  }

  @Override
  public Map<String, Object> run(Map<String, Object> args, String crawlId) throws Exception {

    Map<String, Object> results = new HashMap<>();

    Path segment = null;
    if(args.containsKey(Nutch.ARG_SEGMENTS)) {
      Object seg = args.get(Nutch.ARG_SEGMENTS);
      if(seg instanceof Path) {
        segment = (Path) seg;
      }
      else if(seg instanceof String){
        segment = new Path(seg.toString());
      }
      else if(seg instanceof ArrayList) {
        String[] segmentsArray = (String[])seg;
        segment = new Path(segmentsArray[0].toString());
        	  
        if(segmentsArray.length > 1){
       	  LOG.warn("Only the first segment of segments array is used.");
        }
      }
    }
    else {
      String segmentDir = crawlId+"/segments";
      File segmentsDir = new File(segmentDir);
      File[] segmentsList = segmentsDir.listFiles();  
      Arrays.sort(segmentsList, (f1, f2) -> {
        if(f1.lastModified()>f2.lastModified())
          return -1;
        else
          return 0;
      });
      segment = new Path(segmentsList[0].getPath());
    }


    int threads = getConf().getInt("fetcher.threads.fetch", 10);

    // parse command line
    if (args.containsKey("threads")) { // found -threads option
      threads = Integer.parseInt((String)args.get("threads"));
    }
    getConf().setInt("fetcher.threads.fetch", threads);

    try {
      fetch(segment, threads);
      results.put(Nutch.VAL_RESULT, Integer.toString(0));
      return results;
    } catch (Exception e) {
      LOG.error("Fetcher: {}", StringUtils.stringifyException(e));
      results.put(Nutch.VAL_RESULT, Integer.toString(-1));
      return results;
    }
  }

}
