package org.batfish.atomicpredicates;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.collect.TreeMultimap;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.stream.Collectors;
import net.sf.javabdd.BDD;
import org.batfish.common.BatfishException;

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

  public void detectMultipathInconsistency() {
    // for each root and AP, find the set of reachable terminal nodes

    Set<String> preStates = _transitions.keySet();
    Set<String> postStates =
        _transitions
            .values()
            .stream()
            .flatMap(m -> m.keySet().stream())
            .collect(Collectors.toSet());
    Set<String> terminalStates = Sets.difference(postStates, preStates);

    // root -> AP -> terminal State
    Map<String, Multimap<Integer, String>> apTerminalStates = new HashMap<>();
    // root -> AP -> disposition
    Map<String, Multimap<Integer, String>> apDispositions = new HashMap<>();
    _reachableAps.forEach(
        (terminalState, aps) -> {
          if (!terminalStates.contains(terminalState)) {
            return;
          }
          aps.forEach(
              (ap, root) -> {
                apTerminalStates
                    .computeIfAbsent(root, k -> TreeMultimap.create())
                    .put(ap, terminalState);
                apDispositions
                    .computeIfAbsent(root, k -> TreeMultimap.create())
                    .put(ap, dispostion(terminalState));
              });
        });
    apDispositions.forEach(
        (root, rootApDispositions) ->
            rootApDispositions
                .asMap()
                .forEach(
                    (ap, dispositions) -> {
                      if (dispositions.size() > 1) {
                        System.out.println(
                            String.format(
                                "detected multipath inconsistency: %s -> %s -> %s",
                                root, ap, apTerminalStates.get(root).get(ap)));
                      }
                    }));
  }

  private String dispostion(String state) {
    if (state.startsWith("VrfAccept") || state.startsWith("NeighborUnreachable")) {
      return "Accept";
    }
    if (state.startsWith("VrfDrop") || state.startsWith("VrfNullRouted")) {
      return "Drop";
    }
    throw new BatfishException("WTF");
  }
}
