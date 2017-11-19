package abmt17.pt;

import java.util.Collections;
import java.util.List;

import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.api.core.v01.population.Route;
import org.matsim.core.router.EmptyStageActivityTypes;
import org.matsim.core.router.RoutingModule;
import org.matsim.core.router.StageActivityTypes;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.facilities.Facility;

public class ABMTPTRoutingModule implements RoutingModule {
	final private PopulationFactory populationFactory;
	final private double crowflyDistanceFactor;
	final private double[] speedRegressionParameters;

	public ABMTPTRoutingModule(PopulationFactory populationFactory, double crowflyDistanceFactor, double[] speedRegressionParameters) {
		this.populationFactory = populationFactory;
		this.crowflyDistanceFactor = crowflyDistanceFactor;
		this.speedRegressionParameters = speedRegressionParameters;
	}
	
	private double calculateSpeed(double routedDistance) {
		double speed = 0.0;
		
		for (int i = 0; i < speedRegressionParameters.length; i++) {
			speed += speedRegressionParameters[i] * Math.pow(routedDistance * 1e-3, i);
		}
		
		return speed * 1000.0 / 3600.0;
	}

	public List<? extends PlanElement> calcRoute(Facility<?> fromFacility, Facility<?> toFacility, double departureTime,
			Person person) {
		Leg leg = populationFactory.createLeg(TransportMode.pt);
		Route route = populationFactory.getRouteFactories().createRoute(Route.class, fromFacility.getLinkId(), toFacility.getLinkId());
		
		double crowflyDistance = CoordUtils.calcEuclideanDistance(fromFacility.getCoord(), toFacility.getCoord());
		double routedDistance = crowflyDistance * crowflyDistanceFactor;
		double routedTravelTime = routedDistance / calculateSpeed(routedDistance);
		
		route.setDistance(routedDistance);
		route.setTravelTime(routedTravelTime);
		
		leg.setDepartureTime(departureTime);
		leg.setTravelTime(routedTravelTime);
		leg.setRoute(route);
		
		return Collections.singletonList(leg);
	}

	public StageActivityTypes getStageActivityTypes() {
		return EmptyStageActivityTypes.INSTANCE;
	}
}
