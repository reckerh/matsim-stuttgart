package org.matsim.prepare;

import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.matsim.pt.transitSchedule.api.*;
import org.opengis.feature.simple.SimpleFeature;

import java.util.*;
import java.util.stream.Collectors;


/**
 * Adds fare zone and bike-and-ride attribute to transit stop facilities
 *
 * @author davidwedekind
 */

public class PrepareTransitSchedule {

    private static final Logger log = Logger.getLogger(PrepareTransitSchedule.class);

    private static String inputSchedule = "C:/Users/david/OneDrive/02_Uni/02_Master/05_Masterarbeit/03_MATSim/02_runs/stuttgart-v0.0-snz-original/optimizedSchedule.xml.gz";
    private static String outputSchedule = "C:/Users/david/OneDrive/02_Uni/02_Master/05_Masterarbeit/03_MATSim/02_runs/optimizedSchedule-edd.xml.gz";
    private static String fareZoneShapeFile = "C:/Users/david/OneDrive/02_Uni/02_Master/05_Masterarbeit/03_MATSim/01_prep/03_FareZones/fareZones_sp.shp";


    public static void main(String[] args) {

        if (args.length > 0) {
            inputSchedule = args[0];
            outputSchedule = args[1];
            fareZoneShapeFile = args[2];

            log.info("input plans: " + inputSchedule);
            log.info("output plans: " + outputSchedule);
            log.info("fare zone shape file: " + fareZoneShapeFile);
        }

        // Parse Transit Schedule and put into scenario
        PrepareTransitSchedule preparer = new PrepareTransitSchedule();
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new TransitScheduleReader(scenario).readFile(inputSchedule);

        preparer.run(scenario, fareZoneShapeFile);

        new TransitScheduleWriter(scenario.getTransitSchedule()).writeFile(outputSchedule);


    }

    public void run(Scenario scenario, String shapeFile) {

        TransitSchedule schedule = scenario.getTransitSchedule();
        Collection<SimpleFeature> fareZoneFeatures = ShapeFileReader.getAllFeatures(shapeFile);

        // Create map with all bikeAndRideAssignments in vvs area
        Set<Id<TransitStopFacility>> bikeAndRideAssignment = tagBikeAndRide(schedule);

        for (var transitStopFacility: schedule.getFacilities().values()){

            String fareZone = findFareZone(transitStopFacility, fareZoneFeatures);
            transitStopFacility.getAttributes().putAttribute("ptFareZone", fareZone);

            // Facilities in vvs zone are considered only
            if (fareZone.equals("out")){
                transitStopFacility.getAttributes().putAttribute("VVSBikeAndRide", false);

                if (bikeAndRideAssignment.contains(transitStopFacility.getId())){
                    transitStopFacility.getAttributes().putAttribute("VVSBikeAndRide", true);
                }else{
                    transitStopFacility.getAttributes().putAttribute("VVSBikeAndRide", false);
                }

            }

        }

    }


    private String findFareZone(TransitStopFacility transitStopFacility, Collection<SimpleFeature> features) {

        // Facilities which are not whithin the fare zone shapes are located outside of vvs area and thus marked accordingly
        String fareZone = "out";

        Coord homeCoord = transitStopFacility.getCoord();
        Point point = MGC.coord2Point(homeCoord);

        for (SimpleFeature feature : features ) {

            Geometry geometry = (Geometry) feature.getDefaultGeometry();

            if (geometry.covers(point)) {
                fareZone = feature.getAttribute("FareZone").toString();
            }

        }

        return fareZone;

    }


    private Set<Id<TransitStopFacility>> tagBikeAndRide(TransitSchedule schedule) {

        // This method writes all stops in a list that have departures of mode "tram" or "train"
        // It is assumed that at tram or train stations Bike-and-Ride is possible

        var modes = Set.of(TransportMode.train, "tram");

        var bikeAndRide = schedule.getTransitLines().values().stream()
                .flatMap(line -> line.getRoutes().values().stream())
                .filter(route -> modes.contains(route.getTransportMode()))
                        .flatMap(route -> route.getStops().stream())
                        .map(stop -> stop.getStopFacility().getId())
                        .collect(Collectors.toSet());

        return bikeAndRide;

    }

}
