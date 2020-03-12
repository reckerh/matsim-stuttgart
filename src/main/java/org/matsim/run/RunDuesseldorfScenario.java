package org.matsim.run;

import org.matsim.prepare.CreateNetwork;
import org.matsim.prepare.CreateTransitSchedule;
import picocli.CommandLine;

@CommandLine.Command(
        header = ":: Open Düsseldorf Scenario ::",
        version = "1.0"
)
@MATSimApplication.Prepare({CreateNetwork.class, CreateTransitSchedule.class})
public class RunDuesseldorfScenario extends MATSimApplication {

    public RunDuesseldorfScenario() {
        super("scenarios/duesseldorf-1pct/input/duesseldorf-1pct.config.xml");
    }

    public static void main(String[] args) {
        MATSimApplication.run(RunDuesseldorfScenario.class, args);
    }
}
