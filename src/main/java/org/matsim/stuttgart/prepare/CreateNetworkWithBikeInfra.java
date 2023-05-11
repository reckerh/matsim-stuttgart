package org.matsim.stuttgart.prepare;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkWriter;
import org.matsim.contrib.osm.networkReader.LinkProperties;
import org.matsim.contrib.osm.networkReader.OsmBicycleReader;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.MultimodalNetworkCleaner;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.stuttgart.Utils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class CreateNetworkWithBikeInfra {

    private static final Logger log = LogManager.getLogger(CreateNetworkWithBikeInfra.class);

    private static final String senozonNetworkPath = "input/stuttgart-v0.0-snz-original/optimizedNetwork.xml.gz";
    private static final String outputNetwork = "input/stuttgart-v3.0/matsim-stuttgart-v3.0.network.xml.gz";
    private static final String osmFile = "input/stuttgart-v3.0/raw-data/osm/germany-230503.osm.pbf";


    public static void main(String[] args) {
        final Collection<String> elevationData = List.of("input/stuttgart-v2.0/raw-data/heightmaps/srtm_38_03.tif", "input/stuttgart-v2.0/raw-data/heightmaps/srtm_39_03.tif");
        final CoordinateTransformation transformUTM32ToWGS84 = TransformationFactory.getCoordinateTransformation("EPSG:25832", "EPSG:4326");

        //var arguments = Utils.parseSharedSvn(args);
        var svn = Paths.get("/net/ils/reckermann/matsim-stuttgart");
        var elevationDataPaths = elevationData.stream()
                .map(svn::resolve)
                .map(Path::toString)
                .collect(Collectors.toList());

        var elevationReader = new ElevationReader(elevationDataPaths, transformUTM32ToWGS84);

        var network = createNetwork(svn, elevationReader);
        writeNetwork(network, svn);
    }

    public static Network createNetwork(Path svnPath, ElevationReader elevationReader) {

        var bbox = getBBox(svnPath);

        log.info("Starting to parse osm network. This will not output anything for a while until it reaches the interesting part of the osm file.");

        var network = new OsmBicycleReader.Builder()
                .setCoordinateTransformation(Utils.getTransformationWGS84ToUTM32())
                .setIncludeLinkAtCoordWithHierarchy((coord, hierarchy) ->
                        // only include path or bigger, and within bbox
                        hierarchy <= 10 && bbox.covers(MGC.coord2Point(coord))
                        //Link-Properties für für Rad relevante Links sind definiert in package org.matsim.contrib.osm.networkReader.OsmBicycleReader.java (in Teil zu addOverridingLinkProperties)
                        //-> meine Implementation schließt alles bis auf Steps aus
                )
                .setAfterLinkCreated((link, map, direction) -> {

                    // add ride mode on all streets which allow car
                    if (link.getAllowedModes().contains(TransportMode.car)) {
                        var allowedModes = new HashSet<>(link.getAllowedModes());
                        allowedModes.add(TransportMode.ride);
                        allowedModes.add("freight");
                        link.setAllowedModes(allowedModes);
                    }

                    //add lcn (local cycleway network) key-value to attributes
                    if (map.containsKey("lcn")){
                        link.getAttributes().putAttribute("lcn", map.get("lcn"));
                    }

                    // add z coord
                    Utils.addElevationIfNecessary(link.getFromNode(), elevationReader);
                    Utils.addElevationIfNecessary(link.getToNode(), elevationReader);

                })
                // override the defaults of the bicycle parser with the defaults of the standard parser to reduce memory
                // foot print. Otherwise too many ways are flagged to be included which we filter out later
                //.setLinkProperties(new ConcurrentHashMap<>(LinkProperties.createLinkProperties()))
                // store the original geometry information in the link. we need this for our palm analysis
                .setStoreOriginalGeometry(true)
                .build()
                .read(svnPath.resolve(osmFile));

        log.info("Done parsing osm file. ");


        log.info("Removing lower-priority links without bike infrastructure");

        //remove links with lower priorities that are private roads (-> service) or are not expected to represent bike infrastructure by hand
        //temporarily: Check if actually all road types (even those with lower priority) are present
        //TODO: Make sure that all additionally-added links only allow bicycle
        HashMap<String,Integer> collectedTypes = new HashMap<>();
        for (Link link : network.getLinks().values()){
            String hwValue = (String) link.getAttributes().getAttribute("type");
            collectedTypes.put(hwValue, collectedTypes.getOrDefault(hwValue, 0)+1);

            //remove private (-> service) roads
            if( hwValue.equals("service") ){
                network.removeLink(link.getId());
            }

            //filter other lower-priority highway types (except cycleway) for expected bike infrastructure
            if( hwValue.equals("track") || hwValue.equals("path") || hwValue.equals("footway") || hwValue.equals("pedestrian")){

                if(! (
                        ( link.getAttributes().getAttribute("bike") != null && ( link.getAttributes().getAttribute("bike").equals("yes") || link.getAttributes().getAttribute("bike").equals("designated") ) ) ||
                                ( link.getAttributes().getAttribute("lcn") != null && link.getAttributes().getAttribute("lcn").equals("yes") )
                        )) {
                    network.removeLink(link.getId());
                }

            }

            if( ( hwValue.equals("track") || hwValue.equals("path") || hwValue.equals("footway") || hwValue.equals("pedestrian") || hwValue.equals("cycleway") ) &&
            network.getLinks().get(link.getId())!=null ){
                var allowedModes = new HashSet<String>();
                allowedModes.add(TransportMode.bike);
                network.getLinks().get(link.getId()).setAllowedModes(allowedModes);
            }

        }

        for (String key : collectedTypes.keySet()){
            log.info(key + ": "+collectedTypes.get(key));
        }


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

