package agents;
import java.util.List;

import jade.core.behaviours.CyclicBehaviour;
import jade.domain.AMSService;
import jade.domain.FIPAAgentManagement.AMSAgentDescription;
import jade.domain.FIPAAgentManagement.SearchConstraints;
import jade.lang.acl.ACLMessage;
import trasmapi.sumo.SumoE3Detector;
import trasmapi.sumo.SumoLane;
import trasmapi.sumo.SumoTrafficLight;
import trasmapi.sumo.SumoTrafficLightProgram;
import trasmapi.sumo.SumoTrafficLightProgram.Phase;
import utils.E3Detector;

@SuppressWarnings("serial")
public class ChangeTrafficLigthStateBehaviour extends CyclicBehaviour {

	private TrafficLightAgent trafficLightAgent;	
	//Eficiencia do semáforo na liberação da passagem de cada veículo
	//Cada sensor tem uma direção associada, Leste-Oeste, Norte-Sul
	private double[] lsEficienciaLO, lsEficienciaNS;
	//Indice geral da direção em questão, é assumido pelo menor índice em cada direção.
	double indGeralLO, indGeralNS;
	double mediaLO = 0, mediaNS = 0;
	int position, quantity;
		
	public ChangeTrafficLigthStateBehaviour(TrafficLightAgent trafficLightAgent) {
		super(trafficLightAgent);
		this.trafficLightAgent = trafficLightAgent;
		this.position = this.quantity = 0;
		lsEficienciaLO = new double[3];
		lsEficienciaNS = new double[3];
	}

	@Override
	public void action() {
		double efficiencyLO = 0, efficiencyNS = 0, aux = 0;
		
		E3Detector e3Detector = new E3Detector();
		int sumoCurrentSimStep = trafficLightAgent.getSumoCurrentSimStep();
		
		if (sumoCurrentSimStep / 1000 > 90 && sumoCurrentSimStep / 1000 % 90 == 0) {
			e3Detector.initialize();

			SumoTrafficLight sumoTrafficLight = new SumoTrafficLight(trafficLightAgent.getTlName().split("-")[1]);
			List<String> e3DetectorsNames = e3Detector.getIds(trafficLightAgent.getTlName().split("-")[1]);
			
			for (String e3detectorName : e3DetectorsNames) {
				SumoE3Detector sumoE3Detector = new SumoE3Detector(e3detectorName);
				SumoLane lane = new SumoLane(e3detectorName.split("_")[2] + "_0");
				double tempoMinimoNaVia = lane.getLength() / lane.getMaxSpeed();
				// diego: A velocidade média dos veículos na faixa específica
				double meanSpeed = sumoE3Detector.getMenSpeed();
				double meanTime;
				//??? se não houver média, considere o fluxo livre, tempo mínimo na via? 
				meanTime = (meanSpeed == -1) ? tempoMinimoNaVia : (lane.getLength() / meanSpeed);
				aux = meanTime / tempoMinimoNaVia;
				if (e3detectorName.split("_")[3].equals("lo")) {
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
			//??? diego: por quê o índice geral se ele não é alterado? 
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
			if (Math.abs(mediaLO - mediaNS) > 0.1) {
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
					searchConstraints.setMaxResults(Long.valueOf(-1));
					agents = AMSService.search(trafficLightAgent, new AMSAgentDescription(), searchConstraints);
				} catch (Exception e) {
					System.out.println("Problem searching AMS: " + e);
					e.printStackTrace();
				}
				for (int i = 0; i < agents.length; i++)
					msg.addReceiver(agents[i].getName());
				trafficLightAgent.send(msg);
				ACLMessage msg1 = trafficLightAgent.receive();
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
		SumoTrafficLightProgram newProgram = new SumoTrafficLightProgram(
				trafficLightAgent.getTlName() + (trafficLightAgent.getSumoCurrentSimStep() / 1000) / 90);
		for (Phase phase : sumoTrafficLightProgram.getPhases()) {
			if (phase.getDuration() > 1) {
				newProgram.addPhase(phase.getDuration() - 1, phase.getState());
				if (type == 1) {
					for (char c : phase.getState().toCharArray()) {
						//??? diego: por quê alterna apenas entre 'r' e 'y' ou 'g e 'y'?
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