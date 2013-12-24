package qubase.suite;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.util.EntityUtils;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;

abstract public class Crawler {
	protected String name;
	protected Logger logger;
	protected Listing currentListing;
	
	protected URL siteMapUrl;
	protected String statusFile;
	
	protected ArrayList<URL> list = new ArrayList<URL>();
	protected HashSet<URL> blacklist = new HashSet<URL>();
	protected Status status = new Status();
	
	protected Integer ttl = null;
	protected Boolean useProxy = null;
	protected String logLevel = null;
	
	protected int retry = 0;
	protected long retryAfter = 0;
	protected boolean firstEmptyList = true;
	
	public Crawler() {
		logger = Logger.getLogger(this.getClass().getName());
	}
	
	protected interface Parser {
		public void parse(String input);
	}
	
	//callback helpers
	protected Parser siteMapParser = new Parser() {
		public void parse(String input) {
			parseSiteMap(input);
		}
	};
	
	protected Parser listParser = new Parser() {
		public void parse(String input) {
			parseList(input);
		}
	};
	
	protected Parser listingParser = new Parser() {
		public void parse(String input) {
			parseListing(input);
		}
	};
	
	abstract protected void parseSiteMap(String input);
	abstract protected void parseList(String input);
	abstract protected void parseListing(String input);
	/**
	 * Should modify the list URL to implement the status.page into it to navigate the pager correctly
	 * @param originalUrl
	 * @return
	 */
	abstract protected URL modifyUrl(URL originalUrl);
	
	public String getItem() throws Exception {
		logger.finest("Item request initiated.");
		
		String response = null;
		String error = "error=";
		
		if (!status.init) {
			if (!status.load(statusFile)) {
				status.init = true;
			} //else the init was loaded from the serialized status
		}
		
		
		//clear the sitemap and reset if we are at the end of portal
		//if the last listing couldn't be loaded, the status was not reset
		if (!status.siteMap.isEmpty()) {
			if (status.siteMap.size() == status.siteMapIndex) {
				status.reset().save(statusFile);
			}
		}
		
		//load the sitemap if needed
		if (status.siteMap.isEmpty()) {
			try {
				logger.finest("Loading sitemap: " + siteMapUrl);
				loadPage(siteMapUrl, siteMapParser);
				
				if (status.siteMap.isEmpty()) {
					response = "Failed to load the sitemap - Empty";
					logger.severe(response);
					return error + response;
				} else {
					status.save(statusFile);
					int size = status.siteMap.size();
					logger.info("Sitemap loaded successfuly: " + size + " items");
					
					for (int i = 0; i < size; i++) {
						SiteMapLocation sml = status.siteMap.get(i);
						logger.finest("Sitemap item #" + i + " : [" + sml.name + "] " + sml.url);
					}
				}
				
			} catch (Exception e) {
				response = "Failed to load the sitemap: " + e.getMessage();
				logger.severe(response);
				return error + response;
			}
		}
		
		//load the list if needed
		if (list.isEmpty() && !status.siteMap.isEmpty()) {
			try {
				String coordinates = "[" + status.siteMapIndex + ", " + status.page + ", " + status.pagePosition + "]";
				URL listUrl = modifyUrl(status.siteMap.get(status.siteMapIndex).url);
				logger.finest("Loading list: " + listUrl + " " + coordinates);
				loadPage(listUrl, listParser);
				//if after loading the page list is still empty even after second attempt, we should probably try next category, we are out of range of the pager 
				//this can happen when loading an older status and the page structure changed in the meantime
				if (list.isEmpty()) {
					response = "Failed to load list - Empty: " + listUrl + " " + coordinates;
					logger.warning(response);
					if (firstEmptyList) {
						//this is a first empty list, give it one more chance and go to the next page
						status.nextPage().save(statusFile);
						firstEmptyList = false;
					} else {
						//this is the second time in a row we've received an empty list, go to the next category this time
						status.nextCategory().save(statusFile);
						firstEmptyList = true;
					}
					return error + response;
				}
			} catch (Exception e) {
				response = "Failed to load the list: [" + status.siteMap.get(status.siteMapIndex).url + "] " + e.getMessage();
				logger.severe(response);
				status.nextPage().save(statusFile);
				return error + response;
			}
		}
		
		//load the listing
		if (!list.isEmpty()) {
			boolean isError = false;
			try {
				String coordinates = "[" + status.siteMapIndex + ", " + status.page + ", " + status.pagePosition + "]";
				URL listingUrl = list.get(status.pagePosition);
				logger.finest("Loading listing: " + listingUrl + " " + coordinates);
				loadPage(listingUrl, listingParser);
				if (currentListing == null) {
					response = "Listing null: " + list.get(status.pagePosition);
					logger.severe(response);
					isError = true;
				} else {
					//this is where it comes when everything went right
					response = currentListing.toString();
				}
			} catch (Exception e) {
				response = "Failed to load the listing: " + list.get(status.pagePosition);
				logger.severe(response);
				isError = true;
			} finally {
				if (status.pagePosition == list.size() - 1) {
					if (status.nextPageAvailable) {
						status.nextPage().save(statusFile);
						list.clear();
					} else {
						status.nextCategory().save(statusFile);
						list.clear();
						if (status.siteMap.size() == status.siteMapIndex) {
							status.reset().save(statusFile);
						}
					}
				} else {
					status.pagePosition++;
				}
			}
			
			return (isError) ? error + response : response;
		} else {
			response = "List empty, can not load listing.";
			logger.severe(response);
			return error + response;
		}
	}
	
	protected void loadPage(URL url, Parser parser) {
		
		if (parser.equals(listingParser)) {
			if (listingExists(url)) {
				currentListing = new Listing();
				currentListing.setDuplicate(true);
				//avoid the request
				return;
			}
		}
		
		CloseableHttpResponse response = null;
		int attempts = 0;
		int retry_ = (retry == 0) ? 1 : retry;
		boolean responseOk = false;
		while (attempts < retry_ && !responseOk) {
			
			if (attempts > 0) {
				try {
					logger.finest("Going to sleep for " + retryAfter + "ms " + url);
					Thread.sleep(retryAfter);
				} catch (InterruptedException e) {
					//ignore
					logger.severe("Failed to sleep for " + retryAfter + "ms " + url);
				}
				logger.info("Retrying to get page after " + retryAfter + "ms : Attempt " + new Integer(attempts + 1) + "/" + retry + " " + url);
			}
			
			try {	
				response = LecturaCrawlerSuite.getResponse(url, useProxy);
			} catch (Exception e) {
				logger.severe("Failed to retrieve response: " + e.getMessage());
			}
			
			responseOk = (response != null && response.getStatusLine().getStatusCode() == 200);
			attempts++;
		}
		
        try {
            HttpEntity entity = response.getEntity();

            //if the status code is not OK, report a problem
            if (response.getStatusLine().getStatusCode() != 200) {
            	Header[] headers = response.getAllHeaders();
            	String headersStr = response.getStatusLine().toString();
		        for (int i = 0; i < headers.length; i++) {
		            headersStr += " | " + headers[i];
		        }
		        
		        logger.warning("Status code not OK: [" + url.toString() + "] " + headersStr);
		        logger.warning(EntityUtils.toString(entity));
            }
            
            if (entity != null) {
                parser.parse(EntityUtils.toString(entity));
            }
            
        } catch (Exception e) {
        	logger.severe("Failed to load page: [" + url.toString() + "] " + e.getMessage());
        } finally {
        	try {
				response.close();
			} catch (IOException e) {
				logger.severe("Failed to close HTTP response: " + e.getMessage());
			}
        }
	}
	
	protected String loadCustomPage(URL url) {
		logger.info("Loading custom page: " + url);
		CloseableHttpResponse response = null;
		int attempts = 0;
		int retry_ = (retry == 0) ? 1 : retry;
		boolean responseOk = false;
		while (attempts < retry_ && !responseOk) {
			
			if (attempts > 0) {
				try {
					logger.finest("Going to sleep for " + retryAfter + "ms " + url);
					Thread.sleep(retryAfter);
				} catch (InterruptedException e) {
					//ignore
					logger.severe("Failed to sleep for " + retryAfter + "ms " + url);
				}
				logger.info("Retrying to get page after " + retryAfter + "ms : Attempt " + new Integer(attempts + 1) + "/" + retry + " " + url);
			}
			
			try {	
				response = LecturaCrawlerSuite.getResponse(url, useProxy);
			} catch (Exception e) {
				logger.severe("Failed to retrieve response from custom page: " + e.getMessage());
			}
			
			responseOk = (response != null && response.getStatusLine().getStatusCode() == 200);
			attempts++;
		}
		
        try {
            HttpEntity entity = response.getEntity();

            //if the status code is not OK, report a problem
            if (response.getStatusLine().getStatusCode() != 200) {
            	Header[] headers = response.getAllHeaders();
            	String headersStr = response.getStatusLine().toString();
		        for (int i = 0; i < headers.length; i++) {
		            headersStr += " | " + headers[i];
		        }
		        
		        logger.warning("Status code not OK: [" + url.toString() + "] " + headersStr);
		        logger.warning(EntityUtils.toString(entity));
            }
            
            if (entity != null) {
                return EntityUtils.toString(entity);
            } else {
            	return null;
            }
            
        } catch (Exception e) {
        	logger.severe("Failed to load custom page: [" + url.toString() + "] " + e.getMessage());
        	return null;
        } finally {
        	try {
				response.close();
			} catch (IOException e) {
				logger.severe("Failed to close HTTP response: " + e.getMessage());
			}
        }
	}
	
	private boolean listingExists(URL url) {
		DB db = LecturaCrawlerSuite.getDB();
		DBCollection collection = db.getCollection("listings");
		
		DBCursor cursor = collection.find(new BasicDBObject("url", url.toString()));;
		int cnt = cursor.count();
		cursor.close();
		return cnt > 0;
	}
	
	public void configureLogger(String level, int ttl, boolean debug) {
		if (name == null) return;
		
		Level logLevel = LecturaCrawlerSuite.convertLogLevel(level);
		
		logger.setLevel(logLevel);
		
		//disable passing the logs up to the parent handler
		logger.setUseParentHandlers(false);
		
		//if debug mode is on, enable console handler
		if (debug) {
			ConsoleHandler consoleHandler = new ConsoleHandler();
			consoleHandler.setLevel(Level.ALL);
			logger.addHandler(consoleHandler);
		}
		
		logger.addHandler(new MongoLogHandler("log." + name, ttl));
	}
	
	/**
	 * this method is for testing and debugging purpose
	 * @param url
	 */
	public void testListing(URL url) {
		loadPage(url, listingParser);
	}
	
	public void setStatus(Status status) {
		this.status = status;
	}
	
	public Status getStatus() {
		return status;
	}
	
	public Listing getCurrenListing() {
		return currentListing;
	}
	
	public Integer getTtl() {
		return ttl;
	}
	
	public void setTtl(Integer ttl) {
		this.ttl = ttl;
	}
	
	public Boolean getUseProxy() {
		return useProxy;
	}
	
	public void setUseProxy(Boolean useProxy) {
		this.useProxy = useProxy;
	}
	
	public String getLogLevel() {
		return logLevel;
	}
	
	public void setLogLevel(String logLevel) {
		this.logLevel = logLevel;
	}
	
	public String getName() {
		return name;
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	public int getRetry() {
		return retry;
	}
	
	public void setRetry(int retry) {
		this.retry = retry;
	}
	
	public long getRetryAfter() {
		return retryAfter;
	}
	
	public void setRetryAfter(long retryAfter) {
		this.retryAfter = retryAfter;
	}
	
	public void addToBlacklist(URL url) {
		blacklist.add(url);
	}
	
	/**
	 * adds the site map location only if it's unique - actually if the 'url' property is unique
	 * @param loc
	 */
	public void addToSiteMap(SiteMapLocation loc) {
		if (isUniqueSiteMapLocation(loc)) {
			status.siteMap.add(loc);
		}
	}
	
	//this is not very effective, but was easy to implement at this stage
	//and site maps will never contain more than hundreds of elements, mostly tens
	private boolean isUniqueSiteMapLocation(SiteMapLocation loc) {
		for (SiteMapLocation item : status.siteMap) {
			if (item.url.equals(loc.url)) {
				return false;
			}
		}
		return true;
	}
}
