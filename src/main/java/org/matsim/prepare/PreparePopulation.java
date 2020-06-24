package org.matsim.prepare;

import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.scenario.ScenarioUtils;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * Creates the population from the original input data.
 *
 * @author rakow
 */
@CommandLine.Command(
        name = "population",
        description = "Create population with input data",
        showDefaultValues = true
)
public class PreparePopulation implements Callable<Integer> {

    @CommandLine.Option(names = "--population", description = "Input original population file", required = true)
    private Path population;

    @CommandLine.Option(names = "--attributes", description = "Input person attributes file", required = true)
    private Path attributes;

    @CommandLine.Option(names = "--output", description = "Output folder", defaultValue = "scenarios")
    private Path output;

    @Override
    public Integer call() throws Exception {

        // TODO merge population and attributes
        // also create a 1% scenario

        Config config = ConfigUtils.createConfig();

        config.plans().setInputPersonAttributeFile(attributes.toString());
        config.plans().setInputFile(population.toString());

        config.plans().setInsistingOnUsingDeprecatedPersonAttributeFile(true);

        Scenario scenario = ScenarioUtils.loadScenario(config);

        Path input = output.resolve("duesseldorf-25pct/input");
        if (!Files.exists(input))
            Files.createDirectories(input);

        // Clear wrong coordinate system
        scenario.getPopulation().getAttributes().clear();

        scenario.getPopulation().getPersons().forEach( (k,v) -> v.getAttributes().putAttribute("subpopulation", "person"));

        PopulationUtils.writePopulation(scenario.getPopulation(), input.resolve("duesseldorf-25pct.plans.xml.gz").toString());

        // sample 25% to 1%
        PopulationUtils.sampleDown(scenario.getPopulation(), 0.04);

        Path input1pct = output.resolve("duesseldorf-1pct/input");
        if (!Files.exists(input1pct))
            Files.createDirectories(input1pct);

        PopulationUtils.writePopulation(scenario.getPopulation(), input1pct.resolve("duesseldorf-1pct.plans.xml.gz").toString());

        return 0;
    }

    public static void main(String[] args) {
        System.exit(new CommandLine(new PreparePopulation()).execute(args));
    }
}
