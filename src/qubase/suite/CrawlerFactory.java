package qubase.suite;

public class CrawlerFactory {
	public static Crawler createCrawler(String portalName) throws IllegalArgumentException {
		if (portalName.equals("forkliftaction")) {
			return new Forkliftaction();
		} else if (portalName.equals("bau-portal")) {
			return new Bauportal();
		} else {
			throw new IllegalArgumentException("Invalid portal name: " + portalName);
		}
	}
}
