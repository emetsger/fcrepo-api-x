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

import org.apache.camel.Exchange;
import org.apache.commons.io.IOUtils;
import org.apache.jena.rdf.model.Model;
import org.fcrepo.apix.model.Service;
import org.fcrepo.apix.model.WebResource;
import org.fcrepo.apix.model.components.Registry;
import org.fcrepo.apix.model.components.RoutingFactory;
import org.fcrepo.apix.model.components.ServiceRegistry;
import org.fcrepo.client.FcrepoResponse;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.FormElement;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.ops4j.pax.exam.util.Filter;

import javax.inject.Inject;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.fcrepo.apix.integration.BaseIT.attempt;
import static org.fcrepo.apix.jena.Util.ltriple;
import static org.fcrepo.apix.jena.Util.parse;
import static org.fcrepo.apix.jena.Util.query;
import static org.fcrepo.apix.jena.Util.triple;
import static org.fcrepo.apix.model.Ontologies.Apix.CLASS_EXTENSION;
import static org.fcrepo.apix.model.Ontologies.Apix.PROP_EXPOSES_SERVICE;
import static org.fcrepo.apix.model.Ontologies.Apix.PROP_EXPOSES_SERVICE_AT;
import static org.fcrepo.apix.model.Ontologies.RDF_TYPE;
import static org.fcrepo.apix.model.Ontologies.Service.CLASS_SERVICE_INSTANCE;
import static org.fcrepo.apix.model.Ontologies.Service.PROP_HAS_ENDPOINT;
import static org.fcrepo.apix.model.Ontologies.Service.PROP_IS_SERVICE_INSTANCE_OF;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author apb@jhu.edu
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public abstract class LoaderBaseIT extends AbstractServiceTest {
    static final String LOADER_URI = "http://127.0.0.1:" + System.getProperty(
            "loader.dynamic.test.port") + "/load";

    final URI SERVICE_MINIMAL = URI.create("http://example.org/LoaderIT/minimal");

    final URI SERVICE_FULL = URI.create("http://example.org/LoaderIT/full");

    final URI SERVICE_ONT = URI.create("http://example.org/LoaderIT/ont");

    final URI REQUEST_URI = URI.create(apixBaseURI);

    final AtomicReference<Object> optionsResponse = new AtomicReference<>();

    final AtomicReference<Object> serviceResponse = new AtomicReference<>();

    @Rule
    public TestName name = new TestName();

    @Inject
    @Filter("(org.fcrepo.apix.registry.role=default)")
    Registry repository;

    @Inject
    ServiceRegistry serviceRegistry;

    @Override
    public String testClassName() {
        return "LoaderIT";
    }

    @Override
    public String testMethodName() {
        return name.getMethodName();
    }

    @Inject
    RoutingFactory routing;

    @Before
    public void setUp() {

        onServiceRequest(ex -> {
            ex.setOut(ex.getIn());
            if ("OPTIONS".equals(ex.getIn().getHeader(Exchange.HTTP_METHOD))) {
                ex.getOut().setBody(optionsResponse.get());
            } else if ("GET".equals(ex.getIn().getHeader(Exchange.HTTP_METHOD))) {
                ex.getOut().setBody(serviceResponse.get());
            } else {
                ex.getOut().setHeader(Exchange.HTTP_RESPONSE_CODE, 405);
            }
        });
    }

    @Test
    public void htmlMinimalTest() throws Exception {

        final String SERVICE_RESPONSE_BODY = "BODY";

        optionsResponse.set(IOUtils.toString(testResource("objects/options_LoaderIT_minimal.ttl").representation(),
                "utf8"));
        serviceResponse.set(SERVICE_RESPONSE_BODY);

        final Document html = attempt(60, () -> Jsoup.connect(LOADER_URI).method(Connection.Method.GET).timeout(1000)
                .execute().parse());
        final FormElement form = ((FormElement) html.getElementById("uriForm"));
        form.getElementById("uri").val(serviceEndpoint);

        final Connection.Response response = form.submit().ignoreHttpErrors(true).followRedirects(false).execute();
        update();

        assertEquals("OPTIONS", requestToService.getHeader(Exchange.HTTP_METHOD));
        assertEquals(303, response.statusCode());
        assertNotNull(response.header("Location"));

        // Verify that extension works!

        // Get the intercept/proxy URI for a fedora container
        final URI container = routing.of(REQUEST_URI).interceptUriFor(objectContainer);

        // Deposit an object into the container
        final URI deposited = client.post(container).slug("LoaderIT_htmlMinimalTest")
                .body(IOUtils.toInputStream("<> a <test:LoaderIT#minimal> .", "utf8"), "text/turtle")
                .perform().getLocation();

        // Get the service discovery document
        final URI discoveryDoc = client.options(deposited).perform().getLinkHeaders("service").get(0);

        // Invoke the "minimal" service, and verify that the response body is as expected
        final String body = attempt(10, () -> IOUtils.toString(
                client.get(serviceEndpoints(discoveryDoc).get(SERVICE_MINIMAL)).perform()
                        .getBody(), "utf8"));
        assertEquals(SERVICE_RESPONSE_BODY, body);
    }

    @Test
    public void definedServiceTest() throws Exception {
        final String SERVICE_RESPONSE_BODY = "BODY";

        optionsResponse.set(IOUtils.toString(testResource("objects/options_LoaderIT_full.ttl").representation(),
                "utf8"));
        serviceResponse.set(SERVICE_RESPONSE_BODY);

        // Now add the extension
        attempt(60, () -> textPost(LOADER_URI, serviceEndpoint)).getHeaderValue("Location");
        update();

        // Verify that extension works!

        // Get the intercept/proxy URI for a fedora container
        final URI container = routing.of(REQUEST_URI).interceptUriFor(objectContainer);

        // Deposit an object into the container
        final URI deposited = client.post(container).slug("LoaderIT_definedServiceTest")
                .body(IOUtils.toInputStream("<> a <test:LoaderIT#full> .", "utf8"), "text/turtle")
                .perform().getLocation();

        // Get the service discovery document
        final URI discoveryDoc = client.options(deposited).perform().getLinkHeaders("service").get(0);

        // Invoke the "minimal" service, and verify that the response body is as expected
        final String body = attempt(10, () -> IOUtils.toString(
                client.get(serviceEndpoints(discoveryDoc).get(SERVICE_FULL)).perform()
                        .getBody(), "utf8"));
        assertEquals(SERVICE_RESPONSE_BODY, body);

    }

    @Test
    public void ontologyEmbeddedExtensionTest() throws Exception {
        final String SERVICE_RESPONSE_BODY = "BODY";
        final String TEST_TYPE = "test:type";

        optionsResponse.set(IOUtils.toString(testResource("objects/options_LoaderIT_ont.ttl").representation(),
                "utf8"));
        serviceResponse.set(SERVICE_RESPONSE_BODY);

        // Now add the extension
        final String origLocation = attempt(60, () -> textPost(LOADER_URI, serviceEndpoint)).getHeaderValue(
                "Location");
        update();

        // Verify that extension works!

        // Get the intercept/proxy URI for a fedora container
        final URI container = routing.of(REQUEST_URI).interceptUriFor(objectContainer);

        // Deposit an object into the container, as a text/plain binary
        final URI deposited = client.post(container).slug("LoaderIT_" + name.getMethodName())
                .body(IOUtils.toInputStream("THIS IS TEXT", "utf8"), "text/plain")
                .perform().getLocation();

        // Get the service discovery document
        final URI discoveryDoc = client.options(deposited).perform().getLinkHeaders("service").get(0);

        // Invoke the "minimal" service, and verify that the response body is as expected
        final String body = attempt(10, () -> {
            final URI url = serviceEndpoints(discoveryDoc).get(SERVICE_ONT);
            return IOUtils.toString(
                    client.get(url).perform()
                            .getBody(), "utf8");
        });
        assertEquals(SERVICE_RESPONSE_BODY, body);

        // Now check that we can update it

        // We should be able to re-load it without issue.
        assertEquals(origLocation, textPost(LOADER_URI, serviceEndpoint).getHeaderValue("Location"));

        // Now update it without issue
        optionsResponse.set(optionsResponse.get() + triple("", RDF_TYPE, TEST_TYPE));
        final FcrepoResponse response = textPost(LOADER_URI, serviceEndpoint);
        assertEquals(origLocation, response.getHeaderValue("Location"));

        // Now assure that our new triple is present in its representation
        IOUtils.toString(response.getBody(), "utf8").contains(TEST_TYPE);
    }

    @Test
    public void extensionUpdateTest() throws Exception {

        final String TEST_TYPE = "test:type";
        final String SERVICE_CANONICAL = "test:" + name.getMethodName();
        final URI SERVICE_CANONICAL_URI = URI.create(SERVICE_CANONICAL);
        final String EXPOSED_AT = SERVICE_CANONICAL;

        optionsResponse.set(triple("", RDF_TYPE, CLASS_EXTENSION) +
                ltriple("", PROP_EXPOSES_SERVICE_AT, EXPOSED_AT) +
                triple("", PROP_EXPOSES_SERVICE, SERVICE_CANONICAL));

        // Now add the extension twice, making note of its URI
        final String uri = attempt(60, () -> textPost(LOADER_URI, serviceEndpoint)).getHeaderValue("Location");
        update();

        // First, force an update so that we're not at the whim of the asynchronous updater. Then load the extension
        // for the second time.
        update();
        textPost(LOADER_URI, serviceEndpoint);

        // Now alter the content of the available extension doc, the loader should slurp up the
        // changes
        optionsResponse.set(optionsResponse.get() + triple("", RDF_TYPE, TEST_TYPE));
        final FcrepoResponse response = textPost(LOADER_URI, serviceEndpoint);

        // Make sure the extension document URI didn't change (i.e. we're working with one
        // persisted extension doc, rather than creating a new one each time.
        assertEquals(uri, response.getHeaderValue("Location"));

        // Now, verify that the extension doc has our new statement
        IOUtils.toString(response.getBody(), "utf8").contains(TEST_TYPE);

        update();

        // Make sure we only have ONE entry in the service registry for our service
        assertEquals(1, countServiceRegistryEntries(SERVICE_CANONICAL_URI));
    }

    private final FcrepoResponse textPost(final String uri, final String content) throws Exception {

        final String body = content;

        return client.post(URI.create(uri)).body(new ByteArrayInputStream(body.getBytes()),
                "text/plain").perform();
    }

    private long countServiceRegistryEntries(final URI canonicalURI) {
        return serviceRegistry.list().stream()
                .map(serviceRegistry::getService)
                .map(Service::canonicalURI)
                .filter(u -> canonicalURI.equals(u))
                .count();
    }

    private Map<URI, URI> serviceEndpoints(final URI discoveryDoc) throws Exception {
        try (WebResource resource = repository.get(discoveryDoc)) {
            final Model doc = parse(resource);

            final String sparql = "CONSTRUCT { ?endpoint <test:/endpointFor> ?service . } WHERE { " +
                    String.format("?serviceInstance <%s> <%s> . ", RDF_TYPE, CLASS_SERVICE_INSTANCE) +
                    String.format("?serviceInstance <%s> ?service . ", PROP_IS_SERVICE_INSTANCE_OF) +
                    String.format("?serviceInstance <%s> ?endpoint . ", PROP_HAS_ENDPOINT) +
                    "}";

            return query(sparql, doc).collect(Collectors.toMap(
                    t -> URI.create(t.getObject().getURI()),
                    t -> URI.create(t.getSubject().getURI())));
        }
    }
}
