package org.matsim.prepare;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkWriter;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.algorithms.NetworkCleaner;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.core.utils.io.OsmNetworkReader;
import picocli.CommandLine;

import java.io.File;
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

    @CommandLine.Parameters(arity = "1", paramLabel = "INPUT", description = "Input osm file", defaultValue = "duesseldorf-regbez-latest.osm.pbf")
    private File osmFile;

    @CommandLine.Option(names = "--output", description = "Output xml file", defaultValue = "duesseldorf-network.xml")
    private File output;

    @CommandLine.Option(names = "--input-cs", description = "Input coordinate system of the data", defaultValue = TransformationFactory.WGS84)
    private String inputCS;

    @CommandLine.Option(names = "--target-cs", description = "Target coordinate system of the network", required = true)
    private String targetCS;

    @Override
    public Integer call() throws Exception {

        CoordinateTransformation ct = TransformationFactory.getCoordinateTransformation(inputCS, targetCS);

        Config config = ConfigUtils.createConfig();
        Scenario scenario = ScenarioUtils.createScenario(config);
        Network network = scenario.getNetwork();

        OsmNetworkReader networkReader = new OsmNetworkReader(network, ct, true, true);
        networkReader.parse(osmFile.getAbsolutePath());

        new NetworkCleaner().run(network);
        new NetworkWriter(network).write(output.getAbsolutePath());

        return 0;
    }

    public static void main(String[] args) {
        System.exit(new CommandLine(new CreateNetwork()).execute(args));
    }
}
