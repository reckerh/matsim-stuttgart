package org.matsim.prepare;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.events.Event;
import org.matsim.api.core.v01.events.LinkEnterEvent;
import org.matsim.api.core.v01.events.LinkLeaveEvent;
import org.matsim.api.core.v01.events.handler.LinkEnterEventHandler;
import org.matsim.api.core.v01.events.handler.LinkLeaveEventHandler;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.api.experimental.events.LaneEnterEvent;
import org.matsim.core.api.experimental.events.LaneLeaveEvent;
import org.matsim.core.api.experimental.events.handler.LaneEnterEventHandler;
import org.matsim.core.api.experimental.events.handler.LaneLeaveEventHandler;
import org.matsim.core.events.EventsManagerImpl;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.algorithms.EventWriterXML;
import org.matsim.core.utils.io.IOUtils;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Executable class to extract events.
 */
@CommandLine.Command(
        name = "extractEvents",
        description = "Extract events for a certain network area.",
        showDefaultValues = true
)
public class ExtractEvents implements Callable<Integer>, LinkEnterEventHandler, LinkLeaveEventHandler, LaneEnterEventHandler, LaneLeaveEventHandler {

    private static final Logger log = LogManager.getLogger(ExtractEvents.class);

    @CommandLine.Parameters(arity = "1", paramLabel = "INPUT", description = "Input event file")
    private Path input;

    @CommandLine.Option(names = "--network", description = "Path to the SUMO network to extract events for", required = true)
    private Path network;

    @CommandLine.Option(names = "--output", description = "Path to output file", required = true)
    private Path output;

    private SumoNetworkHandler sumo;
    private List<Event> events;

    public static void main(String[] args) {
        System.exit(new CommandLine(new ExtractEvents()).execute(args));
    }

    @Override
    public Integer call() throws Exception {

        sumo = SumoNetworkHandler.read(network.toFile());
        events = new ArrayList<>();

        EventsManager manager =  new EventsManagerImpl();
        manager.addHandler(this);
        manager.initProcessing();
        EventsUtils.readEvents(manager, input.toString());
        manager.finishProcessing();


        log.info("Filtered {} events", events.size());

        manager = new EventsManagerImpl();

        if (output.getParent() != null)
            Files.createDirectories(output.getParent());

        EventWriterXML writer = new EventWriterXML(IOUtils.getOutputStream(IOUtils.getFileUrl(output.toString()), false));
        manager.addHandler(writer);

        events.forEach(manager::processEvent);

        writer.closeFile();

        return 0;
    }

    @Override
    public void handleEvent(LinkEnterEvent event) {
        if (sumo.edges.containsKey(event.getLinkId().toString()))
            events.add(event);
    }

    @Override
    public void handleEvent(LinkLeaveEvent event) {
        if (sumo.edges.containsKey(event.getLinkId().toString()))
            events.add(event);
    }

    @Override
    public void handleEvent(LaneEnterEvent event) {
        if (sumo.edges.containsKey(event.getLinkId().toString()) || sumo.lanes.containsKey(event.getLaneId().toString()))
            events.add(event);
    }

    @Override
    public void handleEvent(LaneLeaveEvent event) {
        if (sumo.edges.containsKey(event.getLinkId().toString()) || sumo.lanes.containsKey(event.getLaneId().toString()))
            events.add(event);
    }
}
