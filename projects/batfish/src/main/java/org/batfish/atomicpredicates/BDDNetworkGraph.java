package org.batfish.atomicpredicates;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.sf.javabdd.BDD;
import org.batfish.symbolic.bdd.BDDOps;
import org.batfish.symbolic.bdd.BDDPacket;
import org.batfish.z3.expr.StateExpr;
import org.batfish.z3.state.NumberedQuery;

public class BDDNetworkGraph {
  // preState --> postState --> predicate
  private final Map<StateExpr, Map<StateExpr, Edge>> _edges;

  private final Map<StateExpr, BDD> _graphRoots;

  // natting postState --> root --> logical root
  private Map<StateExpr, Map<StateExpr, StateExpr>> _natRoots;
  private int _natRootCounter = 0;

  // postState --> source state --> predicate
  @VisibleForTesting final Map<StateExpr, Map<StateExpr, BDD>> _reachableStates;

  // for NAT
  private final BDD _srcIpVars;

  private Set<StateExpr> _terminalStates;

  BDDNetworkGraph(
      Map<StateExpr, BDD> graphRoots, Map<StateExpr, Map<StateExpr, Edge>> transitions) {
    _edges = transitions;
    _graphRoots = ImmutableMap.copyOf(graphRoots);
    _natRoots = new HashMap<>();
    _reachableStates = new HashMap<>();
    _graphRoots.forEach(
        (root, bdd) -> _reachableStates.computeIfAbsent(root, k -> new HashMap<>()).put(root, bdd));
    _terminalStates = computeTerminalStates();
    _srcIpVars = new BDDOps(BDDPacket.factory).and(new BDDPacket().getSrcIp().getBitvec());
  }

  private Set<StateExpr> computeTerminalStates() {
    Set<StateExpr> preStates = _edges.keySet();
    Set<StateExpr> postStates =
        _edges.values().stream().flatMap(m -> m.keySet().stream()).collect(Collectors.toSet());
    return ImmutableSet.copyOf(Sets.difference(postStates, preStates));
  }

  Map<StateExpr, Map<StateExpr, StateExpr>> getNatRoots() {
    return _natRoots;
  }

  public void computeReachability() {
    // each iteration, only process nodes that we need to.
    Map<StateExpr, StateExpr> dirty =
        _graphRoots
            .keySet()
            .stream()
            .collect(ImmutableMap.toImmutableMap(Function.identity(), Function.identity()));

    List<Long> roundTimes = new LinkedList<>();
    List<Integer> roundDirties = new LinkedList<>();

    while (!dirty.isEmpty()) {
      Map<StateExpr, StateExpr> newDirty = new HashMap<>();
      long time = System.currentTimeMillis();

      dirty.forEach(
          (preState, root) -> {
            Map<StateExpr, Edge> preStateOutEdges = _edges.get(preState);
            if (preStateOutEdges == null) {
              // preState has no out-edges
              return;
            }

            BDD preStateBDD = _reachableStates.get(preState).get(root);
            preStateOutEdges.forEach(
                (postState, edge) -> {
                  BDD result = preStateBDD.and(edge.getConstraint());
                  if (result.isZero()) {
                    return;
                  }

                  List<BDDSourceNat> sourceNats = edge.getSourceNats();
                  boolean natted = false;
                  if (sourceNats != null) {
                    BDD existSrcIp = result.exist(_srcIpVars);
                    BDD orig = result;
                    result = orig.getFactory().zero();
                    for (BDDSourceNat sourceNat : sourceNats) {
                      BDD match = orig.and(sourceNat._condition);
                      if (!match.isZero()) {
                        natted = true;
                        result = result.or(existSrcIp.and(sourceNat._updateSrcIp));
                        orig = orig.and(sourceNat._condition.not());
                      }
                    }
                    result = result.or(orig);
                  }

                  // we want to remember the source BDD, but NAT is destructive. So we're going
                  // to map back to it. We'll create a new dummy state for each combination of
                  // postState/root.
                  StateExpr logicalRoot =
                      natted
                          ? _natRoots
                              .computeIfAbsent(postState, k -> new HashMap<>())
                              .computeIfAbsent(root, k -> new NumberedQuery(this._natRootCounter++))
                          : root;

                  // update postState BDD reachable from source
                  Map<StateExpr, BDD> reachPostState =
                      _reachableStates.computeIfAbsent(postState, k -> new HashMap<>());
                  BDD oldReach = reachPostState.get(logicalRoot);
                  BDD newReach = oldReach == null ? result : oldReach.or(result);

                  if (oldReach == null || !oldReach.equals(newReach)) {
                    reachPostState.put(logicalRoot, newReach);
                    newDirty.put(postState, logicalRoot);
                  }
                });
          });

      dirty = newDirty;

      time = System.currentTimeMillis() - time;
      roundTimes.add(time);
      roundDirties.add(dirty.size());
    }
  }

  public class MultipathConsistencyViolation {
    public final StateExpr originateState;
    public final Set<StateExpr> finalStates;
    public final BDD predicate;

    private MultipathConsistencyViolation(
        StateExpr originateState, Set<StateExpr> finalStates, BDD predicate) {
      this.originateState = originateState;
      this.finalStates = ImmutableSet.copyOf(finalStates);
      this.predicate = predicate;
    }
  }

  public List<MultipathConsistencyViolation> detectMultipathInconsistency() {
    // root --> terminal state --> BDD
    Map<StateExpr, Map<StateExpr, BDD>> rootTerminalBDDs = new HashMap<>();
    _reachableStates
        .entrySet()
        .stream()
        .filter(entry -> _terminalStates.contains(entry.getKey()))
        .forEach(
            entry -> {
              StateExpr terminalState = entry.getKey();
              entry
                  .getValue()
                  .forEach(
                      (root, bdd) -> {
                        rootTerminalBDDs
                            .computeIfAbsent(root, k -> new HashMap<>())
                            .put(terminalState, bdd);
                      });
            });

    ImmutableList.Builder<MultipathConsistencyViolation> violations = ImmutableList.builder();
    class Candidate {
      StateExpr _root;
      StateExpr _term1;
      StateExpr _term2;
      BDD _term1BDD;
      BDD _term2BDD;

      Candidate(StateExpr root, StateExpr term1, StateExpr term2, BDD term1BDD, BDD term2BDD) {
        _root = root;
        _term1 = term1;
        _term2 = term2;
        _term1BDD = term1BDD;
        _term2BDD = term2BDD;
      }
    }

    // generate candidates in parallel, since we can
    List<Candidate> candidates =
        rootTerminalBDDs
            .entrySet()
            .parallelStream()
            .flatMap(
                entry -> {
                  StateExpr root = entry.getKey();
                  Map<StateExpr, BDD> terminalBDDs = entry.getValue();
                  return _terminalStates
                      .stream()
                      .filter(terminalBDDs::containsKey)
                      .flatMap(
                          term1 -> {
                            BDD term1BDD = terminalBDDs.get(term1);
                            return _terminalStates
                                .stream()
                                .filter(term2 -> term1 != term2)
                                .filter(terminalBDDs::containsKey)
                                // hack to avoid duplicate violations
                                .filter(term2 -> term1.toString().compareTo(term2.toString()) < 1)
                                .map(
                                    term2 -> {
                                      BDD term2BDD = terminalBDDs.get(term2);
                                      return new Candidate(root, term1, term2, term1BDD, term2BDD);
                                    });
                          });
                })
            .collect(Collectors.toList());

    return candidates
        .stream()
        .flatMap(
            candidate -> {
              BDD intersection = candidate._term1BDD.and(candidate._term2BDD);
              return intersection.isZero()
                  ? Stream.empty()
                  : Stream.of(
                      new MultipathConsistencyViolation(
                          candidate._root,
                          ImmutableSet.of(candidate._term1, candidate._term2),
                          intersection));
            })
        .collect(ImmutableList.toImmutableList());

    /*
    _terminalStates.stream().flatMap(term1 -> _terminalStates.stream().flatMap(term2 -> _graphRoots.keySet().stream().filter().map(root ->


    )));

    for (StateExpr term1 : _terminalStates) {
      for (StateExpr term2 : _terminalStates) {
        if (term1 == term2) {
          continue;
        }
        if (term1.toString().compareTo(term2.toString()) < 1) {
          // hack to avoid duplicate violations
          continue;
        }
        for (StateExpr root : _graphRoots.keySet()) {
          if (!rootTerminalBDDs.containsKey(root)) {
            continue;
          }
          BDD term1BDD = rootTerminalBDDs.get(root).get(term1);
          BDD term2BDD = rootTerminalBDDs.get(root).get(term2);
          if (term1BDD == null || term2BDD == null) {
            continue;
          }
          BDD intersection = term1BDD.and(term2BDD);
          if (intersection.isZero()) {
            continue;
          }
          violations.add(
              new MultipathConsistencyViolation(root, ImmutableSet.of(term1, term2), intersection));
        }
      }
    }
    return violations.build();
    */
  }
}
