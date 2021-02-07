package org.matsim.stuttgart.prepare;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.core.scenario.ScenarioUtils;

public class RemoveFacilitiesFromPlans {

    private static final Logger log = Logger.getLogger(RemoveFacilitiesFromPlans.class);

    public static void main(String[] args) {
        RemoveFacilitiesFromPlans.Input input = new RemoveFacilitiesFromPlans.Input();
        JCommander.newBuilder().addObject(input).build().parse(args);

        var scenario = ScenarioUtils.createMutableScenario(ConfigUtils.createConfig());

        log.info("Loading population...");
        new PopulationReader(scenario).readFile(input.populationFile);

        log.info("Modify population...");
        new RemoveFacilitiesFromPlans().run(scenario);

        log.info("Write population output...");
        new PopulationWriter(scenario.getPopulation()).write(input.outputFile);

    }

    public void run(Scenario scenario){

        for(var person: scenario.getPopulation().getPersons().values()){
            Plan selectedPlan = person.getSelectedPlan();

            for(PlanElement pe: selectedPlan.getPlanElements()){
                if (pe instanceof Activity){
                    Activity activity = (Activity) pe;
                    activity.setFacilityId(null);
                }
            }
        }

    }

    private static class Input {

        @Parameter(names = "-populationFile")
        private String populationFile;

        @Parameter(names = "-outputFile")
        private String outputFile;

    }

}

