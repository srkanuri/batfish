package org.batfish.symbolic.abstraction;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.batfish.symbolic.Graph;

public class Abstraction {

  private Graph _graph;

  private AbstractionMap _abstractionMap;

  public Abstraction(Graph graph, @Nullable AbstractionMap abstractionMap) {
    this._graph = graph;
    this._abstractionMap = abstractionMap;
  }

  public Graph getGraph() {
    return _graph;
  }

  public AbstractionMap getAbstractionMap() {
    return _abstractionMap;
  }

  @Nonnull
  public Set<String> mapConcreteToAbstract(Collection<String> concreteNodes) {
    if (getAbstractionMap() == null) {
      return new HashSet<>(concreteNodes);
    }
    Set<String> abstractNodes = new HashSet<>();
    for (String c : concreteNodes) {
      Set<String> abs = getAbstractionMap().getAbstractRepresentatives(c);
      abstractNodes.addAll(abs);
    }
    return abstractNodes;
  }
}
