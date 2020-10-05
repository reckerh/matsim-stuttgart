package org.matsim.ptFares;

import org.junit.Test;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy.OverwriteFileSetting;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.TransitSchedule;

import java.util.List;

class PtFaresHandlerTest {

    @Test
    public final void testPtFaresHandler(){

        String configPath = "../../../../../../test/input/config.xml";

        System.out.println(configPath);


        // Load test config and add ptFaresConfigGroup
        Config config = ConfigUtils.loadConfig(configPath);

        PtFaresConfigGroup ptFares = setupPTFaresGroup();
        config.addModule(ptFares);

        // Further controler manipulations
        config.controler().setLastIteration(0);
        config.controler().setOverwriteFileSetting(OverwriteFileSetting.deleteDirectoryIfExists);

        // Load test scenario
        Scenario scenario = prepareScenario(config);

        // Create plans


        // Create controler and add pt Fares module
        Controler controler = new Controler(scenario);
        controler.addOverridingModule(new PtFaresModule());

        // Run scenario
        controler.run();

        // Check output param

        //Assert.assertEquals("Wrong score in iteration 0.", 137.00979198644234, controler.getScoreStats().getScoreHistory().get(ScoreItem.executed).get(0), MatsimTestUtils.EPSILON);
        //ToDo: How to get scroe stats of a specific person
        //controler.getScoreStats().getScoreHistory().get...
    }

    private Scenario prepareScenario(Config config) {

        Scenario scenario = ScenarioUtils.loadScenario(config);

        // write fareZones into transitScheduleFile
        TransitSchedule schedule = scenario.getTransitSchedule();

        schedule.getFacilities().get("1").getAttributes().putAttribute("FareZone", "1");
        schedule.getFacilities().get("2a").getAttributes().putAttribute("FareZone", "2");
        schedule.getFacilities().get("2b").getAttributes().putAttribute("FareZone", "2");
        schedule.getFacilities().get("3").getAttributes().putAttribute("FareZone", "1,2");

        schedule.getFacilities().get("4").getAttributes().putAttribute("FareZone", "2");
        schedule.getFacilities().get("5a").getAttributes().putAttribute("FareZone", "2");
        schedule.getFacilities().get("5b").getAttributes().putAttribute("FareZone", "2");
        schedule.getFacilities().get("6").getAttributes().putAttribute("FareZone", "2");

        return scenario;
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



}