package org.matsim.stuttgart.prepare;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.bicycle.BicycleUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.gis.ShapeFileReader;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashSet;
import java.util.stream.Collectors;

public class PrepareBikeFriendlyScenario {

    private static final String inputNetwork = "input/stuttgart-v3.0/matsim-stuttgart-v3.0.network.xml.gz";
    private static final String outputNetwork = "input/stuttgart-v3.0/matsim-stuttgart-v3.0.bikeFriendlyNetwork.xml.gz";

    public static void main(String[] args) throws MalformedURLException {

        //noch auf Links in Stuttgart begrenzen!

        var svn = Paths.get("C:/users/schim/IdeaProjects/matsim-stuttgart");

        Network net = NetworkUtils.readNetwork(svn.resolve(inputNetwork).toString());

        URL shapeUrl = new URL("https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/stuttgart/stuttgart-v2.0-10pct/input/lh-stuttgart.shp");
        var dilutionArea = getDilutionArea(shapeUrl);

        for (Link link : net.getLinks().values()) {

            if (
                    link.getAttributes().getAttribute(BicycleUtils.WAY_TYPE) != null &&
                            (
                                    link.getAttributes().getAttribute(BicycleUtils.WAY_TYPE).equals("primary") ||
                                            link.getAttributes().getAttribute(BicycleUtils.WAY_TYPE).equals("primary_link") ||
                                            link.getAttributes().getAttribute(BicycleUtils.WAY_TYPE).equals("secondary") ||
                                            link.getAttributes().getAttribute(BicycleUtils.WAY_TYPE).equals("secondary_link") ||
                                            link.getAttributes().getAttribute(BicycleUtils.WAY_TYPE).equals("tertiary") ||
                                            link.getAttributes().getAttribute(BicycleUtils.WAY_TYPE).equals("tertiary_link")
                            )
                            && link.getAllowedModes().contains(TransportMode.bike)
                            && link.getAllowedModes().contains(TransportMode.car)
                            && dilutionArea.stream().anyMatch(geometry -> geometry.covers(MGC.coord2Point(link.getFromNode().getCoord())))
                            && dilutionArea.stream().anyMatch(geometry -> geometry.covers(MGC.coord2Point(link.getToNode().getCoord())))
            ) {

                if (!(link.getAttributes().getAttribute(BicycleUtils.CYCLEWAY) != null && (
                        link.getAttributes().getAttribute(BicycleUtils.CYCLEWAY).equals("track") ||
                                link.getAttributes().getAttribute(BicycleUtils.CYCLEWAY).equals("track;opposite_track") ||
                                link.getAttributes().getAttribute(BicycleUtils.CYCLEWAY).equals("track;opposite_lane") ||
                                link.getAttributes().getAttribute(BicycleUtils.CYCLEWAY).equals("opposite_track")
                ))) {

                    //determine capacity of new bicycle Link
                    Double capaNewLink = determineNewLinkCapa(link);

                    //reduce capa of old link by capa of new Link; reduce number of lanes on old links if they have at least 2 lanes
                    link.setCapacity(link.getCapacity() - capaNewLink);
                    if (link.getNumberOfLanes() >= 2.) {
                        link.setNumberOfLanes(link.getNumberOfLanes() - 1.);
                    }

                    //create new bike links
                    Link newLink = net.getFactory().createLink(Id.createLinkId(link.getId() + "_cycleway"), link.getFromNode(), link.getToNode());
                    newLink.setCapacity(capaNewLink);
                    newLink.getAttributes().putAttribute(BicycleUtils.WAY_TYPE, link.getAttributes().getAttribute(BicycleUtils.WAY_TYPE));
                    newLink.getAttributes().putAttribute(BicycleUtils.CYCLEWAY, "track");
                    newLink.getAttributes().putAttribute(BicycleUtils.SURFACE, "asphalt");
                    var allowedModes = new HashSet<String>();
                    allowedModes.add(TransportMode.bike);
                    newLink.setAllowedModes(allowedModes);

                    //add new bike links to network
                    net.addLink(newLink);

                }
            }

        }

        NetworkUtils.writeNetwork(net, svn.resolve(outputNetwork).toString());

    }

    private static Double determineNewLinkCapa(Link oldLink) {

        Double stdCapa = switch (oldLink.getAttributes().getAttribute(BicycleUtils.WAY_TYPE).toString()) {
            case "primary", "primary_link" -> 1500.;
            case "secondary", "secondary_link" -> 800.;
            case "tertiary", "tertiary_link" -> 600.;
            default -> 0.;
        };

        if (oldLink.getCapacity() < stdCapa * 2.) {
            return oldLink.getCapacity() / 2.;
        } else {
            return stdCapa;
        }


    }

    private static Collection<PreparedGeometry> getDilutionArea(URL shapeFile) {

        var factory = new PreparedGeometryFactory();
       /* var fromCRS = CRS.decode("EPSG:5677");
        var toCRS = CRS.decode("EPSG:25832");
        var transformation = CRS.findMathTransform(fromCRS, toCRS);

        */

        return ShapeFileReader.getAllFeatures(shapeFile).stream()
                .map(simpleFeature -> (Geometry) simpleFeature.getDefaultGeometry())
                //.map(geometry -> transform(geometry, transformation))
                .map(factory::create)
                .collect(Collectors.toSet());

    }

}
