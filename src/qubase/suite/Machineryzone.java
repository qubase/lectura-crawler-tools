package qubase.suite;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.TagNode;

public class Machineryzone extends Crawler {
	
	//save already loaded links when traversing sitemap to not to visit the same more times
	//e.g. backhoe loaders are present on more than one spot
	private HashSet<URL> personalBlacklist = new HashSet<URL>();
	
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
		// TODO Auto-generated method stub

	}

	@Override
	protected void parseListing(String input) {
		// TODO Auto-generated method stub

	}

	@Override
	protected String generateListUrlSuffix() {
		// TODO Auto-generated method stub
		return null;
	}

}
