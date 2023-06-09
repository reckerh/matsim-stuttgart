package org.matsim.stuttgart.run;

import ch.sbb.matsim.config.SwissRailRaptorConfigGroup;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorModule;
import com.jogamp.common.net.Uri;
import org.apache.http.client.utils.URLEncodedUtils;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.*;
import org.matsim.application.MATSimApplication;
import org.matsim.contrib.bicycle.BicycleConfigGroup;
import org.matsim.contrib.bicycle.Bicycles;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.config.groups.PlansCalcRouteConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.controler.OutputDirectoryLogging;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.matsim.stuttgart.Utils;
import org.matsim.stuttgart.analysis.TripAnalyzerModule;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static org.matsim.core.config.groups.ControlerConfigGroup.RoutingAlgorithmType.FastAStarLandmarks;

public class RunStuttgart extends MATSimApplication {

    public static void main(String[] args) throws MalformedURLException, FactoryException {

        Config config = loadConfig(args);
        Scenario scenario = loadScenario(config);
        Controler controler = loadControler(scenario);
        controler.run();
    }


    public static Config loadConfig(String[] args, ConfigGroup... modules) {
        OutputDirectoryLogging.catchLogEntries();

        // Materialize bike config group
        BicycleConfigGroup bikeConfigGroup = new BicycleConfigGroup();
        bikeConfigGroup.setBicycleMode(TransportMode.bike);

        // materialize printer config group
        TripAnalyzerModule.PrinterConfigGroup printerConfigGroup = new TripAnalyzerModule.PrinterConfigGroup();

        // materialize stuttgart config group
        var stuttgartConfig = new StuttgartConfigGroup();

        //this feels a little messy, but I guess this is how var-args work
        List<ConfigGroup> moduleList = new ArrayList<>(Arrays.asList(modules));
        moduleList.add(bikeConfigGroup);
        moduleList.add(printerConfigGroup);
        moduleList.add(stuttgartConfig);
        moduleList.add(new SwissRailRaptorConfigGroup());

        var moduleArray = moduleList.toArray(new ConfigGroup[0]);

        Config config = ConfigUtils.loadConfig(args, moduleArray);

        config.plansCalcRoute().setAccessEgressType(PlansCalcRouteConfigGroup.AccessEgressType.accessEgressModeToLink);
        config.qsim().setUsingTravelTimeCheckInTeleportation(true);
        config.qsim().setUsePersonIdForMissingVehicleId(false);
        config.subtourModeChoice().setProbaForRandomSingleTripMode(0.5);
        config.controler().setRoutingAlgorithmType(FastAStarLandmarks);
        config.controler().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);

        final long minDuration = 600;
        final long maxDuration = 3600 * 27;
        final long difference = 600;

        // Activities without opening & closing time
        Utils.createActivityPatterns("home", minDuration, maxDuration, difference).forEach(params -> config.planCalcScore().addActivityParams(params));
        Utils.createActivityPatterns("errands", minDuration, maxDuration, difference).forEach(params -> config.planCalcScore().addActivityParams(params));
        Utils.createActivityPatterns("educ_secondary", minDuration, maxDuration, difference).forEach(params -> config.planCalcScore().addActivityParams(params));
        Utils.createActivityPatterns("educ_higher", minDuration, maxDuration, difference).forEach(params -> config.planCalcScore().addActivityParams(params));


        // Activities with opening & closing time
        Utils.createActivityPatterns("work", minDuration, maxDuration, difference, 6, 20).forEach(params -> config.planCalcScore().addActivityParams(params));
        Utils.createActivityPatterns("business", minDuration, maxDuration, difference, 6, 20).forEach(params -> config.planCalcScore().addActivityParams(params));
        Utils.createActivityPatterns("leisure", minDuration, maxDuration, difference, 9, 27).forEach(params -> config.planCalcScore().addActivityParams(params));
        Utils.createActivityPatterns("shopping", minDuration, maxDuration, difference, 8, 20).forEach(params -> config.planCalcScore().addActivityParams(params));

        // Freight activities
        config.planCalcScore().addActivityParams(new PlanCalcScoreConfigGroup.ActivityParams("freight_start").setTypicalDuration(60 * 15));
        config.planCalcScore().addActivityParams(new PlanCalcScoreConfigGroup.ActivityParams("freight_end").setTypicalDuration(60 * 15));

        return config;
    }

    public static Scenario loadScenario(Config config) {

        Scenario scenario = ScenarioUtils.loadScenario(config);

        //delete all routes from existing legs
        /*for (Person person : scenario.getPopulation().getPersons().values()) {

            for (Plan plan : person.getPlans()){

                for (PlanElement planElement :  plan.getPlanElements()) {

                    if (planElement.getAttributes().getAsMap().containsKey("mode")){

                        Leg leg = (Leg) planElement;
                        leg.setRoute(null);

                    }

                }

            }

        }*/

        //in "cleaned" input files, all modes are set to walk; this can lead to the simulation needing far too many iterations,
        //as (especially with "slow" strategies like changeSingleTripMode) the model needs many iterations just to introduce and try other modes
        //I want to try to counteract this by assigning specific modes based on target values and rng;
        //refinement by changing the target values based on beeline distance are also possible
        //ATTENTION: This approach also ignores things such as chain modes
        /*for (Person person : scenario.getPopulation().getPersons().values()) {

            for (Plan plan : person.getPlans()){

                for (PlanElement planElement : plan.getPlanElements()){

                    if (planElement instanceof Leg) {

                        Leg leg = (Leg) planElement;

                        if (! leg.getMode().equals("freight") ) {

                            double rand = ThreadLocalRandom.current().nextDouble(100.0);
                            if ( rand <= 29.0 ) {
                                leg.setMode(TransportMode.walk);
                            } else if ( rand > 29.0 && rand <= 37.0 ) {
                                leg.setMode(TransportMode.bike);
                            } else if ( rand > 37.0 && rand <= 46.0 ) {
                                leg.setMode(TransportMode.ride);
                            } else if ( rand > 46.0 && rand <= 77.0 ) {
                                leg.setMode(TransportMode.car);
                            } else {
                                leg.setMode(TransportMode.pt);
                            }

                        }

                    }

                }

            }

        }*/

        return scenario;
    }

    public static Controler loadControler(Scenario scenario) throws MalformedURLException, FactoryException {

        Controler controler = new Controler(scenario);
        if (!controler.getConfig().transit().isUsingTransitInMobsim())
            throw new RuntimeException("Public transit will be teleported and not simulated in the mobsim! "
                    + "This will have a significant effect on pt-related parameters (travel times, modal split, and so on). "
                    + "Should only be used for testing or car-focused studies with fixed modal split.");

        controler.addOverridingModule(new SwissRailRaptorModule());
        // use the (congested) car travel time for the teleported ride mode
        controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {
                addTravelTimeBinding(TransportMode.ride).to(networkTravelTime());
                addTravelDisutilityFactoryBinding(TransportMode.ride).to(carTravelDisutilityFactoryKey());
            }
        });

        // add bicycle module
        Bicycles.addAsOverridingModule(controler);

        // create modal share analysis
        // takes path to dilution area from config, then creates a perosn filter and adds it to the analyzer config
        var stuttgartConfig = (StuttgartConfigGroup)controler.getConfig().getModules().get(StuttgartConfigGroup.GROUP_NAME);
        var shapeUrl = ConfigGroup.getInputFileURL(controler.getConfig().getContext(), stuttgartConfig.getDilutionAreaShape());

        var dilutionArea = getDilutionArea(shapeUrl);
        var printerConfig = (TripAnalyzerModule.PrinterConfigGroup)controler.getConfig().getModules().get(TripAnalyzerModule.PrinterConfigGroup.GROUP_NAME);
        printerConfig.setPersonFilter(personId -> {
            var person = scenario.getPopulation().getPersons().get(personId);
            var firstActivity = TripStructureUtils.getActivities(person.getSelectedPlan(), TripStructureUtils.StageActivityHandling.ExcludeStageActivities).get(0);
            return dilutionArea.stream().anyMatch(geometry -> geometry.covers(MGC.coord2Point(firstActivity.getCoord())));
        });
        controler.addOverridingModule(new TripAnalyzerModule());
        return controler;
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

    private static Geometry transform(Geometry geometry, MathTransform transform) {
        try {
            return JTS.transform(geometry, transform);
        } catch (TransformException e) {
            throw new RuntimeException(e);
        }
    }
}
