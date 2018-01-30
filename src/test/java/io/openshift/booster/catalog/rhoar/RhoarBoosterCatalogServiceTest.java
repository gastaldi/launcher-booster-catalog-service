/*
 * Copyright 2017 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Eclipse Public License version 1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package io.openshift.booster.catalog.rhoar;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.arquillian.smart.testing.rules.git.server.GitServer;
import org.assertj.core.api.JUnitSoftAssertions;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ProvideSystemProperty;

import io.openshift.booster.catalog.LauncherConfiguration;

public class RhoarBoosterCatalogServiceTest {

    @ClassRule
    public static GitServer gitServer = GitServer
            .fromBundle("gastaldi-booster-catalog","repos/custom-catalogs/gastaldi-booster-catalog.bundle")
            .usingPort(8765)
            .create();

    @Rule
    public final ProvideSystemProperty launcherProperties = new ProvideSystemProperty(LauncherConfiguration.PropertyName.LAUNCHER_BOOSTER_CATALOG_REPOSITORY, "http://localhost:8765/booster-catalog/");

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    private static RhoarBoosterCatalogService defaultService;

    @Test
    public void testProcessMetadata() throws Exception {
        RhoarBoosterCatalogService service = buildDefaultCatalogService();
        Path metadataFile = Paths.get(getClass().getResource("metadata.json").toURI());
        Map<String, Mission> missions = new HashMap<>();
        Map<String, Runtime> runtimes = new HashMap<>();

        service.processMetadata(metadataFile, missions, runtimes);

        softly.assertThat(missions).hasSize(5);
        softly.assertThat(runtimes).hasSize(3);
        softly.assertThat(runtimes.get("wildfly-swarm").getIcon()).isNotEmpty();
        softly.assertThat(missions.get("configmap").getDescription()).isNotEmpty();
    }

    @Test
    public void testVertxVersions() throws Exception {
        RhoarBoosterCatalogService service = buildDefaultCatalogService();
        service.index().get();

        Set<Version> versions = service.getVersions(new Mission("rest-http"), new Runtime("vert.x"));

        softly.assertThat(versions).hasSize(2);
    }

    @Test
    public void testGetBoosterRuntime() throws Exception {
        RhoarBoosterCatalogService service = buildDefaultCatalogService();
        service.index().get();
        Runtime vertx = new Runtime("vert.x");

        Collection<RhoarBooster> boosters = service.getBoosters(BoosterPredicates.runtimes(vertx));

        softly.assertThat(boosters.size()).isGreaterThan(0);
    }

    @Test
    public void testGetMissionByRuntime() throws Exception {
        RhoarBoosterCatalogService service = buildDefaultCatalogService();
        service.index().get();
        Runtime vertx = new Runtime("vert.x");

        Set<Mission> missions = service.getMissions(BoosterPredicates.runtimes(vertx));

        softly.assertThat(missions.size()).isGreaterThan(0);
    }

    @Test
    public void testGetRuntimes() throws Exception {
        RhoarBoosterCatalogService service = buildDefaultCatalogService();
        service.index().get();

        softly.assertThat(service.getRuntimes()).contains(new Runtime("vert.x"));
    }

    @Test
    public void testFilter() throws Exception {
        RhoarBoosterCatalogService service = new RhoarBoosterCatalogService.Builder()
                .catalogRepository("http://localhost:8765/gastaldi-booster-catalog").catalogRef("vertx_two_versions")
                .filter(b -> b.getRuntime().getId().equals("vert.x")).build();

        service.index().get();

        softly.assertThat(service.getRuntimes()).containsOnly(new Runtime("vert.x"));
    }

    private RhoarBoosterCatalogService buildDefaultCatalogService() {
        if (defaultService == null) {
            defaultService = new RhoarBoosterCatalogService.Builder()
                    .catalogRepository("http://localhost:8765/gastaldi-booster-catalog").catalogRef("vertx_two_versions")
                    .build();
        }
        return defaultService;
    }
}