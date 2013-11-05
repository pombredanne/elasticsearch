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

package org.elasticsearch.action.admin.cluster.settings;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.support.master.AcknowledgedRequestBuilder;
import org.elasticsearch.client.ClusterAdminClient;
import org.elasticsearch.client.internal.InternalClusterAdminClient;
import org.elasticsearch.common.settings.Settings;

import java.util.Map;

/**
 * Builder for a cluster update settings request
 */
public class ClusterUpdateSettingsRequestBuilder extends AcknowledgedRequestBuilder<ClusterUpdateSettingsRequest, ClusterUpdateSettingsResponse, ClusterUpdateSettingsRequestBuilder> {

    public ClusterUpdateSettingsRequestBuilder(ClusterAdminClient clusterClient) {
        super((InternalClusterAdminClient) clusterClient, new ClusterUpdateSettingsRequest());
    }

    /**
     * Sets the transient settings to be updated. They will not survive a full cluster restart
     */
    public ClusterUpdateSettingsRequestBuilder setTransientSettings(Settings settings) {
        request.transientSettings(settings);
        return this;
    }

    /**
     * Sets the transient settings to be updated. They will not survive a full cluster restart
     */
    public ClusterUpdateSettingsRequestBuilder setTransientSettings(Settings.Builder settings) {
        request.transientSettings(settings);
        return this;
    }

    /**
     * Sets the source containing the transient settings to be updated. They will not survive a full cluster restart
     */
    public ClusterUpdateSettingsRequestBuilder setTransientSettings(String settings) {
        request.transientSettings(settings);
        return this;
    }

    /**
     * Sets the transient settings to be updated. They will not survive a full cluster restart
     */
    public ClusterUpdateSettingsRequestBuilder setTransientSettings(Map settings) {
        request.transientSettings(settings);
        return this;
    }

    /**
     * Sets the persistent settings to be updated. They will get applied cross restarts
     */
    public ClusterUpdateSettingsRequestBuilder setPersistentSettings(Settings settings) {
        request.persistentSettings(settings);
        return this;
    }

    /**
     * Sets the persistent settings to be updated. They will get applied cross restarts
     */
    public ClusterUpdateSettingsRequestBuilder setPersistentSettings(Settings.Builder settings) {
        request.persistentSettings(settings);
        return this;
    }

    /**
     * Sets the source containing the persistent settings to be updated. They will get applied cross restarts
     */
    public ClusterUpdateSettingsRequestBuilder setPersistentSettings(String settings) {
        request.persistentSettings(settings);
        return this;
    }

    /**
     * Sets the persistent settings to be updated. They will get applied cross restarts
     */
    public ClusterUpdateSettingsRequestBuilder setPersistentSettings(Map settings) {
        request.persistentSettings(settings);
        return this;
    }

    @Override
    protected void doExecute(ActionListener<ClusterUpdateSettingsResponse> listener) {
        ((ClusterAdminClient) client).updateSettings(request, listener);
    }
}
