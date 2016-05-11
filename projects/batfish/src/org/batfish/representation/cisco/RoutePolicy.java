package org.batfish.representation.cisco;

import java.util.List;
import java.util.ArrayList;

import org.batfish.common.util.ReferenceCountedStructure;

public class RoutePolicy extends ReferenceCountedStructure {

   private static final long serialVersionUID = 1L;

   private String _policyName;
   private List<RoutePolicyStatement> _stmtList;

   public RoutePolicy(String name) {
      _policyName = name;
      _stmtList = new ArrayList<RoutePolicyStatement>();
   }

   public String getPolicyName() {
      return _policyName;
   }

   public List<RoutePolicyStatement> getStatements() {
      return _stmtList;
   }

}