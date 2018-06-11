package org.batfish.symbolic.bdd;

import java.util.IdentityHashMap;
import java.util.function.Function;
import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDFactory;
import org.batfish.datamodel.AclIpSpace;
import org.batfish.datamodel.IpIpSpace;
import org.batfish.datamodel.IpSpace;
import org.batfish.datamodel.IpWildcardIpSpace;
import org.batfish.datamodel.IpWildcardSetIpSpace;
import org.batfish.datamodel.PrefixIpSpace;

/** Extends {@link IpSpaceToBDD} to memoize IpSpaces. */
public final class MemoizedIpSpaceToBDD extends IpSpaceToBDD {
  private final IdentityHashMap<IpSpace, BDD> _memoTable;
  private int _hits = 0;

  public MemoizedIpSpaceToBDD(BDDFactory factory, BDDInteger var) {
    super(factory, var);
    _memoTable = new IdentityHashMap<>();
  }

  public int getHits() {
    return _hits;
  }

  private <T extends IpSpace> BDD memoize(T ipSpace, Function<T, BDD> method) {
    if (_memoTable.containsKey(ipSpace)) {
      _hits++;
    }
    return _memoTable.computeIfAbsent(ipSpace, x -> method.apply(ipSpace));
  }

  @Override
  public BDD visitAclIpSpace(AclIpSpace aclIpSpace) {
    return memoize(aclIpSpace, super::visitAclIpSpace);
  }

  @Override
  public BDD visitIpIpSpace(IpIpSpace ipSpace) {
    return memoize(ipSpace, super::visitIpIpSpace);
  }

  @Override
  public BDD visitIpWildcardIpSpace(IpWildcardIpSpace ipSpace) {
    return memoize(ipSpace, super::visitIpWildcardIpSpace);
  }

  @Override
  public BDD visitIpWildcardSetIpSpace(IpWildcardSetIpSpace ipSpace) {
    return memoize(ipSpace, super::visitIpWildcardSetIpSpace);
  }

  @Override
  public BDD visitPrefixIpSpace(PrefixIpSpace ipSpace) {
    return memoize(ipSpace, super::visitPrefixIpSpace);
  }
}
