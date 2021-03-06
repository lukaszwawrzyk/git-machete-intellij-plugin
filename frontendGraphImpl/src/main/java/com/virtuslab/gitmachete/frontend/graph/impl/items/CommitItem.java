package com.virtuslab.gitmachete.frontend.graph.impl.items;

import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.UIUtil;
import lombok.Getter;
import org.checkerframework.checker.index.qual.NonNegative;
import org.checkerframework.checker.index.qual.Positive;

import com.virtuslab.gitmachete.backend.api.ICommitOfManagedBranch;
import com.virtuslab.gitmachete.backend.api.INonRootManagedBranchSnapshot;
import com.virtuslab.gitmachete.frontend.graph.api.items.GraphItemColor;
import com.virtuslab.gitmachete.frontend.graph.api.items.ICommitItem;

@Getter
public final class CommitItem extends BaseGraphItem implements ICommitItem {

  private final ICommitOfManagedBranch commit;
  private final INonRootManagedBranchSnapshot containingBranch;

  public CommitItem(
      ICommitOfManagedBranch commit,
      INonRootManagedBranchSnapshot containingBranch,
      GraphItemColor containingBranchGraphItemColor,
      @NonNegative int prevSiblingItemIndex,
      @Positive int nextSiblingItemIndex,
      @NonNegative int indentLevel) {
    super(containingBranchGraphItemColor, prevSiblingItemIndex, nextSiblingItemIndex, indentLevel);
    this.commit = commit;
    this.containingBranch = containingBranch;
  }

  @Override
  public SimpleTextAttributes getAttributes() {
    return new SimpleTextAttributes(SimpleTextAttributes.STYLE_ITALIC | SimpleTextAttributes.STYLE_SMALLER,
        UIUtil.getInactiveTextColor());
  }

  @Override
  public String getValue() {
    return commit.getShortMessage();
  }

  @Override
  public boolean hasBulletPoint() {
    return false;
  }

  @Override
  public boolean hasChildItem() {
    return false;
  }
}
