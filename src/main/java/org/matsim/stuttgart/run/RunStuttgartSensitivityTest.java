package org.matsim.stuttgart.run;

import ch.sbb.matsim.config.SwissRailRaptorConfigGroup;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorModule;
import org.geotools.geometry.jts.JTS;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.application.MATSimApplication;
import org.matsim.contrib.bicycle.BicycleConfigGroup;
import org.matsim.contrib.bicycle.BicycleUtils;
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
import org.matsim.stuttgart.analysis.CountingStation;
import org.matsim.stuttgart.analysis.LinkCountHandler;
import org.matsim.stuttgart.analysis.TripAnalyzerModule;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

import static org.matsim.core.config.groups.ControlerConfigGroup.RoutingAlgorithmType.FastAStarLandmarks;

public class RunStuttgartSensitivityTest extends MATSimApplication {
    //This model sensitivity test will make a central bridge in Stuttgart (König-Karls-Brücke) inaccessible to ride, freight, car

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


        //create sensitivity test / make König-Karls-Brücke inaccessible
        Network network = scenario.getNetwork();

        for (String idString : Arrays.asList("2993895700003f", "2615633160000f")) {

            // get relevant links of bridge
            Link link = network.getLinks().get(Id.createLinkId(idString));

            // create new links to exclusively allow bikes to use the existing infrastructure (while not changing bicycle-scoring-relevant attributes)
            Link newLink = network.getFactory().createLink(Id.createLinkId(idString + "_bikeExclusive"),
                    link.getFromNode(), link.getToNode());
            newLink.setCapacity(link.getCapacity());
            newLink.getAttributes().putAttribute(BicycleUtils.WAY_TYPE, link.getAttributes().getAttribute(BicycleUtils.WAY_TYPE));
            var allowedModes = new HashSet<String>();
            allowedModes.add(TransportMode.bike);
            newLink.setAllowedModes(allowedModes);
            network.addLink(newLink);

            // set capa of "old" links to 0
            link.setCapacity(0.);

        }

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
        var stuttgartConfig = (StuttgartConfigGroup) controler.getConfig().getModules().get(StuttgartConfigGroup.GROUP_NAME);
        var shapeUrl = ConfigGroup.getInputFileURL(controler.getConfig().getContext(), stuttgartConfig.getDilutionAreaShape());

        var dilutionArea = getDilutionArea(shapeUrl);
        var printerConfig = (TripAnalyzerModule.PrinterConfigGroup) controler.getConfig().getModules().get(TripAnalyzerModule.PrinterConfigGroup.GROUP_NAME);
        printerConfig.setPersonFilter(personId -> {
            var person = scenario.getPopulation().getPersons().get(personId);
            var firstActivity = TripStructureUtils.getActivities(person.getSelectedPlan(), TripStructureUtils.StageActivityHandling.ExcludeStageActivities).get(0);
            return dilutionArea.stream().anyMatch(geometry -> geometry.covers(MGC.coord2Point(firstActivity.getCoord())));
        });

        //create counting station network
        new LinkCountHandler.CountingNetwork();
        createCountingNetwork();
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

    private static void createCountingNetwork() {
        LinkCountHandler.CountingNetwork.addCountingStation(new CountingStation("Koenig-Karls-Bruecke Barometer",
                new String[]{"2993895710004f", "2993895710004r", "2993895700003f", "2615633160000f"}));
        LinkCountHandler.CountingNetwork.addCountingStation(new CountingStation("Boeblinger Strasse",
                new String[]{"401040430002f", "401040430002r", "5957276710002f", "5957276710002r"}));
        LinkCountHandler.CountingNetwork.addCountingStation(new CountingStation("Taubenheimstrasse",
                new String[]{"3585536500004f", "3585536500004r"}));
        LinkCountHandler.CountingNetwork.addCountingStation(new CountingStation("Waiblinger Strasse",
                new String[]{"3585536510004f", "10968008150004f"}));

        LinkCountHandler.CountingNetwork.addCountingStation(new CountingStation("Samaraweg",
                new String[]{"10272926360000f", "10272926360000r"}));
        LinkCountHandler.CountingNetwork.addCountingStation(new CountingStation("Tuebinger Strasse",
                new String[]{"3536362270003f", "3467623440004f", "3467623440004r"}));
        LinkCountHandler.CountingNetwork.addCountingStation(new CountingStation("Lautenschlager Strasse",
                new String[]{"1750316500005f", "1750316500005f_bike-reverse"}));
        LinkCountHandler.CountingNetwork.addCountingStation(new CountingStation("Inselstrasse",
                new String[]{"3777509370000f", "3777509370000r", "248127500000f", "227009470000f"}));

        LinkCountHandler.CountingNetwork.addCountingStation(new CountingStation("Kremmlerstrasse",
                new String[]{"237199820003f", "237199820003r"}));
        LinkCountHandler.CountingNetwork.addCountingStation(new CountingStation("Kirchheimer Strasse",
                new String[]{"7629370820007f", "7629370820007r", "262444090001f", "262444090001r"}));
        LinkCountHandler.CountingNetwork.addCountingStation(new CountingStation("Neckartalstrasse",
                new String[]{"2993863910015f", "2993863910015r", "2977618270000f", "1966731520017f"}));
        LinkCountHandler.CountingNetwork.addCountingStation(new CountingStation("Stuttgarter Strasse",
                new String[]{"3684442310011f", "3684442310011r", "4369205380002f", "4369205380002r"}));

        LinkCountHandler.CountingNetwork.addCountingStation(new CountingStation("Solitudestrasse",
                new String[]{"3832380570013f", "3832380570013r", "290371860010f", "290371860010r", "3023316010000f", "3023316010000r"}));
        LinkCountHandler.CountingNetwork.addCountingStation(new CountingStation("Waldburgstrasse",
                new String[]{"3570056810004f", "3570056810004r", "3570049870031f", "3570049870031r"}));
        LinkCountHandler.CountingNetwork.addCountingStation(new CountingStation("Am Kraeherwald",
                new String[]{"2555251750007f", "2555251750007r", "393429710001f", "393429710001r"}));


    }
}
