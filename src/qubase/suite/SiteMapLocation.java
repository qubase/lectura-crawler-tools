package qubase.suite;

import java.io.Serializable;
import java.net.URL;

class SiteMapLocation implements Serializable {

	private static final long serialVersionUID = 1L;
	public URL url;
	public String name;
	
	public SiteMapLocation(URL url, String name) {
		this.url = url;
		this.name = name;
	}
}
