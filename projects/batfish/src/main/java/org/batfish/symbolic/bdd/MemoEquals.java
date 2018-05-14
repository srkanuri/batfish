package org.batfish.symbolic.bdd;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.util.concurrent.ExecutionException;
import scala.Function2;

public class MemoEquals {
  private static final Cache<Object, Cache<Object, Boolean>> EQUALS_CACHE =
      CacheBuilder.newBuilder().weakKeys().build();

  /** null return value means no entry */
  private static Boolean lookupEquals(Object obj1, Object obj2) {
    Cache<Object, Boolean> equalToObj1 = EQUALS_CACHE.getIfPresent(obj1);
    Boolean equal = equalToObj1 != null ? equalToObj1.getIfPresent(obj2) : null;
    return equal;
  }

  /** Only use with immutable objects */
  public static <T> boolean memoEquals(T obj1, T obj2, Function2<T, T, Boolean> equalsImpl) {
    Boolean cachedEqual = lookupEquals(obj1, obj2);
    if (cachedEqual == null) {
      cachedEqual = lookupEquals(obj2, obj1);
    }
    if (cachedEqual != null) {
      return cachedEqual;
    }
    boolean equal = equalsImpl.apply(obj1, obj2);
    setEquals(obj1, obj2, equal);
    return equal;
  }

  private static void setEquals(Object obj1, Object obj2, boolean equal) {
    try {
      EQUALS_CACHE.get(obj1, () -> CacheBuilder.newBuilder().weakKeys().build()).put(obj2, equal);
    } catch (ExecutionException e) {
      e.printStackTrace();
    }
  }
}
