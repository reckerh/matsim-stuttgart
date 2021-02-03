package org.matsim.stuttgart.analysis;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.stuttgart.prepare.ReducePopulation;


public class PlansWithoutOvernightActivityAnalyzer {
    private static final Logger log = Logger.getLogger(PlansWithoutOvernightActivityAnalyzer.class);

    public static void main(String[] args) {

        PlansWithoutOvernightActivityAnalyzer.Input input = new PlansWithoutOvernightActivityAnalyzer.Input();
        JCommander.newBuilder().addObject(input).build().parse(args);

        var scenario = ScenarioUtils.createMutableScenario(ConfigUtils.createConfig());
        log.info("loading population");
        new PopulationReader(scenario).readFile(input.populationFile);
        PlansWithoutOvernightActivityAnalyzer.runAnalysis(scenario.getPopulation());

    }


    public static void runAnalysis (Population population){
        final String homeActivityPrefix = "home";

        double countWithOvernightHome = 0;
        double countWithoutOvernightHome = 0;
        double countWithoutOvernightHomeAndShortDuration = 0;

        for (var person: population.getPersons().values()){

            Plan selectedPlan = person.getSelectedPlan();
            PlanElement lastPlanElement = selectedPlan.getPlanElements().get(selectedPlan.getPlanElements().size() - 1);

            if (lastPlanElement instanceof Activity){
                Activity lastActivity = (Activity)lastPlanElement;
                if (lastActivity.getType().startsWith(homeActivityPrefix)){
                    countWithOvernightHome = countWithOvernightHome + 1;

                } else {
                    countWithoutOvernightHome = countWithoutOvernightHome +1;
                    String lastActivityType = lastActivity.getType().substring(lastActivity.getType().lastIndexOf("_") + 1);
                    double scheduledActivityDuration = Double.parseDouble(lastActivityType);

                    if (scheduledActivityDuration < 3600.){
                        countWithoutOvernightHomeAndShortDuration = countWithoutOvernightHomeAndShortDuration + 1;

                    }

                }
            }

        }

        double countTotal = countWithOvernightHome + countWithoutOvernightHome;
        double percentageWithOvernight = countWithOvernightHome/countTotal;
        double percentageWithoutOvernight = countWithoutOvernightHome/countTotal;
        double percentageWithoutOvernightHomeAndShortDuration = countWithoutOvernightHomeAndShortDuration/countTotal;

        System.out.println("Agents total: " + countTotal);
        System.out.println("Thereof WITH home overnight: " + countWithOvernightHome);
        System.out.println("Thereof WITHOUT home overnight: " + countWithoutOvernightHome);
        System.out.println("Percentage WITH home overnight: " + percentageWithOvernight);
        System.out.println("Percentage WITHOUT home overnight: " + percentageWithoutOvernight);

        System.out.println("Agents WITHOUT home AND short duration (< 60 min): " + countWithoutOvernightHomeAndShortDuration);
        System.out.println("Percentage WITHOUT home AND short duration (< 60 min): " + percentageWithoutOvernightHomeAndShortDuration);

    }


    private static class Input {

        @Parameter(names = "-populationFile")
        private String populationFile;

    }
}
