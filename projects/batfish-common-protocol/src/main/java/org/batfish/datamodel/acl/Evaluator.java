package org.batfish.datamodel.acl;

import com.google.common.collect.ImmutableList;
import java.util.Map;
import org.batfish.datamodel.Flow;
import org.batfish.datamodel.IpAccessList;
import org.batfish.datamodel.IpAccessListActionRecord;
import org.batfish.datamodel.IpSpace;
import org.batfish.datamodel.LineAction;

public class Evaluator implements GenericAclLineMatchExprVisitor<Boolean> {

  public static boolean matches(
      AclLineMatchExpr item,
      Flow flow,
      String srcInterface,
      Map<String, IpAccessList> availableAcls,
      Map<String, IpSpace> namedIpSpaces) {
    return item.accept(
        new Evaluator(flow, srcInterface, availableAcls, namedIpSpaces, ImmutableList.builder()));
  }

  private final Map<String, IpAccessList> _availableAcls;
  private final Flow _flow;

  private final String _srcInterface;
  private final Map<String, IpSpace> _namedIpSpaces;
  private final ImmutableList.Builder<IpAccessListActionRecord> _actionRecords;

  public Evaluator(
      Flow flow,
      String srcInterface,
      Map<String, IpAccessList> availableAcls,
      Map<String, IpSpace> namedIpSpaces,
      ImmutableList.Builder<IpAccessListActionRecord> actionRecords) {
    _srcInterface = srcInterface;
    _flow = flow;
    _availableAcls = availableAcls;
    _namedIpSpaces = namedIpSpaces;
    _actionRecords = actionRecords;
  }

  @Override
  public Boolean visitAndMatchExpr(AndMatchExpr andMatchExpr) {
    return andMatchExpr.getConjuncts().stream().allMatch(c -> c.accept(this));
  }

  @Override
  public Boolean visitFalseExpr(FalseExpr falseExpr) {
    return false;
  }

  @Override
  public Boolean visitMatchHeaderSpace(MatchHeaderSpace matchHeaderSpace) {
    return matchHeaderSpace.getHeaderspace().matches(_flow, _namedIpSpaces);
  }

  @Override
  public Boolean visitMatchSrcInterface(MatchSrcInterface matchSrcInterface) {
    return matchSrcInterface.getSrcInterfaces().contains(_srcInterface);
  }

  @Override
  public Boolean visitNotMatchExpr(NotMatchExpr notMatchExpr) {
    return !notMatchExpr.getOperand().accept(this);
  }

  @Override
  public Boolean visitOrMatchExpr(OrMatchExpr orMatchExpr) {
    return orMatchExpr.getDisjuncts().stream().anyMatch(d -> d.accept(this));
  }

  @Override
  public Boolean visitPermittedByAcl(PermittedByAcl permittedByAcl) {
    return _availableAcls
            .get(permittedByAcl.getAclName())
            .filter(_flow, _srcInterface, _availableAcls, _namedIpSpaces, _actionRecords)
            .getAction()
        == LineAction.ACCEPT;
  }

  @Override
  public Boolean visitTrueExpr(TrueExpr trueExpr) {
    return true;
  }
}
