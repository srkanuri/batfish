package org.batfish.atomicpredicates;

import java.util.List;
import java.util.SortedSet;
import net.sf.javabdd.BDD;

public interface BDDAtomizer {
  List<BDD> atoms();

  SortedSet<Integer> atoms(BDD bdd);
}
