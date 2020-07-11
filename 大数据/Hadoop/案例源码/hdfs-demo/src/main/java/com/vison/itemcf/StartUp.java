package com.vison.itemcf;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.util.hash.Hash;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Vison
 * @date 2020/7/11 12:52
 * @Version 1.0
 */
public class StartUp {

    public static void main(String[] args)throws Exception {

        Configuration configuration = new Configuration(true);
        configuration.set("fs.defaultFS","hdfs://192.168.0.131:9000");
        Map<String, Path> paths = new HashMap<>();

        paths.put("step1Input",new Path("/user/root/itemcf/input/step1"));
        paths.put("step1Output",new Path("/user/root/itemcf/output/step1"));

        paths.put("step2Input",paths.get("step1Output"));
        paths.put("step2Output",new Path("/user/root/itemcf/output/step2"));

        paths.put("step3Input",paths.get("step2Output"));
        paths.put("step3Output",new Path("/user/root/itemcf/output/step3"));

        paths.put("step4Input1",paths.get("step2Output"));
        paths.put("step4Input2",paths.get("step3Output"));
        paths.put("step4Output",new Path("/user/root/itemcf/output/step4"));

        paths.put("step5Input",paths.get("step4Output"));
        paths.put("step5Output",new Path("/user/root/itemcf/output/step5"));

        paths.put("step6Input",paths.get("step5Output"));
        paths.put("step6Output",new Path("/user/root/itemcf/output/step6"));

        Step1.run(configuration,paths);  //1.去重数据
        Step2.run(configuration,paths);  //2.计算每一个用户 所有商品的评分，即用户评分向量
        Step3.run(configuration,paths);  //3.计算商品和商品之间的关系-同向矩阵图数据
        Step4.run(configuration,paths);  //4.将2,3得到的结果向量汇总计算
        Step5.run(configuration,paths);  //5.最终统计用户所有商品的推荐分数

    }

    public static Map<String,Integer> RATE = new HashMap<>();
    static {
        RATE.put("click",1);
        RATE.put("collect",2);
        RATE.put("cart",3);
        RATE.put("alipay",4);
    }


}
