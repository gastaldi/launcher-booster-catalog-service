package io.fabric8.launcher.booster.catalog.rhoar;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;

import io.fabric8.launcher.booster.catalog.AbstractBoosterCatalogService;
import io.fabric8.launcher.booster.catalog.BoosterFetcher;

public class RhoarBoosterCatalogService extends AbstractBoosterCatalogService<RhoarBooster> implements RhoarBoosterCatalog {

    private static final String METADATA_FILE = "metadata.json";

    private static final Logger logger = Logger.getLogger(RhoarBoosterCatalogService.class.getName());

    protected RhoarBoosterCatalogService(Builder config) {
        super(config);
    }

    @Override
    protected RhoarBooster newBooster(Map<String, Object> data, BoosterFetcher boosterFetcher) {
        return new RhoarBooster(data, boosterFetcher);
    }

    @Override
    public Set<Mission> getMissions() {
        return toMissions(getPrefilteredBoosters());
    }

    @Override
    public Set<Mission> getMissions(Predicate<RhoarBooster> filter) {
        return toMissions(getPrefilteredBoosters().filter(filter));
    }

    @Override
    public Set<Runtime> getRuntimes() {
        return toRuntimes(getPrefilteredBoosters());
    }

    @Override
    public Set<Runtime> getRuntimes(Predicate<RhoarBooster> filter) {
        return toRuntimes(getPrefilteredBoosters().filter(filter));
    }

    @Override
    public Set<Version> getVersions(Predicate<RhoarBooster> filter) {
        return toVersions(getPrefilteredBoosters().filter(filter));
    }

    @Override
    public Set<Version> getVersions(Mission mission, Runtime runtime) {
        return getVersions(BoosterPredicates.withMission(mission).and(BoosterPredicates.withRuntime(runtime)));
    }
    
    @Override
    public Optional<RhoarBooster> getBooster(Mission mission, Runtime runtime, Version version) {
        return getPrefilteredBoosters()
                .filter(BoosterPredicates.withMission(mission))
                .filter(BoosterPredicates.withRuntime(runtime))
                .filter(BoosterPredicates.withVersion(version))
                .findAny();
    }

    private Set<Runtime> toRuntimes(Stream<RhoarBooster> bs) {
        return bs
                .map(RhoarBooster::getRuntime)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private Set<Mission> toMissions(Stream<RhoarBooster> bs) {
        return bs
                .map(RhoarBooster::getMission)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private Set<Version> toVersions(Stream<RhoarBooster> bs) {
        return bs
                .filter(b -> b.getVersion() != null)
                .map(RhoarBooster::getVersion)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    @Override
    protected void indexBoosters(final Path catalogPath, final Set<RhoarBooster> boosters) throws IOException {
        super.indexBoosters(catalogPath, boosters);

        // Update the boosters with the proper info for missions, runtimes and versions
        Map<String, Mission> missions = new HashMap<>();
        Map<String, Runtime> runtimes = new HashMap<>();

        // Read the metadata for missions and runtimes
        Path metadataFile = catalogPath.resolve(METADATA_FILE);
        if (Files.exists(metadataFile)) {
            processMetadata(metadataFile, missions, runtimes);
        }

        for (RhoarBooster booster : boosters) {
            List<String> path = booster.getDescriptor().path;
            if (path.size() >= 1) {
                booster.setMission(missions.computeIfAbsent(path.get(0), Mission::new));
                if (path.size() >= 2) {
                    booster.setRuntime(runtimes.computeIfAbsent(path.get(1), Runtime::new));
                    if (path.size() >= 3) {
                        String versionId = path.get(2);
                        String versionName = booster.getMetadata("version/name", versionId);
                        assert versionName != null;
                        booster.setVersion(new Version(versionId, versionName));
                    }
                }
            }
        }
    }

    /**
     * Process the metadataFile and adds to the specified missions and runtimes
     * maps
     */
    public void processMetadata(Path metadataFile, Map<String, Mission> missions, Map<String, Runtime> runtimes) {
        logger.info(() -> "Reading metadata at " + metadataFile + " ...");

        try (BufferedReader reader = Files.newBufferedReader(metadataFile);
             JsonReader jsonReader = Json.createReader(reader)) {
            JsonObject index = jsonReader.readObject();
            index.getJsonArray("missions").stream().map(JsonObject.class::cast)
                    .map(e -> new Mission(e.getString("id"), e.getString("name"), e.getString("description", null), e.getBoolean("suggested", false)))
                    .forEach(m -> missions.put(m.getId(), m));

            index.getJsonArray("runtimes").stream().map(JsonObject.class::cast)
                    .map(e -> new Runtime(e.getString("id"), e.getString("name"), e.getString("icon", null)))
                    .forEach(r -> runtimes.put(r.getId(), r));
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error while processing metadata " + metadataFile, e);
        }
    }

    public static class Builder extends AbstractBuilder<RhoarBooster, RhoarBoosterCatalogService> {
        @Override
        public RhoarBoosterCatalogService build() {
            return new RhoarBoosterCatalogService(this);
        }
    }
}
