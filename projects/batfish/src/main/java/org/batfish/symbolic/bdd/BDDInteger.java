package org.batfish.symbolic.bdd;

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Objects;
import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDException;
import net.sf.javabdd.BDDFactory;

public class BDDInteger {

  private final BDDFactory _factory;

  private final ImmutableList<BDD> _bitvec;

  private Integer _hashCode;

  BDDInteger(BDDFactory factory, List<BDD> bitvec) {
    _factory = factory;
    _bitvec = ImmutableList.copyOf(bitvec);
  }

  BDDInteger(BDDFactory factory, BDD[] bitvec) {
    _factory = factory;
    _bitvec = ImmutableList.copyOf(bitvec);
  }

  /*
   * Create an integer, and initialize its values as "don't care"
   * This requires knowing the start index variables the bitvector
   * will use.
   */
  public static BDDInteger makeFromIndex(
      BDDFactory factory, int length, int start, boolean reverse) {
    BDD[] bitVec = makeBitVecFromIndex(factory, length, start, reverse);
    return new BDDInteger(factory, ImmutableList.copyOf(bitVec));
  }

  private static BDD[] makeBitVecFromIndex(
      BDDFactory factory, int length, int start, boolean reverse) {
    BDD[] bitVec = new BDD[length];
    for (int i = 0; i < length; i++) {
      int idx;
      if (reverse) {
        idx = start + length - i - 1;
      } else {
        idx = start + i;
      }
      bitVec[i] = factory.ithVar(idx);
    }
    return bitVec;
  }

  /*
   * Create an integer and initialize it to a concrete value
   */
  public static BDDInteger makeFromValue(BDDFactory factory, int length, long value) {
    BDD[] bitvec = new BDD[length];
    for (int i = bitvec.length - 1; i >= 0; i--) {
      if ((value & 1) != 0) {
        bitvec[i] = factory.one();
      } else {
        bitvec[i] = factory.zero();
      }
      value >>= 1;
    }
    return new BDDInteger(factory, ImmutableList.copyOf(bitvec));
  }

  /*
   * Map an if-then-else over each bit in the bitvector
   */
  public BDDInteger ite(BDD b, BDDInteger other) {
    BDD[] bitvec = new BDD[_bitvec.size()];
    for (int i = 0; i < bitvec.length; i++) {
      bitvec[i] = b.ite(_bitvec.get(i), other._bitvec.get(i));
    }
    return new BDDInteger(_factory, ImmutableList.copyOf(bitvec));
  }

  /*
   * Create a BDD representing the exact value
   */
  public BDD value(int val) {
    BDD bdd = _factory.one();
    for (int i = this._bitvec.size() - 1; i >= 0; i--) {
      BDD b = this._bitvec.get(i);
      if ((val & 1) != 0) {
        bdd = bdd.and(b);
      } else {
        bdd = bdd.and(b.not());
      }
      val >>= 1;
    }
    return bdd;
  }

  /*
   * Less than or equal to on integers
   */
  public BDD leq(int val) {
    BDD acc = _factory.one();
    for (int i = _bitvec.size() - 1; i >= 0; i--) {
      BDD eq;
      BDD less;
      if ((val & 1) != 0) {
        eq = _bitvec.get(i);
        less = _bitvec.get(i).not();
      } else {
        eq = _bitvec.get(i).not();
        less = _factory.zero();
      }
      acc = less.or(eq.and(acc));
      val >>= 1;
    }
    return acc;
  }

  /*
   * Less than or equal to on integers
   */
  public BDD geq(int val) {
    BDD acc = _factory.one();
    for (int i = _bitvec.size() - 1; i >= 0; i--) {
      BDD eq;
      BDD greater;
      if ((val & 1) != 0) {
        eq = _bitvec.get(i);
        greater = _factory.zero();
      } else {
        eq = _bitvec.get(i).not();
        greater = _bitvec.get(i);
      }
      acc = greater.or(eq.and(acc));
      val >>= 1;
    }
    return acc;
  }

  /*
   * Add two BDDs bitwise to create a new BDD
   */
  public BDDInteger add(BDDInteger other) {
    if (this._bitvec.size() != other._bitvec.size()) {
      throw new BDDException();
    } else {
      BDD var3 = _factory.zero();
      BDD[] bitvec = new BDD[_bitvec.size()];
      for (int i = bitvec.length - 1; i >= 0; --i) {
        bitvec[i] = this._bitvec.get(i).xor(other._bitvec.get(i));
        bitvec[i] = bitvec[i].xor(var3.id());
        BDD var6 = this._bitvec.get(i).or(other._bitvec.get(i));
        var6 = var6.and(var3);
        BDD var7 = this._bitvec.get(i).and(other._bitvec.get(i));
        var7 = var7.or(var6);
        var3 = var7;
      }
      var3.free();
      return new BDDInteger(_factory, ImmutableList.copyOf(bitvec));
    }
  }

  /*
   * Subtract one BDD from another bitwise to create a new BDD
   */
  public BDDInteger sub(BDDInteger other) {
    if (this._bitvec.size() != other._bitvec.size()) {
      throw new BDDException();
    } else {
      BDD var3 = _factory.zero();
      BDD[] bitvec = new BDD[_bitvec.size()];
      for (int i = bitvec.length - 1; i >= 0; --i) {
        bitvec[i] = this._bitvec.get(i).xor(other._bitvec.get(i));
        bitvec[i] = bitvec[i].xor(var3.id());
        BDD var6 = other._bitvec.get(i).or(var3);
        BDD var7 = this._bitvec.get(i).apply(var6, BDDFactory.less);
        var6.free();
        var6 = this._bitvec.get(i).and(other._bitvec.get(i));
        var6 = var6.and(var3);
        var6 = var6.or(var7);
        var3 = var6;
      }
      var3.free();
      return new BDDInteger(_factory, ImmutableList.copyOf(bitvec));
    }
  }

  public List<BDD> getBitvec() {
    return _bitvec;
  }

  public BDDFactory getFactory() {
    return _factory;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof BDDInteger)) {
      return false;
    }
    BDDInteger other = (BDDInteger) o;

    return Objects.equals(_bitvec, other._bitvec);
  }

  @Override
  public int hashCode() {
    if (_hashCode == null) {
      _hashCode = Objects.hashCode(_bitvec);
    }
    return _hashCode;
  }
}
