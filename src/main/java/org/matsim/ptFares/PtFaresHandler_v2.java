package org.matsim.ptFares;


import com.google.inject.Inject;
import lombok.extern.log4j.Log4j2;
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


@Log4j2
final class PtFaresHandler_v2 implements TransitDriverStartsEventHandler, PersonEntersVehicleEventHandler, PersonLeavesVehicleEventHandler, VehicleArrivesAtFacilityEventHandler, ActivityEndEventHandler, AfterMobsimListener {

    private static final Logger log = Logger.getLogger(PtFaresHandler_v2.class );

    // tracks onboard persons of each vehicle
    private Map<Id<Vehicle>, List<Id<Person>>> vehicle2PersonList = new HashMap<>();
    // tracks list of stop sequences traveled per person for each trip
    private Map<Id<Person>, List<List<Id<TransitStopFacility>>>> person2ListOfStopLists = new HashMap<>();
    // tracks stop sequence for the ongoing trip of each person
    private Map<Id<Person>, List<Id<TransitStopFacility>>> person2StopList = new HashMap<>();
    // tracks last vehicle stop position for all transit vehicles
    private Map<Id<Vehicle>, Id<TransitStopFacility>> vehicle2StopFacility = new HashMap<>();
    // tracks whether a person is onboard pt or not
    private Map<Id<Person>, Boolean> person2Boolean = new HashMap<>();
    private Set<Id<Person>> ptDrivers = new HashSet<>();
    private double compensationTime = Double.NaN;


    @Inject
    private EventsManager events;

    @Inject
    private Scenario scenario;

    @Inject
    private QSimConfigGroup qSimConfigGroup;

    @Inject
    private PtFaresConfigGroup ptFaresConfigGroup;




    // collect all transit drivers
    @Override
    public void handleEvent(TransitDriverStartsEvent event) {

        ptDrivers.add(event.getDriverId());
    }


    // Each time a vehicle arrives at a facility
    // all persons with ongoing trips within the vehicle
    // track this stop facility
    @Override
    public void handleEvent(VehicleArrivesAtFacilityEvent event) {

        // Consider transit vehicles only
        if (scenario.getTransitVehicles().getVehicles().keySet().contains(event.getVehicleId())) {

            // Update transitStop in vehicle2StopFacility
            // vehicle2StopFacility tracks the last arrived stop facility for each vehicle
            vehicle2StopFacility.put(event.getVehicleId(), event.getFacilityId());

            if (vehicle2PersonList.containsKey(event.getVehicleId())) {
                // Case: Persons in vehicle are already tracked in vehicle2PersonList

                // Update person2ListOfStopLists for all persons in the current vehicle
                for (Id<Person> person : vehicle2PersonList.get(event.getVehicleId())) {

                    // Track for each person that it has passed the stop the vehicle just arrived
                    List<Id<TransitStopFacility>> stopList = person2StopList.get(person);
                    stopList.add(event.getFacilityId());
                    person2StopList.put(person, stopList);

                }

            }else{
                // Case: Persons in vehicle had not been started tracking yet
                // (first arrival early in the morning)
                // Vehicle gets map entry with empty person list initialized

                List<Id<Person>> personList = new ArrayList<>();
                vehicle2PersonList.put(event.getVehicleId(), personList);
            }

        }
    }


    // Once a person leaves a vehicle, stops of the vehicles are not considered in persons tracked stop sequences anymore
    @Override
    public void handleEvent(PersonLeavesVehicleEvent event) {

        if (ptDrivers.contains(event.getPersonId())){
            // Skip pt-drivers

        }else{

            // Track only transit vehicle unboardings
            if (scenario.getTransitVehicles().getVehicles().keySet().contains(event.getVehicleId())){

                // Update list of persons onboard vehicle
                // Remove person that has unboarded from the vehicle tracker vehicle2PersonList
                List<Id<Person>> personList = vehicle2PersonList.get(event.getVehicleId());
                personList.remove(event.getPersonId());
                vehicle2PersonList.put(event.getVehicleId(), personList);


            }
        }

    }


    // Once a person enters a vehicle, stops of the vehicles are considered in persons tracked stop sequences
    @Override
    public void handleEvent(PersonEntersVehicleEvent event) {


        if (ptDrivers.contains(event.getPersonId())){

            // Skip pt-drivers

        }else{

            // Transit vehicles only important
            if (scenario.getTransitVehicles().getVehicles().keySet().contains(event.getVehicleId())){

                // Update list of persons onboard vehicle
                // Add person that has boarded to the vehicle tracker vehicle2PersonList
                List<Id<Person>> personList = vehicle2PersonList.get(event.getVehicleId());
                personList.add(event.getPersonId());
                vehicle2PersonList.put(event.getVehicleId(), personList);

                if (person2StopList.containsKey(event.getPersonId())){
                    // Person is tracked in person2StopList
                    // -> has already performed pt trips
                }else{
                    // Person is not tracked in person2StopList yet
                    // -> has not performed pt trips yet
                    // stop list needs to be initialized in person2StopList
                    person2StopList.put(event.getPersonId(), new ArrayList<>());
                }

                // Usually stops are tracked within the vehicle arrival event
                // The stop a person enters a vehicle is added to the tracker person2StopList here
                // as the vehicle arrival event has already happened
                // In case this is the first
                List<Id<TransitStopFacility>> stopList = person2StopList.get(event.getPersonId());
                stopList.add(vehicle2StopFacility.get(event.getVehicleId()));
                person2StopList.put(event.getPersonId(), stopList);

            }
        }

    }


    // On activity end events (which are not pt interactions) a stop sequence is closed and considered as a completed trip
    // as an activity follows after a completed trip
    @Override
    public void handleEvent(ActivityEndEvent event) {


        if (person2Boolean.containsKey(event.getPersonId())){
            // Do nothing as person2Boolean is already initialized for this person
        }else{
            // Person has never boarded pt and thus it is not tracked yet within person2Boolean
            person2Boolean.put(event.getPersonId(), false);
        }


        if (event.getActType().startsWith("pt interaction")){

            // On pt interaction event mark within person2Boolean that a trip is ongoing
            // person2Boolean will for a person will be set to false, once the stop sequence of the trip has been collected later
            person2Boolean.put(event.getPersonId(), true);

        }else{

            // Prior to all other activities than pt interactions a trip has been ended

            if (person2Boolean.get(event.getPersonId())){
                // Only if there was a pt trip before the current activity
                // (which is tracked via person2Boolean for each person)
                // go through the finish trip = stop sequence collection process
                finishTrip(event.getPersonId());
            }

        }
    }


    // After mobsim, pt Fares are calculated based on best price principle
    @Override
    public void notifyAfterMobsim(AfterMobsimEvent event) {

        // -- CLOSE LAST TRIP OF EACH PERSON

        person2Boolean.entrySet().stream().forEach(entry ->{

            // Sometimes agents do not reach their final activity
            // Then the last activity performed is ended
            // Which means that for this person an empty new stop list had been created ...
            if (!person2StopList.get(entry.getKey()).isEmpty()){
                // ... do not consider this empty list
                finishTrip(entry.getKey());
            }
        });


        // -- DETERMINE TRANSIT FARES
        log.info("Start calculating transit fares...");


        // Determine fareZones travelled through for each person
        Map<Id<Person>,List<String>> person2ListOfFareZones = new HashMap<>();
        person2ListOfStopLists.entrySet().parallelStream().forEach(personEntry -> {
            person2ListOfFareZones.put(personEntry.getKey(), determineFareZones(personEntry.getValue())) ;
        });

        // Determine best fitting ticket zone range
        Map<Id<Person>, Integer> person2Integer = new HashMap<>();
        Map<String, List<String>> string2stringList = new HashMap<>(){{
            put("key1", Arrays.asList({"a", "b"}));
            put("key2", "value2");
        }};

        person2ListOfFareZones.entrySet().stream().forEach(personEntry -> {

        });


        double time = getOrCalcCompensationTime();
        double amount = 1.;


        // -- PAY OUT TRANSIT FARES

        person2ListOfFareZones.entrySet().stream().forEach(entry ->{
            // Test Case - TO REMOVE

            String purpose = String.format("Person %s has travelled through zones %s", entry.getKey().toString(), entry.getValue().toString() );
            events.processEvent(new PersonMoneyEvent(time, entry.getKey(), amount, purpose, "ptOperator"));

        });

    }


    private List<String> determineFareZones(List<List<Id<TransitStopFacility>>> listOfStopLists) {

        // -- DETERMINE FARE ZONES
        // The body of this method can be easily replaced by another algorithm for determining fareZones from stop sequence input
        // such as creating stop sequence line strings and the interaction of these with the fare zone shapes


        String fareZoneAttributeName = ptFaresConfigGroup.getPtFareZoneAttributeName();

        List<String> fareZones = new ArrayList<>();
        listOfStopLists.stream().forEach(stopList -> {


            stopList.stream().forEach(stop ->{

                String currentFareZone = (String) scenario.getTransitSchedule().getFacilities().get(stop).getAttributes().getAttribute(fareZoneAttributeName);

                if (fareZones.contains(currentFareZone)){
                    // Do nothing when fareZone is in list already
                }else{
                    // When fareZone is not in list, add ...
                    fareZones.add(currentFareZone);
                }

            });

        });
        
        return fareZones;

    }


    private void finishTrip(Id<Person> personId) {

        List<List<Id<TransitStopFacility>>> listOfStopLists;
        if (person2ListOfStopLists.containsKey(personId)){

            // add stop sequence to list of stop sequences
            listOfStopLists = person2ListOfStopLists.get(personId);

        }else{

            // initialize new list of stop sequences and then add first stop sequence
            listOfStopLists = new ArrayList<>();

        }

        List<Id<TransitStopFacility>> stopList = person2StopList.get(personId);
        listOfStopLists.add(stopList);
        person2ListOfStopLists.put(personId, listOfStopLists);

        person2StopList.put(personId, new ArrayList<>());
        person2Boolean.put(personId, false);

    }


    private double getOrCalcCompensationTime() {
        if (Double.isNaN(this.compensationTime)) {
            this.compensationTime = (Double.isFinite(qSimConfigGroup.getEndTime().seconds()) && qSimConfigGroup.getEndTime().seconds() > 0)
                    ? qSimConfigGroup.getEndTime().seconds()
                    : Double.MAX_VALUE;
        }

        return this.compensationTime;
    }
}





