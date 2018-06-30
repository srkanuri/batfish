package org.batfish.atomicpredicates;

import java.util.SortedMap;
import java.util.SortedSet;
import net.sf.javabdd.BDD;

public interface BDDAtomizer {
  SortedMap<Integer, BDD> atoms();

  SortedSet<Integer> atoms(BDD bdd);
}
