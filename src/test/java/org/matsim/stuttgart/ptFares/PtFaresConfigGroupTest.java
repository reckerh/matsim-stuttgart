package org.matsim.stuttgart.ptFares;

import org.junit.Ignore;
import org.junit.Test;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;

public class PtFaresConfigGroupTest {

    @Test
    @Ignore
    public final void runPtFaresConfigGroupTest(){
        String configPath = "test/input/ptFares/config_withPtFares.xml";

        Config config = ConfigUtils.loadConfig(configPath, new PtFaresConfigGroup());

        PtFaresConfigGroup ptFaresConfigGroup = ConfigUtils.addOrGetModule(config, PtFaresConfigGroup.GROUP, PtFaresConfigGroup.class);

        System.out.println(ptFaresConfigGroup.getPtFareZoneAttributeName());
        System.out.println(ptFaresConfigGroup.getPtInteractionPrefix());

        System.out.println(ptFaresConfigGroup.getZonesGroup().getOutOfZoneTag());
        System.out.println(ptFaresConfigGroup.getFaresGroup().getOutOfZonePrice());

        for (var zone: ptFaresConfigGroup.getZonesGroup().getZones()){
            System.out.println(zone.getZoneName());
            System.out.println(zone.getIsHybrid());
            System.out.println(zone.getZoneAssignment());
            System.out.println("-------------------------");
        }

        System.out.println("----------ZONE SET TESTS (BASE ZONES) ---------------");

        for (var zone: ptFaresConfigGroup.getZonesGroup().getBaseZones()){
            System.out.println(zone.getZoneName());
        }
        System.out.println("-------------------------");
        for (var zone: ptFaresConfigGroup.getZonesGroup().getBaseZoneStrings()){
            System.out.println(zone);
        }
        System.out.println("-------------------------");


        System.out.println("----------ZONE SET TESTS (HYBRID ZONES) ---------------");

        for (var zone: ptFaresConfigGroup.getZonesGroup().getHybridZones()){
            System.out.println(zone.getZoneName());
        }
        System.out.println("-------------------------");
        for (var zone: ptFaresConfigGroup.getZonesGroup().getHybridZoneStringsWithCorrBaseZones().keySet()){
            System.out.println(zone);
        }
        System.out.println("-------------------------");


        System.out.println("----------FARES SET TESTS (BASE ZONES) ---------------");

        for (var fare: ptFaresConfigGroup.getFaresGroup().getFares().entrySet()){
            System.out.println("Fare for " + fare.getKey() + " zones is: " + fare.getValue());
        }

    }

}
