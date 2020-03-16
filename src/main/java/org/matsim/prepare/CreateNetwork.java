package org.matsim.prepare;

import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkWriter;
import org.matsim.contrib.osm.networkReader.LinkProperties;
import org.matsim.contrib.osm.networkReader.SupersonicOsmNetworkReader;
import org.matsim.core.network.algorithms.NetworkCleaner;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.run.RunDuesseldorfScenario;
import picocli.CommandLine;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.Callable;

/**
 * Creates the road network layer.
 * <p>
 * Use https://download.geofabrik.de/europe/germany/nordrhein-westfalen/duesseldorf-regbez-latest.osm.pbf
 *
 * @author rakow
 */
@CommandLine.Command(
        name = "network",
        description = "Create MATSim network from OSM data",
        showDefaultValues = true
)
public class CreateNetwork implements Callable<Integer> {

    @CommandLine.Parameters(arity = "1", paramLabel = "INPUT", description = "Input osm file", defaultValue = "scenarios/input/network.osm.pbf")
    private Path osmFile;

    @CommandLine.Option(names = "--output", description = "Output xml file", defaultValue = "scenarios/input/duesseldorf-network.xml.gz")
    private File output;

    @CommandLine.Option(names = "--input-cs", description = "Input coordinate system of the data", defaultValue = TransformationFactory.WGS84)
    private String inputCS;

    @CommandLine.Option(names = "--target-cs", description = "Target coordinate system of the network", defaultValue = RunDuesseldorfScenario.COORDINATE_SYSTEM)
    private String targetCS;

    @Override
    public Integer call() throws Exception {

        CoordinateTransformation ct = TransformationFactory.getCoordinateTransformation(inputCS, targetCS);

        Network network = new SupersonicOsmNetworkReader.Builder()
                .setCoordinateTransformation(ct)
                .setIncludeLinkAtCoordWithHierarchy((coord, hierachyLevel) -> hierachyLevel <= LinkProperties.LEVEL_SECONDARY)
//                .addOverridingLinkProperties("residential", new LinkProperties(9, 1, 30.0 / 3.6, 1500, false))
                .setAfterLinkCreated((link, osmTags, isReverse) -> link.setAllowedModes(new HashSet<>(Arrays.asList(TransportMode.car, TransportMode.bike))))
                .build()
                .read(osmFile);


        new NetworkCleaner().run(network);
        new NetworkWriter(network).write(output.getAbsolutePath());

        return 0;
    }

    public static void main(String[] args) {
        System.exit(new CommandLine(new CreateNetwork()).execute(args));
    }
}
