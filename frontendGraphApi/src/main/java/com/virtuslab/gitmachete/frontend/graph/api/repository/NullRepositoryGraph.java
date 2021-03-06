package com.virtuslab.gitmachete.frontend.graph.api.repository;

import io.vavr.NotImplementedError;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import lombok.Getter;
import org.checkerframework.checker.index.qual.NonNegative;

import com.virtuslab.gitmachete.frontend.graph.api.elements.GraphEdge;
import com.virtuslab.gitmachete.frontend.graph.api.items.IGraphItem;
import com.virtuslab.gitmachete.frontend.graph.api.render.parts.IRenderPart;

public final class NullRepositoryGraph implements IRepositoryGraph {

  @Getter
  private static final NullRepositoryGraph instance = new NullRepositoryGraph();

  private NullRepositoryGraph() {}

  @Override
  public List<GraphEdge> getAdjacentEdges(@NonNegative int itemIndex) {
    return List.empty();
  }

  @Override
  public IGraphItem getGraphItem(@NonNegative int itemIndex) {
    throw new NotImplementedError();
  }

  @Override
  public @NonNegative int getNodesCount() {
    return 0;
  }

  @Override
  public List<? extends IRenderPart> getRenderParts(@NonNegative int itemIndex) {
    return List.empty();
  }

  @Override
  public List<Tuple2<GraphEdge, @NonNegative Integer>> getVisibleEdgesWithPositions(@NonNegative int itemIndex) {
    return List.empty();
  }
}
