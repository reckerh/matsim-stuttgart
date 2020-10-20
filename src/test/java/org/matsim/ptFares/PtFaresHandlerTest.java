package org.matsim.ptFares;

import ch.sbb.matsim.config.SBBTransitConfigGroup;
import ch.sbb.matsim.config.SwissRailRaptorConfigGroup;
import ch.sbb.matsim.mobsim.qsim.SBBTransitModule;
import ch.sbb.matsim.mobsim.qsim.pt.SBBTransitEngineQSimModule;
import ch.sbb.matsim.routing.pt.raptor.RaptorIntermodalAccessEgress;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorModule;
import org.apache.log4j.Logger;
import org.junit.Test;
import org.locationtech.jts.util.Assert;
import org.matsim.api.core.v01.Id;
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
import org.matsim.core.gbl.Gbl;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.parkingCost.ParkingCostConfigGroup;
import org.matsim.parkingCost.ParkingCostModule;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.matsim.core.config.groups.ControlerConfigGroup.RoutingAlgorithmType.FastAStarLandmarks;

public class PtFaresHandlerTest {
    private static final Logger log = Logger.getLogger(PtFaresHandlerTest.class );

    @Test
    public final void testPtFaresHandlerTest2(){

        String configPath = "./test/input/config.xml";

        // Prepare config
        Config config = prepareConfig(configPath);

        // Prepare scenario
        Scenario scenario = prepareScenario(config) ;
        Controler controler = prepareControler(scenario) ;
        controler.run();

        // Check output param
        Assert.equals(scenario.getPopulation().getPersons().get(Id.createPersonId("1")).getSelectedPlan().getScore(), 200);
        Assert.equals(scenario.getPopulation().getPersons().get(Id.createPersonId("2")).getSelectedPlan().getScore(), 200);
        Assert.equals(scenario.getPopulation().getPersons().get(Id.createPersonId("3")).getSelectedPlan().getScore(), 100);
        Assert.equals(scenario.getPopulation().getPersons().get(Id.createPersonId("4")).getSelectedPlan().getScore(), 100);
        Assert.equals(scenario.getPopulation().getPersons().get(Id.createPersonId("5")).getSelectedPlan().getScore(), 100);
        Assert.equals(scenario.getPopulation().getPersons().get(Id.createPersonId("6")).getSelectedPlan().getScore(), 200);

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

        // use parking cost module
        controler.addOverridingModule(new ParkingCostModule());

        // use pt fares module
        controler.addOverridingModule(new PtFaresModule());

        return controler;
    }


    private Scenario prepareScenario(Config config) {

        Gbl.assertNotNull( config );

        Scenario scenario = ScenarioUtils.loadScenario(config);

        // write fareZones into transitScheduleFile
        TransitSchedule schedule = scenario.getTransitSchedule();

        schedule.getFacilities().get(Id.create("1", TransitStopFacility.class)).getAttributes().putAttribute("FareZone", "2");
        schedule.getFacilities().get(Id.create("2a", TransitStopFacility.class)).getAttributes().putAttribute("FareZone", "2");
        schedule.getFacilities().get(Id.create("2b", TransitStopFacility.class)).getAttributes().putAttribute("FareZone", "2");
        schedule.getFacilities().get(Id.create("3", TransitStopFacility.class)).getAttributes().putAttribute("FareZone", "1,2");


        schedule.getFacilities().get(Id.create("4", TransitStopFacility.class)).getAttributes().putAttribute("FareZone", "2");
        schedule.getFacilities().get(Id.create("5a", TransitStopFacility.class)).getAttributes().putAttribute("FareZone", "2");
        schedule.getFacilities().get(Id.create("5b", TransitStopFacility.class)).getAttributes().putAttribute("FareZone", "2");
        schedule.getFacilities().get(Id.create("6", TransitStopFacility.class)).getAttributes().putAttribute("FareZone", "2");

        return scenario;
    }


    private Config prepareConfig(String configPath) {


        ConfigGroup[] customModulesToAdd = new ConfigGroup[]{ setupRaptorConfigGroup(), setupPTFaresGroup(), setupSBBTransit(), new ParkingCostConfigGroup()};
        ConfigGroup[] customModulesAll = new ConfigGroup[customModulesToAdd.length];

        int counter = 0;

        for (ConfigGroup customModule : customModulesToAdd) {
            customModulesAll[counter] = customModule;
            counter++;
        }

        // -- LOAD CONFIG WITH CUSTOM MODULES
        final Config config = ConfigUtils.loadConfig( configPath, customModulesAll );


        // -- CONTROLER --
        config.controler().setLastIteration(0);
        config.controler().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);


        // -- VSP DEFAULTS --
        config.vspExperimental().setVspDefaultsCheckingLevel( VspExperimentalConfigGroup.VspDefaultsCheckingLevel.ignore );
        config.plansCalcRoute().setAccessEgressType(PlansCalcRouteConfigGroup.AccessEgressType.accessEgressModeToLink); //setInsertingAccessEgressWalk( true );
        config.qsim().setUsingTravelTimeCheckInTeleportation( true );
        config.qsim().setTrafficDynamics( QSimConfigGroup.TrafficDynamics.kinematicWaves );


        // -- OTHER --
        config.qsim().setUsePersonIdForMissingVehicleId(false);
        config.controler().setRoutingAlgorithmType(FastAStarLandmarks);
        config.subtourModeChoice().setProbaForRandomSingleTripMode( 0.5 );
        config.qsim().setInsertingWaitingVehiclesBeforeDrivingVehicles( true );

        return config ;

    }


    private static SwissRailRaptorConfigGroup setupRaptorConfigGroup() {

        SwissRailRaptorConfigGroup configRaptor = new SwissRailRaptorConfigGroup();
        configRaptor.setUseIntermodalAccessEgress(false);

        return configRaptor;
    }


    private static PtFaresConfigGroup setupPTFaresGroup() {

        PtFaresConfigGroup configFares = new PtFaresConfigGroup();

        // For values, see https://www.vvs.de/tickets/zeittickets-abo-polygo/jahresticket-jedermann/

        PtFaresConfigGroup.ZonePricesParameterSet paramSetZone1 = new PtFaresConfigGroup.ZonePricesParameterSet();
        paramSetZone1.setNumberZones(1);
        paramSetZone1.setTicketPrice(100.);
        configFares.addZonePriceSettings(paramSetZone1);

        PtFaresConfigGroup.ZonePricesParameterSet paramSetZone2 = new PtFaresConfigGroup.ZonePricesParameterSet();
        paramSetZone1.setNumberZones(2);
        paramSetZone1.setTicketPrice(200.);
        configFares.addZonePriceSettings(paramSetZone2);

        return configFares;
    }


    private static SBBTransitConfigGroup setupSBBTransit() {

        SBBTransitConfigGroup sbbTransit = new SBBTransitConfigGroup();
        Set<String> modes = new HashSet<>(Arrays.asList(new String[]{"train"}));
        sbbTransit.setDeterministicServiceModes(modes);
        sbbTransit.setCreateLinkEventsInterval(1);

        return sbbTransit;
    }

}
