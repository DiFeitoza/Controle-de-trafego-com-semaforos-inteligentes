package agents;

//import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.AMSService;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.AMSAgentDescription;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.SearchConstraints;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
//import trasmapi.genAPI.Lane;
//import trasmapi.genAPI.LanePosition;
//import trasmapi.genAPI.TrafficLight;
//import trasmapi.genAPI.exceptions.UnimplementedMethod;
//import trasmapi.sumo.ControlledLinks;
//import learning.QLearning;
import trasmapi.sumo.Sumo;
import trasmapi.sumo.SumoE3Detector;
//import trasmapi.sumo.SumoEdge;
import trasmapi.sumo.SumoLane;
import trasmapi.sumo.SumoTrafficLight;
import trasmapi.sumo.SumoTrafficLightProgram;
import trasmapi.sumo.SumoTrafficLightProgram.Phase;
//import utils.Logger;
import utils.E3Detector;

//import java.util.ArrayList;
//import java.util.Arrays;
import java.util.List;

public class TrafficLightAgent extends Agent {

	private String name;
	//Eficiencia do semáforo na liberação da passagem de cada veículo
	//Cada sensor tem uma direção associada, Leste-Oeste, Norte-Sul
	private double[] lsEficienciaLO, lsEficienciaNS;
	//Indice geral da direção em questão, é assumido pelo menor índice em cada direção.
	double indGeralLO, indGeralNS;
	double mediaLO = 0, mediaNS = 0;
	int position, quantity;
	Sumo sumo;

	public TrafficLightAgent(Sumo sumo, String name) throws Exception {
		super();
		this.name = "TrafficLight-" + name;
		this.sumo = sumo;
		this.position = this.quantity = 0;
		lsEficienciaLO = new double[3];
		lsEficienciaNS = new double[3];
//        tlController = new TLController(this, sumo, name, (ArrayList<String>) neighbours.clone());
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
//        new Thread(tlController).start();
		changeTrafficLigthStateBehaviour changeTrafficLigthStateBehaviour = new changeTrafficLigthStateBehaviour(this);
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

	private class changeTrafficLigthStateBehaviour extends CyclicBehaviour {
		
		public changeTrafficLigthStateBehaviour(Agent agente) {
			super(agente);
		}

		@SuppressWarnings("deprecation")
		@Override
		public void action() {
			double efficiencyLO = 0, efficiencyNS = 0, aux = 0;
			
			E3Detector e3Detector = new E3Detector();
			
			if (sumo.getCurrentSimStep() / 1000 > 90 && sumo.getCurrentSimStep() / 1000 % 90 == 0) {
				e3Detector.initialize();

				SumoTrafficLight sumoTrafficLight = new SumoTrafficLight(name.split("-")[1]);
				List<String> e3Detectors = e3Detector.getIds(name.split("-")[1]);
				
				for (String detectorName : e3Detectors) {
					SumoE3Detector sumoE3Detector = new SumoE3Detector(detectorName);
					SumoLane lane = new SumoLane(detectorName.split("_")[2] + "_0");
					double tempoMinimoNaVia = lane.getLength() / lane.getMaxSpeed();
					double meanSpeed = sumoE3Detector.getMenSpeed();
					double meanTime;
					//??? se não houver média, considere o fluxo livre, tempo mínimo na via? 
					meanTime = (meanSpeed == -1) ? tempoMinimoNaVia : (lane.getLength() / meanSpeed);
					aux = meanTime / tempoMinimoNaVia;
					if (detectorName.split("_")[3].equals("lo")) {
						if (efficiencyLO == 0 || efficiencyLO < aux) {
							efficiencyLO = aux;
						}
					} else {
						if (efficiencyNS == 0 || efficiencyNS < aux) {
							efficiencyNS = aux;
						}
					}
				}
				// Calcular Glo e Gns
				indGeralLO = efficiencyLO;
				indGeralNS = efficiencyNS;
				// preencher Ilo e Ins
				lsEficienciaLO[position] = indGeralLO;
				lsEficienciaNS[position] = indGeralNS;
				position = ++position % 3;
				//??? diego:aqui só faz a média 1x?
				if (quantity < 3)
					quantity++;
				// preencher Mlo e Mns
				if (quantity == 3) {
					mediaLO = (lsEficienciaLO[0] + lsEficienciaLO[1] + lsEficienciaLO[2]) / 3;
					mediaNS = (lsEficienciaNS[0] + lsEficienciaNS[1] + lsEficienciaNS[2]) / 3;
				}
				// Alteração de plano semáforico
				//??? diego: modifiquei o maior '>' para que envie mensagens sempre que houver diferença de mais de 10%. linha anterior: if (Math.abs(mediaLO - mediaNS) > 0.1) {
				if (Math.abs(mediaLO - mediaNS) <= 0.1) {
					if (mediaLO > mediaNS)
						sumoTrafficLight.setProgram(createProgram(sumoTrafficLight, 2));
					else
						sumoTrafficLight.setProgram(createProgram(sumoTrafficLight, 1));
				} else if (mediaLO > 0 && mediaNS > 0) {
					ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
					msg.setContent(mediaLO + "-" + mediaNS);
					AMSAgentDescription[] agents = null;
					try {
						SearchConstraints searchConstraints = new SearchConstraints();
						searchConstraints.setMaxResults(new Long(-1));
						agents = AMSService.search(TrafficLightAgent.this, new AMSAgentDescription(), searchConstraints);
					} catch (Exception e) {
						System.out.println("Problem searching AMS: " + e);
						e.printStackTrace();
					}
					for (int i = 0; i < agents.length; i++)
						msg.addReceiver(agents[i].getName());
					send(msg);
					ACLMessage msg1 = receive();
					if (msg1 != null) {
						if (msg1.getPerformative() == ACLMessage.INFORM) {
							String content = msg1.getContent();
							float receivedMediaLO = Float.parseFloat(content.split("-")[0]);
							float receivedMediaNS = Float.parseFloat(content.split("-")[1]);
							if ((mediaLO > receivedMediaLO || mediaNS > receivedMediaNS ) && Math.abs(mediaLO - mediaNS) > Math.abs(receivedMediaLO - receivedMediaNS)) {
								if (mediaLO > mediaNS)
									sumoTrafficLight.setProgram(createProgram(sumoTrafficLight, 2));
								else
									sumoTrafficLight.setProgram(createProgram(sumoTrafficLight, 1));
							}
						}
					}
				}
			}

		}

		public SumoTrafficLightProgram createProgram(SumoTrafficLight SumoTrafficLight, int type) {
			SumoTrafficLightProgram sumoTrafficLightProgram = SumoTrafficLight.getProgram();
			String newState = "";
			//??? por quê são criados novos programas?
			SumoTrafficLightProgram newProgram = new SumoTrafficLightProgram(
					name + (sumo.getCurrentSimStep() / 1000) / 90);
			for (Phase phase : sumoTrafficLightProgram.getPhases()) {
				if (phase.getDuration() > 1) {
					newProgram.addPhase(phase.getDuration() - 1, phase.getState());
					if (type == 1) {
						for (char c : phase.getState().toCharArray()) {
							//??? diego: por porquê o tipo influencia na fase mudar ou não de green?
							if (Character.toLowerCase(c) == 'g')
								newState += 'r';
							else if (Character.toLowerCase(c) == 'r')
								newState += 'r';
							else
								newState += 'y';
						}
					} else {
						for (char s : phase.getState().toCharArray()) {
							if (Character.toLowerCase(s) == 'g')
								newState += 'g';
							else if (Character.toLowerCase(s) == 'r')
								newState += 'g';
							else
								newState += 'y';
						}
					}
				}
				newProgram.addPhase(1, newState);
			}
			return newProgram;
		}
	}
}
