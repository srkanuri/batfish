package org.batfish.symbolic.bdd;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.Collection;
import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDFactory;
import net.sf.javabdd.JFactory;
import org.batfish.datamodel.Ip;
import org.batfish.datamodel.IpSpace;
import org.batfish.datamodel.Prefix;
import org.junit.Before;
import org.junit.Test;

public class AtomicPredicatesTests {
  private AtomicPredicates _atomicPredicates;
  private IpSpaceToBDD _ipSpaceToBdd;

  @Before
  public void init() {
    BDDFactory factory = JFactory.init(10000, 1000);
    factory.disableReorder();
    factory.setCacheRatio(64);
    factory.setVarNum(32); // reserve 32 1-bit variables
    BDDInteger ipAddrBdd = BDDInteger.makeFromIndex(factory, 32, 0, true);
    _ipSpaceToBdd = new IpSpaceToBDD(factory, ipAddrBdd);
    _atomicPredicates = new AtomicPredicates(factory);
  }

  private Collection<BDD> toBDD(IpSpace... ipSpaces) {
    return Arrays.stream(ipSpaces)
        .map(_ipSpaceToBdd::visit)
        .collect(ImmutableList.toImmutableList());
  }

  @Test
  public void testSingleIp() {
    BDD bdd1 = _ipSpaceToBdd.toBDD(new Ip("1.2.3.4"));
    assertThat(_atomicPredicates.atomize(bdd1), containsInAnyOrder(bdd1, bdd1.not()));
  }

  @Test
  public void testNonOverlappingPrefixes() {
    BDD bdd1 = _ipSpaceToBdd.toBDD(Prefix.parse("1.0.0.0/8"));
    BDD bdd2 = _ipSpaceToBdd.toBDD(Prefix.parse("2.2.0.0/16"));
    assertThat(
        _atomicPredicates.atomize(bdd1, bdd2),
        containsInAnyOrder(bdd1, bdd2, bdd1.not().and(bdd2.not())));
  }

  @Test
  public void testOverlappingPrefixes() {
    BDD bdd1 = _ipSpaceToBdd.toBDD(Prefix.parse("1.0.0.0/8"));
    BDD bdd2 = _ipSpaceToBdd.toBDD(Prefix.parse("1.1.0.0/16"));
    assertThat(
        _atomicPredicates.atomize(bdd1, bdd2),
        containsInAnyOrder(bdd2, bdd1.and(bdd2.not()), bdd1.not()));
  }
}
