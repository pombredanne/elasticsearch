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

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.DocIdSet;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.join.BitDocIdSetFilter;
import org.apache.lucene.util.BitDocIdSet;
import org.apache.lucene.util.BitSet;
import org.elasticsearch.common.lucene.ReaderContextAware;
import org.elasticsearch.common.lucene.docset.DocIdSets;
import org.elasticsearch.index.mapper.MapperService;
import org.elasticsearch.index.mapper.object.ObjectMapper;
import org.elasticsearch.index.search.nested.NonNestedDocsFilter;
import org.elasticsearch.search.aggregations.*;
import org.elasticsearch.search.aggregations.bucket.SingleBucketAggregator;
import org.elasticsearch.search.aggregations.support.AggregationContext;
import org.elasticsearch.search.internal.SearchContext;

import java.io.IOException;
import java.util.Map;

/**
 *
 */
public class NestedAggregator extends SingleBucketAggregator implements ReaderContextAware {

    private final String nestedPath;
    private final Aggregator parentAggregator;
    private BitDocIdSetFilter parentFilter;
    private final Filter childFilter;

    private DocIdSetIterator childDocs;
    private BitSet parentDocs;

    public NestedAggregator(String name, AggregatorFactories factories, String nestedPath, AggregationContext aggregationContext, Aggregator parentAggregator, Map<String, Object> metaData) {
        super(name, factories, aggregationContext, parentAggregator, metaData);
        this.nestedPath = nestedPath;
        this.parentAggregator = parentAggregator;
        MapperService.SmartNameObjectMapper mapper = aggregationContext.searchContext().smartNameObjectMapper(nestedPath);
        if (mapper == null) {
            throw new AggregationExecutionException("[nested] nested path [" + nestedPath + "] not found");
        }
        ObjectMapper objectMapper = mapper.mapper();
        if (objectMapper == null) {
            throw new AggregationExecutionException("[nested] nested path [" + nestedPath + "] not found");
        }
        if (!objectMapper.nested().isNested()) {
            throw new AggregationExecutionException("[nested] nested path [" + nestedPath + "] is not nested");
        }

        // TODO: Revise the cache usage for childFilter
        // Typical usage of the childFilter in this agg is that not all parent docs match and because this agg executes
        // in order we are maybe better off not caching? We can then iterate over the posting list and benefit from skip pointers.
        // Even if caching does make sense it is likely that it shouldn't be forced as is today, but based on heuristics that
        // the filter cache maintains that the childFilter should be cached.

        // By caching the childFilter we're consistent with other features and previous versions.
        childFilter = aggregationContext.searchContext().filterCache().cache(objectMapper.nestedTypeFilter());
        // The childDocs need to be consumed in docId order, this ensures that:
        aggregationContext.ensureScoreDocsInOrder();
    }

    @Override
    public void setNextReader(LeafReaderContext reader) {
        if (parentFilter == null) {
            // The aggs are instantiated in reverse, first the most inner nested aggs and lastly the top level aggs
            // So at the time a nested 'nested' aggs is parsed its closest parent nested aggs hasn't been constructed.
            // So the trick to set at the last moment just before needed and we can use its child filter as the
            // parent filter.
            Filter parentFilterNotCached = findClosestNestedPath(parentAggregator);
            if (parentFilterNotCached == null) {
                parentFilterNotCached = NonNestedDocsFilter.INSTANCE;
            }
            parentFilter = SearchContext.current().bitsetFilterCache().getBitDocIdSetFilter(parentFilterNotCached);
        }

        try {
            BitDocIdSet parentSet = parentFilter.getDocIdSet(reader);
            if (DocIdSets.isEmpty(parentSet)) {
                parentDocs = null;
            } else {
                parentDocs = parentSet.bits();
            }
            // In ES if parent is deleted, then also the children are deleted. Therefore acceptedDocs can also null here.
            DocIdSet childDocIdSet = childFilter.getDocIdSet(reader, null);
            if (DocIdSets.isEmpty(childDocIdSet)) {
                childDocs = null;
            } else {
                childDocs = childDocIdSet.iterator();
            }
        } catch (IOException ioe) {
            throw new AggregationExecutionException("Failed to aggregate [" + name + "]", ioe);
        }
    }

    @Override
    public void collect(int parentDoc, long bucketOrd) throws IOException {
        // here we translate the parent doc to a list of its nested docs, and then call super.collect for evey one of them so they'll be collected

        // if parentDoc is 0 then this means that this parent doesn't have child docs (b/c these appear always before the parent doc), so we can skip:
        if (parentDoc == 0 || childDocs == null) {
            return;
        }
        int prevParentDoc = parentDocs.prevSetBit(parentDoc - 1);
        int childDocId;
        if (childDocs.docID() > prevParentDoc) {
            childDocId = childDocs.docID();
        } else {
            childDocId = childDocs.advance(prevParentDoc + 1);
        }

        int numChildren = 0;
        for (; childDocId < parentDoc; childDocId = childDocs.nextDoc()) {
            numChildren++;
            collectBucketNoCounts(childDocId, bucketOrd);
        }
        incrementBucketDocCount(bucketOrd, numChildren);
    }

    @Override
    public InternalAggregation buildAggregation(long owningBucketOrdinal) {
        return new InternalNested(name, bucketDocCount(owningBucketOrdinal), bucketAggregations(owningBucketOrdinal), getMetaData());
    }

        @Override
    public InternalAggregation buildEmptyAggregation() {
        return new InternalNested(name, 0, buildEmptySubAggregations(), getMetaData());
    }

    public String getNestedPath() {
        return nestedPath;
    }

    private static Filter findClosestNestedPath(Aggregator parent) {
        for (; parent != null; parent = parent.parent()) {
            if (parent instanceof NestedAggregator) {
                return ((NestedAggregator) parent).childFilter;
            } else if (parent instanceof ReverseNestedAggregator) {
                return ((ReverseNestedAggregator) parent).getParentFilter();
            }
        }
        return null;
    }

    public static class Factory extends AggregatorFactory {

        private final String path;

        public Factory(String name, String path) {
            super(name, InternalNested.TYPE.name());
            this.path = path;
        }

        @Override
        public Aggregator createInternal(AggregationContext context, Aggregator parent, long expectedBucketsCount, Map<String, Object> metaData) {
            return new NestedAggregator(name, factories, path, context, parent, metaData);
        }
    }
}
