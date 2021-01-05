package org.matsim.stuttgart.run;

import org.junit.Ignore;
import org.junit.Test;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.controler.Controler;

public class RunStuttgartTest {

    @Test
    @Ignore
    public void testRunnerWithAllExtensions(){

        String configInput = "C:/Users/david/OneDrive/02_Uni/02_Master/05_Masterarbeit/03_MATSim/02_runs/stuttgart-v1.0/02_stuttgart-v1.0_test/stuttgart-v1.0-0.001pct.config_test.xml" ;
        double bikeTeleportedModeSpeed = 4.0579732;
        double bikeIntermodalTransferCosts = -0.3;
        String[] chainModes = {"car, bike"};
        Config config = RunStuttgartWedekindCalibration.prepareConfig(bikeTeleportedModeSpeed, bikeIntermodalTransferCosts, chainModes, new String[]{configInput} ) ;

        // Some config modifications for the reduced test
        config.controler().setLastIteration(2) ;

        String fareZoneShapeFileName = "fareZones_sp.shp";
        String parkingZoneShapeFileName = "parkingShapes.shp";
        Scenario scenario = RunStuttgartWedekindCalibration.prepareScenario(config, fareZoneShapeFileName, parkingZoneShapeFileName) ;

        Controler controler = RunStuttgartWedekindCalibration.prepareControler(scenario) ;
        controler.run() ;

    }
}
