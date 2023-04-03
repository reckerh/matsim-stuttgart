/* *********************************************************************** *
 * project: org.matsim.*												   *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2008 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */
package org.matsim.stuttgart.run;


import ch.sbb.matsim.config.SBBTransitConfigGroup;
import ch.sbb.matsim.config.SwissRailRaptorConfigGroup;
import ch.sbb.matsim.mobsim.qsim.SBBTransitModule;
import ch.sbb.matsim.mobsim.qsim.pt.SBBTransitEngineQSimModule;
import ch.sbb.matsim.routing.pt.raptor.RaptorIntermodalAccessEgress;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorModule;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlansCalcRouteConfigGroup;
import org.matsim.core.config.groups.QSimConfigGroup;
import org.matsim.core.config.groups.VspExperimentalConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.controler.OutputDirectoryLogging;
import org.matsim.core.gbl.Gbl;
import org.matsim.core.router.AnalysisMainModeIdentifier;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.matsim.extensions.pt.fare.intermodalTripFareCompensator.IntermodalTripFareCompensatorConfigGroup;
import org.matsim.extensions.pt.fare.intermodalTripFareCompensator.IntermodalTripFareCompensatorsConfigGroup;
import org.matsim.extensions.pt.fare.intermodalTripFareCompensator.IntermodalTripFareCompensatorsModule;
import org.matsim.extensions.pt.replanning.singleTripStrategies.ChangeSingleTripModeAndRoute;
import org.matsim.extensions.pt.replanning.singleTripStrategies.RandomSingleTripReRoute;
import org.matsim.extensions.pt.routing.EnhancedRaptorIntermodalAccessEgress;
import org.matsim.extensions.pt.routing.ptRoutingModes.PtIntermodalRoutingModesConfigGroup;
import org.matsim.extensions.pt.routing.ptRoutingModes.PtIntermodalRoutingModesModule;
import org.matsim.stuttgart.Utils;
import org.matsim.stuttgart.prepare.AddAdditionalNetworkAttributes;
import org.matsim.stuttgart.prepare.PrepareTransitSchedule;
import org.matsim.stuttgart.prepare.RemoveFacilitiesFromPlans;
import org.matsim.stuttgart.ptFares.PtFaresConfigGroup;
import org.matsim.stuttgart.ptFares.PtFaresModule;
import org.opengis.feature.simple.SimpleFeature;
import playground.vsp.simpleParkingCostHandler.ParkingCostConfigGroup;
import playground.vsp.simpleParkingCostHandler.ParkingCostModule;

import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Consumer;

import static org.matsim.core.config.groups.ControlerConfigGroup.RoutingAlgorithmType.FastAStarLandmarks;

/**
 * @author dwedekind
 * @author gleich
 */


public class StuttgartMasterThesisRunner {

    private static final Logger log = LogManager.getLogger(StuttgartMasterThesisRunner.class);


    public static void main(String[] args) {

        for (String arg : args) {
            log.info(arg);
        }

        Config config = prepareConfig(args);

        try {

            String fareZoneShapeFileName = (Paths.get(config.getContext().toURI()).getParent()).resolve("input/add/fareZones_bc.shp").toString();
            String parkingZoneShapeFileName = (Paths.get(config.getContext().toURI()).getParent()).resolve("input/add/parkingShapes_bc_v2.shp").toString();
            Scenario scenario = prepareScenario(config);
            finishScenario(scenario, fareZoneShapeFileName, parkingZoneShapeFileName);

            Controler controler = prepareControler(scenario) ;
            controler.run() ;

        } catch (URISyntaxException e) {
            log.error("URISyntaxException: " + e);
        }
    }


    public static Config prepareConfig(String [] args) {
        log.info("StuttgartMasterThesisRunner - 27-04-2021 16:57");
        OutputDirectoryLogging.catchLogEntries();

        // -- LOAD CONFIG GROUP --

        // Load default modules
        String[] typedArgs = Arrays.copyOfRange( args, 1, args.length );
        final Config config = ConfigUtils.loadConfig( args[ 0 ] );

        // Materialize custom config groups
        // Either take default setup from below or read from config file if exists
        materializeConfigGroup(config, SwissRailRaptorConfigGroup.GROUP, SwissRailRaptorConfigGroup.class, StuttgartMasterThesisRunner::setupRaptorConfigGroup);
        materializeConfigGroup(config, IntermodalTripFareCompensatorsConfigGroup.GROUP_NAME, IntermodalTripFareCompensatorsConfigGroup.class, StuttgartMasterThesisRunner::setupCompensatorsConfigGroup);
        materializeConfigGroup(config, PtIntermodalRoutingModesConfigGroup.GROUP, PtIntermodalRoutingModesConfigGroup.class, StuttgartMasterThesisRunner::setupPTRoutingModes);
        materializeConfigGroup(config, PtFaresConfigGroup.GROUP, PtFaresConfigGroup.class, StuttgartMasterThesisRunner::setupPTFaresGroup);
        materializeConfigGroup(config, SBBTransitConfigGroup.GROUP_NAME, SBBTransitConfigGroup.class, StuttgartMasterThesisRunner::setupSBBTransit);
        materializeConfigGroup(config, ParkingCostConfigGroup.GROUP_NAME, ParkingCostConfigGroup.class, StuttgartMasterThesisRunner::setupParkingCostConfigGroup);


        // -- CONTROLER --

        // Some controler setting changes
        config.controler().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
        config.controler().setWriteTripsInterval(50);


        // -- VSP DEFAULTS --

        // Some vsp default settings
        config.vspExperimental().setVspDefaultsCheckingLevel(VspExperimentalConfigGroup.VspDefaultsCheckingLevel.ignore);
        config.plansCalcRoute().setAccessEgressType(PlansCalcRouteConfigGroup.AccessEgressType.accessEgressModeToLink);
        config.qsim().setUsingTravelTimeCheckInTeleportation(true);
        config.qsim().setTrafficDynamics(QSimConfigGroup.TrafficDynamics.kinematicWaves);


        // -- INTERMODAL --

        // Add a new mode to subtour mode choice
        // This mode "pt_w_bike_allowed" is introduced for easier analyzing and distinguishing between regular pt trips and pt trips with bike & ride later
        List<String> modes = new ArrayList<>(Arrays.asList(config.subtourModeChoice().getModes()));
        modes.add("pt_w_bike_allowed");
        config.subtourModeChoice().setModes(modes.toArray(new String[0]));


        // -- OTHER --

        // This was a recommended setting
        config.subtourModeChoice().setProbaForRandomSingleTripMode(0.5);

        config.qsim().setUsePersonIdForMissingVehicleId(false);

        // This is the recommended routing algorithm
        config.controler().setRoutingAlgorithmType(FastAStarLandmarks);

        config.qsim().setInsertingWaitingVehiclesBeforeDrivingVehicles(true);


        // -- ACTIVITIES --

        // For better readability, the input config file does not contain all activity types
        // Create them here

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


        // -- APPLY COMMAND LINE --

        // Finally apply overwriting command line setting if exist
        ConfigUtils.applyCommandline( config, typedArgs ) ;

        log.info("Config successfully prepared...");

        return config ;

    }


    public static Scenario prepareScenario( Config config ) {
        Gbl.assertNotNull( config );

        Scenario scenario = ScenarioUtils.loadScenario(config);

        // Add walk and bike to all network links
        // This is a safety thing, as in the scenario creation some links will have original modes "car" and "ride" removed
        // The network cleaner would remove these links totally (when there would be no modes assigned anymore)
        // And I am not sure if this would lead to strange route results in the scenarios
        new AddAdditionalNetworkAttributes().addWalkAndBikeToNetworkLinks(scenario.getNetwork());

        // Remove facilities from plans
        // For later scenario creation, facilities need to be removed
        // Thus, calibration runner should be without facilities as well
        new RemoveFacilitiesFromPlans().run(scenario);

        return scenario;

    }


    public static void finishScenario(Scenario scenario, String fareZoneShapeFilePath, String parkingZoneShapeFilePath){
        // Add fareZones and VVSBikeAndRideStops
        PrepareTransitSchedule ptPreparer = new PrepareTransitSchedule();
        ptPreparer.run(scenario, fareZoneShapeFilePath);

        // Add parking costs to network
        Collection<SimpleFeature> features = ShapeFileReader.getAllFeatures(parkingZoneShapeFilePath);
        new AddAdditionalNetworkAttributes().mergeNetworkLinksWithParkingAttributes(scenario.getNetwork(), features);

        log.info("Scenario successfully prepared...");

    }


    public static Controler prepareControler( Scenario scenario ) {
        Gbl.assertNotNull(scenario);

        final Controler controler = new Controler( scenario );

        // -- ADDITIONAL MODULES --

        // Use the swiss rail raptor for routing
        // As I am using an earlier release, this module has to be installed
        if (controler.getConfig().transit().isUsingTransitInMobsim()) {

            // use the sbb pt raptor router
            controler.addOverridingModule( new AbstractModule() {
                @Override
                public void install() {
                    install( new SwissRailRaptorModule() );
                }
            } );
        } else {
            log.warn("Public transit will be teleported and not simulated in the mobsim! "
                    + "This will have a significant effect on pt-related parameters (travel times, modal split, and so on). "
                    + "Should only be used for testing or car-focused studies with a fixed modal split.  ");
        }

        // use the (congested) car travel time for the teleported ride modes
        controler.addOverridingModule( new AbstractModule() {
            @Override
            public void install() {
                addTravelTimeBinding( TransportMode.ride ).to( networkTravelTime() );
                addTravelDisutilityFactoryBinding( TransportMode.ride ).to( carTravelDisutilityFactoryKey() ); }
        } );


        // use scoring parameters for intermodal PT routing
        controler.addOverridingModule( new AbstractModule() {
            @Override
            public void install() {
                addPlanStrategyBinding("RandomSingleTripReRoute").toProvider(RandomSingleTripReRoute.class);
                addPlanStrategyBinding("ChangeSingleTripModeAndRoute").toProvider(ChangeSingleTripModeAndRoute.class);

                bind(RaptorIntermodalAccessEgress.class).to(EnhancedRaptorIntermodalAccessEgress.class);
            }
        } );

        // use fare compensators module and pt routing modes module for adjusting sbb router settings
        controler.addOverridingModule(new IntermodalTripFareCompensatorsModule());
        controler.addOverridingModule(new PtIntermodalRoutingModesModule());


        // install the Stuttgart relevant main mode analyzer; override the default one
        controler.addOverridingModule( new AbstractModule() {
            @Override
            public void install() {
                bind(AnalysisMainModeIdentifier.class).to(StuttgartAnalysisMainModeIdentifier.class);
            }
        } );



        // use deterministic transport simulation of SBB
        controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {
                install(new SBBTransitModule());
            }

        });

        controler.configureQSimComponents(new SBBTransitEngineQSimModule()::configure);

        // use parking cost module
        controler.addOverridingModule(new ParkingCostModule());

        // use pt fares module
        controler.addOverridingModule(new PtFaresModule());

        log.info("Controler successfully prepared...");

        return controler;
    }


    private static <T extends ConfigGroup> void materializeConfigGroup (Config config, String configGroupName, Class<T> clazz, Consumer<T> defaultSetup){
        var containsConfigGroup = config.getModules().containsKey(configGroupName);
        T configGroup = ConfigUtils.addOrGetModule(config, clazz);

        if (! containsConfigGroup){
            defaultSetup.accept(configGroup);
        }

    }


    private static void setupRaptorConfigGroup(SwissRailRaptorConfigGroup configRaptor) {

        String personAttributeBike = "canUseBike";
        String personAttributeBikeValue = "true";

        configRaptor.setUseIntermodalAccessEgress(true);

        // AcessEgressWalk
        SwissRailRaptorConfigGroup.IntermodalAccessEgressParameterSet paramSetAEWalk = new SwissRailRaptorConfigGroup.IntermodalAccessEgressParameterSet();
        paramSetAEWalk.setInitialSearchRadius(1000);
        paramSetAEWalk.setSearchExtensionRadius(500);
        paramSetAEWalk.setMode(TransportMode.walk);
        paramSetAEWalk.setMaxRadius(10000);
        configRaptor.addIntermodalAccessEgress(paramSetAEWalk);

        // AccessEgressBike
        SwissRailRaptorConfigGroup.IntermodalAccessEgressParameterSet paramSetAEBike = new SwissRailRaptorConfigGroup.IntermodalAccessEgressParameterSet();
        paramSetAEBike.setInitialSearchRadius(5000);
        paramSetAEBike.setSearchExtensionRadius(2000);
        paramSetAEBike.setMode(TransportMode.bike);
        paramSetAEBike.setMaxRadius(10000);
        paramSetAEBike.setStopFilterAttribute("VVSBikeAndRide");
        paramSetAEBike.setStopFilterValue("true");

        paramSetAEBike.setPersonFilterAttribute(personAttributeBike);
        paramSetAEBike.setPersonFilterValue(personAttributeBikeValue);

        configRaptor.addIntermodalAccessEgress(paramSetAEBike);

    }


    private static void setupCompensatorsConfigGroup(IntermodalTripFareCompensatorsConfigGroup compensatorsCfg) {

        IntermodalTripFareCompensatorConfigGroup compensatorCfg = new IntermodalTripFareCompensatorConfigGroup();
        compensatorCfg.setCompensationCondition(IntermodalTripFareCompensatorConfigGroup.CompensationCondition.PtModeUsedInSameTrip);

        // I have no idea whether this is the correct setting but it compiles
        compensatorCfg.setNonPtModesAsString(TransportMode.bike);
        compensatorCfg.setPtModesAsString("pt");

        // Based on own calculations considering theft, vandalism at Bike and Ride facilities etc...
        compensatorCfg.setCompensationMoneyPerTrip(-0.3);

        compensatorsCfg.addParameterSet(compensatorCfg);

    }


    private static void setupPTRoutingModes(PtIntermodalRoutingModesConfigGroup ptRoutingModes) {
        String personAttributeBike = "canUseBike";
        String personAttributeBikeValue = "true";

        PtIntermodalRoutingModesConfigGroup.PtIntermodalRoutingModeParameterSet routingModeParamSet = new PtIntermodalRoutingModesConfigGroup.PtIntermodalRoutingModeParameterSet();
        routingModeParamSet.setDelegateMode(TransportMode.pt);
        routingModeParamSet.setRoutingMode("pt_w_bike_allowed");
        PtIntermodalRoutingModesConfigGroup.PersonAttribute2ValuePair personAttributeValue = new PtIntermodalRoutingModesConfigGroup.PersonAttribute2ValuePair();
        personAttributeValue.setPersonFilterAttribute(personAttributeBike);
        personAttributeValue.setPersonFilterValue(personAttributeBikeValue);
        routingModeParamSet.addPersonAttribute2ValuePair(personAttributeValue);
        ptRoutingModes.addPtIntermodalRoutingModeParameterSet(routingModeParamSet);

    }


    private static void setupPTFaresGroup(PtFaresConfigGroup configFares) {
        // For values, see https://www.vvs.de/tickets/zeittickets-abo-polygo/jahresticket-jedermann/

        PtFaresConfigGroup.FaresGroup faresGroup = new PtFaresConfigGroup.FaresGroup();

        // 10 seems to be an adequate value (for calibrating long distance trips but also reasonable pricewise)
        faresGroup.setOutOfZonePrice(10.);
        faresGroup.addFare(new PtFaresConfigGroup.FaresGroup.Fare(1, 1.89));
        faresGroup.addFare(new PtFaresConfigGroup.FaresGroup.Fare(2, 2.42));
        faresGroup.addFare(new PtFaresConfigGroup.FaresGroup.Fare(3, 3.23));
        faresGroup.addFare(new PtFaresConfigGroup.FaresGroup.Fare(4, 4.));
        faresGroup.addFare(new PtFaresConfigGroup.FaresGroup.Fare(5, 4.68));
        faresGroup.addFare(new PtFaresConfigGroup.FaresGroup.Fare(6, 5.51));
        faresGroup.addFare(new PtFaresConfigGroup.FaresGroup.Fare(7, 6.22));
        faresGroup.addFare(new PtFaresConfigGroup.FaresGroup.Fare(8, 6.22));
        configFares.setFaresGroup(faresGroup);

        PtFaresConfigGroup.ZonesGroup zonesGroup = new PtFaresConfigGroup.ZonesGroup();
        zonesGroup.setOutOfZoneTag("out");
        zonesGroup.addZone(new PtFaresConfigGroup.ZonesGroup.Zone("1", false));
        zonesGroup.addZone(new PtFaresConfigGroup.ZonesGroup.Zone("1/2", true, Set.of("1", "2")));
        zonesGroup.addZone(new PtFaresConfigGroup.ZonesGroup.Zone("2", false));
        zonesGroup.addZone(new PtFaresConfigGroup.ZonesGroup.Zone("2/3", true, Set.of("2", "3")));
        zonesGroup.addZone(new PtFaresConfigGroup.ZonesGroup.Zone("3", false));
        zonesGroup.addZone(new PtFaresConfigGroup.ZonesGroup.Zone("3/4", true, Set.of("3", "4")));
        zonesGroup.addZone(new PtFaresConfigGroup.ZonesGroup.Zone("4", false));
        zonesGroup.addZone(new PtFaresConfigGroup.ZonesGroup.Zone("4/5", true, Set.of("4", "5")));
        zonesGroup.addZone(new PtFaresConfigGroup.ZonesGroup.Zone("5", false));
        zonesGroup.addZone(new PtFaresConfigGroup.ZonesGroup.Zone("5/6", true, Set.of("5", "6")));
        zonesGroup.addZone(new PtFaresConfigGroup.ZonesGroup.Zone("6", false));
        zonesGroup.addZone(new PtFaresConfigGroup.ZonesGroup.Zone("6/7", true, Set.of("6", "7")));
        zonesGroup.addZone(new PtFaresConfigGroup.ZonesGroup.Zone("7", false));
        zonesGroup.addZone(new PtFaresConfigGroup.ZonesGroup.Zone("7/8", true, Set.of("7", "8")));
        zonesGroup.addZone(new PtFaresConfigGroup.ZonesGroup.Zone("8", false));
        configFares.setZonesGroup(zonesGroup);

    }


    private static void setupSBBTransit(SBBTransitConfigGroup sbbTransit) {
        var modes = Set.of(TransportMode.train, "bus", "tram");
        sbbTransit.setDeterministicServiceModes(modes);
        sbbTransit.setCreateLinkEventsInterval(10);

    }


    private static void setupParkingCostConfigGroup(ParkingCostConfigGroup parkingCostConfigGroup) {
        parkingCostConfigGroup.setFirstHourParkingCostLinkAttributeName("oneHourPCost");
        parkingCostConfigGroup.setExtraHourParkingCostLinkAttributeName("extraHourPCost");
        parkingCostConfigGroup.setMaxDailyParkingCostLinkAttributeName("maxDailyPCost");
        parkingCostConfigGroup.setMaxParkingDurationAttributeName("maxPTime");
        parkingCostConfigGroup.setParkingPenaltyAttributeName("pFine");
        parkingCostConfigGroup.setResidentialParkingFeeAttributeName("resPCosts");

    }
}


