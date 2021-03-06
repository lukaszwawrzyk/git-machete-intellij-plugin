package com.virtuslab.gitmachete.frontend.actions.backgroundables;

import static com.virtuslab.gitmachete.frontend.resourcebundles.GitMacheteBundle.format;
import static com.virtuslab.gitmachete.frontend.resourcebundles.GitMacheteBundle.getString;
import static com.virtuslab.gitmachete.frontend.vfsutils.GitVfsUtils.getMacheteFilePath;

import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsNotifier;
import git4idea.GitLocalBranch;
import git4idea.repo.GitRepository;
import io.vavr.collection.List;
import io.vavr.control.Try;
import org.checkerframework.checker.nullness.qual.Nullable;

import com.virtuslab.branchlayout.api.BranchLayoutEntry;
import com.virtuslab.branchlayout.api.EntryDoesNotExistException;
import com.virtuslab.branchlayout.api.EntryIsDescendantOfException;
import com.virtuslab.branchlayout.api.EntryIsRootException;
import com.virtuslab.branchlayout.api.IBranchLayout;
import com.virtuslab.branchlayout.api.IBranchLayoutEntry;
import com.virtuslab.branchlayout.api.readwrite.IBranchLayoutWriter;
import com.virtuslab.gitmachete.frontend.actions.dialogs.SlideInOptions;

public class SlideInBackgroundable extends Task.Backgroundable {

  private final GitRepository gitRepository;
  private final IBranchLayout branchLayout;
  private final IBranchLayoutWriter branchLayoutWriter;
  private final Runnable preSlideInRunnable;
  private final SlideInOptions slideInOptions;
  private final String parentName;
  private final VcsNotifier notifier;

  public SlideInBackgroundable(
      Project project,
      GitRepository gitRepository,
      IBranchLayout branchLayout,
      IBranchLayoutWriter branchLayoutWriter,
      Runnable preSlideInRunnable,
      SlideInOptions slideInOptions,
      String parentName) {
    super(project, getString("action.GitMachete.BaseSlideInBranchBelowAction.task-title"));
    this.gitRepository = gitRepository;
    this.branchLayout = branchLayout;
    this.branchLayoutWriter = branchLayoutWriter;
    this.preSlideInRunnable = preSlideInRunnable;
    this.slideInOptions = slideInOptions;
    this.parentName = parentName;
    this.notifier = VcsNotifier.getInstance(project);
  }

  @Override
  public void run(ProgressIndicator indicator) {
    preSlideInRunnable.run();

    // `preSlideInRunnable` may perform some sneakily-asynchronous operations (e.g. checkoutRemoteBranch).
    // The high-level method used within the runnable do not allow us to schedule the tasks after them.
    // (Stepping deeper is not an option since we would lose some important logic or become very dependent on the internals of git4idea).
    // Hence we wait for the creation of the branch (with exponential backoff).
    waitForCreationOfLocalBranch();

    Path macheteFilePath = getMacheteFilePath(gitRepository);

    var childEntryByName = branchLayout.findEntryByName(slideInOptions.getName());
    IBranchLayoutEntry entryToSlideIn;
    IBranchLayout targetBranchLayout;
    if (childEntryByName.isDefined()) {
      var entryByName = childEntryByName.get();

      if (slideInOptions.shouldReattach()) {
        entryToSlideIn = entryByName;
        targetBranchLayout = branchLayout;
      } else {
        entryToSlideIn = entryByName.withChildren(List.empty());
        targetBranchLayout = Try.of(() -> branchLayout.slideOut(slideInOptions.getName()))
            .onFailure(EntryDoesNotExistException.class, exceptionWithMessageHandler(
                format(getString("action.GitMachete.BaseSlideInBranchBelowAction.notification.message.entry-does-not-exist"),
                    entryToSlideIn.getName())))
            .onFailure(EntryIsRootException.class, exceptionWithMessageHandler(
                format(getString("action.GitMachete.BaseSlideInBranchBelowAction.notification.message.entry-is-root"),
                    entryToSlideIn.getName())))
            .onFailure(Exception.class, exceptionWithMessageHandler(/* message */ null))
            .getOrNull();

        if (targetBranchLayout == null) {
          return;
        }
      }

    } else {
      entryToSlideIn = new BranchLayoutEntry(slideInOptions.getName(), /* customAnnotation */ null,
          /* children */ List.empty());
      targetBranchLayout = branchLayout;
    }

    var newBranchLayout = Try
        .of(() -> targetBranchLayout.slideIn(parentName, entryToSlideIn))
        .onFailure(EntryDoesNotExistException.class, exceptionWithMessageHandler(
            format(getString("action.GitMachete.BaseSlideInBranchBelowAction.notification.message.entry-does-not-exist"),
                parentName)))
        .onFailure(EntryIsDescendantOfException.class, exceptionWithMessageHandler(
            format(getString("action.GitMachete.BaseSlideInBranchBelowAction.notification.message.entry-is-descendant-of"),
                entryToSlideIn.getName(), parentName)))
        .onFailure(Exception.class, exceptionWithMessageHandler(/* message */ null))
        .toOption();

    newBranchLayout.map(nbl -> Try.run(() -> branchLayoutWriter.write(macheteFilePath, nbl, /* backupOldLayout */ true))
        .onFailure(t -> notifier.notifyError(
            /* title */ getString(
                "action.GitMachete.BaseSlideInBranchBelowAction.notification.title.branch-layout-write-fail"),
            getMessageOrEmpty(t))));
  }

  private void waitForCreationOfLocalBranch() {
    Supplier<@Nullable GitLocalBranch> findLocalBranch = () -> gitRepository.getBranches()
        .findLocalBranch(slideInOptions.getName());

    try {
      //  6 attempts, usually 3 are enough
      final int TIMEOUT = 2048;
      long SLEEP_DURATION = 64;
      while (findLocalBranch.get() == null && SLEEP_DURATION <= TIMEOUT) {
        Thread.sleep(SLEEP_DURATION);
        SLEEP_DURATION *= 2;
      }
    } catch (InterruptedException e) {
      notifier.notifyWeakError(
          format(getString("action.GitMachete.BaseSlideInBranchBelowAction.notification.message.wait-interrupted"),
              slideInOptions.getName()));
    }

    if (findLocalBranch.get() == null) {
      notifier
          .notifyWeakError(format(getString("action.GitMachete.BaseSlideInBranchBelowAction.notification.message.timeout"),
              slideInOptions.getName()));
    }
  }

  private Consumer<Throwable> exceptionWithMessageHandler(@Nullable String message) {
    return t -> {
      notifier.notifyError(
          /* title */ format(getString("action.GitMachete.BaseSlideInBranchBelowAction.notification.title.slide-in-fail"),
              slideInOptions.getName()),
          message != null ? message : getMessageOrEmpty(t));
    };
  }

  private static String getMessageOrEmpty(Throwable t) {
    return t.getMessage() != null ? t.getMessage() : "";
  }
}
