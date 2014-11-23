package qubase.suite;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;


public class Forkliftaction extends Crawler {

	private String baseUrl = null;
	
	public Forkliftaction() {
		super();
		name = "forkliftaction";
		
		baseUrl = "http://forkliftaction.com/marketplace/";
		try {
			siteMapUrl = new URL(baseUrl + "default.aspx");
		} catch (MalformedURLException e) {
			logger.severe("Failed to init siteMapUrl: [" + baseUrl + "default.aspx" + "] " + e.getMessage());
		}
		statusFile = name + ".status";
	}

	protected void parseSiteMap(String input) {
		String[] lines = input.split(System.getProperty("line.separator"));
		boolean inTheRightDiv = false;
		boolean inH2 = false;
		String aRegexp = "<a href=\"(list.*\\.aspx.*=[0-9]+)\".*>";
		String listLink = null;
		for (String lineIn : lines) {
			String line = lineIn.trim();
			if (line.matches("<div class=\"typestyle\">")) {
				inTheRightDiv = true;
				continue;//no need to perform the other regular expressions
			}
			
			//now i'm in the right div and i'm looking for the link to the list
			if (inTheRightDiv && line.matches(aRegexp)) {
				listLink = baseUrl + line.replaceAll(aRegexp, "$1");
				continue;
			}
			
			//now i'm in the h2 and want to save the name
			if (inH2) {
				try {
					addToSiteMap(new SiteMapLocation(new URL(listLink), line.trim()));
				} catch (MalformedURLException e) {
					logger.severe("Failed to parse URL: " + listLink);
				}
				//get out of scope for now
				inH2 = false;
				inTheRightDiv = false;
				listLink = null;
			}
			
			if (line.matches("<h2>")) {
				inH2 = true;
			}
		}
	}
	
	protected void parseList(String input) {
		String[] lines = input.split(System.getProperty("line.separator"));
		String aRegexp = "<a\\s*href=\"/marketplace/([^\"]+)\"\\s*target=\"_blank\">View Details</a>";
		for (String lineIn : lines) {
			String line = lineIn.trim();
			String nextRegex = "^.*<span class='clsrslts'>RESULTS [0-9]+-([0-9]+) FROM ([0-9]+)</span>.*$";
			if (!line.replaceAll(nextRegex, "$1").equals(line.replaceAll(nextRegex, "$2"))) {
				status.nextPageAvailable = true;
			}
			
			if (line.matches(aRegexp)) {
				String link = baseUrl + line.replaceAll(aRegexp, "$1");
				try {
					status.list.add(new URL(link));
				} catch (MalformedURLException e) {
					logger.severe("Failed to add URL to the list: [" + link + "] " + e.getMessage());
				}
			}
		}
	}
	
	protected void parseListing(String input) {
		currentListing = new Listing();
		
		try {
			currentListing.setUrl(status.list.get(status.pagePosition).toString());
			currentListing.setCategory(status.siteMap.get(status.siteMapIndex).name);
			currentListing.setCatLang("EN");
		} catch (Exception e) {
			//ignore, this is a test call
		}
		
		String regexBrand = "^.*?<div\\s*class='maincaption'>Brand:</div>\\s*<h1>\\s*(.*?)\\s*</h1>.*$"; //parse brand
		String regexModel = "^.*?<div\\s*class='maincaption'>Model:</div>\\s*<h1>\\s*(.*?)\\s*</h1>.*$"; //parse model name
		String regexYear = "^.*?<div\\s*class='maincaption'>Year:</div>\\s*<h1>\\s*([0-9]*)\\s*</h1>.*$"; //parse year of manufacture
		String regexCurrPrice = "^.*?<div\\s*class='maincaption'>Price:</div>\\s*<h1>\\s*([A-Z]*)\\s*([0-9,\\.]*).*$"; //parse currency and price;
		String regexDate = "^.*?<b>Date listed</b></div><div.*?>([0-9]+\\s*[a-zA-Z]+\\s*[0-9]+)</div>.*$"; //parse date
		String regexRegionCountry = "^.*?<b>Unit location</b></div><div.*?>(.*?)</div>.*$"; //parse country and location
		String regexHours = "^.*?<b>Machine hours</b></div><div.*?>([0-9\\.,]+)</div>.*$"; //parse machine hours
		String regexSerial = "^.*?<b>Serial number</b></div><div.*?>(.+?)</div>.*$"; //parse serial number
		String regexSubcategory = "^.*?<b>Power / Fuel type</b></div><div.*?>(.+?)</div>.*$"; //parse subcategory
		String regexCompany = "^.*?<div\\s*style='float:left'\\s*class='microcopybold'>Dealer:</div>.*?<a.*?>(.*?)\\s*</a>.*$"; //parse company
		
		String[] lines = input.split(System.getProperty("line.separator"));
		for (String lineIn : lines) {
			String line = lineIn.trim();
			
			if (line.matches(regexBrand)) {
				currentListing.setManName(line.replaceAll(regexBrand, "$1"));
			}
			
			if (line.matches(regexModel)) {
				currentListing.setModelName(line.replaceAll(regexModel, "$1"));
			}
			
			if (line.matches(regexYear)) {
				currentListing.setYear(line.replaceAll(regexYear, "$1"));
			}
			
			if (line.matches(regexCurrPrice)) {
				currentListing.setCurrency(line.replaceAll(regexCurrPrice, "$1"));
				currentListing.setPrice(line.replaceAll(regexCurrPrice, "$2"));
			}
			
			if (line.matches(regexCompany)) {
				currentListing.setCompany(line.replaceAll(regexCompany, "$1"));
			}
			
			if (line.matches(regexDate)) {
				int style = DateFormat.MEDIUM;
				SimpleDateFormat sdf = (SimpleDateFormat) DateFormat.getDateInstance(style, Locale.US);
				sdf.applyPattern("dd MMM yyyy");
				Date date = null;
				String dateIn = line.replaceAll(regexDate, "$1");
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
			
			if (line.matches(regexRegionCountry)) {
				String[] values = line.replaceAll(regexRegionCountry, "$1").split(",");
				currentListing.setCountry(values[values.length - 1].trim());
				if (values.length > 1) {
					String region = "";
					for (int i = 0; i < values.length - 1; i++) {
						region += (i < values.length - 2) ? values[i].trim() + ", " : values[i].trim();
					}
					currentListing.setRegion(region);
				}
			}
			
			if (line.matches(regexHours)) {
				currentListing.setCounter(line.replaceAll(regexHours, "$1"));
			}
			
			if (line.matches(regexSerial)) {
				currentListing.setSerial(line.replaceAll(regexSerial, "$1"));
			}
			
			//if there is a power/fuel type, attach it to the main category name
			if (line.matches(regexSubcategory)) {
				currentListing.setCategory(currentListing.getCategory() + " - " + line.replaceAll(regexSubcategory, "$1"));
			}
		}
	}
	
	protected URL modifyUrl(URL originalUrl) {
		String url = originalUrl.toString() + "&dispcurrency=EUR&page=" + status.page;
		try {
			return new URL(url);
		} catch (MalformedURLException e) {
			logger.severe("Failed to parse URL: [" + url + "] " + e.getMessage());
			return originalUrl;
		}
	}
}
