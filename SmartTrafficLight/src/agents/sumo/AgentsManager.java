package agents.sumo;

import java.util.ArrayList;

import agents.TrafficLightAgent;
import jade.wrapper.ContainerController;
import jade.wrapper.ControllerException;
import trasmapi.sumo.Sumo;
import trasmapi.sumo.SumoTrafficLight;

public class AgentsManager {
    ArrayList<TrafficLightAgent> agents = new ArrayList<>();

    public AgentsManager(Sumo sumo, ContainerController mainContainer) {
        ArrayList<String> sumoTrafficLightIds = SumoTrafficLight.getIdList();
        
        for (String sumoTlId : sumoTrafficLightIds) {           
            try {
            	TrafficLightAgent agent = new TrafficLightAgent(sumo, sumoTlId);
                agents.add(agent);
                mainContainer.acceptNewAgent("TrafficLight-" + sumoTlId, agent);
            }
            catch (Exception e) {
                e.printStackTrace();
                System.exit(1);
            }
        }
    }

    public void startupAgents(ContainerController mainContainer) {
        try {
            for (TrafficLightAgent agent : agents) {
                mainContainer.getAgent(agent.getLocalName()).start();
            }
        } catch (ControllerException e) {
            e.printStackTrace();
        }
    }
}
