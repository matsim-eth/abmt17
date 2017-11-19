package abmt17.pt;

import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.controler.AbstractModule;

import com.google.inject.Provides;
import com.google.inject.Singleton;

public class ABMTPTModule extends AbstractModule {
	final static public double CROWFLY_DISTANCE_FACTOR = 1.9;
	final static public double[] SPEED_REGRESSION_PARAMETERS = new double[] { 3.70784209e+00, 1.15246526e+00,
			-1.21147828e-02, 1.54252014e-04 };

	@Override
	public void install() {
		addRoutingModuleBinding(TransportMode.pt).to(ABMTPTRoutingModule.class);
	}

	@Provides
	@Singleton
	public ABMTPTRoutingModule provideABMTPTRoutingModule(Population population) {
		return new ABMTPTRoutingModule(population.getFactory(), CROWFLY_DISTANCE_FACTOR, SPEED_REGRESSION_PARAMETERS);
	}
}
