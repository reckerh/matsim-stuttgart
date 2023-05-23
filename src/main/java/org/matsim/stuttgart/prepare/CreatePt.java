package org.matsim.stuttgart.prepare;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkWriter;
import org.matsim.contrib.gtfs.GtfsConverter;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.pt.transitSchedule.api.TransitScheduleWriter;
import org.matsim.pt.utils.CreatePseudoNetwork;
import org.matsim.pt.utils.CreateVehiclesForSchedule;
import org.matsim.stuttgart.Utils;
import org.matsim.vehicles.MatsimVehicleWriter;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class CreatePt {

    private static final String schedule = "input/stuttgart-v3.0/raw-data/gtfs/vvs_gtfs_realtime_230509.zip";
    private static final String transitSchedule = "input/stuttgart-v3.0/matsim-stuttgart-v3.0.transit-schedule.xml.gz";
    private static final String transitVehicles = "input/stuttgart-v3.0/matsim-stuttgart-v3.0.transit-vehicles.xml.gz";
    private static final String inputNetwork = "input/stuttgart-v3.0/matsim-stuttgart-v3.0.network.xml.gz";


    public static void main(String[] args) {
        final Collection<String> elevationData = List.of("input/stuttgart-v2.0/raw-data/heightmaps/srtm_38_03.tif", "input/stuttgart-v2.0/raw-data/heightmaps/srtm_39_03.tif");
        final CoordinateTransformation transformUTM32ToWGS84 = TransformationFactory.getCoordinateTransformation("EPSG:25832", "EPSG:4326");

        //var arguments = Utils.parseSharedSvn(args);
        var svn = Paths.get("/net/ils/reckermann/matsim-stuttgart"); //

        var network = NetworkUtils.readNetwork(svn.resolve(inputNetwork).toString());

        var elevationDataPaths = elevationData.stream()
                .map(svn::resolve)
                .map(Path::toString)
                .collect(Collectors.toList());

        var elevationReader = new ElevationReader(elevationDataPaths, transformUTM32ToWGS84);

        create(svn, network, elevationReader);
        CreateNetworkWithBikeInfra.writeNetwork(network, svn);
    }

    public static void create(Path sharedSvn, Network network, ElevationReader elevationReader) {

        var scenario = ScenarioUtils.createMutableScenario(ConfigUtils.createConfig());
        scenario.setNetwork(network);

        GtfsConverter.newBuilder()
                .setScenario(scenario)
                .setTransform(Utils.getTransformationWGS84ToUTM32())
                .setDate(LocalDate.of(2023, 5, 9))
                .setFeed(sharedSvn.resolve(schedule))
                .build()
                .convert();

        //Create simple transit vehicles with a pcu of 0
        new CreateVehiclesForSchedule(scenario.getTransitSchedule(), scenario.getTransitVehicles()).run();
        scenario.getTransitVehicles().getVehicleTypes().forEach((id, type) -> type.setPcuEquivalents(0));

        new CreatePseudoNetwork(scenario.getTransitSchedule(), scenario.getNetwork(), "pt_").createNetwork();

        // add z-Coordinates in transit network
        for (var node: scenario.getNetwork().getNodes().values()){
            // set all to elevation profile
            Utils.addElevationIfNecessary(node, elevationReader);
        }

        // and for stop facilities in transit schedule
        for (var stopFacility: scenario.getTransitSchedule().getFacilities().values()){
            Coord coord = stopFacility.getCoord();
            stopFacility.setCoord(Utils.addElevationIfNecessary(coord, elevationReader));
        }

        writeScheduleVehiclesAndNetwork(scenario, sharedSvn);
    }

    private static void writeScheduleVehiclesAndNetwork(Scenario scenario, Path svn) {

        new TransitScheduleWriter(scenario.getTransitSchedule()).writeFile(svn.resolve(transitSchedule).toString());
        new MatsimVehicleWriter(scenario.getTransitVehicles()).writeFile(svn.resolve(transitVehicles).toString());

    }
}
