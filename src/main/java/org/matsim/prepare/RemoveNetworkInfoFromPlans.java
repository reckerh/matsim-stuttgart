/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2015 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package org.matsim.prepare;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.api.core.v01.population.PopulationWriter;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.router.MainModeIdentifier;
import org.matsim.core.router.MainModeIdentifierImpl;
import org.matsim.core.router.RoutingModeMainModeIdentifier;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.router.TripStructureUtils.Trip;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;

/**
* @author ikaddoura
*/

class RemoveNetworkInfoFromPlans {
	
	private static final Logger log = Logger.getLogger(RemoveNetworkInfoFromPlans.class);

	private static String inputPlans = "../shared-svn/projects/matsim-stuttgart/stuttgart-v0.0-snz-original/optimizedPopulation.xml.gz";
	private static String outputPlans = "../shared-svn/projects/matsim-stuttgart/stuttgart-v1.0/input/optimizedPopulation_withoutNetworkInfo.xml.gz";

	private static String coordSystem = "EPSG:25832";

	public static void main(String[] args) {
		
		if (args.length > 0) {
			inputPlans = args[0];
			outputPlans = args[1];		
			log.info("input plans: " + inputPlans);
			log.info("output plans: " + outputPlans);
		}
		
		RemoveNetworkInfoFromPlans filter = new RemoveNetworkInfoFromPlans();
		filter.run(inputPlans, outputPlans);
	}
	
	private void run (final String inputPlans, final String outputPlans) {
		
		Scenario scOutput;
		
		Config config = ConfigUtils.createConfig();
		config.plans().setInputFile(inputPlans);
		config.global().setCoordinateSystem(coordSystem);
		Scenario scInput = ScenarioUtils.loadScenario(config);
		
		scOutput = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		Population popOutput = scOutput.getPopulation();
		
		for (Person p : scInput.getPopulation().getPersons().values()){
			Plan selectedPlan = p.getSelectedPlan();
			PopulationFactory factory = popOutput.getFactory();
			Person personNew = factory.createPerson(p.getId());
			
			for (String attribute : p.getAttributes().getAsMap().keySet()) {
				personNew.getAttributes().putAttribute(attribute, p.getAttributes().getAttribute(attribute));
			}
			
			Plan plan = factory.createPlan();
			
			CoordinateTransformation trans = TransformationFactory.getCoordinateTransformation("EPSG:25832", "EPSG:25832");
			
			Activity firstAct = (Activity) selectedPlan.getPlanElements().get(0);
			firstAct.setLinkId(null);
			firstAct.setCoord(trans.transform(firstAct.getCoord()));
			plan.addActivity(firstAct);
			
			for (Trip trip : TripStructureUtils.getTrips(selectedPlan)) {		
				MainModeIdentifier modeIdentifier = new MainModeIdentifierImpl();
				String mode = modeIdentifier.identifyMainMode(trip.getTripElements());			
				plan.addLeg(factory.createLeg(mode));
				Activity act = trip.getDestinationActivity();
				act.setLinkId(null);
				act.setCoord(trans.transform(act.getCoord()));
				plan.addActivity(act );
			}
												
			popOutput.addPerson(personNew);
			personNew.addPlan(plan);
		}
		
		log.info("Writing population...");
		new PopulationWriter(scOutput.getPopulation()).write(outputPlans);
		log.info("Writing population... Done.");
	}

}

