package org.batfish.datamodel.questions;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.answers.AutocompleteSuggestion;
import org.batfish.datamodel.answers.Schema;

/**
 * Enables specification a set of node properties.
 *
 * <p>Currently supported example specifier:
 *
 * <ul>
 *   <li>ntp-servers —> gets NTP servers using a configured Java function
 *   <li>ntp.* gets all properties that start with 'ntp'
 * </ul>
 *
 * <p>In the future, we might add other specifier types, e.g., those based on Json Path
 */
public class HypothesisPropertySpecifier extends PropertySpecifier {

  public static Map<String, PropertyDescriptor<Configuration>> JAVA_MAP =
      new ImmutableMap.Builder<String, PropertyDescriptor<Configuration>>()
          .put(
              "LoggingServers",
              new PropertyDescriptor<>(Configuration::getLoggingServers, Schema.set(Schema.STRING)))
          .put(
              "NtpServers",
              new PropertyDescriptor<>(Configuration::getNtpServers, Schema.set(Schema.STRING)))
          .put(
              "TacacsServers",
              new PropertyDescriptor<>(Configuration::getTacacsServers, Schema.set(Schema.STRING)))
          .put(
              "DnsServers",
              new PropertyDescriptor<>(Configuration::getDnsServers, Schema.set(Schema.STRING)))
          .put(
              "SnmpTrapServers",
              new PropertyDescriptor<>(
                  Configuration::getSnmpTrapServers, Schema.set(Schema.STRING)))
          .build();

  public static final HypothesisPropertySpecifier ALL = new HypothesisPropertySpecifier(".*");

  private final String _expression;

  private final Pattern _pattern;

  @JsonCreator
  public HypothesisPropertySpecifier(String expression) {
    _expression = expression.trim().toLowerCase();
    _pattern = Pattern.compile(_expression.trim().toLowerCase()); // canonicalize
  }

  /**
   * Returns a list of suggestions based on the query. The current implementation treats the query
   * as a prefix of the property string.
   *
   * @param query The query to auto complete
   * @return The list of suggestions
   */
  public static List<AutocompleteSuggestion> autoComplete(String query) {
    return PropertySpecifier.baseAutoComplete(query, JAVA_MAP.keySet());
  }

  @Override
  public Set<String> getMatchingProperties() {
    return JAVA_MAP
        .keySet()
        .stream()
        .filter(prop -> _pattern.matcher(prop).matches())
        .collect(Collectors.toSet());
  }

  @Override
  @JsonValue
  public String toString() {
    return _expression;
  }
}
