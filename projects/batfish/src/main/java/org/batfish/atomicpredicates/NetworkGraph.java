package org.batfish.atomicpredicates;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.TreeMultimap;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import net.sf.javabdd.BDD;

public final class NetworkGraph {
  private final List<BDD> _atomicPredicates;

  // preState --> postState --> predicate
  private final Map<String, Map<String, SortedSet<Integer>>> _transitions;

  private final Set<String> _graphRoots;

  private final Map<String, Multimap<Integer, String>> _reachableAps;

  NetworkGraph(
      List<BDD> atomicPredicates,
      Set<String> graphRoots,
      Map<String, Map<String, SortedSet<Integer>>> transitions) {
    _atomicPredicates = atomicPredicates;
    _graphRoots = ImmutableSet.copyOf(graphRoots);
    _transitions = transitions;
    _reachableAps = new HashMap<>();

    for (String root : _graphRoots) {
      Multimap<Integer, String> allAps = TreeMultimap.create();
      for (int i = 0; i < _atomicPredicates.size(); i++) {
        allAps.put(i, root);
      }
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
        Multimap<Integer, String> preStateAps = _reachableAps.get(preState);
        _transitions
            .getOrDefault(preState, ImmutableMap.of())
            .forEach(
                (postState, transitionAps) ->
                    transitionAps.forEach(
                        ap -> {
                          if (!preStateAps.containsKey(ap)) {
                            return;
                          }
                          Collection<String> sources = preStateAps.get(ap);
                          if (_reachableAps
                              .computeIfAbsent(postState, k -> TreeMultimap.create())
                              .putAll(ap, sources)) {
                            newDirty.add(postState);
                          }
                        }));
      }

      dirty = newDirty;
    }
  }

  public Map<String, Multimap<Integer, String>> getReachableAps() {
    return _reachableAps;
  }
}
