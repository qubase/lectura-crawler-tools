package qubase.suite;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Resaleweekly extends Crawler {
	
	private String baseUrl = null;
	
	public Resaleweekly() {
		super();
		name = "resaleweekly";
		
		try {
			siteMapUrl = new URL("http://www.resaleweekly.com");
		} catch (MalformedURLException e) {
			logger.severe("Failed to init siteMapUrl: [http://www.resaleweekly.com] " + e.getMessage());
		}
		statusFile = name + ".status";
		baseUrl = "http://www.resaleweekly.com";
	}

	@Override
	protected void parseSiteMap(String input) {
		String[] lines = input.split("\\r?\\n");
		for (String lineIn : lines) {
			String line = lineIn.trim();
			if (line.matches("^.*?<select.*?name=\"manufacturer\">.*$")) {
				String options = line.replaceFirst(".*?<select.*?name=\"manufacturer\">(.*?)</select>.*$", "$1");
				Pattern pattern = Pattern.compile("<option\\svalue=\"([0-9]+)\">(.*?)</option>");
				Matcher matcher = pattern.matcher(options);
		        
		        while (matcher.find()) {
		        	String listLink = siteMapUrl + "/catalogsearch/advanced/result/?hours=&manufacturer=" + matcher.group(1) + "&model_value=&price=&year=";
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
		String aRegex = "<a href=\"([^\"]{2,})\".*?product-id=\"[0-9]+\"\\s*>";
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
			
			if (line.matches(".*nextpage\\.png.*")) {
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
		
		currentListing.setManName(status.siteMap.get(status.siteMapIndex).name);
		currentListing.setCategory("Unknown");
		currentListing.setCatLang("EN");
		
		String[] lines = input.split("\\r?\\n");
		
		String regexCompanyInfo = "<a.*?id=\"companyinfo[0-9]+\".*?>";
		boolean inCompanyInfo = false;
		String regexPrice = "<span\\sclass=\"price\">";
		boolean inPrice = false;
		String regexDate = "<strong>Listed\\sdate:\\s*</strong>";
		boolean inDate = false;
		String regexYear = "<strong>Year</strong>.*";
		boolean inYear = false;
		String regexHours = "<strong>Hours</strong>.*";
		boolean inHours = false;
		String regexSerial = "<strong>Serial\\sNumber</strong>.*";
		boolean inSerial = false;
		String regexAddress = "(.*?)<div\\sclass=\"mt5\"><strong>Postcode:\\s</strong>(.*?)</div><div\\sclass=\"mt5\"><strong>Country:\\s</strong>(.*?)</div>";
		String regexCondition = "<strong>Condition:\\s</strong>";
		boolean inCondition = false;
		
		for (String lineIn : lines) {
			String line = lineIn.trim();
			
			//model name
			if (line.matches("<title>.*</title>")) {
				String title = line.replaceFirst("<title>Used\\s(.*?)\\sfor\\ssale.*?</title>", "$1");
				String man = currentListing.getManName();
				if (title.toLowerCase().startsWith(man.toLowerCase())) {
					String model = title.substring(man.length()).trim();
					currentListing.setModelName(model);
				}
			}
			
			if (inCompanyInfo) {
				currentListing.setCompany(line.replaceFirst("^\\s*(.*?)\\s*</a>$", "$1"));
				inCompanyInfo = false;
			}
			
			if (line.matches(regexCompanyInfo)) {
				inCompanyInfo = true;
			}
			
			if (inPrice) {
				String price = line.replaceFirst("^\\s*(.*?)\\s*</span>$", "$1");
				if (!price.startsWith("Call")) {
					currentListing.setPrice(price.replaceFirst("^([0-9,]+)(\\.[0-9]{2})?.*$", "$1"));
					if (currentListing.getPrice() != null && !currentListing.getPrice().isEmpty()) {
						currentListing.setCurrency(price.replaceFirst("^.*?\\s([A-Z]{2,4})$", "$1"));
					} else if (currentListing.getPrice().isEmpty()) {
						currentListing.setPrice(null);
					}
				}
				inPrice = false;
			}
			
			if (line.matches(regexPrice)) {
				inPrice = true;
			}
			
			if (inDate) {
				String reg = "([0-9]{1,2})(th|st|rd)\\s([^0-9]{3})[^0-9]+\\s([0-9]{4}).*</span>";
				if (line.matches(reg)) {
					String dateStr = line.replaceFirst(reg, "$1 $3 $4");
					if (dateStr.matches("[0-9]\\s.*")) {
						dateStr = "0" + dateStr;
					}
					DateFormat dffrom = new SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH);
					try {
						Date res = dffrom.parse(dateStr);
						currentListing.setDate(new SimpleDateFormat("yyyy-MM-dd").format(res));
					} catch (ParseException e) {
						e.printStackTrace();
					}
					inDate = false;
					if (currentListing.getDate() == null) {
						currentListing.setDate(new SimpleDateFormat("yyyy-MM-dd").format(new Date()));
						inDate = false;
					}
				}
			}
			
			if (line.matches(regexDate)) {
				inDate = true;
			}
			
			if (inYear) {
				String year = line.replaceFirst("<span\\sclass=\"data\">(.*?)</span>", "$1");
				if (!year.equals("N/A")) {
					currentListing.setYear(year);
				}
				inYear = false;
			}
			
			if (line.matches(regexYear)) {
				inYear = true;
			}
			
			if (inHours) {
				String hours = line.replaceFirst("<span\\sclass=\"data\">(.*?)</span>", "$1");
				if (!hours.equals("N/A")) {
					currentListing.setCounter(hours);
				}
				inHours = false;
			}
			
			if (line.matches(regexHours)) {
				inHours = true;
			}
			
			if (inSerial) {
				String serial = line.replaceFirst("<span\\sclass=\"data\">(.*?)</span>", "$1");
				if (!serial.equals("N/A")) {
					currentListing.setSerial(serial);
				}
				inSerial = false;
			}
			
			if (line.matches(regexSerial)) {
				inSerial = true;
			}
			
			if (line.matches(regexAddress)) {
				String region = line.replaceFirst(regexAddress, "$1");
				String zip = line.replaceFirst(regexAddress, "$2");
				String country = line.replaceFirst(regexAddress, "$3");
				
				if (region != null && !region.isEmpty()) {
					currentListing.setRegion(region);
				}
				
				if (zip != null && !zip.isEmpty()) {
					currentListing.setZip(zip);
				}
				
				if (country != null && !country.isEmpty()) {
					currentListing.setCountry(country);
				}
			}
			
			if (inCondition) {
				String condition = line.replaceFirst("\\s*(.*?)\\s*</p>", "$1");
				currentListing.setNewMachine(condition);
				inCondition = false;
			}
			
			if (line.matches(regexCondition)) {
				inCondition = true;
			}
		}
	}

	@Override
	protected URL modifyUrl(URL originalUrl) {
		try {
			return new URL(originalUrl + "&p=" + status.page);
		} catch (MalformedURLException e) {
			logger.severe("Failed to modify URL: " + originalUrl + "&p=" + status.page + " | " + e.getMessage());
			return null;
		}
	}
}
