package qubase.suite;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Bauportal extends Crawler {

	public Bauportal() {
		super();
		name = "bau-portal";
		
		try {
			siteMapUrl = new URL("http://www.bau-portal.com/");
		} catch (MalformedURLException e) {
			logger.severe("Failed to init siteMapUrl: [http://www.bau-portal.com/] " + e.getMessage());
		}
		statusFile = name + ".status";
		//this will not be taken into consideration when reporting and stopping crawler
		error = "errorNoReport=";
	}
	
	@Override
	protected void parseSiteMap(String input) {
		String[] lines = input.split("\\r?\\n");
		for (String lineIn : lines) {
			String line = lineIn.trim();
			if (line.matches("^.*<div class='category_tree_row'>.*$")) {
				Pattern pattern = Pattern.compile("<a\\s*href='/(gebraucht/[^/]+?/[0-9]+/)'.*?>([^<]+)<");
		        Matcher  matcher = pattern.matcher(line);
		        
		        while (matcher.find()) {
		        	URL link = null;
		        	try {
			        	link = new URL(siteMapUrl + matcher.group(1));
			        	if (!blacklist.contains(link)) {
							addToSiteMap(new SiteMapLocation(link, matcher.group(2)));
			        	}
		        	} catch (MalformedURLException e) {
						logger.severe("Failed to parse URL: " + link);
					}
		        }
			}
		}
	}

	@Override
	protected void parseList(String input) {
		String[] lines = input.split("\\r?\\n");
		String aRegexp = "^.*<a href=\"/(details/[^/]+/[^/]+/[^/]+/)\"\\s*class=\"link_offers_element\".*?>.*$";
		for (String lineIn : lines) {
			String line = lineIn.trim();
			String nextRegex = "^.*<div\\s*class=\"paginatorPosition\">[0-9]+\\s*-\\s*<span\\s*id=\"paginatorEndPosition\">([0-9]+)</span>\\s*von\\s*([0-9]+)</div>.*$";
			if (!line.replaceAll(nextRegex, "$1").equals(line.replaceAll(nextRegex, "$2"))) {
				status.nextPageAvailable = true;
			}
			
			if (line.matches(aRegexp)) {
				String link = siteMapUrl.toString() + line.replaceAll(aRegexp, "$1");
				try {
					URL url = new URL(link);
					if (!status.list.contains(url)) {
						status.list.add(url);
					}
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
			currentListing.setUrl(status.list.get(status.pagePosition).toString());
		} catch (Exception e) {
			//ignore, this is a test call
		}
		
		currentListing.setDate(new SimpleDateFormat("yyyy-MM-dd").format(new Date()));
		
		String[] lines = input.split("\\r?\\n");
		
		String regexBrand = "^.*?<span\\s*itemprop=\"brand\">(.*?)</span>.*$"; //parse brand
		String regexModel = "^.*?<span\\s*itemprop=\"model\">(.*?)</span>.*$"; //parse model name
		String regexYear = "^.*?<td>Baujahr</td><td>([0-9]+)</td>.*$"; //parse year of manufacture
		String regexCurrPrice = "^.*?<span\\s*itemprop=\"price\">([0-9,\\.]*)\\s*([A-Z]*)</span>.*$"; //parse currency and price;
		String regexHours = "^.*?<td>Betriebsstunden</td><td>(.*?)</td>.*$"; //parse machine hours
		String regexZipRegionCountry = "^.*?<div class='(separated|highlighted)'>.*?</div>(<div>.*?</div>){0,}(<div>[-A-Za-z0-9]*\\s*.*?</div>)(<div>.*?</div>)$";
		String regexCategory = "<ul class=\"breadcrumb_detail\".*?Startseite.*?&nbsp;>&nbsp;.*?&nbsp;>&nbsp;.*?<a.*?title=\"(.*?)\"\\s*href.*?>.*$";
		
		boolean inH1 = false;
		boolean zipRegionCountryDone = false;
		for (String lineIn : lines) {
			String line = lineIn.trim();
			
			if (line.matches("<h1\\s*class=\"detailtitle\">")) {
				inH1 = true;
			}
			
			if (line.matches("</h1>") && inH1) {
				inH1 = false;
			} 
			
			if (line.matches(regexBrand) && inH1) {
				currentListing.setManName(line.replaceAll(regexBrand, "$1"));
			}
			
			if (line.matches(regexModel) && inH1) {
				currentListing.setModelName(line.replaceAll(regexModel, "$1"));
			}
			
			if (line.matches(regexYear)) {
				currentListing.setYear(line.replaceAll(regexYear, "$1"));
			}
			
			if (line.matches(regexCurrPrice)) {
				currentListing.setPrice(line.replaceAll(regexCurrPrice, "$1"));
				currentListing.setCurrency(line.replaceAll(regexCurrPrice, "$2"));
			}
			
			if (line.matches(regexHours)) {
				currentListing.setCounter(line.replaceAll(regexHours, "$1"));
			}
			
			if (line.matches(regexZipRegionCountry) && !zipRegionCountryDone) {
				String zip = line.replaceAll(regexZipRegionCountry, "$3").replaceAll("<div>([-A-Za-z0-9]*)(\\s?)([A-Za-z]*[0-9]+[A-Za-z]*\\s|[A-Z]{1,3}\\s)?\\s*(.*?)</div>", "$1$2$3").trim();
				String region = line.replaceAll(regexZipRegionCountry, "$3").replaceAll("<div>([-A-Za-z0-9]*)(\\s?)([A-Za-z]*[0-9]+[A-Za-z]*\\s|[A-Z]{1,3}\\s)?\\s*(.*?)</div>", "$4").trim();
				String country = line.replaceAll(regexZipRegionCountry, "$4").replaceAll("<div>(.*?)</div>", "$1").trim();
				
				if (zip != null && !zip.isEmpty()) {
					currentListing.setZip(zip);
				}
				
				if (region != null && !region.isEmpty()) {
					currentListing.setRegion(region);
				}
				
				if (country != null && !country.isEmpty()) {
					currentListing.setCountry(country);
				}
				
				zipRegionCountryDone = true;
			}
			
			if (line.matches(regexCategory)) {
				currentListing.setCatLang("DE");
				currentListing.setCategory(line.replaceAll(regexCategory, "$1"));
			}
		}
	}
	
	protected URL modifyUrl(URL originalUrl) {
		String url = originalUrl.toString() + "netgross/1/page/" + status.page + "/sort/DESC/sortby/date/";
		try {
			return new URL(url);
		} catch (MalformedURLException e) {
			logger.severe("Failed to parse URL: [" + url + "] " + e.getMessage());
			return originalUrl;
		}
	}

}
