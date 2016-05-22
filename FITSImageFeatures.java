//package org.fits;
import org.apache.hadoop.mapreduce.lib.input.*;
import java.nio.charset.Charset;
import org.apache.hadoop.io.IOUtils;
import java.io.IOException;
import java.util.StringTokenizer;
import featanalysis.ImageFeatures;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import org.json.JSONArray;
import org.json.JSONObject;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.GenericOptionsParser;
import edu.jhu.pha.sdss.fits.FITSImage.DataTypeNotSupportedException;
import edu.jhu.pha.sdss.fits.FITSImage.NoImageDataFoundException;
import nom.tam.fits.FitsException;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.io.LongWritable;
import java.util.Arrays;

public class FITSImageFeatures {

  public static class SeqFileMapper 
       extends Mapper<Text , BytesWritable , Text , Text>{
    
    public void map(Text key, BytesWritable value, Context context
                    ) throws IOException, InterruptedException {

	 InputStream in = new ByteArrayInputStream(Arrays.copyOfRange(value.getBytes(),0,value.getLength()));
//	 InputStream in = new ByteArrayInputStream(Arrays.copyOfRange(value.getBytes(),0,value.getBytes().length));

//	 FileOutputStream outStream = new FileOutputStream("1.fit");
//         outStream.write(Arrays.copyOfRange(value.getBytes(),12,(value.getBytes().length)-12));
	Configuration conf = new Configuration();
//	System.out.println(value.getBytes().length + "		" + value.getLength());
	System.out.println(java.lang.Runtime.getRuntime().totalMemory());
        System.out.println(java.lang.Runtime.getRuntime().freeMemory());
	System.out.println(java.lang.Runtime.getRuntime().maxMemory());
	 ImageFeatures ptf = new ImageFeatures();
	 try{
         ptf.generate(in);
	 }
	 catch(Exception e){}
         JSONObject o = ptf.encode();
	 
	Text v=new Text(o.toString());
	//Text v=new Text("asdf");
	context.write(key,v);
      }
}

  public static void main(String[] args) throws Exception,
FitsException, DataTypeNotSupportedException, NoImageDataFoundException, IOException {
    Configuration conf = new Configuration();
    String[] otherArgs = new GenericOptionsParser(conf, args).getRemainingArgs();
    if (otherArgs.length != 2) {
      System.err.println("Usage: FITSImageFeatures  <in> <out>");
      System.exit(2);
    }
    Job job = new Job(conf, "FITSImageFeatures");
    job.setJarByClass(FITSImageFeatures.class);
    job.setMapperClass(SeqFileMapper.class);
    job.setNumReduceTasks(0);
    System.out.println("map tasks: "+conf.get("mapreduce.job.maps"));
    System.out.println(java.lang.Runtime.getRuntime().totalMemory());
        System.out.println(java.lang.Runtime.getRuntime().freeMemory());
        System.out.println(java.lang.Runtime.getRuntime().maxMemory());
    job.setOutputKeyClass(Text.class);
    job.setOutputValueClass(Text.class);
    job.setInputFormatClass(SequenceFileInputFormat.class);
    SequenceFileInputFormat.addInputPath(job, new Path(otherArgs[0]));
    FileOutputFormat.setOutputPath(job, new Path(otherArgs[1]));
    System.exit(job.waitForCompletion(true) ? 0 : 1);
  }
}
