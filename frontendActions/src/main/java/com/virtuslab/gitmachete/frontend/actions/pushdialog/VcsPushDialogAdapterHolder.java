package com.virtuslab.gitmachete.frontend.actions.pushdialog;

import javax.swing.JRootPane;

import com.intellij.dvcs.push.PushSupport;
import com.intellij.dvcs.push.VcsPushOptionValue;
import com.intellij.dvcs.push.ui.VcsPushDialog;
import com.intellij.dvcs.repo.Repository;
import com.intellij.openapi.project.Project;
import org.checkerframework.checker.guieffect.qual.UIEffect;
import org.checkerframework.checker.initialization.qual.UnderInitialization;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * The holden adapter is required to pass custom {@link GitPushDialog} to {@link com.intellij.dvcs.push.PushController}.
 * It overrides only the methods that can be invoked by the controller and delegates its execution to custom dialog.
*/
public class VcsPushDialogAdapterHolder {

  private final VcsPushDialog vcsPushDialog;
  private final @UnknownInitialization GitPushDialog gitPushDialog;

  public VcsPushDialogAdapterHolder(
      Project project,
      java.util.List<? extends Repository> selectedRepositories,
      @Nullable Repository currentRepo,
      @UnderInitialization GitPushDialog gitPushDialog) {
    this.gitPushDialog = gitPushDialog;
    this.vcsPushDialog = new MyVcsPushDialog(project, selectedRepositories, currentRepo);
  }

  public VcsPushDialog getVcsPushDialog(@UnknownInitialization VcsPushDialogAdapterHolder this) {
    assert vcsPushDialog != null : "VcsPushDialog is null";
    return vcsPushDialog;
  }

  private final class MyVcsPushDialog extends VcsPushDialog {

    @UIEffect
    MyVcsPushDialog(
        Project project, java.util.List<? extends Repository> selectedRepositories,
        @Nullable Repository currentRepo) {
      super(project, selectedRepositories, currentRepo);
    }

    @Override
    @UIEffect
    public @Nullable VcsPushOptionValue getAdditionalOptionValue(PushSupport support) {
      return gitPushDialog.getAdditionalOptionValue(support);
    }

    @Override
    @UIEffect
    public void enableOkActions(boolean value) {
      gitPushDialog.enableOkActions(value);
    }

    @Override
    @UIEffect
    public void updateOkActions() {
      gitPushDialog.updateOkActions();
    }

    @Override
    @UIEffect
    public JRootPane getRootPane(@UnknownInitialization MyVcsPushDialog this) {
      return gitPushDialog.getRootPane();
    }
  }
}