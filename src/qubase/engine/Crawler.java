package qubase.engine;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Random;
import java.util.logging.Logger;

public class Crawler {
	private int id;
	private String name = null;
	private int interval = 0;
	private int random = 0;
	private long sleep = 0;
	private int weight = 0;
	private URL url = null;
	private int ttl = 0;
	
	private int errorsInRow = 0;
	private ArrayList<String> errors = new ArrayList<String>();
	private int errorLimit = 10;
	
	private boolean isPublished = true;
	
	private static final Logger logger = Logger.getLogger(Crawler.class.getName());
	
	public void loop(String separator) throws Exception {
		int maxRequestCount = weight;
		//generate random count if random is defined
		//generated integer will be (1..random + 1)
		if (random > 0) {
			Random randomGenerator = new Random();
			maxRequestCount = randomGenerator.nextInt(random) + 1;
			logger.finest("[" + id + "] Generating random request count: " + maxRequestCount);
		}
		
		logger.info("[" + id + "] Starting request loop for crawler: " + name + " [request count: " + maxRequestCount + "]");
		int successfulRequestCount = 0;
		int requestCount = 0;
		while (requestCount < maxRequestCount) {
			requestCount++;

			Record record = null;
			try {
				record = requestRecord(separator);
			} catch (Exception e) {
				logger.warning(e.getMessage());
			}
			
			if (!isPublished) {
				logger.severe("[" + id + "] Stopping the crawler, too many errors");
				break;
			}
			
			if (record != null && !record.isDuplicate()) {
				if (record.exists()) {
					logger.finest("[" + id + "] Record exists: " + record.toString());
					record.setDuplicate(true);
				}
				
				if (!record.isDuplicate()) {
					try {
						record.save();
						successfulRequestCount++;
						logger.finest("[" + id + "] Save succeeded: " + record.toString());
					} catch (Exception e) {
						logger.severe("[" + id + "] Failed to save record: " + e.getMessage());
					}
				}
			}
			
			if (sleep > 0 && !record.isDuplicate()) {
				Thread.sleep(sleep);
			}
		}
		
		logger.finest("[" + id + "] Request loop finished for crawler: " + name + ", [success ratio: " + successfulRequestCount + "/" + maxRequestCount + "]");
	}
	
	private Record requestRecord(String separator) throws Exception {
		logger.fine("[" + id + "] Starting remote request.");
		Record record = new Record();
		record.setTtl(ttl);
		record.addProperty("portalId", new Integer(id).toString());
		
        BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream(), "UTF8"));
        String line;
        boolean readOne = false;
        while ((line = in.readLine()) != null) {
        	readOne = true;
        	
        	//this is a duplicate
        	//we will not restart the error counter here, because there might not even be a request to the portal
        	if (line.equals("duplicate")) {
        		logger.finer("[" + id + "] Duplicate responded.");
        		record.setDuplicate(true);
        		return record;
        	}
        	
        	//this is not a record, error occured
        	if (line.startsWith("error")) {
        		logger.severe("[" + id + "] Error: " + line);
        		registerError(line);
        		return null;
        	}
        	
        	parseResponseLine(record, line, separator);
        }
        
        
        if (!readOne) {
        	logger.severe("[" + id + "] Nothing to parse. Line empty.");
        	return null;
        } else {
        	logger.finest("[" + id + "] Remote request finished.");
        }
        
		return record;
	}
	
	public void parseResponseLine(Record record, String line, String separator) throws Exception {
		//clear the errors, the series of errors is interrupted by a legitimate response
		//restart the counter
		errors.clear();
		errorsInRow = 0;
		
		String[] crumbs = line.split(separator);
    	logger.finest("[" + id + "] Parsing response line: " + line);
    	for (int i = 0; i < crumbs.length; i++) {
    		
    		String key = null;
    		String value = null;
    		try {
    			key = crumbs[i].substring(0, crumbs[i].indexOf("="));
    			value = crumbs[i].substring(key.length() + 1);
    		} catch (Exception e) {
    			throw new Exception("[" + id + "] Cannot parse response line: " + line);
    		}
    		
        	if (key.isEmpty()) {
        		logger.severe("[" + id + "] Failed to parse key: " + crumbs[i]);
        	} else if (value.isEmpty()) {
        		logger.finest("[" + id + "] Failed to parse value: " + crumbs[i]);
        	} else {
        		record.addProperty(key, value);
        	}
    	}
	}
	
	private void registerError(String error) {
		errorsInRow++;
		errors.add((new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())) + " " + error);
		
		if (errorsInRow == errorLimit) {
			try {
				Controller.unpublishCrawler(id);
				LecturaCrawlerEngine.reportStopping(this);
				errors.clear();
			} catch (Exception e) {
				logger.severe("Failed to unpublish crawler: " + e.getMessage());
			} finally {
				isPublished = false;
			}
		}
	}
	
	/**
	 * checks whether the mandatory properties are set
	 * this includes: id, name, url and one of the both weight or random.
	 * @return true if object is configured properly, false otherwise
	 */
	public boolean isConfigured() {
		return id > 0 && name != null && (weight != 0 || random != 0) && url != null;
	}

	public int getId() {
		return id;
	}
	
	public void setId(int id) {
		this.id = id;
	}
	
	public String getName() {
		return name;
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	public int getInterval() {
		return interval;
	}
	
	public void setInterval(int interval) {
		this.interval = interval;
	}
	
	public int getRandom() {
		return random;
	}

	public void setRandom(int random) {
		this.random = random;
	}

	public long getSleep() {
		return sleep;
	}

	public void setSleep(long sleep) {
		this.sleep = sleep;
	}
	
	public int getWeight() {
		return weight;
	}

	public void setWeight(int weight) {
		this.weight = weight;
	}
	
	public URL getUrl() {
		return url;
	}

	public void setUrl(URL url) {
		this.url = url;
	}

	public int getTtl() {
		return ttl;
	}

	public void setTtl(int ttl) {
		this.ttl = ttl;
	}

	public int getErrorLimit() {
		return errorLimit;
	}

	public void setErrorLimit(int errorLimit) {
		this.errorLimit = errorLimit;
	}

	public boolean isPublished() {
		return isPublished;
	}

	public ArrayList<String> getErrors() {
		return errors;
	}
	
	public void addError(String error) {
		errors.add(error);
	}
}
