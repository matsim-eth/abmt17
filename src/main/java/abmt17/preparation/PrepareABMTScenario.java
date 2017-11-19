package abmt17.preparation;

import java.util.Arrays;
import java.util.List;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.ConfigWriter;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ModeParams;
import org.matsim.core.config.groups.PlansCalcRouteConfigGroup.ModeRoutingParams;
import org.matsim.core.config.groups.StrategyConfigGroup.StrategySettings;
import org.matsim.core.network.io.NetworkWriter;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.core.router.LinkWrapperFacility;
import org.matsim.core.router.MainModeIdentifier;
import org.matsim.core.router.MainModeIdentifierImpl;
import org.matsim.core.router.RoutingModule;
import org.matsim.core.router.StageActivityTypes;
import org.matsim.core.router.StageActivityTypesImpl;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.facilities.FacilitiesWriter;
import org.matsim.pt.PtConstants;
import org.matsim.utils.objectattributes.ObjectAttributesXmlWriter;

import abmt17.pt.ABMTPTModule;
import abmt17.pt.ABMTPTRoutingModule;

/**
 * This runner takes the latest IVT ASTRA scenario and converts it into a state
 * where it can be used in the ABMT course:
 * <ul>
 * <li>Decouple the ASTRA scenario from R5</li>
 * <li>Decouple the ASTRA scenario from fb's mode choice model</li>
 * </ul>
 */
public class PrepareABMTScenario {
	final static private double PT_SPEED = 40.0 * 1000.0 / 3600.0;
	final static private double PT_CROWFLY_FACTOR = 1.9;

	static public void main(String[] args) {
		String configPath = args[0]; // Should be ASTRA config input

		// Remove unused config groups
		Config config = ConfigUtils.loadConfig(configPath);
		Arrays.asList("r5", "astra", "JDEQSim", "changeMode", "counts", "households", "ptCounts",
				"scenario", "transit", "transitRouter", "vspExperimental").forEach(m -> config.removeModule(m));

		// Adjust PT teleportation
		ModeRoutingParams ptConfig = config.plansCalcRoute().getModeRoutingParams().get(TransportMode.pt);
		ptConfig.setTeleportedModeFreespeedFactor(null);
		ptConfig.setTeleportedModeSpeed(PT_SPEED);
		ptConfig.setBeelineDistanceFactor(PT_CROWFLY_FACTOR);

		// (Load Scenario)
		Scenario scenario = ScenarioUtils.loadScenario(config);

		{ // Collapse and reroute public transport legs
			Network network = scenario.getNetwork();
			Population population = scenario.getPopulation();

			StageActivityTypes stageActivityTypes = new StageActivityTypesImpl(PtConstants.TRANSIT_ACTIVITY_TYPE);
			MainModeIdentifier mainModeIdentifier = new MainModeIdentifierImpl();

			RoutingModule ptRouter = new ABMTPTRoutingModule(scenario.getPopulation().getFactory(),
					ABMTPTModule.CROWFLY_DISTANCE_FACTOR, ABMTPTModule.SPEED_REGRESSION_PARAMETERS);

			for (Person person : population.getPersons().values()) {
				boolean hasOutsideActivity = false;

				for (Plan plan : person.getPlans()) {
					List<PlanElement> elements = plan.getPlanElements();

					for (TripStructureUtils.Trip trip : TripStructureUtils.getTrips(elements, stageActivityTypes)) {
						String mainMode = mainModeIdentifier.identifyMainMode(trip.getTripElements());

						if (mainMode.equals(TransportMode.pt)) {
							int index = elements.indexOf(trip.getTripElements().get(0));
							elements.removeAll(trip.getTripElements());

							elements.addAll(index,
									ptRouter.calcRoute(
											new LinkWrapperFacility(
													network.getLinks().get(trip.getOriginActivity().getLinkId())),
											new LinkWrapperFacility(
													network.getLinks().get(trip.getDestinationActivity().getLinkId())),
											trip.getOriginActivity().getEndTime(), person));
						}

						if (trip.getOriginActivity().getType().contains("outside")
								|| trip.getDestinationActivity().getType().contains("outside")) {
							hasOutsideActivity = true;
						}
					}
				}

				population.getPersonAttributes().putAttribute(person.getId().toString(), "has_outside_activity",
						hasOutsideActivity);
			}
		}

		// Adjust paths
		config.plans().setInputFile("abmt_population.xml.gz");
		config.plans().setInputPersonAttributeFile("abmt_population_attributes.xml.gz");
		config.facilities().setInputFile("abmt_facilities.xml.gz");
		config.network().setInputFile("abmt_network.xml.gz");

		// Further config adjustments
		config.linkStats().setWriteLinkStatsInterval(0);
		config.subtourModeChoice().setConsiderCarAvailability(true);
		config.planCalcScore().getModes().remove("outside");
		config.plansCalcRoute().removeModeRoutingParams("outside");
		config.qsim().setFlowCapFactor(0.012);
		
		// Disable activity scoring
		
		config.planCalcScore().setPerforming_utils_hr(0.0); 
		config.planCalcScore().setLateArrival_utils_hr(0.0);
		config.planCalcScore().setEarlyDeparture_utils_hr(0.0);

		// Choice model fb (adjusted)
		config.planCalcScore().setMarginalUtilityOfMoney(0.1541236618);
		
		ModeParams carParams = config.planCalcScore().getOrCreateModeParams("car");
		carParams.setConstant(0.0);
		carParams.setMarginalUtilityOfTraveling(-0.1334710911 * 60.0);
		carParams.setMonetaryDistanceRate(-0.269 / 1000.0);

		ModeParams ptParams = config.planCalcScore().getOrCreateModeParams("pt");
		ptParams.setConstant(1.27152021);
		ptParams.setMarginalUtilityOfTraveling(-0.1029546061 * 60.0);
		ptParams.setMonetaryDistanceRate(-0.57 / 1000.0);

		ModeParams bikeParams = config.planCalcScore().getOrCreateModeParams("bike");
		bikeParams.setConstant(1.2422367142 - 1.0); // modified
		bikeParams.setMarginalUtilityOfTraveling((-0.1400984086 - 0.05) * 60.0); // modified
		bikeParams.setMonetaryDistanceRate(0.0);

		ModeParams walkParams = config.planCalcScore().getOrCreateModeParams("walk");
		walkParams.setConstant(1.7724221109 - 0.6); // modified
		walkParams.setMarginalUtilityOfTraveling((-0.1135891388 - 0.02) * 60.0); // modified
		walkParams.setMonetaryDistanceRate(0.0);

		// Add mode choice

		for (StrategySettings strategy : config.strategy().getStrategySettings()) {
			if (strategy.getStrategyName().equals("ChangeExpBeta")) {
				strategy.setWeight(0.8);
			}
		}

		StrategySettings smcStrategy = new StrategySettings();
		smcStrategy.setWeight(0.1);
		smcStrategy.setStrategyName("SubtourModeChoice");
		config.strategy().addStrategySettings(smcStrategy);

		// Remove outside agents
		new RemoveOutsideAgents().run(scenario.getPopulation());

		// Write scenario
		new PopulationWriter(scenario.getPopulation()).write("abmt_population.xml.gz");
		new ObjectAttributesXmlWriter(scenario.getPopulation().getPersonAttributes())
				.writeFile("abmt_population_attributes.xml.gz");
		new FacilitiesWriter(scenario.getActivityFacilities()).write("abmt_facilities.xml.gz");
		new NetworkWriter(scenario.getNetwork()).write("abmt_network.xml.gz");
		new ConfigWriter(config).write("abmt_config.xml");
	}
}
