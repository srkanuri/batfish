package org.batfish.symbolic.bdd;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Streams;
import java.util.Arrays;
import java.util.Map;
import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDFactory;
import net.sf.javabdd.JFactory;
import org.batfish.datamodel.AclIpSpace;
import org.batfish.datamodel.Ip;
import org.batfish.datamodel.IpSpace;
import org.batfish.datamodel.IpSpaceReference;
import org.batfish.datamodel.IpWildcard;
import org.batfish.datamodel.Prefix;
import org.junit.Before;
import org.junit.Test;

public class IpSpaceToBDDTest {
  private BDDOps _bddOps;
  private BDDFactory _factory;
  private BDDInteger _ipAddrBdd;
  private IpSpaceToBDD _ipSpaceToBdd;
  private Map<String, IpSpace> _namedIpSpaces;

  @Before
  public void init() {
    _factory = JFactory.init(10000, 1000);
    _factory.disableReorder();
    _factory.setCacheRatio(64);
    _factory.setVarNum(32); // reserve 32 1-bit variables
    _bddOps = new BDDOps(_factory);
    _ipAddrBdd = BDDInteger.makeFromIndex(_factory, 32, 0, true);
    _namedIpSpaces = ImmutableMap.of("foo", new Ip("1.2.3.4").toIpSpace());
    _ipSpaceToBdd = new IpSpaceToBDD(_factory, _ipAddrBdd, _namedIpSpaces);
  }

  @Test
  public void testIpIpSpace_0() {
    IpSpace ipSpace = new Ip("0.0.0.0").toIpSpace();
    BDD bdd = ipSpace.accept(_ipSpaceToBdd);
    assertThat(
        bdd,
        equalTo(
            _bddOps.and(
                Arrays.stream(_ipAddrBdd.getBitvec())
                    .map(BDD::not)
                    .collect(ImmutableList.toImmutableList()))));
  }

  @Test
  public void testIpIpSpace_255() {
    IpSpace ipSpace = new Ip("255.255.255.255").toIpSpace();
    BDD bdd = ipSpace.accept(_ipSpaceToBdd);
    assertThat(bdd, equalTo(_bddOps.and(_ipAddrBdd.getBitvec())));
  }

  @Test
  public void testPrefixIpSpace() {
    IpSpace ipSpace = Prefix.parse("255.0.0.0/8").toIpSpace();
    BDD bdd = ipSpace.accept(_ipSpaceToBdd);
    assertThat(bdd, equalTo(_bddOps.and(Arrays.asList(_ipAddrBdd.getBitvec()).subList(0, 8))));
  }

  @Test
  public void testPrefixIpSpace_andMoreSpecific() {
    IpSpace ipSpace1 = Prefix.parse("255.0.0.0/8").toIpSpace();
    IpSpace ipSpace2 = Prefix.parse("255.255.0.0/16").toIpSpace();
    BDD bdd1 = ipSpace1.accept(_ipSpaceToBdd);
    BDD bdd2 = ipSpace2.accept(_ipSpaceToBdd);
    assertThat(_bddOps.and(bdd1, bdd2), equalTo(bdd2));
  }

  @Test
  public void testPrefixIpSpace_andNonOverlapping() {
    IpSpace ipSpace1 = Prefix.parse("0.0.0.0/8").toIpSpace();
    IpSpace ipSpace2 = Prefix.parse("1.0.0.0/8").toIpSpace();
    BDD bdd1 = ipSpace1.accept(_ipSpaceToBdd);
    BDD bdd2 = ipSpace2.accept(_ipSpaceToBdd);
    assertThat(_bddOps.and(bdd1, bdd2), equalTo(_factory.zero()));
  }

  @Test
  public void testPrefixIpSpace_orMoreSpecific() {
    IpSpace ipSpace1 = Prefix.parse("255.0.0.0/8").toIpSpace();
    IpSpace ipSpace2 = Prefix.parse("255.255.0.0/16").toIpSpace();
    BDD bdd1 = ipSpace1.accept(_ipSpaceToBdd);
    BDD bdd2 = ipSpace2.accept(_ipSpaceToBdd);
    assertThat(_bddOps.or(bdd1, bdd2), equalTo(bdd1));
  }

  @Test
  public void testPrefixIpSpace_orNonOverlapping() {
    IpSpace ipSpace1 = Prefix.parse("0.0.0.0/8").toIpSpace();
    IpSpace ipSpace2 = Prefix.parse("1.0.0.0/8").toIpSpace();
    BDD bdd1 = ipSpace1.accept(_ipSpaceToBdd);
    BDD bdd2 = ipSpace2.accept(_ipSpaceToBdd);
    assertThat(
        _bddOps.or(bdd1, bdd2),
        equalTo(
            _bddOps.and(
                Arrays.asList(_ipAddrBdd.getBitvec())
                    .subList(0, 7)
                    .stream()
                    .map(BDD::not)
                    .collect(ImmutableList.toImmutableList()))));
  }

  @Test
  public void testIpWildcard() {
    IpSpace ipSpace = new IpWildcard(new Ip("255.0.255.0"), new Ip("0.255.0.255")).toIpSpace();
    BDD bdd = ipSpace.accept(_ipSpaceToBdd);
    assertThat(
        bdd,
        equalTo(
            _bddOps.and(
                Streams.concat(
                        Arrays.asList(_ipAddrBdd.getBitvec()).subList(0, 8).stream(),
                        Arrays.asList(_ipAddrBdd.getBitvec()).subList(16, 24).stream())
                    .collect(ImmutableList.toImmutableList()))));
  }

  @Test
  public void testIpWildcard_prefix() {
    IpSpace ipWildcardIpSpace =
        new IpWildcard(new Ip("123.0.0.0"), new Ip("0.255.255.255")).toIpSpace();
    IpSpace prefixIpSpace = Prefix.parse("123.0.0.0/8").toIpSpace();
    BDD bdd1 = ipWildcardIpSpace.accept(_ipSpaceToBdd);
    BDD bdd2 = prefixIpSpace.accept(_ipSpaceToBdd);
    assertThat(bdd1, equalTo(bdd2));
  }

  @Test
  public void testAclIpSpace() {
    IpSpace ipSpace1 = new Ip("1.1.1.1").toIpSpace();
    IpSpace ipSpace2 = new Ip("2.2.2.2").toIpSpace();
    IpSpace aclIpSpace =
        AclIpSpace.builder()
            .thenPermitting(ipSpace1, ipSpace2)
            .thenRejecting(ipSpace1, ipSpace2)
            .build();

    BDD bdd1 = ipSpace1.accept(_ipSpaceToBdd);
    BDD bdd2 = ipSpace2.accept(_ipSpaceToBdd);
    BDD aclBDD = aclIpSpace.accept(_ipSpaceToBdd);

    assertFalse(aclBDD.and(bdd1).isZero());
    assertFalse(aclBDD.and(bdd2).isZero());

    assertTrue(aclBDD.not().and(bdd1).isZero());
    assertTrue(aclBDD.not().and(bdd2).isZero());

    IpSpace negIpSpace = aclIpSpace.complement();
    BDD negAclBDD = negIpSpace.accept(_ipSpaceToBdd);
    assertThat(negAclBDD, equalTo(aclBDD.not()));
  }

  @Test
  public void testIpSpaceReference() {
    IpSpace foo = new IpSpaceReference("foo");
    assertThat(foo.accept(_ipSpaceToBdd), equalTo(_namedIpSpaces.get("foo").accept(_ipSpaceToBdd)));
  }

  @Test(expected = IllegalArgumentException.class)
  public void testIpSpaceReference_undefined() {
    IpSpace bar = new IpSpaceReference("bar");
    bar.accept(_ipSpaceToBdd);
  }
}
