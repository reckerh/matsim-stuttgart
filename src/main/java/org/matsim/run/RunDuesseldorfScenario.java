package org.matsim.run;

import picocli.CommandLine;

@CommandLine.Command(
        header = ":: Open Düsseldorf Scenario ::",
        version = "1.0"
)
public class RunDuesseldorfScenario extends MATSimApplication {

    public RunDuesseldorfScenario() {
        super("scenarios/duesseldorf-1pct/input/duesseldorf-1pct.config.xml");
    }

    public static void main(String[] args) {
        MATSimApplication.run(RunDuesseldorfScenario.class, args);
    }
}
