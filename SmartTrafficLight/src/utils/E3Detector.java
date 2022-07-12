package utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

public class E3Detector {
	 File fileE3AddXML;
	 FileInputStream fileInputStreamE3AddXML;
	 DocumentBuilderFactory docBuilderFactory;
	 DocumentBuilder docBuilder;
	 Document doc;
	
	public  boolean initialize() {
		try {
			fileE3AddXML = new File("network/e3.add.xml");
			fileInputStreamE3AddXML = new FileInputStream(fileE3AddXML);
		}catch (Exception e) {
			// TODO: handle exception
			System.out.println("Erro de leitura: " + fileE3AddXML);
		}
		docBuilderFactory = DocumentBuilderFactory.newInstance();
		try {
			docBuilder = docBuilderFactory.newDocumentBuilder();
			doc = docBuilder.parse(fileInputStreamE3AddXML);
			return true;
		} catch (ParserConfigurationException e) {
			// TODO Auto-generated catch block
//			e.printStackTrace();
			return false;
		} catch (SAXException e) {
			// TODO Auto-generated catch block
//			e.printStackTrace();
			return false;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			return false;
//			e.printStackTrace();
		}
	}
	
	public  List<String> getIds(String id) {
		List<String> ids = new ArrayList<>();

//		Lista com todos os dados gerados pelos detectores
		NodeList detectors = doc.getElementsByTagName("e3Detector");
		
		for (int i = 0; i < detectors.getLength(); i++) {
			Node detectorNode = detectors.item(i);
//			Verifica se o elemento � valido		
			if (detectorNode.getNodeType() == Node.ELEMENT_NODE) {
				Element element = (Element) detectorNode;
					if(element.getAttribute("id").split("_")[1].equals(id))
						ids.add(element.getAttribute("id"));
			}
		}
		return ids;
	}
}
