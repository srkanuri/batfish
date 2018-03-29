package org.batfish.z3;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.microsoft.z3.BitVecSort;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.commons.lang3.StringUtils;
import org.batfish.common.BatfishException;
import org.batfish.z3.expr.visitors.BoolExprTransformer;

public class NodProgram {

  private final NodContext _context;

  private final List<BoolExpr> _queries;

  private final List<BoolExpr> _rules;

  public NodProgram(Context ctx, ReachabilityProgram... programs) {
    _context = new NodContext(ctx, programs);
    _queries =
        Arrays.stream(programs)
            .flatMap(
                program ->
                    program
                        .getQueries()
                        .stream()
                        .map(
                            booleanExpr ->
                                BoolExprTransformer.toBoolExpr(
                                    booleanExpr, program.getInput(), _context)))
            .collect(ImmutableList.toImmutableList());
    _rules =
        Arrays.stream(programs)
            .flatMap(
                program ->
                    program
                        .getRules()
                        .stream()
                        .map(
                            booleanExpr ->
                                BoolExprTransformer.toBoolExpr(
                                    booleanExpr, program.getInput(), _context)))
            .collect(ImmutableList.toImmutableList());
  }

  public NodContext getNodContext() {
    return _context;
  }

  public List<BoolExpr> getQueries() {
    return _queries;
  }

  public List<BoolExpr> getRules() {
    return _rules;
  }

  public String toSmt2String() {
    StringBuilder sb = new StringBuilder();
    toSmt2(sb::append);
    return sb.toString();
  }

  public void toSmt2(Writer writer) {
    toSmt2(
        str -> {
          try {
            writer.write(str);
          } catch (IOException e) {
            throw new BatfishException("exception writing smt2", e);
          }
        });
  }

  private void toSmt2(Consumer<String> stringConsumer) {
    String[] variablesAsNames = _context.getVariableNames().stream().toArray(String[]::new);

    String[] variablesAsDebruijnIndices =
        IntStream.range(0, variablesAsNames.length)
            .mapToObj(index -> String.format("(:var %d)", index))
            .toArray(String[]::new);

    Consumer<String> write =
        s ->
            stringConsumer.accept(
                StringUtils.replaceEach(s, variablesAsDebruijnIndices, variablesAsNames));

    _context
        .getVariableNames()
        .forEach(
            var -> {
              int size = _context.getVariables().get(var).getSortSize();
              stringConsumer.accept(String.format("(declare-var %s (_ BitVec %d))\n", var, size));
            });

    StringBuilder varSizesStringBuilder = new StringBuilder();
    _context
        .getBasicStateVariableSorts()
        .stream()
        .map(BitVecSort::getSize)
        .map(s -> String.format("(_ BitVec %d) ", s))
        .forEach(varSizesStringBuilder::append);
    String varSizesString = varSizesStringBuilder.toString();

    _context
        .getRelationDeclarations()
        .values()
        .stream()
        .map(funcDecl -> String.format("(declare-rel %s (%s))", funcDecl.getName(), varSizesString))
        .forEach(declaration -> write.accept(String.format("%s\n", declaration)));
    _rules.forEach(r -> write.accept(String.format("(rule %s)\n", r.toString())));

    stringConsumer.accept("\n");
    _queries.forEach(
        query -> write.accept(String.format("(query %s)\n", query.getFuncDecl().getName())));
  }
}
