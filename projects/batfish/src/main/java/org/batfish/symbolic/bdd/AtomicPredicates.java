package org.batfish.symbolic.bdd;

import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDFactory;

public class AtomicPredicates {

  private final BDDFactory _bddFactory;

  public AtomicPredicates(BDDFactory bddFactory) {
    _bddFactory = bddFactory;
  }

  public Set<BDD> atomize(BDD... inputPreds) {
    return atomize(ImmutableList.copyOf(inputPreds));
  }

  public Set<BDD> atomize(Collection<BDD> inputPreds) {
    Set<BDD> preds = new HashSet<>();
    preds.add(_bddFactory.one());

    long totalTime = 0;
    long[] times = new long[inputPreds.size()];
    int[] sizes = new int[inputPreds.size()];
    int round = 0;

    for (BDD pred1 : inputPreds) {
      times[round] = System.currentTimeMillis();
      Set<BDD> newPreds =
          preds
              .stream()
              .flatMap(pred2 -> Stream.of(pred1.and(pred2), pred1.not().and(pred2)))
              .filter(bdd -> !bdd.isZero())
              .collect(Collectors.toSet());
      preds = newPreds;
      times[round] = System.currentTimeMillis() - times[round];
      totalTime += times[round];
      sizes[round] = preds.size();
      round++;
    }

    return preds;
  }
}
