package org.matsim.stuttgart.ptFares;

import org.junit.Test;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.extensions.pt.fare.intermodalTripFareCompensator.IntermodalTripFareCompensatorsConfigGroup;

import java.util.Map;

public class PtFaresConfigGroupTest {

    @Test
    public final void runPtFaresConfigGroupTest(){
        String configPath = "test/input/ptFares/config_withPtFares.xml";

        Config config = ConfigUtils.loadConfig(configPath, new PtFaresConfigGroup());
        PtFaresConfigGroup ptFaresConfigGroup = ConfigUtils.addOrGetModule(config, PtFaresConfigGroup.GROUP_NAME, PtFaresConfigGroup.class);

        Map<Integer, Double> zones2Fares = ptFaresConfigGroup.getFaresGroup().getAllFares();

        for (var set: zones2Fares.entrySet()){
            System.out.println("No. zones: " + set.getKey().toString() + " => fare: " + set.getValue().toString());
        }

        

    }

}
