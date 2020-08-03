package org.matsim.run;

import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorModule;
import org.matsim.contrib.signals.otfvis.OTFVisWithSignalsLiveModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ActivityParams;
import org.matsim.core.config.groups.PlansConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.prepare.CreateNetwork;
import org.matsim.prepare.CreateTransitSchedule;
import org.matsim.prepare.ExtractEvents;
import org.matsim.prepare.PreparePopulation;
import picocli.CommandLine;

import java.util.List;

@CommandLine.Command(
        header = ":: Open Düsseldorf Scenario ::",
        version = "1.0"
)
@MATSimApplication.Prepare({CreateNetwork.class, CreateTransitSchedule.class, PreparePopulation.class, ExtractEvents.class})
public class RunDuesseldorfScenario extends MATSimApplication {

    /**
     * Current version identifier.
     */
    public static final String VERSION = "v1.0";

    /**
     * Default coordinate system of the scenario.
     */
    public static final String COORDINATE_SYSTEM = "EPSG:25832";

    /**
     * 6.00° - 7.56°
     */
    public static final double[] X_EXTENT = new double[]{290_000.00, 400_000.0};
    /**
     * 50.60 - 51.65°
     */
    public static final double[] Y_EXTENT = new double[]{5_610_000.00, 5_722_000.00};

    @CommandLine.Option(names = "--otfvis", defaultValue = "false", description = "Enable OTFVis live view")
    private boolean otfvis;

    public RunDuesseldorfScenario() {
        super(String.format("scenarios/input/duesseldorf-%s-1pct.config.xml", VERSION));
    }

    public static void main(String[] args) {
        MATSimApplication.run(RunDuesseldorfScenario.class, args);
    }

    @Override
    protected Config prepareConfig(Config config) {

        //addDefaultActivityParams(config);

        for (long ii = 600; ii <= 97200; ii += 600) {

            for (String act : List.of("home", "restaurant", "other", "visit", "errands", "educ_higher", "educ_secondary")) {
                config.planCalcScore().addActivityParams(new ActivityParams(act + "_" + ii + ".0").setTypicalDuration(ii));
            }

            config.planCalcScore().addActivityParams(new ActivityParams("work_" + ii + ".0").setTypicalDuration(ii).setOpeningTime(6. * 3600.).setClosingTime(20. * 3600.));
            config.planCalcScore().addActivityParams(new ActivityParams("business_" + ii + ".0").setTypicalDuration(ii).setOpeningTime(6. * 3600.).setClosingTime(20. * 3600.));
            config.planCalcScore().addActivityParams(new ActivityParams("leisure_" + ii + ".0").setTypicalDuration(ii).setOpeningTime(9. * 3600.).setClosingTime(27. * 3600.));
            config.planCalcScore().addActivityParams(new ActivityParams("shopping_" + ii + ".0").setTypicalDuration(ii).setOpeningTime(8. * 3600.).setClosingTime(20. * 3600.));
        }

        // TODO: workaround to not use unnecessary resources for LinkToLink router
        config.global().setNumberOfThreads(1);

        // config.planCalcScore().addActivityParams(new ActivityParams("freight").setTypicalDuration(12. * 3600.));
        config.planCalcScore().addActivityParams(new ActivityParams("car interaction").setTypicalDuration(60));

        config.plans().setHandlingOfPlansWithoutRoutingMode(PlansConfigGroup.HandlingOfPlansWithoutRoutingMode.useMainModeIdentifier);

        return config;
    }

    @Override
    protected void prepareControler(Controler controler) {

        if (otfvis)
            controler.addOverridingModule(new OTFVisWithSignalsLiveModule());

        controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {
                install(new SwissRailRaptorModule());
            }
        });
    }
}
