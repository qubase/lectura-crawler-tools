package qubase.suite;

public class CrawlerFactory {
	public static Crawler createCrawler(String portalName) throws IllegalArgumentException {
		if (portalName.equals("forkliftaction")) {
			return new Forkliftaction();
		} else if (portalName.equals("bau-portal")) {
			return new Bauportal();
		} else if (portalName.equals("machineryzone")) {
			return new Machineryzone();
		} else if (portalName.equals("agriaffaires")) {
			return new Agriaffaires();
		} else if (portalName.equals("forklift")) {
			return new Forklift();
		} else if (portalName.equals("landwirt")) {
			return new Landwirt();
		} else if (portalName.equals("autotrader")) {
			return new Autotrader();
		} else if (portalName.equals("resaleweekly")) {
			return new Resaleweekly();
		} else if (portalName.equals("earthmovers")) {
			return new Earthmovers();
		} else {
			throw new IllegalArgumentException("Invalid portal name: " + portalName);
		}
	}
}
