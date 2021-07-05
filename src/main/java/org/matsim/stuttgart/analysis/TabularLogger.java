package org.matsim.stuttgart.analysis;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.util.List;
import java.util.stream.Collectors;

public class TabularLogger implements TabularWriter {

    private static final Logger log = LogManager.getLogger(TabularLogger.class);

    private final String[] header;

    public TabularLogger(String[] header) {
        this.header = header;
    }

    @Override
    public void write(List<List<Object>> values) {

        log.info(String.join(", ", header));

        for (var row : values) {

            // assuming three columns
            var rowString = row.stream()
                    .map(Object::toString)
                    .collect(Collectors.joining(", "));

            log.info(rowString);
        }
    }
}
