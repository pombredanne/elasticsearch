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

package org.elasticsearch.indices.fielddata.cache;

import com.google.common.cache.*;
import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.SegmentReader;
import org.elasticsearch.common.component.AbstractComponent;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.fielddata.AtomicFieldData;
import org.elasticsearch.index.fielddata.FieldDataType;
import org.elasticsearch.index.fielddata.IndexFieldData;
import org.elasticsearch.index.fielddata.IndexFieldDataCache;
import org.elasticsearch.index.mapper.FieldMapper;
import org.elasticsearch.monitor.jvm.JvmInfo;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 */
public class IndicesFieldDataCache extends AbstractComponent implements RemovalListener<IndicesFieldDataCache.Key, AtomicFieldData> {

    Cache<Key, AtomicFieldData> cache;

    private volatile String size;
    private volatile long sizeInBytes;
    private volatile TimeValue expire;


    @Inject
    public IndicesFieldDataCache(Settings settings) {
        super(settings);
        this.size = componentSettings.get("size", "40%");
        this.expire = componentSettings.getAsTime("expire", null);
        computeSizeInBytes();
        buildCache();
    }

    private void buildCache() {
        CacheBuilder<Key, AtomicFieldData> cacheBuilder = CacheBuilder.newBuilder()
                .removalListener(this);
        cacheBuilder.maximumWeight(sizeInBytes).weigher(new FieldDataWeigher());
        // defaults to 4, but this is a busy map for all indices, increase it a bit
        cacheBuilder.concurrencyLevel(16);
        if (expire != null) {
            cacheBuilder.expireAfterAccess(expire.millis(), TimeUnit.MILLISECONDS);
        }
        cache = cacheBuilder.build();
    }

    private void computeSizeInBytes() {
        if (size.endsWith("%")) {
            double percent = Double.parseDouble(size.substring(0, size.length() - 1));
            sizeInBytes = (long) ((percent / 100) * JvmInfo.jvmInfo().getMem().getHeapMax().bytes());
        } else {
            sizeInBytes = ByteSizeValue.parseBytesSizeValue(size).bytes();
        }
    }

    public void close() {
        cache.invalidateAll();
    }

    public IndexFieldDataCache buildIndexFieldDataCache(Index index, FieldMapper.Names fieldNames, FieldDataType fieldDataType, IndexFieldDataCache.Listener listener) {
        return new IndexFieldCache(index, fieldNames, fieldDataType, listener);
    }

    @Override
    public void onRemoval(RemovalNotification<Key, AtomicFieldData> notification) {
        if (notification.getKey() != null) {
            IndexFieldCache indexFieldCache = notification.getKey().indexCache;
            indexFieldCache.listener.onUnload(indexFieldCache.index, indexFieldCache.fieldNames, indexFieldCache.fieldDataType, notification.wasEvicted(), notification.getValue());
        }
    }

    public static class FieldDataWeigher implements Weigher<Key, AtomicFieldData> {

        @Override
        public int weigh(Key key, AtomicFieldData fieldData) {
            int weight = (int) Math.min(fieldData.getMemorySizeInBytes(), Integer.MAX_VALUE);
            return weight == 0 ? 1 : weight;
        }
    }

    /**
     * A specific cache instance for the relevant parameters of it (index, fieldNames, fieldType).
     */
    class IndexFieldCache implements IndexFieldDataCache, SegmentReader.CoreClosedListener {

        final Index index;
        final FieldMapper.Names fieldNames;
        final FieldDataType fieldDataType;
        final Listener listener;

        IndexFieldCache(Index index, FieldMapper.Names fieldNames, FieldDataType fieldDataType, Listener listener) {
            this.index = index;
            this.fieldNames = fieldNames;
            this.fieldDataType = fieldDataType;
            this.listener = listener;
        }

        @Override
        public <FD extends AtomicFieldData, IFD extends IndexFieldData<FD>> FD load(final AtomicReaderContext context, final IFD indexFieldData) throws Exception {
            Key key = new Key(this, context.reader().getCoreCacheKey());
            //noinspection unchecked
            return (FD) cache.get(key, new Callable<AtomicFieldData>() {
                @Override
                public AtomicFieldData call() throws Exception {
                    if (context.reader() instanceof SegmentReader) {
                        ((SegmentReader) context.reader()).addCoreClosedListener(IndexFieldCache.this);
                    }
                    AtomicFieldData fieldData = indexFieldData.loadDirect(context);
                    listener.onLoad(index, indexFieldData.getFieldNames(), fieldDataType, fieldData);
                    return fieldData;
                }
            });
        }

        @Override
        public void onClose(SegmentReader owner) {
            cache.invalidate(new Key(this, owner.getCoreCacheKey()));
        }

        @Override
        public void clear(Index index) {
            for (Key key : cache.asMap().keySet()) {
                if (key.indexCache.index.equals(index)) {
                    cache.invalidate(key);
                }
            }
        }

        @Override
        public void clear(Index index, String fieldName) {
            for (Key key : cache.asMap().keySet()) {
                if (key.indexCache.index.equals(index)) {
                    if (key.indexCache.fieldNames.fullName().equals(fieldName)) {
                        cache.invalidate(key);
                    }
                }
            }
        }

        @Override
        public void clear(Index index, IndexReader reader) {
            cache.invalidate(new Key(this, reader.getCoreCacheKey()));
        }
    }

    public static class Key {
        public final IndexFieldCache indexCache;
        public final Object readerKey;

        Key(IndexFieldCache indexCache, Object readerKey) {
            this.indexCache = indexCache;
            this.readerKey = readerKey;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Key key = (Key) o;

            if (!indexCache.equals(key.indexCache)) return false;
            if (!readerKey.equals(key.readerKey)) return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = indexCache.hashCode();
            result = 31 * result + readerKey.hashCode();
            return result;
        }
    }
}
