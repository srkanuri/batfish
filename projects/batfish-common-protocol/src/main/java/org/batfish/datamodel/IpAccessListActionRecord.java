package org.batfish.datamodel;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;

public class IpAccessListActionRecord implements Serializable {

  private static final String PROP_ACL_NAME = "aclName";

  private static final String PROP_ACTION = "action";

  private static final String PROP_DEFAULT_DENY = "defaultDeny";

  private static final String PROP_LINE_DESCRIPTION = "lineDescription";

  private static final long serialVersionUID = 1L;

  private final String _aclName;

  private final LineAction _action;

  private final Boolean _defaultDeny;

  private final String _lineDescription;

  @JsonCreator
  public IpAccessListActionRecord(
      @JsonProperty(PROP_ACL_NAME) String aclName,
      @JsonProperty(PROP_ACTION) LineAction action,
      @JsonProperty(PROP_DEFAULT_DENY) Boolean defaultDeny,
      @JsonProperty(PROP_LINE_DESCRIPTION) String lineDescription) {
    _aclName = aclName;
    _action = action;
    _defaultDeny = defaultDeny;
    _lineDescription = lineDescription;
  }

  @JsonProperty(PROP_ACL_NAME)
  public String getAclName() {
    return _aclName;
  }

  @JsonProperty(PROP_ACTION)
  public LineAction getAction() {
    return _action;
  }

  @JsonProperty(PROP_DEFAULT_DENY)
  public Boolean getDefaultDeny() {
    return _defaultDeny;
  }

  @JsonProperty(PROP_LINE_DESCRIPTION)
  public String getLineDescription() {
    return _lineDescription;
  }
}
