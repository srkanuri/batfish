package org.batfish.atomicpredicates;

import com.google.common.collect.ImmutableSortedSet;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.SortedSet;
import net.sf.javabdd.BDD;
import org.batfish.atomicpredicates.BDDTrie.AtomicPredicate;

public class BDDTrieAtomizer implements BDDAtomizer {
  private final BDDTrie _bddTrie;

  public BDDTrieAtomizer(Collection<BDD> bdds) {
    _bddTrie = new BDDTrie(bdds);
  }

  @Override
  public List<BDD> atoms() {
    return _bddTrie.atomicPredicates();
  }

  @Override
  public SortedSet<Integer> atoms(BDD bdd) {
    return _bddTrie
        .atomicPredicates(bdd)
        .stream()
        .map(AtomicPredicate::getId)
        .collect(ImmutableSortedSet.toImmutableSortedSet(Comparator.naturalOrder()));
  }
}
