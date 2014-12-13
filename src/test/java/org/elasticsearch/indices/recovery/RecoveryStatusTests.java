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
package org.elasticsearch.indices.recovery;

import com.google.common.collect.Sets;
import org.apache.lucene.store.IndexOutput;
import org.elasticsearch.Version;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.common.transport.LocalTransportAddress;
import org.elasticsearch.index.IndexService;
import org.elasticsearch.index.shard.IndexShard;
import org.elasticsearch.index.store.StoreFileMetaData;
import org.elasticsearch.test.ElasticsearchSingleNodeTest;

import java.io.IOException;
import java.util.Set;
import java.util.regex.Pattern;

/**
 */
public class RecoveryStatusTests extends ElasticsearchSingleNodeTest {
    
    public void testRenameTempFiles() throws IOException {
        IndexService service = createIndex("foo");
        RecoveryState state = new RecoveryState();

        IndexShard indexShard = service.shard(0);
        DiscoveryNode node = new DiscoveryNode("foo", new LocalTransportAddress("bar"), Version.CURRENT);
        RecoveryStatus status = new RecoveryStatus(indexShard, node, state, new RecoveryTarget.RecoveryListener() {
            @Override
            public void onRecoveryDone(RecoveryState state) {
            }

            @Override
            public void onRecoveryFailure(RecoveryState state, RecoveryFailedException e, boolean sendShardFailure) {
            }
        });
        try (IndexOutput indexOutput = status.openAndPutIndexOutput("foo.bar", new StoreFileMetaData("foo.bar", 8), status.store())) {
            indexOutput.writeInt(1);
            IndexOutput openIndexOutput = status.getOpenIndexOutput("foo.bar");
            assertSame(openIndexOutput, indexOutput);
            openIndexOutput.writeInt(1);
        }
        status.removeOpenIndexOutputs("foo.bar");
        Set<String> strings = Sets.newHashSet(status.store().directory().listAll());
        String expectedFile = null;
        for (String file : strings) {
            if (Pattern.compile("recovery[.]\\d+[.]foo[.]bar").matcher(file).matches()) {
                expectedFile = file;
                break;
            }
        }
        assertNotNull(expectedFile);
        status.renameAllTempFiles();
        strings = Sets.newHashSet(status.store().directory().listAll());
        assertTrue(strings.toString(), strings.contains("foo.bar"));
        assertFalse(strings.toString(), strings.contains(expectedFile));
        status.markAsDone();
    }
}
