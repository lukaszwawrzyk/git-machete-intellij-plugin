package com.virtuslab.gitmachete.gitmacheteapi;

public interface IGitRebaseParameters {
  String getCurrentBranchName();

  String getNewBaseBranchName();

  String getForkPointCommitHash();
}
