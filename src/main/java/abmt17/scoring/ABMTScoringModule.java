package abmt17.scoring;

import org.matsim.core.controler.AbstractModule;
import org.matsim.core.scoring.ScoringFunctionFactory;

public class ABMTScoringModule extends AbstractModule {
	@Override
	public void install() {
		bind(ScoringFunctionFactory.class).to(ABMTScoringFunctionFactory.class);
	}
}
