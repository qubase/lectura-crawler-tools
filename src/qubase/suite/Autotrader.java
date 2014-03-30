package qubase.suite;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Autotrader extends Crawler {
	
	public Autotrader() {
		super();
		name = "autotrader";
		
		try {
			siteMapUrl = new URL("http://farm.autotrader.co.uk/search");
		} catch (MalformedURLException e) {
			logger.severe("Failed to init siteMapUrl: [http://farm.autotrader.co.uk/search] " + e.getMessage());
		}
		statusFile = name + ".status";
	}

	@Override
	protected void parseSiteMap(String input) {
		 status.siteMap.add(new SiteMapLocation(siteMapUrl, "Search"));
	}

	@Override
	protected void parseList(String input) {
		String[] lines = input.split("\\r?\\n");
		String regexLink = ".*?<a href=\"(.*?)\"\\s*title=\".*?\"\\s*class=\"main\">.*$";
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
			
			if (lines[i].matches(".*?<a\\s*href=\"[^\"]+\"\\s*class=\"last\">Last\\s*&raquo;</a>.*$")) {
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
		
		String regexInBreadCrumbs = "<a\\s*href=\".*?\">Used\\sFarm\\sMachinery</a>\\s*&gt;";
		String regexInAdvert = "<div\\s*class=\"advertInfo\">";
		String regexManufacturer = "<a href=\".*?\">(.*?)</a>\\s*&gt;";
		String regexPrice = "&pound;([,\\.0-9]+)(\\s\\+VAT)?";
		
		String manufacturer = null;
		String category = null;
		
		boolean inBreadCrumbs = false;
		boolean inInfo = false;
		boolean nextLiIsFirst = false;
		
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
			
			//price
			if (line.matches(regexPrice)) {
				String price = line.replaceAll(regexPrice, "$1");
				
				if (price != null && !price.isEmpty()) {
					currentListing.setPrice(price);
					currentListing.setCurrency("GBP");
				}
			}
			
			//category + year + hrs
			if (line.matches("<div\\s*class=\"clear\"></div>") && inInfo) {
				inInfo = false;
			}
			
			if (line.matches("<ul>")) {
				nextLiIsFirst = true;
			}
			
			if (line.matches("<li>.*</li>")) {
				String val = line.replaceAll("<li>(.*)</li>", "$1");
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
