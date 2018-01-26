package org.batfish.z3;

import com.google.common.collect.ImmutableMap;
import com.microsoft.z3.BitVecExpr;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.FuncDecl;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.batfish.z3.node.Comment;
import org.batfish.z3.node.IfExpr;
import org.batfish.z3.node.RuleExpr;
import org.batfish.z3.node.Statement;

public class NodProgram {

  private Context _context;

  private final List<BoolExpr> _queries;

  private final Map<String, FuncDecl> _relationDeclarations;

  private final List<BoolExpr> _rules;

  private final Map<String, BitVecExpr> _variables;

  private final Map<String, BitVecExpr> _variablesAsConsts;

  private final Map<String, Integer> _variableSizes;

  private final boolean _useSMT;

  public NodProgram(Context context) {
    this(context,false);
  }
  public NodProgram(Context context, boolean useSMT) {
    _context = context;
    _queries = new ArrayList<>();
    _relationDeclarations = new LinkedHashMap<>();
    _rules = new ArrayList<>();
    _variables = new LinkedHashMap<>();
    _variableSizes = new LinkedHashMap<>();
    _variablesAsConsts = new LinkedHashMap<>();
    _useSMT = useSMT;
  }

  public NodProgram append(NodProgram queryProgram) {
    NodProgram result = new NodProgram(_context, _useSMT);
    result._queries.addAll(_queries);
    result._relationDeclarations.putAll(_relationDeclarations);
    result._rules.addAll(_rules);
    result._variables.putAll(_variables);
    result._variableSizes.putAll(_variableSizes);
    result._variablesAsConsts.putAll(_variablesAsConsts);
    result._queries.addAll(queryProgram._queries);
    result._relationDeclarations.putAll(queryProgram._relationDeclarations);
    result._rules.addAll(queryProgram._rules);
    result._variables.putAll(queryProgram._variables);
    result._variableSizes.putAll(queryProgram._variableSizes);
    result._variablesAsConsts.putAll(queryProgram._variablesAsConsts);
    return result;
  }

  public Context getContext() {
    return _context;
  }

  public boolean getUseSMT() {
    return _useSMT;
  }

  public List<BoolExpr> getQueries() {
    return _queries;
  }

  public Map<String, FuncDecl> getRelationDeclarations() {
    return _relationDeclarations;
  }

  public List<BoolExpr> getRules() {
    return _rules;
  }

  public Map<String, BitVecExpr> getVariables() {
    return _variables;
  }

  public Map<String, BitVecExpr> getVariablesAsConsts() {
    return _variablesAsConsts;
  }

  public Map<String, Integer> getVariableSizes() {
    return _variableSizes;
  }

  public String toSmt2String() {
    StringBuilder sb = new StringBuilder();
    Synthesizer.PACKET_VAR_SIZES.forEach(
        (var, size) -> sb.append(String.format("(declare-var %s (_ BitVec %d))\n", var, size)));
    StringBuilder sizeSb = new StringBuilder("(");
    if(!_useSMT) {
      Synthesizer.PACKET_VAR_SIZES.values()
          .forEach(s -> sizeSb.append(String.format(" (_ BitVec %d)", s)));
    }
    String sizes = sizeSb.append(")").toString();
    _relationDeclarations
        .keySet()
        .forEach(relation -> {
              String fmt = _useSMT ? "(declare-fun %s %s Bool)\n" : "(declare-rel %s %s)\n";
              sb.append(String.format(fmt, relation, sizes));
            });
    _rules.forEach(r -> sb.append(String.format("(assert %s)\n", r.toString())));
    
    sb.append("\n");
    String[] intermediate = new String[] {sb.toString()};
    final AtomicInteger currentVar = new AtomicInteger(0);
    Synthesizer.PACKET_VAR_SIZES
        .keySet()
        .stream()
        .collect(
            ImmutableMap.toImmutableMap(
                Function.identity(), v -> String.format("(:var %d)", currentVar.getAndIncrement())))
        .forEach(
            (name, var) ->
                intermediate[0] =
                    intermediate[0].replaceAll(Pattern.quote(var), Matcher.quoteReplacement(name)));
    return intermediate[0];
  }

  // for solving with SMT
  public List<BoolExpr> rewriteRulesForSMT(Context ctx, List<RuleExpr> rules) {
    assert getUseSMT();

    Map<BoolExpr, BoolExpr> antecedents = new HashMap<>();
    List<BoolExpr> exprs = new ArrayList<>();

    BoolExpr f = ctx.mkFalse();

    for (RuleExpr rule : rules) {
      if(rule.getSubExpression() instanceof IfExpr) {
        IfExpr sub = (IfExpr) rule.getSubExpression();
        BoolExpr ant = sub.getAntecedent().toBoolExpr(this);
        BoolExpr con = sub.getConsequent().toBoolExpr(this);
        antecedents.put(con, ctx.mkOr(ant, antecedents.getOrDefault(con, f)));
      } else {
        exprs.add(rule.toBoolExpr(this));
      }
    }

    antecedents.forEach((con,ant) -> exprs.add(ctx.mkEq(con,ant)));

    return exprs;
  }
}
