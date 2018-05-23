package org.batfish.z3;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import java.util.Comparator;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeMap;
import net.sf.javabdd.BDD;
import org.batfish.datamodel.AclIpSpace;
import org.batfish.datamodel.AclIpSpaceLine;
import org.batfish.datamodel.EmptyIpSpace;
import org.batfish.datamodel.IpIpSpace;
import org.batfish.datamodel.IpSpace;
import org.batfish.datamodel.IpSpaceReference;
import org.batfish.datamodel.IpWildcard;
import org.batfish.datamodel.IpWildcardIpSpace;
import org.batfish.datamodel.IpWildcardSetIpSpace;
import org.batfish.datamodel.PrefixIpSpace;
import org.batfish.datamodel.UniverseIpSpace;
import org.batfish.datamodel.visitors.GenericIpSpaceVisitor;
import org.batfish.symbolic.bdd.IpSpaceToBDD;

public class BDDIpSpaceSpecializer implements GenericIpSpaceVisitor<IpSpace> {
  private final BDD _bdd;
  private final IpSpaceToBDD _ipSpaceToBDD;
  private final Map<String, IpSpace> _namedIpSpaces;
  private final Map<String, IpSpace> _specializedNamedIpSpaces;

  public BDDIpSpaceSpecializer(
      BDD bdd, IpSpaceToBDD ipSpaceToBDD, Map<String, IpSpace> namedIpSpaces) {
    _bdd = bdd;
    _ipSpaceToBDD = ipSpaceToBDD;

    // TODO specialize namedIpSpaces.
    _namedIpSpaces = ImmutableMap.copyOf(namedIpSpaces);
    _specializedNamedIpSpaces = new TreeMap<>();
  }

  @Override
  public IpSpace castToGenericIpSpaceVisitorReturnType(Object o) {
    return (IpSpace) o;
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

  private AclIpSpaceLine specialize(AclIpSpaceLine line) {
    IpSpace specializedIpSpace = line.getIpSpace().accept(this);
    return AclIpSpaceLine.builder()
        .setAction(line.getAction())
        .setIpSpace(specializedIpSpace)
        .build();
  }

  private SortedSet<IpWildcard> specialize(SortedSet<IpWildcard> wildcards) {
    return wildcards
        .stream()
        .map(IpWildcard::toIpSpace)
        .map(this::emptyIfNoIntersection)
        .filter(IpWildcardIpSpace.class::isInstance)
        .map(IpWildcardIpSpace.class::cast)
        .map(IpWildcardIpSpace::getIpWildcard)
        .collect(ImmutableSortedSet.toImmutableSortedSet(Comparator.naturalOrder()));
  }

  @Override
  public IpSpace visitAclIpSpace(AclIpSpace aclIpSpace) {
    return AclIpSpace.builder()
        .setLines(
            aclIpSpace
                .getLines()
                .stream()
                .map(this::specialize)
                .collect(ImmutableList.toImmutableList()))
        .build();
  }

  @Override
  public IpSpace visitEmptyIpSpace(EmptyIpSpace emptyIpSpace) {
    return emptyIpSpace;
  }

  @Override
  public IpSpace visitIpIpSpace(IpIpSpace ipIpSpace) {
    return emptyIfNoIntersection(ipIpSpace);
  }

  @Override
  public IpSpace visitIpSpaceReference(IpSpaceReference ipSpaceReference) {
    String name = ipSpaceReference.getName();
    return _specializedNamedIpSpaces.computeIfAbsent(
        name, k -> _namedIpSpaces.get(name).accept(this));
  }

  @Override
  public IpSpace visitIpWildcardIpSpace(IpWildcardIpSpace ipWildcardIpSpace) {
    return emptyIfNoIntersection(ipWildcardIpSpace);
  }

  @Override
  public IpSpace visitIpWildcardSetIpSpace(IpWildcardSetIpSpace ipWildcardSetIpSpace) {
    SortedSet<IpWildcard> whitelist = specialize(ipWildcardSetIpSpace.getWhitelist());
    SortedSet<IpWildcard> blacklist = specialize(ipWildcardSetIpSpace.getBlacklist());
    return IpWildcardSetIpSpace.builder().including(whitelist).excluding(blacklist).build();
  }

  @Override
  public IpSpace visitPrefixIpSpace(PrefixIpSpace prefixIpSpace) {
    return emptyIfNoIntersection(prefixIpSpace);
  }

  @Override
  public IpSpace visitUniverseIpSpace(UniverseIpSpace universeIpSpace) {
    return universeIpSpace;
  }
}
