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

import org.apache.camel.CamelContext;
import org.apache.commons.io.IOUtils;
import org.apache.jena.query.QueryExecutionFactory;
import org.fcrepo.apix.model.Extension;
import org.fcrepo.apix.model.components.RoutingFactory;
import org.fcrepo.client.FcrepoResponse;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.ops4j.pax.exam.util.Filter;

import javax.inject.Inject;
import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;

import static org.fcrepo.apix.integration.BaseIT.attempt;
import static org.fcrepo.apix.model.Ontologies.ORE_AGGREGATES;
import static org.fcrepo.apix.model.Ontologies.ORE_DESCRIBES;
import static org.fcrepo.apix.model.Ontologies.RDF_TYPE;
import static org.fcrepo.apix.model.Ontologies.Service.PROP_IS_FUNCTION_OF;
import static org.fcrepo.apix.model.Ontologies.Service.PROP_IS_SERVICE_DOCUMENT_FOR;
import static org.fcrepo.apix.model.Ontologies.Service.PROP_IS_SERVICE_INSTANCE_OF;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author apb@jhu.edu
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public abstract class ServiceIndexingBaseIT extends AbstractServiceTest {

    static final String fusekiBaseURI = String.format("http://localhost:%s/fuseki/", System.getProperty(
            "fcrepo.dynamic.test.port", "8080"));

    static final String tripleDatasetName = "service-index";

    static final String fusekiURI = fusekiBaseURI + tripleDatasetName;

    static final AtomicInteger objectCounter = new AtomicInteger(0);

    static final URI requestURI = URI.create(apixBaseURI);

    @Rule
    public TestName name = new TestName();

    @Inject
    public RoutingFactory routing;

    @Inject
    @Filter("(role=FcrepoServiceIndexer)")
    public CamelContext camelContext;

    @Override
    public String testClassName() {
        return "ServiceIndexingIT";
    }

    @Override
    public String testMethodName() {
        return name.getMethodName();
    }

    @Test
    public void simpleBindingTest() throws Exception {
        final Extension extension = newExtension(name).withScope(Extension.Scope.RESOURCE).create();

        final URI object = createObjectMatching(extension);

        attempt(60, () -> assertSparqlBound(object, extension));
    }

    @Test
    public void addRemoveExtensionTest() throws Exception {
        // Add an extension and object that binds to it
        final Extension extension = newExtension(name).withScope(Extension.Scope.RESOURCE)
                .withDifferentiator("first").create();
        final URI object = createObjectMatching(extension);

        // Now add another extension that binds to our object
        final Extension extension2 = newExtension(name).withScope(Extension.Scope.RESOURCE).withDifferentiator("second")
                .bindsTo(extension.bindingClass())
                .create();

        update();
        // Both extensions should be bound
        attempt(60, () -> assertSparqlBound(object, extension));
        attempt(60, () -> assertSparqlBound(object, extension2));

        // Delete the new extension
        client.delete(extension2.uri()).perform();
        update();

        // Now only the first should be bound
        attempt(60, () -> assertSparqlBound(object, extension));
        attempt(60, () -> assertSparqlNotBound(object, extension2));
    }

    @Test
    public void bindObjectTest() throws Exception {
        final Extension extension = newExtension(name).withScope(Extension.Scope.RESOURCE).create();

        // Just post an empty object
        final URI object = client.post(routing.of(requestURI).interceptUriFor(objectContainer)).perform().getUrl();

        // Make sure the extension is not in service doc.
        attempt(60, () -> assertSparqlNotBound(object, extension));

        // Now give it a type that matches
        client.patch(object).body(
                IOUtils.toInputStream(
                        String.format("INSERT {<> <%s> <%s> .} WHERE {}",
                                RDF_TYPE, extension.bindingClass()), "UTF-8")).perform();

        // NOW the extension should be in the service doc.
        attempt(60, () -> assertSparqlBound(object, extension));

        // Delete the matching type
        client.patch(object).body(
                IOUtils.toInputStream(
                        String.format("DELETE {<> <%s> <%s> .} WHERE {}",
                                RDF_TYPE, extension.bindingClass()), "UTF-8")).perform();

        // Service should disappear from the service doc
        attempt(60, () -> assertSparqlNotBound(object, extension));
    }

    URI createObjectMatching(final Extension extension) throws Exception {

        try (FcrepoResponse response = client.post(routing.of(requestURI).interceptUriFor(objectContainer))
                .body(IOUtils.toInputStream(
                        String.format("<> a <%s> .", extension.bindingClass()), "utf8"),
                        "text/turtle").slug(objectName()).perform()) {
            return response.getLocation();
        }
    }

    private String objectName() {
        return testMethodName() + "-" + objectCounter.incrementAndGet();
    }

    private String getAskForExtensionQuery(final URI object, final Extension extension) {
        final StringBuilder query = new StringBuilder("ASK { GRAPH ?g {\n");

        query.append(String.format("?doc <%s> <%s>. \n", PROP_IS_SERVICE_DOCUMENT_FOR, object));
        query.append(String.format("?doc <%s> ?aggregation .\n", ORE_DESCRIBES));
        query.append(String.format("?aggregation <%s> ?instance .\n", ORE_AGGREGATES));
        query.append(String.format("?instance <%s> <%s>.\n", PROP_IS_SERVICE_INSTANCE_OF, extension.exposed()
                .exposedService()));

        if (extension.exposed().scope().equals(Extension.Scope.RESOURCE)) {
            query.append(String.format("?instance <%s> <%s>.\n", PROP_IS_FUNCTION_OF, object));
        }

        query.append("}}");

        return query.toString();

    }

    private boolean assertSparqlNotBound(final URI object, final Extension extension) {
        assertFalse(QueryExecutionFactory.sparqlService(fusekiURI, getAskForExtensionQuery(object, extension))
                .execAsk());

        return false;
    }

    private boolean assertSparqlBound(final URI object, final Extension extension) {
        assertTrue(QueryExecutionFactory.sparqlService(fusekiURI, getAskForExtensionQuery(object, extension))
                .execAsk());
        return true;
    }
}
