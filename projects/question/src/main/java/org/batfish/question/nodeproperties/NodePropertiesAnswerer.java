package org.batfish.question.nodeproperties;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multiset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import org.batfish.common.Answerer;
import org.batfish.common.plugin.IBatfish;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.answers.AnswerElement;
import org.batfish.datamodel.answers.Schema;
import org.batfish.datamodel.pojo.Node;
import org.batfish.datamodel.questions.DisplayHints;
import org.batfish.datamodel.questions.NodePropertySpecifier;
import org.batfish.datamodel.questions.PropertySpecifier;
import org.batfish.datamodel.questions.Question;
import org.batfish.datamodel.table.ColumnMetadata;
import org.batfish.datamodel.table.Row;
import org.batfish.datamodel.table.Row.RowBuilder;
import org.batfish.datamodel.table.TableAnswerElement;
import org.batfish.datamodel.table.TableMetadata;

public class NodePropertiesAnswerer extends Answerer {

  public static final String COL_NODE = "node";

  public NodePropertiesAnswerer(Question question, IBatfish batfish) {
    super(question, batfish);
  }

  /**
   * Creates {@link ColumnMetadata}s that the answer should have based on the {@code
   * propertySpecifier}.
   *
   * @param propertySpecifier The {@link NodePropertySpecifier} that describes the set of properties
   * @return The {@link List} of {@link ColumnMetadata}s
   */
  public static List<ColumnMetadata> createColumnMetadata(NodePropertySpecifier propertySpecifier) {
    return new ImmutableList.Builder<ColumnMetadata>()
        .add(new ColumnMetadata(COL_NODE, Schema.NODE, "Node", true, false))
        .addAll(
            propertySpecifier
                .getMatchingProperties()
                .stream()
                .map(
                    prop ->
                        new ColumnMetadata(
                            getColumnName(prop),
                            NodePropertySpecifier.JAVA_MAP.get(prop).getSchema(),
                            "Property " + prop,
                            false,
                            true))
                .collect(Collectors.toList()))
        .build();
  }

  static TableMetadata createTableMetadata(@Nonnull NodePropertiesQuestion question) {
    String textDesc = String.format("Properties of node ${%s}.", COL_NODE);
    DisplayHints dhints = question.getDisplayHints();
    if (dhints != null && dhints.getTextDesc() != null) {
      textDesc = dhints.getTextDesc();
    }
    return new TableMetadata(createColumnMetadata(question.getPropertySpec()), textDesc);
  }

  @Override
  public AnswerElement answer() {
    NodePropertiesQuestion question = (NodePropertiesQuestion) _question;
    Map<String, Configuration> configurations = _batfish.loadConfigurations();
    Set<String> nodes = question.getNodeRegex().getMatchingNodes(_batfish);

    TableMetadata tableMetadata = createTableMetadata(question);

    Multiset<Row> propertyRows =
        getProperties(
            question.getPropertySpec(), configurations, nodes, tableMetadata.toColumnMap());

    TableAnswerElement answer = new TableAnswerElement(tableMetadata);
    answer.postProcessAnswer(question, propertyRows);
    return answer;
  }

  /**
   * Gets properties of nodes.
   *
   * @param propertySpecifier Specifies which properties to get
   * @param configurations configuration to use in extractions
   * @param nodes the set of nodes to focus on
   * @param columns a map from column name to {@link ColumnMetadata}
   * @return A multiset of {@link Row}s where each row corresponds to a node and columns correspond
   *     to property values.
   */
  public static Multiset<Row> getProperties(
      NodePropertySpecifier propertySpecifier,
      Map<String, Configuration> configurations,
      Set<String> nodes,
      Map<String, ColumnMetadata> columns) {
    Multiset<Row> rows = HashMultiset.create();

    for (String nodeName : nodes) {
      RowBuilder row = Row.builder(columns).put(COL_NODE, new Node(nodeName));

      for (String property : propertySpecifier.getMatchingProperties()) {
        PropertySpecifier.fillProperty(
            NodePropertySpecifier.JAVA_MAP.get(property),
            configurations.get(nodeName),
            property,
            row);
      }

      rows.add(row.build());
    }

    return rows;
  }

  /** Returns the name of the column that contains the value of property {@code property} */
  public static String getColumnName(String property) {
    return property;
  }
}
