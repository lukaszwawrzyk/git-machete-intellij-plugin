package com.virtuslab.gitcore.gitcoreapi;

import java.nio.file.Path;

public interface GitCoreRepositoryFactory {
  IGitCoreRepository create(Path pathToRoot);
}