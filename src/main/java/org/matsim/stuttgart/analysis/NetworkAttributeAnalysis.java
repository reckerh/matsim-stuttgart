package org.matsim.stuttgart.analysis;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.utils.io.IOUtils;

import java.io.IOException;

public class NetworkAttributeAnalysis {
    //the purpose of this class is to read out selected network attributes and write them into a CSV for analysis in other software

    public static void main(String[] args){
        Network network = NetworkUtils.readNetwork("input/stuttgart-v3.0/matsim-stuttgart-v3.0.bikeFriendlyNetwork.xml.gz");
        String[] HEADER = new String[]{
                "linkId", "capacity", "allowedModes", "fromNodeId", "toNodeId",
                "fromX", "fromY", "fromZ",
                "toX", "toY", "toZ",
                "bike", "surface", "type", "cyclewaytype", "lcn", "numLanes"
        };

        try {

            CSVPrinter csvPrinter = new CSVPrinter(IOUtils.getBufferedWriter("R_Analyses/input/stuttgart-v3.0/networkAttributesStuttgartBikeFriendly.csv"),
                    CSVFormat.DEFAULT.withDelimiter(',').withHeader(HEADER));

            for (Link link : network.getLinks().values()){
                csvPrinter.printRecord(
                        link.getId(),
                        link.getCapacity(),
                        link.getAllowedModes(),
                        link.getFromNode().getId(),
                        link.getToNode().getId(),
                        link.getFromNode().getCoord().getX(),
                        link.getFromNode().getCoord().getY(),
                        link.getFromNode().getCoord().getZ(),
                        link.getToNode().getCoord().getX(),
                        link.getToNode().getCoord().getY(),
                        link.getToNode().getCoord().getZ(),
                        link.getAttributes().getAttribute("bike"),
                        link.getAttributes().getAttribute("surface"),
                        link.getAttributes().getAttribute("type"),
                        link.getAttributes().getAttribute("cycleway"),
                        link.getAttributes().getAttribute("lcn"),
                        link.getNumberOfLanes()
                );
                //System.out.println(link.getAttributes().getAttribute("surface"));
            }

            csvPrinter.close();

        } catch (IOException e) {
            e.printStackTrace();
        }



    }


}
