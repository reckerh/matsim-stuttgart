package org.matsim.prepare;

import org.apache.log4j.Logger;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.matsim.pt.transitSchedule.api.*;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.referencing.crs.CoordinateReferenceSystem;


import javax.imageio.IIOException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;


/**
 * Adds fare zones to the stop facilities in the transit schedule file
 *
 * @author davidwedekind
 */

public class PrepareTransitSchedule {

    private static final Logger log = Logger.getLogger(TagPopulation.class);

    // Hardcoded file locations
    // Otherwise use command line with args: java AddFareZonesToStops <inputSchedulePath> <outputSchedulePath> <PathToFareZonesShape>
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

        preparer.run(scenario);

        new TransitScheduleWriter(scenario.getTransitSchedule()).writeFile(outputSchedule);


    }

    public void run(Scenario scenario) {

        //Parse the transit schedule

        TransitSchedule schedule = scenario.getTransitSchedule();

        // Do a crs check
        // ToDo: Fix crs check!

//        try {
//            URL url = new URL("file:///" + fareZoneShapeFile);
//            ShapefileDataStore shpDataStore = new ShapefileDataStore(url);
//            CoordinateReferenceSystem shapeFileCRS = shpDataStore.getFeatureSource().getSchema().getCoordinateReferenceSystem();
//
//            System.out.println(shapeFileCRS.getCoordinateSystem().getName().toString());
//
//            if (shapeFileCRS.getCoordinateSystem().getName().toString() == "ETRS_1989_UTM_Zone_32N") {
//                //shapeFileCRS and MATSim CRS match
//                log.info("Shape File CRS is of needed type: " + shapeFileCRS.toString());
//
//            } else {
//                throw new Exception("Shape File CRS is not of needed type!");
//            }
//
//        }catch (MalformedURLException e) {
//            e.printStackTrace();
//
//        }catch (IIOException e){
//            e.printStackTrace();
//
//        }catch (Exception e) {
//            e.printStackTrace();
//        }


        // Read-In Shape Files which provide tag inputs
        Collection<SimpleFeature> fareZoneFeatures = ShapeFileReader.getAllFeatures(fareZoneShapeFile);

        // Create map with all bikeAndRideAssignments in vvs area
        List<Id<TransitStopFacility>> bikeAndRideAssignment = tagBikeAndRide(schedule);


        schedule.getFacilities().values().stream()
                .forEach(transitStopFacility -> {

                    String fareZone = findFareZone(transitStopFacility, fareZoneFeatures);

                    if (fareZone.isEmpty()){
                        transitStopFacility.getAttributes().putAttribute("ptFareZone", "out");
                    }else{
                        transitStopFacility.getAttributes().putAttribute("ptFareZone", fareZone);
                    }


                    Boolean bikeAndRide = false;

                    if (fareZone!=""){
                        // This means it is assigned a fareZone and thus located in vvs area

                        if (bikeAndRideAssignment.contains(transitStopFacility.getId())){
                            bikeAndRide = true;
                        }

                    }

                    transitStopFacility.getAttributes().putAttribute("VVSBikeAndRide", bikeAndRide);

                });

    }


    private String findFareZone(TransitStopFacility transitStopFacility, Collection<SimpleFeature> features) {

        String fareZone = "";
        Boolean stopInShapes = false;

        Coord homeCoord = transitStopFacility.getCoord();
        Point point = MGC.coord2Point(homeCoord);

        // Automatic crs detection/ transformation might be needed to avoid errors.

        for (SimpleFeature feature : features ) {

            Geometry geometry = (Geometry) feature.getDefaultGeometry();

            if (geometry.contains(point)) {
                fareZone = feature.getAttribute("FareZone").toString();
                stopInShapes = true;
            }

        }


        if (! stopInShapes) {

            // There are several home locations outside the shape File boundaries
            // as for performance reasons the shape File was reduced to Stuttgart Metropolitan Area

            // How to deal with the people that do not live in Stuttgart Metropolitan Area?
            // Will we need more detailed information on their home locations as well?

            //log.info("No fareZone found for transit stop facility with id: " + transitStopFacility.getId());
        }

        return fareZone;

    }


    private List<Id<TransitStopFacility>> tagBikeAndRide(TransitSchedule schedule) {

        // The idea is that all stops with departures of tram and train within vvs area have BikeAndRideFacilities
        // and allow Bike and Ride

        List<Id<TransitStopFacility>> bikeAndRide = new ArrayList<>();
        List<String> modes = Arrays.asList(new String[]{"tram", "train"});

        schedule.getTransitLines().values().stream().forEach(transitLine -> {

            transitLine.getRoutes().values().stream().forEach(transitRoute -> {

                if (modes.contains(transitRoute.getTransportMode())){

                    transitRoute.getStops().stream().forEach(transitRouteStop -> {

                        if (bikeAndRide.contains(transitRouteStop.getStopFacility().getId())){
                            // Already in list
                        }else{
                            bikeAndRide.add(transitRouteStop.getStopFacility().getId());
                        }

                    });
                }

            });

        });

        return bikeAndRide;

    }

}
