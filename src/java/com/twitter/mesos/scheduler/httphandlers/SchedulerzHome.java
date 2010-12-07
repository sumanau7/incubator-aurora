package com.twitter.mesos.scheduler.httphandlers;

import com.google.common.base.Function;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.MapMaker;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.twitter.common.base.Closure;
import com.twitter.common.net.http.handlers.StringTemplateServlet;
import com.twitter.mesos.Tasks;
import com.twitter.mesos.gen.ScheduledTask;
import com.twitter.mesos.gen.TaskQuery;
import com.twitter.mesos.scheduler.CronJobManager;
import com.twitter.mesos.scheduler.SchedulerCore;
import org.antlr.stringtemplate.StringTemplate;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

/**
 * HTTP interface to serve as a HUD for the mesos scheduler.
 *
 * @author wfarner
 */
public class SchedulerzHome extends StringTemplateServlet {

  @Inject private SchedulerCore scheduler;
  @Inject private CronJobManager cronScheduler;

  @Inject
  public SchedulerzHome(@CacheTemplates boolean cacheTemplates) {
    super("schedulerzhome", cacheTemplates);
  }

  @Override
  protected void doGet(final HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    writeTemplate(resp, new Closure<StringTemplate>() {
      @Override public void execute(StringTemplate template) {
        Map<String, User> users = new MapMaker().makeComputingMap(new Function<String, User>() {
          @Override public User apply(String userName) {
            User user = new User();
            user.name = userName;
            return user;
          }
        });

        Multimap<String, ScheduledTask> userJobs = HashMultimap.create();

        Iterable<ScheduledTask> tasks = scheduler.getTasks(new TaskQuery());

        for (ScheduledTask task : tasks) {
          User user = users.get(task.getAssignedTask().getTask().getOwner());
          switch (task.getStatus()) {
            case PENDING:
              user.pendingTaskCount++;
              break;

            case STARTING:
            case RUNNING:
              user.activeTaskCount++;
              break;

            case KILLED:
            case KILLED_BY_CLIENT:
            case FINISHED:
              user.finishedTaskCount++;
              break;

            case LOST:
            case NOT_FOUND:
            case FAILED:
              user.failedTaskCount++;
              break;

            default:
              throw new IllegalArgumentException("Unsupported status: " + task.getStatus());
          }

          userJobs.put(user.name, task);
        }

        for (User user : users.values()) {
          Iterable<ScheduledTask> activeUserTasks = Iterables.filter(userJobs.get(user.name),
              Tasks.ACTIVE_FILTER);
          user.jobCount = Sets.newHashSet(Iterables.transform(activeUserTasks,
              new Function<ScheduledTask, String>() {
                @Override public String apply(ScheduledTask task) {
                  return task.getAssignedTask().getTask().getJobName();
                }
              })).size();
        }

        template.setAttribute("users",
            DisplayUtils.sort(users.values(), DisplayUtils.SORT_USERS_BY_NAME));

        template.setAttribute("cronJobs",
            DisplayUtils.sort(cronScheduler.getJobs(), DisplayUtils.SORT_JOB_CONFIG_BY_NAME));
      }
    });
  }

  static class User {
    String name;
    int jobCount;
    private int pendingTaskCount = 0;
    private int activeTaskCount = 0;
    private int finishedTaskCount = 0;
    private int failedTaskCount = 0;

    public String getName() {
      return name;
    }

    public int getJobCount() {
      return jobCount;
    }

    public int getPendingTaskCount() {
      return pendingTaskCount;
    }

    public int getActiveTaskCount() {
      return activeTaskCount;
    }

    public int getFinishedTaskCount() {
      return finishedTaskCount;
    }

    public int getFailedTaskCount() {
      return failedTaskCount;
    }
  }
}