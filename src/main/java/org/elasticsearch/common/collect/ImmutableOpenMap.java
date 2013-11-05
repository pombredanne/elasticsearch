/*
 * Licensed to ElasticSearch and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. ElasticSearch licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.common.collect;

import com.carrotsearch.hppc.*;
import com.carrotsearch.hppc.cursors.ObjectObjectCursor;
import com.carrotsearch.hppc.predicates.ObjectPredicate;
import com.carrotsearch.hppc.procedures.ObjectObjectProcedure;

import java.util.Iterator;

/**
 * An immutable map implementation based on open hash map.
 * <p/>
 * Can be constructed using a {@link #builder()}, or using {@link #builder(ImmutableOpenMap)} (which is an optimized
 * option to copy over existing content and modify it).
 */
public final class ImmutableOpenMap<KType, VType> implements Iterable<ObjectObjectCursor<KType, VType>> {

    private final ObjectObjectOpenHashMap<KType, VType> map;

    private ImmutableOpenMap(ObjectObjectOpenHashMap<KType, VType> map) {
        this.map = map;
    }

    /**
     * @return Returns the value associated with the given key or the default value
     *         for the key type, if the key is not associated with any value.
     *         <p/>
     *         <b>Important note:</b> For primitive type values, the value returned for a non-existing
     *         key may not be the default value of the primitive type (it may be any value previously
     *         assigned to that slot).
     */
    public VType get(KType key) {
        return map.get(key);
    }

    /**
     * Returns <code>true</code> if this container has an association to a value for
     * the given key.
     */
    public boolean containsKey(KType key) {
        return map.containsKey(key);
    }

    /**
     * @return Returns the current size (number of assigned keys) in the container.
     */
    public int size() {
        return map.size();
    }

    /**
     * @return Return <code>true</code> if this hash map contains no assigned keys.
     */
    public boolean isEmpty() {
        return map.isEmpty();
    }

    /**
     * Returns a cursor over the entries (key-value pairs) in this map. The iterator is
     * implemented as a cursor and it returns <b>the same cursor instance</b> on every
     * call to {@link Iterator#next()}. To read the current key and value use the cursor's
     * public fields. An example is shown below.
     * <pre>
     * for (IntShortCursor c : intShortMap)
     * {
     *     System.out.println(&quot;index=&quot; + c.index
     *       + &quot; key=&quot; + c.key
     *       + &quot; value=&quot; + c.value);
     * }
     * </pre>
     * <p/>
     * <p>The <code>index</code> field inside the cursor gives the internal index inside
     * the container's implementation. The interpretation of this index depends on
     * to the container.
     */
    @Override
    public Iterator<ObjectObjectCursor<KType, VType>> iterator() {
        return map.iterator();
    }

    /**
     * Returns a specialized view of the keys of this associated container.
     * The view additionally implements {@link ObjectLookupContainer}.
     */
    public ObjectLookupContainer<KType> keys() {
        return map.keys();
    }

    /**
     * @return Returns a container with all values stored in this map.
     */
    public ObjectContainer<VType> values() {
        return map.values();
    }

    @Override
    public String toString() {
        return map.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ImmutableOpenMap that = (ImmutableOpenMap) o;

        if (!map.equals(that.map)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return map.hashCode();
    }

    @SuppressWarnings("unchecked")
    private static final ImmutableOpenMap EMPTY = new ImmutableOpenMap(new ObjectObjectOpenHashMap());

    @SuppressWarnings("unchecked")
    public static <KType, VType> ImmutableOpenMap<KType, VType> of() {
        return EMPTY;
    }

    public static <KType, VType> Builder<KType, VType> builder() {
        return new Builder<KType, VType>();
    }

    public static <KType, VType> Builder<KType, VType> builder(ImmutableOpenMap<KType, VType> map) {
        return new Builder<KType, VType>(map);
    }

    public static class Builder<KType, VType> implements ObjectObjectMap<KType, VType> {

        private ObjectObjectOpenHashMap<KType, VType> map;

        public Builder() {
            //noinspection unchecked
            this(EMPTY);
        }

        public Builder(ImmutableOpenMap<KType, VType> map) {
            this.map = map.map.clone();
        }

        /**
         * Builds a new instance of the
         */
        public ImmutableOpenMap<KType, VType> build() {
            ObjectObjectOpenHashMap<KType, VType> map = this.map;
            this.map = null; // nullify the map, so any operation post build will fail! (hackish, but safest)
            return new ImmutableOpenMap<KType, VType>(map);
        }

        /**
         * A put operation that can be used in the fluent pattern.
         */
        public Builder<KType, VType> fPut(KType key, VType value) {
            map.put(key, value);
            return this;
        }

        @Override
        public VType put(KType key, VType value) {
            return map.put(key, value);
        }

        @Override
        public VType get(KType key) {
            return map.get(key);
        }

        @Override
        public int putAll(ObjectObjectAssociativeContainer<? extends KType, ? extends VType> container) {
            return map.putAll(container);
        }

        @Override
        public int putAll(Iterable<? extends ObjectObjectCursor<? extends KType, ? extends VType>> iterable) {
            return map.putAll(iterable);
        }

        /**
         * Remove that can be used in the fluent pattern.
         */
        public Builder<KType, VType> fRemove(KType key) {
            map.remove(key);
            return this;
        }

        @Override
        public VType remove(KType key) {
            return map.remove(key);
        }

        @Override
        public Iterator<ObjectObjectCursor<KType, VType>> iterator() {
            return map.iterator();
        }

        @Override
        public boolean containsKey(KType key) {
            return map.containsKey(key);
        }

        @Override
        public int size() {
            return map.size();
        }

        @Override
        public boolean isEmpty() {
            return map.isEmpty();
        }

        @Override
        public int removeAll(ObjectContainer<? extends KType> container) {
            return map.removeAll(container);
        }

        @Override
        public int removeAll(ObjectPredicate<? super KType> predicate) {
            return map.removeAll(predicate);
        }

        @Override
        public <T extends ObjectObjectProcedure<? super KType, ? super VType>> T forEach(T procedure) {
            return map.forEach(procedure);
        }

        @Override
        public void clear() {
            map.clear();
        }

        @Override
        public ObjectCollection<KType> keys() {
            return map.keys();
        }

        @Override
        public ObjectContainer<VType> values() {
            return map.values();
        }
    }
}
