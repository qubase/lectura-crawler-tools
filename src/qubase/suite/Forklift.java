package qubase.suite;

import java.net.MalformedURLException;
import java.net.URL;

public class Forklift extends Crawler {

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
		String[] lines = input.split("\\r?\\n");
		boolean inSelectBox = false;
		String optionRegexp = "<option\\s*value=\"([0-9]+),(0)\"\\s*style=\"font-weight:\\s*bold;\">([a-zA-Z0-9]+)</option>";
		String listLinkReplace = "http://www.forklift.de/de/endkunde/staplersuche.php?Bauart=$1%2C$2&sonderbit=0&reifen=*&Fabrikat=alle&antriebsart=*&masttypid=alle&tkvon=0&tkbis=100000&bhvon=0&bhbis=9000&hhvon=0&hhbis=30000&fhvon=0&fhbis=4000&baujahr=0&bjbis=2014&preisvon=0&preisbis=1000000&landid=1&plz=&entfernung=1000&hatbild=0";
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
			
			if (inSelectBox && line.matches("*.?</select>")) {
				inSelectBox = false;
				break;
			}
		}
		
		//parse cleaning machines
		input = null;
		try {
			input = loadCustomPage(new URL("http://www.forklift.de/de/indexrt.php"));
		} catch (MalformedURLException e) {
			logger.severe("Failed to parse URL: http://www.forklift.de/de/indexrt.php");
		}
		
		if (input != null) {
			
		}
		
		//parse platforms
		input = null;
		try {
			input = loadCustomPage(new URL("http://www.forklift.de/de/indexbuehne.php"));
		} catch (MalformedURLException e) {
			logger.severe("Failed to parse URL: http://www.forklift.de/de/indexbuehne.php");
		}
		
		if (input != null) {
			
		}
	}

	@Override
	protected void parseList(String input) {
		// TODO Auto-generated method stub

	}

	@Override
	protected void parseListing(String input) {
		// TODO Auto-generated method stub

	}

	@Override
	protected URL modifyUrl(URL originalUrl) {
		// TODO Auto-generated method stub
		return null;
	}

}
