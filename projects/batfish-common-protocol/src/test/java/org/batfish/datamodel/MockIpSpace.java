package org.batfish.datamodel;

import com.google.common.collect.ImmutableList.Builder;
import java.util.Map;
import java.util.Objects;
import org.batfish.datamodel.visitors.GenericIpSpaceVisitor;

public class MockIpSpace extends IpSpace {

  /** */
  private static final long serialVersionUID = 1L;

  private final int _num;

  public MockIpSpace(int num) {
    _num = num;
  }

  @Override
  public <R> R accept(GenericIpSpaceVisitor<R> visitor) {
    throw new UnsupportedOperationException(
        "no implementation for generated method"); // TODO Auto-generated method stub
  }

  @Override
  protected int compareSameClass(IpSpace o) {
    return Integer.compare(_num, ((MockIpSpace) o)._num);
  }

  @Override
  public IpSpace complement() {
    throw new UnsupportedOperationException(
        "no implementation for generated method"); // TODO Auto-generated method stub
  }

  @Override
  public boolean containsIp(Ip ip, Map<String, IpSpace> namedIpSpaces, Builder<AccessListActionRecord> actionRecords, String aclIpSpaceName) {
    throw new UnsupportedOperationException(
        "no implementation for generated method"); // TODO Auto-generated method stub
  }

  @Override
  protected boolean exprEquals(Object o) {
    return _num == ((MockIpSpace) o)._num;
  }

  @Override
  public int hashCode() {
    return Objects.hash(_num);
  }

  @Override
  public String toString() {
    return String.format("TestIpSpace%d", _num);
  }
}
