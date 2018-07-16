package org.batfish.dataplane.ibdp;

import java.io.Serializable;
import java.util.SortedMap;
import org.batfish.datamodel.AbstractRoute;
import org.batfish.datamodel.DataPlane;
import org.batfish.datamodel.GenericRib;

public class IncrementalDataPlane implements DataPlane, Serializable {

  @Override
  public SortedMap<String, SortedMap<String, GenericRib<AbstractRoute>>> getRibs() {
    return _ribs;
  }
}
