package org.matsim.stuttgart.prepare;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationWriter;
import org.matsim.application.prepare.freight.ExtractRelevantFreightTrips;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.stuttgart.Utils;
import picocli.CommandLine;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class MergeFreightTrips {
    private static final Logger log = Logger.getLogger(MergeFreightTrips.class);

    private static final String freightDataDirectory = "projects\\german-wide-freight\\v1.1\\german-wide-freight-25pct.xml.gz";
    private static final String networkPath = "projects\\german-wide-freight\\original-data\\german-primary-road.network.xml.gz";
    private static final String shapeFilePath = "projects\\matsim-stuttgart\\stuttgart-v0.0-snz-original\\stuttgart_umland_5677.shp";
    private static final String inputCrs = "EPSG:5677";
    private static final String outputCrs = "EPSG:25832";

    private static final String freightPopOutputPath = "projects\\matsim-stuttgart\\stuttgart-v2.0\\input\\population-25pct-stuttgart-freight-only.xml.gz";
    private static final String populationInputPath = "projects\\matsim-stuttgart\\stuttgart-v2.0\\input\\optimizedPopulationCleaned.xml.gz";
    private static final String populationOutputPath = "projects\\matsim-stuttgart\\stuttgart-v2.0\\input\\optimizedPopulationWithFreight.xml.gz";


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


        //MergeFreightTrips.extractRelevantFreightTrips(svn);
        MergeFreightTrips.mergePopulationFiles(svn, elevationReader);

    }


    public static void extractRelevantFreightTrips(Path svn) {
        log.info("extract relevant freight trips");

        String[] args = new String[]{
                svn.resolve(freightDataDirectory).toString(),
                "--network", svn.resolve(networkPath).toString(),
                "--shp", svn.resolve(shapeFilePath).toString(),
                "--output", svn.resolve(freightPopOutputPath).toString(),
                "--cut-on-boundary",
                "--input-crs", inputCrs,
                "--target-crs", outputCrs
        };

        (new CommandLine(new ExtractRelevantFreightTrips())).execute(args);
    }


    public static void mergePopulationFiles(Path svn, ElevationReader elevationReader) {
        log.info("merge population files");

        Config config = ConfigUtils.createConfig();
        config.plans().setInputFile(svn.resolve(populationInputPath).toString());
        Scenario inputScenario = ScenarioUtils.loadScenario(config);

        config.plans().setInputFile(svn.resolve(freightPopOutputPath).toString());
        Scenario freightScenario = ScenarioUtils.loadScenario(config);

        for (var person: freightScenario.getPopulation().getPersons().values()){
            inputScenario.getPopulation().addPerson(person);
        }

        // add z values
        inputScenario.getPopulation().getPersons().values().parallelStream()
                .flatMap(person -> person.getPlans().stream())
                .flatMap(plan -> TripStructureUtils.getActivities(plan, TripStructureUtils.StageActivityHandling.ExcludeStageActivities).stream())
                .forEach(activity -> {
                    activity.setCoord(Utils.addElevationIfNecessary(activity.getCoord(), elevationReader));
                });

        new PopulationWriter(inputScenario.getPopulation()).write(svn.resolve(populationOutputPath).toString());

    }

}
