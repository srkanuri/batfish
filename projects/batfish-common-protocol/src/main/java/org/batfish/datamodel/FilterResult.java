package org.batfish.datamodel;

import com.google.common.collect.ImmutableList;
import java.util.List;

public class FilterResult {

  private final LineAction _action;

  private final List<IpAccessListActionRecord> _actionRecords;

  private final Integer _matchLine;

  public FilterResult(
      Integer matchLine, LineAction action, Iterable<IpAccessListActionRecord> actionRecords) {
    _action = action;
    _actionRecords = ImmutableList.copyOf(actionRecords);
    _matchLine = matchLine;
  }

  public LineAction getAction() {
    return _action;
  }

  public List<IpAccessListActionRecord> getActionRecords() {
    return _actionRecords;
  }

  public Integer getMatchLine() {
    return _matchLine;
  }
}
