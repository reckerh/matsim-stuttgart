package org.matsim.stuttgart.analysis;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;


public class PlansActivityAnalyzer {
    private static final Logger log = Logger.getLogger(PlansActivityAnalyzer.class);

    public static void main(String[] args) {

        PlansActivityAnalyzer.Input input = new PlansActivityAnalyzer.Input();
        JCommander.newBuilder().addObject(input).build().parse(args);

        var scenario = ScenarioUtils.createMutableScenario(ConfigUtils.createConfig());
        log.info("loading population");
        new PopulationReader(scenario).readFile(input.populationFile);
        PlansActivityAnalyzer.runAnalysis(scenario.getPopulation());

    }


    public static void runAnalysis (Population population){
        final String homeActivityPrefix = "home";

        int countWithFirstActivityHome = 0;
        int countWithoutFirstActivityHome = 0;

        int countWithOvernightHome = 0;
        int countWithoutOvernightHome = 0;
        int countWithoutOvernightHomeAndShortDuration = 0;

        Map<Id<Person>,Integer> personId2SubtourCount = new HashMap<>();

        for (var person: population.getPersons().values()){

            Plan selectedPlan = person.getSelectedPlan();

            // Inspect first element
            PlanElement firstPlanElement = selectedPlan.getPlanElements().get(0);

            if (firstPlanElement instanceof Activity) {
                Activity firstActivity = (Activity) firstPlanElement;
                if (firstActivity.getType().startsWith(homeActivityPrefix)) {
                    countWithFirstActivityHome = countWithFirstActivityHome + 1;
                } else {
                    countWithoutFirstActivityHome = countWithoutFirstActivityHome + 1;
                }
            }

            // Inspect last element
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


            // Inspect subtours
            personId2SubtourCount.put(person.getId(), TripStructureUtils.getSubtours(selectedPlan).size());

        }


        int countFirstActivityTotal = countWithFirstActivityHome + countWithoutFirstActivityHome;
        double percentageWithFirstHome = (double) countWithFirstActivityHome/countFirstActivityTotal;
        double percentageWithoutFirstHome = (double) countWithoutFirstActivityHome/countFirstActivityTotal;


        System.out.println("Agents total: " + countFirstActivityTotal);
        System.out.println("----------------------------------------");

        System.out.println("FIRST ACTIVITIES");
        System.out.println("First act type 'home' [cnt]: " + countWithFirstActivityHome);
        System.out.println("First act type 'home' [%]: " + Math.round(percentageWithFirstHome * 100)) ;
        System.out.println("First act other type [cnt]: " + countWithoutFirstActivityHome);
        System.out.println("First act other type [%]: " + Math.round(percentageWithoutFirstHome * 100)) ;


        int countLastActivityTotal = countWithFirstActivityHome + countWithoutFirstActivityHome;
        double percentageWithOvernight = (double) countWithOvernightHome/countLastActivityTotal;
        double percentageWithoutOvernight = (double) countWithoutOvernightHome/countLastActivityTotal;
        double percentageWithoutOvernightHomeAndShortDuration = (double) countWithoutOvernightHomeAndShortDuration/countLastActivityTotal;

        System.out.println("----------------------------------------");
        System.out.println("LAST ACTIVITIES");
        System.out.println("Last act type 'home' [cnt]: " + countWithOvernightHome);
        System.out.println("Last act type 'home' [%]: " + Math.round(percentageWithOvernight * 100)) ;
        System.out.println("Last act other type [cnt]: " + countWithoutOvernightHome);
        System.out.println("Last act other type [%]: " + Math.round(percentageWithoutOvernight * 100));
        System.out.println("Last act type 'home' AND act dur (< 60 min) [cnt]: " + countWithoutOvernightHomeAndShortDuration);
        System.out.println("Last act type 'home' AND act dur (< 60 min) [%]: " + Math.round(percentageWithoutOvernightHomeAndShortDuration * 100));

        System.out.println("----------------------------------------");
        System.out.println("SUBTOURS");

        OptionalDouble average = personId2SubtourCount.values()
                .stream()
                .mapToDouble(a -> a)
                .average();

        if (average.isPresent()){
            System.out.println("Avg. number of subtours: " + average.getAsDouble());
        }

        long noAct = personId2SubtourCount.values().stream().filter(v -> v == 0).count();
        System.out.println("0 subtours [cnt]: " + noAct);

        long oneAct = personId2SubtourCount.values().stream().filter(v -> v == 1).count();
        System.out.println("1 subtours [cnt]: " + oneAct);

        long twoAct = personId2SubtourCount.values().stream().filter(v -> v == 2).count();
        System.out.println("2 subtours [cnt]: " + twoAct);

        long threeAct = personId2SubtourCount.values().stream().filter(v -> v == 3).count();
        System.out.println("3 subtours [cnt]: " + threeAct);

        long fourAct = personId2SubtourCount.values().stream().filter(v -> v == 4).count();
        System.out.println("4 subtours [cnt]: " + fourAct);

        long moreAct = personId2SubtourCount.values().stream().filter(v -> v > 4).count();
        System.out.println("More than 4 subtours [cnt]: " + moreAct);


    }


    private static class Input {

        @Parameter(names = "-populationFile")
        private String populationFile;

    }
}
