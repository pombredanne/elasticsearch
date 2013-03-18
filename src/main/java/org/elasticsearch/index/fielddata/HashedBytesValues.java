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

package org.elasticsearch.index.fielddata;

import org.apache.lucene.util.BytesRef;
import org.elasticsearch.ElasticSearchIllegalStateException;
import org.elasticsearch.common.lucene.HashedBytesRef;
import org.elasticsearch.index.fielddata.ordinals.EmptyOrdinals;
import org.elasticsearch.index.fielddata.ordinals.Ordinals;

/**
 */
public interface HashedBytesValues {

    static final HashedBytesValues EMPTY = new Empty();

    /**
     * Is one of the documents in this field data values is multi valued?
     */
    boolean isMultiValued();

    /**
     * Is there a value for this doc?
     */
    boolean hasValue(int docId);

    /**
     * Converts the provided bytes to "safe" ones from a "non" safe call made (if needed).
     */
    HashedBytesRef makeSafe(HashedBytesRef bytes);

    /**
     * Returns a bytes value for a docId. Note, the content of it might be shared across invocation,
     * call {@link #makeSafe(org.elasticsearch.common.lucene.HashedBytesRef)} to converts it to a "safe"
     * option (if needed).
     */
    HashedBytesRef getValue(int docId);

    /**
     * Returns a bytes value iterator for a docId. Note, the content of it might be shared across invocation.
     */
    Iter getIter(int docId);

    /**
     * Go over all the possible values in their BytesRef format for a specific doc.
     */
    void forEachValueInDoc(int docId, ValueInDocProc proc);

    public static interface ValueInDocProc {
        void onValue(int docId, HashedBytesRef value);

        void onMissing(int docId);
    }

    static interface Iter {

        boolean hasNext();

        HashedBytesRef next();

        static class Empty implements Iter {

            public static final Empty INSTANCE = new Empty();

            @Override
            public boolean hasNext() {
                return false;
            }

            @Override
            public HashedBytesRef next() {
                throw new ElasticSearchIllegalStateException();
            }
        }

        static class Single implements Iter {

            public HashedBytesRef value;
            public boolean done;

            public Single reset(HashedBytesRef value) {
                this.value = value;
                this.done = false;
                return this;
            }

            @Override
            public boolean hasNext() {
                return !done;
            }

            @Override
            public HashedBytesRef next() {
                assert !done;
                done = true;
                return value;
            }
        }
    }

    static class Empty implements HashedBytesValues {
        @Override
        public boolean isMultiValued() {
            return false;
        }

        @Override
        public boolean hasValue(int docId) {
            return false;
        }

        @Override
        public HashedBytesRef getValue(int docId) {
            return null;
        }

        @Override
        public Iter getIter(int docId) {
            return Iter.Empty.INSTANCE;
        }

        @Override
        public void forEachValueInDoc(int docId, ValueInDocProc proc) {
            proc.onMissing(docId);
        }

        @Override
        public HashedBytesRef makeSafe(HashedBytesRef bytes) {
            //todo maybe better to throw an excepiton here as the only value this method accepts is a scratch value...
            //todo ...extracted from this ByteValues, in our case, there are not values, so this should never be called!?!?
            return HashedBytesRef.deepCopyOf(bytes);
        }
    }

    /**
     * A {@link BytesValues} based implementation.
     */
    static class BytesBased implements HashedBytesValues {

        private final BytesValues values;

        protected final HashedBytesRef scratch = new HashedBytesRef(new BytesRef());
        private final ValueIter valueIter = new ValueIter();
        private final Proc proc = new Proc();

        public BytesBased(BytesValues values) {
            this.values = values;
        }

        @Override
        public boolean isMultiValued() {
            return values.isMultiValued();
        }

        @Override
        public boolean hasValue(int docId) {
            return values.hasValue(docId);
        }

        @Override
        public HashedBytesRef makeSafe(HashedBytesRef bytes) {
            return new HashedBytesRef(values.makeSafe(bytes.bytes), bytes.hash);
        }

        @Override
        public HashedBytesRef getValue(int docId) {
            BytesRef value = values.getValue(docId);
            if (value == null) return null;
            scratch.bytes = value;
            return scratch.resetHashCode();
        }

        @Override
        public Iter getIter(int docId) {
            return valueIter.reset(values.getIter(docId));
        }

        @Override
        public void forEachValueInDoc(int docId, final ValueInDocProc proc) {
            values.forEachValueInDoc(docId, this.proc.reset(proc));
        }

        static class ValueIter implements Iter {

            private final HashedBytesRef scratch = new HashedBytesRef(new BytesRef());
            private BytesValues.Iter iter;

            public ValueIter reset(BytesValues.Iter iter) {
                this.iter = iter;
                return this;
            }

            @Override
            public boolean hasNext() {
                return iter.hasNext();
            }

            @Override
            public HashedBytesRef next() {
                scratch.bytes = iter.next();
                return scratch.resetHashCode();
            }
        }

        static class Proc implements BytesValues.ValueInDocProc {

            private final HashedBytesRef scratch = new HashedBytesRef();
            private ValueInDocProc proc;

            public Proc reset(ValueInDocProc proc) {
                this.proc = proc;
                return this;
            }

            @Override
            public void onValue(int docId, BytesRef value) {
                scratch.bytes = value;
                proc.onValue(docId, scratch.resetHashCode());
            }

            @Override
            public void onMissing(int docId) {
                proc.onMissing(docId);
            }
        }
    }

    static class StringBased implements HashedBytesValues {

        private final StringValues values;

        protected final HashedBytesRef scratch = new HashedBytesRef(new BytesRef());
        private final ValueIter valueIter = new ValueIter();
        private final Proc proc = new Proc();

        public StringBased(StringValues values) {
            this.values = values;
        }

        @Override
        public boolean isMultiValued() {
            return values.isMultiValued();
        }

        @Override
        public boolean hasValue(int docId) {
            return values.hasValue(docId);
        }

        @Override
        public HashedBytesRef makeSafe(HashedBytesRef bytes) {
            // we use scratch to provide it, so just need to copy it over to a new instance
            return new HashedBytesRef(bytes.bytes, bytes.hash);
        }

        @Override
        public HashedBytesRef getValue(int docId) {
            String value = values.getValue(docId);
            if (value == null) return null;
            scratch.bytes.copyChars(value);
            return scratch.resetHashCode();
        }

        @Override
        public Iter getIter(int docId) {
            return valueIter.reset(values.getIter(docId));
        }

        @Override
        public void forEachValueInDoc(int docId, final ValueInDocProc proc) {
            values.forEachValueInDoc(docId, this.proc.reset(proc));
        }

        static class ValueIter implements Iter {

            private final HashedBytesRef scratch = new HashedBytesRef(new BytesRef());
            private StringValues.Iter iter;

            public ValueIter reset(StringValues.Iter iter) {
                this.iter = iter;
                return this;
            }

            @Override
            public boolean hasNext() {
                return iter.hasNext();
            }

            @Override
            public HashedBytesRef next() {
                scratch.bytes.copyChars(iter.next());
                return scratch.resetHashCode();
            }
        }

        static class Proc implements StringValues.ValueInDocProc {

            private final HashedBytesRef scratch = new HashedBytesRef(new BytesRef());
            private ValueInDocProc proc;

            public Proc reset(ValueInDocProc proc) {
                this.proc = proc;
                return this;
            }

            @Override
            public void onValue(int docId, String value) {
                scratch.bytes.copyChars(value);
                proc.onValue(docId, scratch);
            }

            @Override
            public void onMissing(int docId) {
                proc.onMissing(docId);
            }
        }
    }


    public interface WithOrdinals extends HashedBytesValues {

        Ordinals.Docs ordinals();

        HashedBytesRef getValueByOrd(int ord);

        HashedBytesRef getSafeValueByOrd(int ord);

        public static class Empty extends HashedBytesValues.Empty implements WithOrdinals {

            private final Ordinals ordinals;

            public Empty(EmptyOrdinals ordinals) {
                this.ordinals = ordinals;
            }

            @Override
            public Ordinals.Docs ordinals() {
                return ordinals.ordinals();
            }

            @Override
            public HashedBytesRef getValueByOrd(int ord) {
                return null;
            }

            @Override
            public HashedBytesRef getSafeValueByOrd(int ord) {
                return null;
            }
        }

        static class BytesBased extends HashedBytesValues.BytesBased implements WithOrdinals {

            private final BytesValues.WithOrdinals values;

            public BytesBased(BytesValues.WithOrdinals values) {
                super(values);
                this.values = values;
            }

            @Override
            public Ordinals.Docs ordinals() {
                return values.ordinals();
            }

            @Override
            public HashedBytesRef getValueByOrd(int ord) {
                scratch.bytes = values.getValueByOrd(ord);
                return scratch.resetHashCode();
            }

            @Override
            public HashedBytesRef getSafeValueByOrd(int ord) {
                return new HashedBytesRef(values.getSafeValueByOrd(ord));
            }
        }

        static class StringBased extends HashedBytesValues.StringBased implements WithOrdinals {

            private final StringValues.WithOrdinals values;

            public StringBased(StringValues.WithOrdinals values) {
                super(values);
                this.values = values;
            }

            @Override
            public Ordinals.Docs ordinals() {
                return values.ordinals();
            }

            @Override
            public HashedBytesRef getValueByOrd(int ord) {
                scratch.bytes.copyChars(values.getValueByOrd(ord));
                return scratch.resetHashCode();
            }

            @Override
            public HashedBytesRef getSafeValueByOrd(int ord) {
                return new HashedBytesRef(new BytesRef(values.getValueByOrd(ord)));
            }
        }
    }
}
