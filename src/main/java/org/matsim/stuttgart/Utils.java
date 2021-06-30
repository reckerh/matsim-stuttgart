package org.matsim.stuttgart;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.stuttgart.prepare.ElevationReader;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehiclesFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * @author janekdererste
 * @author davidwedekind
 */

public class Utils {

    public static String diluationAreaShape = "projects\\matsim-stuttgart\\stuttgart-v0.0-snz-original\\stuttgart_umland_5677.shp";

    // Copied from https://github.com/matsim-vsp/mosaik-2/blob/master/src/main/java/org/matsim/mosaik2/Utils.java
    public static List<PlanCalcScoreConfigGroup.ActivityParams> createActivityPatterns(String type, long minDurationInSeconds, long maxDurationInSeconds, long durationDifferenceInSeconds) {

        List<PlanCalcScoreConfigGroup.ActivityParams> result = new ArrayList<>();
        for (long duration = minDurationInSeconds; duration <= maxDurationInSeconds; duration += durationDifferenceInSeconds) {
            final PlanCalcScoreConfigGroup.ActivityParams params = new PlanCalcScoreConfigGroup.ActivityParams(type + "_" + duration + ".0");
            params.setTypicalDuration(duration);
            result.add(params);
        }
        return result;
    }


    public static List<PlanCalcScoreConfigGroup.ActivityParams> createActivityPatterns(String type, long minDurationInSeconds, long maxDurationInSeconds, long durationDifferenceInSeconds, double openingHour, double closingHour) {

        List<PlanCalcScoreConfigGroup.ActivityParams> result = createActivityPatterns(type, minDurationInSeconds, maxDurationInSeconds, durationDifferenceInSeconds);
        for (var activityParams: result){
            activityParams.setOpeningTime(openingHour * 3600.);
            activityParams.setClosingTime(closingHour * 3600.);
        }
        return result;

    }


    public static CoordinateTransformation getTransformationWGS84ToUTM32() {
        return TransformationFactory.getCoordinateTransformation("EPSG:4326", "EPSG:25832");
    }


    public static synchronized void addElevationIfNecessary(Node node, ElevationReader elevationReader) {

        var coord = addElevationIfNecessary(node.getCoord(), elevationReader);
        node.setCoord(coord);
    }

    public static synchronized Coord addElevationIfNecessary(Coord coord, ElevationReader elevationReader) {

        if (!coord.hasZ()) {

            //the height map is in WGS-84 but the node was already transformed to UTM-32. Transform it the other way around now.
            var height = elevationReader.getElevationAt(coord);

            // I think it should work to replace the coord on the node reference, since the network only stores references
            // to the node and the internal quad tree only references the x,y-values and the node. janek 4.2020
            return CoordUtils.createCoord(coord.getX(), coord.getY(), height);
        } else {
            return coord;
        }
    }


    public static VehicleType createVehicleType(String id, double length, double maxV, double pce, VehiclesFactory factory) {
        var vehicleType = factory.createVehicleType(Id.create(id, VehicleType.class));
        vehicleType.setNetworkMode(id);
        vehicleType.setPcuEquivalents(pce);
        vehicleType.setLength(length);
        vehicleType.setMaximumVelocity(maxV);
        vehicleType.setWidth(1.0);
        return vehicleType;
    }

    public static InputArgs parseSharedSvn(String[] args) {
        var input = new InputArgs();
        JCommander.newBuilder().addObject(input).build().parse(args);
        return input;
    }

    @SuppressWarnings("FieldMayBeFinal")
    public static class InputArgs {

        @Parameter(names = {"-sharedSvn"}, required = true)
        private String sharedSvn = "https://svn.vsp.tu-berlin.de/repos/shared-svn/";

        public String getSharedSvn() {
            return sharedSvn;
        }
    }



}