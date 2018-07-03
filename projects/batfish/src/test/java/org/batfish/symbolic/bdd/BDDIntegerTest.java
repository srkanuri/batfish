package org.batfish.symbolic.bdd;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.util.Optional;
import net.sf.javabdd.BDD;
import org.junit.Test;

public class BDDIntegerTest {
  @Test
  public void testSatAssignmentToLong() {
    BDDInteger dstIp = new BDDPacket().getDstIp();
    long value = 12345;
    BDD bdd = dstIp.value(value);
    assertThat(dstIp.getValueSatisfying(bdd), equalTo(Optional.of(value)));

    value = 0xFFFFFFFFL;
    bdd = dstIp.value(value);
    assertThat(dstIp.getValueSatisfying(bdd), equalTo(Optional.of(value)));
  }
}
