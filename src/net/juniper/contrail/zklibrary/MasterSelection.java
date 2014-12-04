package net.juniper.contrail.zklibrary;

import org.apache.curator.framework.recipes.leader.LeaderLatch;
import org.apache.curator.framework.recipes.leader.Participant;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;
import java.io.Closeable;
import java.io.IOException;

import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.api.GetDataBuilder;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.framework.recipes.leader.LeaderSelector;
import org.apache.curator.framework.recipes.leader.LeaderSelectorListener;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooDefs.Perms;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.data.Id;
import org.apache.zookeeper.data.Stat;
import org.apache.curator.framework.state.ConnectionState;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;
import org.apache.log4j.Level;



/**
 *  * @author Kiran Desai
 *   * @date Nov 19 2014
 *    */
public class MasterSelection {

    private CuratorFramework client;
    private String latchPath;
    private String id;
    private LeaderLatch leaderLatch;
    private static Logger s_logger = null;

    public MasterSelection(String connString, String latchPath, String id) {
        s_logger = Logger.getLogger(MasterSelection.class);
        s_logger.info("Creating zookepper client with connection string: "+connString+" timeoutvalue=1s latchpath="+latchPath+" and id="+id);
        client = CuratorFrameworkFactory.newClient(connString, 1000, 1000, new ExponentialBackoffRetry(1000, Integer.MAX_VALUE));
        this.id = id;
        this.latchPath = latchPath;
    }

    public void start() throws Exception{
        client.start();
        client.getZookeeperClient().blockUntilConnectedOrTimedOut();
        leaderLatch = new LeaderLatch(client, latchPath, id);
        leaderLatch.start();
    }

    public boolean isLeader() {
        return leaderLatch.hasLeadership();
    }

    public Participant currentLeader() throws Exception{
        return leaderLatch.getLeader();
    }
    public void waitForLeadership() {
        try {
        	start();
        	Thread.sleep(1000); 
	        s_logger.info("Awaiting Leadership....");
		leaderLatch.await();
	        s_logger.info("Acquired Leadership..Proceeding further");
	} catch (Exception e) {
		s_logger.error("Exception in starting leader latch:"+e);
	}	
    }
	
    public void close() throws IOException {
        s_logger.info("Close called for MasterSecletion. Shutting down client");
        leaderLatch.close();
        client.close();
    }
}
