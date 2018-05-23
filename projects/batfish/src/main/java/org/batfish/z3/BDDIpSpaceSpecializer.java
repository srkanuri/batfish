package org.batfish.z3;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.sf.javabdd.BDD;
import org.batfish.datamodel.EmptyIpSpace;
import org.batfish.datamodel.Ip;
import org.batfish.datamodel.IpSpace;
import org.batfish.datamodel.IpWildcard;
import org.batfish.datamodel.UniverseIpSpace;
import org.batfish.symbolic.bdd.IpSpaceToBDD;

public final class BDDIpSpaceSpecializer extends IpSpaceSpecializer {
  private final BDD _bdd;
  private final IpSpaceToBDD _ipSpaceToBDD;
  private final Map<String, IpSpace> _namedIpSpaces;

  public BDDIpSpaceSpecializer(
      BDD bdd, IpSpaceToBDD ipSpaceToBDD, Map<String, IpSpace> namedIpSpaces) {
    super(namedIpSpaces);
    _bdd = bdd;
    _ipSpaceToBDD = ipSpaceToBDD;
    _namedIpSpaces = ImmutableMap.copyOf(namedIpSpaces);
  }

  @Override
  protected Optional<IpSpaceSpecializer> restrictSpecializerToBlacklist(Set<IpWildcard> blacklist) {
    BDD refinedBDD =
        blacklist.stream().map(_ipSpaceToBDD::toBDD).map(BDD::not).reduce(_bdd, BDD::and);
    return refinedBDD.isZero()
        ? Optional.empty()
        : Optional.of(new BDDIpSpaceSpecializer(refinedBDD, _ipSpaceToBDD, _namedIpSpaces));
  }

  @Override
  protected IpSpace specialize(Ip ip) {
    return emptyIfNoIntersection(ip.toIpSpace());
  }

  @Override
  protected IpSpace specialize(IpWildcard ipWildcard) {
    return emptyIfNoIntersection(ipWildcard.toIpSpace());
  }

  /**
   * This method does the interesting work. Replace the input ipSpace with an equivalent one for
   * _bdd. If ipSpace and _bdd are disjoint, return EmptyIpSpace. If ipSpace is a superset of _bdd,
   * return UniverseIpSpace. Otherwise, return ipSpace.
   */
  private IpSpace emptyIfNoIntersection(IpSpace ipSpace) {
    BDD ipSpaceBDD = ipSpace.accept(_ipSpaceToBDD);

    if (ipSpaceBDD.and(_bdd).isZero()) {
      // disjoint ip spaces
      return EmptyIpSpace.INSTANCE;
    }

    if (ipSpaceBDD.not().and(_bdd).isZero()) {
      // _bdd's ip space is a subset of ipSpace.
      return UniverseIpSpace.INSTANCE;
    }

    return ipSpace;
  }
}
