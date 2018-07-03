package org.batfish.atomicpredicates;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;
import java.util.List;
import java.util.SortedSet;
import net.sf.javabdd.BDD;

public class DummyAtomizer implements BDDAtomizer {

  @Override
  public List<BDD> atoms() {
    return ImmutableList.of();
  }

  @Override
  public SortedSet<Integer> atoms(BDD bdd) {
    return ImmutableSortedSet.of();
  }
}
