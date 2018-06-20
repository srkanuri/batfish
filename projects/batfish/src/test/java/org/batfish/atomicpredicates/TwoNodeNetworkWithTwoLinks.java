package org.batfish.atomicpredicates;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import java.io.IOException;
import java.util.SortedMap;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.Configuration.Builder;
import org.batfish.datamodel.ConfigurationFormat;
import org.batfish.datamodel.Interface;
import org.batfish.datamodel.InterfaceAddress;
import org.batfish.datamodel.NetworkFactory;
import org.batfish.datamodel.Prefix;
import org.batfish.datamodel.StaticRoute;
import org.batfish.datamodel.Vrf;
import org.batfish.main.Batfish;
import org.batfish.main.BatfishTestUtils;
import org.junit.rules.TemporaryFolder;

/** A test network with two nodes and two static routes from one to the other. */
class TwoNodeNetworkWithTwoLinks {
  public static final Prefix DST_PREFIX_1 = Prefix.parse("1.1.0.0/32");
  public static final Prefix DST_PREFIX_2 = Prefix.parse("2.1.0.0/32");
  public static final Prefix LINK_1_NETWORK = Prefix.parse("1.0.0.0/31");
  public static final Prefix LINK_2_NETWORK = Prefix.parse("2.0.0.0/31");

  final Batfish _batfish;
  final SortedMap<String, Configuration> _configs;
  final Interface _dstIface1;
  final Interface _dstIface2;
  final Configuration _dstNode;
  final Configuration _srcNode;
  final Interface _link1Src;
  final Interface _link2Src;
  final Interface _link1Dst;
  final Interface _link2Dst;

  TwoNodeNetworkWithTwoLinks() throws IOException {
    NetworkFactory nf = new NetworkFactory();
    Builder cb = nf.configurationBuilder().setConfigurationFormat(ConfigurationFormat.CISCO_IOS);
    Interface.Builder ib = nf.interfaceBuilder().setActive(true).setBandwidth(1E9d);
    Vrf.Builder vb = nf.vrfBuilder().setName(Configuration.DEFAULT_VRF_NAME);

    _srcNode = cb.build();
    _dstNode = cb.build();
    Vrf srcVrf = vb.setOwner(_srcNode).build();
    Vrf dstVrf = vb.setOwner(_dstNode).build();

    // first link
    _link1Src =
        ib.setOwner(_srcNode)
            .setVrf(srcVrf)
            .setAddress(
                new InterfaceAddress(LINK_1_NETWORK.getStartIp(), LINK_1_NETWORK.getPrefixLength()))
            .build();
    _link1Dst =
        ib.setOwner(_dstNode)
            .setVrf(dstVrf)
            .setAddress(
                new InterfaceAddress(LINK_1_NETWORK.getEndIp(), LINK_1_NETWORK.getPrefixLength()))
            .setSourceNats(ImmutableList.of())
            .build();

    // second link
    _link2Src =
        ib.setOwner(_srcNode)
            .setVrf(srcVrf)
            .setAddress(
                new InterfaceAddress(LINK_2_NETWORK.getStartIp(), LINK_2_NETWORK.getPrefixLength()))
            .build();
    _link2Dst =
        ib.setOwner(_dstNode)
            .setVrf(dstVrf)
            .setAddress(
                new InterfaceAddress(LINK_2_NETWORK.getEndIp(), LINK_2_NETWORK.getPrefixLength()))
            .setSourceNats(ImmutableList.of())
            .build();

    // destination for the first link
    _dstIface1 =
        ib.setOwner(_dstNode)
            .setVrf(dstVrf)
            .setAddress(
                new InterfaceAddress(DST_PREFIX_1.getStartIp(), DST_PREFIX_1.getPrefixLength()))
            .build();

    // destination for the second link
    _dstIface2 =
        ib.setOwner(_dstNode)
            .setVrf(dstVrf)
            .setAddress(
                new InterfaceAddress(DST_PREFIX_2.getStartIp(), DST_PREFIX_2.getPrefixLength()))
            .build();

    StaticRoute.Builder bld = StaticRoute.builder();
    srcVrf.setStaticRoutes(
        ImmutableSortedSet.of(
            bld.setNetwork(DST_PREFIX_1).setNextHopIp(LINK_1_NETWORK.getEndIp()).build(),
            bld.setNetwork(DST_PREFIX_2).setNextHopIp(LINK_2_NETWORK.getEndIp()).build()));

    _configs = ImmutableSortedMap.of(_srcNode.getName(), _srcNode, _dstNode.getName(), _dstNode);
    TemporaryFolder temp = new TemporaryFolder();
    temp.create();
    _batfish = BatfishTestUtils.getBatfish(_configs, temp);
  }
}
