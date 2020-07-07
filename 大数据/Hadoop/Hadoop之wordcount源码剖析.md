

主要是客户端，Map，Reduce三个维度分析源码

依然参考代码

```java
    public static void main(String[] args) throws Exception {

        Configuration configuration = new Configuration();
        configuration.set("fs.defaultFS","hdfs://192.168.109.133:8020");
        Job job= Job.getInstance(configuration,"vison-wordcount");
        //通过类名打成jar包
        job.setJarByClass(WordCount.class);

        //1.输入文件
        input(args[0],job);
        //2.编写mapper处理逻辑
        job.setMapperClass(MyMapper.class);
        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(LongWritable.class);

        //3.shuffle流程(暂不处理)

        //4.编写reducer逻辑
        job.setReducerClass(MyReducer.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(LongWritable.class);

        //5.输出文件
        output(args[1],job);


        //6.运行
        boolean b = job.waitForCompletion(true);
        System.out.println(b);

    }
```



# 1.wordcount客户端源码

主要处理客户端的功能，入口代码如下

```java
job.waitForCompletion(true);
```

--> Job# submit() -->  JobSubmitter#submitJobInternal

```java
1.检查输入输出
2.计算分片
3.将jar包和配置复制到文件系统中
4.提交计算任务

JobStatus submitJobInternal(Job job, Cluster cluster) 
  throws ClassNotFoundException, InterruptedException, IOException {

    //validate the jobs output specs 
    checkSpecs(job);

    Configuration conf = job.getConfiguration();
    addMRFrameworkToDistributedCache(conf);

    Path jobStagingArea = JobSubmissionFiles.getStagingDir(cluster, conf);
    //configure the command line options correctly on the submitting dfs
    InetAddress ip = InetAddress.getLocalHost();
    if (ip != null) {
      submitHostAddress = ip.getHostAddress();
      submitHostName = ip.getHostName();
      conf.set(MRJobConfig.JOB_SUBMITHOST,submitHostName);
      conf.set(MRJobConfig.JOB_SUBMITHOSTADDR,submitHostAddress);
    }
    JobID jobId = submitClient.getNewJobID();
    job.setJobID(jobId);
    Path submitJobDir = new Path(jobStagingArea, jobId.toString());
    JobStatus status = null;
    try {
      conf.set(MRJobConfig.USER_NAME,
          UserGroupInformation.getCurrentUser().getShortUserName());
      conf.set("hadoop.http.filter.initializers", 
          "org.apache.hadoop.yarn.server.webproxy.amfilter.AmFilterInitializer");
      conf.set(MRJobConfig.MAPREDUCE_JOB_DIR, submitJobDir.toString());
      LOG.debug("Configuring job " + jobId + " with " + submitJobDir 
          + " as the submit dir");
      // get delegation token for the dir
      TokenCache.obtainTokensForNamenodes(job.getCredentials(),
          new Path[] { submitJobDir }, conf);
      
      populateTokenCache(conf, job.getCredentials());

      // generate a secret to authenticate shuffle transfers
      if (TokenCache.getShuffleSecretKey(job.getCredentials()) == null) {
        KeyGenerator keyGen;
        try {
          keyGen = KeyGenerator.getInstance(SHUFFLE_KEYGEN_ALGORITHM);
          keyGen.init(SHUFFLE_KEY_LENGTH);
        } catch (NoSuchAlgorithmException e) {
          throw new IOException("Error generating shuffle secret key", e);
        }
        SecretKey shuffleKey = keyGen.generateKey();
        TokenCache.setShuffleSecretKey(shuffleKey.getEncoded(),
            job.getCredentials());
      }
      if (CryptoUtils.isEncryptedSpillEnabled(conf)) {
        conf.setInt(MRJobConfig.MR_AM_MAX_ATTEMPTS, 1);
        LOG.warn("Max job attempts set to 1 since encrypted intermediate" +
                "data spill is enabled");
      }

      copyAndConfigureFiles(job, submitJobDir);

      Path submitJobFile = JobSubmissionFiles.getJobConfPath(submitJobDir);
      
      // Create the splits for the job
      LOG.debug("Creating splits at " + jtFs.makeQualified(submitJobDir));
       // 这里计算分片的数量，细节往里面看
      int maps = writeSplits(job, submitJobDir);
      conf.setInt(MRJobConfig.NUM_MAPS, maps);
      LOG.info("number of splits:" + maps);

      // write "queue admins of the queue to which job is being submitted"
      // to job file.
      String queue = conf.get(MRJobConfig.QUEUE_NAME,
          JobConf.DEFAULT_QUEUE_NAME);
      AccessControlList acl = submitClient.getQueueAdmins(queue);
      conf.set(toFullPropertyName(queue,
          QueueACL.ADMINISTER_JOBS.getAclName()), acl.getAclString());

      // removing jobtoken referrals before copying the jobconf to HDFS
      // as the tasks don't need this setting, actually they may break
      // because of it if present as the referral will point to a
      // different job.
      TokenCache.cleanUpTokenReferral(conf);

      if (conf.getBoolean(
          MRJobConfig.JOB_TOKEN_TRACKING_IDS_ENABLED,
          MRJobConfig.DEFAULT_JOB_TOKEN_TRACKING_IDS_ENABLED)) {
        // Add HDFS tracking ids
        ArrayList<String> trackingIds = new ArrayList<String>();
        for (Token<? extends TokenIdentifier> t :
            job.getCredentials().getAllTokens()) {
          trackingIds.add(t.decodeIdentifier().getTrackingId());
        }
        conf.setStrings(MRJobConfig.JOB_TOKEN_TRACKING_IDS,
            trackingIds.toArray(new String[trackingIds.size()]));
      }

      // Set reservation info if it exists
      ReservationId reservationId = job.getReservationId();
      if (reservationId != null) {
        conf.set(MRJobConfig.RESERVATION_ID, reservationId.toString());
      }

      // Write job file to submit dir
      writeConf(conf, submitJobFile);
      
      //
      // Now, actually submit the job (using the submit name)
      //
      printTokens(jobId, job.getCredentials());
      status = submitClient.submitJob(
          jobId, submitJobDir.toString(), job.getCredentials());
      if (status != null) {
        return status;
      } else {
        throw new IOException("Could not launch job");
      }
    } finally {
      if (status == null) {
        LOG.info("Cleaning up the staging area " + submitJobDir);
        if (jtFs != null && submitJobDir != null)
          jtFs.delete(submitJobDir, true);

      }
    }
  }
```

--> writeSplits -->writeNewSplits

核心分片逻辑如下

```java
  private <T extends InputSplit>
  int writeNewSplits(JobContext job, Path jobSubmitDir) throws IOException,
      InterruptedException, ClassNotFoundException {
    Configuration conf = job.getConfiguration();
    InputFormat<?, ?> input =
      ReflectionUtils.newInstance(job.getInputFormatClass(), conf);
	
    //这里的Input是TextInputFormat（继承FileInputFormat），调用父类的方法
    List<InputSplit> splits = input.getSplits(job);
    T[] array = (T[]) splits.toArray(new InputSplit[splits.size()]);

    // sort the splits into order based on size, so that the biggest
    // go first
    Arrays.sort(array, new SplitComparator());
    JobSplitWriter.createSplitFiles(jobSubmitDir, conf, 
        jobSubmitDir.getFileSystem(conf), array);
    return array.length;
  }
```

分片的具体逻辑

```java
  public List<InputSplit> getSplits(JobContext job) throws IOException {
    StopWatch sw = new StopWatch().start();
    long minSize = Math.max(getFormatMinSplitSize(), getMinSplitSize(job)); //最小分片大小，可以配置，默认1
    long maxSize = getMaxSplitSize(job); //最大分片大小，可以配置，默认long最大值

    // generate splits
    List<InputSplit> splits = new ArrayList<InputSplit>();
    List<FileStatus> files = listStatus(job);
    for (FileStatus file: files) {
      Path path = file.getPath();
      long length = file.getLen();
      if (length != 0) {
        BlockLocation[] blkLocations;		//获取block块数量
        if (file instanceof LocatedFileStatus) {
          blkLocations = ((LocatedFileStatus) file).getBlockLocations();
        } else {
          FileSystem fs = path.getFileSystem(job.getConfiguration());
          blkLocations = fs.getFileBlockLocations(file, 0, length);
        }
        if (isSplitable(job, path)) {		//是否可以分片
          long blockSize = file.getBlockSize();
		  //计算分片大小
          long splitSize = computeSplitSize(blockSize, minSize, maxSize); 
		  

		  //开始分片处理
          long bytesRemaining = length;
          while (((double) bytesRemaining)/splitSize > SPLIT_SLOP) { 
		    //计算分片所在块的索引
            int blkIndex = getBlockIndex(blkLocations, length-bytesRemaining); //其实分片位置为0
			//根据相对文件的起止位置，分片大小，host存储分片信息
            splits.add(makeSplit(path, length-bytesRemaining, splitSize,
                        blkLocations[blkIndex].getHosts(),
                        blkLocations[blkIndex].getCachedHosts()));
            bytesRemaining -= splitSize;
          }

		  //如果分片没有完整分片完，添加最后一个分片	
          if (bytesRemaining != 0) {
            int blkIndex = getBlockIndex(blkLocations, length-bytesRemaining);
            splits.add(makeSplit(path, length-bytesRemaining, bytesRemaining,
                       blkLocations[blkIndex].getHosts(),
                       blkLocations[blkIndex].getCachedHosts()));
          }
        } else { // not splitable
          splits.add(makeSplit(path, 0, length, blkLocations[0].getHosts(),
                      blkLocations[0].getCachedHosts()));
        }
      } else { 
        //Create empty hosts array for zero length files
        splits.add(makeSplit(path, 0, length, new String[0]));
      }
    }
    // Save the number of input files for metrics/loadgen
    job.getConfiguration().setLong(NUM_INPUT_FILES, files.size());
    sw.stop();
    if (LOG.isDebugEnabled()) {
      LOG.debug("Total # of splits generated by getSplits: " + splits.size()
          + ", TimeTaken: " + sw.now(TimeUnit.MILLISECONDS));
    }
    return splits;
  }


 //获取分片所在的block块位置
  protected int getBlockIndex(BlockLocation[] blkLocations, 
                              long offset) {
    for (int i = 0 ; i < blkLocations.length; i++) {
      // 判定当前offset是否在这个block块内，有的话返回块的下标
      if ((blkLocations[i].getOffset() <= offset) &&
          (offset < blkLocations[i].getOffset() + blkLocations[i].getLength())){
        return i;
      }
    }
    BlockLocation last = blkLocations[blkLocations.length -1];
    long fileLength = last.getOffset() + last.getLength() -1;
    throw new IllegalArgumentException("Offset " + offset + 
                                       " is outside of file (0.." +
                                       fileLength + ")");
  }
```

**总结:**
   客户端做分片功能，分片大小默认是block块大小，
   分片的大小可以通过配置设置



# 2.wordcount的Map源码

map过程需要关心的事：

- 读取每一行

- 数据处理

-  然后进行排序输出 （数据过多还会combine操作）

  ​	

MapTask -> run方法任务调用 --> runNewMapper 运行mapper，一个split对应一个map任务

```java
  @SuppressWarnings("unchecked")
  private <INKEY,INVALUE,OUTKEY,OUTVALUE>
  void runNewMapper(final JobConf job,
                    final TaskSplitIndex splitIndex,
                    final TaskUmbilicalProtocol umbilical,
                    TaskReporter reporter
                    ) throws IOException, ClassNotFoundException,
                             InterruptedException {
    // make a task context so we can get the classes
    org.apache.hadoop.mapreduce.TaskAttemptContext taskContext =
      new org.apache.hadoop.mapreduce.task.TaskAttemptContextImpl(job, 
                                                                  getTaskID(),
                                                                  reporter);
    // make a mapper
    org.apache.hadoop.mapreduce.Mapper<INKEY,INVALUE,OUTKEY,OUTVALUE> mapper =
      (org.apache.hadoop.mapreduce.Mapper<INKEY,INVALUE,OUTKEY,OUTVALUE>)
        ReflectionUtils.newInstance(taskContext.getMapperClass(), job);
    // make the input format
    org.apache.hadoop.mapreduce.InputFormat<INKEY,INVALUE> inputFormat =
      (org.apache.hadoop.mapreduce.InputFormat<INKEY,INVALUE>)
        ReflectionUtils.newInstance(taskContext.getInputFormatClass(), job);
    // rebuild the input split
    org.apache.hadoop.mapreduce.InputSplit split = null;
    split = getSplitDetails(new Path(splitIndex.getSplitLocation()),
        splitIndex.getStartOffset());
    LOG.info("Processing split: " + split);

     //数据读取器
    org.apache.hadoop.mapreduce.RecordReader<INKEY,INVALUE> input =
      new NewTrackingRecordReader<INKEY,INVALUE>
        (split, inputFormat, reporter, taskContext);
    
    job.setBoolean(JobContext.SKIP_RECORDS, isSkipping());
    org.apache.hadoop.mapreduce.RecordWriter output = null;
    
    // get an output object
    if (job.getNumReduceTasks() == 0) {
      output = 
        new NewDirectOutputCollector(taskContext, job, umbilical, reporter);
    } else {
      //输出器  
      output = new NewOutputCollector(taskContext, job, umbilical, reporter);
    }

    org.apache.hadoop.mapreduce.MapContext<INKEY, INVALUE, OUTKEY, OUTVALUE> 
    mapContext = 
      new MapContextImpl<INKEY, INVALUE, OUTKEY, OUTVALUE>(job, getTaskID(), 
          input, output, 
          committer, 
          reporter, split);

    org.apache.hadoop.mapreduce.Mapper<INKEY,INVALUE,OUTKEY,OUTVALUE>.Context 
        mapperContext = 
          new WrappedMapper<INKEY, INVALUE, OUTKEY, OUTVALUE>().getMapContext(
              mapContext);

    try {
      //初始化  
      input.initialize(split, mapperContext);
      //map任务执行  
      mapper.run(mapperContext);
      mapPhase.complete();
      setPhase(TaskStatus.Phase.SORT);
      statusUpdate(umbilical);
      input.close();
      input = null;
      output.close(mapperContext);
      output = null;
    } finally {
      closeQuietly(input);
      closeQuietly(output, mapperContext);
    }
  }
```



## 2.1 数据输入

```java
org.apache.hadoop.mapreduce.RecordReader<INKEY,INVALUE> input =
      new NewTrackingRecordReader<INKEY,INVALUE>
        (split, inputFormat, reporter, taskContext);

//创建NewTrackingRecordReader 所做的事

  @Override
  public RecordReader<LongWritable, Text> 
    createRecordReader(InputSplit split,
                       TaskAttemptContext context) {
    String delimiter = context.getConfiguration().get(
        "textinputformat.record.delimiter");
    byte[] recordDelimiterBytes = null;
    if (null != delimiter)
      recordDelimiterBytes = delimiter.getBytes(Charsets.UTF_8);
	 //实际的读取器
    return new LineRecordReader(recordDelimiterBytes);
  }
  
  
```

>  input.initialize(split, mapperContext);上面 初始化reader,设置拉取位置

```java
// input.initialize(split, mapperContext);上面 初始化reader,设置拉取位置
   @Override
    public void initialize(org.apache.hadoop.mapreduce.InputSplit split,
                           org.apache.hadoop.mapreduce.TaskAttemptContext context
                           ) throws IOException, InterruptedException {
      long bytesInPrev = getInputBytes(fsStats);
      real.initialize(split, context);
      long bytesInCurr = getInputBytes(fsStats);
      fileInputByteCounter.increment(bytesInCurr - bytesInPrev);
    }
	  
	  public void initialize(InputSplit genericSplit,
                         TaskAttemptContext context) throws IOException {
    FileSplit split = (FileSplit) genericSplit;
    Configuration job = context.getConfiguration();
    this.maxLineLength = job.getInt(MAX_LINE_LENGTH, Integer.MAX_VALUE);
    start = split.getStart();
    end = start + split.getLength();
    final Path file = split.getPath();

    // open the file and seek to the start of the split
    final FileSystem fs = file.getFileSystem(job);
    fileIn = fs.open(file);
    
    CompressionCodec codec = new CompressionCodecFactory(job).getCodec(file);
    if (null!=codec) {
      isCompressedInput = true;	
      decompressor = CodecPool.getDecompressor(codec);
      if (codec instanceof SplittableCompressionCodec) {
        final SplitCompressionInputStream cIn =
          ((SplittableCompressionCodec)codec).createInputStream(
            fileIn, decompressor, start, end,
            SplittableCompressionCodec.READ_MODE.BYBLOCK);
        in = new CompressedSplitLineReader(cIn, job,
            this.recordDelimiterBytes);
        start = cIn.getAdjustedStart();
        end = cIn.getAdjustedEnd();
        filePosition = cIn;
      } else {
        in = new SplitLineReader(codec.createInputStream(fileIn,
            decompressor), job, this.recordDelimiterBytes);
        filePosition = fileIn;
      }
    } else {
      fileIn.seek(start);
      in = new UncompressedSplitLineReader(
          fileIn, job, this.recordDelimiterBytes, split.getLength());
      filePosition = fileIn;
    }

	
	//这里是让每一个分片第一行数据往后面读取一行，排除第一个分片；
	//原因是多个分片可能会存在某一个行被切到两个分片去了
    if (start != 0) {
      start += in.readLine(new Text(), 0, maxBytesToConsume(start));
    }
    //赋值起始位置偏移量
    this.pos = start;
  }  
```

**总结：**

- 创建一个lineRecordReader读取器，初始化当前分片的起始偏移量
- 默认除了第一个分片都会多读取一行偏移量

## 2.2 数据处理

> ```java
> Mapper#run
> mapper.run(mapperContext);
> ```



```java
public void run(Context context) throws IOException, InterruptedException {
    setup(context);
    try {
        
        while (context.nextKeyValue()) {   //判定是否有下一个值计算
            //实际的业务逻辑处理，这里交给我们自己处理
            map(context.getCurrentKey(), context.getCurrentValue(), context);
        }
    } finally {
        cleanup(context);
    }
}
```

Mapper#nextKeyValue ->MapContextImpl#nextKeyValue
--> LineRecordReader#nextKeyValue

很明显发现这是行读取器读取数据，详细看这个行处理器处理数据

```java

  //判定是否还有下一个值，并且有的话会直接拉取值
  public boolean nextKeyValue() throws IOException {
    if (key == null) {
      key = new LongWritable();
    }
    key.set(pos);  //初始化的时候pos就是当前分片的起始偏移量
    if (value == null) {
      value = new Text();
    }
    int newSize = 0;
    // We always read one extra line, which lies outside the upper
    // split limit i.e. (end - 1)
    while (getFilePosition() <= end || in.needAdditionalRecordAfterSplit()) {
      if (pos == 0) {
        newSize = skipUtfByteOrderMark();
      } else {
		//将当前行读取赋值给vlaue值
        newSize = in.readLine(value, maxLineLength, maxBytesToConsume(pos));
		//增大偏移量
        pos += newSize;
      }

      if ((newSize == 0) || (newSize < maxLineLength)) {
        break;
      }

      // line too long. try again
      LOG.info("Skipped line of size " + newSize + " at pos " + 
               (pos - newSize));
    }
	//如果没有数据则返回false，赋值key,value =null
    if (newSize == 0) {
      key = null;
      value = null;
      return false;
    } else {
      return true;
    }
  }
```

所以上面nextKeyValue就是通过拉取值来判定是否还有数据。

业务逻辑交给我们map自定义处理



实际我们使用context#write写出的时候，是写出key，value,partition的;

```java
//MapTask   
@Override
    public void write(K key, V value) throws IOException, InterruptedException {
      collector.collect(key, value,
                        partitioner.getPartition(key, value, partitions));
    }
```





## 2.3 排序输出

mapreduce会通过两种方式排序输出：

- 一个是后台启动线程定时从缓冲区排序然后输出到磁盘中

- close处理

```java
	//输出对象创建   
   job.setBoolean(JobContext.SKIP_RECORDS, isSkipping());
    org.apache.hadoop.mapreduce.RecordWriter output = null;
    
  
    if (job.getNumReduceTasks() == 0) {
	  //如果没有reduce操作就是这个对象
      output = 
        new NewDirectOutputCollector(taskContext, job, umbilical, reporter);
    } else {
	   //默认值为1，所以我们分析这个
      output = new NewOutputCollector(taskContext, job, umbilical, reporter);
    }
	
	// 分析NewOutputCollector创建
	 NewOutputCollector(org.apache.hadoop.mapreduce.JobContext jobContext,
                       JobConf job,
                       TaskUmbilicalProtocol umbilical,
                       TaskReporter reporter
                       ) throws IOException, ClassNotFoundException {
      collector = createSortingCollector(job, reporter);
	  //reducer的数量就是分区数目
      partitions = jobContext.getNumReduceTasks(); 
      if (partitions > 1) {
	    //使用HashPartitioner 分区
        partitioner = (org.apache.hadoop.mapreduce.Partitioner<K,V>)
          ReflectionUtils.newInstance(jobContext.getPartitionerClass(), job);
      } else {
		//只有一个的话，直接返回一个
        partitioner = new org.apache.hadoop.mapreduce.Partitioner<K,V>() {
          @Override
          public int getPartition(K key, V value, int numPartitions) {
            return partitions - 1;
          }
        };
      }
    }
	
	
	//如下实现，使用key的hash值来做partition分区
	public class HashPartitioner<K, V> extends Partitioner<K, V> {

	  /** Use {@link Object#hashCode()} to partition. */
	  public int getPartition(K key, V value,
							  int numReduceTasks) {
		return (key.hashCode() & Integer.MAX_VALUE) % numReduceTasks;
	  }

	}
```



我们简单看下后台线程执行

```java
  protected class SpillThread extends Thread {

      @Override
      public void run() {
        spillLock.lock();
        spillThreadRunning = true;
        try {
          while (true) {
            spillDone.signal();
            while (!spillInProgress) {
              spillReady.await();
            }
            try {
              spillLock.unlock();
              sortAndSpill();  //排序和写磁盘
            } catch (Throwable t) {
              sortSpillException = t;
            } finally {
              spillLock.lock();
              if (bufend < bufstart) {
                bufvoid = kvbuffer.length;
              }
              kvstart = kvend;
              bufstart = bufend;
              spillInProgress = false;
            }
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        } finally {
          spillLock.unlock();
          spillThreadRunning = false;
        }
      }
    }


    //文件多也会有reduce操作
    private void sortAndSpill() throws IOException, ClassNotFoundException,
                                       InterruptedException {
                
       。。。
       //快排序QuickSort      
       sorter.sort(MapOutputBuffer.this, mstart, mend, reporter);                               //写磁盘  conbine,
   }
```



# 3.wordcount 的Reduce源码

​    shffule 拉取数据 多线程拉取数据
​	sort 排序分组
​    merge 
​	reduce

具体是ReduceTask做处理



```java
  public void run(JobConf job, final TaskUmbilicalProtocol umbilical)
    throws IOException, InterruptedException, ClassNotFoundException {
    job.setBoolean(JobContext.SKIP_RECORDS, isSkipping());

    if (isMapOrReduce()) {
      copyPhase = getProgress().addPhase("copy");
      sortPhase  = getProgress().addPhase("sort");
      reducePhase = getProgress().addPhase("reduce");
    }
    // start thread that will handle communication with parent
    TaskReporter reporter = startReporter(umbilical);
    
    boolean useNewApi = job.getUseNewReducer();
    initialize(job, getJobID(), reporter, useNewApi);

    // check if it is a cleanupJobTask
    if (jobCleanup) {
      runJobCleanupTask(umbilical, reporter);
      return;
    }
    if (jobSetup) {
      runJobSetupTask(umbilical, reporter);
      return;
    }
    if (taskCleanup) {
      runTaskCleanupTask(umbilical, reporter);
      return;
    }
    
    // Initialize the codec
    codec = initCodec();
    RawKeyValueIterator rIter = null;
    ShuffleConsumerPlugin shuffleConsumerPlugin = null;
    
    Class combinerClass = conf.getCombinerClass();
    CombineOutputCollector combineCollector = 
      (null != combinerClass) ? 
     new CombineOutputCollector(reduceCombineOutputCounter, reporter, conf) : null;

    Class<? extends ShuffleConsumerPlugin> clazz =
          job.getClass(MRConfig.SHUFFLE_CONSUMER_PLUGIN, Shuffle.class, ShuffleConsumerPlugin.class);
					
    shuffleConsumerPlugin = ReflectionUtils.newInstance(clazz, job);
    LOG.info("Using ShuffleConsumerPlugin: " + shuffleConsumerPlugin);

    ShuffleConsumerPlugin.Context shuffleContext = 
      new ShuffleConsumerPlugin.Context(getTaskID(), job, FileSystem.getLocal(job), umbilical, 
                  super.lDirAlloc, reporter, codec, 
                  combinerClass, combineCollector, 
                  spilledRecordsCounter, reduceCombineInputCounter,
                  shuffledMapsCounter,
                  reduceShuffleBytes, failedShuffleCounter,
                  mergedMapOutputsCounter,
                  taskStatus, copyPhase, sortPhase, this,
                  mapOutputFile, localMapFiles);
    shuffleConsumerPlugin.init(shuffleContext);
	
      //这里就是shuffle去拉取map数据，具体就不分析了，多线程执行
    rIter = shuffleConsumerPlugin.run();

    // free up the data structures
    mapOutputFilesOnDisk.clear();
    
    sortPhase.complete();                         // sort is complete
    setPhase(TaskStatus.Phase.REDUCE); 
    statusUpdate(umbilical);
    Class keyClass = job.getMapOutputKeyClass(); //key 输出
    Class valueClass = job.getMapOutputValueClass(); //value 输出
    //看下这个排序  
    RawComparator comparator = job.getOutputValueGroupingComparator();

    if (useNewApi) {
      runNewReducer(job, umbilical, reporter, rIter, comparator, 
                    keyClass, valueClass);
    } else {
      runOldReducer(job, umbilical, reporter, rIter, comparator, 
                    keyClass, valueClass);
    }

    shuffleConsumerPlugin.close();
    done(umbilical, reporter);
  }
```



## 3.1 排序分组器的使用

```java
  public RawComparator getOutputValueGroupingComparator() {
    Class<? extends RawComparator> theClass = getClass(
      JobContext.GROUP_COMPARATOR_CLASS, null, RawComparator.class);
    if (theClass == null) { //先查询配置的组排序
      return getOutputKeyComparator(); //如果不存在就使用key排序器
    }
    
    return ReflectionUtils.newInstance(theClass, this);
  }

  //key的排序器
  public RawComparator getOutputKeyComparator() {
    Class<? extends RawComparator> theClass = getClass(
      JobContext.KEY_COMPARATOR, null, RawComparator.class);
    if (theClass != null)
      return ReflectionUtils.newInstance(theClass, this);
      
    //没有找到就找map的输出的key排序器  
    return WritableComparator.get(getMapOutputKeyClass().asSubclass(WritableComparable.class), this);
  }

  //上面通过key排序仍然没有找到，就使用map输出的key排序器
  public Class<?> getMapOutputKeyClass() {
    Class<?> retv = getClass(JobContext.MAP_OUTPUT_KEY_CLASS, null, Object.class);
    if (retv == null) {
      retv = getOutputKeyClass();
    }
    return retv;
  }

```



## 3.2 merge业务操作

还是查看新api使用

```java
  private <INKEY,INVALUE,OUTKEY,OUTVALUE>
  void runNewReducer(JobConf job,
                     final TaskUmbilicalProtocol umbilical,
                     final TaskReporter reporter,
                     RawKeyValueIterator rIter,
                     RawComparator<INKEY> comparator,
                     Class<INKEY> keyClass,
                     Class<INVALUE> valueClass
                     ) throws IOException,InterruptedException, 
                              ClassNotFoundException {
    // wrap value iterator to report progress.
    final RawKeyValueIterator rawIter = rIter;
    rIter = new RawKeyValueIterator() {
      public void close() throws IOException {
        rawIter.close();
      }
      public DataInputBuffer getKey() throws IOException {
        return rawIter.getKey();
      }
      public Progress getProgress() {
        return rawIter.getProgress();
      }
      public DataInputBuffer getValue() throws IOException {
        return rawIter.getValue();
      }
      public boolean next() throws IOException {
        boolean ret = rawIter.next();
        reporter.setProgress(rawIter.getProgress().getProgress());
        return ret;
      }
    };
    // make a task context so we can get the classes
    org.apache.hadoop.mapreduce.TaskAttemptContext taskContext =
      new org.apache.hadoop.mapreduce.task.TaskAttemptContextImpl(job,
          getTaskID(), reporter);
    // make a reducer
    org.apache.hadoop.mapreduce.Reducer<INKEY,INVALUE,OUTKEY,OUTVALUE> reducer =
      (org.apache.hadoop.mapreduce.Reducer<INKEY,INVALUE,OUTKEY,OUTVALUE>)
        ReflectionUtils.newInstance(taskContext.getReducerClass(), job);
    org.apache.hadoop.mapreduce.RecordWriter<OUTKEY,OUTVALUE> trackedRW = 
      new NewTrackingRecordWriter<OUTKEY, OUTVALUE>(this, taskContext);
    job.setBoolean("mapred.skip.on", isSkipping());
    job.setBoolean(JobContext.SKIP_RECORDS, isSkipping());
    org.apache.hadoop.mapreduce.Reducer.Context 
         reducerContext = createReduceContext(reducer, job, getTaskID(),
                                               rIter, reduceInputKeyCounter, 
                                               reduceInputValueCounter, 
                                               trackedRW,
                                               committer,
                                               reporter, comparator, keyClass,
                                               valueClass);
    try {
       //Reducer 的run方法
      reducer.run(reducerContext);
    } finally {
      trackedRW.close(reducerContext);
    }
  }
```



```java
  public void run(Context context) throws IOException, InterruptedException {
    setup(context);
    try {
      while (context.nextKey()) {  //看看这里代码实现
        //这里的接口留个用户业务实现  
        reduce(context.getCurrentKey(), context.getValues(), context);
        // If a back up store is used, reset it
        Iterator<VALUEIN> iter = context.getValues().iterator();
        if(iter instanceof ReduceContext.ValueIterator) {
          ((ReduceContext.ValueIterator<VALUEIN>)iter).resetBackupStore();        
        }
      }
    } finally {
      cleanup(context);
    }
  }


//WrappedReducer#nextKey -->ReduceContextImpl#nextKey

  public boolean nextKey() throws IOException,InterruptedException {
    while (hasMore && nextKeyIsSame) { //还有多的并且key是同一组的
      nextKeyValue();
    }
    if (hasMore) {
      if (inputKeyCounter != null) {
        inputKeyCounter.increment(1);
      }
      return nextKeyValue(); //否则获取下一个数据
    } else {
      return false;
    }
  }


  public boolean nextKeyValue() throws IOException, InterruptedException {
    if (!hasMore) {   //除了这里直接false
      key = null;
      value = null;
      return false;
    }
    //中间这一块代码主要是比较当前key和下一个key是否属于同一组，并且获取key值，value值
    //主要用于迭代的时候使用
    firstValue = !nextKeyIsSame; 
      
    //这里将key，value持久化，并反序列化为数据  
    DataInputBuffer nextKey = input.getKey();
    currentRawKey.set(nextKey.getData(), nextKey.getPosition(), 
                      nextKey.getLength() - nextKey.getPosition());
    buffer.reset(currentRawKey.getBytes(), 0, currentRawKey.getLength());
    key = keyDeserializer.deserialize(key);
    DataInputBuffer nextVal = input.getValue();
    buffer.reset(nextVal.getData(), nextVal.getPosition(), nextVal.getLength()
        - nextVal.getPosition());
    value = valueDeserializer.deserialize(value);

    currentKeyLength = nextKey.getLength() - nextKey.getPosition();
    currentValueLength = nextVal.getLength() - nextVal.getPosition();

    if (isMarked) {
      backupStore.write(nextKey, nextVal);
    }

    hasMore = input.next();
    if (hasMore) {
      nextKey = input.getKey();
      //预先比较下一个值相等，表示属于同一组，这里就是组比较器  
      nextKeyIsSame = comparator.compare(currentRawKey.getBytes(), 0, 
                                     currentRawKey.getLength(),
                                     nextKey.getData(),
                                     nextKey.getPosition(),
                                     nextKey.getLength() - nextKey.getPosition()
                                         ) == 0;
    } else {
      nextKeyIsSame = false;
    }
    inputValueCounter.increment(1);
    return true; //直接返回true
  }
```

如下保证相同group的key在一起这一组key调用一次reduce方法

```java
	    
@Override
    protected void reduce(Text key, Iterable<LongWritable> values, Context context) throws IOException, InterruptedException {

        Long sum = 0L;
        for (LongWritable value : values) { //这里我们会调用values的迭代器做迭代
            //累计单词数量
            sum+= value.get();
        }
        //输出结果
        context.write(key,new LongWritable(sum));
    }

// context.getValues() == 》 ValueIterable iterable

//实际上是调用ReduceContextImpl中  ValueIterable iterable 变量的ValueIterator
//ValueIterator
  public VALUEIN next() {
      if (inReset) {
        try {
          if (backupStore.hasNext()) {
            backupStore.next();
            DataInputBuffer next = backupStore.nextValue();
            buffer.reset(next.getData(), next.getPosition(), next.getLength()
                - next.getPosition());
            value = valueDeserializer.deserialize(value);
            return value;
          } else {
            inReset = false;
            backupStore.exitResetMode();
            if (clearMarkFlag) {
              clearMarkFlag = false;
              isMarked = false;
            }
          }
        } catch (IOException e) {
          e.printStackTrace();
          throw new RuntimeException("next value iterator failed", e);
        }
      } 

      // if this is the first record, we don't need to advance
      if (firstValue) {  //这里就表示是新的一个group，那么直接放回，这个数据是在上一次迭代就把值判定了
        firstValue = false;
        return value;
      }
      // if this isn't the first record and the next key is different, they
      // can't advance it here.
      if (!nextKeyIsSame) {
        throw new NoSuchElementException("iterate past last value");
      }
      // otherwise, go to the next key/value pair
      try {  //真正的迭代处理
        nextKeyValue(); //每一次我们的values调用next值的时候，就通过这个迭代新的value值
        return value;
      } catch (IOException ie) {
        throw new RuntimeException("next value iterator failed", ie);
      } catch (InterruptedException ie) {
        // this is bad, but we can't modify the exception list of java.util
        throw new RuntimeException("next value iterator interrupted", ie);        
      }
    }

```

总结：

- 通过分组sort排序器做排序，可以自定义

- sort排序是预先通过当前数据和下一条数据做排序，不会占用过多的内存，所以这里其实还是会依赖map的排序。
- merge业务操作的next值实际是在我们迭代value的时候做真正的判定数据是否在一组。然后迭代，否则即使下一组。

