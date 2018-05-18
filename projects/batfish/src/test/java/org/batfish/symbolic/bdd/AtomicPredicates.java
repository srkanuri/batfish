package org.batfish.symbolic.bdd;

import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
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

    for (BDD pred1 : inputPreds) {
      Set<BDD> newPreds = new HashSet<>();
      for (BDD pred2 : preds) {
        BDD newPred = pred1.and(pred2);
        if (!newPred.isZero()) {
          newPreds.add(newPred);
        }

        newPred = pred1.not().and(pred2);
        if (!newPred.isZero()) {
          newPreds.add(newPred);
        }
      }
      preds = newPreds;
    }

    return preds;
  }
}
