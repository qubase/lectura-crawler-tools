package qubase.suite;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Forklift extends Crawler {

	String baseUrl = "http://www.forklift-international.com/de/e/";
	
	public Forklift() {
		super();
		name = "forklift";
		
		try {
			siteMapUrl = new URL("http://www.forklift-international.com/de/indexstapler.php");
		} catch (MalformedURLException e) {
			logger.severe("Failed to init siteMapUrl: [http://www.forklift-international.com/de/indexstapler.php] " + e.getMessage());
		}
		statusFile = name + ".status";
	}
	
	@Override
	protected void parseSiteMap(String input) {
		//parse forklifts
		String[] lines = input.split("\\r?\\n");
		boolean inSelectBox = false;
		String optionRegexp = "<option\\s*value=\"([0-9]+),(0)\"\\s*style=\"font-weight:\\s*bold;\">([a-zA-Z0-9]+)</option>";
		String listLinkReplace = "http://www.forklift-international.com/de/e/staplersuche3.php?Bauart=$1,$2&sonderbit=0&reifen=*&Fabrikat=alle&antriebsart=*&masttypid=alle&tkvon=0&tkbis=100000&bhvon=0&bhbis=9000&hhvon=0&hhbis=30000&fhvon=0&fhbis=4000&baujahr=0&bjbis=2014&preisvon=0&preisbis=1000000&landid=*&entfernung=0&hatbild=0&numbers=50&page=1";
		for (String lineIn : lines) {
			String line = lineIn.trim();
			if (line.matches("<select\\s*name=\"Bauart\"\\s*id=\"Bauart\".*?>")) {
				inSelectBox = true;
				continue;//no need to perform the other regular expressions
			}
			
			if (line.matches(optionRegexp) && inSelectBox) {
				try {
					addToSiteMap(new SiteMapLocation(new URL(line.replaceAll(optionRegexp, listLinkReplace)), line.replaceAll(optionRegexp, "$3")));
				} catch (MalformedURLException e) {
					logger.severe("Failed to parse URL: " + line.replaceAll(optionRegexp, listLinkReplace));
				}
			}
			
			if (inSelectBox && line.matches(".*?</select>")) {
				inSelectBox = false;
				break;
			}
		}
		
		//parse cleaning machines
		input = null;
		try {
			input = loadCustomPage(new URL("http://www.forklift-international.com/de/indexrt.php"));
		} catch (MalformedURLException e) {
			logger.severe("Failed to parse URL: http://www.forklift-international.com/de/indexrt.php");
		}
		
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
							addToSiteMap(new SiteMapLocation(new URL("http://www.forklift-international.com/de/e/kehr/suche.php?plz=&entfernung=0&Bauart=" 
										+ matcher.group(1) 
										+ "&Fabrikat=alle&antriebsartid=alle&kbvon=0&kbbis=4000&preisvon=0&preisbis=1000000&landid=*&numbers=50&page=1")
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
		
		//parse platforms
		//same code as cleaning machines, different URLs, could be a function, fuck it
		input = null;
		try {
			input = loadCustomPage(new URL("http://www.forklift-international.com/de/indexbuehne.php"));
		} catch (MalformedURLException e) {
			logger.severe("Failed to parse URL: http://www.forklift-international.com/de/indexbuehne.php");
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
								addToSiteMap(new SiteMapLocation(new URL("http://www.forklift-international.com/de/e/buehne/suche.php?plz=&entfernung=0&Bauart=" 
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
				if (listUrl.startsWith("http://www.forklift-international.com/de/e/kehr")) {
					infix = "kehr/";
				} else if (listUrl.startsWith("http://www.forklift-international.com/de/e/buehne")) {
					infix = "buehne/";
				}
				
				String link = baseUrl + infix + line.replaceAll(aRegexp, "$1$2");
				try {
					status.list.add(new URL(link));
				} catch (MalformedURLException e) {
					logger.severe("Failed to add URL to the list: [" + link + "] " + e.getMessage());
				}
			}
		}
		System.out.println();
	}

	@Override
	protected void parseListing(String input) {
		currentListing = new Listing();
		
		try {
			currentListing.setUrl(status.list.get(status.pagePosition).toString());
		} catch (Exception e) {
			//ignore, this is a test call
		}
		
		boolean inTheMainDiv = false;
		
		String potentialBrand = null;
		String potentialCategory = null;
		String potentialModel = null;
		String brand = null;
		String category = null;
		String model = null;
		
		String regexPotentialBrand = ".*?<h1>(.*?)</h1>$";
		String regexPotentialCategory = ".*?<h2>(.*?)</h2>$";
		String regexAddress = "<div\\s*class=\"adress\">(.*?)</div>";
		
		String[] lines = input.split("\\r?\\n");
		for (int i = 0; i < lines.length; i++) {
			String line = lines[i].trim();
			
			if (line.matches("</div><!--\\s*#detailView\\s*-->") && inTheMainDiv) {
				inTheMainDiv = false;
				break;
			}
			
			if (line.matches("<div\\s*class=\"main_content_bg\">")) {
				inTheMainDiv = true;
			}
			
			if (inTheMainDiv && line.matches("<div\\s*class=\"newMachine\\s*NMdetailView\">NEU</div>.*$")) {
				currentListing.setNewMachine("1");
			}
			
			if (inTheMainDiv && potentialBrand == null && line.matches(regexPotentialBrand)) {
				potentialBrand = line.replaceAll(regexPotentialBrand, "$1");
			}
			
			if (inTheMainDiv && potentialCategory == null && line.matches(regexPotentialCategory)) {
				potentialCategory = line.replaceAll(regexPotentialCategory, "$1");
			}
			
			if (inTheMainDiv && line.matches("<span\\s*class=\"label\">Fabrikat:</span>")) {
				i++;
				if (lines.length > i) {
					line = lines[i].trim();
					brand = line.replaceAll("<span\\s*class=\"value\">(.*?)</span>", "$1");
				}
			}
			
			if (inTheMainDiv && line.matches("<span\\s*class=\"label\">Baujahr:</span>")) {
				i++;
				if (lines.length > i) {
					line = lines[i].trim();
					currentListing.setYear(line.replaceAll("<span\\s*class=\"value\">([0-9]+)</span>", "$1"));
					if (currentListing.getYear().equals("0")) {
						currentListing.setYear(null);
					}
				}
			}
			
			if (inTheMainDiv && line.matches("<span\\s*class=\"label\">Betriebsstunden:</span>")) {
				i++;
				if (lines.length > i) {
					line = lines[i].trim();
					currentListing.setCounter(line.replaceAll("<span\\s*class=\"value\">([0-9\\., ']+)</span>", "$1"));
					if (currentListing.getCounter().equals("0")) {
						currentListing.setCounter(null);
					}
				}
			}
			
			if (inTheMainDiv && line.matches("<span\\s*class=\"label\">Fahrgestellnummer:</span>")) {
				i++;
				if (lines.length > i) {
					line = lines[i].trim();
					currentListing.setSerial(line.replaceAll("<span\\s*class=\"value\">(.*?)</span>", "$1"));
				}
			}
			
			if (inTheMainDiv && line.matches("<span\\s*class=\"label\">Bauart:</span>")) {
				i++;
				if (lines.length > i) {
					line = lines[i].trim();
					category = line.replaceAll("<span\\s*class=\"value\">(.*?)</span>", "$1");
				}
			}
			
			if (inTheMainDiv && line.matches("<span\\s*class=\"label\">Typ:</span>")) {
				i++;
				if (lines.length > i) {
					line = lines[i].trim();
					model = line.replaceAll("<span\\s*class=\"value\">(.*?)</span>", "$1");
				}
			}
			
			if (inTheMainDiv && potentialModel == null && line.matches("<div\\s*class=\"boxInnerHeadline\">")) {
				i++;
				if (lines.length > i) {
					line = lines[i].trim();
					potentialModel = line.replaceAll("(.*?)</div>", "$1").trim();
				}
			}
			
			if (inTheMainDiv && line.matches("<span\\s*class=\"labelHighlight\">Verkaufspreis.*?</span>")) {
				i++;
				if (lines.length > i) {
					line = lines[i].trim();
					String priceCurrency = line.replaceAll("<span\\s*class=\"valueHighlight\">(.*?)</span>", "$1");
					if (priceCurrency.matches(".*[0-9].*")) {
						String currency = priceCurrency.replaceAll("[^A-Z]", "");
						String price = priceCurrency.replaceFirst(currency, "").trim();
						if (!price.equals("1")) {
							currentListing.setCurrency(priceCurrency.replaceAll("[^A-Z]", ""));
							currentListing.setPrice(price);
						}
					}
				}
			}
			
			if (inTheMainDiv && line.matches(regexAddress)) {
				String address = line.replaceAll(regexAddress, "$1");
				String country = address.replaceFirst(".*?<span\\s*itemprop=\"addressCountry\">(.*?)</span>.*$", "$1").trim();
				String zip = address.replaceFirst(".*?<span\\s*itemprop=\"postalCode\">(.*?)</span>.*$", "$1").trim();
				String region = address.replaceFirst(".*?<span\\s*itemprop=\"addressLocality\">(.*?)</span>.*$", "$1").trim();
				String company = address.replaceFirst(".*?<span\\s*itemprop=\"name\">(.*?)</span>.*$", "$1").trim();
				
				currentListing.setCountry(country);
				currentListing.setZip(zip);
				currentListing.setRegion(region);
				currentListing.setCompany(company);
			}
		}
		
		if (brand == null) {
			brand = potentialBrand.trim();
		}
		
		if (category == null) {
			category = potentialCategory;
		}
		
		if (model == null) {
			if (potentialModel != null && brand != null) {
				model = potentialModel.substring(potentialModel.indexOf(brand) + brand.length()).trim();
			}
		}
		
		currentListing.setModelName(model);
		currentListing.setManName(brand);
		currentListing.setCategory(category);
		currentListing.setCatLang("DE");
		currentListing.setDate(new SimpleDateFormat("yyyy-MM-dd").format(new Date()));
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
