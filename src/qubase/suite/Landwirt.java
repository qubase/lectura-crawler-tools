package qubase.suite;

import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

public class Landwirt extends Crawler {
	
	private String baseUrl = null;
	private String categorySpecialUrlPath = null;
	
	public Landwirt() {
		super();
		name = "landwirt";
		
		try {
			siteMapUrl = new URL("http://www.landwirt.com/gebrauchte/allekategorien.php");
		} catch (MalformedURLException e) {
			logger.severe("Failed to init siteMapUrl: [http://www.landwirt.com/gebrauchte/allekategorien.php] " + e.getMessage());
		}
		statusFile = name + ".status";
		baseUrl = "http://www.landwirt.com";
	}

	@Override
	protected void parseSiteMap(String input) {
		String[] lines = input.split("\\r?\\n");
		String categoryList = "";
		boolean inList = false;
		for (int i = 0; i < lines.length; i++) {
			if (lines[i].matches("\\s*<div id=\"gmmkatlistung\">\\s*")) {
				inList = true;
			}
			
			if (lines[i].matches("\\s*<div\\s*id=\"bannertopseite\"\\s*class=\"nodruck\">\\s*")) {
				break;
			}
			
			if (inList) {
				categoryList += lines[i].replaceAll("&", "&amp;") + "\r\n";
			}
		}
		
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();  
	    DocumentBuilder builder;  
	    try {  
	        builder = factory.newDocumentBuilder();  
	        Document document = builder.parse(new InputSource(new StringReader(categoryList)));
	        
	        XPath xPath =  XPathFactory.newInstance().newXPath();

	        NodeList links = (NodeList) xPath.compile("//li/a").evaluate(document, XPathConstants.NODESET);
	        
	        int size = links.getLength();
			for (int i = 0; i < size; i++) {
				Element link = (Element) links.item(i);
				String name = link.getTextContent();
				URL url = new URL(baseUrl + "/gebrauchte/" + link.getAttribute("href"));
				
				if (!blacklist.contains(url)) {
					status.siteMap.add(new SiteMapLocation(url, name));
				}
			}
	    } catch (Exception e) {  
	        logger.severe("Couldn't parse site map XML: " + e.getMessage());  
	    } 
	}

	@Override
	protected void parseList(String input) {
		String[] lines = input.split("\\r?\\n");
		String regexLink = ".*?<a\\s*href=\"(/gebrauchte,[0-9]+,.*?\\.html)\">.*$";
		for (int i = 0; i < lines.length; i++) {
			if (lines[i].matches(regexLink)) {
				String link = lines[i].replaceAll(regexLink, "$1");
				try {
					URL url = new URL(baseUrl + link);
					if (!status.list.contains(url)) {
						status.list.add(url);
					}
				} catch (MalformedURLException e) { 
					logger.severe("Failed to add URL to the list: [" + baseUrl + link + "] " + e.getMessage());
				}
			}
			
			if (lines[i].matches("\\s*document\\.getElementById\\(\"navajax1\"\\)\\.innerHTML\\s*=\\s*'.*?<a\\s*href=\"[^\"]+\">[0-9]+</a>\\s*';\\s*$")) {
				status.nextPageAvailable = true;
			}
		}
	}

	@Override
	protected void parseListing(String input) {
		currentListing = new Listing();
		
		try {
			currentListing.setUrl(status.list.get(status.pagePosition).toString());
			currentListing.setCategory(status.siteMap.get(status.siteMapIndex).name.replaceAll("\\[[0-9]+\\]", "").trim());
			currentListing.setCatLang("DE");
		} catch (Exception e) {
			//ignore, this is a test call
		}
		
		currentListing.setDate(new SimpleDateFormat("yyyy-MM-dd").format(new Date()));
		
		String[] lines = input.split("\\r?\\n");
		
		boolean inHI = false;
		boolean inAddress = false;
		boolean inTitle = false;
		boolean inTel = false;
		boolean inCell = false;
		
		String title = null;
		
		String regexTitleStart = "<a\\s*href=\"[^\"]+\"><h1>.*";
		String regexTitleEnd = ".*</h1></a>";
		String regexType = ".*<strong>Type:</strong>\\s*(.*?)\\s*$";
		String regexOpHrs = ".*<strong>Betriebsstunden:</strong>\\s*(.*?)\\s*$";
		String regexYear = ".*<strong>Baujahr:</strong>\\s*(.*?)\\s*$";
		String regexPriceCurrencyVAT = ".*<strong>Preis:\\s*([A-Z]{1,4})\\s*([\\.0-9]+),--</strong>.*$";
		String regexInAddress = ".*\\s*<address>\\s*.*";
		String regexZipRegion = ".*<br\\s*/>\\s*([- A-Z0-9]+)\\s+-\\s+(.*?)<.*$";
		String regexInTel = ".*<span\\s*class=\"telefonnummerdetail\\s*telefonicon\">.*";
		String regexInCell = ".*<span\\s*class=\"telefonnummerdetail\\s*handyicon\">.*";
		String regexTelCell = ".*</span>\\s*(\\+[0-9]+)\\s.*";
		
		String regexNew = "<img\\s*src=\"/gebrauchte/cssjs/2.gif\"\\s*class=\"floatLeft\"\\s*alt=\"\"\\s*/>";//1 - new
		String regexShow = "<img\\s*src=\"/gebrauchte/cssjs/1.gif\"\\s*class=\"floatLeft\"\\s*alt=\"\"\\s*/>";//2 - show
		
		boolean zipRegionDone = false;
		boolean statusDone = false;
		
		for (String lineIn : lines) {
			String line = lineIn.trim();
			
			if (line.matches("<div\\s*id=\"headerinnen\">")) {
				inHI = true;
			}
			
			if (inTitle && !line.matches(regexTitleEnd)) {
				if (title != null) {
					title += line;
				}
			}
			
			if (line.matches(regexTitleEnd) && inHI) {
				if (title != null) {
					title += line;
				} else {
					title = line;
				}
				inTitle = false;
				inHI = false;
				
				title = title.replaceAll("<a\\s*href=\"[^\"]+\"><h1>(.*?)</h1></a>", "$1");
			}
			
			if (line.matches(regexTitleStart) && inHI) {
				title = line;
				inTitle = true;
			}
			
			//model name + manufacturer
			if (line.matches(regexType)) {
				String modelName = line.replaceAll(regexType, "$1");
				if (!modelName.isEmpty() && !title.isEmpty()) {
					currentListing.setModelName(modelName);
					currentListing.setManName(title.replace(modelName, "").trim());
				}
			}
			
			//op hrs
			if (line.matches(regexOpHrs)) {
				String opHrs = line.replaceAll(regexOpHrs, "$1");
				if (!opHrs.isEmpty()) {
					currentListing.setCounter(opHrs);
				}
			}
			
			//year
			if (line.matches(regexYear)) {
				String year = line.replaceAll(regexYear, "$1");
				
				if (!year.isEmpty()) {
					currentListing.setYear(year);
				}
				
				//if 99, 12, 1 or similar idiotic year, try to resolve it
				if (year.length() < 3) {
					if (year.length() == 1) {
						year = "0" + year;
					}
					
					try {
						DateFormat sdfp = new SimpleDateFormat("yy");
						Date y = sdfp.parse(year);
						DateFormat sdff = new SimpleDateFormat("yyyy");
						String yearNew = sdff.format(y);
						currentListing.setYear(yearNew);
					} catch (Exception e) {
						//ignore, original year will be used
					}
				}
			}
			
			//price
			if (line.matches(regexPriceCurrencyVAT)) {
				String price = line.replaceFirst(regexPriceCurrencyVAT, "$2");
				String currency = line.replaceFirst(regexPriceCurrencyVAT, "$1");
				String text = null;
				if (line.matches(".*<i>[^<]+</i>.*")) {
					text = line.replaceFirst(".*<i>([^<]+)</i>.*", "$1");
				}
				
				if (text != null && text.startsWith("inkl")) {
					//if the VAT is included, need to recalculate
					if (price != null) {
						String percentStr = text.replaceAll("^[^0-9]*([,0-9]+)\\s*%[^0-9]*$", "$1").replaceAll(",", ".");
						float percent = -1;
						if (percentStr != null && !percentStr.isEmpty()) {
							try {
								percent = Float.parseFloat(percentStr);
							} catch (NumberFormatException e) {
								//ignore
							}
						}
						
						if (text.equals("inkl. MwSt./Verm.")) {
							percent = 12.0f;
						}
						
						if (percent > 0) {
							try {
								String nettoPrice = new Integer(Math.round(Float.parseFloat(price.replaceAll("[^0-9]", "")) / (100 + percent) * 100)).toString();
								currentListing.setPrice(nettoPrice);
								currentListing.setCurrency(currency);
							} catch (Exception e) {
								//ignore
							}
						}
					}
				} else {
					if (price != null && !price.isEmpty()) {
						currentListing.setPrice(price);
						currentListing.setCurrency(currency);
					}
				}
			}
			
			//address
			if (line.matches(regexZipRegion) && inAddress && !zipRegionDone) {
				String zip = line.replaceAll(regexZipRegion, "$1").trim();
				String region = line.replaceAll(regexZipRegion, "$2").trim();
				
				if (zip != null && !zip.isEmpty()) {
					currentListing.setZip(zip);
				}
				
				if (region != null && !zip.isEmpty()) {
					currentListing.setRegion(region);
				}
				
				zipRegionDone = true;
			}
			
			if (inTel && inAddress) {
				inTel = false;
				String tel = line.replaceAll(regexTelCell, "$1");
				
				if (tel != null && !tel.isEmpty()) {
					inAddress = false;
					String country = Dialing.getCountry(tel);
					if (country != null) {
						currentListing.setCountry(country);
					}
				}
			}
			
			if (line.matches(regexInTel) && inAddress) {
				inTel = true;
			}
			
			if (inCell && inAddress) {
				inCell = false;
				String cell = line.replaceAll(regexTelCell, "$1");
				
				if (cell != null && !cell.isEmpty()) {
					inAddress = false;
					String country = Dialing.getCountry(cell);
					if (country != null) {
						currentListing.setCountry(country);
					}
				}
			}
			
			if (line.matches(regexInCell) && inAddress) {
				inCell = true;
			}
			
			if (line.matches(regexInAddress)) {
				inAddress = true;
			}
			
			//status
			if (line.matches(regexNew) && !statusDone) {
				currentListing.setNewMachine("1");
				statusDone = true;
			}
			
			if (line.matches(regexShow) && !statusDone) {
				currentListing.setNewMachine("2");
				statusDone = true;
			}
		}
		
		System.out.println("here");
	}

	@Override
	protected URL modifyUrl(URL originalUrl) {
		
		if (status.page == 1 || categorySpecialUrlPath == null) {
			URL result = retrieveUrl(originalUrl);
			if (result != null) {
				return result;
			}
		} else {
			String link = baseUrl + "/gebrauchte/" + categorySpecialUrlPath.replaceAll("offset=0", "offset=" + ((status.page - 1) * 20));
			try {
				return new URL(link);
			} catch (MalformedURLException e) {
				logger.severe("Malformed url: " + link + " | " + e.getMessage());
			}
		}
		
		return originalUrl;
	}

	private URL retrieveUrl(URL originalUrl) {
		String html = loadCustomPage(originalUrl);
		
		if (html != null) {
			String[] lines = html.split("\\r?\\n");
			String regexNextPage = "document.getElementById\\(\"navajax1\"\\)\\.innerHTML\\s*=\\s*'<span\\s*style=\"color:#000000\">1</span>\\s*<a\\s*href=\"([^\"]+)\">2</a>.*$";
			for (int i = 0; i < lines.length; i++) {
				if (lines[i].matches(regexNextPage)) {
					categorySpecialUrlPath = lines[i].replaceAll(regexNextPage, "$1").replaceAll("offset=20", "offset=" + ((status.page - 1) * 20));
					try {
						return new URL(baseUrl + "/gebrauchte/" + categorySpecialUrlPath);
					} catch (MalformedURLException e) {
						logger.severe("Malformed url: " + baseUrl + "/gebrauchte/" + categorySpecialUrlPath + " | " + e.getMessage());
					}
				}
			}
		}
		return originalUrl;
	}
	
}
