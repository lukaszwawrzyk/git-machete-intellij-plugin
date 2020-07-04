package com.virtuslab.gitmachete.backend.api;

import io.vavr.collection.List;
import io.vavr.control.Option;

import com.virtuslab.branchlayout.api.IBranchLayout;
import com.virtuslab.gitmachete.backend.api.hook.IExecutionResult;

public final class NullGitMacheteRepositorySnapshot implements IGitMacheteRepositorySnapshot {
  private static final NullGitMacheteRepositorySnapshot instance = new NullGitMacheteRepositorySnapshot();

  private NullGitMacheteRepositorySnapshot() {}

  public static IGitMacheteRepositorySnapshot getInstance() {
    return instance;
  }

  @Override
  public Option<IBranchLayout> getBranchLayout() {
    return Option.none();
  }

  @Override
  public List<IGitMacheteRootBranch> getRootBranches() {
    return List.empty();
  }

  @Override
  public Option<IGitMacheteBranch> getCurrentBranchIfManaged() {
    return Option.none();
  }

  @Override
  public List<IGitMacheteBranch> getManagedBranches() {
    return List.empty();
  }

  @Override
  public Option<IGitMacheteBranch> getManagedBranchByName(String branchName) {
    return Option.none();
  }

  @Override
  public List<String> getSkippedBranchNames() {
    return List.empty();
  }

  @Override
  public Option<IExecutionResult> executeMachetePreRebaseHookIfPresent(IGitRebaseParameters gitRebaseParameters) {
    return Option.none();
  }

  @Override
  public OngoingRepositoryOperation getOngoingRepositoryOperation() {
    return OngoingRepositoryOperation.NO_OPERATION;
  }
}
