package org.matsim.run;

import com.google.common.collect.Lists;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.ControlerConfigGroup;
import org.matsim.core.config.groups.GlobalConfigGroup;
import org.matsim.core.config.groups.QSimConfigGroup;
import org.matsim.core.controler.Controler;
import org.matsim.core.scenario.ScenarioUtils;
import picocli.AutoComplete;
import picocli.CommandLine;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.Callable;

/**
 * A helper class to execute MATSim scenarios. This class provides a common scenario setup procedure and command line parsing.
 * Scenarios simply need to extend it and overwrite the *prepare methods if needed.
 * <p>
 * To run your application use:
 * <code>
 * MATSimApplication.run(RunScenario.class, args);
 * </code>
 */
@CommandLine.Command(
        name = MATSimApplication.DEFAULT_NAME,
        description = {"", "Run the MATSim scenario"},
        headerHeading = MATSimApplication.HEADER,
        parameterListHeading = "%n@|bold,underline Parameters:|@%n",
        optionListHeading = "%n@|bold,underline Options:|@%n",
        commandListHeading = "%n@|bold,underline Commands:|@%n",
        footerHeading = "\n",
        footer = "@|cyan If you would like to contribute or report an issue please go to https://github.com/matsim-org.|@",
        usageHelpWidth = 120,
        usageHelpAutoWidth = true,
        showDefaultValues = true,
        mixinStandardHelpOptions = true,
        subcommands = AutoComplete.GenerateCompletion.class
)
public class MATSimApplication implements Callable<Integer>, CommandLine.IDefaultValueProvider {

    public static final String DEFAULT_NAME = "MATSimApplication";
    public static final String COLOR = "@|bold,fg(81) ";
    public static final String HEADER = COLOR +
            "  __  __   _ _____ ___ _       \n" +
            " |  \\/  | /_\\_   _/ __(_)_ __  \n" +
            " | |\\/| |/ _ \\| | \\__ \\ | '  \\ \n" +
            " |_|  |_/_/ \\_\\_| |___/_|_|_|_|\n|@";

    @CommandLine.Parameters(arity = "1", paramLabel = "CONFIG", description = "Scenario config used for the run.")
    protected File scenario;

    /**
     * Path to the default scenario config, if applicable.
     */
    @Nullable
    protected final String defaultScenario;

    /**
     * Constructor for an application without a default scenario path.
     */
    public MATSimApplication() {
        defaultScenario = null;
    }

    /**
     * Constructor
     *
     * @param defaultScenario path to the default scenario config
     */
    public MATSimApplication(@Nullable String defaultScenario) {
        this.defaultScenario = defaultScenario;
    }

    /**
     * The main scenario setup procedure.
     *
     * @return return code
     */
    @Override
    public Integer call() throws Exception {

        Config config = loadConfig(scenario.getAbsolutePath());

        Objects.requireNonNull(config);

        final Scenario scenario = ScenarioUtils.createScenario(config);

        prepareScenario(scenario);

        final Controler controler = new Controler(scenario);

        prepareControler(controler);

        controler.run();
        return 0;
    }


    /**
     * Custom module configs that will be added to the {@link Config} object.
     *
     * @return {@link ConfigGroup} to add
     */
    @Nonnull
    protected List<ConfigGroup> getCustomModules() {
        return Lists.newArrayList();
    }

    /**
     * Modules that are configurable via command line arguments.
     */
    private List<ConfigGroup> getConfigurableModules() {
        return Lists.newArrayList(
                new ControlerConfigGroup(),
                new GlobalConfigGroup(),
                new QSimConfigGroup()
        );
    }

    /**
     * Preparation step for the config.
     *
     * @param config initialized config
     * @return prepared {@link Config}
     */
    @Nonnull
    protected Config prepareConfig(Config config) {
        return config;
    }

    /**
     * Preparation step for the scenario.
     */
    protected void prepareScenario(@Nonnull Scenario scenario) {
    }

    /**
     * Preparation step for the controller.
     */
    protected void prepareControler(@Nonnull Controler controler) {
    }


    private Config loadConfig(String path) {
        List<ConfigGroup> customModules = getCustomModules();

        final Config config = ConfigUtils.loadConfig(path, customModules.toArray(new ConfigGroup[0]));

        return prepareConfig(config);
    }

    @Override
    public String defaultValue(CommandLine.Model.ArgSpec argSpec) throws Exception {
        Object obj = argSpec.userObject();
        if (obj instanceof Field) {
            Field field = (Field) obj;
            if (field.getName().equals("scenario") && field.getDeclaringClass().equals(MATSimApplication.class)) {
                return defaultScenario;
            }
        }

        return null;
    }

    public static void run(Class<? extends MATSimApplication> clazz, String[] args) {
        MATSimApplication app;
        try {
            app = clazz.getConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            System.err.println("Could not instantiate the application class");
            e.printStackTrace();
            System.exit(1);
            return;
        }

        CommandLine cli = new CommandLine(app);

        if (cli.getCommandName().equals(DEFAULT_NAME))
            cli.setCommandName(clazz.getSimpleName());

        setupOptions(cli, app);

        List<ConfigGroup> modules = Lists.newArrayList();
        modules.addAll(app.getConfigurableModules());
        modules.addAll(app.getCustomModules());

        // setupConfig(cli, modules);

        System.exit(cli.execute(args));
    }

    private static void setupOptions(CommandLine cli, MATSimApplication app) {

        CommandLine.Model.CommandSpec spec = cli.getCommandSpec();
        String[] header = spec.usageMessage().header();
        // set formatting for header
        if (header.length == 1) {
            spec.usageMessage().header(COLOR + " " + header[0].trim() + "|@%n");
        }

        spec.defaultValueProvider(app);
    }

    /**
     * Inserts modules config into command line, but not used at the moment
     */
    private static void setupConfig(CommandLine cli, List<ConfigGroup> modules) {

        CommandLine.Model.CommandSpec spec = cli.getCommandSpec();
        for (ConfigGroup module : modules) {

            CommandLine.Model.ArgGroupSpec.Builder group = CommandLine.Model.ArgGroupSpec.builder()
                    .headingKey(module.getName())
                    .heading(module.getName() + "\n");

            for (Map.Entry<String, String> param : module.getParams().entrySet()) {

                // Escape format symbols
                String desc = module.getComments().get(param.getKey());
                if (desc != null)
                    desc = desc.replace("%", "%%");

                group.addArg(CommandLine.Model.OptionSpec.builder("--" + module.getName() + "-" + param.getKey())
                        .hideParamSyntax(true)
                        .hidden(false)
                        .description((desc != null ? desc + " " : "") + "Default: ${DEFAULT-VALUE}")
                        .defaultValue(param.getValue())
                        .build());

            }

            spec.addArgGroup(group.build());
        }
    }
}
