package agents;

import jade.core.Agent;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import trasmapi.sumo.Sumo;

@SuppressWarnings("serial")
public class TrafficLightAgent extends Agent {

	private Sumo sumo;
	private String tlName;

	public TrafficLightAgent(Sumo sumo, String name) throws Exception {
		super();
		this.sumo = sumo;
		this.tlName = "TrafficLight-" + name;
		// tlController = new TLController(this, sumo, name, (ArrayList<String>) neighbours.clone());
	}
	
	public int getSumoCurrentSimStep() {
		return sumo.getCurrentSimStep();
	}
	
	public String getTlName() {
		return tlName;
	}

	@Override
	protected void setup() {
		// Registro DF
		DFAgentDescription dfAgentDescription = new DFAgentDescription();
		ServiceDescription serviceDescription = new ServiceDescription();
		serviceDescription.setType("TrafficLightsAgent");
		serviceDescription.setName(getName());
		dfAgentDescription.setName(getAID());
		dfAgentDescription.addServices(serviceDescription);
		try {
			DFService.register(this, dfAgentDescription);
		} catch (FIPAException e) {
			doDelete();
		}
		// new Thread(tlController).start();
		ChangeTrafficLigthStateBehaviour changeTrafficLigthStateBehaviour = new ChangeTrafficLigthStateBehaviour(this);
		addBehaviour(changeTrafficLigthStateBehaviour);
		super.setup();
	}

	@Override
	protected void takeDown() {
		try {
			DFService.deregister(this);
		} catch (FIPAException e) {
			e.printStackTrace();
		}
		super.takeDown();
	}

}
