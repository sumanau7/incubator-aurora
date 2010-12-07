package com.twitter.mesos.scheduler;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.twitter.common.args.Option;
import com.twitter.common.base.Command;
import com.twitter.common.process.GuicedProcess;
import com.twitter.common.process.GuicedProcessOptions;
import com.twitter.common.zookeeper.Group;
import com.twitter.common.zookeeper.ServerSet.EndpointStatus;
import com.twitter.common.zookeeper.ServerSet.UpdateException;
import com.twitter.common.zookeeper.SingletonService;
import com.twitter.common.zookeeper.SingletonService.LeadershipListener;
import com.twitter.mesos.gen.MesosSchedulerManager;
import com.twitter.thrift.Status;
import mesos.MesosSchedulerDriver;
import org.apache.thrift.transport.TTransportException;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Launcher for the twitter nexue scheduler.
 *
 * TODO(wfarner): Include information in /schedulerz about who is the current scheduler leader.
 *
 * @author wfarner
 */
public class SchedulerMain extends GuicedProcess<SchedulerMain.TwitterSchedulerOptions, Exception> {
  public static class TwitterSchedulerOptions extends GuicedProcessOptions {
    @Option(name = "executor_path", required = true,
            usage = "Path to the executorHub launch script.")
    public String executorPath;

    @Option(name = "zk_endpoints", usage = "Endpoint specification for the ZooKeeper servers.")
    public List<InetSocketAddress> zooKeeperEndpoints;

    @Option(name = "zk_session_timeout_secs",
            usage = "The ZooKeeper session timeout in seconds.")
    public int zooKeeperSessionTimeoutSecs = 5;

    @Option(name = "mesos_scheduler_ns", required = true,
            usage = "The name service name for the mesos scheduler thrift server.")
    public String mesosSchedulerNameSpec;

    @Option(name = "mesos_master_address",
            usage = "Mesos address for the master, can be a mesos address or zookeeper path.")
    public String mesosMasterAddress;

    @Option(name = "scheduler_persistence_zookeeper_path",
            usage = "Path in ZooKeeper that scheduler will persist to/from (overrides local).")
    public String schedulerPersistenceZooKeeperPath;

    @Option(name = "scheduler_persistence_zookeeper_version",
            usage = "Version for scheduler persistence node in ZooKeeper.")
    public int schedulerPersistenceZooKeeperVersion = 1;

    @Option(name = "scheduler_persistence_local_path",
            usage = "Local file path that scheduler will persist to/from.")
    public File schedulerPersistenceLocalPath = new File("/tmp/mesos_scheduler_dump");

    @Option(name = "machine_restrictions",
        usage = "Map of machine hosts to job keys."
                + "  If A maps to B, only B can run on A and B can only run on A.")
    public Map<String, String> machineRestrictions = Maps.newHashMap();
  }

  private static Logger LOG = Logger.getLogger(SchedulerMain.class.getName());

  private final LeadershipListener leadershipListener = new LeadershipListener() {
    @Override public void onLeading(EndpointStatus status) {
      LOG.info("Elected as leading scheduler!");
      try {
        runMesosDriver();
        status.update(Status.ALIVE);
      } catch (UpdateException e) {
        LOG.log(Level.SEVERE, "Failed to update endpoint status.", e);
      } catch (InterruptedException e) {
        LOG.log(Level.SEVERE, "Interrupted while updating endpoint status.", e);
      }
    }

    @Override public void onDefeated(@Nullable EndpointStatus status) {
      LOG.info("Lost leadership, committing suicide.");

      try {
        if (status != null) status.update(Status.DEAD);
      } catch (InterruptedException e) {
        LOG.log(Level.WARNING, "Interrupted while leaving server set.", e);
      } catch (UpdateException e) {
        LOG.log(Level.WARNING, "Failed to leave server set.", e);
      } finally {
        destroy();
      }
    }
  };

  public SchedulerMain() {
    super(TwitterSchedulerOptions.class);
  }

  @Inject private SingletonService schedulerService;
  @Inject private SchedulerThriftInterface schedulerThriftInterface;
  @Inject private MesosSchedulerDriver driver;

  @Override
  protected Iterable<Class<? extends Module>> getProcessModuleClasses() {
    return ImmutableList.<Class<? extends Module>>of(SchedulerModule.class);
  }

  @Override
  protected void runProcess() {
    int port = -1;
    try {
      port = startThriftServer();
    } catch (IOException e) {
      LOG.log(Level.SEVERE, "Failed to start thrift server.", e);
    } catch (TTransportException e) {
      LOG.log(Level.SEVERE, "Failed to start thrift server.", e);
    } catch (Group.JoinException e) {
      LOG.log(Level.SEVERE, "Failed to join mesos scheduler server set in ZooKeeper.", e);
    } catch (InterruptedException e) {
      LOG.log(Level.SEVERE, "Interrupted while starting thrift server.", e);
    }
    if (port == -1) return;

    if (schedulerService != null) {
      try {
        leadService(schedulerService, port, Status.STARTING, leadershipListener);
      } catch (Group.WatchException e) {
        LOG.log(Level.SEVERE, "Failed to watch group and lead service.", e);
      } catch (Group.JoinException e) {
        LOG.log(Level.SEVERE, "Failed to join scheduler service group.", e);
      } catch (InterruptedException e) {
        LOG.log(Level.SEVERE, "Interrupted while joining scheduler service group.", e);
      } catch (UnknownHostException e) {
        LOG.log(Level.SEVERE, "Failed to find self host name.", e);
      }
    } else {
      runMesosDriver();
    }

    waitForEver();
  }

  private void runMesosDriver() {
    addShutdownAction(new Command() {
      @Override public void execute() {
        if (driver != null) {
          LOG.info("Stopping scheduler driver.");
          int result = driver.stop();
          if (result != 0) {
            LOG.warning("Attempt to stop driver returned code " + result);
          }
        }
      }
    });

    new ThreadFactoryBuilder().setNameFormat("Driver-Runner-%d").setDaemon(true).build().newThread(
        new Runnable() {
          @Override public void run() {
            int result = driver.run();
            LOG.info("Driver completed with exit code " + result);
            destroy();
          }
        }
    ).start();
  }

  private int startThriftServer() throws IOException, TTransportException,
      Group.JoinException, InterruptedException {
    schedulerThriftInterface.start(0,
        new MesosSchedulerManager.Processor(schedulerThriftInterface));

    addShutdownAction(new Command() {
      @Override public void execute() {
        LOG.info("Stopping thrift server.");
        schedulerThriftInterface.shutdown();
      }
    });

    return schedulerThriftInterface.getListeningPort();
  }

  public static void main(String[] args) throws Exception {
    new SchedulerMain().run(args);
  }
}