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

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.util.NameValuePair;

// marketbook parser works in a different way, it saves all the refernces
// to listing details already in the first step when parsing the sitemap
// so there is only one sitemap entry and a single huge list saved into the status
public class Marketbook extends Crawler {

	WebClient webClient;
	String baseUrl = "http://www.marketbook.de";
	
	public Marketbook() {
		super();
		name = "marketbook";
		//no unique sitemap entry point, different approach chosen, crawling by manufacturer - manufacturer index for construction and ag equipment on different URLs
		//the provided one is for construction equipment, ag is called separately afterwards
		try {
			siteMapUrl = new URL("http://www.marketbook.de/drilldown/manulist.aspx?lp=MAT");
		} catch (MalformedURLException e) {
			logger.severe("Failed to init siteMapUrl: [http://www.marketbook.de/drilldown/manulist.aspx?lp=MAT] " + e.getMessage());
		}
		statusFile = name + ".status";
	}
	
	@Override
	protected void parseSiteMap(String input) {
		String[] lines = input.split("\\r?\\n");
		String xml = "";
		boolean inList = false;
		
		//load the list
		for (int i = 0; i < lines.length; i++) {
			String line = lines[i].trim();
			
			if (line.matches("</tr>") && inList) {
				xml += line;
				break;
			}
			
			if (line.matches("<tr\\s*id=\"ctl00_ContentPlaceHolder1_DrillDown1_trInformation\">")) {
				inList = true;
			}
			
			if (inList) {
				xml += line;
			}
		}
		
		//process the list using xml/xpath approach
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();  
	    DocumentBuilder builder; 
	    xml = xml.replaceAll("&", "&amp;");
	    try {  
	        builder = factory.newDocumentBuilder();  
	        Document document = builder.parse(new InputSource(new StringReader(xml)));
	        
	        XPath xPath =  XPathFactory.newInstance().newXPath();

	        NodeList links = (NodeList) xPath.compile("//tr/td/a").evaluate(document, XPathConstants.NODESET);
	        
	        int size = links.getLength();	       
			for (int i = 0; i < size; i++) {
				Element link = (Element) links.item(i);
				String name = link.getTextContent();
				String href = link.getAttribute("href").replaceAll("&amp;", "&");
				
				//this goes directly to the list
				if (href.startsWith("/list")) {
					try {
						addToSiteMap(new SiteMapLocation(new URL(baseUrl + href + "&pg=1"), name));
					} catch (MalformedURLException e) {
						logger.severe("Failed to parse URL: " + baseUrl + href + "&pg=1");
					}
				}
				
				//if this is a drilldown
				if (href.startsWith("/drilldown")) {
					int listLength = Integer.parseInt(name.replaceFirst(".*?\\s\\(([0-9]+)\\)", "$1"));
					//if this is a drilldown with less than 10K results, transform the URL to list, otherwise a break down is needed
					//beacause they don't return lists longer than 10K
					if (listLength < 10000) {
						href = href.replaceFirst("drilldown/modellist.aspx", "list/list.aspx"); 
						
						try {
							addToSiteMap(new SiteMapLocation(new URL(baseUrl + href + "&pg=1"), name));
						} catch (MalformedURLException e) {
							logger.severe("Failed to parse URL: " + baseUrl + href + "&pg=1");
						}
					} else {
						
					}
				}
			}
	    } catch (Exception e) {  
	        logger.severe("Couldn't parse site map XML: " + e.getMessage());  
	    }
	}

	@Override
	protected void parseList(String input) {
		String[] lines = input.split("\\r?\\n");
		String regexPager = ".*?<b>Sie sind jetzt auf Seite ([0-9]+) von ([0-9]+)<br\\s*/>";
		for (String lineIn : lines) {
			String line = lineIn.trim();
			
			if (line.matches(".*?href=\"/listingsdetail/detail.aspx.*?\"\\s*id=\"aDetailsLink\">")) {
				String href = line.replaceFirst(".*?href=\"([^\"]+)\".*$", "$1").replaceAll("&amp;", "&");
				
				try {
					URL url = new URL(baseUrl + href);
					if (!status.list.contains(url)) {
						status.list.add(url);
					}
				} catch (MalformedURLException e) { 
					logger.severe("Failed to add URL to the list: [" + baseUrl + href + "] " + e.getMessage());
				}
			}
			
			if (line.matches(regexPager)) {
				String nowAt = line.replaceAll(regexPager, "$1");
				String siteCnt = line.replaceAll(regexPager, "$2");
				if (!nowAt.equals(siteCnt)) {
					status.nextPageAvailable = true;
				}
				break;
			}
			
			if (line.startsWith("<span id=\"ctl00_ContentPlaceHolder1_lblDetailedSearchInfo")) {
				break;
				//no need to iterate further, there's a lot of useless lines after the relevant content is already processed
			}
		}
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
		String url = originalUrl.toString().replaceAll("&pg=[0-9]+", "&pg=" + status.page);
		try {
			return new URL(url);
		} catch (MalformedURLException e) {
			logger.severe("Failed to parse URL: [" + url + "] " + e.getMessage());
			return originalUrl;
		}
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
		
		//load the list if needed
		if (status.list.isEmpty() && !status.siteMap.isEmpty()) {
			String coordinates = "[" + status.siteMapIndex + ", " + status.page + ", " + status.pagePosition + "]";
			URL listUrl = modifyUrl(status.siteMap.get(status.siteMapIndex).url);
			logger.finest("Loading list: " + listUrl + " " + coordinates);
			
			try {
				loadPageFromHtmlUnit(listUrl, listParser);
			} catch (Exception e) {
				response = "Failed to load the list: [" + status.siteMap.get(status.siteMapIndex).url + "] " + e.getMessage();
				logger.severe(response);
				return error + response;
			} finally {
				//if after loading the page list is still empty even after second attempt, we should probably try next category, we are out of range of the pager 
				//this can happen when loading an older status and the page structure changed in the meantime
				if (status.list.isEmpty()) {
					response = "Failed to load list - Empty: " + listUrl + " " + coordinates;
					logger.warning(response);
					if (firstEmptyList) {
						//this is a first empty list, give it one more chance and go to the next page
						status.nextPage().save(statusFile);
						firstEmptyList = false;
					} else {
						//this is the second time in a row we've received an empty list, go to the next category this time
						status.nextCategory().save(statusFile);
						firstEmptyList = true;
					}
					return error + response;
				}
			}
		}
		
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
		boolean _useProxy = (useProxy == null) 
				? LecturaCrawlerSuite.getProperties().getProperty("use-proxy").equals("1")
				: useProxy;
		
		if (webClient == null) {
			//the webClient should be a global object not to have one for every parser which needs it
			//also the logging should be turned of globally
			java.util.logging.Logger.getLogger("com.gargoylesoftware.htmlunit").setLevel(java.util.logging.Level.OFF);
		    java.util.logging.Logger.getLogger("org.apache.http").setLevel(java.util.logging.Level.OFF);

		    if (_useProxy) {
		    	webClient = new WebClient(BrowserVersion.CHROME, 
		    			LecturaCrawlerSuite.getProperties().getProperty("proxy-host"),
	    				Integer.parseInt(LecturaCrawlerSuite.getProperties().getProperty("proxy-port"))
		    			);
		    } else {
		    	webClient = new WebClient(BrowserVersion.CHROME);
		    }
		    
		    webClient.getOptions().setThrowExceptionOnScriptError(false); 
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
				if (_useProxy) {
					LecturaCrawlerSuite.waitForTorPolipo();
				}
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
			page.cleanUp();
		} else {
			logger.severe("Failed to load page: [" + url + "]");
		}
		webClient.closeAllWindows();
	}
}
