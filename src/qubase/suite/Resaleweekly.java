package qubase.suite;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Resaleweekly extends Crawler {
	
//	private String baseUrl = null;
	
	public Resaleweekly() {
		super();
		name = "resaleweekly";
		
		try {
			siteMapUrl = new URL("http://www.resaleweekly.com");
		} catch (MalformedURLException e) {
			logger.severe("Failed to init siteMapUrl: [http://www.resaleweekly.com] " + e.getMessage());
		}
		statusFile = name + ".status";
//		baseUrl = "http://www.resaleweekly.com";
	}

	@Override
	protected void parseSiteMap(String input) {
		String[] lines = input.split("\\r?\\n");
		for (String lineIn : lines) {
			String line = lineIn.trim();
			if (line.matches("^.*?<div\\s*class='firstCategory'><span\\s*class=\"categoryName\">.*$")) {
				Pattern pattern = Pattern.compile("<div\\s*class='firstCategory'><span\\s*class=\"categoryName\"><a\\s*href=\"(.*?)\">(.*?)</a>");
				Matcher matcher = pattern.matcher(line);
		        
		        while (matcher.find()) {
		        	String listLink = siteMapUrl + matcher.group(1);
		        	try {
						addToSiteMap(new SiteMapLocation(new URL(listLink), matcher.group(2)));
					} catch (MalformedURLException e) {
						logger.severe("Failed to parse URL: " + listLink);
					}
		        }
			}
		} 
	}

	@Override
	protected void parseList(String input) {
		String[] lines = input.split("\\r?\\n");
		String aRegex = "<a\\s*class=\"dark-text\"\\s*href=\"(.*?)\"\\s*title=\".*?\">";
		for (String lineIn : lines) {
			String line = lineIn.trim();
			if (line.matches(aRegex)) {
				String link = line.replaceAll(aRegex, "$1");
				try {
					status.list.add(new URL(link));
				} catch (MalformedURLException e) {
					logger.severe("Failed to add URL to the list: [" + link + "] " + e.getMessage());
				}
			}
			
			if (line.matches(".*aria-label=\"Next\".*")) {
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
		
		currentListing.setCategory(status.siteMap.get(status.siteMapIndex).name);
		currentListing.setCatLang("EN");
		currentListing.setDate(new SimpleDateFormat("yyyy-MM-dd").format(new Date()));
		
		String[] lines = input.split("\\r?\\n");
		
		String regexMake = "<td\\s*class=\"name\">Make</td>";
		boolean inMake = false;
		String regexModel = "<td\\s*class=\"name\">Model</td>";
		boolean inModel = false;
		String regexPrice = "<span class=\"price\">(.*?)</span>";
		String regexYear = "<td\\s*class=\"name\">Year</td>";
		boolean inYear = false;
		String regexHours = "<td\\s*class=\"name\">Hours</td>";
		boolean inHours = false;
		String regexSerial = "<td\\s*class=\"name\">Serial\\sNumber</td>";
		boolean inSerial = false;
		String regexAddress = "<td>Location</td>";
		boolean inAddress = false;
		String regexCondition = "<td>Condition</td>";
		boolean inCondition = false;
		String regexCompany = "<div\\s*class=\"contactTheSeller.*?\">(.*?)</div>";
		String regexPurchaseType = "<td>Purchase\\sType</td>";
		boolean inPurchaseType = false;
		String regexTitle = "<title>(.*?)</title>";
		String title = null;
		
		for (String lineIn : lines) {
			String line = lineIn.trim();
			
			if (line.matches(regexTitle)) {
				title = line.replaceFirst(regexTitle, "$1");
			}
			
			if (inMake) {
				String make = line.replaceFirst("<td>(.*?)</td>", "$1");
				if (!make.equals("N/A") && !make.equals("Unknown") && !make.isEmpty()) {
					currentListing.setManName(make);
				}
				inMake = false;
			}
			
			if (line.matches(regexMake)) {
				inMake = true;
			}
			
			if (inModel) {
				String model = line.replaceFirst("<td>(.*?)</td>", "$1");
				if (!model.equals("N/A") && !model.equals("Unknown") && !model.isEmpty()) {
					currentListing.setModelName(model);
				} else if (model.isEmpty() && currentListing.getManName() != null) {
					// get it from title, some bug on resaleweekly side, no model in Model property but available in the title
					model = title.substring(currentListing.getManName().length() + 5).replaceFirst("(.*?)\\sfor\\ssale.*", "$1");
					if (!model.isEmpty()) {
						currentListing.setModelName(model);
					}
				}
				inModel = false;
			}
			
			if (line.matches(regexModel)) {
				inModel = true;
			}
			
			if (line.matches(regexCompany)) {
				currentListing.setCompany(line.replaceFirst(regexCompany, "$1"));
			}
			
			if (line.matches(regexPrice)) {
				String price = line.replaceFirst(regexPrice, "$1");
				if (!price.startsWith("Call")) {
					currentListing.setPrice(price.replaceFirst("^([0-9,]+)(\\.[0-9]{2})?.*$", "$1"));
					if (currentListing.getPrice() != null && !currentListing.getPrice().isEmpty()) {
						String curr = price.replaceFirst("^.*?\\s([A-Z]{2,4})$", "$1");
						if (curr.matches("[A-Z]{2,4}")) {
							currentListing.setCurrency(curr);
						}
					} else if (currentListing.getPrice().isEmpty()) {
						currentListing.setPrice(null);
					}
				}
			}
			
			if (inYear) {
				String year = line.replaceFirst("<td>(.*?)</td>", "$1");
				if (!year.equals("N/A") && !year.equals("Unknown") && !year.isEmpty() && !year.equals("0") && year.length() != 4) {
					currentListing.setYear(year);
				}
				inYear = false;
			}
			
			if (line.matches(regexYear)) {
				inYear = true;
			}
			
			if (inHours) {
				String hours = line.replaceFirst("<td>(.*?)</td>", "$1");
				if (!hours.equals("N/A") && !hours.equals("Unknown") && !hours.equals("0") && !hours.isEmpty()) {
					currentListing.setCounter(hours);
				}
				inHours = false;
			}
			
			if (line.matches(regexHours)) {
				inHours = true;
			}
			
			if (inSerial) {
				String serial = line.replaceFirst("<td>(.*?)</td>", "$1");
				if (!serial.equals("N/A") && !serial.equals("Unknown") && !serial.isEmpty() && !serial.equals("0")) {
					currentListing.setSerial(serial);
				}
				inSerial = false;
			}
			
			if (line.matches(regexSerial)) {
				inSerial = true;
			}
			
			if (inAddress && !line.equals("<td>")) {
				String normalizedAddr = line.replaceAll("<br/>", " ").replaceFirst("\\s*</td>", "");
				String[] crumbs = normalizedAddr.split(", ");
				String country = null;
				if (crumbs.length > 0) {
					country = crumbs[0];
				}
				String region = "";
				String zip = null;
				if (crumbs.length > 1) {
					String delim = "";
					for (int i = 1; i < crumbs.length; i++) {
						if (i == crumbs.length - 1) {
							if (crumbs[i].matches("[-A-Z0-9]{2,3}\\s?([-A-Z0-9]{2,3})?\\s?[A-Z0-9]{2,3}")) {
								zip = crumbs[i];
							} else if (!crumbs[i].equals("0") && !crumbs[i].equals("??") && !crumbs[i].isEmpty()) {
								region += delim + crumbs[i];
							}
						} else {
							region += delim + crumbs[i];
						}
						delim = ", ";
					}
				}
				
				if (country != null) {
					currentListing.setCountry(country);
				}
				
				if (!region.isEmpty()) {
					currentListing.setRegion(region);
				}
				
				if (zip != null) {
					currentListing.setZip(zip);
				}
				
				inAddress = false;
			}
			
			if (line.matches(regexAddress)) {
				inAddress = true;
			}
			
			if (inCondition) {
				String condition = line.replaceFirst("<td>(.*?)</td>", "$1");
				if (condition.equals("New")) {
					currentListing.setNewMachine("1");
				}
				inCondition = false;
			}
			
			if (line.matches(regexCondition)) {
				inCondition = true;
			}
			
			if (inPurchaseType) {
				String pt = line.replaceFirst("<td>(.*?)</td>", "$1");
				// this is most likely an unfinished auction, remove model name to not to save the record
				if (pt.equals("Auction")) {
					currentListing.setManName("This is an auction");
					currentListing.setModelName(null);
					break;
				}
				inPurchaseType = false;
			}
			
			if (line.matches(regexPurchaseType)) {
				inPurchaseType = true;
			}
		}
	}

	@Override
	protected URL modifyUrl(URL originalUrl) {
		try {
			return new URL(originalUrl + "?p=" + status.page);
		} catch (MalformedURLException e) {
			logger.severe("Failed to modify URL: " + originalUrl + "?p=" + status.page + " | " + e.getMessage());
			return null;
		}
	}
}
