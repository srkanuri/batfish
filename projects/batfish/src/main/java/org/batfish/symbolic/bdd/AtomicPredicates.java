package org.batfish.symbolic.bdd;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.ImmutableSortedSet.Builder;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.SortedSet;
import java.util.stream.Stream;
import net.sf.javabdd.BDD;
import org.batfish.atomicpredicates.BDDAtomizer;

public class AtomicPredicates implements BDDAtomizer {
  private final List<BDD> _atoms;
  private final int[] _roundSizes;
  private final long[] _roundTimes;
  private final long _totalTime;

  public AtomicPredicates(Collection<BDD> bdds) {
    List<BDD> atoms = ImmutableList.of(bdds.iterator().next().getFactory().one());

    long totalTime = System.currentTimeMillis();
    _roundTimes = new long[bdds.size()];
    _roundSizes = new int[bdds.size()];
    int round = 0;

    for (BDD pred1 : bdds) {
      _roundTimes[round] = System.currentTimeMillis();
      List<BDD> newPreds =
          atoms
              .stream()
              .flatMap(pred2 -> Stream.of(pred1.and(pred2), pred1.not().and(pred2)))
              .filter(bdd -> !bdd.isZero())
              .collect(ImmutableList.toImmutableList());
      atoms = newPreds;
      _roundTimes[round] = System.currentTimeMillis() - _roundTimes[round];
      _roundSizes[round] = atoms.size();
      round++;
    }
    _totalTime = System.currentTimeMillis() - totalTime;
    _atoms = ImmutableList.copyOf(atoms);
  }

  @Override
  public List<BDD> atoms() {
    return _atoms;
  }

  @Override
  public SortedSet<Integer> atoms(BDD bdd) {
    ImmutableSortedSet.Builder<Integer> indices = new Builder<>(Comparator.naturalOrder());
    for (int i = 0; i < _atoms.size(); i++) {
      if (!_atoms.get(i).and(bdd).isZero()) {
        indices.add(i);
      }
    }
    return indices.build();
  }
}
