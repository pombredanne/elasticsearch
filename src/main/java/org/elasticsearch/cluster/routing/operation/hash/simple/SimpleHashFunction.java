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

package org.elasticsearch.cluster.routing.operation.hash.simple;

import org.elasticsearch.cluster.routing.operation.hash.HashFunction;

/**
 * This class implements a simple hash function based on Java Build-In {@link Object#hashCode()}
 */
public class SimpleHashFunction implements HashFunction {

    @Override
    public int hash(String routing) {
        return routing.hashCode();
    }

    @Override
    public int hash(String type, String id) {
        return type.hashCode() + 31 * id.hashCode();
    }
}
