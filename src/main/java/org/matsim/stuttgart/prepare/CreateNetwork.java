package org.matsim.stuttgart.prepare;

import org.apache.log4j.Logger;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkWriter;
import org.matsim.api.core.v01.network.Node;
import org.matsim.contrib.osm.networkReader.LinkProperties;
import org.matsim.contrib.osm.networkReader.OsmBicycleReader;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.MultimodalNetworkCleaner;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.stuttgart.Utils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class CreateNetwork {

    private static final Logger log = Logger.getLogger(CreateNetwork.class);

    private static final String senozonNetworkPath = "projects\\matsim-stuttgart\\stuttgart-v0.0-snz-original\\optimizedNetwork.xml.gz";
    private static final String outputNetwork = "projects\\matsim-stuttgart\\stuttgart-v2.0\\input\\network-stuttgart.xml.gz";
    private static final String osmFile = "projects\\matsim-stuttgart\\stuttgart-v2.0\\raw-data\\osm\\germany-20200715.osm.pbf";


    public static void main(String[] args) {
        final Collection<String> elevationData = List.of("projects\\matsim-stuttgart\\stuttgart-v2.0\\raw-data\\heightmaps\\srtm_38_03.tif", "projects\\matsim-stuttgart\\stuttgart-v2.0\\raw-data\\heightmaps\\srtm_39_03.tif");
        final CoordinateTransformation transformUTM32ToWGS84 = TransformationFactory.getCoordinateTransformation("EPSG:25832", "EPSG:4326");

        var arguments = Utils.parseSharedSvn(args);
        var svn = Paths.get(arguments.getSharedSvn());
        var elevationDataPaths = elevationData.stream()
                .map(svn::resolve)
                .map(Path::toString)
                .collect(Collectors.toList());

        var elevationReader = new ElevationReader(elevationDataPaths, transformUTM32ToWGS84);

        var network = createNetwork(Paths.get(arguments.getSharedSvn()), elevationReader);
        writeNetwork(network, Paths.get(arguments.getSharedSvn()));
    }

    public static Network createNetwork(Path svnPath, ElevationReader elevationReader) {

        var bbox = getBBox(svnPath);

        log.info("Starting to parse osm network. This will not output anything for a while until it reaches the interesting part of the osm file.");

        var network = new OsmBicycleReader.Builder()
                .setCoordinateTransformation(Utils.getTransformationWGS84ToUTM32())
                .setIncludeLinkAtCoordWithHierarchy((coord, hierarchy) ->
                        // only include living streets or bigger, and within bbox
                        hierarchy <= LinkProperties.LEVEL_LIVING_STREET && bbox.covers(MGC.coord2Point(coord))
                )
                .setAfterLinkCreated((link, map, direction) -> {

                    // add ride mode on all streets which allow car
                    if (link.getAllowedModes().contains(TransportMode.car)) {
                        var allowedModes = new HashSet<>(link.getAllowedModes());
                        allowedModes.add(TransportMode.ride);
                        allowedModes.add("freight");
                        link.setAllowedModes(allowedModes);
                    }

                    // add z coord
                    Utils.addElevationIfNecessary(link.getFromNode(), elevationReader);
                    Utils.addElevationIfNecessary(link.getToNode(), elevationReader);

                })
                // override the defaults of the bicycle parser with the defaults of the standard parser to reduce memory
                // foot print. Otherwise too many ways are flagged to be included which we filter out later
                .setLinkProperties(new ConcurrentHashMap<>(LinkProperties.createLinkProperties()))
                .build()
                .read(svnPath.resolve(osmFile));

        log.info("Done parsing osm file. ");
        log.info("Starting network cleaner");

        var cleaner = new MultimodalNetworkCleaner(network);
        cleaner.run(Set.of(TransportMode.car));
        cleaner.run(Set.of(TransportMode.ride));
        cleaner.run(Set.of(TransportMode.bike));

        log.info("Finished network cleaner");
        return network;
    }

    public static void writeNetwork(Network network, Path svn) {
        log.info("Writing network to " + svn.resolve(outputNetwork));
        new NetworkWriter(network).write(svn.resolve(outputNetwork).toString());

        log.info("");
        log.info("Finished \uD83C\uDF89");
    }

    private static PreparedGeometry getBBox(Path sharedSvn) {

        log.info("Reading senozon network");
        var senozonNetwork = NetworkUtils.createNetwork();
        new MatsimNetworkReader(senozonNetwork).readFile(sharedSvn.resolve(senozonNetworkPath).toString());

        return BoundingBox.fromNetwork(senozonNetwork).toGeometry();
    }

}
