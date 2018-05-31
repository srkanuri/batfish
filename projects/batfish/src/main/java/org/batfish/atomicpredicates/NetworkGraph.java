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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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

    // computeReachability();
    initializeReachableAps();
    allPairsReachability();
  }

  private void initializeReachableAps() {
    Set<String> allStates =
        Sets.union(
            _transitions.keySet(),
            _transitions
                .values()
                .stream()
                .map(Map::keySet)
                .flatMap(Set::stream)
                .collect(Collectors.toSet()));

    for (String state : allStates) {
      _reachableAps.put(state, TreeMultimap.create());
    }

    _graphRoots
        .parallelStream()
        .forEach(
            root -> {
              Multimap<Integer, String> allAps = _reachableAps.get(root);
              for (int i = 0; i < _atomicPredicates.size(); i++) {
                allAps.put(i, root);
              }
              _reachableAps.put(root, allAps);
            });
  }

  private void allPairsReachability() {
    Set<String> dirty = _graphRoots;

    while (!dirty.isEmpty()) {
      dirty =
          dirty
              .parallelStream()
              .filter(_transitions::containsKey)
              .flatMap(
                  preState ->
                      _transitions
                          .get(preState)
                          .entrySet()
                          .stream()
                          .flatMap(
                              transitionEntry -> {
                                String postState = transitionEntry.getKey();
                                Set<Integer> transitionAps = transitionEntry.getValue();

                                Multimap<Integer, String> preStateAps = _reachableAps.get(preState);
                                Multimap<Integer, String> postStateAps =
                                    _reachableAps.get(postState);

                                /*
                                 * To prevent deadlock, obtain the map locks in order determined by
                                 * the state names.
                                 */
                                boolean preStateFirst = preState.compareTo(postState) < 0;
                                Object lock1 = preStateFirst ? preStateAps : postStateAps;
                                Object lock2 = preStateFirst ? postStateAps : preStateAps;

                                synchronized (lock1) {
                                  synchronized (lock2) {
                                    boolean updated =
                                        preStateAps
                                            .asMap()
                                            .entrySet()
                                            .stream()
                                            .filter(entry -> transitionAps.contains(entry.getKey()))
                                            .map(
                                                entry ->
                                                    postStateAps.putAll(
                                                        entry.getKey(), entry.getValue()))
                                            // non-short-circuiting version of anyMatch
                                            .reduce(false, (b1, b2) -> b1 || b2);
                                    return updated ? Stream.of(postState) : Stream.empty();
                                  }
                                }
                              }))
              .collect(Collectors.toSet());
    }
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
    Map<String, Map<Integer, Set<String>>> apTerminalStates = new ConcurrentHashMap<>();
    // root -> AP -> disposition
    Map<String, Map<Integer, Set<String>>> apDispositions = new ConcurrentHashMap<>();
    _reachableAps
        .entrySet()
        .parallelStream()
        .filter(entry -> terminalStates.contains(entry.getKey()))
        .forEach(
            entry -> {
              String terminalState = entry.getKey();
              Multimap<Integer, String> aps = entry.getValue();
              if (!terminalStates.contains(terminalState)) {
                return;
              }
              aps.forEach(
                  (ap, root) -> {
                    apTerminalStates
                        .computeIfAbsent(root, k -> new ConcurrentHashMap<>())
                        .computeIfAbsent(ap, k -> new ConcurrentSkipListSet<>())
                        .add(terminalState);
                    apDispositions
                        .computeIfAbsent(root, k -> new ConcurrentHashMap<>())
                        .computeIfAbsent(ap, k -> new ConcurrentSkipListSet<>())
                        .add(dispostion(terminalState));
                  });
            });
    apDispositions.forEach(
        (root, rootApDispositions) ->
            rootApDispositions.forEach(
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
