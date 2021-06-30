package org.matsim.stuttgart.analysis;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.groups.ControlerConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.controler.events.AfterMobsimEvent;
import org.matsim.core.controler.events.BeforeMobsimEvent;
import org.matsim.core.controler.listener.AfterMobsimListener;
import org.matsim.core.controler.listener.BeforeMobsimListener;
import org.matsim.core.utils.collections.Tuple;

import javax.inject.Inject;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class TripAnalyzerModule extends AbstractModule {

    private static final Logger log = LogManager.getLogger(TripAnalyzerModule.class);

    private final HandlerConfig handlerConfig;

    public TripAnalyzerModule(Predicate<Id<Person>> filterPerson, String modalShareSpreadsheetId, String modalDistanceSpreadsheetId) {

        this.handlerConfig = new HandlerConfig(
                modalShareSpreadsheetId,
                modalDistanceSpreadsheetId,
                filterPerson::test
        );
    }

    @Override
    public void install() {
        addControlerListenerBinding().to(MobsimHandler.class);
        bind(HandlerConfig.class).toInstance(this.handlerConfig);
    }

    interface PersonFilter {
        boolean filter(Id<Person> id);
    }

    private static class  HandlerConfig {

        private final String modalShareSpreadsheetId;
        private final String modalDistanceSpreadsheetId;
        private final PersonFilter personFilter;

        public HandlerConfig(String modalShareSpreadsheetId, String modalDistanceSpreadsheetId, PersonFilter personFilter) {
            this.modalShareSpreadsheetId = modalShareSpreadsheetId;
            this.modalDistanceSpreadsheetId = modalDistanceSpreadsheetId;
            this.personFilter = personFilter;
        }
    }

    private static class MobsimHandler implements BeforeMobsimListener, AfterMobsimListener {

        // we want our table to always look the same
        private static final List<String> distanceClasses = List.of("<1", "1 to 3", "3 to 5", "5 to 10", ">10");
        private static final List<String> modes = List.of(TransportMode.car, TransportMode.ride, TransportMode.pt, TransportMode.bike, TransportMode.walk);

        @Inject
        private EventsManager eventsManager;

        @Inject
        private Network network;

        @Inject
        private OutputDirectoryHierarchy outputDirectoryHierarchy;

        @Inject
        private HandlerConfig handlerConfig;

        @Inject
        private ControlerConfigGroup controlerConfig;

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
                var filteredEntries = tripsByPerson.entrySet().stream()
                        .filter(entry -> handlerConfig.personFilter.filter(entry.getKey()))
                        .collect(Collectors.toSet());


               // modalShare(filteredEntries, Paths.get(outputDirectoryHierarchy.getIterationFilename(event.getIteration(), "modal-share.csv")));
                var modalShareHeader = new String[] { "mode", "count", "share" };
                var modalShare = modalShare2(filteredEntries,event.getIteration());


                log.info("Writing modal share to Modal Share to CSV");
                new TabularLogger(modalShareHeader).write(modalShare);
                log.info("Writing modal share to Modal Share to CSV");
                new CSVWriter(Paths.get(outputDirectoryHierarchy.getIterationFilename(event.getIteration(), "modal-share.csv")), modalShareHeader).write(modalShare);
                log.info("Writing modal share to Google Spreadsheets");
                new GoogleSheetsWriter(handlerConfig.modalShareSpreadsheetId, controlerConfig.getRunId(), modalShareHeader).write(modalShare);

               // modalDistanceShare(filteredEntries, Paths.get(outputDirectoryHierarchy.getIterationFilename(event.getIteration(), "modal-distance-share.csv")));
                var modalDistanceShareHeader = new String[] { "mode", "distance", "count", "share" };
                var modalDistanceShare = modalDistanceShare2(filteredEntries);

                new TabularLogger(modalDistanceShareHeader).write(modalDistanceShare);
                new CSVWriter(Paths.get(outputDirectoryHierarchy.getIterationFilename(event.getIteration(), "modal-distance-share.csv")), modalDistanceShareHeader).write(modalDistanceShare);
                new GoogleSheetsWriter(handlerConfig.modalDistanceSpreadsheetId, controlerConfig.getRunId(), modalDistanceShareHeader).write(modalDistanceShare);
            }
        }

        private List<List<Object>> modalShare2(Set<Map.Entry<Id<Person>, List<TripEventHandler.Trip>>> filteredEntries, int iterationNumber) {

            var modalSplit = filteredEntries.stream()
                    .map(Map.Entry::getValue)
                    .flatMap(Collection::stream)
                    .filter(trip -> trip.getStartCoord() != null && trip.getEndCoord() != null)
                    .map(TripEventHandler.Trip::getMode)
                    .collect(Collectors.toMap(mode -> mode, mode -> 1, Integer::sum));

            var totalNumberOfTrips = modalSplit.values().stream()
                    .mapToInt(value -> value)
                    .sum();

            List<List<Object>> result = new ArrayList<>();
            for (var mode : modes) {
                var value = modalSplit.get(mode);
                var share = (double)value / totalNumberOfTrips;
                result.add(
                        List.of(mode, value, share)
                );
            }

          return result;

            /*
            return modalSplit.entrySet().stream()
                    .map(entry -> {
                        List<Object> row = new ArrayList<>();
                        row.add(entry.getKey());
                        row.add(entry.getValue());
                        row.add((double)entry.getValue() / totalNumberOfTrips);
                        return row;
                    })
                    .collect(Collectors.toList());

             */
        }

        private List<List<Object>> modalDistanceShare2(Set<Map.Entry<Id<Person>, List<TripEventHandler.Trip>>> filteredEntries) {

            var distancesByMode = filteredEntries.stream()
                    .map(Map.Entry::getValue)
                    .flatMap(Collection::stream)
                    .filter(trip -> trip.getStartCoord() != null && trip.getEndCoord() != null)
                    .map(trip -> Tuple.of(trip.getMode(), getDistanceKey(trip.getDistance())))
                    .collect(Collectors.groupingBy(Tuple::getFirst, Collectors.toMap(Tuple::getSecond, t -> 1, Integer::sum, Object2IntOpenHashMap::new)));

            var numberOfTripsPerDistanceClass = filteredEntries.stream()
                    .map(Map.Entry::getValue)
                    .flatMap(Collection::stream)
                    .filter(trip -> trip.getStartCoord() != null && trip.getEndCoord() != null)
                    .map(trip -> getDistanceKey(trip.getDistance()))
                    .collect(Collectors.toMap(distance -> distance, distance -> 1, Integer::sum, Object2IntOpenHashMap::new));

            // we want our table to always look the same
            var distanceClasses = List.of("<1", "1 to 3", "3 to 5", "5 to 10", ">10");
            var modes = List.of(TransportMode.car, TransportMode.ride, TransportMode.pt, TransportMode.bike, TransportMode.walk);

            List<List<Object>> values = new ArrayList<>();

            //wrap values into list list
            for(var mode : modes) {

                // get the distanceClasses for mode
                var distances = distancesByMode.get(mode);

                for (var distanceClass : distanceClasses) {

                    var totalNumberForDistance = numberOfTripsPerDistanceClass.getInt(distanceClass);
                    var distanceAndModeValue = distances.getInt(distanceClass);
                    var share = (double)distanceAndModeValue/totalNumberForDistance;
                    log.info(mode + ", " + distanceClass + ": " + distanceAndModeValue + ", " + totalNumberForDistance + ", " + share);

                    values.add(List.of(mode, distanceClass, distanceAndModeValue, share));
                }
            }

            return values;
        }

        private String getDistanceKey(double distance) {
            if (distance < 1000) return "<1";
            if (distance < 3000) return "1 to 3";
            if (distance < 5000) return "3 to 5";
            if (distance < 10000) return "5 to 10";
            return ">10";
        }
    }
}
