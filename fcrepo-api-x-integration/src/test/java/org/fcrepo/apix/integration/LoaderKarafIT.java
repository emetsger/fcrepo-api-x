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
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.features;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.options.MavenArtifactUrlReference;
import org.ops4j.pax.exam.options.MavenUrlReference;
import org.osgi.framework.BundleContext;

import javax.inject.Inject;

/**
 * @author apb@jhu.edu
 */
@RunWith(PaxExam.class)
public class LoaderKarafIT extends LoaderBaseIT implements KarafServiceIT {

    @Inject
    BundleContext bundleCtx;

    @Override
    public List<Option> additionalKarafConfig() {
        MavenArtifactUrlReference testBundle = maven()
                .groupId("org.fcrepo.apix")
                .artifactId("fcrepo-api-x-test")
                .versionAsInProject();
        final List<Option> options = new ArrayList<>(singletonList(mavenBundle(testBundle)));

        // This test dependency is not in any features files, so we have to add it manually.
        final MavenArtifactUrlReference jsoup = maven().groupId("org.jsoup")
                .artifactId("jsoup")
                .versionAsInProject();

        final MavenUrlReference apixRepo =
                maven().groupId("org.fcrepo.apix")
                        .artifactId("fcrepo-api-x-karaf").versionAsInProject()
                        .classifier("features").type("xml");

        options.addAll(Arrays.asList(mavenBundle(jsoup), features(apixRepo, "fcrepo-api-x-loader")));

        return options;
    }

    @Override
    protected void update() throws Exception {
        new KarafUpdater().update(bundleCtx);
    }
}
