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
package org.matsim.run;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import ch.sbb.matsim.config.SBBTransitConfigGroup;
import ch.sbb.matsim.mobsim.qsim.SBBTransitModule;
import ch.sbb.matsim.mobsim.qsim.pt.SBBTransitEngineQSimModule;
import org.apache.log4j.Logger;
import org.matsim.Utils;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlansCalcRouteConfigGroup;
import org.matsim.core.config.groups.QSimConfigGroup.TrafficDynamics;
import org.matsim.core.config.groups.VspExperimentalConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.controler.OutputDirectoryLogging;
import org.matsim.core.gbl.Gbl;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.parkingCost.ParkingCostConfigGroup;
import org.matsim.parkingCost.ParkingCostModule;

import ch.sbb.matsim.config.SwissRailRaptorConfigGroup;
import ch.sbb.matsim.routing.pt.raptor.RaptorIntermodalAccessEgress;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorModule;
import org.matsim.prepare.AddAdditionalNetworkAttributes;
import org.matsim.prepare.PrepareTransitSchedule;
import org.matsim.ptFares.PtFaresConfigGroup;
import org.matsim.ptFares.PtFaresModule;

import static org.matsim.core.config.groups.ControlerConfigGroup.RoutingAlgorithmType.FastAStarLandmarks;

/**
 * @author nagel, ikaddoura
 *
 */
public class RunIhabsClass {
    private static final Logger log = Logger.getLogger(RunIhabsClass.class );

    public static void main(String[] args) {

        for (String arg : args) {
            log.info( arg );
        }

        if ( args.length==0 ) {
            args = new String[] {"C:/Users/david/OneDrive/02_Uni/02_Master/05_Masterarbeit/03_MATSim/02_runs/stuttgart-v1.0/stuttgart-v1.0_fstRun01/stuttgart-v1.0-25pct.config_fstRun01_Test.xml"}  ;
        }

        Config config = prepareConfig( args ) ;
        // ConfigUtils.writeConfig(config, "C:/Users/david/OneDrive/02_Uni/02_Master/05_Masterarbeit/03_MATSim/02_runs/stuttgart-v1.0/stuttgart-v1.0_fstRun01/stuttgart-v1.0-25pct.config_fstRun01_Test_ed.xml");

        Scenario scenario = prepareScenario( config ) ;
        Controler controler = prepareControler( scenario ) ;
        controler.run() ;
    }


    public static Config prepareConfig(String [] args, ConfigGroup... customModules) {
        OutputDirectoryLogging.catchLogEntries();


        // -- PREPARE CUSTOM MODULES
        String[] typedArgs = Arrays.copyOfRange( args, 1, args.length );

        ConfigGroup[] customModulesToAdd = new ConfigGroup[]{ setupRaptorConfigGroup(), setupPTFaresGroup(), setupSBBTransit(), new ParkingCostConfigGroup()};
        ConfigGroup[] customModulesAll = new ConfigGroup[customModules.length + customModulesToAdd.length];

        int counter = 0;
        for (ConfigGroup customModule : customModules) {
            customModulesAll[counter] = customModule;
            counter++;
        }

        for (ConfigGroup customModule : customModulesToAdd) {
            customModulesAll[counter] = customModule;
            counter++;
        }

        // -- LOAD CONFIG WITH CUSTOM MODULES
        final Config config = ConfigUtils.loadConfig( args[ 0 ], customModulesAll );


        // -- CONTROLER --
        config.controler().setLastIteration(2);
        config.controler().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);


        // -- VSP DEFAULTS --
        config.vspExperimental().setVspDefaultsCheckingLevel( VspExperimentalConfigGroup.VspDefaultsCheckingLevel.ignore );
        config.plansCalcRoute().setAccessEgressType(PlansCalcRouteConfigGroup.AccessEgressType.accessEgressModeToLink); //setInsertingAccessEgressWalk( true );
        config.qsim().setUsingTravelTimeCheckInTeleportation( true );
        config.qsim().setTrafficDynamics( TrafficDynamics.kinematicWaves );


        // -- OTHER --
        config.qsim().setUsePersonIdForMissingVehicleId(false);
        config.controler().setRoutingAlgorithmType(FastAStarLandmarks);
        config.subtourModeChoice().setProbaForRandomSingleTripMode( 0.5 );
        // config.plansCalcRoute().setRoutingRandomness( 3. );
        // config.plansCalcRoute().removeModeRoutingParams(TransportMode.ride); // since we are using the (congested) car travel time
        // config.plansCalcRoute().removeModeRoutingParams(TransportMode.pt); // since we are using simulated public transit
        // config.plansCalcRoute().removeModeRoutingParams("undefined"); // since we don't have such a mode
        config.qsim().setInsertingWaitingVehiclesBeforeDrivingVehicles( true );


        // -- MODES --
        // config.plansCalcRoute().getOrCreateModeRoutingParams("bike").setBeelineDistanceFactor(1.27);
        // config.plansCalcRoute().getOrCreateModeRoutingParams("bike").setTeleportedModeSpeed(14.6/3.6);


        // -- ACTIVITIES --
        final long minDuration = 600;
        final long maxDuration = 3600 * 27;
        final long difference = 600;

        Utils.createTypicalDurations("home", minDuration, maxDuration, difference).forEach(params -> config.planCalcScore().addActivityParams(params));
        Utils.createTypicalDurations("work", minDuration, maxDuration, difference).forEach(params -> config.planCalcScore().addActivityParams(params));
        Utils.createTypicalDurations("leisure", minDuration, maxDuration, difference).forEach(params -> config.planCalcScore().addActivityParams(params));
        Utils.createTypicalDurations("shopping", minDuration, maxDuration, difference).forEach(params -> config.planCalcScore().addActivityParams(params));
        Utils.createTypicalDurations("errands", minDuration, maxDuration, difference).forEach(params -> config.planCalcScore().addActivityParams(params));
        Utils.createTypicalDurations("business", minDuration, maxDuration, difference).forEach(params -> config.planCalcScore().addActivityParams(params));
        Utils.createTypicalDurations("educ_secondary", minDuration, maxDuration, difference).forEach(params -> config.planCalcScore().addActivityParams(params));
        Utils.createTypicalDurations("educ_higher", minDuration, maxDuration, difference).forEach(params -> config.planCalcScore().addActivityParams(params));


        ConfigUtils.applyCommandline( config, typedArgs ) ;

        return config ;
    }


    public static Scenario prepareScenario( Config config ) {
        Gbl.assertNotNull( config );

        Scenario scenario = ScenarioUtils.loadScenario(config);

        // Add fareZones and VVSBikeAndRideStops
        PrepareTransitSchedule ptPreparer = new PrepareTransitSchedule();
        ptPreparer.run(scenario);

        // Add parking costs to network
        AddAdditionalNetworkAttributes parkingPreparer = new AddAdditionalNetworkAttributes();
        parkingPreparer.run(scenario);

        return scenario;
    }


    public static Controler prepareControler( Scenario scenario ) {
        Gbl.assertNotNull(scenario);

        final Controler controler = new Controler( scenario );

        // -- ADDITIONAL MODULES --
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
                bind(RaptorIntermodalAccessEgress.class).to(org.matsim.run.StuttgartRaptorIntermodalAccessEgress.class);
            }
        } );

        // use deterministic transport simulation of SBB
        controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {
                // To use the deterministic pt simulation (Part 1 of 2):
                install(new SBBTransitModule());
            }

            // To use the deterministic pt simulation (Part 2 of 2):
        });

        controler.configureQSimComponents(components -> {
            SBBTransitEngineQSimModule.configure(components);
        });

/*        // use our own Analysis(Main-)ModeIdentifier
        controler.addOverridingModule( new AbstractModule() {
            @Override
            public void install() {
                // mainly relevant for DRT applications:
                bind(MainModeIdentifier.class).to(LosAngelesIntermodalPtDrtRouterModeIdentifier.class);
                // in order to look into the different types of intermodal pt trips:
                bind(AnalysisMainModeIdentifier.class).to(LosAngelesIntermodalPtDrtRouterAnalysisModeIdentifier.class);
            }
        } );*/

/*        // use income dependent marginal utility of money
        LosAngelesPlanScoringFunctionFactory initialPlanScoringFunctionFactory = new LosAngelesPlanScoringFunctionFactory(controler.getScenario());
        controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {
                this.bindScoringFunctionFactory().toInstance(initialPlanScoringFunctionFactory);
            }
        });*/

        // use parking cost module
        controler.addOverridingModule(new ParkingCostModule());

        // use pt fares module
        controler.addOverridingModule(new PtFaresModule());

        return controler;
    }




    private static SwissRailRaptorConfigGroup setupRaptorConfigGroup() {
        SwissRailRaptorConfigGroup configRaptor = new SwissRailRaptorConfigGroup();

        // -- Intermodal Routing --

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

        configRaptor.addIntermodalAccessEgress(paramSetAEBike);


        return configRaptor;
    }


    private static PtFaresConfigGroup setupPTFaresGroup() {

        PtFaresConfigGroup configFares = new PtFaresConfigGroup();

        // For values, see https://www.vvs.de/tickets/zeittickets-abo-polygo/jahresticket-jedermann/

        PtFaresConfigGroup.ZonePricesParameterSet paramSetZone1 = new PtFaresConfigGroup.ZonePricesParameterSet();
        paramSetZone1.setNumberZones(1);
        paramSetZone1.setTicketPrice(1.89);
        configFares.addZonePriceSettings(paramSetZone1);

        PtFaresConfigGroup.ZonePricesParameterSet paramSetZone2 = new PtFaresConfigGroup.ZonePricesParameterSet();
        paramSetZone1.setNumberZones(2);
        paramSetZone1.setTicketPrice(2.42);
        configFares.addZonePriceSettings(paramSetZone2);

        PtFaresConfigGroup.ZonePricesParameterSet paramSetZone3 = new PtFaresConfigGroup.ZonePricesParameterSet();
        paramSetZone1.setNumberZones(3);
        paramSetZone1.setTicketPrice(3.23);
        configFares.addZonePriceSettings(paramSetZone3);

        PtFaresConfigGroup.ZonePricesParameterSet paramSetZone4 = new PtFaresConfigGroup.ZonePricesParameterSet();
        paramSetZone1.setNumberZones(4);
        paramSetZone1.setTicketPrice(4.);
        configFares.addZonePriceSettings(paramSetZone4);

        PtFaresConfigGroup.ZonePricesParameterSet paramSetZone5 = new PtFaresConfigGroup.ZonePricesParameterSet();
        paramSetZone1.setNumberZones(5);
        paramSetZone1.setTicketPrice(4.68);
        configFares.addZonePriceSettings(paramSetZone5);

        PtFaresConfigGroup.ZonePricesParameterSet paramSetZone6 = new PtFaresConfigGroup.ZonePricesParameterSet();
        paramSetZone1.setNumberZones(6);
        paramSetZone1.setTicketPrice(5.51);
        configFares.addZonePriceSettings(paramSetZone6);

        PtFaresConfigGroup.ZonePricesParameterSet paramSetZone7 = new PtFaresConfigGroup.ZonePricesParameterSet();
        paramSetZone1.setNumberZones(7);
        paramSetZone1.setTicketPrice(6.22);
        configFares.addZonePriceSettings(paramSetZone7);

        return configFares;
    }


    private static SBBTransitConfigGroup setupSBBTransit() {

        SBBTransitConfigGroup sbbTransit = new SBBTransitConfigGroup();
        Set<String> modes = new HashSet<>(Arrays.asList(new String[]{"train", "tram", "bus"}));
        sbbTransit.setDeterministicServiceModes(modes);
        sbbTransit.setCreateLinkEventsInterval(10);

        return sbbTransit;
    }



}
