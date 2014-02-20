package qubase.suite;

import java.net.MalformedURLException;
import java.net.URL;

public class Marketbook extends Crawler {

	public Marketbook() {
		super();
		name = "marketbook";
		
		try {
			siteMapUrl = new URL("http://www.marketbook.de/sitemap.xml");
		} catch (MalformedURLException e) {
			logger.severe("Failed to init siteMapUrl: [http://www.marketbook.de/sitemap.xml] " + e.getMessage());
		}
		statusFile = name + ".status";
	}
	
	@Override
	protected void parseSiteMap(String input) {
		String[] lines = input.split("\\r?\\n");
		String regexLink = "^\\s*<loc>(.+?)</loc>\\s*$";
		for (String lineIn : lines) {
			String line = lineIn.trim();
			if (line.matches(regexLink)) {
				String link = line.replaceAll(regexLink, "$1");
				parseSiteMapCustom(link);
			}
		}
	}
	
	private void parseSiteMapCustom(String link) {
		
		URL url = null;
		try {
			url = new URL(link);
		} catch (MalformedURLException e) {
			logger.warning("Failed to parse custom site map URL: " + link);
			return;
		}
		
		String input = loadCustomPage(url);
		String[] lines = input.split("\\r?\\n");
		String regexLink = "^\\s*<loc>(.+?)</loc>\\s*$";
		for (String lineIn : lines) {
			String line = lineIn.trim();
			if (line.matches(regexLink)) {
				String listLink = line.replaceAll(regexLink, "$1");
				if (listLink.matches(".*?http://www.marketbook.ca/list/list.aspx.+$") && !listLink.matches(".*?http://www.marketbook.ca/list/list.aspx.+pg=[0-9]+$")) {
					try {
						status.siteMap.add(new SiteMapLocation(new URL(listLink), null));
					} catch (MalformedURLException e) {
						logger.severe("Failed to parse URL: " + listLink);
					}
				}
			}
		}
	}

	@Override
	protected void parseList(String input) {
		
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
