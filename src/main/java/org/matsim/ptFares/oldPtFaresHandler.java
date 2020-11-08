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

/**
 * @author dwedekind
 */


final class oldPtFaresHandler implements TransitDriverStartsEventHandler, PersonEntersVehicleEventHandler, PersonLeavesVehicleEventHandler, VehicleArrivesAtFacilityEventHandler, ActivityEndEventHandler, AfterMobsimListener {

    private static final Logger log = Logger.getLogger(oldPtFaresHandler.class );

    // ToDo: Build vehicle object
    private final Map<Id<Vehicle>, List<Id<Person>>> vehicle2PersonsOnboard = new HashMap<>();
    private final Map<Id<Vehicle>, Id<TransitStopFacility>> vehicle2LastStopFacilityPos = new HashMap<>();

    // ToDo: TransitRider object
    // ToDo: Trips object

    // tracks list of stop sequences traveled per person for each trip
    private final Map<Id<Person>, List<List<Id<TransitStopFacility>>>> person2ListOfStopLists = new HashMap<>();
    // tracks stop sequence for the ongoing trip of each person
    private final Map<Id<Person>, List<Id<TransitStopFacility>>> person2StopList = new HashMap<>();

    // tracks whether a person is onboard pt or not
    private final Map<Id<Person>, Boolean> person2Boolean = new HashMap<>();
    private final Set<Id<Person>> ptDrivers = new HashSet<>();
    private double compensationTime = Double.NaN;
    Map<String, Set<String>> string2stringList = new HashMap<>();



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
        if (scenario.getTransitVehicles().getVehicles().containsKey(event.getVehicleId())) {

            // Update transitStop in vehicle2StopFacility
            // vehicle2StopFacility tracks the last arrived stop facility for each vehicle
            vehicle2LastStopFacilityPos.put(event.getVehicleId(), event.getFacilityId());

            if (vehicle2PersonsOnboard.containsKey(event.getVehicleId())) {
                // Case: Persons in vehicle are already tracked in vehicle2PersonList

                // Update person2ListOfStopLists for all persons in the current vehicle
                for (Id<Person> person : vehicle2PersonsOnboard.get(event.getVehicleId())) {

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
                vehicle2PersonsOnboard.put(event.getVehicleId(), personList);
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
            if (scenario.getTransitVehicles().getVehicles().containsKey(event.getVehicleId())){

                // Update list of persons onboard vehicle
                // Remove person that has unboarded from the vehicle tracker vehicle2PersonList
                List<Id<Person>> personList = vehicle2PersonsOnboard.get(event.getVehicleId());
                personList.remove(event.getPersonId());
                vehicle2PersonsOnboard.put(event.getVehicleId(), personList);


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
            if (scenario.getTransitVehicles().getVehicles().containsKey(event.getVehicleId())){

                // Update list of persons onboard vehicle
                // Add person that has boarded to the vehicle tracker vehicle2PersonList
                List<Id<Person>> personList = vehicle2PersonsOnboard.get(event.getVehicleId());
                personList.add(event.getPersonId());
                vehicle2PersonsOnboard.put(event.getVehicleId(), personList);

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
                stopList.add(vehicle2LastStopFacilityPos.get(event.getVehicleId()));
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

        for (var entry: person2StopList.entrySet()){
            // Sometimes agents do not reach their final activity
            // Then the last activity performed is ended
            // Which means that for this person an empty new stop list had been created ...
            if (! person2StopList.get(entry.getKey()).isEmpty()){
                // ... do not consider this empty list
                finishTrip(entry.getKey());
            }
        }


        // -- DETERMINE FARE ZONES
        log.info("Start calculating transit fares...");

        Map<Id<Person>,List<String>> person2ListOfFareZones = new HashMap<>();
        for (var entry: person2ListOfStopLists.entrySet()){
            person2ListOfFareZones.put(entry.getKey(), determineFareZones(entry.getValue())) ;
        }


        // -- PAY OUT
        string2stringList = ptFaresConfigGroup.getZonesGroup().getAllHybridZones();
        Map<Integer, Double> allFares = ptFaresConfigGroup.getFaresGroup().getAllFares();
        String outOfTariff = ptFaresConfigGroup.getFaresGroup().getOutOfZoneTag();

        Map<Id<Person>, Integer> person2Integer = new HashMap<>();

        for(var personEntry: person2ListOfFareZones.entrySet()){
            Double tariff;
            if (personEntry.getValue().contains(outOfTariff)){
                // Pay outOfTariff pt price
                tariff = ptFaresConfigGroup.getFaresGroup().getOutOfZonePrice();
            } else {
                tariff = allFares.get(findMinimalZoneRange(personEntry.getValue()));
            }

            events.processEvent(new PersonMoneyEvent(getOrCalcCompensationTime(), personEntry.getKey(), -tariff, "ptFare", "ptOperator"));
        }

    }


    private Integer findMinimalZoneRange(List<String> fareZones) {

        List<List<String>> rawZones = splitIntoRawZones(fareZones);
        List<List<String>> zoneCombinations = generateCombinations(rawZones, 0);

        Map<List<String>, Integer> combination2ZoneRange = new HashMap<>();
        for (var combination: zoneCombinations){
            Collections.sort(combination);
            Integer range = Integer.parseInt(combination.get(combination.size()-1)) - Integer.parseInt(combination.get(0)) + 1;
            combination2ZoneRange.put(combination, range);
        }

        return Collections.min(combination2ZoneRange.values());

    }


    private List<List<String>> splitIntoRawZones(List<String> fareZones) {

        List<List<String>> rawFareZones = new ArrayList<>();
        for(var hybridZone: fareZones){
            List<String> rawZone = new ArrayList<>();
            if (string2stringList.containsKey(hybridZone)){
                rawZone.addAll(string2stringList.get(hybridZone));
            }else{
                rawZone.add(hybridZone);
            }

            rawFareZones.add(rawZone);
        }

        return rawFareZones;
    }


    private static List<List<String>> generateCombinations(List<List<String>> input, int i) {

        // stop condition
        if(i == input.size()) {
            // return a list with an empty list
            List<List<String>> result = new ArrayList<>();
            result.add(new ArrayList<>());
            return result;
        }

        List<List<String>> result = new ArrayList<>();
        List<List<String>> recursive = generateCombinations(input, i+1); // recursive call

        // for each element of the first list of input
        for(int j = 0; j < input.get(i).size(); j++) {
            // add the element to all combinations obtained for the rest of the lists
            for (List<String> strings : recursive) {
                // copy a combination from recursive
                List<String> newList = new ArrayList<>(strings);
                // add element of the first list
                newList.add(input.get(i).get(j));
                // add new combination to result
                result.add(newList);
            }
        }

        return result;
    }


    private List<String> determineFareZones(List<List<Id<TransitStopFacility>>> listOfStopLists) {

        // -- DETERMINE FARE ZONES
        // The body of this method can be easily replaced by another algorithm for determining fareZones from stop sequence input
        // such as creating stop sequence line strings and the interaction of these with the fare zone shapes


        String fareZoneAttributeName = ptFaresConfigGroup.getPtFareZoneAttributeName();

        List<String> fareZones = new ArrayList<>();
        for(var stopList: listOfStopLists){
            for(var stop: stopList){
                String currentFareZone = (String) scenario.getTransitSchedule().getFacilities().get(stop).getAttributes().getAttribute(fareZoneAttributeName);
                fareZones.add(currentFareZone);
            }
        }

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





