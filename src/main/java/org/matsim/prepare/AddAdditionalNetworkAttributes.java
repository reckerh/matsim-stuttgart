package org.matsim.prepare;


import org.apache.log4j.Logger;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkWriter;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
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
    private static String inputNetwork = "C:/Users/david/OneDrive/02_Uni/02_Master/05_Masterarbeit/03_MATSim/01_Input/stuttgart-inkl-umland-vsp/network-stuttgart.xml.gz";
    private static String outputNetwork = "C:/Users/david/OneDrive/02_Uni/02_Master/05_Masterarbeit/03_MATSim/01_Input/stuttgart-inkl-umland-civity/network-stuttgart-edited.xml.gz";

    private static Collection<SimpleFeature> features = null;
    Network network = NetworkUtils.createNetwork();


    public static void main(String[] args) {

        if (args.length > 0) {
            inputNetwork = args[0];
            outputNetwork = args[1];
            log.info("input plans: " + inputNetwork);
            log.info("output plans: " + outputNetwork);
        }

        AddAdditionalNetworkAttributes extender = new AddAdditionalNetworkAttributes();

        extender.run(inputNetwork, outputNetwork);

    }

    private void run (String inputNetwork, String outputNetwork){


        new MatsimNetworkReader(network).readFile(inputNetwork);


        // ---------------------------
        // -- ADD PARKING ATTRIBUTES

        // Collect shapes from postgis

        //* Code to be inserted here */

        // Read-In shape File
        String shapeFile = "C:/Users/david/OneDrive/02_Uni/02_Master/05_Masterarbeit/03_MATSim/00_InputPrep/01_Parking/test.shp";
        features = ShapeFileReader.getAllFeatures(shapeFile);

        // Do join network with parking shapes
        mergeNetworkLinksWithParkingAttributes(network, features);

        log.info("Parking merge successful!");

        // Write Network Output
        new NetworkWriter(network).write(outputNetwork);

    }


    private void mergeNetworkLinksWithParkingAttributes(Network network, Collection<SimpleFeature> features){

        // Don't forget automatic coordinate detection & transformation ?

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
