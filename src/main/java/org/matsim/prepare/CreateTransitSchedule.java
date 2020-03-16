package org.matsim.prepare;

import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.gtfs.RunGTFS2MATSim;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.run.RunDuesseldorfScenario;
import picocli.CommandLine;

import java.io.File;
import java.time.LocalDate;
import java.util.concurrent.Callable;

/**
 * This script utilizes GTFS2MATSim and creates a pseudo network and vehicles using MATSim standard API functionality.
 *
 * @author rakow
 */
@CommandLine.Command(
        name = "transit",
        description = "Create transit schedule from GTFS data",
        showDefaultValues = true
)
public class CreateTransitSchedule implements Callable<Integer> {

    @CommandLine.Parameters(arity = "1", paramLabel = "INPUT", description = "Input GTFS zip file", defaultValue = "scenarios/input/gtfs.zip")
    private File gtfsZipFile;

    @CommandLine.Option(names = "--output", description = "Output folder", defaultValue = "scenarios/input")
    private File output;

    @CommandLine.Option(names = "--input-cs", description = "Input coordinate system of the data", defaultValue = TransformationFactory.WGS84)
    private String inputCS;

    @CommandLine.Option(names = "--target-cs", description = "Target coordinate system of the network", defaultValue = RunDuesseldorfScenario.COORDINATE_SYSTEM)
    private String targetCS;

    @CommandLine.Option(names = "--date", description = "The day for which the schedules will be extracted", defaultValue = "2020-03-09")
    private LocalDate date;


    @Override
    public Integer call() throws Exception {

        CoordinateTransformation ct = TransformationFactory.getCoordinateTransformation(inputCS, targetCS);

        // Output files
        File scheduleFile = new File(output, "transitSchedule.xml.gz");
        File networkFile = new File(output, "network-with-pt.xml.gz");
        File transitVehiclesFile = new File(output, "transitVehicles.xml.gz");

        RunGTFS2MATSim.convertGtfs(gtfsZipFile.getAbsolutePath(), scheduleFile.getAbsolutePath(), date, ct, false);

        // TODO: filtering and pseudo network

        // Parse the schedule again
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new TransitScheduleReader(scenario).readFile(scheduleFile.getAbsolutePath());

        // Create a network around the schedule
//        new CreatePseudoNetwork(scenario.getTransitSchedule(), scenario.getNetwork(), "pt_" + fileName + "_").createNetwork();


        return null;
    }


    public static void main(String[] args) {
        System.exit(new CommandLine(new CreateTransitSchedule()).execute(args));
    }

}
