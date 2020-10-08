package org.matsim.ptFares;

import org.junit.Test;
import org.locationtech.jts.util.Assert;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy.OverwriteFileSetting;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;


public class PtFaresHandlerTest {

    @Test
    public final void testPtFaresHandler(){

        String configPath = "./test/input/config.xml";

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
        Assert.equals(scenario.getPopulation().getPersons().get(Id.createPersonId("1")).getSelectedPlan().getScore(), 200);
        Assert.equals(scenario.getPopulation().getPersons().get(Id.createPersonId("2")).getSelectedPlan().getScore(), 200);
        Assert.equals(scenario.getPopulation().getPersons().get(Id.createPersonId("3")).getSelectedPlan().getScore(), 100);
        Assert.equals(scenario.getPopulation().getPersons().get(Id.createPersonId("4")).getSelectedPlan().getScore(), 100);
        Assert.equals(scenario.getPopulation().getPersons().get(Id.createPersonId("5")).getSelectedPlan().getScore(), 100);
        Assert.equals(scenario.getPopulation().getPersons().get(Id.createPersonId("6")).getSelectedPlan().getScore(), 200);

    }

    private Scenario prepareScenario(Config config) {

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