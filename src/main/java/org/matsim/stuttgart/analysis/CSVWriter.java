package org.matsim.stuttgart.analysis;

import org.apache.commons.csv.CSVFormat;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class CSVWriter implements TabularWriter {

    private static final Logger log = LogManager.getLogger(CSVWriter.class);

    private final Path filename;
    private final String[] header;

    public CSVWriter(Path filename, String[] header) {

        this.filename = filename;
        this.header = header;
    }

    @Override
    public void write(List<List<Object>> values) {

        try(var writer = Files.newBufferedWriter(filename); var printer = CSVFormat.DEFAULT.withHeader(header).print(writer)) {

            for (var row : values) {
                printer.printRecord(row);
            }

        } catch (IOException e) {
            log.error("Failed to write to CSV: " + filename);
            log.error(e.getMessage());
            e.printStackTrace();
        }
    }
}
