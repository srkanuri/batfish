package org.batfish.symbolic.bdd;

import java.util.IdentityHashMap;
import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDFactory;
import org.batfish.datamodel.AclIpSpace;

/** Extends {@link IpSpaceToBDD} to memoize AclIpSpaces. */
public final class MemoizedIpSpaceToBDD extends IpSpaceToBDD {
  private final IdentityHashMap<AclIpSpace, BDD> _memoTable;

  public MemoizedIpSpaceToBDD(BDDFactory factory, BDDInteger var) {
    super(factory, var);
    _memoTable = new IdentityHashMap<>();
  }

  @Override
  public BDD visitAclIpSpace(AclIpSpace aclIpSpace) {
    return _memoTable.computeIfAbsent(aclIpSpace, super::visitAclIpSpace);
  }
}
