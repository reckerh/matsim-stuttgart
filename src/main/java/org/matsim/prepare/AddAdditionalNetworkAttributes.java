package org.matsim.prepare;


import org.apache.log4j.Logger;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkWriter;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.opengis.feature.simple.SimpleFeature;

import java.util.Collection;


/**
* @author davidwedekind
*/

public class AddAdditionalNetworkAttributes {

    private static final Logger log = Logger.getLogger(AddAdditionalNetworkAttributes.class);

    // Specify path strings for network in- and output
    private static String inputNetwork = "C:/Users/david/OneDrive/02_Uni/02_Master/05_Masterarbeit/03_MATSim/02_runs/stuttgart-v1.0/stuttgart-v1.0_fstRun01/input/optimizedNetwork.xml.gz";
    private static String outputNetwork = "C:/Users/david/OneDrive/02_Uni/02_Master/05_Masterarbeit/03_MATSim/02_runs/network-stuttgart-edited.xml.gz";
    private static String shapeFile = "C:/Users/david/OneDrive/02_Uni/02_Master/05_Masterarbeit/03_MATSim/01_prep/01_Parking/test.shp";
    private static Collection<SimpleFeature> features = null;


    public static void main(String[] args) {

        if (args.length > 0) {
            inputNetwork = args[0];
            outputNetwork = args[1];
            log.info("input plans: " + inputNetwork);
            log.info("output plans: " + outputNetwork);
        }

        // read-in network
        AddAdditionalNetworkAttributes extender = new AddAdditionalNetworkAttributes();
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        Network network = scenario.getNetwork();
        new MatsimNetworkReader(network).readFile(inputNetwork);

        extender.run(scenario);

        // Write Network Output
        new NetworkWriter(scenario.getNetwork()).write(outputNetwork);
    }


    public void run (Scenario scenario){

        Network network = scenario.getNetwork();

        // ---------------------------
        // -- ADD PARKING ATTRIBUTES

        // Collect shapes from postgis

        //* Code to be inserted here */

        // Read-In shape File

        features = ShapeFileReader.getAllFeatures(shapeFile);

        // Do join network with parking shapes
        mergeNetworkLinksWithParkingAttributes(network, features);

        log.info("Parking merge successful!");

    }


    private void mergeNetworkLinksWithParkingAttributes(Network network, Collection<SimpleFeature> features){

        // Don't forget automatic crs detection & transformation ?

        network.getLinks().values().stream()
                .forEach(link -> {


                    // Which coord is returned? => start node, end node? center of the link?
                    Coord coord = link.getCoord();

                    Point point = MGC.coord2Point(coord);

                    Double dailyPCost = 0.;
                    Double oneHourPCost = 0.;
                    Double extraHourPCost = 0.;
                    Double maxDailyPCost = 0.;

                    for (SimpleFeature feature : features ) {
                        Geometry geometry = (Geometry) feature.getDefaultGeometry();

                        if (geometry.contains(point)) {

                            if (feature.getAttribute("daily") != null){
                                dailyPCost = (Double) feature.getAttribute("daily");
                            }

                            if (feature.getAttribute("oneHour") != null){
                                oneHourPCost = (Double) feature.getAttribute("oneHour");
                            }

                            if (feature.getAttribute("extraHour") != null){
                                extraHourPCost = (Double) feature.getAttribute("extraHour");
                            }

                            if (feature.getAttribute("maxDaily") != null){
                                maxDailyPCost = (Double) feature.getAttribute("maxDaily");
                            }

                            break;
                        }
                    }

                    link.getAttributes().putAttribute("dailyPCost", dailyPCost);
                    link.getAttributes().putAttribute("oneHourPCost", oneHourPCost);
                    link.getAttributes().putAttribute("extraHourPCost", extraHourPCost);
                    link.getAttributes().putAttribute("maxDailyPCost", maxDailyPCost);

                });

    }

}
