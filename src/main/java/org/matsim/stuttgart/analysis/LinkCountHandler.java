package org.matsim.stuttgart.analysis;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.LinkEnterEvent;
import org.matsim.api.core.v01.events.VehicleEntersTrafficEvent;
import org.matsim.api.core.v01.events.handler.LinkEnterEventHandler;
import org.matsim.api.core.v01.events.handler.VehicleEntersTrafficEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.vehicles.Vehicle;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class LinkCountHandler implements LinkEnterEventHandler, VehicleEntersTrafficEventHandler {

    private final Map<Id<Link>, Integer> bikeCounter = new HashMap<>();
    private final Map<Id<Link>, Integer> carCounter = new HashMap<>();

    @Override
    public void handleEvent(LinkEnterEvent event){

        //count car and bike link entries in entire network
        if( isBike(event.getVehicleId()) ){

            if( bikeCounter.get(event.getLinkId()) == null ){
                bikeCounter.put(event.getLinkId(), 1);
            }else{
                bikeCounter.put(event.getLinkId(), bikeCounter.get(event.getLinkId())+1);
            }

        }
        if( isCar(event.getVehicleId()) ){

            if( carCounter.get(event.getLinkId()) == null ){
                carCounter.put(event.getLinkId(), 1);
            }else{
                carCounter.put(event.getLinkId(), carCounter.get(event.getLinkId())+1);
            }

        }

        //handle link entries for counting stations
        if ( CountingNetwork.getCountingStations() != null ){

            for ( CountingStation countingStation : CountingNetwork.getCountingStations() ) {

                if( countingStation.bikeCounts.containsKey(event.getLinkId()) && isBike(event.getVehicleId()) ) {
                    countingStation.bikeCounts.put(event.getLinkId(), countingStation.bikeCounts.get(event.getLinkId())+1);
                }

            }

        }

    }

    @Override
    public void handleEvent(VehicleEntersTrafficEvent event){

        //count car and bike link entries in entire network
        if( isBike(event.getVehicleId()) ){

            if( bikeCounter.get(event.getLinkId()) == null ){
                bikeCounter.put(event.getLinkId(), 1);
            }else{
                bikeCounter.put(event.getLinkId(), bikeCounter.get(event.getLinkId())+1);
            }

        }
        if( isCar(event.getVehicleId()) ){

            if( carCounter.get(event.getLinkId()) == null ){
                carCounter.put(event.getLinkId(), 1);
            }else{
                carCounter.put(event.getLinkId(), carCounter.get(event.getLinkId())+1);
            }

        }

        //handle link entries for counting stations
        if ( CountingNetwork.getCountingStations() != null ){

            for ( CountingStation countingStation : CountingNetwork.getCountingStations() ) {

                if( countingStation.bikeCounts.containsKey(event.getLinkId()) && isBike(event.getVehicleId()) ) {
                    countingStation.bikeCounts.put(event.getLinkId(), countingStation.bikeCounts.get(event.getLinkId())+1);
                }

            }

        }

    }

    public Map<Id<Link>, Integer> getBikeCounts(){
        return bikeCounter;
    }

    public Map<Id<Link>, Integer> getCarCounts(){
        return carCounter;
    }

    private boolean isBike(Id<Vehicle> vehId){
        //I would prefer a solution that gets the vehicles from the scenario and gets the mode from the vehicle via the Id, as I'm not sure if the suffixes are consistent (although I suspect that)
        //and if toString is overwritten here (as the java default method will not give me the string and IntelliJ only refers me to the default method...)
        return vehId.toString().endsWith("bike");
    }

    private boolean isCar(Id<Vehicle> vehId){
        //I would prefer a solution that gets the vehicles from the scenario and gets the mode from the vehicle via the Id, as I'm not sure if the suffixes are consistent (although I suspect that)
        //and if toString is overwritten here (as the java default method will not give me the string and IntelliJ only refers me to the default method...)
        return vehId.toString().endsWith("car");
    }

    public static class CountingNetwork {
        private static HashSet<CountingStation> countingStations;

        public CountingNetwork(){
            countingStations = new HashSet<>();
        }

        public static void addCountingStation(CountingStation countingStation){
            countingStations.add(countingStation);
        }

        public static Set<CountingStation> getCountingStations(){
            return countingStations;
        }


    }

}
