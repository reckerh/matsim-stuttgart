package org.matsim.stuttgart;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehiclesFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * @author janekdererste
 * @author davidwedekind
 */

public class Utils {

    private static final Logger logger = LogManager.getLogger("UtilsLogger");
    private static final CoordinateTransformation transformation = TransformationFactory.getCoordinateTransformation("EPSG:4326", "EPSG:25832");


    // Copied from https://github.com/matsim-vsp/mosaik-2/blob/master/src/main/java/org/matsim/mosaik2/Utils.java
    public static List<PlanCalcScoreConfigGroup.ActivityParams> createTypicalDurations(String type, long minDurationInSeconds, long maxDurationInSeconds, long durationDifferenceInSeconds) {

        List<PlanCalcScoreConfigGroup.ActivityParams> result = new ArrayList<>();
        for (long duration = minDurationInSeconds; duration <= maxDurationInSeconds; duration += durationDifferenceInSeconds) {
            final PlanCalcScoreConfigGroup.ActivityParams params = new PlanCalcScoreConfigGroup.ActivityParams(type + "_" + duration + ".0");
            params.setTypicalDuration(duration);
            result.add(params);
        }
        return result;
    }

    public static CoordinateTransformation getTransformationWGS84ToUTM32() {
        return transformation;
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