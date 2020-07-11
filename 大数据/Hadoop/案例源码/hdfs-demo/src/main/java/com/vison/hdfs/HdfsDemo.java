package com.vison.hdfs;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.BlockLocation;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IOUtils;

import java.io.IOException;

/**
 * @author Vison
 * @date 2020/7/2 14:35
 * @Version 1.0
 */
public class HdfsDemo {

    //这个地址就是nameserver的地址，即core.site.xml中fs.defaultFS的值
   // private static final String HDFA_PATH = "hdfs://192.168.124.158:8020";
    private FileSystem  fileSystem= null;


    public void setUp()  {
        Configuration configuration = new Configuration();
        configuration.set("fs.defaultFS","hdfs://192.168.4.131:9000");

        //1.如果不添加这个，无法和datanode通信导致上传文件不成功；
        //2.需要配置本地hosts文件做ip映射；如果nameNode和dataNode使用内网ip(hostname)通信，那么本地获取的是内网ip(hostname)
        // 那么需要配置本地hosts文件做ip映射，比如我的是本地虚拟机的通过node1的通信，我直接配置了 192.168.4.131 node1
        configuration.set("dfs.client.use.datanode.hostname","true");

        // 这里用于测试，设置块的大小为1M
        configuration.set("dfs.block.size","1048576");
        try {
            //URI uri = new URI(HDFA_PATH);
            fileSystem =  FileSystem.get(configuration);
            System.out.println(fileSystem);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * 获取块大小
     * @param path
     * @return
     * @throws IOException
     */
    public  long getDefaultBlockSize(String path) throws IOException {
        try {
            return fileSystem.getDefaultBlockSize(new Path(path));
        }finally {
            fileSystem.close();
        }
    }

    /**
     * 删除文件
     * @param path
     * @throws IOException
     */
    public  boolean delete(String path) throws IOException {
        try {
            return fileSystem.delete(new Path(path), false);
        }finally {
            fileSystem.close();
        }
    }

    /**
     * 获取块位置
     * @param path
     * @return
     * @throws IOException
     */
    public  void getFileBlockLocations(String path) throws IOException {
        try {
            FileStatus fileStatus = fileSystem.getFileStatus(new Path(path));

            BlockLocation[] blks = fileSystem.getFileBlockLocations(fileStatus, 0, fileStatus.getLen());
            for (BlockLocation blk : blks) { //查看块信息
                System.err.println("--------"+blk);
                //会打印出类似如下
                // 起始位置  长度    块存储的节点名
                // 0,      1048576,node1
                // 1048576,1048576,node1
                // 2097152,1048576,node1
                // 3145728,1048576,node1
                // 4194304,1048576,node1
                // 5242880,1048576,node1 ...
            }
        }finally {
            fileSystem.close();
        }
    }

    /**
     * 创建目录
     * @param path
     * @return
     * @throws IOException
     */
    public boolean mkdir(String path) throws IOException {
        try {
            return fileSystem.mkdirs(new Path(path));
        }finally {
            fileSystem.close();
        }
    }

    /**
     *
     * @param name
     * @return
     * @throws Exception
     */
    public boolean create(String name) throws Exception{
        try(FSDataOutputStream fos = fileSystem.create(new Path(name))) {
            fos.write("hello world".getBytes());
            fos.flush();
        }finally {
            fileSystem.close();
        }
        return true;
    }

    /**
     * 下载文件
     * @param name
     * @throws Exception
     */
    public void open(String name)throws Exception{
        try(FSDataInputStream fis = fileSystem.open(new Path(name))){
            IOUtils.copyBytes(fis,System.out,1024);
        }finally {
            fileSystem.close();
        }
    }

    /**
     * 上传文件
     * @throws Exception
     */
    public void copyFromLocalFile(String from,String toPath)throws Exception{
        try {
            fileSystem.copyFromLocalFile(new Path(from),
                    new Path(toPath)); //Windows系统和Linux系统都可以，写法不一样
        }finally {
            fileSystem.close();
        }
    }


    public static void main(String[] args) throws Exception{
        System.setProperty("HADOOP_USER_NAME","root"); //设置当前用户是root用户
        HdfsDemo hdfsDemo = new HdfsDemo();
        hdfsDemo.setUp();
//        boolean bool = hdfsDemo.mkdir("/vison");
//        System.out.println(bool);
//        boolean bool = hdfsDemo.create("/vison/v1.txt");
//        hdfsDemo.open("/vison/v1.txt");
        //上传
        hdfsDemo.copyFromLocalFile("C:\\Users\\vison\\Desktop\\sql.txt","/vison");
        //删除
      // boolean delete = hdfsDemo.delete("/vison/market-release.apk");
//        System.out.println("result: "+delete);

        //查看块位置
        //hdfsDemo.getFileBlockLocations("/user/root/hadoop-2.7.7.tar.gz");
        //获取块大小
//        long defaultBlockSize = hdfsDemo.getDefaultBlockSize("/user/root/hadoop-2.7.7.tar.gz");
//        System.out.println(defaultBlockSize);

    }
}
