package org.matsim.stuttgart.run;

import org.junit.Ignore;
import org.junit.Test;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.controler.Controler;
import org.matsim.core.scenario.ScenarioUtils;

public class RunStuttgartTest {

    @Test
    @Ignore
    public void testRunner(){


        String configInput = System.getProperty("user.dir") + "/../shared-svn/projects/matsim-stuttgart/stuttgart-v1.0/stuttgart-v1.0-1pct.config_cal_147_01_1pct.xml" ;
        Config config = RunStuttgart.loadConfig(new String[]{configInput} ) ;

        // Some config modifications for the reduced test
        config.controler().setLastIteration(2);
        String outputDir = System.getProperty("user.dir") + "/../shared-svn/projects/matsim-stuttgart/stuttgart-v1.0/output" ;
        config.controler().setOutputDirectory(outputDir);

        Scenario scenario = RunStuttgart.loadScenario(config);
        Controler controler = RunStuttgart.loadControler(scenario);

        controler.run();


    }
}
