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

import static java.util.Collections.singletonList;

import static org.ops4j.pax.exam.CoreOptions.maven;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.editConfigurationFilePut;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.features;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.options.MavenArtifactUrlReference;
import org.ops4j.pax.exam.options.MavenUrlReference;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.osgi.framework.BundleContext;

import javax.inject.Inject;

/**
 * @author apb@jhu.edu
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class ServiceIndexingKarafIT extends ServiceIndexingBaseIT implements KarafServiceIT {

    @Inject
    BundleContext bundleCtx;

    @Override
    public List<Option> additionalKarafConfig() {
        final MavenArtifactUrlReference testBundle = maven()
                .groupId("org.fcrepo.apix")
                .artifactId("fcrepo-api-x-test")
                .versionAsInProject();

        final MavenUrlReference apixRepo =
                maven().groupId("org.fcrepo.apix")
                        .artifactId("fcrepo-api-x-karaf").versionAsInProject()
                        .classifier("features").type("xml");

        final ArrayList<Option> options = new ArrayList<>(singletonList(mavenBundle(testBundle)));

        options.addAll(Arrays.asList(
                editConfigurationFilePut("etc/system.properties", "reindexing.dynamic.test.port", System.getProperty(
                        "reindexing.dynamic.test.port")),
                deployFile("cfg/org.fcrepo.camel.reindexing.cfg"),
                deployFile("cfg/org.fcrepo.camel.service.activemq.cfg"),
                deployFile("cfg/org.fcrepo.camel.service.cfg"),
                deployFile("cfg/org.fcrepo.apix.indexing.cfg"),
                features(apixRepo, "fcrepo-api-x-indexing")));

        return options;
    }

    @Override
    protected void update() throws Exception {
        new KarafUpdater().update(bundleCtx);
    }
}
