package org.matsim;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import java.io.File;
import java.io.FileFilter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author janekdererste
 * @author davidwedekind
 */

public class Utils {

    private static final Logger logger = LogManager.getLogger("UtilsLogger");


    // Copied from https://github.com/matsim-vsp/mosaik-2/blob/master/src/main/java/org/matsim/mosaik2/Utils.java
    public static List<PlanCalcScoreConfigGroup.ActivityParams> createTypicalDurations(String type, long minDurationInSeconds, long maxDurationInSeconds, long durationDifferenceInSeconds) {

        List<PlanCalcScoreConfigGroup.ActivityParams> result = new ArrayList<>();
        for (long duration = minDurationInSeconds; duration <= maxDurationInSeconds; duration += durationDifferenceInSeconds) {
            final PlanCalcScoreConfigGroup.ActivityParams params = new PlanCalcScoreConfigGroup.ActivityParams(type + "_" + duration + ".0");
            params.setTypicalDuration(duration);
            result.add(params);
        }
        return result;
    }


    public static String setRunOutputDirectory(String outputBasePath){

        Date date = new Date();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd");
        String pathString = outputBasePath + formatter.format(date) + "/";
        File file = new File(pathString);

        String subPathString;

        if (Files.notExists(Path.of(pathString))) {

            boolean bool = file.mkdir();

            if (bool){
                logger.info("New output directory created: " + pathString);
            }else{
                logger.debug("No output directory created although needed.");
            }

            subPathString = pathString + "01/";

        }else{

            File[] files = file.listFiles(new FileFilter() {
                @Override
                public boolean accept(File f) {
                    return f.isDirectory();
                }
            });

            String subFolderNameString = String.format("%02d", files.length + 1);
            subPathString = pathString + subFolderNameString + "/";

        }

        File subFile = new File(subPathString);
        boolean bool = subFile.mkdir();

        if (bool){
            logger.info("New output sub-directory for current run created: " + subPathString);
        }else{
            logger.debug("No output sub-directory for current run created although needed.");
        }


        return subPathString;

    }

}