package qubase.engine;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Properties;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class Controller {
	ArrayList<Crawler> crawlerList = new ArrayList<Crawler>();
	private Properties props = null;
	private static final Logger logger = Logger.getLogger(Controller.class.getName());
	
	public Controller(Properties props) {
		this.props = props;
	}

	public void run() throws Exception {
		
		//primary application loop
		while (true) {
			logger.finest("Starting primary application loop.");
			//dynamic crawler loading
			loadCrawlers();
			int crawlerCount = crawlerList.size();
			
			//all crawlers are turned off, wait for 5s and retry
			if (crawlerCount == 0) {
				Thread.sleep(5000);
			}
			
			//secondary application loop
			for (int i = 0; i < crawlerCount; i++) {
				logger.finest("Starting secondary application loop: " + i);
				Crawler crawler = crawlerList.get(i);
				
				try {
					crawler.loop(props.getProperty("separator"));
				} catch (Exception e) {
					logger.severe("[" + crawler.getId() + "] Request loop crashed: " + crawler.getName() + " | " + e.getMessage());
				}
			}
		}
	}
	
	private void loadCrawlers() throws Exception {
		String crawlerConfigFile = props.getProperty("crawler-config");
		
		if (crawlerConfigFile == null) {
			throw new Exception("Crawler configuration file not defined.");
		}
		
		crawlerList.clear();
		logger.finest("Loading crawler configuration.");
		//read the XML configuration file
		File crawlerFile = new File(crawlerConfigFile);
		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
		Document doc = dBuilder.parse(crawlerFile);
		doc.getDocumentElement().normalize();
		
		//create all crawler objects
		NodeList crawlerElements = doc.getElementsByTagName("crawler");
		int crawlerCount = crawlerElements.getLength();
		for (int i = 0; i < crawlerCount; i++) {
			Node crawlerNode = crawlerElements.item(i);
			
			NodeList crawlerProperties = crawlerNode.getChildNodes();
			
			Crawler crawler = new Crawler();
			
			try {
				crawler.setErrorLimit(Integer.parseInt(props.getProperty("error-limit")));
			} catch (NumberFormatException e) {
				logger.warning("Failed to set error limit.");
			}
			
			if (crawlerNode.getNodeType() == Node.ELEMENT_NODE) {
				Element crawlerElement = (Element) crawlerNode;
				
				//if this crawler is turned off, don't load it
				String crawlerStatusFlag = crawlerElement.getAttribute("status");
				if (crawlerStatusFlag.equals("0")) {
					continue;
				}
				
				String crawlerIdString = crawlerElement.getAttribute("id");
				if (crawlerIdString == null) {
					throw new Exception("Crawler ID not defined.");
				}
				
				int crawlerId = Integer.parseInt(crawlerIdString);
				crawler.setId(crawlerId);
				
				int childrenCount = crawlerProperties.getLength();
				for (int j = 0; j < childrenCount; j++) {
					Node crawlerPropertyNode = crawlerProperties.item(j);
					//even blank characters between the elements form children
					//e.g. \n or space, this can be skipped
					if (crawlerPropertyNode.getNodeType() != Node.ELEMENT_NODE) {
						continue;
					}
					
					Element crawlerProperty = (Element) crawlerPropertyNode;
					
					String crawlerPropertyName = crawlerProperty.getNodeName();
					
					//determine the property and save it in the object
					if (crawlerPropertyName.equals("name")) {
						crawler.setName(crawlerProperty.getTextContent());
					} else if (crawlerPropertyName.equals("interval")) {
						crawler.setInterval(Integer.parseInt(crawlerProperty.getTextContent()));
					} else if (crawlerPropertyName.equals("weight")) {
						//weight can have random element instead of a value
						Element weight = (Element) crawlerProperty;
						if (weight.getTextContent().matches("[0-9]+")) {
							crawler.setWeight(Integer.parseInt(weight.getTextContent()));
						} else {
							NodeList weightChildren = weight.getChildNodes();
							int weightChildrenCount = weightChildren.getLength();
							for (int k = 0; k < weightChildrenCount; k++) {
								Node weightChild = weightChildren.item(k);
								if (weightChild.getNodeType() == Node.ELEMENT_NODE
										&& weightChild.getNodeName().equals("random")) {
									crawler.setRandom(Integer.parseInt(weightChild.getTextContent()));
								}
							}
						}
					
					} else if (crawlerPropertyName.equals("max-duplicates")) {
						crawler.setMaxDuplicates(Integer.parseInt(crawlerProperty.getTextContent()));
					} else if (crawlerPropertyName.equals("sleep")) {
						crawler.setSleep(Long.parseLong(crawlerProperty.getTextContent()));
					} else if (crawlerPropertyName.equals("url")) {
						crawler.setUrl(new URL(crawlerProperty.getTextContent()));
					} else if (crawlerPropertyName.equals("ttl")) {
						crawler.setTtl(Integer.parseInt(crawlerProperty.getTextContent()));
					}
				}
				
				//if the crawler object was set properly, save it to the list, inform otherwise
				if (crawler.isConfigured()) {
					logger.finest("Adding crawler: " + crawler.getName());
					crawlerList.add(crawler);
				} else {
					//id won't be null, this throws an exception and won't run anyway
					logger.warning("Crawler not configured properly: " + crawler.getId());
				}
			}
		}
		logger.finest("Crawler configuration loaded.");
	}
	
	public static void unpublishCrawler(int id) throws SAXException, IOException, ParserConfigurationException, XPathExpressionException, TransformerException {
		String crawlerConfig = LecturaCrawlerEngine.getProperties().getProperty("crawler-config");
		File crawlerFile = new File(crawlerConfig);
		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
		Document doc = dBuilder.parse(crawlerFile);
		doc.getDocumentElement().normalize();
			
		XPath xPath =  XPathFactory.newInstance().newXPath();
		Element crawler = (Element) xPath.compile("/crawler-config/crawler[@id='" + id + "']").evaluate(doc, XPathConstants.NODE);
		Attr status = crawler.getAttributeNode("status");
		status.setNodeValue("0");
		
		// write the content into xml file
		TransformerFactory transformerFactory = TransformerFactory.newInstance();
		Transformer transformer = transformerFactory.newTransformer();
		transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
		
		DOMSource source = new DOMSource(doc);
		StreamResult result = new StreamResult(new File(crawlerConfig));
		transformer.transform(source, result);
	}
}
