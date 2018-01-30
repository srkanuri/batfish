package org.batfish.z3;

import com.google.common.math.LongMath;
import com.microsoft.z3.BitVecExpr;
import com.microsoft.z3.BitVecNum;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.Expr;
import com.microsoft.z3.Fixedpoint;
import com.microsoft.z3.FuncDecl;
import com.microsoft.z3.Model;
import com.microsoft.z3.Params;
import com.microsoft.z3.Solver;
import com.microsoft.z3.Status;
import com.microsoft.z3.Z3Exception;
import java.math.RoundingMode;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.batfish.common.BatfishException;
import org.batfish.common.Pair;
import org.batfish.common.util.CommonUtil;
import org.batfish.config.Settings;
import org.batfish.datamodel.Flow;
import org.batfish.datamodel.Ip;
import org.batfish.datamodel.IpProtocol;
import org.batfish.datamodel.State;
import org.batfish.job.BatfishJob;
import org.batfish.z3.node.PreOutEdgeExpr;
import org.batfish.z3.node.RuleExpr;
import org.batfish.z3.node.Statement;

public final class NodJob extends BatfishJob<NodJobResult> {

  public static Flow createFlow(
      String node, String vrf, Map<String, Long> constraints, String tag) {
    Flow.Builder flowBuilder = new Flow.Builder();
    flowBuilder.setIngressNode(node);
    flowBuilder.setTag(tag);
    for (String varName : constraints.keySet()) {
      Long value = constraints.get(varName);
      switch (varName) {
        case Synthesizer.SRC_IP_VAR:
          flowBuilder.setSrcIp(new Ip(value));
          break;

        case Synthesizer.DST_IP_VAR:
          flowBuilder.setDstIp(new Ip(value));
          break;

        case Synthesizer.SRC_PORT_VAR:
          flowBuilder.setSrcPort(value.intValue());
          break;

        case Synthesizer.DST_PORT_VAR:
          flowBuilder.setDstPort(value.intValue());
          break;

        case Synthesizer.FRAGMENT_OFFSET_VAR:
          flowBuilder.setFragmentOffset(value.intValue());
          break;

        case Synthesizer.IP_PROTOCOL_VAR:
          flowBuilder.setIpProtocol(IpProtocol.fromNumber(value.intValue()));
          break;

        case Synthesizer.DSCP_VAR:
          flowBuilder.setDscp(value.intValue());
          break;

        case Synthesizer.ECN_VAR:
          flowBuilder.setEcn(value.intValue());
          break;

        case Synthesizer.STATE_VAR:
          flowBuilder.setState(State.fromNum(value.intValue()));
          break;

        case Synthesizer.ICMP_TYPE_VAR:
          flowBuilder.setIcmpType(value.intValue());
          break;

        case Synthesizer.ICMP_CODE_VAR:
          flowBuilder.setIcmpCode(value.intValue());
          break;

        case Synthesizer.PACKET_LENGTH_VAR:
          flowBuilder.setPacketLength(value.intValue());
          break;

        case Synthesizer.TCP_FLAGS_CWR_VAR:
          flowBuilder.setTcpFlagsCwr(value.intValue());
          break;

        case Synthesizer.TCP_FLAGS_ECE_VAR:
          flowBuilder.setTcpFlagsEce(value.intValue());
          break;

        case Synthesizer.TCP_FLAGS_URG_VAR:
          flowBuilder.setTcpFlagsUrg(value.intValue());
          break;

        case Synthesizer.TCP_FLAGS_ACK_VAR:
          flowBuilder.setTcpFlagsAck(value.intValue());
          break;

        case Synthesizer.TCP_FLAGS_PSH_VAR:
          flowBuilder.setTcpFlagsPsh(value.intValue());
          break;

        case Synthesizer.TCP_FLAGS_RST_VAR:
          flowBuilder.setTcpFlagsRst(value.intValue());
          break;

        case Synthesizer.TCP_FLAGS_SYN_VAR:
          flowBuilder.setTcpFlagsSyn(value.intValue());
          break;

        case Synthesizer.TCP_FLAGS_FIN_VAR:
          flowBuilder.setTcpFlagsFin(value.intValue());
          break;

        default:
          throw new BatfishException("invalid variable name");
      }
    }
    return flowBuilder.build();
  }

  private Synthesizer _dataPlaneSynthesizer;

  private final SortedSet<Pair<String, String>> _nodeVrfSet;

  private QuerySynthesizer _querySynthesizer;

  private String _tag;

  public NodJob(
      Settings settings,
      Synthesizer dataPlaneSynthesizer,
      QuerySynthesizer querySynthesizer,
      SortedSet<Pair<String, String>> nodeVrfSet,
      String tag) {
    super(settings);
    _dataPlaneSynthesizer = dataPlaneSynthesizer;
    _querySynthesizer = querySynthesizer;
    _nodeVrfSet = new TreeSet<>();
    _nodeVrfSet.addAll(nodeVrfSet);
    _tag = tag;
  }

  @Override
  public NodJobResult call() {
    long startTime = System.currentTimeMillis();
    long elapsedTime = 0;
    try (Context ctx = new Context()) {
      List<Statement> ruleStatements = _dataPlaneSynthesizer.synthesizeNodDataPlaneRules();
      NodProgram baseProgram = _dataPlaneSynthesizer.synthesizeNodProgram(ctx,ruleStatements);
      NodProgram queryProgram = _querySynthesizer.getNodProgram(baseProgram);
      NodProgram program = baseProgram.append(queryProgram);
      //      StringBuilder sb = new StringBuilder();
      //      sb.append(
      //          String.join(
      //              "\n",
      //              program
      //                  .getRelationDeclarations()
      //                  .values()
      //                  .stream()
      //                  .map(Object::toString)
      //                  .collect(Collectors.toList())
      //                  .toArray(new String[] {})));
      //      sb.append("\n");
      //      sb.append(
      //          String.join(
      //              "\n",
      //              program
      //                  .getRules()
      //                  .stream()
      //                  .map(Object::toString)
      //                  .collect(Collectors.toList())
      //                  .toArray(new String[] {})));
      //      sb.append("\n");
      //      CommonUtil.writeFile(Paths.get("/home/arifogel/scratch/dump"), sb.toString());
      //CommonUtil.writeFile(Paths.get("/tmp/nodpgm" + System.currentTimeMillis()), program.toSmt2String());
      Params p = ctx.mkParams();
      p.add("timeout", _settings.getZ3timeout());

      Solver solver = ctx.mkSolver();
      if(_dataPlaneSynthesizer.getUseSMT()) {
        // rewrite rules for SMT
        ReachabilityQuerySynthesizer rqs = (ReachabilityQuerySynthesizer) _querySynthesizer;
        List<Statement> stmts = new LinkedList<>();
        stmts.addAll(ruleStatements);
        stmts.addAll(rqs.getAllRules());

        List<RuleExpr> rules =
            stmts.stream()
                .filter(r -> r instanceof RuleExpr)
                .map(r -> (RuleExpr)r)
                .collect(Collectors.toList());

        // each consequent is true if and only if the disjunction of its antecedents is
        // (i.e. they are equal)
        Map<String,BoolExpr> antecedents = program.getRuleAntecedents(ctx,rules);

        // TODO: what to do about multiple queries?
        // the case below looks wrong too; fix that also

        Function<String,Expr> rel = nm ->
            ctx.mkApp( program.getRelationDeclarations().get(nm));
        Function<String,BoolExpr> varExpr = nm -> ctx.mkEq(rel.apply(nm), ctx.mkTrue());
        BiFunction<BoolExpr,BoolExpr,BoolExpr> eqNeg = (e1,e2) -> ctx.mkEq(e1, ctx.mkNot(e2));
        Function<Collection<BoolExpr>,BoolExpr> all = es ->
            es.stream().reduce(ctx.mkTrue(),(a,b) -> ctx.mkAnd(a,b));
        Function<Collection<BoolExpr>,BoolExpr> any = es ->
            es.stream().reduce(ctx.mkFalse(),(a,b) -> ctx.mkOr(a,b));
        Function<Collection<BoolExpr>,BoolExpr> none = es ->
            //ctx.mkNot(any.apply(es));
            all.apply(es.stream().map(e -> ctx.mkNot(e)).collect(Collectors.toList()));

        // Disposition sanity constraints
        // TODO: handle other dispositions
        // solver.add(ctx.mkNot(ctx.mkEq(rel.apply("R_accept"), rel.apply("R_drop"))));

        // Originate sanity constraints
        // 1. traffic must originate somewhere
        // 2. traffic can only originate from one place
        Set<BoolExpr> R_originates_vrfs =
            program.getRelationDeclarations().keySet().stream()
            .filter(nm -> nm.startsWith("R_originate_vrf_"))
            .map(varExpr)
            .collect(Collectors.toSet());

        int selectOriginateVrfBits = LongMath.log2(R_originates_vrfs.size(), RoundingMode.CEILING);
        BitVecExpr selectOriginateVrf =
            ctx.mkBVConst("Select_originate_vrf", selectOriginateVrfBits);

        // one of R_originates_vrfs must be true
        solver.add(any.apply(R_originates_vrfs));

        // only one of R_originates_vrfs must be true: each one implies none others
        /*
        int selectOriginateVrfValue = 0;
        for (BoolExpr orig : R_originates_vrfs) {
          solver.add(
              ctx.mkImplies(orig,
                  ctx.mkEq(selectOriginateVrf,
                      ctx.mkBV(selectOriginateVrfValue,selectOriginateVrfBits)
                      )));
          selectOriginateVrfValue++;
        }
        */
        R_originates_vrfs.forEach(orig -> {
          Set<BoolExpr> others = R_originates_vrfs.stream().collect(Collectors.toSet());
          others.remove(orig);
          solver.add(ctx.mkImplies(orig, none.apply(others)));
        });

        // ACL sanity constraints
        // TODO this should not be necessary.
        /*
        program.getRelationDeclarations().keySet().forEach(nm -> {
          if (nm.startsWith("P_acl_")) {
            // ACLs cannot both A (accept) and D (drop)
            String deniedNm = "D" + nm.substring(1);
            solver.add(eqNeg.apply(varExpr.apply(nm), varExpr.apply(deniedNm)));
          } else if (nm.startsWith("M_acl")) {
            // ACL filter lines cannot be both M (matched) and N (nonmatched)
            String nonmatchNm = "N" + nm.substring(1);
            solver.add(eqNeg.apply(varExpr.apply(nm), varExpr.apply(nonmatchNm)));
          }
        });
        */


        // Hop count constraints. 1 per node
        Map<String,BitVecExpr> hopCounts = new HashMap<>();
        BitVecExpr bv1 = ctx.mkBV(1, 8);
        Function<String,BitVecExpr> getHopCount = n -> {
          if(!hopCounts.containsKey(n)) {
            hopCounts.put(n, ctx.mkBVConst("HopCount_" + n, 8));
          }
          return hopCounts.get(n);
        };

        Map<String,Set<BoolExpr>> nodePreoutEdges = new HashMap<>();
        Function<String,Set<BoolExpr>> getNodePreoutEdges = n -> {
          if(!nodePreoutEdges.containsKey(n)) {
            nodePreoutEdges.put(n, new HashSet<>());
          }
          return nodePreoutEdges.get(n);
        };

        _dataPlaneSynthesizer.getTopologyEdges().forEach(e -> {
          String
              n1 = e.getNode1(), i1 = e.getInt1(),
              n2 = e.getNode2(), i2 = e.getInt2();
          BitVecExpr hc1 = getHopCount.apply(n1), hc2 = getHopCount.apply(n2);
          BoolExpr preOutExpr = new PreOutEdgeExpr(n1,i1,n2,i2).toBoolExpr(program);
          getNodePreoutEdges.apply(n1).add(preOutExpr);
          solver.add(ctx.mkEq(preOutExpr, ctx.mkEq(hc2, ctx.mkBVAdd(hc1, bv1))));
        });

        // Allow only one R_preout_edge variable to be true for each node
        nodePreoutEdges.forEach((n,edges) -> {
          edges.forEach(e -> {
            Set<BoolExpr> others = edges.stream().collect(Collectors.toSet());
            others.remove(e);

            String eNm = e.toString();
            BoolExpr ant = antecedents.get(eNm);
            ant = ctx.mkAnd(ant, none.apply(others));
            antecedents.put(eNm, ant);
          });
          /*
          if(edges.size() > 1) {
            int bvSize = LongMath.log2(edges.size(), RoundingMode.CEILING);
            BitVecExpr preOutSel = ctx.mkBVConst("Select_PreOut_" + n, bvSize);
            int i = 0;
            for (BoolExpr e : edges) {
              String eNm = e.toString();
              BoolExpr ant = antecedents.get(eNm);
              ant = ctx.mkAnd(ant, ctx.mkEq(preOutSel, ctx.mkBV(i, bvSize)));
              antecedents.put(eNm, ant);
              i++;
            }
          }
          */
        });

        antecedents.forEach((con,ant) -> {
          solver.add(ctx.mkEq(ctx.mkBoolConst(con), ant));
        });

        solver.add(program.getQueries().get(0));
      } else {
        p.add("fixedpoint.engine", "datalog");
        p.add("fixedpoint.datalog.default_relation", "doc");
        p.add("fixedpoint.print_answer", true);
        Fixedpoint fix = ctx.mkFixedpoint();
        fix.setParameters(p);
        for (FuncDecl relationDeclaration : program.getRelationDeclarations().values()) {
          fix.registerRelation(relationDeclaration);
        }
        for (BoolExpr rule : program.getRules()) {
          fix.addRule(rule, null);
        }
        for (BoolExpr query : program.getQueries()) {
          //CommonUtil.writeFile(Paths.get("/tmp/nodpgm2." + System.currentTimeMillis()), fix.toString());
          Long nodStart = System.currentTimeMillis();
          Status status = fix.query(query);
          Long nodEnd = System.currentTimeMillis();
          System.out.println("NOD query time = " + String.valueOf(nodEnd - nodStart));
          switch (status) {
          case SATISFIABLE:
            break;
          case UNKNOWN:
            throw new BatfishException("Query satisfiability unknown");
          case UNSATISFIABLE:
            break;
          default:
            throw new BatfishException("invalid status");
          }
        }
        Expr answer = fix.getAnswer();
        BoolExpr solverInput = null;
        if (answer.getArgs().length > 0) {
          List<Expr> reversedVarList = new ArrayList<>();
          reversedVarList.addAll(program.getVariablesAsConsts().values());
          Collections.reverse(reversedVarList);
          Expr[] reversedVars = reversedVarList.toArray(new Expr[] {});
          Expr substitutedAnswer = answer.substituteVars(reversedVars);
          solverInput = (BoolExpr) substitutedAnswer;
        } else {
          solverInput = (BoolExpr) answer;
        }
        if (_querySynthesizer.getNegate()) {
          solverInput = ctx.mkNot(solverInput);
        }
        solver.add(solverInput);
      }
      CommonUtil.writeFile(Paths.get("/tmp/smtquery.smt." + System.currentTimeMillis()), solver.toString());
      Long solverStart = System.currentTimeMillis();
      Status solverStatus = solver.check();
      Long solverEnd = System.currentTimeMillis();
      System.out.println("SMT solver time = " + String.valueOf(solverEnd - solverStart));
      switch (solverStatus) {
        case SATISFIABLE:
          break;

        case UNKNOWN:
          throw new BatfishException("Stage 2 query satisfiability unknown:\n" + solver.getReasonUnknown());

        case UNSATISFIABLE:
          elapsedTime = System.currentTimeMillis() - startTime;
          return new NodJobResult(elapsedTime, _logger.getHistory());

        default:
          throw new BatfishException("invalid status");
      }
      Model model = solver.getModel();
      Long flowsStartTime = System.currentTimeMillis();
      Map<String, Long> constraints = new LinkedHashMap<>();
      program.getVariablesAsConsts().entrySet().forEach(entry -> {
        BitVecNum v = (BitVecNum) model.getConstInterp(entry.getValue());
        // null means the constant is unconstrained
        if(v != null) {
          long val = v.getLong();
          constraints.put(entry.getKey(), val);
        }
      });
      Set<Flow> flows = new HashSet<>();
      for (Pair<String, String> nodeVrf : _nodeVrfSet) {
        String node = nodeVrf.getFirst();
        String vrf = nodeVrf.getSecond();
        Flow flow = createFlow(node, vrf, constraints);
        flows.add(flow);
      }
      Long flowsElapsedTime = System.currentTimeMillis() - flowsStartTime;
      System.out.println("Flows time = " + String.valueOf(flowsElapsedTime));
      elapsedTime = System.currentTimeMillis() - startTime;
      return new NodJobResult(elapsedTime, _logger.getHistory(), flows);
    } catch (Z3Exception e) {
      elapsedTime = System.currentTimeMillis() - startTime;
      return new NodJobResult(
          elapsedTime,
          _logger.getHistory(),
          new BatfishException("Error running NoD on concatenated data plane", e));
    } catch (Exception e) {
      throw new BatfishException("Query failed.", e);
    } finally {
      Pair<String,String> p = _nodeVrfSet.first();
      String endPoint = p.getFirst() + "_" + p.getSecond();
      System.out.println("NOD query for " + endPoint + " elapsed time = " + String.valueOf(elapsedTime));
    }
  }

  private Flow createFlow(String node, String vrf, Map<String, Long> constraints) {
    return createFlow(node, vrf, constraints, _tag);
  }
}
