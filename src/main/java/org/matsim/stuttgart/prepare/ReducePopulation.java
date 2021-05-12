package org.matsim.stuttgart.prepare;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.core.population.io.StreamingPopulationReader;
import org.matsim.core.population.io.StreamingPopulationWriter;
import org.matsim.core.scenario.ScenarioUtils;

import java.nio.file.Path;
import java.util.Random;

public class ReducePopulation {
    private static final Logger log = Logger.getLogger(ReducePopulation.class);

    private static final Random random = new Random();
    private static final String inputPopulation = "projects\\matsim-stuttgart\\stuttgart-v2.0\\input\\optimizedPopulationWithFreight.xml.gz";
    private static final String outputPopulation = "projects\\matsim-stuttgart\\stuttgart-v2.0\\input\\population-%dpct-stuttgart.xml.gz";


    public static void main(String[] args) {

        Input input = new Input();
        JCommander.newBuilder().addObject(input).build().parse(args);

        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        StreamingPopulationReader reader = new StreamingPopulationReader(scenario);
        StreamingPopulationWriter writer = new StreamingPopulationWriter(input.fraction);

        reader.addAlgorithm(writer);

        try {
            writer.startStreaming(input.outputFile);
            reader.readFile(input.populationFile);
        } finally {
            writer.closeStreaming();
        }
    }

    public static void createDownsamples(Path sharedSvn){
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());

        log.info("loading population");
        new PopulationReader(scenario).readFile(sharedSvn.resolve(inputPopulation).toString());

        log.info("write reduced populations");
        new PopulationWriter(scenario.getPopulation()).write(sharedSvn.resolve(String.format(outputPopulation, 25)).toString());
        writeReducedPopulation(scenario, 0.4, sharedSvn.resolve(String.format(outputPopulation, 10)).toString());
        writeReducedPopulation(scenario, 0.04, sharedSvn.resolve(String.format(outputPopulation, 1)).toString());
        writeReducedPopulation(scenario, 0.004, sharedSvn.resolve(String.format(outputPopulation, 0)).toString());

    }

    private static void writeReducedPopulation(Scenario scenario, double fractionOfOriginal, String outputPath) {
        log.info(String.format("write population as fraction of original: %f", fractionOfOriginal));

        var reducedPopulation = PopulationUtils.createPopulation(ConfigUtils.createConfig());
        for (Person person : scenario.getPopulation().getPersons().values()) {

            if (random.nextDouble() <= fractionOfOriginal) {
                reducedPopulation.addPerson(person);
            }
        }

        new PopulationWriter(reducedPopulation).write(outputPath);

    }

    private static class Input {

        @Parameter(names = "-populationFile")
        private String populationFile;

        @Parameter(names = "-outputFile")
        private String outputFile;

        @Parameter(names = "-fraction")
        private double fraction = 0.4; // create a 10% sample by default
    }
}
