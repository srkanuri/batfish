package org.batfish.representation.cisco;

import java.util.List;
import java.util.Set;
import org.batfish.common.Warnings;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.routing_policy.expr.BooleanExpr;
import org.batfish.datamodel.routing_policy.expr.DestinationNetwork;
import org.batfish.datamodel.routing_policy.expr.Disjunction;
import org.batfish.datamodel.routing_policy.expr.MatchPrefixSet;
import org.batfish.datamodel.routing_policy.expr.NamedPrefixSet;

public class RouteMapMatchIpPrefixListLine extends RouteMapMatchLine {

  private static final long serialVersionUID = 1L;

  private Set<String> _listNames;

  public RouteMapMatchIpPrefixListLine(Set<String> names) {
    _listNames = names;
  }

  public Set<String> getListNames() {
    return _listNames;
  }

  @Override
  public BooleanExpr toBooleanExpr(Configuration c, CiscoConfiguration cc, Warnings w) {
    Disjunction d = new Disjunction();
    List<BooleanExpr> disjuncts = d.getDisjuncts();
    for (String listName : _listNames) {
      PrefixList list = cc.getPrefixLists().get(listName);
      if (list != null) {
        disjuncts.add(new MatchPrefixSet(new DestinationNetwork(), new NamedPrefixSet(listName)));
      }
    }
    return d.simplify();
  }
}
