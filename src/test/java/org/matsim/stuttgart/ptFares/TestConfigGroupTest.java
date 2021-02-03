package org.matsim.stuttgart.ptFares;

import org.junit.Test;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;

public class TestConfigGroupTest {

    @Test
    public void runConfigGroupTest(){

        String configPath = "C:/Users/david/OneDrive/02_Uni/02_Master/05_Masterarbeit/03_MATSim/02_runs/stuttgart-v1.0/05_stuttgart-v1.0_scenariotest/stuttgart-v1.0-1pct.config_cfgTest.xml";
        Config config = ConfigUtils.loadConfig(configPath, new TestConfigGroup());

        TestConfigGroup testConfigGroup = ConfigUtils.addOrGetModule(config, TestConfigGroup.GROUP_NAME, TestConfigGroup.class);

        System.out.println(testConfigGroup.getDoubleField());
        System.out.println(testConfigGroup.getConfigSubgroup().getIntegerField());
    }
}
