package org.matsim.prepare;

import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.opengis.feature.simple.SimpleFeature;

import java.util.Collection;
import java.util.List;

/**
 * Tags persons in the population with certain attributes such as "resident of Stuttgart city"
 *
 * @author davidwedekind
 */

public class TagPopulation {

    private static final Logger log = Logger.getLogger(TagPopulation.class);

    private static String inputPlans = "C:/Users/david/OneDrive/02_Uni/02_Master/05_Masterarbeit/03_MATSim/01_Input/stuttgart-inkl-umland-vsp/population-25pct-stuttgart.xml.gz";
    private static String outputPlans = "C:/Users/david/OneDrive/02_Uni/02_Master/05_Masterarbeit/03_MATSim/01_Input/population-25pct-stuttgart-bearbeitet.xml.gz";
    private static String shapeFile = "C:/Users/david/OneDrive/02_Uni/02_Master/05_Masterarbeit/03_MATSim/00_InputPrep/02_vg250/communityShape.shp";


    public static void main(String[] args) {

        if (args.length > 0) {
            inputPlans = args[0];
            outputPlans = args[1];
            log.info("input plans: " + inputPlans);
            log.info("output plans: " + outputPlans);
        }

        TagPopulation tagger = new TagPopulation();
        tagger.run(inputPlans, outputPlans);

    }


    public void run(String inputPlans, String outputPlans){

        Config config = ConfigUtils.createConfig();
        config.plans().setInputFile(inputPlans);
        Scenario scenario = ScenarioUtils.loadScenario(config);

        // Get Population
        Population population = scenario.getPopulation();

        // Read-In Shape Files which provide tag inputs
        Collection<SimpleFeature> features = ShapeFileReader.getAllFeatures(shapeFile);

        population.getPersons().values().parallelStream()
                .forEach(person -> {

                    // Set homeAgs (Amtlicher Gemeindeschlüssel)

                    String homeAgs = findHomeAgs(person, features);
                    person.getAttributes().putAttribute("homeAgs", homeAgs);

                    //* Add additional taggings here */

                });

        // Write population to output path
        PopulationWriter writer = new PopulationWriter(population);
        writer.write(outputPlans);
    }


    private String findHomeAgs(Person person, Collection<SimpleFeature> features) {

        String homeAgs = "";
        Boolean homeInShape = false;

        Activity homeActivity = (Activity) person.getSelectedPlan().getPlanElements().get(getHomeActivity(person));
        Coord homeCoord = homeActivity.getCoord();
        Point point = MGC.coord2Point(homeCoord);

        // Automatic crs detection/ transformation might be needed to avoid errors.

        for (PlanElement planElement : person.getSelectedPlan().getPlanElements()){

            for (SimpleFeature feature : features ) {
                Geometry geometry = (Geometry) feature.getDefaultGeometry();

                if (geometry.contains(point)) {
                    homeAgs = feature.getAttribute("ags").toString();
                    homeInShape = true;
                }

            }

        }

        if (! homeInShape) {

            // There are several home locations outside the shape File boundaries
            // as for performance reasons the shape File was reduced to Stuttgart Metropolitan Area

            // How to deal with the people that do not live in Stuttgart Metropolitan Area?
            // Will we need more detailed information on their home locations as well?

            log.error("No home community found in shapes for person: " + person.getId().toString());
        }

        return homeAgs;

    }


    private int getHomeActivity(Person person) {

        int homeElement = 0;
        Boolean hasHomeActivity = false;

        List<PlanElement> planElements = person.getSelectedPlan().getPlanElements();

        for (PlanElement planElement : planElements){

            if (planElement instanceof Activity){
                Activity activity = (Activity) planElement;

                if (activity.getType().startsWith("home")){
                    homeElement = planElements.indexOf(planElement);
                    hasHomeActivity = true;
                }

            }

        }

        if (! hasHomeActivity){
            log.error("No home activity found for person: " + person.getId().toString());
        }

        return homeElement;

    }
}
