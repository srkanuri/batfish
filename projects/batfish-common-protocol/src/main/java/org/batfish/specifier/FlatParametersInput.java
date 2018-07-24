package org.batfish.specifier;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;

public class FlatParametersInput {

  public enum KeyType {
    HEADERSPACE,
    LOCATION
  }

  public enum CanonicalKeyName {
    LOC_SRC
  }

  public static class Key {
    public CanonicalKeyName _canonicalName;
    public KeyType _type;

    public Key(CanonicalKeyName canonicalName, KeyType type) {
      _canonicalName = canonicalName;
      _type = type;
    }
  }

  public static Map<String, Key> KEY_MAP =
      new ImmutableMap.Builder<String, Key>()
          .put("loc.src", new Key(CanonicalKeyName.LOC_SRC, KeyType.LOCATION))
          .build();

  public enum Operator {
    EQ("=="),
    GE(">="),
    GT(">"),
    LE("<="),
    LT("<"),
    MATCHES("~"),
    NE("!=");

    public static final String OPERATOR_PATTERN_STR = "[<>=!~]=?";

    private static final Map<String, Operator> MAP = initMap();

    @JsonCreator
    public static Operator fromString(String shorthand) {
      Operator value = MAP.get(shorthand);
      if (value == null) {
        throw new IllegalArgumentException(
            "No " + Operator.class.getSimpleName() + " with shorthand: '" + shorthand + "'");
      }
      return value;
    }

    private static Map<String, Operator> initMap() {
      ImmutableMap.Builder<String, Operator> map = ImmutableMap.builder();
      for (Operator value : Operator.values()) {
        String shorthand = value._shorthand;
        map.put(shorthand, value);
      }
      return map.build();
    }

    private final String _shorthand;

    Operator(String shorthand) {
      _shorthand = shorthand;
    }

    @Override
    public String toString() {
      return _shorthand;
    }
  }

  public static class Parameter {

    private static final String KEY_PATTERN_STR = "[a-z][a-z\\.]+";

    private static final Pattern PARAMATER_INPUT_PATTERN =
        Pattern.compile(
            "^\\s*("
                + KEY_PATTERN_STR
                + ")\\s+("
                + Operator.OPERATOR_PATTERN_STR
                + ")\\s+("
                + ".*"
                + ")\\s*$");

    private String _expression;
    private String _key;
    private Operator _operator;
    private String _value;

    public Parameter(String input) {
      Matcher matcher = PARAMATER_INPUT_PATTERN.matcher(input);
      if (matcher.find()) {
        _expression = input;
        _key = matcher.group(1);
        _operator = Operator.fromString(matcher.group(2).replaceAll("\\s+", ""));
        _value = matcher.group(3);
      } else {
        throw new IllegalArgumentException("Illegal parameter input pattern: '" + input + "'");
      }
    }
  }

  @Nonnull List<Parameter> _parameters = ImmutableList.of();

  public FlatParametersInput(String input) {
    if (input != null) {
      _parameters =
          Arrays.stream(input.split(" and "))
              .map(str -> new Parameter(str))
              .collect(ImmutableList.toImmutableList());
    }
  }
}
