package qubase.suite;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Forklift extends Crawler {

	String baseUrl = "http://www.forklift.de/de/endkunde/";
	
	public Forklift() {
		super();
		name = "forklift";
		
		try {
			siteMapUrl = new URL("http://www.forklift.de/de/indexstapler.php");
		} catch (MalformedURLException e) {
			logger.severe("Failed to init siteMapUrl: [http://www.forklift.de/de/indexstapler.php] " + e.getMessage());
		}
		statusFile = name + ".status";
	}
	
	@Override
	protected void parseSiteMap(String input) {
		//parse forklifts
		String[] lines = input.split("\\r?\\n");
		boolean inSelectBox = false;
		String optionRegexp = "<option\\s*value=\"([0-9]+),(0)\"\\s*style=\"font-weight:\\s*bold;\">([a-zA-Z0-9]+)</option>";
		String listLinkReplace = "http://www.forklift.de/de/endkunde/staplersuche3.php?Bauart=$1,$2&sonderbit=0&reifen=*&Fabrikat=alle&antriebsart=*&masttypid=alle&tkvon=0&tkbis=100000&bhvon=0&bhbis=9000&hhvon=0&hhbis=30000&fhvon=0&fhbis=4000&baujahr=0&bjbis=2014&preisvon=0&preisbis=1000000&landid=*&entfernung=0&hatbild=0&numbers=50&page=1";
//		for (String lineIn : lines) {
//			String line = lineIn.trim();
//			if (line.matches("<select\\s*name=\"Bauart\"\\s*id=\"Bauart\".*?>")) {
//				inSelectBox = true;
//				continue;//no need to perform the other regular expressions
//			}
//			
//			if (line.matches(optionRegexp) && inSelectBox) {
//				try {
//					addToSiteMap(new SiteMapLocation(new URL(line.replaceAll(optionRegexp, listLinkReplace)), line.replaceAll(optionRegexp, "$3")));
//				} catch (MalformedURLException e) {
//					logger.severe("Failed to parse URL: " + line.replaceAll(optionRegexp, listLinkReplace));
//				}
//			}
//			
//			if (inSelectBox && line.matches(".*?</select>")) {
//				inSelectBox = false;
//				break;
//			}
//		}
		
//		//parse cleaning machines
//		input = null;
//		try {
//			input = loadCustomPage(new URL("http://www.forklift.de/de/indexrt.php"));
//		} catch (MalformedURLException e) {
//			logger.severe("Failed to parse URL: http://www.forklift.de/de/indexrt.php");
//		}
//		
//		if (input != null) {
//			lines = input.split("\\r?\\n");
//			for (String lineIn : lines) {
//				String line = lineIn.trim();
//				if (line.matches("<select\\s*name=\"Bauart\"\\s*id=\"Bauart\".*?>")) {
//					inSelectBox = true;
//					continue;//no need to perform the other regular expressions
//				}
//				
//				if (line.matches("<option.*") && inSelectBox) {
//					Pattern pattern = Pattern.compile("<option\\s*value=\"([0-9]+)\">(.*?)</option>");
//			        Matcher  matcher = pattern.matcher(line);
//			        
//			        while (matcher.find()) {
//			        	try {
//							addToSiteMap(new SiteMapLocation(new URL("http://www.forklift.de/de/endkunde/kehr/suche.php?plz=&entfernung=0&Bauart=" 
//										+ matcher.group(1) 
//										+ "&Fabrikat=alle&antriebsartid=alle&kbvon=0&kbbis=4000&preisvon=0&preisbis=1000000&landid=*&numbers=50&page=1")
//								, matcher.group(2)));
//						} catch (MalformedURLException e) {
//							logger.severe("Failed to parse URL: " + siteMapUrl + matcher.group(1));
//						}
//			        }
//				}
//				
//				if (line.matches(".*?</select>") && inSelectBox) {
//					inSelectBox = false;
//					break;
//				}
//			}
//		}
		
		//parse platforms
		//same code as cleaning machines, different URLs, could be a function, fuck it
		input = null;
		try {
			input = loadCustomPage(new URL("http://www.forklift.de/de/indexbuehne.php"));
		} catch (MalformedURLException e) {
			logger.severe("Failed to parse URL: http://www.forklift.de/de/indexbuehne.php");
		}
		
		if (input != null) {
			if (input != null) {
				lines = input.split("\\r?\\n");
				for (String lineIn : lines) {
					String line = lineIn.trim();
					if (line.matches("<select\\s*name=\"Bauart\"\\s*id=\"Bauart\".*?>")) {
						inSelectBox = true;
						continue;//no need to perform the other regular expressions
					}
					
					if (line.matches("<option.*") && inSelectBox) {
						Pattern pattern = Pattern.compile("<option\\s*value=\"([0-9]+)\">(.*?)</option>");
				        Matcher  matcher = pattern.matcher(line);
				        
				        while (matcher.find()) {
				        	try {
								addToSiteMap(new SiteMapLocation(new URL("http://www.forklift.de/de/endkunde/buehne/suche.php?plz=&entfernung=0&Bauart=" 
											+ matcher.group(1) 
											+ "&Fabrikat=alle&antriebsartid=alle&ahvon=0&ahbis=80000&klvon=0&klbis=2000&preisvon=0&preisbis=1000000&landid=*&numbers=50&page=1")
									, matcher.group(2)));
							} catch (MalformedURLException e) {
								logger.severe("Failed to parse URL: " + siteMapUrl + matcher.group(1));
							}
				        }
					}
					
					if (line.matches(".*?</select>") && inSelectBox) {
						inSelectBox = false;
						break;
					}
				}
			}
		}
	}

	@Override
	protected void parseList(String input) {
		String[] lines = input.split("\\r?\\n");
		String aRegexp = "<div\\s*class=\"makeBig\"><a\\s*href=\"(cgi/)?([^\"]+)\">(.*?)</a></div>";
		for (String lineIn : lines) {
			String line = lineIn.trim();
			String nextRegex = "^.*<a\\s*class=\"pfeilRechts\".*$";
			if (line.matches(nextRegex)) {
				status.nextPageAvailable = true;
			}
			
			if (line.matches(aRegexp)) {
				String infix = "";
				String listUrl = status.siteMap.get(status.siteMapIndex).url.toString();
				if (listUrl.startsWith("http://www.forklift.de/de/endkunde/kehr")) {
					infix = "kehr/";
				} else if (listUrl.startsWith("http://www.forklift.de/de/endkunde/buehne")) {
					infix = "buehne/";
				}
				
				String link = baseUrl + infix + line.replaceAll(aRegexp, "$1$2");
				try {
					list.add(new URL(link));
				} catch (MalformedURLException e) {
					logger.severe("Failed to add URL to the list: [" + link + "] " + e.getMessage());
				}
			}
		}
	}

	@Override
	protected void parseListing(String input) {
		currentListing = new Listing();
		
		try {
			currentListing.setUrl(list.get(status.pagePosition).toString());
		} catch (Exception e) {
			//ignore, this is a test call
		}
		
		boolean inTheMainDiv = false;
		
		String potentialBrand = null;
		String potentialCategory = null;
		
		String regexPotentialBrand = ".*?<h1>(.*?)</h1>$";
		String regexPotentialCategory = ".*?<h2>(.*?)</h2>$";
		
		String[] lines = input.split("\\r?\\n");
		for (int i = 0; i < lines.length; i++) {
			String line = lines[i].trim();
			
			if (line.matches("<div\\s*class=\"main_content_bg\">")) {
				inTheMainDiv = true;
			}
			
			if (inTheMainDiv && potentialBrand == null && line.matches(regexPotentialBrand)) {
				potentialBrand = line.replaceAll(regexPotentialBrand, "$1");
			}
			
			if (inTheMainDiv && potentialCategory == null && line.matches(regexPotentialCategory)) {
				potentialCategory = line.replaceAll(regexPotentialCategory, "$1");
			}
			
			if (line.matches("</div><!--\\s*#detailView\\s*-->") && inTheMainDiv) {
				inTheMainDiv = false;
				break;
			}
		}
		
		currentListing.setModelName(potentialBrand);
		currentListing.setCategory(potentialCategory);
		currentListing.setCatLang("DE");
	}

	@Override
	protected URL modifyUrl(URL originalUrl) {
		String url = originalUrl.toString().replaceAll("&page=[0-9]+", "&page=" + status.page);
		try {
			return new URL(url);
		} catch (MalformedURLException e) {
			logger.severe("Failed to parse URL: [" + url + "] " + e.getMessage());
			return originalUrl;
		}
	}

}
