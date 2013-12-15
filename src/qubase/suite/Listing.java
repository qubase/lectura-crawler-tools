package qubase.suite;

public class Listing {
	private String modelName = null;
	private String manName = null;
	private String category = null;
	private String catLang = null;
	private String serial = null;
	private String year = null;
	private String counter = null;
	private String price = null;
	private String currency = null;
	private String country = null;
	private String region = null;
	private String zip = null;
	private String date = null;
	private String url = null;
	
	private boolean isDuplicate = false;
	
	public String getModelName() {
		return modelName;
	}
	public void setModelName(String modelName) {
		this.modelName = modelName;
	}
	public String getManName() {
		return manName;
	}
	public void setManName(String manName) {
		this.manName = manName;
	}
	public String getCategory() {
		return category;
	}
	public void setCategory(String category) {
		this.category = category;
	}
	public String getCatLang() {
		return catLang;
	}
	public void setCatLang(String catLang) {
		this.catLang = catLang;
	}
	public String getSerial() {
		return serial;
	}
	public void setSerial(String serial) {
		this.serial = serial;
	}
	public String getYear() {
		return year;
	}
	public void setYear(String year) {
		this.year = year;
	}
	public String getCounter() {
		return counter;
	}
	public void setCounter(String counter) {
		this.counter = counter;
	}
	public String getPrice() {
		return price;
	}
	public void setPrice(String price) {
		this.price = price;
	}
	public String getCurrency() {
		return currency;
	}
	public void setCurrency(String currency) {
		this.currency = currency;
	}
	public String getCountry() {
		return country;
	}
	public void setCountry(String country) {
		this.country = country;
	}
	public String getRegion() {
		return region;
	}
	public void setRegion(String region) {
		this.region = region;
	}
	public String getZip() {
		return zip;
	}
	public void setZip(String zip) {
		this.zip = zip;
	}
	public String getDate() {
		return date;
	}
	public void setDate(String date) {
		this.date = date;
	}
	public String getUrl() {
		return url;
	}
	public void setUrl(String url) {
		this.url = url;
	}
	public boolean isDuplicate() {
		return isDuplicate;
	}
	public void setDuplicate(boolean isDuplicate) {
		this.isDuplicate = isDuplicate;
	}
	
	public String toString() {
		String s = LecturaCrawlerSuite.getProperties().getProperty("separator");
		return (isDuplicate) ? "duplicate" : 
			"modelName=" + normalizeForOutput(modelName) + s +
			"manName=" + normalizeForOutput(manName) + s +
			"category=" + normalizeForOutput(category) + s +
			"catLang=" + normalizeForOutput(catLang) + s +
			"serial=" + normalizeForOutput(serial) + s +
			"year=" + normalizeForOutput(year) + s +
			"counter=" + normalizeForOutput(counter) + s +
			"price=" + normalizeForOutput(price) + s +
			"currency=" + normalizeForOutput(currency) + s +
			"country=" + normalizeForOutput(country) + s +
			"region=" + normalizeForOutput(region) + s +
			"zip=" + normalizeForOutput(zip) + s +
			"date=" + normalizeForOutput(date) + s +
			"url=" + normalizeForOutput(url);
	}
	
	private String normalizeForOutput(String in) {
		return (in == null) ? "" : in.trim();
	}
}
