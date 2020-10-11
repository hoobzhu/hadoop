package cn.hoob.structured_streaming;

import cn.hoob.structured_streaming.model.Words;
import org.apache.spark.api.java.function.FlatMapFunction;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Encoders;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.catalyst.encoders.ExpressionEncoder;
import org.apache.spark.sql.streaming.StreamingQuery;
import org.apache.spark.sql.streaming.Trigger;
import scala.Tuple2;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author zhuqinhe
 * complete：完整模式，全部输出，必须有聚合才可以使用，
 * append：追加模式，只输出那些将来永远不可能再更新的数据。没有聚合的时候，append和update是一样的，有聚合的时候，一定要有水印才能使用append。
 * update：只输出更新的模式，只输出变化的部分，也就是哪一条数据发生了变化，就输出哪一条数据
 */
public class WordCountNewSocketStructuredStreaming {
    public static void main(String[] args) throws Exception {
        //获取sparkSession
        SparkSession sparkSession= SparkSession.builder().master("local[*]").appName("WordCountSocketStructuredStreaming").getOrCreate();
        Dataset<Row> streamingdata = sparkSession.readStream()  //读一个流数据，lines其实就是一个输入表
                .format("socket") //指定这个流的格式
                .option("host", "hdp1")   //指定socket的地址和端口号
                .option("port", 10000)
                .option("includeTimestamp",true)  //给产生的数据自动添加时间戳
                .load();
       // Dataset<Words>strdata = streamingdata.as(ExpressionEncoder.javaBean(Words.class)); //类型转换
        Dataset<Words>strdata = streamingdata.as(Encoders.bean(Words.class));
        //Dataset获取RDD 等本质对应的时iterator
        Dataset<Words> flatmStrData = strdata.flatMap((FlatMapFunction<Words, Words>)
                        line -> {
                            String words[] = line.getValue().split(",");
                            List<Words> wordsList=new ArrayList<Words>();
                            if(words!=null&&words.length>0){
                                for (String word:words) {
                                    //重新构建每一个单词
                                    wordsList.add(new Words(line.getTimestamp(),word));
                                }
                            }
                            return wordsList.iterator();
                        },
                Encoders.bean(Words.class));
        //Dataset<Row> countData = flatmStrData.groupBy("value").count();
         //sql的也可以采用写SQL的方式
        flatmStrData.createOrReplaceTempView("words");
        Dataset<Row> countData =sparkSession.sql("select first(timestamp),value as word,count(value) as count from words group by value ");

        StreamingQuery result = countData.writeStream().format("console")  //指定输出到哪，这里输出到控制台
                .outputMode("update") //输出模式，输出模式有三种：complete append  update
                .trigger(Trigger.ProcessingTime("60 seconds")) //多长时间触发一次，如果不写的话就是尽快处理
                .start();
        result.awaitTermination();//阻止当前线程退出
        sparkSession.stop();
    }
}
