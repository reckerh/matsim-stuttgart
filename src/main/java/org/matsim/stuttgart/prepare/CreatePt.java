package org.matsim.stuttgart.prepare;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import org.matsim.stuttgart.Utils;
import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.gtfs.GtfsConverter;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.TransitScheduleWriter;
import org.matsim.pt.utils.CreatePseudoNetwork;
import org.matsim.vehicles.MatsimVehicleWriter;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;

public class CreatePt {

    private static final String schedule = "projects\\mosaik-2\\raw-data\\gtfs\\vvs_gtfs_20201105.zip";
    private static final String transitSchedule = "projects\\matsim-stuttgart\\stuttgart-v2.0\\input\\transitSchedule-stuttgart.xml.gz";
    private static final String transitVehicles = "projects\\matsim-stuttgart\\stuttgart-v2.0\\input\\transitVehicles-stuttgart.xml.gz";

    public static void main(String[] args) {

        var arguments = Utils.parseSharedSvn(args);
        create(Paths.get(arguments.getSharedSvn()));
    }

    public static void create(Path sharedSvn) {

        var scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());

        GtfsConverter.newBuilder()
                .setScenario(scenario)
                .setTransform(Utils.getTransformationWGS84ToUTM32())
                .setDate(LocalDate.now())
                .setFeed(sharedSvn.resolve(schedule))
                .build()
                .convert();

        new CreatePseudoNetwork(scenario.getTransitSchedule(), scenario.getNetwork(), "pt_").createNetwork();
        writeScheduleAndVehicles(scenario, sharedSvn);
    }

    private static void writeScheduleAndVehicles(Scenario scenario, Path svn) {

        new TransitScheduleWriter(scenario.getTransitSchedule()).writeFile(svn.resolve(transitSchedule).toString());
        new MatsimVehicleWriter(scenario.getTransitVehicles()).writeFile(svn.resolve(transitVehicles).toString());
    }
}
