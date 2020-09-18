package org.matsim.prepare;

import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.pt.transitSchedule.api.TransitScheduleWriter;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.opengis.feature.simple.SimpleFeature;

import java.util.Collection;


/**
 * Adds fare zones to the stop facilities in the transit schedule file
 *
 * @author davidwedekind
 */

public class AddFareZonesToStops {

    private static final Logger log = Logger.getLogger(TagPopulation.class);

    // Hardcoded file locations
    // Otherwise use command line with args: java AddFareZonesToStops <inputSchedulePath> <outputSchedulePath> <PathToFareZonesShape>
    private static String inputSchedule = "C:/Users/david/OneDrive/02_Uni/02_Master/05_Masterarbeit/03_MATSim/02_runs/stuttgart-v0.0-snz-original/optimizedSchedule.xml.gz";
    private static String outputSchedule = "C:/Users/david/OneDrive/02_Uni/02_Master/05_Masterarbeit/03_MATSim/02_runs/optimizedSchedule-edd.xml.gz";
    private static String shapeFile = "C:/Users/david/OneDrive/02_Uni/02_Master/05_Masterarbeit/03_MATSim/01_prep/03_FareZones/fareZones.shp";


    public static void main(String[] args) {

        if (args.length > 0) {
            inputSchedule = args[0];
            outputSchedule = args[1];
            shapeFile = args[2];
            log.info("input plans: " + inputSchedule);
            log.info("output plans: " + outputSchedule);
            log.info("fare zone shape file: " + shapeFile);
        }

        AddFareZonesToStops adder = new AddFareZonesToStops();
        adder.run(inputSchedule, outputSchedule);

    }


    public void run(String inputSchedule, String outputSchedule){

        //Parse the transit schedule
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new TransitScheduleReader(scenario).readFile(inputSchedule);

        // Read-In Shape Files which provide tag inputs
        Collection<SimpleFeature> features = ShapeFileReader.getAllFeatures(shapeFile);

        TransitSchedule schedule = scenario.getTransitSchedule();
        schedule.getFacilities().values().stream()
                .forEach(transitStopFacility -> {

                    String fareZone = findFareZone(transitStopFacility, features);
                    transitStopFacility.getAttributes().putAttribute("FareZone", fareZone);
                });

        new TransitScheduleWriter(schedule).writeFile(outputSchedule);

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

            log.error("No fareZone found for transit stop facility with id: " + transitStopFacility.getId());
        }

        return fareZone;

    }
}
