package org.batfish.datamodel;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.collect.ImmutableSortedSet;
import java.io.Serializable;
import java.util.Collections;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import javax.annotation.Nullable;

public class PrefixTrie implements Serializable {

  private class ByteTrie implements Serializable {

    /** */
    private static final long serialVersionUID = 1L;

    private ByteTrieNode _root;

    public ByteTrie() {
      _root = new ByteTrieNode();
    }

    private ByteTrie(ByteTrieNode root) {
      _root = root;
    }

    public void addPrefix(Prefix prefix) {
      int prefixLength = prefix.getPrefixLength();
      long bits = prefix.getStartIp().asLong();
      _root.addPrefix(prefix, bits, prefixLength, 0);
    }

    public boolean containsPathFromPrefix(Prefix prefix) {
      int prefixLength = prefix.getPrefixLength();
      long bits = prefix.getStartIp().asLong();
      return _root.containsPathFromPrefix(bits, prefixLength, 0);
    }

    public Prefix getLongestPrefixMatch(Ip address) {
      long addressBits = address.asLong();
      return _root.getLongestPrefixMatch(address, addressBits, 0);
    }

    public void addAllFrom(ByteTrie trie) {
      _root.addAllFrom(trie._root);
    }

    public SortedSet<Prefix> getPrefixes() {
      TreeSet<Prefix> prefixes = new TreeSet<>();
      _root.collectPrefixes(prefixes);
      return prefixes;
    }
  }

  private class ByteTrieNode implements Serializable {

    /** */
    private static final long serialVersionUID = 1L;

    private ByteTrieNode _left;

    /*
     * TODO why store this? There's only one choice of prefix at each node, so we just need a bit
     * to indicate whether it's present.
     */
    private Prefix _prefix;

    private ByteTrieNode _right;

    public void addAllFrom(ByteTrieNode other) {
      if (other == null) {
        return;
      }

      if (_prefix == null) {
        _prefix = other._prefix;
      } else {
        // there's only 1 choice of prefix at each node, so make sure they agree
        assert other._prefix == null || _prefix.equals(other._prefix);
      }

      if (other._left != null) {
        if (_left == null) {
          _left = new ByteTrieNode();
        }
        _left.addAllFrom(other._left);
      }

      if (other._right != null) {
        if (_right == null) {
          _right = new ByteTrieNode();
        }
        _right.addAllFrom(other._right);
      }
    }

    public void addPrefix(Prefix prefix, long bits, int prefixLength, int depth) {
      if (prefixLength == depth) {
        _prefix = prefix;
        return;
      } else {
        boolean currentBit = Ip.getBitAtPosition(bits, depth);
        if (currentBit) {
          if (_right == null) {
            _right = new ByteTrieNode();
          }
          _right.addPrefix(prefix, bits, prefixLength, depth + 1);
        } else {
          if (_left == null) {
            _left = new ByteTrieNode();
          }
          _left.addPrefix(prefix, bits, prefixLength, depth + 1);
        }
      }
    }

    public boolean containsPathFromPrefix(long bits, int prefixLength, int depth) {
      if (prefixLength == depth) {
        if (depth == 0 && _prefix == null) {
          return false;
        } else {
          return true;
        }
      } else {
        boolean currentBit = Ip.getBitAtPosition(bits, depth);
        if (currentBit) {
          if (_right == null) {
            return false;
          } else {
            return _right.containsPathFromPrefix(bits, prefixLength, depth + 1);
          }
        } else {
          if (_left == null) {
            return false;
          } else {
            return _left.containsPathFromPrefix(bits, prefixLength, depth + 1);
          }
        }
      }
    }

    @Nullable
    private Prefix getLongestPrefixMatch(Ip address) {
      if (_prefix != null && _prefix.containsIp(address)) {
        return _prefix;
      } else {
        return null;
      }
    }

    public Prefix getLongestPrefixMatch(Ip address, long bits, int index) {
      Prefix longestPrefixMatch = getLongestPrefixMatch(address);
      if (index == Prefix.MAX_PREFIX_LENGTH) {
        return longestPrefixMatch;
      }
      boolean currentBit = Ip.getBitAtPosition(bits, index);
      Prefix longerMatch = null;
      if (currentBit) {
        if (_right != null) {
          longerMatch = _right.getLongestPrefixMatch(address, bits, index + 1);
        }
      } else {
        if (_left != null) {
          longerMatch = _left.getLongestPrefixMatch(address, bits, index + 1);
        }
      }
      if (longerMatch == null) {
        return longestPrefixMatch;
      } else {
        return longerMatch;
      }
    }

    public void collectPrefixes(Set<Prefix> prefixes) {
      if (_prefix != null) {
        prefixes.add(_prefix);
      }
      if (_left != null) {
        _left.collectPrefixes(prefixes);
      }
      if (_right != null) {
        _right.collectPrefixes(prefixes);
      }
    }
  }

  /** */
  private static final long serialVersionUID = 1L;

  private SortedSet<Prefix> _prefixes;

  private ByteTrie _trie;

  public PrefixTrie() {
    _trie = new ByteTrie();
    _prefixes = Collections.emptySortedSet();
  }

  @JsonCreator
  public PrefixTrie(SortedSet<Prefix> prefixes) {
    _prefixes = ImmutableSortedSet.copyOf(prefixes);
    _trie = new ByteTrie();
    for (Prefix prefix : _prefixes) {
      _trie.addPrefix(prefix);
    }
  }

  public boolean containsIp(Ip address) {
    return _trie.getLongestPrefixMatch(address) != null;
  }

  public boolean containsPathFromPrefix(Prefix prefix) {
    return _trie.containsPathFromPrefix(prefix);
  }

  public Prefix getLongestPrefixMatch(Ip address) {
    return _trie.getLongestPrefixMatch(address);
  }

  @JsonValue
  public SortedSet<Prefix> getPrefixes() {
    if (_prefixes == null) {
      _prefixes = _trie.getPrefixes();
    }
    return _prefixes;
  }

  public PrefixTrie union(PrefixTrie other) {
    PrefixTrie result = new PrefixTrie();
    result._trie.addAllFrom(_trie);
    result._trie.addAllFrom(other._trie);
    result._prefixes = null;
    return result;
  }
}
