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

package org.elasticsearch.common.util.concurrent;

import org.elasticsearch.ElasticSearchIllegalStateException;
import org.elasticsearch.ElasticSearchInterruptedException;
import org.elasticsearch.common.metrics.CounterMetric;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;

/**
 */
public class EsAbortPolicy implements XRejectedExecutionHandler {

    private final CounterMetric rejected = new CounterMetric();

    @Override
    public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
        if (r instanceof AbstractRunnable) {
            if (((AbstractRunnable) r).isForceExecution()) {
                BlockingQueue<Runnable> queue = executor.getQueue();
                if (!(queue instanceof SizeBlockingQueue)) {
                    throw new ElasticSearchIllegalStateException("forced execution, but expected a size queue");
                }
                try {
                    ((SizeBlockingQueue) queue).forcePut(r);
                } catch (InterruptedException e) {
                    throw new ElasticSearchInterruptedException(e.getMessage(), e);
                }
                return;
            }
        }
        rejected.inc();
        StringBuilder sb = new StringBuilder("rejected execution ");
        if (executor.isShutdown()) {
            sb.append("(shutting down) ");
        } else {
            if (executor.getQueue() instanceof SizeBlockingQueue) {
                sb.append("(queue capacity ").append(((SizeBlockingQueue) executor.getQueue()).capacity()).append(") ");
            }
        }
        sb.append("on ").append(r.toString());
        throw new EsRejectedExecutionException(sb.toString());
    }

    @Override
    public long rejected() {
        return rejected.count();
    }
}
