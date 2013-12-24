package qubase.suite;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.logging.Logger;

public class Status implements Serializable {
	private static final long serialVersionUID = -7459947783495818562L;
	public int siteMapIndex = 0;
	public int page = 1;
	public int pagePosition = 0;
	public boolean init = false;
	public boolean nextPageAvailable = false;
	public ArrayList<SiteMapLocation> siteMap = new ArrayList<SiteMapLocation>();
	private transient Logger logger = Logger.getLogger(Status.class.getName());
	
	public Status reset() {
		logger.finest("Resetting status at coordinates: [" + siteMapIndex + ", " + page + ", " + pagePosition + "]");
		siteMapIndex = 0;
		page = 1;
		pagePosition = 0;
		siteMap.clear();
		return this;
	}
	
	public Status nextPage() {
		logger.finest("Next page at coordinates: [" + siteMapIndex + ", " + page + ", " + pagePosition + "]");
		page++;
		pagePosition = 0;
		nextPageAvailable = false;//no idea if there are more pages
		return this;
	}
	
	public Status nextCategory() {
		logger.finest("Next category at coordinates: [" + siteMapIndex + ", " + page + ", " + pagePosition + "]");
		siteMapIndex++;
		page = 1;
		pagePosition = 0;
		nextPageAvailable = false;//no idea if there are more pages
		return this;
	}
	
	public boolean load(String statusFile) {
		File file = new File(statusFile);
		if (!file.exists()) return false;
		
		try {
			FileInputStream fis = new FileInputStream(statusFile);
			ObjectInputStream in = new ObjectInputStream(fis);
			Status storedStatus = (Status)in.readObject();
			in.close();
			
			siteMapIndex = storedStatus.siteMapIndex;
			page = storedStatus.page;
			pagePosition = storedStatus.pagePosition;
			init = storedStatus.init;
			nextPageAvailable = storedStatus.nextPageAvailable;
			siteMap = storedStatus.siteMap;
			
			logger.info("Status loaded with coordinates: [" + siteMapIndex + ", " + page + ", " + pagePosition + "]");
			
			return true;
		} catch(Exception e) {
			logger.severe("Failed to load status: " + e.getMessage());
			return false;
		}
	}
	
	public void save(String statusFile) throws IOException {
		ObjectOutputStream out = null;
		try {
			FileOutputStream fos = new FileOutputStream(statusFile);
			out = new ObjectOutputStream(fos);
			out.writeObject(this);
			logger.info("Status saved with coordinates: [" + siteMapIndex + ", " + page + ", " + pagePosition + "]");
		} catch(IOException e) {
			logger.severe("Failed to save status: " + e.getMessage());
		} finally {
			if (out != null) {
				out.flush();
				out.close();
			}
		}
	}
}
