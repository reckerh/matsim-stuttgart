package org.matsim.stuttgart.prepare;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.stuttgart.Utils;
import picocli.CommandLine;

import java.nio.file.Path;
import java.nio.file.Paths;

public class CheckPopulation {

    private static final Logger log = LogManager.getLogger(CheckPopulation.class);
    private static final String inputPopulation = "projects\\matsim-stuttgart\\stuttgart-v0.0-snz-original\\optimizedPopulation.xml.gz";
    private static final String shapeFilePath = "projects\\matsim-stuttgart\\stuttgart-v0.0-snz-original\\stuttgart_umland_5677.shp";


    public static void main(String... args) {

        if (args.length != 1) throw new IllegalArgumentException("please provide path to shared svn");

        check(Paths.get(args[0]));
    }

    static void check(Path svn) {

        var args = new String[] {
                svn.resolve(inputPopulation).toString(),
                "--shp", svn.resolve(shapeFilePath).toString(),
                "--input-crs", "EPSG:25832"
        };

        new CommandLine(new org.matsim.application.analysis.CheckPopulation()).execute(args);
    }
}
