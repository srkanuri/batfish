package org.batfish.common.util;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public abstract class ReferenceCountedStructure implements Serializable {

  /** */
  private static final long serialVersionUID = 1L;

  private transient Map<Object, String> _referers;

  @JsonIgnore
  public void addReference(Object referer, String description) {
    if (_referers == null) {
      _referers = new HashMap<>();
    }
    _referers.put(referer, description);
  }

  @JsonIgnore
  public int getNumReferers() {
    return _referers == null ? 0 : _referers.size();
  }

  @JsonIgnore
  public boolean isUnused() {
    return _referers == null || _referers.isEmpty();
  }
}
