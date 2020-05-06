package org.matsim.run;

import org.matsim.core.config.Config;
import org.matsim.core.config.groups.PlansConfigGroup;
import org.matsim.core.controler.Controler;
import org.matsim.prepare.CreateNetwork;
import org.matsim.prepare.CreateTransitSchedule;
import picocli.CommandLine;

@CommandLine.Command(
        header = ":: Open Düsseldorf Scenario ::",
        version = "1.0"
)
@MATSimApplication.Prepare({CreateNetwork.class, CreateTransitSchedule.class})
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

        addDefaultActivityParams(config);

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
