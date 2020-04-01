package com.virtuslab.gitmachete.frontend.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import lombok.Getter;

import com.virtuslab.gitmachete.frontend.actions.RebaseCurrentBranchOntoParentAction;
import com.virtuslab.gitmachete.frontend.ui.table.GitMacheteGraphTableManager;

public class GitMachetePanel {
  private static final String TOGGLE_LIST_COMMIT_TEXT = "Toggle List Commits";
  private static final String TOGGLE_LIST_COMMIT_DESCRIPTION = "Toggle list commits";
  private static final String REFRESH_STATUS_TEXT = "Refresh Status";
  private static final String REFRESH_STATUS_DESCRIPTION = "Refresh status";
  private static final String REFRESH_STATUS_SHORT_NAME = "Refresh";
  private static final String REBASE_CURRENT_ONTO_PARENT_SHORT_NAME = "Rebase Current Branch Onto Parent";

  @Getter
  private final GitMacheteGraphTableManager gitMacheteGraphTableManager;

  public GitMachetePanel(Project project) {
    gitMacheteGraphTableManager = new GitMacheteGraphTableManager(project);
    gitMacheteGraphTableManager.updateAndRefreshInBackground();
  }

  public ActionToolbar createGitMacheteToolbar() {
    DefaultActionGroup gitMacheteActions = new DefaultActionGroup();

    DefaultActionGroup refresh = new DefaultActionGroup(REFRESH_STATUS_SHORT_NAME, /* popup */ false);
    refresh.add(new RefreshGitMacheteStatusAction());

    DefaultActionGroup toggleListCommits = new DefaultActionGroup(TOGGLE_LIST_COMMIT_TEXT, /* popup */ false);
    toggleListCommits.add(new ToggleListCommitsAction());

    DefaultActionGroup rebaseCurrentBranchOntoParent = new DefaultActionGroup(REBASE_CURRENT_ONTO_PARENT_SHORT_NAME,
        /* popup */ false);
    rebaseCurrentBranchOntoParent.add(new RebaseCurrentBranchOntoParentAction());

    gitMacheteActions.addAll(refresh, toggleListCommits, rebaseCurrentBranchOntoParent);

    ActionToolbar toolbar = ActionManager.getInstance()
        .createActionToolbar(GitMacheteContentProvider.GIT_MACHETE_TOOLBAR, gitMacheteActions, /* horizontal */ false);
    toolbar.setTargetComponent(gitMacheteGraphTableManager.getGitMacheteGraphTable());
    return toolbar;
  }

  private class RefreshGitMacheteStatusAction extends AnAction {
    RefreshGitMacheteStatusAction() {
      super(REFRESH_STATUS_TEXT, REFRESH_STATUS_DESCRIPTION, AllIcons.Actions.Refresh);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      gitMacheteGraphTableManager.updateAndRefreshInBackground();
    }
  }

  private class ToggleListCommitsAction extends ToggleAction implements DumbAware {
    ToggleListCommitsAction() {
      super(TOGGLE_LIST_COMMIT_TEXT, TOGGLE_LIST_COMMIT_DESCRIPTION, AllIcons.Actions.ShowHiddens);
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return gitMacheteGraphTableManager.isListingCommits();
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      gitMacheteGraphTableManager.setListingCommits(state);
      gitMacheteGraphTableManager.refreshUI();
    }
  }
}