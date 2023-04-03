package org.matsim.stuttgart.analysis;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.ReflectiveConfigGroup;
import org.matsim.core.config.groups.ControlerConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.controler.events.AfterMobsimEvent;
import org.matsim.core.controler.events.BeforeMobsimEvent;
import org.matsim.core.controler.listener.AfterMobsimListener;
import org.matsim.core.controler.listener.BeforeMobsimListener;
import org.matsim.core.utils.collections.Tuple;

import javax.inject.Inject;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class TripAnalyzerModule extends AbstractModule {

    private static final Logger log = LogManager.getLogger(TripAnalyzerModule.class);

    @Override
    public void install() {

        addControlerListenerBinding().to(MobsimHandler.class);
    }

    @SuppressWarnings("unused")
    public static class PrinterConfigGroup extends ReflectiveConfigGroup {

        public static String GROUP_NAME = "modesPrinter";

        private int[] distanceClasses = new int[0];
        private String[] modes = new String[0];
        private Predicate<Id<Person>> personFilter = id -> true;
        private String modalShareId = "";
        private String modalDistanceShareId = "";
        private Path tokenDirectory = Paths.get("");
        private Path credentials = Paths.get("");

        @StringGetter("modalShareSpreadsheetId")
        public String getModalShareId() {
            return modalShareId;
        }

        @StringGetter("modalDistanceShareSpreadsheetId")
        public String getModalDistanceShareId() {
            return modalDistanceShareId;
        }

        @StringGetter("tokenDirectory")
        public String getTokenDirectoryAsString() {
            return tokenDirectory.toString();
        }

        @StringGetter("credentialsPath")
        public String getCredentialsAsString() {
            return credentials.toString();
        }

        @StringSetter("modalShareSpreadsheetId")
        public PrinterConfigGroup setModalShareId(String modalShareId) {
            this.modalShareId = modalShareId;
            return this;
        }

        @StringSetter("modalDistanceShareSpreadsheetId")
        public PrinterConfigGroup setModalDistanceShareId(String modalDistanceShareId) {
            this.modalDistanceShareId = modalDistanceShareId;
            return this;
        }

        @StringSetter("tokenDirectory")
        public PrinterConfigGroup setTokenDirectory(String tokenDirectory) {
            this.tokenDirectory = Paths.get(tokenDirectory);
            return this;
        }

        @StringSetter("credentialsPath")
        public PrinterConfigGroup setCredentials(String credentials) {
            this.credentials = Paths.get(credentials);
            return this;
        }

        @StringSetter("modes")
        public void setModes(String modes) {
            setModes(StringUtils.split(modes, ","));
        }

        @StringSetter("distanceClasses")
        public void setDistanceClasses(String classes) {
            var distanceClasses = Arrays.stream(StringUtils.split(classes, ","))
                    .map(String::trim)
                    .mapToInt(Integer::parseInt)
                    .toArray();

            setDistanceClasses(distanceClasses);
        }

        @StringGetter("modes")
        public String getModesAsString() {
            return String.join(",", modes);
        }
        @StringGetter("distanceClasses")
        public String getDistanceClassesAsString() {
            return StringUtils.join(",", distanceClasses);
        }

        public int[] getDistanceClasses() {
            return distanceClasses;
        }

        public String[] getModes() {
            return modes;
        }

        public PrinterConfigGroup setDistanceClasses(int[] distanceClasses) {
            this.distanceClasses = distanceClasses;
            return this;
        }

        public PrinterConfigGroup setModes(String[] modes) {
            this.modes = modes;
            return this;
        }

        public PrinterConfigGroup setPersonFilter(Predicate<Id<Person>> personFilter) {
            this.personFilter = personFilter;
            return this;
        }

        public Predicate<Id<Person>> getPersonFilter() { return personFilter; }

        public Path getTokenDirectory() {
            return tokenDirectory;
        }

        public Path getCredentials() {
            return credentials;
        }

        public PrinterConfigGroup() {
            super(GROUP_NAME);
        }

        public boolean hasGoogleIdForModalShare() {
            return !StringUtils.isBlank(this.modalShareId);
        }

        public boolean hasGoogleIdForModalDistanceShare() {
            return !StringUtils.isBlank(this.modalDistanceShareId);
        }
    }

    private static class MobsimHandler implements BeforeMobsimListener, AfterMobsimListener {

        @Inject
        private EventsManager eventsManager;

        @Inject
        private Network network;

        @Inject
        private OutputDirectoryHierarchy outputDirectoryHierarchy;

        @Inject
        private ControlerConfigGroup controlerConfig;

        @Inject
        private PrinterConfigGroup printerConfigGroup;

        private TripEventHandler handler;

        @Override
        public void notifyBeforeMobsim(BeforeMobsimEvent event) {

            if (event.isLastIteration()) {
                this.handler = new TripEventHandler(network);
                eventsManager.addHandler(this.handler);
            }
        }

        @Override
        public void notifyAfterMobsim(AfterMobsimEvent event) {

            if (event.isLastIteration()) {
                var tripsByPerson = handler.getTripsByPerson();
                var modesSet = Set.of(printerConfigGroup.getModes());
                var filteredTrips = tripsByPerson.entrySet().stream()
                        .filter(entry -> printerConfigGroup.getPersonFilter().test(entry.getKey()))
                        .map(Map.Entry::getValue)
                        .flatMap(Collection::stream)
                        .filter(trip -> trip.getStartCoord() != null && trip.getEndCoord() != null)
                        .filter(trip -> modesSet.contains(trip.getMode()))
                        .collect(Collectors.toSet());

                var modalShareHeader = new String[]{"mode", "count", "share"};
                var modalShare = modalShare2(filteredTrips, printerConfigGroup.getModes());


                log.info("----------------------- Trip Analyzer Module - Modal Share ---------------------------");
                new TabularLogger(modalShareHeader).write(modalShare);
                new CSVWriter(Paths.get(outputDirectoryHierarchy.getIterationFilename(event.getIteration(), "modal-share.csv")), modalShareHeader).write(modalShare);

                if (printerConfigGroup.hasGoogleIdForModalShare()) {
                    new GoogleSheetsWriter(
                            printerConfigGroup.getModalShareId(),
                            controlerConfig.getRunId(),
                            printerConfigGroup.getTokenDirectory(),
                            printerConfigGroup.getCredentials(),
                            modalShareHeader)
                            .write(modalShare);
                }

                var modalDistanceShareHeader = new String[]{"mode", "distance", "count", "share"};
                var modalDistanceShare = modalDistanceShare2(filteredTrips, printerConfigGroup.modes, printerConfigGroup.distanceClasses);

                log.info("----------------------- Trip Analyzer Module - Modal Distance Share ---------------------------");
                new TabularLogger(modalDistanceShareHeader).write(modalDistanceShare);
                new CSVWriter(Paths.get(outputDirectoryHierarchy.getIterationFilename(event.getIteration(), "modal-distance-share.csv")), modalDistanceShareHeader).write(modalDistanceShare);

                if (printerConfigGroup.hasGoogleIdForModalDistanceShare()) {
                    new GoogleSheetsWriter(
                            printerConfigGroup.getModalDistanceShareId(),
                            controlerConfig.getRunId(),
                            printerConfigGroup.getTokenDirectory(),
                            printerConfigGroup.getCredentials(),
                            modalDistanceShareHeader)
                            .write(modalDistanceShare);
                }
            }
        }

        private List<List<Object>> modalShare2(Set<TripEventHandler.Trip> filteredEntries, String[] modes) {

            var modalSplit = filteredEntries.stream()
                    .map(TripEventHandler.Trip::getMode)
                    .collect(Collectors.toMap(mode -> mode, mode -> 1, Integer::sum));

            var totalNumberOfTrips = modalSplit.values().stream()
                    .mapToInt(value -> value)
                    .sum();

            List<List<Object>> result = new ArrayList<>();
            for (var mode : modes) {
                var value = modalSplit.getOrDefault(mode, 0);
                var share = (double) value / totalNumberOfTrips;
                result.add(
                        List.of(mode, value, share)
                );
            }

            return result;
        }

        private List<List<Object>> modalDistanceShare2(Set<TripEventHandler.Trip> filteredEntries, String[] modes, int[] distanceClasses) {

            var distancesByMode = filteredEntries.stream()
                    .map(trip -> Tuple.of(trip.getMode(), getDistanceKey(trip.getDistance(), distanceClasses)))
                    .collect(Collectors.groupingBy(Tuple::getFirst, Collectors.toMap(Tuple::getSecond, t -> 1, Integer::sum, Object2IntOpenHashMap::new)));

            var numberOfTripsPerDistanceClass = filteredEntries.stream()
                    .map(trip -> getDistanceKey(trip.getDistance(), distanceClasses))
                    .collect(Collectors.toMap(distance -> distance, distance -> 1, Integer::sum, Object2IntOpenHashMap::new));

            List<List<Object>> values = new ArrayList<>();

            //wrap values into list list
            for (var mode : modes) {

                // get the distanceClasses for mode
                var distances = distancesByMode.getOrDefault(mode, new Object2IntOpenHashMap<>());

                for (var distanceClass : convertToString(distanceClasses)) {

                    var totalNumberForDistance = numberOfTripsPerDistanceClass.containsKey(distanceClass) ? numberOfTripsPerDistanceClass.getInt(distanceClass) : 0;
                    var distanceAndModeValue = distances.containsKey(distanceClass) ? distances.getInt(distanceClass) : 0;  //distances.getOrDefault(distanceClass, 0);
                    var calculatedShare = (double) distanceAndModeValue / totalNumberForDistance;
                    // google spreadsheets doesn't allow for NaN values. In case we have 0 observed and 0 overall trips
                    // in a category we decide to set the share to 0.
                    var share = Double.isNaN(calculatedShare) ? 0 : calculatedShare;

                    values.add(List.of(mode, distanceClass, distanceAndModeValue, share));
                }
            }

            return values;
        }

        private String getDistanceKey(double distance, int[] distanceClasses) {

            for (int i = 0; i < distanceClasses.length; i++) {
                var upperBound = distanceClasses[i];

                if (distance < upperBound) {

                    var lowerBound = i == 0 ? 0 : distanceClasses[i - 1];
                    return lowerBound + " to " + upperBound;
                }
            }
            // if we reach here we are bigger than the biggest distance class
            return "> " + distanceClasses[distanceClasses.length - 1];
        }

        private List<String> convertToString(int[] distanceClasses) {
            List<String> result = new ArrayList<>();
            for (int i = 0; i < distanceClasses.length; i++) {
                var upperBound = distanceClasses[i];
                var lowerBound = i == 0 ? 0 : distanceClasses[i - 1];
                result.add(lowerBound + " to " + upperBound);
            }
            result.add("> " + distanceClasses[distanceClasses.length - 1]);
            return result;
        }
    }
}
