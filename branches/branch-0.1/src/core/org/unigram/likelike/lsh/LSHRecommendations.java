/**
 * Copyright 2009 Takahiko Ito
 * 
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at 
 * 
 *    http://www.apache.org/licenses/LICENSE-2.0 
 *        
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the License for the specific language governing permissions and 
 * limitations under the License.
 */
package org.unigram.likelike.lsh;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Random;
import java.util.Vector;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;

import org.apache.hadoop.mapreduce.Counters;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.SequenceFileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.SequenceFileOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

import org.unigram.likelike.lsh.function.MinWiseFunction;
import org.unigram.likelike.common.Candidate;
import org.unigram.likelike.common.FsUtil;
import org.unigram.likelike.common.LikelikeConstants;
import org.unigram.likelike.common.LikelikeLogger;
import org.unigram.likelike.common.RelatedUsersWritable;

/**
 * Extract recommendations for input examples. 
 */
public class LSHRecommendations extends
    Configured implements Tool {
    
    /** logger. */
    private final LikelikeLogger logger 
        = LikelikeLogger.getLogger();

    /** random generator. */
    private final Random rand = new Random();
    
    /**
     * Run from ToolRunner.
     *  
     * @param args contains arguments
     * @return 0 when succeeded.
     * @throws IOException -
     * @throws InterruptedException -
     * @throws ClassNotFoundException -
     * @throws Exception -
     */
    public final int run(final String[] args) 
    throws IOException,
        InterruptedException, 
        ClassNotFoundException, Exception {
        this.setDefaultConfiguration();
        Configuration conf = getConf();
        return this.run(args, conf);
    }
    
    /**
     * Run.
     * @param args arguments
     * @param conf configuration
     * @return 0 when succeeded
     * @throws Exception -
     */
    public int run(final String[] args, final Configuration conf) 
    throws Exception {

        String inputFile = "";
        String outputPrefix = "";
        String clusterDir = "";
        int iterate = 1;
        int depth   = 0;
        int rowSize = 0;
        
        FileSystem fs = FileSystem.get(conf);

        for (int i = 0; i < args.length; ++i) {
            if ("-input".equals(args[i])) {
                inputFile = args[++i];
                clusterDir = inputFile + ".clusters";
            } else if ("-output".equals(args[i])) {
                outputPrefix = args[++i];
            } else if ("-depth".equals(args[i])) {
                conf.setInt(LikelikeConstants.FEATURE_DEPTH, 
                        Integer.parseInt(args[++i]));
            } else if ("-iterate".equals(args[i])) {
                iterate = Integer.parseInt(args[++i]);
            }  else if ("-maxCluster".equals(args[i])) {
                conf.setLong(LikelikeConstants.MAX_CLUSTER_SIZE, 
                        Long.parseLong(args[++i]));
            }  else if ("-minCluster".equals(args[i])) {
                conf.setLong(LikelikeConstants.MIN_CLUSTER_SIZE, 
                        Long.parseLong(args[++i]));
            } else if ("-maxRecommend".equals(args[i])) {
                conf.setLong(LikelikeConstants.MAX_OUTPUT_SIZE, 
                        Long.parseLong(args[++i]));
            } 
        }
        
        int numReducers = conf.getInt(
                LikelikeConstants.NUMBER_OF_REDUCES,
                    LikelikeConstants.DEFAULT_NUMBER_OF_REDUCES);
                this.logger.logInfo("Number of reducers: " + numReducers);
        
        Vector<Long> keys = new Vector<Long>();
        
        /* iterate to extract clusters */ 
        FsUtil.checkPath(new Path(clusterDir),
                FileSystem.get(conf));        
        for (int i =0; i < iterate; i++) {
            String clusterOutputFile = new String(
                    clusterDir+"/"+"iter"+Integer.toString(i));
            this.logger.logInfo("Extracting clusters: " + clusterOutputFile);
            Long hashKey = this.rand.nextLong();
            conf.setLong(MinWiseFunction.MINWISE_HASH_SEED, hashKey);
            keys.add(hashKey);
            Counters counters = this.extractClusters(inputFile, 
                    clusterOutputFile, conf);
            
            if (i == 0) {
                this.setResultConf(counters, conf);
            }
        }
        
         this.getRecommendations(clusterDir + "/*", 
                 outputPrefix, conf, fs);

         FsUtil.clean(FileSystem.get(conf), clusterDir);
         this.saveKeys(keys, inputFile, conf);
        return 0;
    }
  
    /**
     * Add the configuration information from the result of 
     * extract candidates to conf.
     * 
     * @param counters contains counter
     * @param conf configuration
     */
    private void setResultConf(final Counters counters, 
            final Configuration conf) {
        conf.setLong(LikelikeConstants.LIKELIKE_INPUT_RECORDS, 
                counters.findCounter(
                        LikelikeConstants.COUNTER_GROUP, 
                   "MAP_INPUT_RECORDS").getValue());    
        this.logger.logInfo("The number of record is " 
                + conf.getLong(
                        LikelikeConstants.LIKELIKE_INPUT_RECORDS, -1));
    }

    /**
     * Save keys for hash seed.
     * 
     * @param keys keys used as hash seed.
     * @param inputFile input file name of this program. The output file 
     * name for saving kesy is input file name + ".keys." 
     * @param conf configuration 
     * @throws IOException -
     */
    private void saveKeys(final Vector<Long> keys, 
            final String inputFile, final Configuration conf) 
    throws IOException {
        /* save to local fs */
        String tempKeyFile = new String("keys.tmp");
        try {
            FileOutputStream fos = new FileOutputStream(tempKeyFile);
            OutputStreamWriter osw = new OutputStreamWriter(fos , "UTF-8");
            BufferedWriter bw = new BufferedWriter(osw);
            for (Long key : keys) {
                bw.write(Long.toString(key)+"\n");
            }
            bw.close();
            osw.close();
            fos.close();
        } catch (Exception e) {
          e.printStackTrace();
        }

        /* put local file to hdfs */
        FileSystem fs = FileSystem.get(conf);
        Path localKeyFilePath = new Path(tempKeyFile);
        Path hdfsKeyFilePath = new Path(inputFile + ".keys");
        fs.copyFromLocalFile(localKeyFilePath, hdfsKeyFilePath);
        
        /* remove local file*/
        fs.delete(localKeyFilePath, true);
        
        return;
    }

    /**
     * Get items to be recommended.
     * 
     * @param inputDir input 
     * @param outputFile output
     * @param conf configuration
     * @param fs file system
     * @return true when succeeded otherwise false
     * @throws IOException -
     * @throws InterruptedException -
     * @throws ClassNotFoundException -
     */
    private boolean getRecommendations(final String inputDir,
            final String outputFile, final Configuration conf, 
            final FileSystem fs) 
    throws IOException, InterruptedException, 
    ClassNotFoundException {
        this.logger.logInfo("Extracting recommendation to " + inputDir);
        Path inputPath = new Path(inputDir);
        Path outputPath = new Path(outputFile);
        FsUtil.checkPath(outputPath, FileSystem.get(conf));

        Job job = new Job(conf);
        job.setJarByClass(LSHRecommendations.class);
        FileInputFormat.addInputPath(job, inputPath);
        FileOutputFormat.setOutputPath(job, outputPath);
        job.setMapperClass(GetRecommendationsMapper.class);
        job.setReducerClass(GetRecommendationsReducer.class);
        job.setMapOutputKeyClass(LongWritable.class);
        job.setMapOutputValueClass(Candidate.class);
        job.setOutputKeyClass(LongWritable.class);
        job.setOutputValueClass(LongWritable.class);
        job.setInputFormatClass(SequenceFileInputFormat.class);
        job.setNumReduceTasks(conf.getInt(LikelikeConstants.NUMBER_OF_REDUCES,
                LikelikeConstants.DEFAULT_NUMBER_OF_REDUCES));

        return job.waitForCompletion(true);        
    }

    /**
     * Extract clusters.
     * 
     * @param inputFile input file name 
     * @param clusterFile output file
     * @param conf configuration 
     * @return Counters object containing the statistics for the input file
     * @throws IOException -
     * @throws InterruptedException -
     * @throws ClassNotFoundException -
     */
    private Counters extractClusters(final String inputFile, 
            final String clusterFile, final Configuration conf) 
    throws IOException, 
            InterruptedException, ClassNotFoundException {
        
        
        Path inputPath = new Path(inputFile);
        Path outputPath = new Path(clusterFile);
        FsUtil.checkPath(outputPath, FileSystem.get(conf));

        Job job = new Job(conf);
        job.setJarByClass(LSHRecommendations.class);
        FileInputFormat.addInputPath(job, inputPath);
        FileOutputFormat.setOutputPath(job, outputPath);
        job.setMapperClass(SelectClustersMapper.class);
        job.setReducerClass(SelectClustersReducer.class);
        job.setMapOutputKeyClass(LongWritable.class);
        job.setMapOutputValueClass(LongWritable.class);
        job.setOutputKeyClass(LongWritable.class);
        job.setOutputValueClass(RelatedUsersWritable.class);
        job.setOutputFormatClass(
                SequenceFileOutputFormat.class);
        job.setNumReduceTasks(
                conf.getInt(LikelikeConstants.NUMBER_OF_REDUCES,
                LikelikeConstants.DEFAULT_NUMBER_OF_REDUCES));

        job.waitForCompletion(true);
        return job.getCounters();
    }


    /**
     * Main method.
     *
     * @param args argument strings which contain input and output files.
     * @throws Exception -
     */
    public static void main(final String[] args)
    throws Exception {
        int exitCode = ToolRunner.run(
                new LSHRecommendations(), args);
        System.exit(exitCode);
    }

    /**
     * Add configuration from xml files.
     */
    private void setDefaultConfiguration()  {
        Configuration.addDefaultResource("conf/likelike-default.xml");
        Configuration.addDefaultResource("conf/likelike-site.xml");
    }
    
}