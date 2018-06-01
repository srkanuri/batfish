package org.batfish.atomicpredicates;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.sf.javabdd.BDD;
import org.apache.commons.lang3.builder.CompareToBuilder;
import org.batfish.z3.expr.StateExpr;

public final class NetworkGraph {
  private final List<BDD> _atomicPredicates;

  // preState --> postState --> predicate
  private final Map<StateExpr, Map<StateExpr, SortedSet<Integer>>> _transitions;

  private final Set<StateExpr> _graphRoots;

  private final Map<StateExpr, Multimap<Integer, StateExpr>> _reachableAps;

  private Set<StateExpr> _terminalStates;

  NetworkGraph(
      List<BDD> atomicPredicates,
      Set<StateExpr> graphRoots,
      Map<StateExpr, Map<StateExpr, SortedSet<Integer>>> transitions) {
    _atomicPredicates = atomicPredicates;
    _graphRoots = ImmutableSet.copyOf(graphRoots);
    _transitions = transitions;
    _reachableAps = new HashMap<>();
    _terminalStates = computeTerminalStates();

    // computeReachability();
    initializeReachableAps();
    allPairsReachability();
  }

  private void initializeReachableAps() {
    Streams.concat(
            _graphRoots.stream(),
            _transitions.keySet().stream(),
            _transitions.values().stream().map(Map::keySet).flatMap(Set::stream))
        .forEach(state -> _reachableAps.put(state, HashMultimap.create()));

    _graphRoots
        .parallelStream()
        .forEach(
            root -> {
              Multimap<Integer, StateExpr> allAps = _reachableAps.get(root);
              for (int i = 0; i < _atomicPredicates.size(); i++) {
                allAps.put(i, root);
              }
              _reachableAps.put(root, allAps);
            });
  }

  private void allPairsReachability() {
    Set<StateExpr> dirty = _graphRoots;

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
                                StateExpr postState = transitionEntry.getKey();
                                Set<Integer> transitionAps = transitionEntry.getValue();

                                Multimap<Integer, StateExpr> preStateAps =
                                    _reachableAps.get(preState);
                                Multimap<Integer, StateExpr> postStateAps =
                                    _reachableAps.get(postState);

                                /*
                                 * To prevent deadlock, obtain the map locks in order determined by
                                 * the state names.
                                 * TODO make StateExpr comparable
                                 */
                                boolean preStateFirst =
                                    preState.getClass() == postState.getClass()
                                        ? CompareToBuilder.reflectionCompare(preState, postState)
                                            < 0
                                        : preState
                                                .getClass()
                                                .getSimpleName()
                                                .compareTo(postState.getClass().getSimpleName())
                                            < 0;
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
    Set<StateExpr> dirty = _graphRoots;

    while (!dirty.isEmpty()) {
      Set<StateExpr> newDirty = new HashSet<>();

      for (StateExpr preState : dirty) {
        Multimap<Integer, StateExpr> preStateAps = _reachableAps.get(preState);
        _transitions
            .getOrDefault(preState, ImmutableMap.of())
            .forEach(
                (postState, transitionAps) ->
                    transitionAps.forEach(
                        ap -> {
                          if (!preStateAps.containsKey(ap)) {
                            return;
                          }
                          Collection<StateExpr> sources = preStateAps.get(ap);
                          if (_reachableAps
                              .computeIfAbsent(postState, k -> HashMultimap.create())
                              .putAll(ap, sources)) {
                            newDirty.add(postState);
                          }
                        }));
      }

      dirty = newDirty;
    }
  }

  public Map<StateExpr, Multimap<Integer, StateExpr>> getReachableAps() {
    return _reachableAps;
  }

  private Set<StateExpr> computeTerminalStates() {
    Set<StateExpr> preStates = _transitions.keySet();
    Set<StateExpr> postStates =
        _transitions
            .values()
            .stream()
            .flatMap(m -> m.keySet().stream())
            .collect(Collectors.toSet());
    return ImmutableSet.copyOf(Sets.difference(postStates, preStates));
  }

  public Set<StateExpr> getTerminalStates() {
    return _terminalStates;
  }

  public void detectMultipathInconsistency() {
    // root -> AP -> terminal State
    Map<StateExpr, Map<Integer, Set<StateExpr>>> apTerminalStates = new ConcurrentHashMap<>();
    _reachableAps
        .entrySet()
        .parallelStream()
        .filter(entry -> _terminalStates.contains(entry.getKey()))
        .forEach(
            entry -> {
              StateExpr terminalState = entry.getKey();
              Multimap<Integer, StateExpr> aps = entry.getValue();
              if (!_terminalStates.contains(terminalState)) {
                return;
              }
              aps.forEach(
                  (ap, root) -> {
                    apTerminalStates
                        .computeIfAbsent(root, k -> new ConcurrentHashMap<>())
                        .computeIfAbsent(
                            ap, k -> Collections.newSetFromMap(new ConcurrentHashMap<>()))
                        .add(terminalState);
                  });
            });
    apTerminalStates.forEach(
        (root, rootApDispositions) ->
            rootApDispositions.forEach(
                (ap, terminals) -> {
                  if (terminals.size() > 1) {
                    System.out.println(
                        String.format(
                            "detected multipath inconsistency: %s -> %s -> %s",
                            root, ap, terminals));
                  }
                }));
  }
}
