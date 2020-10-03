package org.matsim.run;

import ch.sbb.matsim.config.SBBTransitConfigGroup;
import ch.sbb.matsim.config.SwissRailRaptorConfigGroup;
import ch.sbb.matsim.mobsim.qsim.SBBTransitModule;
import ch.sbb.matsim.mobsim.qsim.pt.SBBTransitEngineQSimModule;
import ch.sbb.matsim.routing.pt.raptor.RaptorIntermodalAccessEgress;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorModule;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.compress.utils.FileNameUtils;
import org.apache.log4j.Logger;
import org.matsim.Utils;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlansCalcRouteConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.controler.OutputDirectoryLogging;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.parkingCost.ParkingCostConfigGroup;
import org.matsim.parkingCost.ParkingCostModule;
import org.matsim.ptFares.PtFaresConfigGroup;
import org.matsim.ptFares.PtFaresModule;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehiclesFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;


@Log4j2
public class RunStuttgartBaseCase {

    private static final Boolean withIntermodalRouter = true;
    private static final Boolean withDeterministicPt = true;
    private static final Boolean withParkingCosts = false;
    private static final Boolean withPtFares = false;

    private static final Logger log = Logger.getLogger(RunStuttgartBaseCase.class );
    private static final String outputBasePath = "C:/Users/david/OneDrive/02_Uni/02_Master/05_Masterarbeit/03_MATSim/02_runs/stuttgart-v1.0/output/";

    public static void main(String[] args) {

        // Show program arguments
        for (String arg : args) {
            log.info( arg );
        }

        // Prepare config, controler and scenario
        Config config = prepareConfig( args );
        Scenario scenario = prepareScenario( config );
        Controler controler = prepareControler( scenario );

        // Run controler
        controler.run();

    }


    public static Config prepareConfig(String[] args, ConfigGroup... customModules) {


        OutputDirectoryLogging.catchLogEntries();

        // -- INCLUDE CUSTOM MODULES
        ConfigGroup[] customModulesToAdd;

        if (withParkingCosts & withPtFares){
            customModulesToAdd = new ConfigGroup[]{new ParkingCostConfigGroup(), new PtFaresConfigGroup()};

        } else if (withParkingCosts & !withPtFares){
            customModulesToAdd = new ConfigGroup[]{new ParkingCostConfigGroup()};

        } else if (!withParkingCosts & withPtFares){
            customModulesToAdd = new ConfigGroup[]{new PtFaresConfigGroup()};

        } else {
            customModulesToAdd = new ConfigGroup[]{};
        }

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
        Config config = ConfigUtils.loadConfig(args, customModules);



        // -- CONTROLER --
        // Set Output directory according to config name
        config.controler().setOutputDirectory(outputBasePath + setOutputFolder(args[0]) + "/");

        // Overwrite file settings if exist
        config.controler().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);

        // Set last iteration
        config.controler().setLastIteration(2);


        // -- PLANS CALC ROUTE --

        config.plansCalcRoute().setAccessEgressType(PlansCalcRouteConfigGroup.AccessEgressType.accessEgressModeToLink);


        // -- QSIM --

        config.qsim().setUsingTravelTimeCheckInTeleportation(true);
        config.qsim().setUsePersonIdForMissingVehicleId(false);


        // -- SUB TOUR MODE CHOICE --
        config.subtourModeChoice().setProbaForRandomSingleTripMode(0.5);


        // -- PLANS CALC SCORE --
        // Typical Durations

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


        // -- SWISS RAIL RAPTOR --
        SwissRailRaptorConfigGroup raptor = setupRaptorConfigGroup();
        config.addModule(raptor);


        // -- SBB Deterministic Transit Simulation
        if (withDeterministicPt){
            SBBTransitConfigGroup transit = new SBBTransitConfigGroup();
            Set<String> modes = new HashSet<>(Arrays.asList(new String[]{"train", "tram", "bus"}));
            transit.setDeterministicServiceModes(modes);
            transit.setCreateLinkEventsInterval(10);
            config.addModule(transit);
        }


        // -- PT Fares config
        if (withPtFares){
            PtFaresConfigGroup ptFares = setupPTFaresGroup();
            config.addModule(ptFares);
        }


        return config;

    }


    public static Scenario prepareScenario(Config config) {

        return ScenarioUtils.loadScenario(config);
    }


    public static Controler prepareControler(Scenario scenario) {

        Controler controler = new Controler(scenario);

        if (!controler.getConfig().transit().isUsingTransitInMobsim())
            throw new RuntimeException("Public transit will be teleported and not simulated in the mobsim! "
                    + "This will have a significant effect on pt-related parameters (travel times, modal split, and so on). "
                    + "Should only be used for testing or car-focused studies with fixed modal split.");


        // use the (congested) car travel time for the teleported ride mode
        controler.addOverridingModule( new AbstractModule() {
            @Override
            public void install() {
                addTravelTimeBinding( TransportMode.ride ).to( networkTravelTime() );
                addTravelDisutilityFactoryBinding( TransportMode.ride ).to( carTravelDisutilityFactoryKey() );

            }
        } );


        // use the swiss rail raptor module
        controler.addOverridingModule( new AbstractModule() {
            @Override
            public void install() {
                install( new SwissRailRaptorModule() );
            }
        } );

        // use scoring parameters for intermodal PT routing
        if (withIntermodalRouter){
            controler.addOverridingModule(new AbstractModule(){
                @Override
                public void install() {
                    bind(RaptorIntermodalAccessEgress.class).to(org.matsim.run.StuttgartRaptorIntermodalAccessEgress.class);
                }
            });
        }


        // use deterministic pt simulation
        if (withDeterministicPt){
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
        }


        // add parking cost module
        if (withParkingCosts){
            controler.addOverridingModule(new ParkingCostModule());
        }


        // add ptfares Module
        if (withPtFares){
            controler.addOverridingModule(new PtFaresModule());
        }


        return controler;
    }


    private static VehicleType createVehicleType(String id, double length, double maxV, double pce, VehiclesFactory factory) {
        var vehicleType = factory.createVehicleType(Id.create(id, VehicleType.class));
        vehicleType.setNetworkMode(id);
        vehicleType.setPcuEquivalents(pce);
        vehicleType.setLength(length);
        vehicleType.setMaximumVelocity(maxV);
        vehicleType.setWidth(1.0);
        return vehicleType;
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
        // Later define such stops that have bike & ride facilities here
        // paramSetWalk.setStopFilterAttribute(null);

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

    private static String setOutputFolder(String configPath){

        Path path = Paths.get( configPath );
        String outputPath = FileNameUtils.getBaseName(path.getFileName().toString());
        return outputPath.replace(".config", "");

    }

}
