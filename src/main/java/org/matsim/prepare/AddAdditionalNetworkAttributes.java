package org.matsim.prepare;


import org.apache.log4j.Logger;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkWriter;
import org.matsim.core.config.ConfigUtils;
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

        extender.run(scenario, shapeFile);

        // Write Network Output
        new NetworkWriter(scenario.getNetwork()).write(outputNetwork);
    }


    public void run (Scenario scenario, String shapeFile){

        Network network = scenario.getNetwork();

        // Read-In shape File

        features = ShapeFileReader.getAllFeatures(shapeFile);

        // Do join network with parking shapes
        mergeNetworkLinksWithParkingAttributes(network, features);

        log.info("Parking merge successful!");

    }


    private void mergeNetworkLinksWithParkingAttributes(Network network, Collection<SimpleFeature> features){


        network.getLinks().values().stream()
                .forEach(link -> {

                    if (link.getAllowedModes().contains("pt")){

                        // pTLinks are not relevant for parking

                    }else{

                        // Which coord is returned? => start node, end node? center of the link?
                        Coord coord = link.getCoord();

                        Point point = MGC.coord2Point(coord);

                        Double oneHourPCost = 0.;
                        Double extraHourPCost = 0.;
                        Double maxDailyPCost = 0.;
                        Integer maxParkingTime = 1800;
                        Double pFine = 0.;
                        Double resPCosts = 0.;
                        String zoneName = "";
                        String zoneGroup = "";


                        for (SimpleFeature feature : features ) {
                            Geometry geometry = (Geometry) feature.getDefaultGeometry();


                            if (geometry.contains(point)) {

                                if (feature.getAttribute("zone_name") != null){
                                    zoneName = (String) feature.getAttribute("zone_name");
                                }

                                if (feature.getAttribute("zone_group") != null){
                                    zoneGroup = (String) feature.getAttribute("zone_group");
                                }

                                if (feature.getAttribute("h_costs") != null){
                                    oneHourPCost = (Double) feature.getAttribute("h_costs");
                                }

                                if (feature.getAttribute("h_costs") != null){
                                    extraHourPCost = (Double) feature.getAttribute("h_costs");
                                }

                                if (feature.getAttribute("dmax_costs") != null){
                                    maxDailyPCost = (Double) feature.getAttribute("dmax_costs");
                                }

                                if (feature.getAttribute("max_time") != null){
                                    maxParkingTime = (Integer) feature.getAttribute("max_time");
                                }

                                if (feature.getAttribute("penalty") != null){
                                    pFine = (Double) feature.getAttribute("penalty");
                                }

                                if (feature.getAttribute("res_costs") != null){
                                    resPCosts = (Double) feature.getAttribute("res_costs");
                                }

                                break;
                            }
                        }

                        link.getAttributes().putAttribute("oneHourPCost", oneHourPCost);
                        link.getAttributes().putAttribute("extraHourPCost", extraHourPCost);
                        link.getAttributes().putAttribute("maxDailyPCost", maxDailyPCost);
                        link.getAttributes().putAttribute("maxPTime", maxParkingTime);
                        link.getAttributes().putAttribute("pFine", pFine);
                        link.getAttributes().putAttribute("resPCosts", resPCosts);
                        link.getAttributes().putAttribute("zoneName", zoneName);
                        link.getAttributes().putAttribute("zoneGroup", zoneGroup);

                    }


                });

    }

}
