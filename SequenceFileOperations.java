import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URI;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.IOUtils;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.util.ReflectionUtils;
import java.io.FileOutputStream;
import org.apache.hadoop.io.Writable.*;
import java.io.FileOutputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.util.Arrays;

public class SequenceFileOperations {

	private Configuration conf = new Configuration();
	private SequenceFile.Writer sequenceFileWriter;
	private FileSystem fs;
	{
		try {
			fs = FileSystem.get(URI.create("hdfs://nimbus-head:54310"), conf);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	void addDocsToSeqFile(String SrcDir, String seqFilePath) throws IOException {
		File srcDir = new File(SrcDir);
		System.out.println("start  " + SrcDir);
		if (!srcDir.isDirectory()) {
			System.out.println("Please provide an absolute path of a directory");
			return;
		}
		try {

			sequenceFileWriter = SequenceFile.createWriter(fs, conf, new Path(
                                        seqFilePath), Text.class, BytesWritable.class);
			File[] documents = srcDir.listFiles();

			for (File document : documents) {
				System.out.println(document);
				subFunction(document);

			}

		} finally {
			 sequenceFileWriter.close();
		}
		System.out.println(java.lang.Runtime.getRuntime().totalMemory());
        System.out.println(java.lang.Runtime.getRuntime().freeMemory());
        System.out.println(java.lang.Runtime.getRuntime().maxMemory());
	}

	void subFunction(File document) {
		if (document.isFile()) {
			System.out.println(document.getAbsolutePath());
			try {
				RandomAccessFile rf = new RandomAccessFile(document, "r");
				byte[] content = new byte[(int) rf.length()];
				rf.readFully(content);
//				rf.read(content);

				sequenceFileWriter.append(new Text(document.getAbsolutePath()), new BytesWritable(
						content));

				rf.close();
			} catch (Exception e) {
			}
		} else {
			File[] docs = document.listFiles();
			for (File doc : docs) {
				subFunction(doc);
			}
		}

	}

	private void readSeqFile(String seqFilePath, String downloadFile)
			throws IOException {

		SequenceFile.Reader seqFileReader = new SequenceFile.Reader(fs,
				new Path(seqFilePath), conf);
		File outFile = new File(downloadFile);
		outFile.delete();
		int i = 0;

		Writable key = (Writable) ReflectionUtils.newInstance(
				seqFileReader.getKeyClass(), conf);

		BytesWritable value = (BytesWritable) ReflectionUtils.newInstance(
				seqFileReader.getValueClass(), conf);
		try {

			while (seqFileReader.next(key, value)) {

				System.out.println(value.getClass() + "," + key.toString());
				System.out.println(value.getBytes().length +"	"+value.getLength());
				/*if (downloadFile.equals(key.toString().substring(0,
						key.toString().indexOf(',')))) {

					if (outFile.exists()) {
						i++;
						DataOutputStream outStream = new DataOutputStream(
								new FileOutputStream(downloadFile.toString()
										+ i));

						value.write(outStream);
					} else {
						DataOutputStream outStream = new DataOutputStream(
								new FileOutputStream(downloadFile.toString()));

						value.write(outStream);
					}

				}*/
				DataOutputStream outStream = new DataOutputStream(
                                                                new FileOutputStream(i+".fit"));

//				FileOutputStream outStream=new FileOutputStream(i+".fit");

// 				value.write(outStream);
//				outStream.write(value.getBytes());
//				outStream.write(Arrays.copyOfRange(value.getBytes(),0,value.getLength()));
				outStream.write(Arrays.copyOfRange(value.getBytes(),0,value.getLength()));

				i++;
			}
		} finally {
			seqFileReader.close();
		}
	}

	public static void main(String[] args) throws IOException {

		SequenceFileOperations SeqFile = new SequenceFileOperations();

		String SrcDir = args[0];
		String seqFilePath = args[1];
		String downloadFile = args[2];
		System.out.println("Writing files present at " + SrcDir
				+ " to the sequence file " + seqFilePath);

		SeqFile.addDocsToSeqFile(SrcDir,seqFilePath);
//		SeqFile.readSeqFile(seqFilePath, downloadFile);

	}
}

