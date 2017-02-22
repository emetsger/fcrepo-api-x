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
package org.fcrepo.apix.integration;

import org.apache.commons.io.FilenameUtils;
import org.apache.http.HttpStatus;
import org.fcrepo.apix.model.WebResource;
import org.fcrepo.client.FcrepoClient;
import org.fcrepo.client.FcrepoOperationFailedException;
import org.fcrepo.client.FcrepoResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.concurrent.Callable;

import static org.apache.commons.io.FilenameUtils.getBaseName;

/**
 * @author apb@jhu.edu
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public interface BaseIT {

    Logger _log = LoggerFactory.getLogger(BaseIT.class);

    String apixBaseURI = String.format("http://localhost:%s", System.getProperty(
            "apix.dynamic.test.port", "32080"));

    String fcrepoBaseURI = String.format("http://localhost:%s/%s/rest/", System.getProperty(
            "fcrepo.dynamic.test.port", "8080"), System.getProperty("fcrepo.cxtPath", "fcrepo"));

    File testResources = new File(System.getProperty("project.basedir"), "src/test/resources");

    FcrepoClient client = FcrepoClient.client().throwExceptionOnFailure().build();

    URI testContainer = URI.create(System.getProperty("test.container", ""));

    URI objectContainer = URI.create(testContainer + "/objects");

    URI extensionContainer = URI.create(System.getProperty("registry.extension.container", ""));

    URI serviceContainer = URI.create(System.getProperty("registry.service.container", ""));

    URI ontologyContainer = URI.create(System.getProperty("registry.ontology.container", ""));

    String testClassName();

    String testMethodName();


    /**
     * Get a test resource from test-classes
     *
     * @param path the resource path relative to {@link #testResources}
     * @return the resulting WebResource
     */
    default WebResource testResource(String path) {
        return testResource(path, "text/turtle");
    }

    /**
     * Get a test resource from test-classes
     *
     * @param path the resource path relative to {@link #testResources}
     * @return the resulting WebResource
     */
    default WebResource testResource(String path, String contentType) {
        final File file = new File(testResources, path);
        try {
            return WebResource.of(new FileInputStream(file), contentType, URI.create(FilenameUtils.getBaseName(
                    path)), null);
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    default URI postFromTestResource(final String filePath, final URI intoContainer) throws Exception {

        try (final WebResource object = testResource(filePath);
             final FcrepoResponse response = client.post(intoContainer)
                     .body(object.representation(), object.contentType())
                     .slug(String.format("%s_%s", testMethodName(), getBaseName(filePath)))
                     .perform()) {
            return response.getLocation();
        }
    }

    default URI postFromTestResource(final String filePath, final URI intoContainer, final String contentType)
            throws Exception {
        return postFromTestResource(filePath, intoContainer, contentType,
                String.format("%s_%s", testMethodName(), getBaseName(filePath)));
    }

    default URI postFromTestResource(final String filePath, final URI intoContainer,
                                            final String contentType, final String slug) throws Exception {
        try (final WebResource object = testResource(filePath, contentType);
             final FcrepoResponse response = client.post(intoContainer)
                     .body(object.representation(), object.contentType())
                     .slug(slug)
                     .perform()) {
            return response.getLocation();
        }
    }

    default URI postFromStream(final InputStream in, final URI intoContainer, final String contentType,
                                      final String slug) throws Exception {
        try (final WebResource object = WebResource.of(in, contentType);
             final FcrepoResponse response = client.post(intoContainer)
                     .body(object.representation(), object.contentType())
                     .slug(slug)
                     .perform()) {
            return response.getLocation();
        }
    }

    static <T> T attempt(final int times, final Callable<T> it) {

        Throwable caught = null;

        for (int tries = 0; tries < times; tries++) {
            try {
                return it.call();
            } catch (final Throwable e) {
                caught = e;
                try {
                    Thread.sleep(1000);
                    System.out.println(".");
                } catch (final InterruptedException i) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }
        throw new RuntimeException("Failed executing task", caught);
    }

    /**
     * Create all necessary containers for registries, etc.
     * <p>
     * Tests that need functional registries need to do this <code>@BeforeClass</code>, or deploy alternate
     * configuration files for jena registry impls.
     * </p>
     *
     * @throws Exception when something goes wrong
     */
     static void createContainers() throws Exception {

        for (final URI container : Arrays.asList(testContainer, objectContainer, extensionContainer,
                serviceContainer,
                ontologyContainer)) {
            // Add the container, if it doesn't exist.

            attempt(60, () -> {
                try (FcrepoResponse head = client.head(container).perform()) {
                    return true;
                } catch (final FcrepoOperationFailedException e) {
                    if (e.getStatusCode() == HttpStatus.SC_NOT_FOUND) {
                        try (FcrepoResponse response = client.put(container)
                                .perform()) {
                            if (response.getStatusCode() != HttpStatus.SC_CREATED && response
                                    .getStatusCode() != HttpStatus.SC_NO_CONTENT) {
                                _log.info("Could not create container {}, retrying...", container);
                                try {
                                    Thread.sleep(1000);
                                } catch (final InterruptedException i) {
                                    Thread.currentThread().interrupt();
                                }
                            }
                        }
                    }
                }
                return true;
            });
        }
    }

}
