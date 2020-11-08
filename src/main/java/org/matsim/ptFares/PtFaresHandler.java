package org.matsim.ptFares;

import com.google.inject.Inject;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.*;
import org.matsim.api.core.v01.events.handler.*;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.api.experimental.events.VehicleArrivesAtFacilityEvent;
import org.matsim.core.api.experimental.events.handler.VehicleArrivesAtFacilityEventHandler;
import org.matsim.core.config.groups.QSimConfigGroup;
import org.matsim.core.controler.events.AfterMobsimEvent;
import org.matsim.core.controler.listener.AfterMobsimListener;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.vehicles.Vehicle;

import java.util.*;
import java.util.stream.Collectors;


public class PtFaresHandler implements TransitDriverStartsEventHandler, PersonDepartureEventHandler, PersonLeavesVehicleEventHandler, PersonArrivalEventHandler, VehicleArrivesAtFacilityEventHandler, PersonEntersVehicleEventHandler, AfterMobsimListener {
    private static final Logger log = Logger.getLogger( PtFaresHandler.class );
    private double compensationTime = Double.NaN;
    private Set<Id<Person>> ptDrivers = new HashSet<>();
    private Map<Id<Vehicle>, TransitVehicle> transitVehicles = new HashMap<>();
    private Map<Id<Person>, TransitRider> transitRiders = new HashMap<>();

    @Inject
    private EventsManager events;

    @Inject
    private Scenario scenario;

    @Inject
    private QSimConfigGroup qSimConfigGroup;

    @Inject
    private PtFaresConfigGroup ptFaresConfigGroup;


    @Override
    public void handleEvent(TransitDriverStartsEvent event) {

        ptDrivers.add(event.getDriverId());
    }


    @Override
    public void handleEvent(PersonDepartureEvent event) {

        if (! ptDrivers.contains(event.getPersonId())){
            transitRiders.computeIfAbsent(event.getPersonId(), riderId -> new TransitRider(riderId));

            if (event.getLegMode().equals("pt")){
                transitRiders.get(event.getPersonId()).startTrip();
            }
        }
    }


    @Override
    public void handleEvent(PersonArrivalEvent event) {

        if (! ptDrivers.contains(event.getPersonId())){
            if (transitRiders.get(event.getPersonId()).getOnTransit()){
                transitRiders.get(event.getPersonId()).closeTrip();
            }
        }
    }


    @Override
    public void handleEvent(VehicleArrivesAtFacilityEvent event) {
        if (scenario.getTransitVehicles().getVehicles().containsKey(event.getVehicleId())) {

            // case: vehicles first arrival of the day
            transitVehicles.computeIfAbsent(event.getVehicleId(), vehicleId ->
                    new TransitVehicle(event.getVehicleId()));

            // update vehicle position
            transitVehicles.get(event.getVehicleId()).
                    updateLastTransitStopFacility(event.getFacilityId());

            // update persons trip stop sequence
            for (var riderId:transitVehicles.get(event.getVehicleId()).getPersonsOnboard()){
                    transitRiders.get(riderId).updateTrip(event.getFacilityId());
            }
        }
    }


    @Override
    public void handleEvent(PersonEntersVehicleEvent event) {

        // transit riders only
        if (! ptDrivers.contains(event.getPersonId()) &
                scenario.getTransitVehicles().getVehicles().containsKey(event.getVehicleId())){

            // update persons onboard vehicle and persons trip stop sequence
            transitVehicles.get(event.getVehicleId()).personEntersVehicle(event.getPersonId());
            transitRiders.get(event.getPersonId()).updateTrip(
                    transitVehicles.get(event.getVehicleId()).getLastTransitStopFacility());

        }

    }


    @Override
    public void handleEvent(PersonLeavesVehicleEvent event) {

        // transit riders only
        if (! ptDrivers.contains(event.getPersonId()) &
                scenario.getTransitVehicles().getVehicles().containsKey(event.getVehicleId())){

            // update persons onboard vehicle
            transitVehicles.get(event.getVehicleId()).personLeavesVehicle(event.getPersonId());

        }

    }


    @Override
    public void notifyAfterMobsim(AfterMobsimEvent event) {

        for (var transitRider: transitRiders.values()){
            events.processEvent(new PersonMoneyEvent(getOrCalcCompensationTime(),
                    transitRider.getId(),
                    0.,
                    String.format("Person %s has travelled following trips: %s",
                            transitRider.getId().toString(),
                            transitRider.getAllTrips().toString()),
                    "ptOperator"));

        }
    }


    private double getOrCalcCompensationTime() {
        if (Double.isNaN(this.compensationTime)) {
            this.compensationTime = (Double.isFinite(qSimConfigGroup.getEndTime().seconds()) && qSimConfigGroup.getEndTime().seconds() > 0)
                    ? qSimConfigGroup.getEndTime().seconds()
                    : Double.MAX_VALUE;
        }

        return this.compensationTime;
    }


    class TransitVehicle{
        private Id<Vehicle> vehicleId;
        private Set<Id<Person>> personsOnboard = new HashSet<>();
        private Id<TransitStopFacility> lastTransitStopFacility;

        public TransitVehicle(Id<Vehicle> vehicleId){
            this.setId(vehicleId);
        }

        public Id<Vehicle> getId() {
            return vehicleId;
        }

        public void setId(Id<Vehicle> vehicleId) {
            this.vehicleId = vehicleId;
        }

        public void personEntersVehicle(Id<Person> personId){
            personsOnboard.add(personId);
        }

        public void personLeavesVehicle(Id<Person> personId){
            personsOnboard.remove(personId);
        }

        public Set<Id<Person>> getPersonsOnboard(){
            return personsOnboard;
        }

        public void updateLastTransitStopFacility(Id<TransitStopFacility> lastTransitStopFacility){
            this.lastTransitStopFacility = lastTransitStopFacility;
        }

        public Id<TransitStopFacility> getLastTransitStopFacility(){
            return lastTransitStopFacility;
        }
    }


    class TransitRider{
        private Id<Person> personId;
        private List<TransitTrip> trips = new ArrayList<>();
        private Boolean onTransit = false;

        public TransitRider(Id<Person> personId){
            setId(personId);
        }

        public Id<Person> getId() {
            return personId;
        }

        public void setId(Id<Person> personId) {
            this.personId = personId;
        }

        public void startTrip(){
            onTransit = true;
            trips.add(new TransitTrip());
        }

        public void closeTrip(){
            trips.get(trips.size() - 1).markTripAsFinished();
            onTransit = false;
        }

        public void updateTrip(Id<TransitStopFacility> currentFacility){
            trips.get(trips.size() - 1).updateStopSequence(currentFacility);
        }

        public Boolean getOnTransit() {
            return onTransit;
        }

        public List<String> getAllTrips(){
            return trips.stream()
                    .map(TransitTrip::getStopSequence)
                    .map(stopSequence -> stopSequence.toString())
                    .collect(Collectors.toList());
        }


        public class TransitTrip{
            private List<Id<TransitStopFacility>> stopSequence = new ArrayList<>();
            private Boolean tripClosed = false;

            public void updateStopSequence(Id<TransitStopFacility> facilityId){
                if (tripClosed){
                    throw new AssertionError("Trip of a transit rider is to be updated although already finished.");
                } else {
                    stopSequence.add(facilityId);
                }
            }

            public void markTripAsFinished(){
                tripClosed = true;
            }

            public List<Id<TransitStopFacility>> getStopSequence(){
                return stopSequence;
            }

        }
    }
}
