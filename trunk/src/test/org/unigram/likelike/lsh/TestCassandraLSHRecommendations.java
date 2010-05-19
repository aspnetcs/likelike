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


import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import me.prettyprint.cassandra.service.CassandraClient;
import me.prettyprint.cassandra.service.CassandraClientPool;
import me.prettyprint.cassandra.service.CassandraClientPoolFactory;
import me.prettyprint.cassandra.service.Keyspace;
import me.prettyprint.cassandra.testutils.EmbeddedServerHelper;

import org.apache.cassandra.service.Column;
import org.apache.cassandra.service.ColumnParent;
import org.apache.cassandra.service.ColumnPath;
import org.apache.cassandra.service.InvalidRequestException;
import org.apache.cassandra.service.NotFoundException;
import org.apache.cassandra.service.SlicePredicate;
import org.apache.cassandra.service.SliceRange;
import org.apache.cassandra.service.TimedOutException;
import org.apache.cassandra.service.UnavailableException;
import org.apache.commons.collections.MultiHashMap;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.thrift.TException;
import org.apache.thrift.transport.TTransportException;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static me.prettyprint.cassandra.utils.StringUtils.bytes;
import static me.prettyprint.cassandra.utils.StringUtils.string;

import junit.framework.TestCase;

public class TestCassandraLSHRecommendations extends TestCase {
 
    private static EmbeddedServerHelper embedded;

    
    private CassandraClient client;
    private Keyspace keyspace;
    private CassandraClientPool pools;
            
    @AfterClass 
    protected void tearDown() throws IOException {
        embedded.teardown();
    }
    
    @BeforeClass
        public void setUp() throws TTransportException, 
        IOException, InterruptedException {
        
        embedded = new EmbeddedServerHelper();
        embedded.setup();
        
        try {
            this.pools = CassandraClientPoolFactory.INSTANCE.get();
            this.client = pools.borrowClient("localhost", 9170);
            this.keyspace = client.getKeyspace("Likelike",1,
                                          CassandraClient.DEFAULT_FAILOVER_POLICY);
        } catch (Exception e){
            e.printStackTrace();
        }

    }

    
    public boolean runWithCheck(int depth, int iterate) {
        String inputPath  = "build/test/resources/testSmallInput.txt";
        String outputPath = "build/test/outputLsh";           
        
        /* run lsh */
        String[] args = {"-input",  inputPath, 
                         "-output", outputPath,                
                         "-depth",  Integer.toString(depth),
                         "-iterate", Integer.toString(iterate) 
        };

        Configuration conf = new Configuration();
        conf.set("fs.default.name", "file:///");
        conf.set("mapred.job.tracker", "local");
        
        CassandraLSHRecommendations job 
            = new CassandraLSHRecommendations();
        
        try {
            job.run(args, conf);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        
        assertTrue(this.check(conf));
        
        return true;
    }
    
    private boolean check(Configuration conf)  {
        
        ColumnParent clp = new ColumnParent("RelatedPairs", null);
        SliceRange sr = new SliceRange(new byte[0], 
                new byte[0], false, 150);
        SlicePredicate sp = new SlicePredicate(null, sr);
        
        Long keys[] = {0L, 1L, 2L, 3L, 7L, 8L};
        MultiHashMap resultMap = new MultiHashMap();
        for (int i =0; i<keys.length; i++) {
            Long key = keys[i];
            try {
                List<Column> cols  = keyspace.getSlice(key.toString(), clp, sp);
                //System.out.println("size of Column for " + key + "\t" + cols.size());              
                Iterator itrHoge = cols.iterator();
                while(itrHoge.hasNext()){
                    Column c = (Column) itrHoge.next();
                    resultMap.put(key, // target 
                            Long.parseLong(new String(c.name)));                    
                    //System.out.println("\tname: " + new String(c.getName()));
                    //System.out.println("\tvalue: " + new String(c.getValue()));
                   }
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            } 
        }
        
        /* basic test cases */
        Collection coll = (Collection) resultMap.get(new Long(0));
        assertTrue(coll.size() >= 2 && coll.size() <= 4);
        coll = (Collection) resultMap.get(new Long(1));
        assertTrue(coll.size() >= 2 && coll.size() <= 4);
        coll = (Collection) resultMap.get(new Long(2));
        assertTrue(coll.size() >= 2 && coll.size() <= 4);
        coll = (Collection) resultMap.get(new Long(3));
        assertTrue(coll.size() >= 1 && coll.size() <= 3);
        
        /* examples with no recommendation */
        assertFalse(resultMap.containsKey(new Long(7)));
        assertFalse(resultMap.containsKey(new Long(8)));        
        
        return true;
    }

    @Test
    public void testRun() throws IOException {
        assertTrue(this.runWithCheck(1, 1));
        assertTrue(this.runWithCheck(1, 5));
        //assertTrue(this.runWithCheck(1, 10));
        
        //assertTrue(this.runWithCheck(2, 1));
        //assertTrue(this.runWithCheck(2, 5));
        //assertTrue(this.runWithCheck(2, 10));
        
        //assertTrue(this.runWithCheck(3, 1));
        //assertTrue(this.runWithCheck(3, 5));
        //assertTrue(this.runWithCheck(3, 10));
        
        /*TODO add tests for pareters such as minCluster */ 
        //return; 
    }    

}
