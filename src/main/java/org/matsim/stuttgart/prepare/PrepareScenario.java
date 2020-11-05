package org.matsim.stuttgart.prepare;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

import java.nio.file.Paths;

public class PrepareScenario {

    public static void main(String[] args) {

        var arguments = new InputArgs();
        JCommander.newBuilder().addObject(arguments).build().parse(args);
        var svn = Paths.get(arguments.sharedSvn);

        // have this scope so that the network can be collected by GC if not enough memory
        {
            // get network from osm
            var network = CreateNetwork.createNetwork(svn);

            // write pt schedule files and at pt routes to the network
            CreatePt.create(svn);

            // write the network with pt
            CreateNetwork.writeNetwork(network, svn);
        }

        // clean population from old network references and save it
        CleanPopulation.clean(svn);

        // clean facilities from old network references and save it
        CleanFacilities.clean(svn);
    }

    private static class InputArgs {

        @Parameter(names = {"-sharedSvn"}, required = true)
        String sharedSvn = "https://svn.vsp.tu-berlin.de/repos/shared-svn/";
    }
}
