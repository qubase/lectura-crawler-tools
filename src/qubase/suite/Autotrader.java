package qubase.suite;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Autotrader extends Crawler {
	
	private URL siteMapUrlPlant;
	
	public Autotrader() {
		super();
		name = "autotrader";
		
		try {
			siteMapUrl = new URL("http://farm.autotrader.co.uk/search");
			siteMapUrlPlant = new URL("http://plant.autotrader.co.uk/search");
		} catch (MalformedURLException e) {
			logger.severe("Failed to init siteMapUrl: [http://farm.autotrader.co.uk/search] or [http://plant.autotrader.co.uk/search] " + e.getMessage());
		}
		statusFile = name + ".status";
	}

	@Override
	protected void parseSiteMap(String input) {
		 status.siteMap.add(new SiteMapLocation(siteMapUrl, "Farm"));
		 status.siteMap.add(new SiteMapLocation(siteMapUrlPlant, "Plant"));
	}

	@Override
	protected void parseList(String input) {
		String[] lines = input.split("\\r?\\n");
		String regexLink = ".*?<a href=\"(.*?)\"\\s*title=\".*?\"\\s*class=\"tracking-standard-link\"\\s*data-label=\"listing-advert-title\">.*$";
		for (int i = 0; i < lines.length; i++) {
			if (lines[i].matches(regexLink)) {
				String link = lines[i].replaceAll(regexLink, "$1");
				try {
					URL url = new URL(link);
					if (!status.list.contains(url)) {
						status.list.add(url);
					}
				} catch (MalformedURLException e) { 
					logger.severe("Failed to add URL to the list: [" + link + "] " + e.getMessage());
				}
			}
			
			if (lines[i].matches(".*?<span\\s*class=\"pagination__text\"\\s*data-label=\"bottom-next-page\">Next</span></a>.*$")) {
				status.nextPageAvailable = true;
			}
		}
	}

	@Override
	protected void parseListing(String input) {
		currentListing = new Listing();
		
		try {
			currentListing.setUrl(status.list.get(status.pagePosition).toString());
		} catch (Exception e) {
			//ignore, this is a test call
		}
		
		currentListing.setDate(new SimpleDateFormat("yyyy-MM-dd").format(new Date()));
		currentListing.setCountry("GB");
		
		String[] lines = input.split("\\r?\\n");
		
		String regexInBreadCrumbs = "<a\\s*href=\".*?\">Used\\s(Farm|Plant)\\sMachinery</a>\\s*/";
		String regexInAdvert = "<div\\s*class=\"advertInfo\">";
		String regexManufacturer = "<a href=\".*?\">(.*?)</a>\\s*/\\s*";
		String regexPrice = ".*&pound;([,\\.0-9]+)(\\s\\+VAT)?.*";
		String regexPriceEur = ".*&euro;([,\\.0-9]+).*";
		String regexCompany = ".*?<h2\\s*class=\"dealerProfileHeading\">([^<]+)</h2>.*$";
		
		String manufacturer = null;
		String category = null;
		
		boolean inBreadCrumbs = false;
		boolean inInfo = false;
		boolean nextLiIsFirst = false;
		
		boolean hasEur = false;
		
		for (int i = 0; i < lines.length; i++) {
			
			String line = lines[i].trim();
			
			//model + brand
			if (line.matches(regexManufacturer) && inBreadCrumbs && manufacturer == null) {
				manufacturer = line.replaceAll(regexManufacturer, "$1");
				
				if (!manufacturer.isEmpty()) {
					currentListing.setManName(manufacturer);
				}
			}
			
			if (manufacturer != null && !manufacturer.isEmpty() && line.matches(manufacturer + " .*$") && inBreadCrumbs) {
				currentListing.setModelName(line.replaceAll(manufacturer + " (.*)$", "$1"));
				inBreadCrumbs = false;
			}
			
			if (line.matches(regexInBreadCrumbs)) {
					inBreadCrumbs = true;
			}
			
			//company
			if (line.matches(regexCompany)) {
				String company = line.replaceFirst(regexCompany, "$1");
				if (company != null && !company.isEmpty()) {
					currentListing.setCompany(company);
				}
				continue;
			}
			
			
			if (line.matches(regexPrice) && !hasEur) {
				String price = line.replaceAll(regexPrice, "$1");
				if (price != null && !price.isEmpty()) {
					currentListing.setPrice(price);
					currentListing.setCurrency("GBP");
				}
			}
			
			if (line.matches(regexPriceEur) && !hasEur) {
				String price = line.replaceAll(regexPriceEur, "$1");
				if (price != null && !price.isEmpty()) {
					currentListing.setPrice(price);
					currentListing.setCurrency("EUR");
					hasEur = true;
				}
			}
			
			//category + year + hrs
			if (line.matches("<div\\s*class=\"clear\"></div>$") && inInfo) {
				inInfo = false;
				continue;
			}
			
			if (line.matches("<ul>") && inInfo) {
				nextLiIsFirst = true;
				continue;
			}
			
			if (line.matches("[^<>]+") && inInfo) {
				String val = line;
				boolean valAccepted = false;
				if (val.matches("[0-9]{4}")) {
					currentListing.setYear(val);
					valAccepted = true;
				}
				
				if (val.matches("[0-9]+\\sHrs\\sUsed")) {
					currentListing.setCounter(val.replaceAll("([0-9]+)\\sHrs\\sUsed", "$1"));
					valAccepted = true;
				}
				
				if (nextLiIsFirst && !valAccepted) {
					category = val;
				}
				
				if (nextLiIsFirst) {
					nextLiIsFirst = false;
				}
			}
			
			if (line.matches(regexInAdvert)) {
				inInfo = true;
				continue;
			}
			
			//region
			if (line.matches("<h1>.*?</h1>")) {
				String region = line.replaceAll("<h1>.*\\sin\\s(.*?)?</h1>", "$1");
				
				if (region != null && !region.isEmpty()) {
					currentListing.setRegion(region);
				}
			}
		}
		
		if (category != null && !category.isEmpty()) {
			currentListing.setCategory(category);
			currentListing.setCatLang("EN");
		}
		
	}

	@Override
	protected URL modifyUrl(URL originalUrl) {
		try {
			return new URL(originalUrl + "?pageNumber=" + status.page);
		} catch (MalformedURLException e) {
			logger.severe("Failed to modify URL: " + originalUrl + "?pageNumber=" + status.page + " | " + e.getMessage());
			return null;
		}
	}

}
