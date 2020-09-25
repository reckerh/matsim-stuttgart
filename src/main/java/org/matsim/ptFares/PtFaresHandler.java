package org.matsim.ptFares;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.ActivityStartEvent;
import org.matsim.api.core.v01.events.PersonEntersVehicleEvent;
import org.matsim.api.core.v01.events.PersonLeavesVehicleEvent;
import org.matsim.api.core.v01.events.TransitDriverStartsEvent;
import org.matsim.api.core.v01.events.handler.ActivityStartEventHandler;
import org.matsim.api.core.v01.events.handler.PersonEntersVehicleEventHandler;
import org.matsim.api.core.v01.events.handler.PersonLeavesVehicleEventHandler;
import org.matsim.api.core.v01.events.handler.TransitDriverStartsEventHandler;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.api.experimental.events.VehicleArrivesAtFacilityEvent;
import org.matsim.core.api.experimental.events.VehicleDepartsAtFacilityEvent;
import org.matsim.core.api.experimental.events.handler.VehicleArrivesAtFacilityEventHandler;
import org.matsim.core.api.experimental.events.handler.VehicleDepartsAtFacilityEventHandler;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.vehicles.Vehicle;

import javax.inject.Inject;
import java.util.*;

final class PtFaresHandler implements TransitDriverStartsEventHandler, PersonLeavesVehicleEventHandler, PersonEntersVehicleEventHandler, VehicleArrivesAtFacilityEventHandler {

    private final Set<Id<Person>> ptDrivers = new HashSet<>();
    private final Map<Id<Vehicle>, Id<TransitStopFacility>> vehicle2StopFacility = new HashMap<>();
    private final Map<Id<Person>, Id<TransitStopFacility>> person2StopFacility = new HashMap<>();
    private final Map<Id<Vehicle>, Id<TransitLine>> vehicle2transitLine = new HashMap<>();
    private final Map<Id<Vehicle>, Id<TransitRoute>> vehicle2transitRoute = new HashMap<>();
    private final Map<Id<Person>, List<String>> person2fareZoneList = new HashMap<>();

    @Inject
    private PtFaresConfigGroup ptFaresConfigGroup;

    @Inject
    private EventsManager events;

    @Inject
    private Scenario scenario;


    @Override
    public void reset(int iteration) {

    }

    public void PtFaresHandler(){

        // When initializing this handler, make assignment of vehicles to transit line and route
        // What is the case for one transit vehicle being assigned to multiple routes/ lines
        // This case is not fetched

        scenario.getTransitSchedule().getTransitLines().values().parallelStream().forEach(transitLine -> {

            transitLine.getRoutes().values().stream().forEach(transitRoute -> {

                transitRoute.getDepartures().values().stream().forEach(departure -> {

                    vehicle2transitLine.put(departure.getVehicleId(),transitLine.getId());
                    vehicle2transitRoute.put(departure.getVehicleId(),transitRoute.getId());

                });


            });
        });
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
                        onLine = false;
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

    //ToDo: Add MoneyEvent "at the end of the day"



}
