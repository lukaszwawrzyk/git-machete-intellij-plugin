package com.virtuslab.branchlayout.impl;

import java.util.Optional;

import lombok.Data;

import io.vavr.collection.List;

import com.virtuslab.branchlayout.api.IBranchLayoutEntry;

@Data
public class BranchLayoutEntry implements IBranchLayoutEntry {
  private final String name;
  private final String customAnnotation;
  private final List<IBranchLayoutEntry> subbranches;

  @Override
  public Optional<String> getCustomAnnotation() {
    return Optional.ofNullable(customAnnotation);
  }
}