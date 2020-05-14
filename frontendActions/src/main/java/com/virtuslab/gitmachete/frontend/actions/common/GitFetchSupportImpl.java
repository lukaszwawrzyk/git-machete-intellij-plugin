package com.virtuslab.gitmachete.frontend.actions.common;

import static com.intellij.openapi.vcs.VcsNotifier.STANDARD_NOTIFICATION;
import static git4idea.GitUtil.findRemoteByName;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.intellij.dvcs.MultiMessage;
import com.intellij.dvcs.MultiRootMessage;
import com.intellij.internal.statistic.IdeActivity;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.util.concurrency.AppExecutorUtil;
import git4idea.GitUtil;
import git4idea.commands.Git;
import git4idea.commands.GitAuthenticationGate;
import git4idea.commands.GitAuthenticationListener;
import git4idea.commands.GitImpl;
import git4idea.commands.GitRestrictingAuthenticationGate;
import git4idea.config.GitConfigUtil;
import git4idea.fetch.GitFetchResult;
import git4idea.fetch.GitFetchSupport;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import io.vavr.control.Option;
import io.vavr.control.Try;
import kotlin.jvm.functions.Function1;
import lombok.AllArgsConstructor;
import org.checkerframework.checker.nullness.qual.Nullable;

import com.virtuslab.gitmachete.frontend.actions.toolbar.FetchAllRemotesAction;
import com.virtuslab.logger.IPrefixedLambdaLogger;
import com.virtuslab.logger.PrefixedLambdaLoggerFactory;

/**
 * @deprecated This implementation is a workaround to provide features missing in pre-2020.1 versions.
 * The main benefit of it is specific branch update ({@link GitFetchSupportImpl#fetch(GitRepository, GitRemote, String)}).
 * We need this class for {@link FetchAllRemotesAction} and {@link BasePullBranchAction}.
 */
@Deprecated
public final class GitFetchSupportImpl implements GitFetchSupport {

  private static final IPrefixedLambdaLogger LOG = PrefixedLambdaLoggerFactory.getLogger("frontendActions");

  private static final int MAX_SSH_CONNECTIONS = 10;

  private final Project project;
  private final ProgressManager progressManager = ProgressManager.getInstance();

  private final AtomicInteger fetchRequestCounter = new AtomicInteger(0);

  private GitFetchSupportImpl(Project project) {
    this.project = project;
  }

  public static GitFetchSupportImpl fetchSupport(Project project) {
    return new GitFetchSupportImpl(project);
  }

  public boolean isFetchRunning() {
    return fetchRequestCounter.get() > 0;
  }

  @Override
  public FetchResultImpl fetchDefaultRemote(Collection<GitRepository> repositories) {
    var remotesToFetch = new ArrayList<RemoteRefCoordinates>();
    for (var repository : repositories) {
      var remote = getDefaultRemoteToFetch(repository);
      if (remote != null) {
        remotesToFetch.add(new RemoteRefCoordinates(repository, remote, /* refspec */ null));
      } else {
        LOG.info("No remote to fetch found in ${repository}");
      }
    }
    return fetch(remotesToFetch);
  }

  @Override
  public FetchResultImpl fetchAllRemotes(Collection<GitRepository> repositories) {
    var remotesToFetch = new ArrayList<RemoteRefCoordinates>();
    for (var repository : repositories) {
      if (repository.getRemotes().isEmpty()) {
        LOG.info("No remote to fetch found in ${repository}");
      } else {
        for (var remote : repository.getRemotes()) {
          remotesToFetch.add(new RemoteRefCoordinates(repository, remote, /* refspec */ null));
        }
      }
    }
    return fetch(remotesToFetch);
  }

  public FetchResultImpl fetch(GitRepository repository, GitRemote remote, String refspec) {
    return fetch(List.of(new RemoteRefCoordinates(repository, remote, refspec)));
  }

  @Override
  public FetchResultImpl fetch(GitRepository repository, GitRemote remote) {
    return fetch(List.of(new RemoteRefCoordinates(repository, remote, /* refspec */ null)));
  }

  public FetchResultImpl fetch(List<RemoteRefCoordinates> arguments) {
    try {
      fetchRequestCounter.incrementAndGet();
      return withIndicator(() -> {
        var activity = IdeActivity.started(project, /* group */ "vcs", /* activityName */ "fetch");

        var tasks = fetchInParallel(arguments);
        var results = waitForFetchTasks(tasks);

        var mergedResults = new HashMap<GitRepository, RepoResult>();
        for (var result : results) {
          var res = mergedResults.get(result.repository);
          mergedResults.put(result.repository, mergeRepoResults(res, result));
        }
        activity.finished();

        return new FetchResultImpl(project, VcsNotifier.getInstance(project), mergedResults);
      });
    } finally {
      fetchRequestCounter.decrementAndGet();
    }
  }

  private List<FetchTask> fetchInParallel(List<RemoteRefCoordinates> remotes) {
    var tasks = new ArrayList<FetchTask>();
    var maxThreads = getMaxThreads(remotes.stream().map(r -> r.repository).collect(Collectors.toList()), remotes.size());
    LOG.debug("Fetching ${remotes} using ${maxThreads} threads");
    var executor = AppExecutorUtil.createBoundedApplicationPoolExecutor(/* name */ "GitFetch Pool", maxThreads);
    var commonIndicator = Option.of(progressManager.getProgressIndicator()).getOrElse(new EmptyProgressIndicator());
    var authenticationGate = new GitRestrictingAuthenticationGate();
    for (var remoteRefCoordinates : remotes) {
      LOG.debug("Fetching ${remoteRefCoordinates.remote} in ${remoteRefCoordinates.repository}");
      Future<SingleRemoteResult> future = executor.submit(() -> {
        commonIndicator.checkCanceled();
        AtomicReference<@Nullable SingleRemoteResult> result = new AtomicReference<>(null);

        ProgressManager.getInstance().executeProcessUnderProgress(() -> {
          commonIndicator.checkCanceled();
          result.set(
              doFetch(remoteRefCoordinates.repository,
                  remoteRefCoordinates.remote,
                  remoteRefCoordinates.refspec,
                  authenticationGate));
        }, commonIndicator);

        SingleRemoteResult singleRemoteResult = result.get();
        assert singleRemoteResult != null : "Single remote result is null";
        return singleRemoteResult;
      });
      tasks.add(new FetchTask(remoteRefCoordinates.repository, remoteRefCoordinates.remote, future));
    }
    return tasks;
  }

  private int getMaxThreads(Collection<GitRepository> repositories, int numberOfRemotes) {
    var config = Registry.intValue("git.parallel.fetch.threads");
    var maxThreads = 1;
    if (config > 0) {
      maxThreads = config;
    } else if (config == -1) {
      maxThreads = Runtime.getRuntime().availableProcessors();
    } else if (config == -2) {
      maxThreads = numberOfRemotes;
    } else if (config == -3) {
      maxThreads = Math.min(numberOfRemotes, Runtime.getRuntime().availableProcessors() * 2);
    }

    if (isStoreCredentialsHelperUsed(repositories)) {
      return 1;
    }

    return Math.min(maxThreads, MAX_SSH_CONNECTIONS);
  }

  private boolean isStoreCredentialsHelperUsed(Collection<GitRepository> repositories) {
    for (var repo : repositories) {
      var option = Try.of(() -> Option.of(GitConfigUtil.getValue(project, repo.getRoot(), "credential.helper")))
          .getOrElse(Option.none());
      if (option.isDefined() && option.get().equalsIgnoreCase("store")) {
        return true;
      }
    }
    return false;
  }

  private List<SingleRemoteResult> waitForFetchTasks(List<FetchTask> tasks) {
    var results = new ArrayList<SingleRemoteResult>();
    for (var task : tasks) {
      try {
        results.add(task.future.get());
      } catch (CancellationException | InterruptedException e) {
        throw new ProcessCanceledException(e);
      } catch (ExecutionException e) {
        if (e.getCause() instanceof ProcessCanceledException) {
          throw (ProcessCanceledException) e.getCause();
        }
        String msg = Option.of(e.getCause())
            .flatMap(c -> Option.of(c.getMessage()))
            .getOrElse("Error");
        results.add(new SingleRemoteResult(task.repository, task.remote, msg, Collections.emptyList()));
        LOG.error("Task execution error: ${msg}");
      }
    }
    return results;
  }

  private RepoResult mergeRepoResults(@Nullable RepoResult firstResult, SingleRemoteResult secondResult) {
    if (firstResult == null) {
      return new RepoResult(Map.of(secondResult.remote, secondResult));
    } else {
      Map<GitRemote, SingleRemoteResult> results = new HashMap<>(firstResult.results);
      results.put(secondResult.remote, secondResult);
      return new RepoResult(results);
    }
  }

  private <T> T withIndicator(Supplier<T> operation) {
    var indicator = progressManager.getProgressIndicator();
    var prevText = "";
    if (indicator != null) {
      prevText = indicator.getText();
      indicator.setText("Fetching...");
    }
    try {
      return operation.get();
    } finally {
      if (indicator != null) {
        indicator.setText(prevText);
      }
    }
  }

  @Nullable
  @Override
  public GitRemote getDefaultRemoteToFetch(GitRepository repository) {
    var remotes = repository.getRemotes();
    if (remotes.isEmpty()) {
      return null;
    } else if (remotes.size() == 1) {
      return remotes.iterator().next();
    } else {
      // this emulates behavior of the native `git fetch`:
      // if current branch doesn't give a hint, then return "origin"; if there is no "origin", don't guess and fail
      return Option.of(repository.getCurrentBranch())
          .flatMap(b -> Option.of(b.findTrackedBranch(repository)))
          .map(t -> t.getRemote())
          .flatMap(r -> Option.of(findRemoteByName(repository, GitRemote.ORIGIN)))
          .getOrNull();
    }
  }

  private SingleRemoteResult doFetch(GitRepository repository, GitRemote remote, @Nullable String refspec,
      GitAuthenticationGate authenticationGate) {
    var recurseSubmodules = "--recurse-submodules=no";

    java.util.List<String> params = refspec == null
        ? Collections.singletonList(recurseSubmodules)
        : Arrays.asList(refspec, recurseSubmodules);

    GitImpl instance = (GitImpl) Git.getInstance();
    var result = instance.fetch(repository, remote, Collections.emptyList(), authenticationGate, params.toArray(new String[0]));
    var pruned = result.getOutput().stream().map(this::getPrunedRef).filter(r -> r.isEmpty()).collect(Collectors.toList());

    if (result.success()) {
      BackgroundTaskUtil.syncPublisher(repository.getProject(), GitAuthenticationListener.GIT_AUTHENTICATION_SUCCESS)
          .authenticationSucceeded(repository, remote);
      repository.update();
    }

    String error = result.success() ? null : result.getErrorOutputAsJoinedString();

    return new SingleRemoteResult(repository, remote, error, pruned);
  }

  private String getPrunedRef(String line) {
    var PRUNE_PATTERN = Pattern.compile("\\s*x\\s*\\[deleted\\].*->\\s*(\\S*)"); // x [deleted] (none) -> origin/branch
    var matcher = PRUNE_PATTERN.matcher(line);
    String result = "";
    if (matcher.matches()) {
      result = matcher.group(1);
    }
    assert result != null : "Matched group is null";
    return result;
  }

  @AllArgsConstructor
  private class RemoteRefCoordinates {
    private final GitRepository repository;
    private final GitRemote remote;
    @Nullable
    private final String refspec;
  }

  @AllArgsConstructor
  private class FetchTask {
    private final GitRepository repository;
    private final GitRemote remote;
    private final Future<SingleRemoteResult> future;
  }

  @AllArgsConstructor
  private class RepoResult {
    Map<GitRemote, SingleRemoteResult> results;

    boolean totallySuccessful() {
      return results.values().stream().allMatch(v -> v.success());
    }

    @Nullable
    String error() {
      var errorMessage = multiRemoteMessage(true);
      for (Map.Entry<GitRemote, SingleRemoteResult> e : results.entrySet()) {
        String error = e.getValue().error;
        if (error != null) {
          errorMessage.append(e.getKey(), error);
        }
      }
      return errorMessage.asString();
    }

    String prunedRefs() {
      var prunedRefs = multiRemoteMessage(false);
      for (Map.Entry<GitRemote, SingleRemoteResult> e : results.entrySet()) {
        if (!e.getValue().prunedRefs.isEmpty()) {
          prunedRefs.append(e.getKey(), String.join(System.lineSeparator(), e.getValue().prunedRefs));
        }
      }
      return prunedRefs.asString();
    }

    /*
     * For simplicity, remote and repository results are merged separately. It means that they are not merged, if two
     * repositories have two remotes, and then fetch succeeds for the first remote in both repos, and fails for the second
     * remote in both repos. Such cases are rare, and can be handled when actual problem is reported.
     */
    private MultiMessage multiRemoteMessage(boolean remoteInPrefix) {
      return new MultiMessage(results.keySet(),
          (Function1<GitRemote, String>) GitRemote::getName,
          (Function1<GitRemote, String>) GitRemote::getName,
          remoteInPrefix,
          /* html */ true);
    }
  }

  @AllArgsConstructor
  private class SingleRemoteResult {
    private final GitRepository repository;
    private final GitRemote remote;
    @Nullable
    private final String error;
    private final java.util.List<String> prunedRefs;

    public boolean success() {
      return error == null;
    }
  }

  public final class FetchResultImpl implements GitFetchResult {
    private final Project project;
    private final VcsNotifier vcsNotifier;
    private final Map<GitRepository, RepoResult> results;
    private final boolean isFailed;

    private FetchResultImpl(Project project, VcsNotifier vcsNotifier, Map<GitRepository, RepoResult> results) {
      this.project = project;
      this.vcsNotifier = vcsNotifier;
      this.results = results;
      this.isFailed = results.values().stream().anyMatch(v -> !v.totallySuccessful());
    }

    @Override
    public void showNotification() {
      doShowNotification();
    }

    @Override
    public boolean showNotificationIfFailed(String title) {
      if (isFailed) {
        doShowNotification(title);
      }
      return !isFailed;
    }

    @Override
    public boolean showNotificationIfFailed() {
      return showNotificationIfFailed("Fetch Failed");
    }

    private void doShowNotification(String failureTitle) {
      NotificationType type = isFailed ? NotificationType.ERROR : NotificationType.INFORMATION;
      var message = buildMessage(failureTitle);
      var notification = STANDARD_NOTIFICATION.createNotification(/* title */ "", message, type, /* listener */ null);
      vcsNotifier.notify(notification);
    }

    private void doShowNotification() {
      doShowNotification("Fetch Failed");
    }

    public void throwExceptionIfFailed() {
      // Actual (idea) implementations do throw here like we do in `FetchResultImpl.ourThrowExceptionIfFailed`.
      // This is achieved thanks to Kotlin which has no checked exception.
    }

    public void ourThrowExceptionIfFailed() throws VcsException {
      if (isFailed) {
        throw new VcsException(buildMessage());
      }
    }

    private String buildMessage(String failureTitle) {
      var roots = results.keySet().stream().map(it -> it.getRoot()).collect(Collectors.toList());
      var errorMessage = new MultiRootMessage(project, roots, /* rootInPrefix */ true, /* html */ true);
      var prunedRefs = new MultiRootMessage(project, roots, /* rootInPrefix */ false, /* html */ true);
      var failed = results.entrySet().stream()
          .filter(e -> !e.getValue().totallySuccessful())
          .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

      for (Map.Entry<GitRepository, RepoResult> e : failed.entrySet()) {
        String error = e.getValue().error();
        if (error != null) {
          errorMessage.append(e.getKey().getRoot(), error);
        }
      }
      for (Map.Entry<GitRepository, RepoResult> e : results.entrySet()) {
        prunedRefs.append(e.getKey().getRoot(), e.getValue().prunedRefs());
      }

      var mentionFailedRepos = failed.size() == roots.size() ? "" : GitUtil.mention(failed.keySet());
      var title = !isFailed ? "<b>Fetch Successful</b>" : "<b>${failureTitle}</b>${mentionFailedRepos}";
      return title + prefixWithBr(errorMessage.asString()) + prefixWithBr(prunedRefs.asString());
    }

    private String buildMessage() {
      return buildMessage("Fetch Failed");
    }

    private String prefixWithBr(String text) {
      return text.isEmpty() ? "" : "<br/>${text}";
    }

  }
}