package org.batfish.atomicpredicates;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.sf.javabdd.BDD;

/**
 * A trie of BDDs, representing disjoint sets of headerspaces (atomic predicates). It is inspired by
 * DDNF, with two key differences: it stores BDDs instead of ternary bitvectors, and the children of
 * each node are disjoint subsets of the parent (rather than possibly overlapping subsets as in
 * DDNF). The latter property makes this a tree, while DDNFs are DAGs.
 *
 * <p>The DDNF as a whole represents a set of atomic predicates -- a partition of the full
 * headerspace into headerspaces such that for each one, the network does not distinguish between
 * any to headers in that space.
 *
 * <p>Each leaf node is an atomic predicate. Leaf nodes can be split into multiple disjoint subtries
 *
 * <p>The headerspace of each internal node is equal to the union of its children's headerspaces.
 */
public class BDDTrie {
  public final class AtomicPredicate {
    public final int _id;
    public final BDD _bdd;

    AtomicPredicate(int id, BDD bdd) {
      _id = id;
      _bdd = bdd;
    }

    public int getId() {
      return _id;
    }

    public BDD getBDD() {
      return _bdd;
    }
  }

  public static class BDDTrieException extends Exception {
    private static final long serialVersionUID = 0;

    private String _message;
    private List<Integer> _parentPath;
    private Integer _childIndex;

    BDDTrieException(String message, List<Integer> parentPath, Integer childIndex) {
      _message = message;
      _parentPath = ImmutableList.copyOf(parentPath);
      _childIndex = childIndex;
    }
  }

  private class Node {
    // the original space.
    BDD _headerspace;
    // the negation of the original headerspace
    Supplier<BDD> _notHeaderspace;

    final Supplier<Integer> _id;

    /** Invariant: For all i, _children[i]._headerspace is a strict subset of _headerspace. */
    final List<Node> _children;

    /* how many nodes are in this subtree */
    int _size;

    Node(BDD headerspace) {
      this(headerspace, new ArrayList<>());
    }

    Node(BDD headerspace, List<Node> children) {
      _children = children;
      _headerspace = headerspace;
      _notHeaderspace = Suppliers.memoize(_headerspace::not);
      _id = Suppliers.memoize(this::computeAtomicPredicateId);
      _size = 1 + children.stream().map(c -> c._size).reduce(0, (x, y) -> x + y);
      assert _children.stream().noneMatch(child -> child._headerspace.equals(_headerspace));
      assert _children.isEmpty()
          || _children
              .stream()
              .map(node -> node._headerspace)
              .reduce(_headerspace.getFactory().zero(), BDD::or)
              .equals(_headerspace);
    }

    private void computeIds() {
      _id.get();
      for (Node child : _children) {
        child.computeIds();
      }
    }

    private @Nullable Integer computeAtomicPredicateId() {
      if (!_children.isEmpty()) {
        return null;
      }

      Integer id = _atomicPredicates.size();
      _atomicPredicates.add(_headerspace);
      return id;
    }

    Stream<AtomicPredicate> atomicPredicate() {
      return _children.isEmpty()
          ? Stream.of(new AtomicPredicate(_id.get(), _headerspace))
          : Stream.empty();
    }

    Stream<AtomicPredicate> atomicPredicates() {
      return Streams.concat(atomicPredicate(), _children.stream().flatMap(Node::atomicPredicates));
    }

    void checkInvariants(List<Integer> path) throws BDDTrieException {
      BDD childrenHeaderspaces = _headerspace.getFactory().zero();
      for (int i = 0; i < _children.size(); i++) {
        BDD childHeaderspace = _children.get(i)._headerspace;
        if (!childHeaderspace.imp(_headerspace).isOne()) {
          throw new BDDTrieException(
              "child headerspace is not a subset of parent headerspace", path, i);
        }
        if (_headerspace.imp(childHeaderspace).isOne()) {
          throw new BDDTrieException("child headerspace is equal to parent headerspace", path, i);
        }
        if (!childHeaderspace.and(childrenHeaderspaces).isZero()) {
          throw new BDDTrieException(
              "child headerspace intersects with a sibling's headerspace", path, i);
        }
        childrenHeaderspaces = childrenHeaderspaces.or(childHeaderspace);
      }

      if (!_children.isEmpty()
          && !_children
              .stream()
              .map(node -> node._headerspace)
              .reduce(_headerspace.getFactory().zero(), BDD::or)
              .equals(_headerspace)) {
        throw new BDDTrieException("children don't partition parent headerspace", path, -1);
      }

      // recursively check child invariants
      for (int i = 0; i < _children.size(); i++) {
        path.add(i);
        _children.get(i).checkInvariants(path);
        path.remove(path.size() - 1);
      }
    }

    /*
     * @param atoms - store which atoms intersect
     * return how many leaf nodes were created.
     */
    int insert(BDD bdd, List<Node> bddNodes) {
      // invariant: bdd is a subset of _headerspace
      assert !bdd.isZero() && bdd.imp(_headerspace).isOne();

      if (_headerspace.equals(bdd)) {
        bddNodes.add(this);
        return 0;
      }

      if (_children.isEmpty()) {
        // split
        _children.add(new Node(bdd));
        _children.add(new Node(bdd.not().and(_headerspace)));
        // net 1 leaf node was created: 1 leaf became an internal node, and we created 2 new leaves.
        return 1;
      }

      boolean absorbChildren = _children.size() > 10;
      int newNodes = insertIntoChildren(bdd, bddNodes, absorbChildren);
      _size += newNodes;

      reorder();
      rebalance();

      return newNodes;
    }

    void reorder() {
      for (int i = 0; i < _children.size() - 1; i++) {
        Node child1 = _children.get(i);
        Node child2 = _children.get(i + 1);
        if (child1._size < child2._size) {
          _children.set(i, child2);
          _children.set(i + 1, child1);
        }
      }
    }

    void rebalance() {
      // if our largest grandchild is responsible for more than half our size, then move it up
      if (!_children.isEmpty()) {
        Node largestChild = _children.get(0);
        if (!largestChild._children.isEmpty()) {
          Node largestGrandchild = largestChild._children.get(0);
          if (largestGrandchild._size > _size - largestGrandchild._size) {
            // adopt the grandchild
            _children.add(0, largestGrandchild);
            largestChild._size -= largestGrandchild._size;
            largestChild._children.remove(0);
            largestChild._headerspace =
                largestChild._headerspace.and(largestGrandchild._notHeaderspace.get());
            largestChild._notHeaderspace = Suppliers.memoize(() -> largestChild._headerspace.not());
          }
        }
      }
    }

    int insertIntoChildren(BDD bdd, List<Node> bddNodes, boolean adoptChildren) {
      List<Node> adoptedChildren = new LinkedList<>();

      int newNodes = 0;

      for (Node child : _children) {
        if (bdd.equals(child._headerspace)) {
          bddNodes.add(child);
          return newNodes;
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
          if (adoptChildren) {
            adoptedChildren.add(child);
          } else {
            bdd = bdd.and(child._notHeaderspace.get());
          }
          continue;
        } else if (intersection.equals(bdd)) {
          /*
           * bdd is a subset of child._headerspace.
           * since children headerspaces are disjoint, bddChildren must be empty;
           */
          assert adoptedChildren.isEmpty();

          // bdd is a strict subset of child._headerspace
          return newNodes + child.insert(bdd, bddNodes);
        } else {
          /*
           * We know the intersection only intersects this child (because the children are
           * disjoint), so insert directly to it.
           */
          newNodes += child.insert(intersection, bddNodes);

          // bdd intersects child. split.
          BDD bddMinusIntersection = bdd.and(child._notHeaderspace.get());

          assert !bddMinusIntersection.isZero();

          /*
           * The bddMinusIntersection can only intersect children we haven't looked at yet, so continue
           * this scan with the nonIntersection instead of bdd.
           */
          bdd = bddMinusIntersection;

          /*
           * Either bddChildren is empty (i.e. bdd is not a superset of any previous children), or
           * every previous child that is a subset of bdd is also a subset of nonIntersection.
           */
          assert adoptedChildren
              .stream()
              .allMatch(bddChild -> bddChild._headerspace.imp(bddMinusIntersection).isOne());
        }
      }

      // since the children's headerspaces partitions this headerspace, can only reach here if we're
      // adopting. so children must not be empty.
      assert !_children.isEmpty();
      _children.removeAll(adoptedChildren);
      Node node = new Node(bdd, adoptedChildren);
      _children.add(node);
      bddNodes.add(node);
      return newNodes;
    }

    public void atomicPredicates(BDD bdd, List<AtomicPredicate> atomicPredicates) {
      // bdd should be a strict non-empty subset of _headerspace
      assert !bdd.isZero();
      assert bdd.and(this._headerspace).equals(bdd);

      if (bdd.equals(this._headerspace)) {
        atomicPredicates.addAll(atomicPredicates().collect(Collectors.toList()));
        return;
      }

      Stream<AtomicPredicate> result = Stream.empty();
      for (Node child : _children) {
        BDD intersection = bdd.and(child._headerspace);
        if (intersection.isZero()) {
          continue;
        }
        child.atomicPredicates(intersection, atomicPredicates);
        if (intersection.equals(bdd)) {
          // nothing left
          return;
        } else {
          bdd = bdd.and(child._notHeaderspace.get());
        }
      }

      if (!bdd.isZero()) {
        // part of the input bdd is disjoint from the children. so it overlaps this
        atomicPredicates.addAll(atomicPredicate().collect(Collectors.toList()));
      }
      return;
    }
  }

  private List<BDD> _atomicPredicates;

  /** Remember the nodes that correspond to a BDD that has been inserted into the trie. */
  private Map<BDD, List<Node>> _bddNodes;

  private Node _root;

  public BDDTrie(Collection<BDD> bdds) {
    _atomicPredicates = new LinkedList<>();
    _bddNodes = new HashMap<>();
    _root = new Node(bdds.iterator().next().getFactory().one());
    for (BDD bdd : bdds) {
      if (!bdd.isZero()) {
        List<Node> bddAtoms = new LinkedList<>();
        _root.insert(bdd, bddAtoms);
        _bddNodes.put(bdd, bddAtoms);
      }
    }
  }

  public List<BDD> atomicPredicates() {
    // force computation of tree of atomic predicates
    _root.computeIds();
    return ImmutableList.copyOf(_atomicPredicates);
  }

  public List<AtomicPredicate> atomicPredicates(BDD bdd) {
    // TODO: have to handle the fact that Nodes can move, have their headerspace reduced, etc.
    if (false && _bddNodes.containsKey(bdd)) {
      // bdd is one of the input BDDs.
      return _bddNodes
          .get(bdd)
          .stream()
          .flatMap(Node::atomicPredicates)
          .collect(Collectors.toList());
    } else {
      return slowAtomicPredicates(bdd);
    }
  }

  List<AtomicPredicate> slowAtomicPredicates(BDD bdd) {
    List<AtomicPredicate> predicates = new LinkedList<>();
    _root.atomicPredicates(bdd, predicates);
    return predicates;
  }

  public void checkInvariants() throws BDDTrieException {
    _root.checkInvariants(new ArrayList<>());
  }
}
