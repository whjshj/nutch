WARC_OUTPUT_DIR="$CRAWL_DIR"/warc
WARC_OPERATOR="Unknown Coward"
DATE=$(date +%Y-%m-%d)
#!/bin/bash

# run a crawl using Common Crawl's fork of Apache Nutch and store content in WARC files

# command-line arguments
CRAWL_DIR="$1"
NUM_CYCLES="$2"
SEED_DIR="$3"
FLAG="$4"

BASE_DIR="$(dirname $0)"
NUTCH_HOME="/root/nutch-cc/runtime/deploy"
# total size of fetch lists
TOPN=20000000
# size of fetch lists per host
TOPN_PER_HOST=500
# max. time spent for fetching per cycle
MAX_FETCH_TIME_MINUTES=$((1+$TOPN_PER_HOST/5))
# parallel threads used for fetching
FETCHER_THREADS=80
FETCHER_NUMBER=40

# WARC options (please modify)
WARC_OUTPUT_DIR="$CRAWL_DIR"/warc
WARC_OPERATOR="Unknown Coward"
DATE=$(date +%Y-%m-%d)
WARC_PUBLISHER="$OPERATOR"
WARC_PREFIX="TEST-CRAWL"
WARC_ISPARTOF="$PREFIX-$DATE"
WARC_DESCRIPTION="Test crawl, Apache Nutch, $DATE"
WARC_SOFTWARE="Apache Nutch 1.17 (modified, https://github.com/commoncrawl/nutch/)"
WARC_OPTIONS=(
    -Dfetcher.store.warc=true
    -Dwarc.deduplicate=false
    -Dwarc.export.text=false
    -Dwarc.export.crawldiagnostics=true
    -Dwarc.export.robotstxt=true
    -Dwarc.export.cdx=true
    -Dwarc.export.prefix="$WARC_PREFIX"
    -Dwarc.export.operator="$WARC_OPERATOR"
    -Dwarc.export.publisher="$WARC_PUBLISHER"
    -Dwarc.export.software="Apache Nutch 1.17 (modified, https://github.com/commoncrawl/nutch/)"
    -Dwarc.export.description="$WARC_DESCRIPTION"
    -Dwarc.export.isPartOf="$WARC_ISPARTOF"
    -Dwarc.detect.language=true
)


function nutch() {
    nutch_tool="$1"; shift
    echo "nutch $nutch_tool $@"

    # configure log directory
    export NUTCH_LOG_DIR=$CRAWL_DIR/logs
    # make Nutch pick configuration files first from $BASE_DIR/conf
    export NUTCH_CONF_DIR=$BASE_DIR/conf:$NUTCH_HOME/conf

    $NUTCH_HOME/bin/nutch "$nutch_tool" "$@"

    RETCODE=$?
    if [ $RETCODE -eq 0 ]; then
        : # ok: no error
    elif [ "$nutch_tool" == "generate" ] && [ $RETCODE -eq 1 ]; then
        echo "Generate returned 1 (no segment created / no more URLs to fetch)"
    else
        echo "Error running:"
        echo "  nutch $@"
        echo "Failed with exit value $RETCODE."
        exit $RETCODE
    fi
}

start_time=$(date +%s)
if [ -n "$SEED_DIR" ] && ! [ -z "$SEED_DIR"  ]; then
    echo "Injecting seed URLs"
    nutch inject "${commonOptions[@]}" "$CRAWL_DIR"/crawldb "$SEED_DIR" -noNormalize -noFilter
fi

for cycle in $(seq 1 $NUM_CYCLES); do
    echo "cycle $cycle"
    nutch generate \
          -Dgenerate.max.count=$TOPN_PER_HOST \
          -Dgenerate.count.mode=host \
          -Dgenerate.min.score=0.0 \
          "$CRAWL_DIR"/crawldb "$CRAWL_DIR"/segments"$cycle""$FLAG" \
          -topN $TOPN -noFilter -noNorm -numFetchers $FETCHER_NUMBER

    SEGMENT=$( hadoop fs -ls  "$CRAWL_DIR"/segments"$cycle""$FLAG"/ | awk '{print $8}' | sort | tail -n 1 | awk -F/ '{print $NF}')
    echo $SEGMENT

    nutch fetch \
          -Dfetcher.parse=true \
          -Dfetcher.timelimit.mins=$MAX_FETCH_TIME_MINUTES \
          "${WARC_OPTIONS[@]}" \
          -Dwarc.export.path=$WARC_OUTPUT_DIR/$SEGMENT \
          -Dwarc.export.cdx.path=$WARC_OUTPUT_DIR/$SEGMENT/cdx \
	  -Dfetcher.follow.outlinks.depth=0 \
          "$CRAWL_DIR"/segments"$cycle""$FLAG"/$SEGMENT \
          -threads $FETCHER_THREADS
    # TODO: increase number of WARC files (num reducers)
    #       if size of fetch list (or top-N) exceeds 50k

    nutch updatedb "$CRAWL_DIR"/crawldb "$CRAWL_DIR"/segments"$cycle""$FLAG"/$SEGMENT

done

end_time=$(date +%s)

# 计算时间差
elapsed_time=$((end_time - start_time))

# 输出时间差
echo "脚本执行时间：$elapsed_time 秒"
