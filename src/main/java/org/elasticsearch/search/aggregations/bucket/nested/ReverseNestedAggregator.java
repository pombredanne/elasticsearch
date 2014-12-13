/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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
package org.elasticsearch.search.aggregations.bucket.nested;

import com.carrotsearch.hppc.LongIntOpenHashMap;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.DocIdSet;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.join.BitDocIdSetFilter;
import org.elasticsearch.common.lucene.ReaderContextAware;
import org.elasticsearch.common.lucene.docset.DocIdSets;
import org.elasticsearch.index.mapper.MapperService;
import org.elasticsearch.index.mapper.object.ObjectMapper;
import org.elasticsearch.index.search.nested.NonNestedDocsFilter;
import org.elasticsearch.search.SearchParseException;
import org.elasticsearch.search.aggregations.*;
import org.elasticsearch.search.aggregations.bucket.SingleBucketAggregator;
import org.elasticsearch.search.aggregations.support.AggregationContext;
import org.elasticsearch.search.internal.SearchContext;

import java.io.IOException;
import java.util.Map;

/**
 *
 */
public class ReverseNestedAggregator extends SingleBucketAggregator implements ReaderContextAware {

    private final BitDocIdSetFilter parentFilter;
    private DocIdSetIterator parentDocs;

    // TODO: Add LongIntPagedHashMap?
    private final LongIntOpenHashMap bucketOrdToLastCollectedParentDoc;

    public ReverseNestedAggregator(String name, AggregatorFactories factories, String nestedPath, AggregationContext aggregationContext, Aggregator parent, Map<String, Object> metaData) {
        super(name, factories, aggregationContext, parent, metaData);

        // Early validation
        NestedAggregator closestNestedAggregator = findClosestNestedAggregator(parent);
        if (closestNestedAggregator == null) {
            throw new SearchParseException(context.searchContext(), "Reverse nested aggregation [" + name + "] can only be used inside a [nested] aggregation");
        }
        if (nestedPath == null) {
            parentFilter = SearchContext.current().bitsetFilterCache().getBitDocIdSetFilter(NonNestedDocsFilter.INSTANCE);
        } else {
            MapperService.SmartNameObjectMapper mapper = SearchContext.current().smartNameObjectMapper(nestedPath);
            if (mapper == null) {
                throw new AggregationExecutionException("[reverse_nested] nested path [" + nestedPath + "] not found");
            }
            ObjectMapper objectMapper = mapper.mapper();
            if (objectMapper == null) {
                throw new AggregationExecutionException("[reverse_nested] nested path [" + nestedPath + "] not found");
            }
            if (!objectMapper.nested().isNested()) {
                throw new AggregationExecutionException("[reverse_nested] nested path [" + nestedPath + "] is not nested");
            }
            parentFilter = SearchContext.current().bitsetFilterCache().getBitDocIdSetFilter(objectMapper.nestedTypeFilter());
        }
        bucketOrdToLastCollectedParentDoc = new LongIntOpenHashMap(32);
        aggregationContext.ensureScoreDocsInOrder();
    }

    @Override
    public void setNextReader(LeafReaderContext reader) {
        bucketOrdToLastCollectedParentDoc.clear();
        try {
            // In ES if parent is deleted, then also the children are deleted, so the child docs this agg receives
            // must belong to parent docs that is alive. For this reason acceptedDocs can be null here.
            DocIdSet docIdSet = parentFilter.getDocIdSet(reader, null);
            if (DocIdSets.isEmpty(docIdSet)) {
                parentDocs = null;
            } else {
                parentDocs = docIdSet.iterator();
            }
        } catch (IOException ioe) {
            throw new AggregationExecutionException("Failed to aggregate [" + name + "]", ioe);
        }
    }

    @Override
    public void collect(int childDoc, long bucketOrd) throws IOException {
        if (parentDocs == null) {
            return;
        }

        // fast forward to retrieve the parentDoc this childDoc belongs to
        final int parentDoc;
        if (parentDocs.docID() < childDoc) {
            parentDoc = parentDocs.advance(childDoc);
        } else {
            parentDoc = parentDocs.docID();
        }
        assert childDoc <= parentDoc && parentDoc != DocIdSetIterator.NO_MORE_DOCS;
        if (bucketOrdToLastCollectedParentDoc.containsKey(bucketOrd)) {
            int lastCollectedParentDoc = bucketOrdToLastCollectedParentDoc.lget();
            if (parentDoc > lastCollectedParentDoc) {
                innerCollect(parentDoc, bucketOrd);
                bucketOrdToLastCollectedParentDoc.lset(parentDoc);
            }
        } else {
            innerCollect(parentDoc, bucketOrd);
            bucketOrdToLastCollectedParentDoc.put(bucketOrd, parentDoc);
        }
    }

    private void innerCollect(int parentDoc, long bucketOrd) throws IOException {
        collectBucket(parentDoc, bucketOrd);
    }

    private static NestedAggregator findClosestNestedAggregator(Aggregator parent) {
        for (; parent != null; parent = parent.parent()) {
            if (parent instanceof NestedAggregator) {
                return (NestedAggregator) parent;
            }
        }
        return null;
    }

    @Override
    public InternalAggregation buildAggregation(long owningBucketOrdinal) {
        return new InternalReverseNested(name, bucketDocCount(owningBucketOrdinal), bucketAggregations(owningBucketOrdinal), getMetaData());
    }

    @Override
    public InternalAggregation buildEmptyAggregation() {
        return new InternalReverseNested(name, 0, buildEmptySubAggregations(), getMetaData());
    }

    Filter getParentFilter() {
        return parentFilter;
    }

    public static class Factory extends AggregatorFactory {

        private final String path;

        public Factory(String name, String path) {
            super(name, InternalReverseNested.TYPE.name());
            this.path = path;
        }

        @Override
        public Aggregator createInternal(AggregationContext context, Aggregator parent, long expectedBucketsCount, Map<String, Object> metaData) {
            return new ReverseNestedAggregator(name, factories, path, context, parent, metaData);
        }
    }
}
