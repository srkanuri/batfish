package org.batfish.atomicpredicates;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import net.sf.javabdd.BDD;

public class NetworkGraph {
  private final List<BDD> _atomicPredicates;

  // preState --> postState --> predicate
  private final Map<String, Map<String, SortedSet<Integer>>> _transitions;

  private final Set<String> _graphRoots;

  private final Map<String, SortedSet<Integer>> _reachableAps;

  NetworkGraph(
      List<BDD> atomicPredicates,
      Set<String> graphRoots,
      Map<String, Map<String, SortedSet<Integer>>> transitions) {
    _atomicPredicates = atomicPredicates;
    _graphRoots = ImmutableSet.copyOf(graphRoots);
    _transitions = transitions;
    _reachableAps = new HashMap<>();

    SortedSet<Integer> allAps = new TreeSet<>();
    for (int i = 0; i < _atomicPredicates.size(); i++) {
      allAps.add(i);
    }
    for (String root : _graphRoots) {
      _reachableAps.put(root, allAps);
    }

    computeReachability();
  }

  private void computeReachability() {
    // each iteration, only process nodes that we need to.
    Set<String> dirty = _graphRoots;

    while (!dirty.isEmpty()) {
      Set<String> newDirty = new HashSet<>();

      for (String preState : dirty) {
        SortedSet<Integer> preStateAps = _reachableAps.get(preState);
        _transitions
            .get(preState)
            .forEach(
                (postState, transitionAps) -> {
                  if (updatePredicates(postState, Sets.intersection(preStateAps, transitionAps))) {
                    newDirty.add(postState);
                  }
                });
      }

      dirty = newDirty;
    }
  }

  private boolean updatePredicates(String state, SetView<Integer> newAps) {
    if (_reachableAps.containsKey(state)) {
      return _reachableAps.get(state).addAll(newAps);
    } else {
      _reachableAps.put(state, new TreeSet<>(newAps));
      return true;
    }
  }

  public Map<String, SortedSet<Integer>> getReachableAps() {
    return _reachableAps;
  }
}
