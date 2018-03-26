package org.batfish.z3.expr;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;
import org.batfish.z3.state.OriginateVrf;

/**
 * Remove states that can't possibly be derived from the origination states. (i.e. if there is no
 * chain of rules from an OriginateVrf to the state)
 *
 * <p>Remove states that are irrelevant to the query (i.e. if there is no sequence of rules that
 * leads from them to a query state
 */
public class ReachabilityProgramOptimizer {
  private final List<StateExpr> _queryStates;
  private boolean _useFindDerivableStates;
  private Map<StateExpr, Set<RuleStatement>> _derivingRules;
  private Map<StateExpr, Set<RuleStatement>> _dependentRules;
  private Set<StateExpr> _states;
  private Set<RuleStatement> _rules;
  private List<OriginateVrf> _originateVrfs;
  private boolean _removeIrrelevantRulesAndStates;
  private boolean _removeUnreachableStates;
  private boolean _removeUnusableRules;

  public ReachabilityProgramOptimizer(
      List<OriginateVrf> originateVrfs, List<RuleStatement> rules, List<QueryStatement> queries) {
    _rules = new HashSet<>(rules);
    _derivingRules = new HashMap<>();
    _dependentRules = new HashMap<>();
    _states = new HashSet<>();
    _originateVrfs = originateVrfs;
    _queryStates = queries.stream().map(QueryStatement::getStateExpr).collect(Collectors.toList());
    _useFindDerivableStates = true;
    _removeIrrelevantRulesAndStates = false;
    _removeUnreachableStates = false;
    _removeUnusableRules = true;

    init();
    computeFixpoint();
  }

  private void init() {
    _states.addAll(_originateVrfs);
    _states.addAll(_queryStates);
    _rules.forEach(
        rule -> {
          _derivingRules
              .computeIfAbsent(rule.getPostconditionState(), s -> new HashSet<>())
              .add(rule);
          rule.getPreconditionStates()
              .forEach(stateExpr ->
                  _dependentRules
                      .computeIfAbsent(stateExpr, s -> new HashSet<>())
                      .add(rule));
          _states.add(rule.getPostconditionState());
          _states.addAll(rule.getPreconditionStates());
        });
  }

  private void computeFixpoint() {
    int round = 0;
    boolean converged = false;

    int origStates = _states.size();
    int origRules = _rules.size();

    while (!converged) {
      round++;
      int states = _states.size();
      int rules = _rules.size();

      optimize();

      int statesRemoved = states - _states.size();
      int rulesRemoved = rules - _rules.size();
      System.out.println(
          String.format("Round %d removed %d states and %d rules.", round, states, rules));
      converged = statesRemoved == 0 && rulesRemoved == 0;
    }

    System.out.println(
        String.format("Nod optimization removed %d states and %d rules.",
            origStates - _states.size(),
            origRules - _rules.size()));
  }

  public Set<RuleStatement> getOptimizedRules() {
    // return them in the same order as the original rules.
    return _rules;
  }

  private void removeRules(Collection<RuleStatement> rulesToRemove) {
    _rules.removeAll(rulesToRemove);
    // removed rules no longer derive their poststate.
    rulesToRemove.forEach(
        rule -> {
          if (_derivingRules.containsKey(rule.getPostconditionState())) {
            _derivingRules.get(rule.getPostconditionState()).remove(rule);
          }
        });
  }

  private void optimize() {
    if(_removeUnusableRules) {
      removeRules(_rules.stream().filter(this::unusableRule).collect(Collectors.toList()));
    }
    if(_removeUnreachableStates) {
      removeStates(_states.stream().filter(this::unreachableState).collect(Collectors.toList()));
    }
    if(_removeIrrelevantRulesAndStates) {
      removeIrrelevantRulesAndStates();
    }
    if(_useFindDerivableStates) {
      findDerivableStates();
    }
  }

  private void removeStates(Collection<StateExpr> statesToRemove) {
    _states.removeAll(statesToRemove);
    statesToRemove.forEach(_derivingRules::remove);
  }

  /* A rule is unusable if one of its precondition states has been removed */
  private boolean unusableRule(RuleStatement rule) {
    return !_states.containsAll(rule.getPreconditionStates());
  }

  /* A state is unreachable if no rules can derive it */
  private boolean unreachableState(StateExpr state) {
    return !_derivingRules.containsKey(state) || _derivingRules.get(state).isEmpty();
  }

  private void removeIrrelevantRulesAndStates() {
    Set<RuleStatement> relevantRules = new HashSet<>();

    Set<StateExpr> relevantStates = new HashSet<>(_queryStates);
    Queue<StateExpr> stateWorkQueue = new ArrayDeque<>(_queryStates);

    while (!stateWorkQueue.isEmpty()) {
      StateExpr state = stateWorkQueue.poll();
      if (_derivingRules.containsKey(state)) {
        relevantRules.addAll(_derivingRules.get(state));
        _derivingRules
            .get(state)
            .forEach(
                rule ->
                    rule.getPreconditionStates()
                        .stream()
                        .filter(preState -> !relevantStates.contains(preState))
                        .forEach(
                            preState -> {
                              relevantStates.add(preState);
                              stateWorkQueue.add(preState);
                            }));
      }
    }

    _rules = relevantRules;
    _states = relevantStates;
  }

  /* Find all states forward reachable from the graph roots (states without prestates) */
  private void findDerivableStates() {
    // initially, the derivable states are the roots
    Set<StateExpr> derivableStates = new HashSet<>();

    assert(derivableStates.containsAll(_originateVrfs));
    // should also have some acl states, etc.

    Set<StateExpr> newStates =
        _derivingRules
            .entrySet()
            .stream()
            .filter(stateAndDerivingRules ->
                stateAndDerivingRules
                    .getValue()
                    .stream()
                    .anyMatch(rule -> rule.getPreconditionStates().isEmpty()))
            .map(Entry::getKey)
            .collect(Collectors.toSet());
    Set<RuleStatement> visitedRules = new HashSet<>();

    // keep looking for new forward-reachable states until we're done
    long startTime = System.currentTimeMillis();
    int iterations = 0;
    while(!newStates.isEmpty()) {
      iterations++;
      derivableStates.addAll(newStates);

      HashSet<StateExpr> newNewStates = new HashSet<>();
      newStates
          .stream()
          .filter(_dependentRules::containsKey)
          .forEach(state ->
              _dependentRules
                  .get(state)
                  .stream()
                  .filter(rule -> !visitedRules.contains(rule)
                      && derivableStates.containsAll(rule.getPreconditionStates()))
                  .forEach(rule -> {
                    StateExpr postState = rule.getPostconditionState();
                    if(!derivableStates.contains(postState)) {
                      newNewStates.add(postState);
                    }
                    visitedRules.add(rule);
                  }));
      newStates = newNewStates;
      /*
      _rules
          .stream()
          .filter(rule -> !visitedRules.contains(rule)
              && derivableStates.containsAll(rule.getPreconditionStates()))
              */
    }
      _states = derivableStates;
      _rules = visitedRules;
    long elapsed = System.currentTimeMillis() - startTime;
    System.out.println(
        String.format("findDerivableStates completed %d iterations in %d milliseconds",
            iterations, elapsed));
  }
}
