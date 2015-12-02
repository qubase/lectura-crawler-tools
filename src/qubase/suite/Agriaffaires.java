package qubase.suite;

import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

public class Agriaffaires extends Crawler {
	
	private HashMap<String, String> priceMap = new HashMap<String, String>();
	
	public Agriaffaires() {
		super();
		name = "agriaffaires";
		
		try {
			siteMapUrl = new URL("http://www.agriaffaires.de");
		} catch (MalformedURLException e) {
			logger.severe("Failed to init siteMapUrl: [http://www.agriaffaires.de/] " + e.getMessage());
		}
		statusFile = name + ".status";
	}

	@Override
	protected void parseSiteMap(String input) {
		String[] lines = input.split("\\r?\\n");
		ArrayList<SiteMapLocation> cats = new ArrayList<SiteMapLocation>();
		
		String regexPreHref = "<li\\s*class=\"li3\">";
		String regexHref = "<a\\s*href=\"(/gebrauchte/.*?)\">";
		String regexPostHref = "(.+?)<span>\\([0-9,\\.]+\\)</span>";
		
		String regexPreHref2 = "<div\\s*class=\"title-ul3\">";
		String regexPostHref2 = "([^<>]+)";
		
		boolean inHref = false;
		boolean inHref2 = false;
		
		String leafUrlStr = null;
		String urlStr = null;
		for (String lineIn : lines) {
			String line = lineIn.trim();
			
			if (line.equals("<div class=\"floatleft\"><h2>Forstmaschinen</h2>")) {
				// the rest of the categories is parsed already at machineryzone.eu, break here
				break;
			}
			
			if (inHref && line.matches(regexPostHref)) {
				inHref = false;
				try {
					URL leafUrl = new URL(siteMapUrl + leafUrlStr);
					if (blacklist.contains(leafUrl)) {
						continue;
					}
					blacklist.add(leafUrl);
					String name = line.replaceFirst(regexPostHref, "$1");
					SiteMapLocation sm = new SiteMapLocation(leafUrl, name);
					cats.add(sm);
				} catch (MalformedURLException e) {
					logger.severe("Failed to parse site map: " + e.getMessage());
				}
			}
			
			if (inHref) {
				String href = line.replaceFirst(regexHref, "$1");
				if (href != null && !href.isEmpty()) {
					leafUrlStr = line.replaceFirst(regexHref, "$1");
				}
			}
			
			if (line.matches(regexPreHref)) {
				inHref = true;
			}
			
			// non-leaf nodes
			if (inHref2 && line.matches(regexPostHref2)) {
				inHref2 = false;
				try {
					URL url = new URL(siteMapUrl + urlStr);
					if (blacklist.contains(url)) {
						continue;
					}
					blacklist.add(url);
					String name = line.replaceFirst(regexPostHref2, "$1");
					SiteMapLocation sm = new SiteMapLocation(url, name);
					cats.add(sm);
				} catch (MalformedURLException e) {
					logger.severe("Failed to parse site map: " + e.getMessage());
				}
			}
			
			if (inHref2) {
				String href = line.replaceFirst(regexHref, "$1");
				if (href != null && !href.isEmpty()) {
					urlStr = line.replaceFirst(regexHref, "$1");
				}
			}
			
			if (line.matches(regexPreHref2)) {
				inHref2 = true;
			}
		}
		
		for (SiteMapLocation sm : cats) {
			confirmAndSaveSitemapLink(sm);
		}
	}
	
	private void confirmAndSaveSitemapLink(SiteMapLocation sm) {
		String html = loadCustomPage(sm.url);
		
		//check if this is a list
		if (html.indexOf("<div class=\"liste-simple\">") > 0) {
			addToSiteMap(sm);
			return;
		}
	}

	@Override
	protected void parseList(String input) {
		String[] lines = input.split("\\r?\\n");
		
		String regexItemStart = "<div\\s*class=\"(liste-simple|liste-avant)\">";
		String regexItemHref = "<a\\s*href=\"(/gebrauchte/.*?)\">";
		
		boolean inItem = false;
		String lastLink = null;
		
		for (String lineIn : lines) {
			String line = lineIn.trim();
			
			if (line.matches(regexItemHref) && inItem) {
				inItem = false;
				String link = siteMapUrl + line.replaceFirst(regexItemHref, "$1");
				try {
					URL url = new URL(link);
					status.list.add(url);
					lastLink = link;
				} catch (MalformedURLException e) {
					logger.severe("Failed to parse URL: " + siteMapUrl + line.replaceFirst(regexItemHref, "$1"));
				}
			}
			
			if (line.matches(regexItemStart)) {
				inItem = true;
			}
			
			if (line.indexOf("<span class=\"glyphicons arrowright\"></span>") > 0) {
				status.nextPageAvailable = true;
			}
			
			if (line.startsWith("<span class=\"js-price\" data-value=") && lastLink != null) {
				priceMap.put(lastLink, line.replaceFirst(".+?>(.+?)<.*", "$1"));
			}
		}
	}

	@Override
	protected void parseListing(String input) {
		currentListing = new Listing();
		
		String url = null;
		try {
			url = status.list.get(status.pagePosition).toString();
			String price = priceMap.get(url);
			if (price != null) {
				currentListing.setPrice(price.replaceAll("[^0-9]", ""));
				currentListing.setCurrency("EUR");
			}
			currentListing.setUrl(url);
			currentListing.setCategory(status.siteMap.get(status.siteMapIndex).name);
			currentListing.setCatLang("EN");		
		} catch (Exception e) {
			//ignore, this is a test call
		}
		
		String[] lines = input.split("\\r?\\n");
		
		boolean inCompany = false;
		
		boolean inDetail = false;
		String detail = "";
		for (int i = 0; i < lines.length; i++) {
			
			String line = lines[i].trim();
			
			if (line.matches("\\s*</table>\\s*")) {
				detail += line;
				inDetail = false;
			}
			
			if (inDetail) {
				detail += line.replaceAll("&", "&amp;") + "\r\n";
			}
			
			if (line.matches("\\s*<div\\s*class=\"bloc-annonce\">\\s*")) {
				inDetail = true;
			}
			
			if (inCompany && line.matches("[^<]+")) {
				currentListing.setCompany(line);
				inCompany = false;
			}
			
			if (line.equals("<h3 itemprop=\"name\" class=\"p14 bold linkunderline margin10b\">")) {
				inCompany = true;
			}
			
			if (line.startsWith("<p itemprop=\"address\"")) {
				String zip = line.replaceFirst(".*<span\\s*itemprop=\"postalCode\">([^<]+)</span>.*", "$1");
				String region = line.replaceFirst(".*<span\\s*itemprop=\"addressLocality\">([^<]+)</span>.*", "$1");
				String country = line.replaceFirst(".*?-\\s*([^<]+)</p>", "$1");
				
				currentListing.setZip(zip);
				currentListing.setRegion(region);
				currentListing.setCountry(country);
			}
		}
		
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();  
	    DocumentBuilder builder;
	    try {  
	        builder = factory.newDocumentBuilder();  
	        Document document = builder.parse(new InputSource(new StringReader(detail.replaceAll("<br>", "<br />"))));
	        
	        XPath xPath =  XPathFactory.newInstance().newXPath();

	        NodeList links = (NodeList) xPath.compile("//*/text()").evaluate(document, XPathConstants.NODESET);
	        
	        int size = links.getLength();
	        
	        boolean inMake = false;
	        boolean inModel = false;
	        boolean inYear = false;
	        boolean inHrs = false;
	        boolean inDate = false;
	        boolean inMileage = false;
	        boolean inSerial = false;
	        
			for (int i = 0; i < size; i++) {
				String item = links.item(i).getTextContent().trim();
				
				if (item.isEmpty()) {
					continue;
				}
				
				if (inMake) {
					inMake = false;
					currentListing.setManName(notAvailableValue(item));
				}
				
				if (inModel) {
					inModel = false;
					currentListing.setModelName(notAvailableValue(item));
				}
				
				if (inYear) {
					inYear = false;
					currentListing.setYear(notAvailableValue(item));
				}
				
				if (inSerial) {
					inSerial = false;
					currentListing.setSerial(notAvailableValue(item));
				}
				
				if (inHrs || inMileage) {
					inHrs = false;
					inMileage = false;
					String val = notAvailableValue(item);
					if (val != null) {
						currentListing.setCounter(val.replaceAll("[^0-9]", ""));
					}
				}
				
				if (inDate) {
					inDate = false;
					int style = DateFormat.MEDIUM;
					SimpleDateFormat sdf = (SimpleDateFormat) DateFormat.getDateInstance(style, Locale.US);
					sdf.applyPattern("MM.dd.yyyy");
					Date date = null;
					String dateIn = item;
					try {
						date = sdf.parse(dateIn);
					} catch (Exception e) {
						//couldn't parse the date use now()
						logger.warning("Couldn't parse the date: " + dateIn + " | " + currentListing.getUrl() + " | " + e.getMessage());
						date = new Date();
					}
					
					sdf.applyPattern("yyyy-MM-dd");
					String dateOut = sdf.format(date);
					
					currentListing.setDate(dateOut);
				}
				
				if (item.equals("Marke :")) {
					inMake = true;
				} else if (item.equals("Modell :")) {
					inModel = true;
				} else if (item.equals("Jahr :")) {
					inYear = true;
				} else if (item.equals("Stunden :")) {
					inHrs = true;
				} else if (item.equals("Datum der Anmeldung :")) {
					inDate = true;
				} else if (item.startsWith("Serial") || item.startsWith("Serien") || item.startsWith("Fahrgestellnummer")) {
					inSerial = true;
				}
			}
	    } catch (Exception e) {  
	        logger.severe(e.getMessage());  
	    }
	    
	    System.out.println("DONE");
    }

	@Override
	protected URL modifyUrl(URL originalUrl) {
		String url = originalUrl.toString().replaceAll("/gebrauchte/[0-9]+/", "/gebrauchte/" + status.page + "/");
		try {
			return new URL(url);
		} catch (MalformedURLException e) {
			logger.severe("Failed to parse URL: [" + url + "] " + e.getMessage());
			return originalUrl;
		}
	}
	
	private String notAvailableValue(String val) {
		return val.trim().equals("N/A") ? null : val;
	}
}
