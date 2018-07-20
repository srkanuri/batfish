package org.batfish.datamodel;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.util.Objects;
import javax.annotation.Nonnull;
import org.batfish.datamodel.routing_policy.expr.CommunitySetExpr;

/** A line in a CommunityList */
public class CommunityListLine implements Serializable {

  private static final String PROP_ACTION = "action";

  private static final String PROP_MATCH_CONDITION = "matchCondition";

  private static final long serialVersionUID = 1L;

  private final LineAction _action;

  private final CommunitySetExpr _matchCondition;

  public CommunityListLine(@Nonnull LineAction action, @Nonnull CommunitySetExpr matchCondition) {
    _action = action;
    _matchCondition = matchCondition;
  }

  @JsonCreator
  private @Nonnull CommunityListLine create(
      @JsonProperty(PROP_ACTION) LineAction action,
      @JsonProperty(PROP_MATCH_CONDITION) CommunitySetExpr matchCondition) {
    return new CommunityListLine(requireNonNull(action), requireNonNull(matchCondition));
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    } else if (!(o instanceof CommunityListLine)) {
      return false;
    }
    CommunityListLine other = (CommunityListLine) o;
    return _action == other._action && _matchCondition.equals(other._matchCondition);
  }

  /** The action the underlying access-list will take when this line matches a route. */
  @JsonProperty(PROP_ACTION)
  public @Nonnull LineAction getAction() {
    return _action;
  }

  @JsonProperty(PROP_MATCH_CONDITION)
  public @Nonnull CommunitySetExpr getMatchCondition() {
    return _matchCondition;
  }

  @Override
  public int hashCode() {
    return Objects.hash(_action.ordinal(), _matchCondition);
  }
}
