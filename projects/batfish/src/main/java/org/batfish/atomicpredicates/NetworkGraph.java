package org.batfish.atomicpredicates;

import static org.batfish.common.util.CommonUtil.toImmutableMap;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.batfish.z3.expr.StateExpr;

public final class NetworkGraph {
  // preState --> postState --> predicate
  private final Map<StateExpr, Map<StateExpr, SortedSet<Integer>>> _transitions;

  private final Map<StateExpr, SortedSet<Integer>> _graphRoots;

  private final Map<StateExpr, Multimap<Integer, StateExpr>> _reachableAps;

  private Set<StateExpr> _terminalStates;

  NetworkGraph(
      Map<StateExpr, SortedSet<Integer>> graphRoots,
      Map<StateExpr, Map<StateExpr, SortedSet<Integer>>> transitions) {
    _transitions = transitions;
    _graphRoots =
        toImmutableMap(
            graphRoots, Entry::getKey, entry -> ImmutableSortedSet.copyOf(entry.getValue()));
    _reachableAps = new HashMap<>();
    _terminalStates = computeTerminalStates();

    initializeReachableAps();
    parallelReachability();
  }

  /** Find all states an AP can reach from a given root. */
  public Set<StateExpr> reach(StateExpr root, Integer ap) {
    return _reachableAps
        .entrySet()
        .stream()
        .filter(entry -> entry.getValue().containsEntry(ap, root))
        .map(Entry::getKey)
        .collect(ImmutableSet.toImmutableSet());
  }

  private void initializeReachableAps() {
    Streams.concat(
            _graphRoots.keySet().stream(),
            _transitions.keySet().stream(),
            _transitions.values().stream().map(Map::keySet).flatMap(Set::stream))
        .forEach(state -> _reachableAps.put(state, HashMultimap.create()));

    _graphRoots.forEach(
        (stateExpr, aps) -> {
          Multimap<Integer, StateExpr> sources = _reachableAps.get(stateExpr);
          aps.forEach(ap -> sources.put(ap, stateExpr));
          _reachableAps.put(stateExpr, sources);
        });
  }

  private void parallelReachability() {
    Set<StateExpr> dirty = _graphRoots.keySet();

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
                                assert preState.getClass() != postState.getClass();
                                boolean preStateFirst =
                                    preState
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
                                            /*
                                             * non-short-circuiting version of anyMatch.
                                             * Needed to make sure we consume the entire stream.
                                             */
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
    Set<StateExpr> dirty = _graphRoots.keySet();

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

  public class MultipathConsistencyViolation {
    public final StateExpr originateState;
    public final Set<StateExpr> finalStates;
    public final Integer predicate;

    private MultipathConsistencyViolation(
        StateExpr originateState, Set<StateExpr> finalStates, Integer predicate) {
      this.originateState = originateState;
      this.finalStates = ImmutableSet.copyOf(finalStates);
      this.predicate = predicate;
    }
  }
  /** @return Final state --> Originate state --> APs with multipath inconsistency */
  public List<MultipathConsistencyViolation> detectMultipathInconsistency() {
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

    ImmutableList.Builder<MultipathConsistencyViolation> result = ImmutableList.builder();
    apTerminalStates.forEach(
        (root, rootApDispositions) ->
            rootApDispositions.forEach(
                (ap, terminals) -> {
                  if (terminals.size() > 1) {
                    System.out.println(
                        String.format(
                            "detected multipath inconsistency: %s -> %s -> %s",
                            root, ap, terminals));
                    result.add(new MultipathConsistencyViolation(root, terminals, ap));
                  }
                }));
    return result.build();
  }
}
