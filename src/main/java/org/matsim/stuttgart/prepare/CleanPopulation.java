package org.matsim.stuttgart.prepare;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.stuttgart.Utils;

import java.nio.file.Path;
import java.nio.file.Paths;

public class CleanPopulation {

    private static final Logger log = Logger.getLogger(CleanPopulation.class);
    private static final String inputPopulation = "projects\\matsim-stuttgart\\stuttgart-v0.0-snz-original\\optimizedPopulation.xml.gz";
    private static final String outputPopulation = "projects\\matsim-stuttgart\\stuttgart-v2.0\\input\\population-25pct-stuttgart.xml.gz";

    public static void main(String[] args) {

        var arguments = Utils.parseSharedSvn(args);
        clean(Paths.get(arguments.getSharedSvn()));
    }

    public static void clean(Path sharedSvn) {
        var scenario = ScenarioUtils.createMutableScenario(ConfigUtils.createConfig());

        log.info("loading population");
        new PopulationReader(scenario).readFile(sharedSvn.resolve(inputPopulation).toString());

        scenario.getPopulation().getPersons().values().parallelStream()
                .forEach(person -> {

                    var plan = person.getSelectedPlan();
                    plan.setScore(null);

                    // clear plans
                    person.getPlans().clear();
                    person.setSelectedPlan(null);

                    // remove network information of plan elements
                    for (PlanElement element : plan.getPlanElements()) {

                        if (element instanceof Activity) {
                            var activity = (Activity) element;
                            activity.setFacilityId(null);
                            activity.setLinkId(null);
                        } else if (element instanceof Leg) {
                            var leg = (Leg) element;
                            leg.setRoute(null);
                        }
                    }

                    // will also set the plan to be selected plan
                    person.addPlan(plan);
                });

        splitActivityTypesBasedOnDuration(scenario.getPopulation());

        new PopulationWriter(scenario.getPopulation()).write(sharedSvn.resolve(outputPopulation).toString());
    }

    /**
     * Split activities into typical durations to improve value of travel time savings calculation.
     */
    private static void splitActivityTypesBasedOnDuration(Population population) {

        final double timeBinSize_s = 600.;

        // Calculate activity durations for the next step
        for (Person p : population.getPersons().values()) {
            for (Plan plan : p.getPlans()) {
                for (PlanElement el : plan.getPlanElements()) {

                    if (!(el instanceof Activity))
                        continue;

                    Activity act = (Activity) el;
                    double duration = act.getEndTime().orElse(24 * 3600)
                            - act.getStartTime().orElse(0);

                    int durationCategoryNr = (int) Math.round((duration / timeBinSize_s));

                    if (durationCategoryNr <= 0) {
                        durationCategoryNr = 1;
                    }

                    String newType = act.getType() + "_" + (durationCategoryNr * timeBinSize_s);
                    act.setType(newType);

                }

                mergeOvernightActivities(plan);
            }
        }
    }

    private static void mergeOvernightActivities(Plan plan) {

        if (plan.getPlanElements().size() > 1) {
            Activity firstActivity = (Activity) plan.getPlanElements().get(0);
            Activity lastActivity = (Activity) plan.getPlanElements().get(plan.getPlanElements().size() - 1);

            String firstBaseActivity = firstActivity.getType().split("_")[0];
            String lastBaseActivity = lastActivity.getType().split("_")[0];

            if (firstBaseActivity.equals(lastBaseActivity)) {
                double mergedDuration = Double.parseDouble(firstActivity.getType().split("_")[1]) + Double.parseDouble(lastActivity.getType().split("_")[1]);


                firstActivity.setType(firstBaseActivity + "_" + mergedDuration);
                lastActivity.setType(lastBaseActivity + "_" + mergedDuration);
            }
        }  // skipping plans with just one activity

    }
}
