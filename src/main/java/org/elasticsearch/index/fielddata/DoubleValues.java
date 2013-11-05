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

import org.elasticsearch.ElasticSearchIllegalStateException;
import org.elasticsearch.index.fielddata.ordinals.Ordinals;
import org.elasticsearch.index.fielddata.ordinals.Ordinals.Docs;

/**
 * A state-full lightweight per document set of <code>double</code> values.
 *
 * To iterate over values in a document use the following pattern:
 * <pre>
 *   DoubleValues values = ..;
 *   final int numValues = values.setDocId(docId);
 *   for (int i = 0; i < numValues; i++) {
 *       double value = values.nextValue();
 *       // process value
 *   }
 * </pre>
 */
public abstract class DoubleValues {

    /**
     * An empty {@link DoubleValues instance}
     */
    public static final DoubleValues EMPTY = new Empty();

    private final boolean multiValued;

    protected int docId;

    /**
     * Creates a new {@link DoubleValues} instance
     * @param multiValued <code>true</code> iff this instance is multivalued. Otherwise <code>false</code>.
     */
    protected DoubleValues(boolean multiValued) {
        this.multiValued = multiValued;
    }

    /**
     * Is one of the documents in this field data values is multi valued?
     */
    public final boolean isMultiValued() {
        return multiValued;
    }

    /**
     * Sets iteration to the specified docID and returns the number of
     * values for this document ID,
     * @param docId document ID
     *
     * @see #nextValue()
     */
    public abstract int setDocument(int docId);

    /**
     * Returns the next value for the current docID set to {@link #setDocument(int)}.
     * This method should only be called <tt>N</tt> times where <tt>N</tt> is the number
     * returned from {@link #setDocument(int)}. If called more than <tt>N</tt> times the behavior
     * is undefined.
     *
     * @return the next value for the current docID set to {@link #setDocument(int)}.
     */
    public abstract double nextValue();

    /**
     * Ordinal based {@link DoubleValues}.
     */
    public static abstract class WithOrdinals extends DoubleValues {

        protected final Docs ordinals;

        protected WithOrdinals(Ordinals.Docs ordinals) {
            super(ordinals.isMultiValued());
            this.ordinals = ordinals;
        }

        /**
         * Returns the associated ordinals instance.
         * @return the associated ordinals instance.
         */
        public Docs ordinals() {
            return ordinals;
        }

        /**
         * Returns the value for the given ordinal.
         * @param ord the ordinal to lookup.
         * @return a double value associated with the given ordinal.
         */
        public abstract double getValueByOrd(long ord);


        @Override
        public int setDocument(int docId) {
            this.docId = docId;
            return ordinals.setDocument(docId);
        }

        @Override
        public double nextValue() {
            return getValueByOrd(ordinals.nextOrd());
        }
    }
    /**
     * An empty {@link DoubleValues} implementation
     */
    private static class Empty extends DoubleValues {

        Empty() {
            super(false);
        }

        @Override
        public int setDocument(int docId) {
            return 0;
        }
        
        @Override
        public double nextValue() {
            throw new ElasticSearchIllegalStateException("Empty DoubleValues has no next value");
        }
    }

}
