package org.matsim.prepare;

import com.google.common.collect.Sets;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.locationtech.jts.geom.Geometry;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.*;
import org.matsim.contrib.osm.networkReader.LinkProperties;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.NetworkCleaner;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.matsim.lanes.*;
import org.matsim.run.RunDuesseldorfScenario;
import org.xml.sax.SAXException;
import picocli.CommandLine;

import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

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

    private static final Logger log = LogManager.getLogger(CreateNetwork.class);

    @CommandLine.Parameters(arity = "1..*", paramLabel = "INPUT", description = "Input file", defaultValue = "scenarios/input/sumo.net.xml")
    private List<Path> input;

    @CommandLine.Option(names = "--output", description = "Output xml file", defaultValue = "scenarios/input/duesseldorf-network.xml.gz")
    private File output;

    @CommandLine.Option(names = "--shp", description = "Shape file used for filtering",
            defaultValue = "../../shared-svn/komodnext/matsim-input-files/duesseldorf-senozon/dilutionArea/dilutionArea.shp")
    private Path shapeFile;

    public static void main(String[] args) {
        System.exit(new CommandLine(new CreateNetwork()).execute(args));
    }

    /**
     * Network area based on the cut-out.
     */
    static Geometry calculateNetworkArea(Path shapeFile) {
        // only the first feature is used
        return ((Geometry) ShapeFileReader.getAllFeatures(shapeFile.toString()).iterator().next().getDefaultGeometry());
    }

    @Override
    public Integer call() throws Exception {

        // CoordinateTransformation ct = TransformationFactory.getCoordinateTransformation(inputCS, targetCS);

        /*
        Network network = new SupersonicOsmNetworkReader.Builder()
                .setCoordinateTransformation(ct)
                .setIncludeLinkAtCoordWithHierarchy((coord, hierachyLevel) ->
                        hierachyLevel <= LinkProperties.LEVEL_RESIDENTIAL &&
                                coord.getX() >= RunDuesseldorfScenario.X_EXTENT[0] && coord.getX() <= RunDuesseldorfScenario.X_EXTENT[1] &&
                                coord.getY() >= RunDuesseldorfScenario.Y_EXTENT[0] && coord.getY() <= RunDuesseldorfScenario.Y_EXTENT[1]
                )

//                .addOverridingLinkProperties("residential", new LinkProperties(9, 1, 30.0 / 3.6, 1500, false))
                .setAfterLinkCreated((link, osmTags, isReverse) -> link.setAllowedModes(new HashSet<>(Arrays.asList(TransportMode.car, TransportMode.bike, TransportMode.ride))))
                .build()
                .read(input);

         */

        Network network = NetworkUtils.createNetwork();
        Lanes lanes = LanesUtils.createLanesContainer();

        convert(network, lanes);

        // This needs to run without errors, otherwise network is broken
        network.getLinks().values().forEach(link -> {
            LanesToLinkAssignment l2l = lanes.getLanesToLinkAssignments().get(link.getId());
            if (l2l != null)
                LanesUtils.createLanes(link, l2l);
        });


        new NetworkWriter(network).write(output.getAbsolutePath());
        new LanesWriter(lanes).write(output.getAbsolutePath().replace(".xml", "-lanes.xml"));

        return 0;
    }

    private void convert(Network network, Lanes lanes) throws ParserConfigurationException, SAXException, IOException {

        log.info("Parsing SUMO network");

        SumoNetworkHandler sumoHandler = SumoNetworkHandler.read(input.get(0).toFile());
        log.info("Parsed {} edges with {} junctions", sumoHandler.edges.size(), sumoHandler.junctions.size());

        for (int i = 1; i < input.size(); i++) {

            CoordinateTransformation ct = TransformationFactory.getCoordinateTransformation("EPSG:32632", RunDuesseldorfScenario.COORDINATE_SYSTEM);

            File file = input.get(i).toFile();
            SumoNetworkHandler other = SumoNetworkHandler.read(file);
            log.info("Merging {} edges with {} junctions from {} into base network", other.edges.size(), other.junctions.size(), file);
            sumoHandler.merge(other, ct);
        }

        NetworkFactory f = network.getFactory();
        LanesFactory lf = lanes.getFactory();

        Map<String, LinkProperties> linkProperties = LinkProperties.createLinkProperties();

        for (SumoNetworkHandler.Edge edge : sumoHandler.edges.values()) {

            // skip railways and unknowns
            if (edge.type == null || !edge.type.startsWith("highway"))
                continue;

            Link link = f.createLink(Id.createLinkId(edge.id),
                    createNode(network, sumoHandler, edge.from),
                    createNode(network, sumoHandler, edge.to)
            );

            link.setNumberOfLanes(edge.lanes.size());
            Set<String> modes = Sets.newHashSet(TransportMode.car, TransportMode.ride);

            SumoNetworkHandler.Type type = sumoHandler.types.get(edge.type);

            if (type.allow.contains("bicycle") || (type.allow.isEmpty() && !type.disallow.contains("bicycle")))
                modes.add(TransportMode.bike);

            link.setAllowedModes(modes);
            link.setLength(edge.lanes.get(0).length);
            LanesToLinkAssignment l2l = lf.createLanesToLinkAssignment(link.getId());

            for (SumoNetworkHandler.Lane lane : edge.lanes) {
                Lane mLane = lf.createLane(Id.create(lane.id, Lane.class));
                mLane.setAlignment(lane.index);
                mLane.setStartsAtMeterFromLinkEnd(lane.length);
                l2l.addLane(mLane);
            }


            // incoming lane connected to the others
            // this is needed by matsim for lanes to work properly
            if (edge.lanes.size() >= 1) {
                Lane inLane = lf.createLane(Id.create(link.getId() + "_in", Lane.class));
                inLane.setStartsAtMeterFromLinkEnd(link.getLength());
                inLane.setAlignment(0);

                l2l.getLanes().keySet().forEach(inLane::addToLaneId);
                l2l.addLane(inLane);
            }

            // set link prop based on MATSim defaults
            LinkProperties prop = linkProperties.get(type.highway);

            if (prop == null) {
                log.warn("Skipping unknown link type: {}", type.highway);
                continue;
            }

            link.setFreespeed(LinkProperties.calculateSpeedIfSpeedTag(type.speed));
            link.setCapacity(LinkProperties.getLaneCapacity(link.getLength(), prop) * link.getNumberOfLanes());

            lanes.addLanesToLinkAssignment(l2l);
            network.addLink(link);
        }

        Geometry shp = calculateNetworkArea(shapeFile);

        // remove lanes outside survey area
        for (Node node : network.getNodes().values()) {
            if (!shp.contains(MGC.coord2Point(node.getCoord()))) {
                node.getOutLinks().keySet().forEach(l -> lanes.getLanesToLinkAssignments().remove(l));
                node.getInLinks().keySet().forEach(l -> lanes.getLanesToLinkAssignments().remove(l));
            }
        }

        // clean up network
        new NetworkCleaner().run(network);

        // also clean lanes
        lanes.getLanesToLinkAssignments().keySet().removeIf(l2l -> !network.getLinks().containsKey(l2l));

        for (List<SumoNetworkHandler.Connection> connections : sumoHandler.connections.values()) {
            for (SumoNetworkHandler.Connection conn : connections) {

                Id<Link> fromLink = Id.createLinkId(conn.from);
                Id<Link> toLink = Id.createLinkId(conn.to);

                LanesToLinkAssignment l2l = lanes.getLanesToLinkAssignments().get(fromLink);

                // link was removed
                if (l2l == null)
                    continue;

                Lane lane = l2l.getLanes().values().stream().filter(l -> l.getAlignment() == conn.fromLane).findFirst().orElse(null);
                if (lane == null) {
                    log.warn("Could not find from lane in network for {}", conn);
                    continue;
                }

                lane.addToLinkId(toLink);
            }
        }

        int removed = 0;

        Iterator<LanesToLinkAssignment> it = lanes.getLanesToLinkAssignments().values().iterator();

        // lanes needs to have a target, if missing we need to chose one
        while (it.hasNext()) {
            LanesToLinkAssignment l2l = it.next();

            for (Lane lane : l2l.getLanes().values()) {
                if (lane.getToLinkIds() == null && lane.getToLaneIds() == null) {
                    // chose first reachable link from this lane
                    Link out = network.getLinks().get(l2l.getLinkId()).getToNode().getOutLinks().values().iterator().next();
                    lane.addToLinkId(out.getId());

                    log.warn("No target for lane {}, chosen {}", lane.getId(), out);
                }
            }

            Set<Id<Link>> targets = l2l.getLanes().values().stream()
                    .filter(l -> l.getToLinkIds() != null)
                    .map(Lane::getToLinkIds).flatMap(List::stream)
                    .collect(Collectors.toSet());

            // remove superfluous lanes (both pointing to same link with not alternative)
            if (targets.size() == 1 && network.getLinks().get(l2l.getLinkId()).getToNode().getOutLinks().size() <= 1) {
                it.remove();
                removed++;
            }
        }

        log.info("Removed {} superfluous lanes, total={}", removed, lanes.getLanesToLinkAssignments().size());
    }

    private Node createNode(Network network, SumoNetworkHandler sumoHandler, String nodeId) {

        Id<Node> id = Id.createNodeId(nodeId);
        Node node = network.getNodes().get(id);
        if (node != null)
            return node;

        Coord coord;
        if (sumoHandler.junctions.containsKey(nodeId)) {
            coord = sumoHandler.createCoord(sumoHandler.junctions.get(nodeId).coord);
        } else {
            throw new IllegalStateException("Junction not in network:" + nodeId);
        }

        node = network.getFactory().createNode(id, coord);
        network.addNode(node);

        return node;
    }

}
