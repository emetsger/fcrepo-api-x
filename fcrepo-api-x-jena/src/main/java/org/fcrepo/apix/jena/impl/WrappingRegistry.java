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

package org.fcrepo.apix.jena.impl;

import java.net.URI;
import java.util.Collection;

import org.fcrepo.apix.model.WebResource;
import org.fcrepo.apix.model.components.Registry;

/**
 * Internal wrapper around a delegate registry
 *
 * @author apb@jhu.edu
 */
abstract class WrappingRegistry implements Registry {

    Registry delegate;

    void setRegistryDelegate(final Registry delegate) {
        this.delegate = delegate;
    }

    @Override
    public WebResource get(final URI id) {
        return delegate.get(id);
    }

    @Override
    public URI put(final WebResource resource, final boolean asBinary) {
        return delegate.put(resource, asBinary);
    }

    @Override
    public URI put(final WebResource resource) {
        return delegate.put(resource);
    }

    @Override
    public void delete(final URI uri) {
        delegate.delete(uri);
    }

    @Override
    public boolean canWrite() {
        return delegate.canWrite();
    }

    @Override
    public Collection<URI> list() {
        return delegate.list();
    }

    @Override
    public boolean contains(final URI id) {
        return delegate.contains(id);
    }

    @Override
    public boolean hasInDomain(final URI uri) {
        return delegate.hasInDomain(uri);
    }

}
