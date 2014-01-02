package qubase.suite;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.TagNode;

public class Machineryzone extends Crawler {
	
	//save already loaded links when traversing sitemap to not to visit the same more times
	//e.g. backhoe loaders are present on more than one spot
	private HashSet<URL> personalBlacklist = new HashSet<URL>();
	
	private String currentCategory = null;
	
	public Machineryzone() {
		super();
		name = "machineryzone";
		
		try {
			siteMapUrl = new URL("http://www.machineryzone.eu/");
		} catch (MalformedURLException e) {
			logger.severe("Failed to init siteMapUrl: [http://www.machineryzone.eu/] " + e.getMessage());
		}
		statusFile = name + ".status";
	}

	@Override
	protected void parseSiteMap(String input) {
		//TODO parse this one instead of the recursive solution
		//http://www.machineryzone.eu/rubriques.asp?action=consulter
		
		String[] lines = input.split("\\r?\\n");
		
		for (String lineIn : lines) {
			String line = lineIn.trim();
			if (line.matches("^.*<div class=\"pac_MenuRub\"\\s*>.*$")) {
				
				try {
					String html = line;
					
					HtmlCleaner cleaner = new HtmlCleaner();
					TagNode root = cleaner.clean(html);
					
					Object[] level1links = root.evaluateXPath("//div/h3/a");
					
					for (Object level1link : level1links) {
						URL level1url = new URL(siteMapUrl + ((TagNode)level1link).getAttributeByName("href").replaceFirst("/", ""));
						
						traverse(level1url, null);
						personalBlacklist.clear();//this was needed only for traversing, clear it for the next traversing
					}
					
				} catch (Exception e) {
					logger.severe("Failed to parse site map: " + e.getMessage());
				}
			}
		}
	}
	
	private void traverse(URL url, String name) throws MalformedURLException  {
		String html = loadCustomPage(url);
		
		//check if this is a list, if not, go deeper
		//if so, save the URL unless it's on blacklist
		if (html.indexOf("<h2 class=\"nbResultats\">") > 0) {
			addToSiteMap(new SiteMapLocation(url, name));
			return;
		}
		
		String[] lines = html.split("\\r?\\n");
		for (String lineIn : lines) {
			String line = lineIn.trim();
			if (line.matches("^</script><td><table\\s*id=\"arianeHaut\".*$")) {
				Pattern pattern = Pattern.compile("<a href=/(used/1/[^\\.]+\\.html) class=\"lienSsRub\">([^<]+?)</a>");
				Matcher matcher = pattern.matcher(line);
		        
		        while (matcher.find()) {
		        	URL nextUrl = new URL(siteMapUrl + matcher.group(1));
		        	if (blacklist.contains(nextUrl) || personalBlacklist.contains(nextUrl)) {
		        		continue;
		        	} else {
		        		personalBlacklist.add(nextUrl);
		        		traverse(nextUrl, matcher.group(2));
		        	}
		        }
			}
		}
	}

	@Override
	protected void parseList(String input) {
		String[] lines = input.split("\\r?\\n");
		for (String lineIn : lines) {
			String line = lineIn.trim();
			
			//parse the category name here, use it in parseListing later
			String regexCategory = "^.*?<td\\s*id=\"arianeHautLiensNav\"><a\\s*href=\"/\">Home</a>.*?:\\s*<a\\s*href=[^>]+>([^>]+)</a></td>.*$";
			if (line.matches(regexCategory)) {
				currentCategory = line.replaceAll(regexCategory, "$1");
			}
			
			if (line.matches("<div><span class=\"right rech-tri\">.*$")) {
				
				if (line.indexOf("next page") > 0) {
					status.nextPageAvailable = true;
				}
				
				Pattern pattern = Pattern.compile("<a href=\"/(used/[^\\.]+?\\.html)\" data-id=\"[0-9]*\">");
		        Matcher  matcher = pattern.matcher(line);
		        
		        while (matcher.find()) {
		        	try {
						list.add(new URL(siteMapUrl + matcher.group(1)));
					} catch (MalformedURLException e) {
						logger.severe("Failed to parse URL: " + siteMapUrl + matcher.group(1));
					}
		        }
			}
		}
	}

	@Override
	protected void parseListing(String input) {
		currentListing = new Listing();
		
		String url = null;
		try {
			url = list.get(status.pagePosition).toString();
			currentListing.setUrl(url);
		} catch (Exception e) {
			//ignore, this is a test call
		}
		
		//the value for the category is loaded in the parseList, because its always the same for the whole list
		currentListing.setCategory(currentCategory);
		currentListing.setCatLang("EN");
		
		String[] lines = input.split("\\r?\\n");
		
		String regexMake = "^.*?<b>Make</b>.*?class=\"droite\\s*[a-zA-Z]*\"><a\\s*href=\"[^\"]+\">(.*?)</a>.*$"; //parse brand
		String regexModel = "^.*?<b>Model</b>.*?class=\"droite\\s*[a-zA-Z]*\">(.*?)</td>.*$"; //parse model name
		String regexYear = "^.*?<b>Year</b>.*?class=\"droite\\s*[a-zA-Z]*\">([0-9]+)</td>.*$"; //parse year of manufacture
		String regexHours = "^.*?<b>Hours</b>.*?class=\"droite\\s*[a-zA-Z]*\">([0-9\\.,]+) h</td>.*$"; //parse op. hours
		String regexSerial = "^.*?<b>Serial&nbsp;number</b>.*?class=\"droite\\s*[a-zA-Z]*\">(.*?)</td>.*$"; //parse serial nr.
		String regexPrice = "^.*?<b>Price\\s*excl\\.\\s*VAT</b>&nbsp;:\\s*</td><td\\s*class=\"droite\\s*[a-zA-Z]*\">([0-9\\.,]+).*$"; //parse price
		String regexCurrency = "^.*?<option\\s*value=[A-Z]{3}\\s*selected\\s*>([A-Z]{3})</option>.*$"; //parse currency
		String regexLocation = "^.*?<b>Location</b>.*?class=\"droite\\s*[a-zA-Z]*\">(.*?)</td>.*$"; //parse country
		String regexDate = "^.*?<div\\s*class=\"enteteCadre\">.*\\s([0-9]{1,2}/[0-9]{1,2}/[0-9]{4})</div>.*$"; //parse date
		String regexAddress = "^.*?<b>Address</b>.*?class=\"droite\\s*[a-zA-Z]*\">(.*?)</td>.*$"; //parse address
		
		for (String lineIn : lines) {
			String line = lineIn.trim();
			
			if (line.matches(regexMake)) {
				String val = line.replaceAll(regexMake, "$1");
				if (!isNotAvailableValue(val)) {
					currentListing.setManName(val);
				}
			}
			
			if (line.matches(regexModel)) {
				String val = line.replaceAll(regexModel, "$1");
				if (!isNotAvailableValue(val)) {
					currentListing.setModelName(val);
				}
			}
			
			//this will never be N/A due to the regex
			if (line.matches(regexYear)) {
				currentListing.setYear(line.replaceAll(regexYear, "$1"));
			}
			
			//this will never be N/A due to the regex
			if (line.matches(regexHours)) {
				currentListing.setCounter(line.replaceAll(regexHours, "$1"));
			}
			
			if (line.matches(regexSerial)) {
				String val = line.replaceAll(regexSerial, "$1");
				if (!isNotAvailableValue(val)) {
					currentListing.setSerial(val);
				}
			}
			
			//this will never be N/A due to the regex
			if (line.matches(regexPrice)) {
				currentListing.setPrice(line.replaceAll(regexPrice, "$1"));
			}
			
			//this will never be N/A due to the regex
			if (line.matches(regexCurrency)) {
				currentListing.setCurrency(line.replaceAll(regexCurrency, "$1"));
			}
			
			if (line.matches(regexLocation)) {
				String val = line.replaceAll(regexLocation, "$1");
				if (!isNotAvailableValue(val)) {
					currentListing.setCountry(val);
				}
			}
			
			if (line.matches(regexDate)) {
				SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
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
			
			if (line.matches(regexAddress)) {
				String[] address = line.replaceAll(regexAddress, "$1").split("<br>",-1);
				String regionZipCountry = null;
				if (address.length == 2) {
					regionZipCountry = address[1];
				} else if (address.length == 1) {
					regionZipCountry = address[0];
				} else if (address.length > 2) {
					regionZipCountry = address[1];
					logger.warning("Address has more than 2 lines: " + url);
				}
				
				if (regionZipCountry != null && !regionZipCountry.isEmpty()) {
					/*
					 * try to parse the address to zip and region
					 * specification is following:
					 *	1. zip can contain big letters, numbers and dash
					 *	2. zip consists of 1 to 3 parts separated by a blank character
					 *	3. zip and region can be in arbitrary order, e.g. Dungannon BT71 6NL or even BT71 6NL Dungannon
					 *	4. zip and region are followed by the country and are separated from it by a "blank dash blank" char. sequence
					 *	5. the part after the dash and dash itself can be ignored, country is parsed from a different source
					 *	6. zip must be max 9 chars long excluding blank chars
					 *	7. zip must either be followed by the end of the string or by a blank character
					 *
					 * Some of the addresses are formatted differently (there are user defined new lines, these are ignored)
					 */
					
					String regionZip = regionZipCountry.replaceAll("^(.*?)\\s-\\s.*$", "$1");
					String zip = regionZip.replaceAll("^.*?([-A-Z0-9]{2,3})(\\s)?([-A-Z0-9]{2,3})?(\\s?)([A-Z0-9]{2,3})($|\\s.*$)", "$1$2$3$4$5");
					String region = null;
					
					//no zip recognized, apparently it is not there
					if (zip.equals(regionZip) || regionZip.startsWith(". ")) {
						zip = null;
						region = (regionZip.startsWith(". ")) ? regionZip.replaceFirst("\\. ", "").trim() : regionZip;
					} else {
						region = regionZip.replaceAll(zip, "").trim();
					}
					
					//if the region still contains some numbers, it's wrong, rather leave it empty
					//it was parsed in a wrong way most probably, region wouldn't normally contain numbers
					if (region.matches("([0-9]+\\s.*|.*\\s[0-9]+|.*[0-9]+.*)")) {
						zip = null;
						region = null;
					}
					
					if (zip != null && !zip.isEmpty()) {
						currentListing.setZip(zip);
					}
					
					if (region != null && !region.isEmpty()) {
						currentListing.setRegion(region);
					}					
				}
			}
		}
	}

	@Override
	protected URL modifyUrl(URL originalUrl) {
		String url = originalUrl.toString().replaceAll("/used/[0-9]+/", "/used/" + status.page + "/");
		try {
			return new URL(url);
		} catch (MalformedURLException e) {
			logger.severe("Failed to parse URL: [" + url + "] " + e.getMessage());
			return originalUrl;
		}
	}
	
	private boolean isNotAvailableValue(String val) {
		return val.trim().equals("N/A");
	}

}
