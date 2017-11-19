package abmt17.preparation;

import java.util.Iterator;

import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;

public class RemoveOutsideAgents {
	public void run(Population population) {
		Iterator<? extends Person> iterator = population.getPersons().values().iterator();
		
		while (iterator.hasNext()) {
			Person person = iterator.next();
			
			if ((Boolean) population.getPersonAttributes().getAttribute(person.getId().toString(), "has_outside_activity")) {
				iterator.remove();
				population.getPersonAttributes().removeAllAttributes(person.getId().toString());
			}
		}
	}
	
	static public void main(String[] args) {
		
	}
}
