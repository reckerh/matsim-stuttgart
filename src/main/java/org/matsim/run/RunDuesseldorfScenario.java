package org.matsim.run;

import org.matsim.core.config.Config;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.config.groups.PlansConfigGroup;
import org.matsim.core.controler.Controler;
import org.matsim.prepare.CreateNetwork;
import org.matsim.prepare.CreateTransitSchedule;
import org.matsim.prepare.PreparePopulation;
import picocli.CommandLine;

@CommandLine.Command(
        header = ":: Open Düsseldorf Scenario ::",
        version = "1.0"
)
@MATSimApplication.Prepare({CreateNetwork.class, CreateTransitSchedule.class, PreparePopulation.class})
public class RunDuesseldorfScenario extends MATSimApplication {

    /**
     * Default coordinate system of the scenario.
     */
    public static final String COORDINATE_SYSTEM = "EPSG:25832";

    public static final double[] X_EXTENT = new double[]{333926.98, 357174.31};
    public static final double[] Y_EXTENT = new double[]{5665283.05, 5687261.18};


    public RunDuesseldorfScenario() {
        super("scenarios/duesseldorf-1pct/input/duesseldorf-1pct.config.xml");
    }

    @Override
    protected Config prepareConfig(Config config) {

        //addDefaultActivityParams(config);

        // TODO: typical durations
        double ii = 3600 * 6;

        config.planCalcScore().addActivityParams(new PlanCalcScoreConfigGroup.ActivityParams("home").setTypicalDuration(ii));
        config.planCalcScore().addActivityParams(new PlanCalcScoreConfigGroup.ActivityParams("work").setTypicalDuration(ii).setOpeningTime(6. * 3600.).setClosingTime(20. * 3600.));
        config.planCalcScore().addActivityParams(new PlanCalcScoreConfigGroup.ActivityParams("business").setTypicalDuration(ii).setOpeningTime(6. * 3600.).setClosingTime(20. * 3600.));
        config.planCalcScore().addActivityParams(new PlanCalcScoreConfigGroup.ActivityParams("leisure").setTypicalDuration(ii).setOpeningTime(9. * 3600.).setClosingTime(27. * 3600.));
        config.planCalcScore().addActivityParams(new PlanCalcScoreConfigGroup.ActivityParams("shopping").setTypicalDuration(ii).setOpeningTime(8. * 3600.).setClosingTime(20. * 3600.));
        config.planCalcScore().addActivityParams(new PlanCalcScoreConfigGroup.ActivityParams("restaurant").setTypicalDuration(ii));
        config.planCalcScore().addActivityParams(new PlanCalcScoreConfigGroup.ActivityParams("other").setTypicalDuration(ii));
        config.planCalcScore().addActivityParams(new PlanCalcScoreConfigGroup.ActivityParams("visit").setTypicalDuration(ii));
        config.planCalcScore().addActivityParams(new PlanCalcScoreConfigGroup.ActivityParams("errands").setTypicalDuration(ii));
        config.planCalcScore().addActivityParams(new PlanCalcScoreConfigGroup.ActivityParams("educ_secondary").setTypicalDuration(ii));
        config.planCalcScore().addActivityParams(new PlanCalcScoreConfigGroup.ActivityParams("educ_higher").setTypicalDuration(ii));

        config.planCalcScore().addActivityParams(new PlanCalcScoreConfigGroup.ActivityParams("freight").setTypicalDuration(12. * 3600.));
        config.planCalcScore().addActivityParams(new PlanCalcScoreConfigGroup.ActivityParams("car interaction").setTypicalDuration(60));

        config.plans().setHandlingOfPlansWithoutRoutingMode(PlansConfigGroup.HandlingOfPlansWithoutRoutingMode.useMainModeIdentifier);

        return config;
    }

    @Override
    protected void prepareControler(Controler controler) {

      //  controler.addOverridingModule( new AbstractModule() {
      //      @Override
      //      public void install() {
      //          install( new SwissRailRaptorModule() );
      //      }
      //  } );
    }

    public static void main(String[] args) {
        MATSimApplication.run(RunDuesseldorfScenario.class, args);
    }
}
