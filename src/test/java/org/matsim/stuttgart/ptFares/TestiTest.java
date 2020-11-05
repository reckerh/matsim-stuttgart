package org.matsim.stuttgart.ptFares;

import org.junit.Test;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlansCalcRouteConfigGroup;
import org.matsim.core.config.groups.QSimConfigGroup;
import org.matsim.core.config.groups.VspExperimentalConfigGroup;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.stuttgart.ptFares.PtFaresConfigGroup_v2;

import static org.matsim.core.config.groups.ControlerConfigGroup.RoutingAlgorithmType.FastAStarLandmarks;

public class TestiTest {

    @Test
    public final void main() {

        String configPath = "./test/input/config.xml";

        // Prepare config
        Config config = prepareConfig(configPath);
        ConfigUtils.writeConfig(config, "./test/test_config.xml");

    }

    private Config prepareConfig(String configPath) {

        // Add custom modules
        ConfigGroup[] customModulesToAdd = new ConfigGroup[]{setupPTFaresGroup()};
        ConfigGroup[] customModulesAll = new ConfigGroup[customModulesToAdd.length];

        int counter = 0;

        for (ConfigGroup customModule : customModulesToAdd) {
            customModulesAll[counter] = customModule;
            counter++;
        }

        // -- LOAD CONFIG WITH CUSTOM MODULES
        final Config config = ConfigUtils.loadConfig(configPath, customModulesAll);


        // -- CONTROLER --
        config.controler().setLastIteration(0);
        config.controler().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);


        // -- VSP DEFAULTS --
        config.vspExperimental().setVspDefaultsCheckingLevel(VspExperimentalConfigGroup.VspDefaultsCheckingLevel.ignore);
        config.plansCalcRoute().setAccessEgressType(PlansCalcRouteConfigGroup.AccessEgressType.accessEgressModeToLink); //setInsertingAccessEgressWalk( true );
        config.qsim().setUsingTravelTimeCheckInTeleportation(true);
        config.qsim().setTrafficDynamics(QSimConfigGroup.TrafficDynamics.kinematicWaves);


        // -- OTHER --
        config.qsim().setUsePersonIdForMissingVehicleId(false);
        config.controler().setRoutingAlgorithmType(FastAStarLandmarks);
        config.subtourModeChoice().setProbaForRandomSingleTripMode(0.5);
        config.qsim().setInsertingWaitingVehiclesBeforeDrivingVehicles(true);

        return config;

    }


    private static PtFaresConfigGroup_v2 setupPTFaresGroup() {

        PtFaresConfigGroup_v2 configFares = new PtFaresConfigGroup_v2();

        configFares.setOutOfZoneTag("outTest");
        configFares.setPtFareZoneAttributeName("zoneTest");



//        PtFaresConfigGroup_v2.FareTypesParameterSet fareTypes = new PtFaresConfigGroup_v2.FareTypesParameterSet();
//
//        PtFaresConfigGroup_v2.FareTypesParameterSet.FareTypeParameterSet fareType = new PtFaresConfigGroup_v2.FareTypesParameterSet.FareTypeParameterSet();
//        fareType.setNumberZones(1);
//        fareType.setTicketPrice(100.);
//        fareTypes.addFareTypeSettings(fareType);
//        configFares.addParameterSet(fareTypes);


        PtFaresConfigGroup_v2.ZonesParameterSet zones = new PtFaresConfigGroup_v2.ZonesParameterSet();

/*        PtFaresConfigGroup_v2.ZonesParameterSet.ZoneParameterSet zone = new PtFaresConfigGroup_v2.ZonesParameterSet.ZoneParameterSet();
        zone.setZoneName("1");
        zones.addZonesSettings(zone);*/

        configFares.addParameterSet(zones);


        return configFares;
    }


}
