package org.matsim.stuttgart.prepare;

import org.matsim.application.prepare.freight.tripExtraction.ExtractRelevantFreightTrips;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.stuttgart.Utils;
import picocli.CommandLine;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class PrepareScenario {
    private static final Collection<String> elevationData = List.of("projects\\matsim-stuttgart\\stuttgart-v2.0\\raw-data\\heightmaps\\srtm_38_03.tif", "projects\\matsim-stuttgart\\stuttgart-v2.0\\raw-data\\heightmaps\\srtm_39_03.tif");
    private static final CoordinateTransformation transformUTM32ToWGS84 = TransformationFactory.getCoordinateTransformation("EPSG:25832", "EPSG:4326");
    private static final String filename = "matsim-stuttgart-v2.0";

    public static void main(String[] args) {

        var arguments = Utils.parseSharedSvn(args);
        var svn = Paths.get(arguments.getSharedSvn());

        // create elevationReader on this level as it is needed for several z-coord additions
        var elevationDataPaths = elevationData.stream()
                .map(svn::resolve)
                .map(Path::toString)
                .collect(Collectors.toList());
        var elevationReader = new ElevationReader(elevationDataPaths, transformUTM32ToWGS84);

        // have this scope so that the network can be collected by GC if not enough memory
        {
            // get network from osm
            var network = CreateNetwork.createNetwork(svn, elevationReader);

            // write pt schedule files and at pt routes to the network
            CreatePt.create(svn, network, elevationReader);

            // write the network with pt
            CreateNetwork.writeNetwork(network, svn);
        }

        // print statistics about original population
        CheckPopulation.check(svn);

        // Extract freight trips population
        MergeFreightTrips.extractRelevantFreightTrips(svn);

        // clean population from old network references and save it
        CleanPopulation.clean(svn);

        // Merge cleaned population and freight population
        MergeFreightTrips.mergePopulationFiles(svn, elevationReader);

        // Create downscaled populations
        ReducePopulation.createDownsamples(svn);

        // clean facilities from old network references and save it
        CleanFacilities.clean(svn);

        // create vehicles
        CreateVehicleTypes.create(svn);
    }
}
