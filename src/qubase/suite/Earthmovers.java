package qubase.suite;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Earthmovers extends Crawler {
	
	private URL siteMapUrlFarm;
	private String baseUrl;
	
	public Earthmovers() {
		super();
		name = "earthmovers";
		baseUrl = "http://www.tradeearthmovers.com.au";
		try {
			siteMapUrl = new URL("http://www.tradeearthmovers.com.au/search/");
			siteMapUrlFarm = new URL("http://www.tradefarmmachinery.com.au/search");
		} catch (MalformedURLException e) {
			logger.severe("Failed to init siteMapUrl: [http://www.tradeearthmovers.com.au/search/] or [http://www.tradefarmmachinery.com.au/search] " + e.getMessage());
		}
		statusFile = name + ".status";
	}

	@Override
	protected void parseSiteMap(String input) {
		 status.siteMap.add(new SiteMapLocation(siteMapUrl, "Earthmovers"));
		 status.siteMap.add(new SiteMapLocation(siteMapUrlFarm, "Farmmachinery"));
	}

	@Override
	protected void parseList(String input) {
		String[] lines = input.split("\\r?\\n");
		String regexLink = ".*?<a\\s*class=\"view-button\"\\s*href=\"(/detail/[^\"]+)\">View\\s*Details</a>.*$";
		for (int i = 0; i < lines.length; i++) {
			if (lines[i].matches(regexLink)) {
				String link = baseUrl + lines[i].replaceAll(regexLink, "$1");
				try {
					URL url = new URL(link);
					if (!status.list.contains(url)) {
						status.list.add(url);
					}
				} catch (MalformedURLException e) { 
					logger.severe("Failed to add URL to the list: [" + link + "] " + e.getMessage());
				}
			}
			
			// ak je currentpage inde nez na konci zoznamu, tak nie som na poslednej
			// sekvencia '</li><li>' za currentpage znamena, ze tam je este nejaka dalsia stranka
			if (lines[i].matches(".*?<li\\s*class=\"currentpage\">[0-9]+</li><li>.*$")) {
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
		String regexPrice = "<div>&pound;([,\\.0-9]+)(\\s\\+VAT)?</div>";
		String regexPriceEur = "<div\\s*class=\"priceEur\">&euro;([,\\.0-9]+)</div>";
		String regexInPrice = "<div\\s*class=\"price\">";
		String regexInCompany = ".*?<span\\s*class=\"seller\">Trade Seller:</span>.*$";
		
		String manufacturer = null;
		String category = null;
		
		boolean inBreadCrumbs = false;
		boolean inInfo = false;
		boolean nextLiIsFirst = false;
		boolean inPrice = false;
		boolean inCompany = false;
		
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
			if (inCompany) {
				String company = line.trim();
				if (company != null && !company.isEmpty()) {
					currentListing.setCompany(company);
				}
				inCompany = false;
				continue;
			}
			
			if (line.matches(regexInCompany)) {
				inCompany = true;
				continue;
			}
			
			//price
			if (inPrice) {
				if (line.matches(regexPrice)) {
					String price = line.replaceAll(regexPrice, "$1");
					if (price != null && !price.isEmpty()) {
						currentListing.setPrice(price);
						currentListing.setCurrency("GBP");
					}
					inPrice = false;
				}
				
				if (lines[i+1].trim().matches(regexPriceEur)) {
					line = lines[i+1].trim();
					String price = line.replaceAll(regexPriceEur, "$1");
					if (price != null && !price.isEmpty()) {
						currentListing.setPrice(price);
						currentListing.setCurrency("EUR");
					}
					inPrice = false;
					i++;
				}
			}
			
			if (inPrice && line.matches("^\\s*</div>\\s*$")) {
				inPrice = false;
				continue;
			}
			
			if (line.matches(regexInPrice)) {
				inPrice = true;
				continue;
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
			
			if (line.matches("<li>.*</li>") && inInfo) {
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
