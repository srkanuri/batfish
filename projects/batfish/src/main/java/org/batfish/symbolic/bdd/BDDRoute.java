package org.batfish.symbolic.bdd;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Streams;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDFactory;
import net.sf.javabdd.BDDPairing;
import net.sf.javabdd.JFactory;
import org.batfish.common.BatfishException;
import org.batfish.common.util.CommonUtil;
import org.batfish.datamodel.Ip;
import org.batfish.datamodel.Prefix;
import org.batfish.symbolic.CommunityVar;
import org.batfish.symbolic.CommunityVar.Type;
import org.batfish.symbolic.IDeepCopy;
import org.batfish.symbolic.OspfType;
import org.batfish.symbolic.Protocol;

/**
 * A collection of attributes describing an advertisement, represented using BDDs
 *
 * @author Ryan Beckett
 */
public class BDDRoute implements IDeepCopy<BDDRoute> {

  static final BDDFactory factory;

  static final List<Protocol> ALL_PROTOS;

  private static final List<OspfType> allMetricTypes;

  private static final BDDPairing pairing;

  static final int METRIC_LENGTH = 32;
  static final int MED_LENGTH = 32;
  static final int ADMIN_DIST_LENGTH = 32;
  static final int LOCAL_PREF_LENGTH = 32;
  static final int PREFIX_LENGTH_LENGTH = 5;
  static final int PREFIX_LENGTH = 32;

  private int _hcode = 0;

  static {
    allMetricTypes = new ArrayList<>();
    allMetricTypes.add(OspfType.O);
    allMetricTypes.add(OspfType.OIA);
    allMetricTypes.add(OspfType.E1);
    allMetricTypes.add(OspfType.E2);

    ALL_PROTOS = ImmutableList.of(Protocol.CONNECTED, Protocol.STATIC, Protocol.OSPF, Protocol.BGP);

    factory = JFactory.init(100000, 10000);
    // factory.disableReorder();
    factory.setCacheRatio(64);
    /*
    try {
      // Disables printing
      CallbackHandler handler = new CallbackHandler();
      Method m = handler.getClass().getDeclaredMethod("handle", (Class<?>[]) null);
      factory.registerGCCallback(handler, m);
      factory.registerResizeCallback(handler, m);
      factory.registerReorderCallback(handler, m);
    } catch (NoSuchMethodException e) {
      e.printStackTrace();
    }
    */
    pairing = factory.makePair();
  }

  private BDDRoute(Builder builder) {
    _adminDist = builder._adminDist;
    _bitNames = ImmutableMap.copyOf(builder._bitNames);
    _communities = ImmutableSortedMap.copyOf(builder._communities);
    _localPref = builder._localPref;
    _med = builder._med;
    _metric = builder._metric;
    _ospfMetric = builder._ospfMetric;
    _prefix = builder._prefix;
    _prefixLength = builder._prefixLength;
    _protocolHistory = builder._protocolHistory;
  }

  @Override
  public BDDRoute deepCopy() {
    // immutable, so no need for deep copies anymore
    return this;
  }

  public static class Builder {

    private BDDInteger _adminDist;

    private Map<Integer, String> _bitNames;

    private SortedMap<CommunityVar, BDD> _communities;

    private BDDInteger _localPref;

    private BDDInteger _med;

    private BDDInteger _metric;

    private BDDDomain<OspfType> _ospfMetric;

    private BDDInteger _prefix;

    private BDDInteger _prefixLength;

    private BDDDomain<Protocol> _protocolHistory;

    BDDRoute build() {
      return new BDDRoute(this);
    }

    public Builder setAdminDist(BDDInteger adminDist) {
      _adminDist = adminDist;
      return this;
    }

    public Builder setBitNames(Map<Integer, String> bitNames) {
      _bitNames = bitNames;
      return this;
    }

    public Builder setCommunities(SortedMap<CommunityVar, BDD> communities) {
      _communities = ImmutableSortedMap.copyOf(communities);
      return this;
    }

    public Builder updateCommunities(SortedMap<CommunityVar, BDD> communities) {
      _communities =
          Streams.concat(
                  _communities
                      .entrySet()
                      .stream()
                      .filter(entry -> !communities.containsKey(entry.getKey())),
                  communities.entrySet().stream())
              .collect(
                  ImmutableSortedMap.toImmutableSortedMap(
                      Comparator.naturalOrder(), Entry::getKey, Entry::getValue));
      return this;
    }

    public Builder setLocalPref(BDDInteger localPref) {
      _localPref = localPref;
      return this;
    }

    public Builder setMed(BDDInteger med) {
      _med = med;
      return this;
    }

    public Builder setMetric(BDDInteger metric) {
      _metric = metric;
      return this;
    }

    public Builder setOspfMetric(BDDDomain<OspfType> ospfMetric) {
      _ospfMetric = ospfMetric;
      return this;
    }

    public Builder setPrefix(BDDInteger prefix) {
      _prefix = prefix;
      return this;
    }

    public Builder setPrefixLength(BDDInteger prefixLength) {
      _prefixLength = prefixLength;
      return this;
    }

    public Builder setProtocolHistory(BDDDomain<Protocol> protocolHistory) {
      _protocolHistory = protocolHistory;
      return this;
    }
  }

  private final BDDInteger _adminDist;

  private final Map<Integer, String> _bitNames;

  private final SortedMap<CommunityVar, BDD> _communities;

  private final BDDInteger _localPref;

  private final BDDInteger _med;

  private final BDDInteger _metric;

  private final BDDDomain<OspfType> _ospfMetric;

  private final BDDInteger _prefix;

  private final BDDInteger _prefixLength;

  private final BDDDomain<Protocol> _protocolHistory;

  /*
   * Creates a collection of BDD variables representing the
   * various attributes of a control plane advertisement.
   */
  public static Builder builder(Set<CommunityVar> comms) {
    int numVars = factory.varNum();
    int numNeeded = 32 * 5 + 5 + comms.size() + 4;
    if (numVars < numNeeded) {
      factory.setVarNum(numNeeded);
    }
    ImmutableMap.Builder<Integer, String> bitNamesBuilder = ImmutableMap.builder();
    Builder routeBuilder = builder();

    int idx = 0;
    BDDDomain<Protocol> protocolHistory = new BDDDomain<>(factory, ALL_PROTOS, idx);
    int len = protocolHistory.getInteger().getBitvec().size();
    routeBuilder.setProtocolHistory(protocolHistory);
    addBitNames(bitNamesBuilder, "proto", len, idx, false);
    idx += len;

    // Initialize integer values
    routeBuilder.setMetric(BDDInteger.makeFromIndex(factory, METRIC_LENGTH, idx, false));
    addBitNames(bitNamesBuilder, "metric", METRIC_LENGTH, idx, false);
    idx += METRIC_LENGTH;

    routeBuilder.setMed(BDDInteger.makeFromIndex(factory, MED_LENGTH, idx, false));
    addBitNames(bitNamesBuilder, "med", MED_LENGTH, idx, false);
    idx += MED_LENGTH;

    routeBuilder.setAdminDist(BDDInteger.makeFromIndex(factory, ADMIN_DIST_LENGTH, idx, false));
    addBitNames(bitNamesBuilder, "ad", ADMIN_DIST_LENGTH, idx, false);
    idx += ADMIN_DIST_LENGTH;

    routeBuilder.setLocalPref(BDDInteger.makeFromIndex(factory, LOCAL_PREF_LENGTH, idx, false));
    addBitNames(bitNamesBuilder, "lp", LOCAL_PREF_LENGTH, idx, false);
    idx += LOCAL_PREF_LENGTH;

    routeBuilder.setPrefixLength(
        BDDInteger.makeFromIndex(factory, PREFIX_LENGTH_LENGTH, idx, true));
    addBitNames(bitNamesBuilder, "pfxLen", PREFIX_LENGTH_LENGTH, idx, true);
    idx += PREFIX_LENGTH_LENGTH;

    routeBuilder.setPrefix(BDDInteger.makeFromIndex(factory, PREFIX_LENGTH, idx, true));
    addBitNames(bitNamesBuilder, "pfx", PREFIX_LENGTH, idx, true);
    idx += PREFIX_LENGTH;

    // Initialize communities
    TreeMap<CommunityVar, BDD> communities = new TreeMap<>();
    for (CommunityVar comm : comms) {
      if (comm.getType() != Type.REGEX) {
        communities.put(comm, factory.ithVar(idx));
        bitNamesBuilder.put(idx, comm.getValue());
        idx++;
      }
    }
    routeBuilder.setCommunities(communities);

    // Initialize OSPF type
    BDDDomain<OspfType> ospfMetric = new BDDDomain<>(factory, allMetricTypes, idx);
    len = ospfMetric.getInteger().getBitvec().size();
    routeBuilder.setOspfMetric(ospfMetric);

    addBitNames(bitNamesBuilder, "ospfMetric", len, idx, false);
    routeBuilder.setBitNames(bitNamesBuilder.build());
    return routeBuilder;
  }

  private static Builder builder() {
    return new Builder();
  }

  /*
   * Create a BDDRecord from another. Because BDDs are immutable,
   * there is no need for a deep copy.
   */
  public Builder toBuilder() {
    return new Builder()
        .setAdminDist(_adminDist)
        .setBitNames(_bitNames)
        .setCommunities(_communities)
        .setLocalPref(_localPref)
        .setMed(_med)
        .setMetric(_metric)
        .setOspfMetric(_ospfMetric)
        .setPrefix(_prefix)
        .setPrefixLength(_prefixLength)
        .setProtocolHistory(_protocolHistory);
  }

  /*
   * Helper function that builds a map from BDD variable index
   * to some more meaningful name. Helpful for debugging.
   */
  private static void addBitNames(
      ImmutableMap.Builder<Integer, String> bitNamesBuilder,
      String s,
      int length,
      int index,
      boolean reverse) {
    for (int i = index; i < index + length; i++) {
      if (reverse) {
        bitNamesBuilder.put(i, s + (length - 1 - (i - index)));
      } else {
        bitNamesBuilder.put(i, s + (i - index + 1));
      }
    }
  }

  /*
   * Converts a BDD to the graphviz DOT format for debugging.
   */
  String dot(BDD bdd) {
    StringBuilder sb = new StringBuilder();
    sb.append("digraph G {\n");
    sb.append("0 [shape=box, label=\"0\", style=filled, shape=box, height=0.3, width=0.3];\n");
    sb.append("1 [shape=box, label=\"1\", style=filled, shape=box, height=0.3, width=0.3];\n");
    dotRec(sb, bdd, new HashSet<>());
    sb.append("}");
    return sb.toString();
  }

  /*
   * Creates a unique id for a bdd node when generating
   * a DOT file for graphviz
   */
  private Integer dotId(BDD bdd) {
    if (bdd.isZero()) {
      return 0;
    }
    if (bdd.isOne()) {
      return 1;
    }
    return bdd.hashCode() + 2;
  }

  /*
   * Recursively builds each of the intermediate BDD nodes in the
   * graphviz DOT format.
   */
  private void dotRec(StringBuilder sb, BDD bdd, Set<BDD> visited) {
    if (bdd.isOne() || bdd.isZero() || visited.contains(bdd)) {
      return;
    }
    int val = dotId(bdd);
    int valLow = dotId(bdd.low());
    int valHigh = dotId(bdd.high());
    String name = _bitNames.get(bdd.var());
    sb.append(val).append(" [label=\"").append(name).append("\"]\n");
    sb.append(val).append(" -> ").append(valLow).append("[style=dotted]\n");
    sb.append(val).append(" -> ").append(valHigh).append("[style=filled]\n");
    visited.add(bdd);
    dotRec(sb, bdd.low(), visited);
    dotRec(sb, bdd.high(), visited);
  }

  public BDDInteger getAdminDist() {
    return _adminDist;
  }

  public SortedMap<CommunityVar, BDD> getCommunities() {
    return _communities;
  }

  public BDDInteger getLocalPref() {
    return _localPref;
  }

  public BDDInteger getMed() {
    return _med;
  }

  public BDDInteger getMetric() {
    return _metric;
  }

  public BDDDomain<OspfType> getOspfMetric() {
    return _ospfMetric;
  }

  public BDDInteger getPrefix() {
    return _prefix;
  }

  public BDDInteger getPrefixLength() {
    return _prefixLength;
  }

  public BDDDomain<Protocol> getProtocolHistory() {
    return _protocolHistory;
  }

  @Override
  public int hashCode() {
    if (_hcode == 0) {
      int result = _adminDist != null ? _adminDist.hashCode() : 0;
      result = 31 * result + (_metric != null ? _metric.hashCode() : 0);
      result = 31 * result + (_ospfMetric != null ? _ospfMetric.hashCode() : 0);
      result = 31 * result + (_med != null ? _med.hashCode() : 0);
      result = 31 * result + (_localPref != null ? _localPref.hashCode() : 0);
      result = 31 * result + (_communities != null ? _communities.hashCode() : 0);
      _hcode = result;
    }
    return _hcode;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof BDDRoute)) {
      return false;
    }
    BDDRoute other = (BDDRoute) o;

    return MemoEquals.memoEquals(this, other, BDDRoute::equalsImpl);
  }

  private static boolean equalsImpl(BDDRoute r1, BDDRoute r2) {
    return Objects.equals(r1._metric, r2._metric)
        && Objects.equals(r1._ospfMetric, r2._ospfMetric)
        && Objects.equals(r1._localPref, r2._localPref)
        && Objects.equals(r1._communities, r2._communities)
        && Objects.equals(r1._med, r2._med)
        && Objects.equals(r1._adminDist, r2._adminDist);
  }

  /*
   * Take the point-wise disjunction of two BDDRecords
   */
  public BDDRoute or(BDDRoute other) {
    BDD[] adminDist = new BDD[32];
    BDD[] localPref = new BDD[32];
    BDD[] med = new BDD[32];
    BDD[] metric = new BDD[32];
    BDD[] ospfMet = new BDD[getOspfMetric().getInteger().getBitvec().size()];

    for (int i = 0; i < 32; i++) {
      adminDist[i] = _adminDist.getBitvec().get(i).or(other._adminDist.getBitvec().get(i));
      localPref[i] = _localPref.getBitvec().get(i).or(other._localPref.getBitvec().get(i));
      med[i] = _med.getBitvec().get(i).or(other._med.getBitvec().get(i));
      metric[i] = _metric.getBitvec().get(i).or(other._metric.getBitvec().get(i));
    }
    for (int i = 0; i < ospfMet.length; i++) {
      ospfMet[i] =
          _ospfMetric
              .getInteger()
              .getBitvec()
              .get(i)
              .or(other._ospfMetric.getInteger().getBitvec().get(i));
    }

    return toBuilder()
        .setAdminDist(new BDDInteger(_adminDist.getFactory(), adminDist))
        .setCommunities(
            CommonUtil.toImmutableSortedMap(
                _communities,
                Entry::getKey,
                entry -> entry.getValue().or(other.getCommunities().get(entry.getKey()))))
        .setBitNames(_bitNames)
        .setLocalPref(new BDDInteger(_localPref.getFactory(), localPref))
        .setMed(new BDDInteger(_med.getFactory(), med))
        .setMetric(new BDDInteger(_metric.getFactory(), metric))
        .setOspfMetric(
            new BDDDomain<>(
                _ospfMetric.getValues(),
                new BDDInteger(_ospfMetric.getInteger().getFactory(), ospfMet)))
        .build();
  }

  public BDDRoute restrict(Prefix pfx) {
    int len = pfx.getPrefixLength();
    long bits = pfx.getStartIp().asLong();
    int[] vars = new int[len];
    BDD[] vals = new BDD[len];
    // NOTE: do not create a new pairing each time
    // JavaBDD will start to memory leak
    pairing.reset();
    for (int i = 0; i < len; i++) {
      int var = _prefix.getBitvec().get(i).var(); // prefixIndex + i;
      BDD subst = Ip.getBitAtPosition(bits, i) ? factory.one() : factory.zero();
      vars[i] = var;
      vals[i] = subst;
    }
    pairing.set(vars, vals);

    BDD[] adminDist = new BDD[32];
    BDD[] localPref = new BDD[32];
    BDD[] med = new BDD[32];
    BDD[] metric = new BDD[32];
    BDD[] ospfMet = new BDD[_ospfMetric.getInteger().getBitvec().size()];

    for (int i = 0; i < 32; i++) {
      adminDist[i] = _adminDist.getBitvec().get(i).veccompose(pairing);
      localPref[i] = _localPref.getBitvec().get(i).veccompose(pairing);
      med[i] = _med.getBitvec().get(i).veccompose(pairing);
      metric[i] = _metric.getBitvec().get(i).veccompose(pairing);
    }
    for (int i = 0; i < ospfMet.length; i++) {
      ospfMet[i] = _ospfMetric.getInteger().getBitvec().get(i).veccompose(pairing);
    }
    return toBuilder()
        .setAdminDist(new BDDInteger(_adminDist.getFactory(), adminDist))
        .setCommunities(
            CommonUtil.toImmutableSortedMap(
                _communities, Entry::getKey, entry -> entry.getValue().veccompose(pairing)))
        .setLocalPref(new BDDInteger(_localPref.getFactory(), localPref))
        .setMed(new BDDInteger(_med.getFactory(), med))
        .setMetric(new BDDInteger(_metric.getFactory(), metric))
        .setOspfMetric(
            new BDDDomain<>(
                _ospfMetric.getValues(),
                new BDDInteger(_ospfMetric.getInteger().getFactory(), ospfMet)))
        .build();
  }

  public BDDRoute restrict(List<Prefix> prefixes) {
    if (prefixes.isEmpty()) {
      throw new BatfishException("Empty prefix list in BDDRecord restrict");
    }
    BDDRoute r = restrict(prefixes.get(0));
    for (int i = 1; i < prefixes.size(); i++) {
      Prefix p = prefixes.get(i);
      BDDRoute x = restrict(p);
      r = r.or(x);
    }
    return r;
  }
}
