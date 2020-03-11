package com.virtuslab.gitmachete.frontend.ui;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

import javax.annotation.Nonnull;

import lombok.Getter;
import lombok.Setter;

import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryChangeListener;
import io.vavr.control.Try;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.ui.GuiUtils;
import com.intellij.util.messages.Topic;

import com.virtuslab.gitmachete.backend.api.IGitMacheteRepository;
import com.virtuslab.gitmachete.backend.root.BackendFactoryModule;
import com.virtuslab.gitmachete.backend.root.GitMacheteRepositoryBuilderFactory;
import com.virtuslab.gitmachete.frontend.graph.repositorygraph.RepositoryGraph;
import com.virtuslab.gitmachete.frontend.graph.repositorygraph.RepositoryGraphFactory;
import com.virtuslab.gitmachete.frontend.ui.table.GitMacheteGraphTable;
import com.virtuslab.gitmachete.frontend.ui.table.GraphTableModel;
import org.checkerframework.checker.initialization.qual.Initialized;
import org.checkerframework.checker.initialization.qual.UnderInitialization;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

public class GitMacheteGraphTableManager {
  private static final Logger LOG = Logger.getInstance(GitMacheteGraphTableManager.class);
  private final Project project;
  private boolean isListingCommits;
  private final GitMacheteGraphTable gitMacheteGraphTable;
  private final GitMacheteRepositoryBuilderFactory gitMacheteRepositoryBuilderFactory;
  @MonotonicNonNull
  private IGitMacheteRepository repository;
  private final RepositoryGraphFactory repositoryGraphFactory;

  @SuppressWarnings("method.invocation.invalid")
  public GitMacheteGraphTableManager(@Nonnull Project project) {
    this.project = project;
    this.isListingCommits = false;
    GraphTableModel graphTableModel = new GraphTableModel(RepositoryGraphFactory.getNullRepositoryGraph());
    @SuppressWarnings("assignment.type.incompatible")
    @Initialized
    @NonNull
    // GitMacheteGraphTable extracted to local variable only for allow to use @SuppressWarnings
    GitMacheteGraphTable graphTable = new GitMacheteGraphTable(graphTableModel, project, /* tableManager */ this);
    this.gitMacheteGraphTable = graphTable;
    this.gitMacheteRepositoryBuilderFactory = BackendFactoryModule.getInjector()
        .getInstance(GitMacheteRepositoryBuilderFactory.class);
    this.repositoryGraphFactory = new RepositoryGraphFactory();
    subscribeToGitRepositoryChanges();
  }

  /** Creates a new repository graph and sets it to the graph table model. */
  public void refreshUI() {
    /*
     * isUnitTestMode() checks if IDEA is running as a command line applet or in unit test mode. No UI should be shown
     * when IDEA is running in this mode.
     */
    if (!project.isInitialized() || ApplicationManager.getApplication().isUnitTestMode()) {
      return;
    }

    RepositoryGraph repositoryGraph = repositoryGraphFactory.getRepositoryGraph(repository, isListingCommits);
    gitMacheteGraphTable.getModel().setRepositoryGraph(repositoryGraph);

    GuiUtils.invokeLaterIfNeeded(gitMacheteGraphTable::updateUI, ModalityState.NON_MODAL);
  }

  /**
   * Updates repository which is the base of graph table model. The change will be seen after
   * {@link GitMacheteGraphTableManager#refreshUI()}.
   */
  public void updateRepository() {
    Path pathToRepoRoot = Paths.get(Objects.requireNonNull(project.getBasePath()));
    repository = Try.of(() -> gitMacheteRepositoryBuilderFactory.create(pathToRepoRoot).build())
        .onFailure(e -> LOG.error("Unable to create Git Machete repository", e)).get();
  }

  public void updateAndRefreshInBackground() {
    if (project != null && !project.isDisposed()) {
      new Task.Backgroundable(project, "Updating Git Machete Repository And Refreshing") {
        @Override
        public void run(@Nonnull ProgressIndicator indicator) {
          updateRepository();
          refreshUI();
        }
      }.queue();
    }
  }

  private void subscribeToGitRepositoryChanges() {
    Topic<GitRepositoryChangeListener> topic = GitRepository.GIT_REPO_CHANGE;
    GitRepositoryChangeListener listener = repository -> updateAndRefreshInBackground();
    project.getMessageBus().connect().subscribe(topic, listener);
  }


  public boolean isListingCommits() {
    return isListingCommits;
  }

  public void setListingCommits(boolean listingCommits) {
    isListingCommits = listingCommits;
  }

  public GitMacheteGraphTable getGitMacheteGraphTable() {
    return gitMacheteGraphTable;
  }

  @Nullable
  public IGitMacheteRepository getRepository() {
    return repository;
  }
}
