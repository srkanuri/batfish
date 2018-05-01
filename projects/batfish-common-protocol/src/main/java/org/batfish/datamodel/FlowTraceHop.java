package org.batfish.datamodel;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.SortedSet;
import javax.annotation.Nullable;

public final class FlowTraceHop implements Serializable {

  private static final String PROP_EDGE = "edge";

  private static final String PROP_FILTER_IN = "filterIn";

  private static final String PROP_FILTER_IN_ACTIONS = "filterInActions";

  private static final String PROP_FILTER_OUT = "filterOut";

  private static final String PROP_FILTER_OUT_ACTIONS = "filterOutActions";

  private static final String PROP_ROUTES = "routes";

  private static final String PROP_TRANSFORMED_FLOW = "transformedFlow";

  /** */
  private static final long serialVersionUID = 1L;

  private final Edge _edge;

  @Nullable private String _filterIn;

  private List<IpAccessListActionRecord> _filterInActions;

  @Nullable private String _filterOut;

  private List<IpAccessListActionRecord> _filterOutActions;

  private final SortedSet<String> _routes;

  private final Flow _transformedFlow;

  @JsonCreator
  public FlowTraceHop(
      @JsonProperty(PROP_EDGE) Edge edge,
      @JsonProperty(PROP_ROUTES) SortedSet<String> routes,
      @JsonProperty(PROP_TRANSFORMED_FLOW) Flow transformedFlow) {
    _edge = edge;
    _routes = routes;
    _transformedFlow = transformedFlow;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || !(obj instanceof FlowTraceHop)) {
      return false;
    }
    FlowTraceHop other = (FlowTraceHop) obj;
    return Objects.equals(_edge, other._edge)
        && Objects.equals(_routes, other._routes)
        && Objects.equals(_filterOut, other._filterOut)
        && Objects.equals(_filterOutActions, other._filterOutActions)
        && Objects.equals(_filterIn, other._filterIn)
        && Objects.equals(_filterInActions, other._filterInActions)
        && Objects.equals(_transformedFlow, other._transformedFlow);
  }

  @JsonProperty(PROP_EDGE)
  public Edge getEdge() {
    return _edge;
  }

  @JsonProperty(PROP_FILTER_IN)
  public String getFilterIn() {
    return _filterIn;
  }

  @JsonProperty(PROP_FILTER_IN_ACTIONS)
  public List<IpAccessListActionRecord> getFilterInActions() {
    return _filterInActions;
  }

  @JsonProperty(PROP_FILTER_OUT)
  public String getFilterOut() {
    return _filterOut;
  }

  @JsonProperty(PROP_FILTER_OUT_ACTIONS)
  public List<IpAccessListActionRecord> getFilterOutActions() {
    return _filterOutActions;
  }

  @JsonProperty(PROP_ROUTES)
  public SortedSet<String> getRoutes() {
    return _routes;
  }

  @JsonProperty(PROP_TRANSFORMED_FLOW)
  public Flow getTransformedFlow() {
    return _transformedFlow;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        _edge,
        _filterIn,
        _filterInActions,
        _filterOut,
        _filterOutActions,
        _routes,
        _transformedFlow);
  }

  @JsonProperty(PROP_FILTER_IN)
  public void setFilterIn(String filterIn) {
    _filterIn = filterIn;
  }

  @JsonProperty(PROP_FILTER_IN_ACTIONS)
  public void setFilterInActions(Iterable<IpAccessListActionRecord> filterInActions) {
    _filterInActions = ImmutableList.copyOf(filterInActions);
  }

  @JsonProperty(PROP_FILTER_OUT)
  public void setFilterOut(String filterOut) {
    _filterOut = filterOut;
  }

  @JsonProperty(PROP_FILTER_OUT_ACTIONS)
  public void setFilterOutActions(Iterable<IpAccessListActionRecord> filterOutActions) {
    _filterOutActions = ImmutableList.copyOf(filterOutActions);
  }
}
