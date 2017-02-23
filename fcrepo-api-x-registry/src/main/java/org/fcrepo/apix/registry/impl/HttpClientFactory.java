/*
 * Licensed to DuraSpace under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.
 *
 * DuraSpace licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.fcrepo.apix.registry.impl;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.inject.Named;

/**
 * Creates configured instances of HttpClients.
 * <p>
 * Useful to containers like blueprint or sping for creating HttpClients for wiring.
 * </p>
 * <p>
 * TODO; Expose more useful params.
 * </p>
 *
 * @author apb@jhu.edu
 */
@Named
@Configuration
public class HttpClientFactory {

    private int connectTimeout = 1000;

    private int socketTimeout = 1000;

    /**
     * Timeout in miliseconds.
     *
     * @param timeout milliseconds.
     */
    public void setConnectTimeout(final int timeout) {
        this.connectTimeout = timeout;
    }

    /**
     * Socket timeout in milliseconds.
     *
     * @param timeout milliseconds.
     */
    public void setSocketTimeout(final int timeout) {
        this.socketTimeout = timeout;
    }

    /**
     * Construct a new HttpClient.
     *
     * @return HttpClient impl.
     */
    @Bean
    public CloseableHttpClient getClient() {
        final RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(connectTimeout)
                .setSocketTimeout(socketTimeout).build();

        return HttpClientBuilder.create().setDefaultRequestConfig(config).build();
    }
}
