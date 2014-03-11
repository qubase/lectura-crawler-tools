package qubase.suite;

import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.apache.http.HttpStatus;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.util.NameValuePair;

// marketbook parser works in a different way, it saves all the refernces
// to listing details already in the first step when parsing the sitemap
// so there is only one sitemap entry and a single huge list saved into the status
public class Marketbook extends Crawler {

	WebClient webClient;
	
	public Marketbook() {
		super();
		name = "marketbook";
		
		try {
			siteMapUrl = new URL("http://www.marketbook.de/sitemap.xml");
		} catch (MalformedURLException e) {
			logger.severe("Failed to init siteMapUrl: [http://www.marketbook.de/sitemap.xml] " + e.getMessage());
		}
		statusFile = name + ".status";
	}
	
	@Override
	protected void parseSiteMap(String input) {
		String[] lines = input.split("\\r?\\n");
		String regexLink = "^\\s*<loc>(.+?)</loc>\\s*$";
		for (String lineIn : lines) {
			String line = lineIn.trim();
			if (line.matches(regexLink)) {
				String link = line.replaceAll(regexLink, "$1");
				parseSiteMapCustom(link);
			}
		}
		status.siteMap.add(new SiteMapLocation(siteMapUrl, "Sitemap.xml"));
	}
	
	private void parseSiteMapCustom(String link) {
		
		URL url = null;
		try {
			url = new URL(link);
		} catch (MalformedURLException e) {
			logger.warning("Failed to parse custom site map URL: " + link);
			return;
		}
		
		String input = loadCustomPage(url);
		String[] lines = input.split("\\r?\\n");
		String regexLink = "^\\s*<loc>(.+?)</loc>\\s*$";
		for (String lineIn : lines) {
			String line = lineIn.trim();
			if (line.matches(regexLink)) {
				String listLink = line.replaceAll(regexLink, "$1");
				if (listLink.matches(".*?http://www.marketbook.ca/listingsdetail/detail.aspx.+$")) {
					try {
						String finalLink = listLink
								.replaceAll("www\\.marketbook\\.ca", "www.marketbook.de")
								.replaceAll("&amp;", "&")
								.replaceAll("(.*?)lp=([A-Za-z]+)&ohid=([0-9]+)", "$1OHID=$3&LP=$2")
								.trim();
						status.list.add(new URL(finalLink));
					} catch (MalformedURLException e) {
						logger.severe("Failed to parse URL: " + listLink);
					}
				}
			}
		}
	}

	@Override
	protected void parseList(String input) {
		// no need to implement, all of this happens already in the first step - sitemap capturing
		// where instead of finding the sitemap links, listings links are captured directly
	}

	@Override
	protected void parseListing(String input) {
		currentListing = new Listing();
		
		String url = null;
		try {
			url = status.list.get(status.pagePosition).toString();
			currentListing.setUrl(url);
		} catch (Exception e) {
			//ignore, this is a test call
		}
		
		currentListing.setDate(new SimpleDateFormat("yyyy-MM-dd").format(new Date()));
		
		try {
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			Document doc = dBuilder.parse(new InputSource(new StringReader(input)));
			doc.getDocumentElement().normalize();
			
			XPath xPath =  XPathFactory.newInstance().newXPath();
			String title = xPath.compile("/html/head/title/text()[1]").evaluate(doc).trim();
			String listingTitle = xPath.compile("//h1[@id='hListingTitle']/text()[1]").evaluate(doc).trim();
			String category = title.substring(listingTitle.length() + 1).replaceAll(" zum Verkauf.*$", "").trim();
			currentListing.setCategory(category);
			currentListing.setCatLang("DE");
			
			String price = xPath.compile("//span[@id='listingpricevalue']/text()[1]").evaluate(doc).trim();
			String currency = "EUR";
			if (!price.equals("Auf Anfrage")) {
				currentListing.setPrice(price);
				currentListing.setCurrency(currency);
			}
			
			Element table = (Element) xPath.compile("//table[@id='listing-detail'][1]").evaluate(doc, XPathConstants.NODE);
			NodeList tableRows = table.getElementsByTagName("tr");
			int size = tableRows.getLength();
			for (int i = 0; i < size; i++) {
				Element e = (Element) tableRows.item(i);
				String key = e.getElementsByTagName("th").item(0).getTextContent().trim();
				String value = e.getElementsByTagName("td").item(0).getTextContent().trim();
				
				if (key.equals("Jahr")) {
					currentListing.setYear(value);
				}
				
				if (key.equals("Hersteller")) {
					currentListing.setManName(value);
				}
				
				if (key.equals("Typ")) {
					currentListing.setModelName(value);
				}
				
				if (key.equals("Serien Nummer")) {
					currentListing.setSerial(value);
				}
				
				if (key.equals("Ort")) {
					String[] regionCrumbs = value.split(", ");
					String region = null;
					String country = null;
					if (regionCrumbs.length > 1) {
						region = "";
						for (int j = 0; j < regionCrumbs.length - 1; j++) {
							region += regionCrumbs[j] + ", ";
						}
						region = region.replaceAll(", $", "");
						
						country = regionCrumbs[regionCrumbs.length - 1];
					} else {
						region = value;
						country = value;
					}
					
					currentListing.setRegion(region);
					currentListing.setCountry(country);
				}
				
				if (key.equals("Betriebsstunden")) {
					currentListing.setCounter(value);
				}
				
				if (key.equals("Zustand des Produkts")) {
					currentListing.setNewMachine(value);
				}
			}
			
		} catch (Exception e) {
			logger.warning("Failed to parse listing: " + url);
		}
	}

	@Override
	protected URL modifyUrl(URL originalUrl) {
		return originalUrl;
	}
	
	@Override
	public String getItem() throws Exception {
		logger.finest("Item request initiated.");
		
		String response = null;
		String error = "error=";
		
		if (!status.init) {
			if (!status.load(statusFile)) {
				status.init = true;
			} //else the init was loaded from the serialized status
		}
		
		
		//clear the sitemap and reset if we are at the end of portal
		//if the last listing couldn't be loaded, the status was not reset
		if (!status.siteMap.isEmpty()) {
			if (status.siteMap.size() == status.siteMapIndex) {
				status.reset().save(statusFile);
			}
		}
		
		//load the sitemap if needed
		if (status.siteMap.isEmpty()) {
			try {
				logger.finest("Loading sitemap: " + siteMapUrl);
				loadPage(siteMapUrl, siteMapParser);
				
				if (status.siteMap.isEmpty()) {
					response = "Failed to load the sitemap - Empty";
					logger.severe(response);
					return error + response;
				} else {
					status.save(statusFile);
					int size = status.siteMap.size();
					logger.info("Sitemap loaded successfuly: " + size + " items");
					
					for (int i = 0; i < size; i++) {
						SiteMapLocation sml = status.siteMap.get(i);
						logger.finest("Sitemap item #" + i + " : [" + sml.name + "] " + sml.url);
					}
				}
				
			} catch (Exception e) {
				response = "Failed to load the sitemap: " + e.getMessage();
				logger.severe(response);
				return error + response;
			}
		}
		
		//no list load part
		
		//load the listing
		if (!status.list.isEmpty()) {
			boolean isError = false;
			try {
				String coordinates = "[" + status.siteMapIndex + ", " + status.page + ", " + status.pagePosition + "]";
				URL listingUrl = status.list.get(status.pagePosition);
				logger.finest("Loading listing: " + listingUrl + " " + coordinates);
				loadPageFromHtmlUnit(listingUrl, listingParser);
				if (currentListing == null) {
					response = "Listing null: " + status.list.get(status.pagePosition);
					logger.severe(response);
					isError = true;
				} else {
					//this is where it comes when everything went right
					response = currentListing.toString();
				}
			} catch (Exception e) {
				response = "Failed to load the listing: " + status.list.get(status.pagePosition);
				logger.severe(response);
				isError = true;
			} finally {
				if (status.pagePosition == status.list.size() - 1) {
					if (status.nextPageAvailable) {
						status.nextPage().save(statusFile);
						status.list.clear();
					} else {
						status.nextCategory().save(statusFile);
						status.list.clear();
						if (status.siteMap.size() == status.siteMapIndex) {
							status.reset().save(statusFile);
						}
					}
				} else {
					status.pagePosition++;
				}
			}
			
			return (isError) ? error + response : response;
		} else {
			response = "List empty, can not load listing.";
			logger.severe(response);
			return error + response;
		}
	}
	
	private void loadPageFromHtmlUnit(URL url, Parser parser) {
		if (webClient == null) {
			//the webClient should be a global object not to have one for every parser which needs it
			//also the logging should be turned of globally
			java.util.logging.Logger.getLogger("com.gargoylesoftware.htmlunit").setLevel(java.util.logging.Level.OFF);
		    java.util.logging.Logger.getLogger("org.apache.http").setLevel(java.util.logging.Level.OFF);
			webClient = new WebClient();
		}
		
		HtmlPage page = null;
		int attempts = 0;
		int retry_ = (retry == 0) ? 1 : retry;
		boolean responseOk = false;
		while (attempts < retry_ && !responseOk) {
			
			if (attempts > 0) {
				try {
					logger.finest("Going to sleep for " + retryAfter + "ms " + url);
					Thread.sleep(retryAfter);
				} catch (InterruptedException e) {
					//ignore
					logger.severe("Failed to sleep for " + retryAfter + "ms " + url);
				}
				logger.info("Retrying to get page after " + retryAfter + "ms : Attempt " + new Integer(attempts + 1) + "/" + retry + " " + url);
			}
			
			try {	
				page = webClient.getPage(url);
			} catch (Exception e) {
				logger.severe("Failed to retrieve page from htmlunit: " + e.getMessage());
			}
			
			responseOk = (page != null && page.getWebResponse().getStatusCode() == HttpStatus.SC_OK);
			attempts++;
		}
		
		if (page != null) {
			if (page.getWebResponse().getStatusCode() != HttpStatus.SC_OK) {
            	List<NameValuePair> headers = page.getWebResponse().getResponseHeaders();
            	String headersStr = page.getWebResponse().getStatusMessage();
		        for (int i = 0; i < headers.size(); i++) {
		            headersStr += " | " + headers.get(i);
		        }
		        
		        logger.warning("Status code not OK: [" + url + "] " + headersStr);
		        logger.warning(page.asXml());
            }
			
			parser.parse(page.asXml());
		} else {
			logger.severe("Failed to load page: [" + url + "]");
		}
	}
}
