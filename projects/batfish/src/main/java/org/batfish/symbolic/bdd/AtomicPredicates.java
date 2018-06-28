package org.batfish.symbolic.bdd;

import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;
import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDFactory;

public class AtomicPredicates {

  private final BDDFactory _bddFactory;

  public AtomicPredicates(BDDFactory bddFactory) {
    _bddFactory = bddFactory;
  }

  public List<BDD> atomize(BDD... inputPreds) {
    return atomize(ImmutableList.copyOf(inputPreds));
  }

  public List<BDD> atomize(Collection<BDD> inputPreds) {
    List<BDD> preds = ImmutableList.of(_bddFactory.one());

    long totalTime = System.currentTimeMillis();
    long[] times = new long[inputPreds.size()];
    int[] sizes = new int[inputPreds.size()];
    int round = 0;

    for (BDD pred1 : inputPreds) {
      times[round] = System.currentTimeMillis();
      List<BDD> newPreds =
          preds
              .stream()
              .flatMap(pred2 -> Stream.of(pred1.and(pred2), pred1.not().and(pred2)))
              .filter(bdd -> !bdd.isZero())
              .collect(ImmutableList.toImmutableList());
      preds = newPreds;
      times[round] = System.currentTimeMillis() - times[round];
      sizes[round] = preds.size();
      round++;
    }
    totalTime = System.currentTimeMillis() - totalTime;

    return preds;
  }
}
