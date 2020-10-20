package org.matsim.ptFares;


import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.PersonEntersVehicleEvent;
import org.matsim.api.core.v01.events.PersonLeavesVehicleEvent;
import org.matsim.api.core.v01.events.PersonMoneyEvent;
import org.matsim.api.core.v01.events.TransitDriverStartsEvent;
import org.matsim.api.core.v01.events.handler.PersonEntersVehicleEventHandler;
import org.matsim.api.core.v01.events.handler.PersonLeavesVehicleEventHandler;
import org.matsim.api.core.v01.events.handler.TransitDriverStartsEventHandler;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.api.experimental.events.VehicleArrivesAtFacilityEvent;
import org.matsim.core.api.experimental.events.handler.VehicleArrivesAtFacilityEventHandler;
import org.matsim.core.config.groups.QSimConfigGroup;
import org.matsim.core.controler.events.AfterMobsimEvent;
import org.matsim.core.controler.listener.AfterMobsimListener;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.run.RunStuttgartBaseCase;
import org.matsim.vehicles.Vehicle;
import org.apache.log4j.Logger;
import lombok.extern.log4j.Log4j2;

import com.google.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;

@Log4j2
final class PtFaresHandler implements TransitDriverStartsEventHandler, PersonLeavesVehicleEventHandler, PersonEntersVehicleEventHandler, VehicleArrivesAtFacilityEventHandler, AfterMobsimListener {

    private static final Logger log = Logger.getLogger(RunStuttgartBaseCase.class );

    private Set<Id<Person>> ptDrivers = new HashSet<>();
    private Map<Id<Vehicle>, Id<TransitStopFacility>> vehicle2StopFacility = new HashMap<>();
    private Map<Id<Person>, Id<TransitStopFacility>> person2StopFacility = new HashMap<>();
    private Map<Id<Vehicle>, Id<TransitLine>> vehicle2transitLine = new HashMap<>();
    private Map<Id<Vehicle>, Id<TransitRoute>> vehicle2transitRoute = new HashMap<>();

    private Map<Id<Person>, List<String>> person2fareZoneList = new HashMap<>();
    private double compensationTime = Double.NaN;

    @Inject
    private PtFaresConfigGroup ptFaresConfigGroup;

    @Inject
    private EventsManager events;

    @Inject
    private Scenario scenario;

    @Inject
    private QSimConfigGroup qSimConfigGroup;






    @Override
    public void reset(int iteration) {
        vehicle2StopFacility.clear();
        person2StopFacility.clear();
    }


    public PtFaresHandler(){

        // When initializing this handler, make assignment of vehicles to transit line and route
        // What is the case for one transit vehicle being assigned to multiple routes/ lines
        // This case is not fetched

/*        scenario.getTransitSchedule().getTransitLines().values().parallelStream().forEach(transitLine -> {

            transitLine.getRoutes().values().stream().forEach(transitRoute -> {

                transitRoute.getDepartures().values().stream().forEach(departure -> {

                    vehicle2transitLine.put(departure.getVehicleId(),transitLine.getId());
                    vehicle2transitRoute.put(departure.getVehicleId(),transitRoute.getId());

                });


            });
        });*/
    }


    @Inject
    public Map<Id<Vehicle>, Id<TransitRoute>> getVehicle2transitRoute(){

        Map<Id<Vehicle>, Id<TransitRoute>> vehicle2transitRoute = new HashMap<>();

        scenario.getTransitSchedule().getTransitLines().values().parallelStream().forEach(transitLine -> {

            transitLine.getRoutes().values().stream().forEach(transitRoute -> {

                transitRoute.getDepartures().values().stream().forEach(departure -> {

                    vehicle2transitRoute.put(departure.getVehicleId(),transitRoute.getId());

                });


            });
        });

        return vehicle2transitRoute;

    }



    @Inject
    public Map<Id<Vehicle>, Id<TransitLine>> getVehicle2transitLine(){

        Map<Id<Vehicle>, Id<TransitLine>> vehicle2transitLine = new HashMap<>();

        scenario.getTransitSchedule().getTransitLines().values().parallelStream().forEach(transitLine -> {

            transitLine.getRoutes().values().stream().forEach(transitRoute -> {

                transitRoute.getDepartures().values().stream().forEach(departure -> {

                    vehicle2transitLine.put(departure.getVehicleId(),transitLine.getId());

                });


            });
        });

        return vehicle2transitLine;

    }


    @Override
    public void handleEvent(TransitDriverStartsEvent event) {
        ptDrivers.add(event.getDriverId());
    }


    @Override
    public void handleEvent(PersonEntersVehicleEvent event) {

        if (ptDrivers.contains(event.getPersonId())){
            // skip pt-drivers

        }else{

            // transit vehicles only important
            if (scenario.getTransitVehicles().getVehicles().values().contains(event.getVehicleId())){

                person2StopFacility.put(event.getPersonId(),vehicle2StopFacility.get(event.getVehicleId()));

            }
        }

    }


    @Override
    public void handleEvent(PersonLeavesVehicleEvent event) {

        vehicle2transitLine = getVehicle2transitLine();
        vehicle2transitRoute = getVehicle2transitRoute();

        if (ptDrivers.contains(event.getPersonId())){
            // skip pt-drivers

        }else{

            // transit vehicles only important
            if (scenario.getTransitVehicles().getVehicles().values().contains(event.getVehicleId())){

                Id<TransitStopFacility> stopEnteredPt = person2StopFacility.get(event.getVehicleId());
                Id<TransitStopFacility> stopExitedPt = vehicle2StopFacility.get(event.getVehicleId());

                // Look up which zones were travelled between the first and last stop of pt
                TransitRoute route = scenario.getTransitSchedule().getTransitLines().get(vehicle2transitLine.get(event.getVehicleId())).getRoutes().get(vehicle2transitRoute.get(event.getVehicleId()));
                List<TransitRouteStop> stops = route.getStops();

                Boolean onLine = false;
                for (TransitRouteStop stop:stops){

                    if (stop.getStopFacility().getId() == stopEnteredPt){
                        onLine = true;
                    }

                    if (stop.getStopFacility().getId() == stopExitedPt){
                        break;
                    }

                    if (onLine){

                        List<String> fareZones = person2fareZoneList.get(event.getPersonId());
                        String currentFareZone = (String) stop.getStopFacility().getAttributes().getAttribute(ptFaresConfigGroup.getPtFareZoneAttributeName());

                        if (fareZones.contains(currentFareZone)){
                            // Do nothing when fareZone is in list already
                        }else{
                            // When fareZone is not in list, add ...
                            fareZones.add(currentFareZone);
                            person2fareZoneList.put(event.getPersonId(), fareZones);
                        }

                    }

                }

            }
        }

    }


    @Override
    public void handleEvent(VehicleArrivesAtFacilityEvent event) {

        // transit vehicles only important
        if (scenario.getTransitVehicles().getVehicles().values().contains(event.getVehicleId())){

            // update the current transitStop that the vehicle is at/ was at last
            vehicle2StopFacility.put(event.getVehicleId(), event.getFacilityId());

        }
    }


    @Override
    public void notifyAfterMobsim(AfterMobsimEvent event) {

        double time = getOrCalcCompensationTime();

        log.info("Start paying out transit fares...");

        Map<Integer, Double> allFares = ptFaresConfigGroup.getAllFares();

        person2fareZoneList.entrySet().stream().forEach(entry -> {

            List<List<String>> listOfFareZoneLists = entry.getValue().stream().map(e -> splitByComma(e)).collect(Collectors.toList());
            List<List<String>> fareZoneCombinations = generateCombinations(listOfFareZoneLists, 0);
            Integer fareZonesToPay = findMinimalZoneRange(fareZoneCombinations);
            Double amount = -(allFares.get(fareZonesToPay));
            events.processEvent(new PersonMoneyEvent(time, entry.getKey(), amount, "ptFare", "ptOperator"));
        });

    }


    private static Integer findMinimalZoneRange(List<List<String>> fareZoneCombinations){

        Map<List<String>, Integer> combination2ZoneRange = new HashMap<>();

        fareZoneCombinations.stream().forEach(combination ->{

            List<String> sortedCombination = new ArrayList<>(combination);
            Collections.sort(combination);
            Integer range = Integer.parseInt(combination.get(combination.size()-1)) - Integer.parseInt(combination.get(0)) + 1;
            combination2ZoneRange.put(combination, range);
        });

        return Collections.min(combination2ZoneRange.values());


    }


    private static List<List<String>> generateCombinations(List<List<String>> input, int i) {

        // stop condition
        if(i == input.size()) {
            // return a list with an empty list
            List<List<String>> result = new ArrayList<List<String>>();
            result.add(new ArrayList<String>());
            return result;
        }

        List<List<String>> result = new ArrayList<List<String>>();
        List<List<String>> recursive = generateCombinations(input, i+1); // recursive call

        // for each element of the first list of input
        for(int j = 0; j < input.get(i).size(); j++) {
            // add the element to all combinations obtained for the rest of the lists
            for(int k = 0; k < recursive.size(); k++) {
                // copy a combination from recursive
                List<String> newList = new ArrayList<String>();
                for(String string : recursive.get(k)) {
                    newList.add(string);
                }
                // add element of the first list
                newList.add(input.get(i).get(j));
                // add new combination to result
                result.add(newList);
            }
        }
        return result;
    }


    public static List<String> splitByComma(String s){

        if (s.contains(",")){
            return Arrays.asList(s.split(","));
        }else{
            return Arrays.asList(s);
        }
    }


    // Copied from:
    // https://github.com/matsim-scenarios/matsim-berlin/blob/7a347c7ff4988233f04019c6d9ed27639c1e9fd5/src/main/java/org/matsim/run/drt/intermodalTripFareCompensator/IntermodalTripFareCompensatorPerDay.java#L101-L109

    private double getOrCalcCompensationTime() {
        if (Double.isNaN(this.compensationTime)) {
            this.compensationTime = (Double.isFinite(qSimConfigGroup.getEndTime().seconds()) && qSimConfigGroup.getEndTime().seconds() > 0)
                    ? qSimConfigGroup.getEndTime().seconds()
                    : Double.MAX_VALUE;
        }

        return this.compensationTime;
    }

}





