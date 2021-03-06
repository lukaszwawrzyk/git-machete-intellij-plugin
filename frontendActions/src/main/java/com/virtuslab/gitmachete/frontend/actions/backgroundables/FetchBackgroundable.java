package com.virtuslab.gitmachete.frontend.actions.backgroundables;

import static com.virtuslab.gitmachete.frontend.resourcebundles.GitMacheteBundle.format;
import static com.virtuslab.gitmachete.frontend.resourcebundles.GitMacheteBundle.getString;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsNotifier;
import git4idea.GitUtil;
import git4idea.fetch.GitFetchSupport;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import lombok.CustomLog;
import org.checkerframework.checker.guieffect.qual.UIEffect;

@CustomLog
public class FetchBackgroundable extends Task.Backgroundable {

  private final Project project;
  private final GitRepository gitRepository;
  private final String remoteName;
  private final String refspec;

  /** Use as {@code remoteName} when referring to the local repository. */
  public static final String LOCAL_REPOSITORY_NAME = ".";

  public FetchBackgroundable(Project project,
      GitRepository gitRepository,
      String remoteName,
      String refspec,
      String taskTitle) {
    super(project, taskTitle, /* canBeCancelled */ true);
    this.project = project;
    this.gitRepository = gitRepository;
    this.remoteName = remoteName;
    this.refspec = refspec;
  }

  @Override
  public void run(ProgressIndicator indicator) {
    var fetchSupport = GitFetchSupport.fetchSupport(project);
    var remote = remoteName.equals(LOCAL_REPOSITORY_NAME)
        ? GitRemote.DOT
        : GitUtil.findRemoteByName(gitRepository, remoteName);
    if (remote == null) {
      // This is generally NOT expected, the task should never be triggered
      // for an invalid remote in the first place.
      LOG.warn("Remote '${remoteName}' does not exist");
      return;
    }
    var fetchResult = fetchSupport.fetch(gitRepository, remote, refspec);
    fetchResult.showNotificationIfFailed(
        format(getString("action.GitMachete.FetchBackgroundable.notification.title.fetch-fail"), refspec));
  }

  @UIEffect
  @Override
  public void onSuccess() {
    VcsNotifier.getInstance(project)
        .notifySuccess(format(getString("action.GitMachete.FetchBackgroundable.notification.title.fetch-success"), refspec));
  }
}
