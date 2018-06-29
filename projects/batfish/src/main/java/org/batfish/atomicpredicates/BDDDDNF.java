package org.batfish.atomicpredicates;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.sf.javabdd.BDD;

/**
 * An adaptation if DDNF using BDDs to represent headerspaces rather than ternary bitvectors. The
 * DDNF is a DAG where each node has an associated headerspace, and an edge between two nodes exists
 * when the target's headerspace is a subset of the source's headerspace (and no intermediate
 * headerspace exist in the DAG.
 *
 * <p>The DDNF as a whole represents a set of atomic predicates -- a partition of the full
 * headerspace into headerspaces such that for each one, the network does not distinguish between
 * any to headers in that space.
 *
 * <p>Each node represents the headerspace described by its BDD minus the headerspaces of its
 * children.
 */
public class BDDDDNF {
  public static class BDDDDNFException extends Exception {
    private String _message;
    private List<Integer> _parentPath;
    private Integer _childIndex;

    BDDDDNFException(String message, List<Integer> parentPath, Integer childIndex) {
      _message = message;
      _parentPath = ImmutableList.copyOf(parentPath);
      _childIndex = childIndex;
    }
  }

  private static class Node {
    final BDD _headerspace;
    final Supplier<BDD> _notHeaderspace;

    /** Invariant: For all i, _children[i]._headerspace is a strict subset of _headerspace. */
    final List<Node> _children;

    Node(BDD headerspace) {
      this(headerspace, new ArrayList<>());
    }

    Node(BDD headerspace, List<Node> children) {
      _headerspace = headerspace;
      _notHeaderspace = Suppliers.memoize(_headerspace::not);
      _children = children;
    }

    Stream<BDD> atomicPredicate() {
      BDD result = _headerspace;
      for (Node child : _children) {
        result = result.and(child._notHeaderspace.get());
      }
      return result.isZero() ? Stream.empty() : Stream.of(result);
    }

    Stream<BDD> atomicPredicates() {
      return Streams.concat(atomicPredicate(), _children.stream().flatMap(Node::atomicPredicates));
    }

    void checkInvariants(List<Integer> path) throws BDDDDNFException {
      BDD childrenHeaderspaces = _headerspace.getFactory().zero();
      for (int i = 0; i < _children.size(); i++) {
        BDD childHeaderspace = _children.get(i)._headerspace;
        if (!childHeaderspace.imp(_headerspace).isOne()) {
          throw new BDDDDNFException(
              "child headerspace is not a subset of parent headerspace", path, i);
        }
        if (_headerspace.imp(childHeaderspace).isOne()) {
          throw new BDDDDNFException("child headerspace is equal to parent headerspace", path, i);
        }
        if (!childHeaderspace.and(childrenHeaderspaces).isZero()) {
          throw new BDDDDNFException(
              "child headerspace intersects with a sibling's headerspace", path, i);
        }
        childrenHeaderspaces = childrenHeaderspaces.or(childHeaderspace);
      }
    }

    void insert(BDD bdd) {
      // invariant: bdd is a subset of _headerspace
      assert !bdd.isZero() && bdd.imp(_headerspace).isOne();

      if (_headerspace.equals(bdd)) {
        return;
      }

      // initialize lazily
      List<Node> bddChildren = null;

      for (Node child : _children) {
        if (bdd.equals(child._headerspace)) {
          return;
        }
        BDD intersection = bdd.and(child._headerspace);
        if (intersection.isZero()) {
          continue;
        } else if (intersection.equals(child._headerspace)) {
          /*
           * child._headerspace is a subset of bdd.
           * We want to insert a new node between this and child, but bdd might not be the right
           * headerspace (it may intersect other _children). So we have to wait until we've checked
           * all the other children.
           */
          if (bddChildren == null) {
            bddChildren = new ArrayList<>();
          }
          bddChildren.add(child);
          continue;
        } else if (intersection.equals(bdd)) {
          /*
           * bdd is a subset of child._headerspace.
           * since children headerspaces are disjoint, bddChildren must be empty;
           */
          assert bddChildren == null;

          // bdd is a strict subset of child._headerspace
          child.insert(bdd);
          return;
        } else {
          // bdd intersects child. split.
          BDD bddMinusIntersection = bdd.and(child._notHeaderspace.get());

          /*
           * We know the intersection only intersects this child (because the children are
           * disjoint), so insert directly to it.
           */
          child.insert(intersection);

          /*
           * The nonIntersection can only intersect children we haven't looked at yet, so continue
           * this scan with the nonIntersection instead of bdd.
           */
          bdd = bddMinusIntersection;

          /*
           * Either bddChildren is empty (i.e. bdd is not a superset of any previous children), or
           * every previous child that is a subset of bdd is also a subset of nonIntersection.
           */
          assert bddChildren == null
              || bddChildren
                  .stream()
                  .allMatch(bddChild -> bddChild._headerspace.imp(bddMinusIntersection).isOne());
        }
      }

      // bdd does not intersect any child headerspace. add a new child for it.
      if (bddChildren != null) {
        _children.removeAll(bddChildren);
        _children.add(new Node(bdd, bddChildren));
      } else {
        _children.add(new Node(bdd));
      }
    }
  }

  private Node _root;

  public BDDDDNF(List<BDD> bdds) {
    _root = new Node(bdds.get(0).getFactory().one());
    for (BDD bdd : bdds) {
      if (!bdd.isZero()) {
        _root.insert(bdd);
      }
    }
  }

  public List<BDD> atomicPredicates() {
    return _root.atomicPredicates().collect(ImmutableList.toImmutableList());
  }

  public void checkInvariants() throws BDDDDNFException {
    _root.checkInvariants(new ArrayList<>());
  }
}
