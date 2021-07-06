package org.matsim.stuttgart.run;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.application.MATSimApplication;
import org.matsim.contrib.bicycle.BicycleConfigGroup;
import org.matsim.contrib.bicycle.Bicycles;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.config.groups.PlansCalcRouteConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.matsim.stuttgart.Utils;
import org.matsim.stuttgart.analysis.TripAnalyzerModule;
import org.opengis.referencing.FactoryException;
import picocli.CommandLine;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static org.matsim.core.config.groups.ControlerConfigGroup.RoutingAlgorithmType.FastAStarLandmarks;

@CommandLine.Command(header = ":: Open Stuttgart Scenario ::", version = StuttgartApplication.VERSION)
public class StuttgartApplication extends MATSimApplication {

    /**
     * Current version identifier.
     */
    public static final String VERSION = "v2.0";

    public static void main(String[] args) {
        MATSimApplication.run(StuttgartApplication.class, args);
    }

    @Override
    protected List<ConfigGroup> getCustomModules() {
        // Materialize bike config group
        BicycleConfigGroup bikeConfigGroup = new BicycleConfigGroup();
        bikeConfigGroup.setBicycleMode(TransportMode.bike);

        // materialize printer config group
        TripAnalyzerModule.PrinterConfigGroup printerConfigGroup = new TripAnalyzerModule.PrinterConfigGroup();

        // materialize stuttgart config group
        var stuttgartConfig = new StuttgartConfigGroup();

        return List.of(bikeConfigGroup, printerConfigGroup, stuttgartConfig);
    }

    @Override
    protected Config prepareConfig(Config config) {
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

    @Override
    protected void prepareScenario(Scenario scenario) {

        // don't do anything
    }

    @Override
    protected void prepareControler(Controler controler) {

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
            var person = controler.getScenario().getPopulation().getPersons().get(personId);
            var firstActivity = TripStructureUtils.getActivities(person.getSelectedPlan(), TripStructureUtils.StageActivityHandling.ExcludeStageActivities).get(0);
            return dilutionArea.stream().anyMatch(geometry -> geometry.covers(MGC.coord2Point(firstActivity.getCoord())));
        });
        controler.addOverridingModule(new TripAnalyzerModule());
    }

    private static Collection<PreparedGeometry> getDilutionArea(URL shapeFile) {

        var factory = new PreparedGeometryFactory();

        return ShapeFileReader.getAllFeatures(shapeFile).stream()
                .map(simpleFeature -> (Geometry) simpleFeature.getDefaultGeometry())
                .map(factory::create)
                .collect(Collectors.toSet());
    }
}
