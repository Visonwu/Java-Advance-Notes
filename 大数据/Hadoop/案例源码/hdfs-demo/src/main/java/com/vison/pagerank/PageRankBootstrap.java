package com.vison.pagerank;


import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.MRConfig;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.KeyValueTextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

import java.io.IOException;

/**
 * pagerank 代码实现
 *
 *
 * 原数据：第一个字符和后面通过 制表符 间隔
 *A B D
 *B C
 *C A B
 *D B C
 *B C
 *
 * -------------
 * map:
 * A    1.0 B D     表示老值pr和关系
 * B    0.5         表示票面值
 * D    0.5         表示票面值
 * ...
 *
 * -------------
 * reduce:
 * A    0.5 B D     表示新的pr值和关系
 * ...
 *
 * @author Vison
 * @date 2020/7/8 21:24
 * @Version 1.0
 */
public class PageRankBootstrap {

    public  enum MyCount {
        MY;
    }

    public static void main(String[] args) throws Exception {

        //这可以直接本地代码运行
        Configuration conf = new Configuration(true);
        conf.set("fs.defaultFS", "hdfs://192.168.4.131:9000");


        //表示异构平台的支撑，一般要打成jar包到服务器执行，这里表示可以支持本地windows,linux系统可以不用配置
        //conf.set(MRConfig.MAPREDUCE_APP_SUBMISSION_CROSS_PLATFORM, "true");
        //这里使用local而不是yarn（分布式），表示本地跑，使用单机模拟跑
         //conf.set(MRConfig.FRAMEWORK_NAME, "local");

        //--------------
        // 如果需要分布式启动，必须通过打jar打到分布式系统中才能执行


        double d = 0.001; //我们的均值小于0.001的时候就停止迭代
        int i = 0;
        while (true) {
            i++;
            try {
                conf.setInt("runCount", i); //设置job的环境参数，便于作业运行的时候获取，类似传参功能

                FileSystem fileSystem = FileSystem.get(conf);
                Job job = Job.getInstance(conf);
                job.setJobName("pageRank" + i);
                job.setJarByClass(PageRankBootstrap.class);
                //job.setJar("/xxx/oo.jar"); # 如果上面不是使用local，而是yarn即分布式，那么这里需要配置jar包

                //map
                Path input = new Path("/user/root/pagerank/input/");
                //根据计算的结果读取不同文件目录
                if (i > 1) {
                    input = new Path("/user/root/pagerank/output/pr-" + (i - 1));
                }
                FileInputFormat.addInputPath(job, input);


                //默认按照制表符切割字符，切割后的key，value都是TEXT类型；源码  String sepStr = conf.get(KEY_VALUE_SEPERATOR, "\t");
                job.setInputFormatClass(KeyValueTextInputFormat.class);
                job.setMapOutputKeyClass(Text.class);
                job.setMapOutputValueClass(Text.class);
                job.setMapperClass(PageRankMapper.class);

                //reduce
                Path output = new Path("/user/root/pagerank/output/pr-" + i);
                if (fileSystem.exists(output)) {
                    fileSystem.delete(output, true);
                }
                FileOutputFormat.setOutputPath(job, output);


                job.setOutputKeyClass(Text.class);
                job.setOutputValueClass(Text.class);

                job.setReducerClass(PageRankReduce.class);


                boolean b = job.waitForCompletion(true); //等待作业完成，再执行下一个作业
                if (b) {
                    System.out.println("success:" + i);

                    //这里是通过reduce操作将A,B,C,D四个页面分别的新旧PR值的差值计算出来然后加在一起
                    long sum = job.getCounters().findCounter(MyCount.MY).getValue();
                    System.out.println(sum);

                    double avg = sum / 4000.0;  //这里除以4用于平均，再除以1000的原因是上面计算差值的时候乘以了1000
                    if (avg < d) {
                        break;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }


        }

    }


    static class PageRankMapper extends Mapper<Text, Text, Text, Text> {

        @Override
        protected void map(Text key, Text value, Context context) throws IOException, InterruptedException {

            // A   B D
            // A   B D 0.3

            //K:A
            //V:B
            //K:A
            //V:0.3 B D
            String pageStr = key.toString();
            Node node = null;
            int runCount = context.getConfiguration().getInt("runCount", 1);
            if (runCount == 1) {
                node = Node.fromMR(1.0, value.toString());
            } else {
                node = Node.fromMR(value.toString());
            }

            //A   1.0 B D ; 传递老值
            context.write(new Text(pageStr), new Text(node.toString()));

            if (node.containsAdjacentNodes()) {
                //均值除以 节点数
                double outValue = node.getPageRank() / node.getAdjacentNodeNames().length;
                for (int i = 0; i < node.getAdjacentNodeNames().length; i++) {
                    String outPage = node.getAdjacentNodeNames()[i];
                    // B   0.5
                    // D   0.5 页面A投给谁，谁作为key，value就是票面值，票面值是：A的pr值除以超链接数
                    //这一步就是 PR(j)/L(j)
                    context.write(new Text(outPage), new Text(outValue + ""));
                }
            }

        }
    }

    static class PageRankReduce extends Reducer<Text, Text, Text, Text> {


        Text text = new Text();

        @Override
        protected void reduce(Text key, Iterable<Text> values, Context context) throws IOException, InterruptedException {

            //相同的key为一组
            //比如key:B
            //包含两类数据
            //B    1.0 C       页面关系和老值

            //B    0.5         投票值
            //B    0.5

            double sum = 0.0;          //计算总的票面值
            Node sourceNode = null;    //这个表示老值数据和关系
            for (Text value : values) {
                Node node = Node.fromMR(value.toString());
                if (node.containsAdjacentNodes()) {
                    sourceNode = node;
                } else {
                    sum += node.getPageRank();
                }
            }
            //

            // 按照公式计算PR；4位页面总数即：ABCD
            double newPR = (0.15 / 4) + (0.85 * sum);
            System.out.println(key.toString() + "----new PR---> " + newPR);

            //计算新的pr值和老pr值的差值
            double subPr = newPR - sourceNode.getPageRank();
            int j = (int) (subPr * 1000);  //这里扩大1000；后面会除去
            j = Math.abs(j);  //保证正整数

            //累计当前所有的key的差值，计算完了在对比
            context.getCounter(MyCount.MY).increment(j);

            //保存新值
            sourceNode.setPageRank(newPR);
            text.set(sourceNode.toString());
            context.write(key, text);

        }
    }

    /**
     * 包装一个页面的属性信息
     * pageRank表示pr值
     * adjacentNodeNames 表示出链页面
     */
    static class Node {

        private double pageRank = 1.0;

        private String[] adjacentNodeNames;

        @Override
        public String toString() {
            if (adjacentNodeNames == null || adjacentNodeNames.length == 0) {
                return pageRank + "";
            }
            return pageRank + " " + StringUtils.join(adjacentNodeNames, " ");
        }

        public static Node fromMR(double pageRank, String adjacentNodes) {
            Node node = new Node();
            node.setPageRank(pageRank);
            node.setAdjacentNodeNames(adjacentNodes.split(" "));
            return node;
        }

        /**
         * @param values value值，第一个是票面值，后续还有值就是
         * @return node值
         */
        public static Node fromMR(String values) {
            Node node = new Node();
            String[] str = values.split(" ");
            node.setPageRank(Double.parseDouble(str[0]));
            if (str.length > 1) {
                String[] adjs = new String[str.length - 1];
                System.arraycopy(str, 1, adjs, 0, str.length - 1);
                node.setAdjacentNodeNames(adjs);
            }
            return node;
        }

        public double getPageRank() {
            return pageRank;
        }

        public void setPageRank(double pageRank) {
            this.pageRank = pageRank;
        }

        public String[] getAdjacentNodeNames() {
            return adjacentNodeNames;
        }

        public void setAdjacentNodeNames(String[] adjacentNodeNames) {
            this.adjacentNodeNames = adjacentNodeNames;
        }

        public boolean containsAdjacentNodes() {
            return adjacentNodeNames != null && adjacentNodeNames.length > 0;
        }
    }
}
