package qubase.suite;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.Properties;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.http.HttpHost;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

import com.mongodb.DB;
import com.mongodb.MongoClient;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * @author Martin Račko <info@qubase.sk>
 *
 */
public class LecturaCrawlerSuite {

	private static Properties props = new Properties();
	private static Logger logger = Logger.getLogger("qubase.suite");
	private static DB db;
	
	private static CloseableHttpClient httpClient = null;
	private static RequestConfig defaultRequestConfig;
	
	private static int connectionTimeout = 0;
	
	/**
	 * @param args
	 * @throws IOException 
	 */
	public static void main(String[] args) throws Exception {
		init();
		
		HttpServer server = HttpServer.create(new InetSocketAddress(Integer.parseInt(props.getProperty("port"))), 0);
        server.createContext("/suite", new EngineRequestHandler());
        server.setExecutor(null); // creates a default executor
        server.start();
		
//		Machineryzone b = new Machineryzone();
//		b.testListing(new URL("http://www.machineryzone.eu/used/track-excavator/4171542/doosan-dx140lc.html"));
	}	
	
	public static void init() throws Exception {
		props.load(new FileInputStream("config.properties"));
		connectionTimeout = Integer.parseInt(props.getProperty("connection-timeout"));
		if (!initMongo()) {
			String message = "Not able to authenticate to MongoDB database: " + props.getProperty("mongodb-db");
			throw new Exception(message);
		}
		configureLogger();
		configureHttpClient();
	}
	
	public static CloseableHttpResponse getResponse(URL _url, Boolean _useProxy) throws ClientProtocolException, IOException {
		
		boolean useProxy = (_useProxy == null) 
				? props.getProperty("use-proxy").equals("1")
				: _useProxy;
		CloseableHttpResponse response = null;
		HttpGet request = null;
		
		URL url = _url;
		try {
			url = encodeUrl(url);
		} catch (Exception e) {
			logger.severe("Failed to encode URL: [" + url.toString() + "] " + e.getMessage());
		}
		
		if (useProxy) {
			logger.finest("Starting request via proxy: " + url.toString());
			try {
				waitForTorPolipo();
			} catch (Exception e) {
				logger.severe("Failed to wait for Tor & Polipo: " + e.getMessage());
				return null;
			}
			
			HttpHost target = new HttpHost(url.getHost(), url.getPort(), url.getProtocol());
            HttpHost proxy = new HttpHost(props.getProperty("proxy-host"), 
            		Integer.parseInt(props.getProperty("proxy-port")), 
            		props.getProperty("proxy-protocol"));
            
            //adjust the default request configuration
            RequestConfig config = RequestConfig.copy(defaultRequestConfig)
            		.setProxy(proxy)
            		.build();
            
            String get = ((url.getPath() != null) ? url.getPath() : "")
            		+ ((url.getQuery() != null) ? "?" + url.getQuery() : "")
            		+ ((url.getRef() != null) ? "#" + url.getRef() : "");
            
            request = new HttpGet(get);
            request.setConfig(config);
            response = httpClient.execute(target, request);
		} else {
			logger.finest("Starting request: " + url);
			
			request = new HttpGet(url.toString());
			response = httpClient.execute(request);
		}
		
		return response;
	}
	
	private static URL encodeUrl(URL url) throws Exception {
		//encode the respective path portions
		String path = url.getPath();
		if (path != null) {
			String[] pathCrumbs = path.split("/", -1);
			int length = pathCrumbs.length;
			path = "";
			for (int i = 0; i < length; i++) {
				String encodedCrumb = URLEncoder.encode(pathCrumbs[i], "UTF-8");
				path += (i == length - 1) ? encodedCrumb : encodedCrumb + "/";
			}
		} else {
			path = "";
		}
		
		//encode the respective query values
		String query = url.getQuery();
		if (query != null) {
			String[] queryCrumbs = query.split("&", -1);
			int length = queryCrumbs.length;
			query = "?";
			for (int i = 0; i < length; i++) {
				String keyValuePair[] = queryCrumbs[i].split("=");
				if (keyValuePair.length == 2) {
					String encodedValue = URLEncoder.encode(keyValuePair[1], "UTF-8");
					query += (i == length - 1) ? keyValuePair[0] + "=" + encodedValue : keyValuePair[0] + "=" + encodedValue + "&";
				} else {
					query += (i == length - 1) ? queryCrumbs[i] : queryCrumbs[i] + "&";
				}
			}
		} else {
			query = "";
		}
		
		if (url.getProtocol() == null || url.getAuthority() == null) {
			throw new Exception("Protocol or authority empty: [" + url.toString() + "]");
		}
		
		String ref = (url.getRef() == null) ? "" : "#" + url.getRef();
		return new URL(url.getProtocol() + "://" 
				+ url.getAuthority()
				+ path
				+ query
				+ ref);
	}

	private static void waitForTorPolipo() throws IOException, InterruptedException {
		int interval = Integer.parseInt(props.getProperty("tor-polipo-timeout"));
		boolean ready = torPolipoRunning();
		if (ready) {
			logger.finest("Tor & Polipo are ready.");
			return;
		} else {
			int i = 1;
			//activate waiting
			boolean readyNow = false;
			while (!readyNow) {
				logger.info("Waiting for Tor & Polipo: " + i);
				Thread.sleep(interval);
				readyNow = torPolipoRunning();
				i++;
			}
		}
	}
	
	private static boolean torPolipoRunning() throws IOException, InterruptedException {
		Process process = Runtime.getRuntime().exec("ps aux");
		process.waitFor();
		
		BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
		
		String line = null;
		boolean tor = false;
		boolean polipo = false;
		while ((line = reader.readLine()) != null) {
			if (line.matches(".*/etc/init\\.d/tor restart.*")
					|| line.matches(".*/etc/init\\.d/tor restart.*")
					|| line.matches(".*/etc/init\\.d/tor stop.*")
					|| line.matches(".*/etc/init\\.d/tor reload.*")
					|| line.matches(".*/etc/init\\.d/tor force-reload.*")
					|| line.matches(".*/etc/init\\.d/polipo restart.*")
					|| line.matches(".*/etc/init\\.d/polipo stop.*")
					|| line.matches(".*/etc/init\\.d/polipo force-reload.*")) {
				logger.finer("Found line: " + line);
				return false;
			}
			
			if (line.matches(".*/usr/sbin/tor.*")) {
				logger.finest("Found Tor");
				tor = true;
			}
			
			if (line.matches(".*/usr/bin/polipo.*")) {
				logger.finest("Found Polipo");
				polipo = true;
			}
		}
		
		return tor && polipo;
	}

	static class EngineRequestHandler implements HttpHandler {
		
        public void handle(HttpExchange exchange) throws IOException {
            String query = null;
            query = exchange.getRequestURI().getQuery();
            
            if (query == null) {
            	logger.severe("No parameters defined, handler can not serve request.");
            	return;
            }
            
            String portalName = null;
            for (String param : query.split("&")) {
            	String[] crumbs = param.split("=");
            	try {
	            	if (crumbs[0].equals("portal")) {
	            		portalName = crumbs[1];
	            	}
            	} catch (IndexOutOfBoundsException ignore) {
            		//ignore
            	}
            }
            
            if (portalName == null) {
            	logger.severe("No portal name defined, handler can not serve request.");
            	return;
            }
            
            //else server request
            logger.finest("Handling request for portal name: " + portalName);
            
            Crawler crawler = CrawlerStack.getCrawler(portalName);
            
            String response = null;
            try {
            	response = crawler.getItem();
            } catch (Exception e) {
            	response = "Failed to get item: " + e.getMessage();
            	logger.severe(response);
            }
            
        	Headers headers = exchange.getResponseHeaders();
        	headers.add("Content-Type", "text/plain; charset=utf-8");
            exchange.sendResponseHeaders(200, 0);
            
            OutputStream os = exchange.getResponseBody();
            try {
            	os.write(response.getBytes(Charset.forName("UTF-8")));
            } catch (Exception e) {
            	logger.severe("Failed to output the response: " + e.getMessage());
            }
            os.close();
        }
    }
	
	/**
	 * Configure the applications logger
	 * @param props application properties
	 */
	private static void configureLogger() {
		Level logLevel = convertLogLevel(props.getProperty("log-level"));
		
		logger.setLevel(logLevel);
		
		//disable passing the logs up to the parent handler
		logger.setUseParentHandlers(false);
		
		//if debug mode is on, enable console handler
		if (props.getProperty("log-debug").equals("1")) {
			ConsoleHandler consoleHandler = new ConsoleHandler();
			consoleHandler.setLevel(Level.ALL);
			logger.addHandler(consoleHandler);
		}
		
		logger.addHandler(new MongoLogHandler("log.suite", Integer.parseInt(props.getProperty("log-ttl-hours"))));
	}
	
	private static void configureHttpClient() {
		PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
		connectionManager.setMaxTotal(100);
		connectionManager.setDefaultMaxPerRoute(10);
		
		defaultRequestConfig = RequestConfig.custom()
        		.setConnectTimeout(connectionTimeout)
        		.setConnectionRequestTimeout(connectionTimeout)
        		.setSocketTimeout(connectionTimeout)
        		.setStaleConnectionCheckEnabled(false)
        		.build();
		
		httpClient = HttpClients
				.custom()
				.setConnectionManager(connectionManager)
				.setDefaultRequestConfig(defaultRequestConfig)
				.disableAutomaticRetries()
				.build();
		
		//run the connection monitor
		ConnectionMonitor connectionMonitor = null;
		try {
			connectionMonitor = new ConnectionMonitor(connectionManager, 
					Integer.parseInt(props.getProperty("purge-connections-interval")), 
					Integer.parseInt(props.getProperty("close-idle-connections-after-seconds")));
		} catch (Exception e) {
			logger.severe("Failed to initialize connection monitor: " + e.getMessage());
		}
		
		if (connectionMonitor != null) {
			connectionMonitor.start();
		}
	}
	
	/**
	 * Converts integer-like string to a logging level
	 * @param _logLevel
	 * @return level for logging
	 */
	public static Level convertLogLevel(String _logLevel) {
		Level logLevel = null;
		if (_logLevel.equals("1")) { logLevel = Level.FINEST; }
		else if (_logLevel.equals("2")) { logLevel = Level.FINER; }
		else if (_logLevel.equals("3")) { logLevel = Level.FINE; }
		else if (_logLevel.equals("4")) { logLevel = Level.CONFIG; }
		else if (_logLevel.equals("5")) { logLevel = Level.INFO; }
		else if (_logLevel.equals("6")) { logLevel = Level.WARNING; }
		else if (_logLevel.equals("7")) { logLevel = Level.SEVERE; }
		else { logLevel = Level.OFF; }
		
		return logLevel;
	}
	
	private static boolean initMongo() throws UnknownHostException {
		MongoClient mongoClient = new MongoClient(props.getProperty("mongodb-host"), Integer.parseInt(props.getProperty("mongodb-port")));
		db = mongoClient.getDB(props.getProperty("mongodb-db"));
		return db.authenticate(props.getProperty("mongodb-user"), props.getProperty("mongodb-pass").toCharArray());
	}
	
	public static DB getDB() {
		return db;
	}
	
	public static Properties getProperties() {
		return props;
	}
}
