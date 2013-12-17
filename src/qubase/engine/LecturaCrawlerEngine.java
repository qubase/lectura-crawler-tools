package qubase.engine;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Properties;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.mongodb.DB;
import com.mongodb.MongoClient;

/**
 * @author Martin Racko <info@qubase.sk>
 *
 */
public class LecturaCrawlerEngine {
	private static Properties props = new Properties();
	private static Logger logger = Logger.getLogger("qubase.engine");
	
	private static DB db;
	/**
	 * @param args
	 * @throws IOException 
	 * @throws FileNotFoundException 
	 */
	public static void main(String[] args) throws Exception  {
		initProps();
		if (!initMongo()) {
			String message = "Not able to authenticate to MongoDB database: " + props.getProperty("mongodb-db");
			throw new Exception(message);
		}
		configureLogger();
		Controller controller = new Controller(props);
		
		try {
			controller.run();
		} catch (Exception e) {
			logger.severe("Crawler Engine crashed: " + e.getMessage());
			StringWriter stackTrace = new StringWriter();
			e.printStackTrace(new PrintWriter(stackTrace));
			logger.severe(stackTrace.toString());
		}
		
		System.exit(0);
	}
	
	public static void initProps() throws FileNotFoundException, IOException {
		props.load(new FileInputStream("config.properties"));
	}
	
	private static boolean initMongo() throws UnknownHostException {
		MongoClient mongoClient = new MongoClient(props.getProperty("mongodb-host"), Integer.parseInt(props.getProperty("mongodb-port")));
		db = mongoClient.getDB(props.getProperty("mongodb-db"));
		return db.authenticate(props.getProperty("mongodb-user"), props.getProperty("mongodb-pass").toCharArray());
	}
	
	public static DB getDB() {
		return db;
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
		
		logger.addHandler(new MongoLogHandler());
	}
	
	/**
	 * Converts integer-like string to a logging level
	 * @param _logLevel
	 * @return level for logging
	 */
	private static Level convertLogLevel(String _logLevel) {
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
	
	public static Properties getProperties() {
		return props;
	}

	/**
	 * send the email report
	 * @param crawler
	 */
	public static void reportStopping(Crawler crawler) {
		String[] recipients = props.getProperty("email-recipients").split("//s*,//s*");
		
		String text = "Stopping crawler, too many errors occured. After revision you will need to change the status of this crawler back to 1 manually in the file: " + props.getProperty("crawler-config") + "\n\n";
		text += "Crawler: [" + crawler.getId() + "] " + crawler.getName() + "\n\n";
		text += "Error list:\n";
		ArrayList<String> errors = crawler.getErrors();
		for (String error : errors) {
			text += error + "\n";
		}
		
		Email.send(recipients, "Stopping crawler: " + crawler.getName(), text, props.getProperty("email-user"), props.getProperty("email-pass"), false);
	}
}
