package org.matsim.stuttgart.analysis;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.io.IOUtils;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;


/**
 * @author dwedekind
 */

/**
 * This is a class for analyzing the Senozon plans input
 *
 * The main method takes to input arguments:
 * -populationFile => File of population to be analyzed
 * -outputDir => What directory to write the analysis output to
 *
 */


public class PlansActivityAnalyzer {
    private static final Logger log = LogManager.getLogger(PlansActivityAnalyzer.class);
    private final String[] HEADER = {"measure", "value"};
    private final String separator = ";";

    private final String homeActivityPrefix = "home";

    private int countWithFirstActivityHome = 0;
    private int countWithoutFirstActivityHome = 0;

    private int countWithOvernightHome = 0;
    private int countWithoutOvernightHome = 0;
    private int countWithoutOvernightHomeAndShortDuration = 0;

    private int countIsNotHomeConsistent = 0;

    private int countStayAtHome = 0;
    private int countStayAtOther = 0;

    private final Map<Id<Person>,Integer> personId2SubtourCount = new HashMap<>();


    public static void main(String[] args) {

        PlansActivityAnalyzer.Input input = new PlansActivityAnalyzer.Input();
        JCommander.newBuilder().addObject(input).build().parse(args);

        var scenario = ScenarioUtils.createMutableScenario(ConfigUtils.createConfig());
        log.info("loading population");
        new PopulationReader(scenario).readFile(input.populationFile);

        PlansActivityAnalyzer analyzer = new PlansActivityAnalyzer();
        analyzer.runAnalysis(scenario.getPopulation());

        log.info("print results to..");
        log.info(input.outputDir);
        analyzer.printResults(input.outputDir);

    }




    public void runAnalysis (Population population){
        for (var person: population.getPersons().values()){

            // Exclude freight and commercial
            if (! (person.getId().toString().startsWith("freight") || person.getId().toString().startsWith("commercial"))){

                Plan selectedPlan = person.getSelectedPlan();

                // Check whether agents stay at home
                checkStayAtHome(selectedPlan);

                // Check home location consistency
                checkHomeLocationConsistency(selectedPlan);

                // Check first and last plan element for home activity
                checkFirstAndLastActivities(selectedPlan);

                // Inspect subtours
                personId2SubtourCount.put(person.getId(), TripStructureUtils.getSubtours(selectedPlan).size());

            }

        }

    }


    private void checkStayAtHome(Plan selectedPlan) {
        // Inspect if persons has one (home) activity only
        if (selectedPlan.getPlanElements().size() == 1){
            if (((Activity) selectedPlan.getPlanElements().get(0)).getType().startsWith(homeActivityPrefix)){
                countStayAtHome = countStayAtHome + 1;
            } else {
                countStayAtOther = countStayAtOther + 1;
            }
        }
    }


    private void checkHomeLocationConsistency(Plan selectedPlan) {
        // Inspect all elements for home locations (and whether they are consistent)
        List<Activity> homeActivities = selectedPlan.getPlanElements().stream()
                .filter(planElement -> planElement instanceof Activity)
                .map(planElement -> (Activity) planElement)
                .filter(activity -> activity.getType().startsWith(homeActivityPrefix))
                .collect(Collectors.toList());

        boolean isConsistent = true;
        if (homeActivities.size() > 1){
            Coord homeCoord = homeActivities.get(0).getCoord();


            // Start with 1!
            for (int i = 1; i < homeActivities.size(); i++){
                if (! homeCoord.equals(homeActivities.get(i).getCoord())){
                    isConsistent = false;
                }
            }
        }

        if (! isConsistent){
            countIsNotHomeConsistent = countIsNotHomeConsistent + 1;
        }
    }


    private void checkFirstAndLastActivities(Plan selectedPlan){
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
    }


    private void printResults(String outputDir) {
        try {

            CSVPrinter csvPrinter = new CSVPrinter(IOUtils.getBufferedWriter(outputDir + "/activityAnalysisOutput.csv"),
                    CSVFormat.DEFAULT.withDelimiter(separator.charAt(0)).withHeader(HEADER));

            int countFirstActivityTotal = countWithFirstActivityHome + countWithoutFirstActivityHome;
            double percentageWithFirstHome = (double) countWithFirstActivityHome/countFirstActivityTotal;
            double percentageWithoutFirstHome = (double) countWithoutFirstActivityHome/countFirstActivityTotal;
            int countStayNotAtNome = countFirstActivityTotal - countStayAtHome;
            double percentageStayAtHome = (double) countStayAtHome/countFirstActivityTotal;
            double percentageStayNotAtNome = (double) countStayNotAtNome/countFirstActivityTotal;
            int countHomeConsistent = countFirstActivityTotal - countIsNotHomeConsistent;
            double percentageHomeConsistent = (double) countHomeConsistent/countFirstActivityTotal;
            double percentageHomeNotConsistent = (double) countIsNotHomeConsistent/countFirstActivityTotal;
            int countLastActivityTotal = countWithFirstActivityHome + countWithoutFirstActivityHome;
            double percentageWithOvernight = (double) countWithOvernightHome/countLastActivityTotal;
            double percentageWithoutOvernight = (double) countWithoutOvernightHome/countLastActivityTotal;
            double percentageWithoutOvernightHomeAndShortDuration = (double) countWithoutOvernightHomeAndShortDuration/countLastActivityTotal;

            OptionalDouble average = personId2SubtourCount.values()
                    .stream()
                    .mapToDouble(a -> a)
                    .average();


            csvPrinter.printRecord(formatPrintOutput("Agents total", String.valueOf(countFirstActivityTotal)));
            csvPrinter.printRecord(formatPrintOutput("----------------------------------------"));

            csvPrinter.printRecord(formatPrintOutput("STAY AT HOME"));
            csvPrinter.printRecord(formatPrintOutput("Stay not at home [cnt]", String.valueOf(countStayNotAtNome)));
            csvPrinter.printRecord(formatPrintOutput("Stay not at home [%]", String.valueOf(Math.round(percentageStayNotAtNome * 100))));
            csvPrinter.printRecord(formatPrintOutput("Stay at home [cnt]", String.valueOf(countStayAtHome)));
            csvPrinter.printRecord(formatPrintOutput("Stay at home [%]", String.valueOf(Math.round(percentageStayAtHome * 100))));

            if (countStayAtOther > 0){
                csvPrinter.printRecord(formatPrintOutput("Be careful! There are agents staying the full simulation day at other activity type than home"));
            }

            csvPrinter.printRecord(formatPrintOutput("----------------------------------------"));
            csvPrinter.printRecord(formatPrintOutput("HOME ACTIVITY LOCATION CONSISTENCY"));
            csvPrinter.printRecord(formatPrintOutput("Consistent Home Activity Locations [cnt]", String.valueOf(countHomeConsistent)));
            csvPrinter.printRecord(formatPrintOutput("Consistent Home Activity Locations [%]", String.valueOf(Math.round(percentageHomeConsistent * 100))));
            csvPrinter.printRecord(formatPrintOutput("Non-consistent Home Activity Locations [cnt]", String.valueOf(countIsNotHomeConsistent)));
            csvPrinter.printRecord(formatPrintOutput("Non-consistent Home Activity Locations [%]", String.valueOf(Math.round(percentageHomeNotConsistent * 100))));

            csvPrinter.printRecord(formatPrintOutput("----------------------------------------"));
            csvPrinter.printRecord(formatPrintOutput("FIRST ACTIVITIES"));
            csvPrinter.printRecord(formatPrintOutput("First act type 'home' [cnt]", String.valueOf(countWithFirstActivityHome)));
            csvPrinter.printRecord(formatPrintOutput("First act type 'home' [%]", String.valueOf(Math.round(percentageWithFirstHome * 100))));
            csvPrinter.printRecord(formatPrintOutput("First act other type [cnt]", String.valueOf(countWithoutFirstActivityHome)));
            csvPrinter.printRecord(formatPrintOutput("First act other type [%]", String.valueOf(Math.round(percentageWithoutFirstHome * 100))));

            csvPrinter.printRecord(formatPrintOutput("----------------------------------------"));
            csvPrinter.printRecord(formatPrintOutput("LAST ACTIVITIES"));
            csvPrinter.printRecord(formatPrintOutput("Last act type 'home' [cnt]", String.valueOf(countWithOvernightHome)));
            csvPrinter.printRecord(formatPrintOutput("Last act type 'home' [%]", String.valueOf(Math.round(percentageWithOvernight * 100))));
            csvPrinter.printRecord(formatPrintOutput("Last act other type [cnt]", String.valueOf(countWithoutOvernightHome)));
            csvPrinter.printRecord(formatPrintOutput("Last act other type [%]", String.valueOf(Math.round(percentageWithoutOvernight * 100))));
            csvPrinter.printRecord(formatPrintOutput("Last act type 'home' AND act dur (< 60 min) [cnt]", String.valueOf(countWithoutOvernightHomeAndShortDuration)));
            csvPrinter.printRecord(formatPrintOutput("Last act type 'home' AND act dur (< 60 min) [%]", String.valueOf(Math.round(percentageWithoutOvernightHomeAndShortDuration * 100))));

            csvPrinter.printRecord(formatPrintOutput("----------------------------------------"));
            csvPrinter.printRecord(formatPrintOutput("SUBTOURS"));



            if (average.isPresent()){
                csvPrinter.printRecord(formatPrintOutput("Avg. number of subtours: ", String.valueOf(average.getAsDouble())));
            }

            long noAct = personId2SubtourCount.values().stream().filter(v -> v == 0).count();
            csvPrinter.printRecord(formatPrintOutput("0 subtours [cnt]", String.valueOf(noAct)));

            long oneAct = personId2SubtourCount.values().stream().filter(v -> v == 1).count();
            csvPrinter.printRecord(formatPrintOutput("1 subtours [cnt]", String.valueOf(oneAct)));

            long twoAct = personId2SubtourCount.values().stream().filter(v -> v == 2).count();
            csvPrinter.printRecord(formatPrintOutput("2 subtours [cnt]", String.valueOf(twoAct)));

            long threeAct = personId2SubtourCount.values().stream().filter(v -> v == 3).count();
            csvPrinter.printRecord(formatPrintOutput("3 subtours [cnt]", String.valueOf(threeAct)));

            long fourAct = personId2SubtourCount.values().stream().filter(v -> v == 4).count();
            csvPrinter.printRecord(formatPrintOutput("4 subtours [cnt]", String.valueOf(fourAct)));

            long moreAct = personId2SubtourCount.values().stream().filter(v -> v > 4).count();
            csvPrinter.printRecord(formatPrintOutput("More than 4 subtours [cnt]", String.valueOf(moreAct)));

            csvPrinter.close();
            log.info("person2Fare written to: " + outputDir + "/activityAnalysisOutput.csv");

        } catch (IOException e) {
            e.printStackTrace();
        }

    }


    private List<String> formatPrintOutput(String fstArgument) {
        System.out.println(fstArgument);
        return Arrays.asList(fstArgument, "");
    }


    private List<String> formatPrintOutput(String fstArgument, String sndArgument) {
        System.out.println(fstArgument + ": " + sndArgument);
        return Arrays.asList(fstArgument, sndArgument);
    }


    private static class Input {

        @Parameter(names = "-populationFile")
        private String populationFile;

        @Parameter(names = "-outputDir")
        private String outputDir;

    }
}
