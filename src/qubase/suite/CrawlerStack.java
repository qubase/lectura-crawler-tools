package qubase.suite;

import java.io.File;
import java.net.URL;
import java.util.HashMap;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class CrawlerStack {
	
	private static HashMap<String, Crawler> crawlers = new HashMap<String, Crawler>();
	private static Logger logger = Logger.getLogger(CrawlerStack.class.getName());
	
	public static Crawler getCrawler(String portalName) {
		if (crawlers.containsKey(portalName)) {
			return crawlers.get(portalName);
		} else {
			try {
				Crawler crawler = CrawlerFactory.createCrawler(portalName);
				int retry = 0;
				try {
					retry = Integer.parseInt(LecturaCrawlerSuite.getProperties().getProperty("retry-request"));
				} catch (Exception ignore) {
					//ignore
				}
				
				int retryAfter = 0;
				try {
					retryAfter = Integer.parseInt(LecturaCrawlerSuite.getProperties().getProperty("retry-request-after"));
				} catch (Exception ignore) {
					//ignore
				}
				
				crawler.setRetry(retry);
				crawler.setRetryAfter(retryAfter);
				
				try {
						configureCrawler(crawler);
				} catch (Exception e) {
					logger.severe("Couldn't load configuration for crawler: " + portalName);
				}
				
				String logLevel = (crawler.getLogLevel() == null) 
						? LecturaCrawlerSuite.getProperties().getProperty("log-level")
						: crawler.getLogLevel();
						
				Integer ttl = (crawler.getTtl() == null) 
						? Integer.parseInt(LecturaCrawlerSuite.getProperties().getProperty("log-ttl-hours"))
						: crawler.getTtl();
				
				crawler.configureLogger(logLevel, ttl, LecturaCrawlerSuite.getProperties().getProperty("log-debug").equals("1"));
				crawlers.put(portalName, crawler);
				return crawler;
			} catch (IllegalArgumentException e) {
				logger.severe(e.getMessage());
			}
		}
		
		return null;
	}
	
	private static void configureCrawler(Crawler crawler) throws Exception {
		logger.finest("Loading crawler configuration.");
		//read the XML configuration file
		File crawlerFile = new File(LecturaCrawlerSuite.getProperties().getProperty("crawler-config"));
		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
		Document doc = dBuilder.parse(crawlerFile);
		doc.getDocumentElement().normalize();
		
		String portalName = crawler.getName();
		
		XPath xPath =  XPathFactory.newInstance().newXPath();
		String ttl = xPath.compile("/crawler-config/" + portalName + "/force-log-ttl-hours/text()[1]").evaluate(doc);
		String useProxy = xPath.compile("/crawler-config/" + portalName + "/force-use-proxy/text()[1]").evaluate(doc);
		String logLevel = xPath.compile("/crawler-config/" + portalName + "/force-log-level/text()[1]").evaluate(doc);
		
		crawler.setTtl((ttl.isEmpty() || ttl == null) ? null : Integer.parseInt(ttl));
		crawler.setUseProxy((useProxy.isEmpty() || useProxy == null) ? null : useProxy.equals("1"));
		crawler.setLogLevel((logLevel.isEmpty() || logLevel == null) ? null : logLevel);
		
		NodeList blacklist = (NodeList) xPath.compile("/crawler-config/" + portalName + "/blacklist/url").evaluate(doc, XPathConstants.NODESET);
		
		int size = blacklist.getLength();
		for (int i = 0; i < size; i++) {
			Element url = (Element) blacklist.item(i);
			crawler.addToBlacklist(new URL(url.getTextContent().trim()));
		}
	}
}
