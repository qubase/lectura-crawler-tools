package qubase.suite;

import java.util.Calendar;
import java.util.Date;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;

public class MongoLogHandler extends Handler {
	
	private String collectionName;
	private int ttl;
	
	public MongoLogHandler(String collectionName, int ttl) {
		this.collectionName = collectionName;
		this.ttl = ttl;
		
		DB db = LecturaCrawlerSuite.getDB();
		DBCollection collection = db.getCollection(collectionName);
		collection.ensureIndex(new BasicDBObject("ttl", 1), new BasicDBObject("expireAfterSeconds", 0));
		collection.ensureIndex(new BasicDBObject("date", -1));
	}

	@Override
	public void close() throws SecurityException {
	}

	@Override
	public void flush() {
	}

	@Override
	public void publish(LogRecord record) {
		DB db = LecturaCrawlerSuite.getDB();
		DBCollection collection = db.getCollection(collectionName);
		Date now = new Date();
		BasicDBObject json = new BasicDBObject("ttl", addTtl(now, ttl))
			.append("date", now)
			.append("level", convertLevel(record.getLevel()))
			.append("message", record.getMessage())
			.append("logger", record.getLoggerName())
			.append("method", record.getSourceMethodName())
			.append("class", record.getSourceClassName());
		
		collection.insert(json);
	}
	
	private Date addTtl(Date date, int ttl) {
		Calendar cal = Calendar.getInstance();  
	    cal.setTime(date);  
	    cal.add(Calendar.HOUR, ttl);
		return cal.getTime();
	}
	
	private Integer convertLevel(Level level) {
		Integer result = null;
		if (level == Level.FINEST) { result = 1; }
		else if (level == Level.FINER) { result = 2; }
		else if (level == Level.FINE) { result = 3; }
		else if (level == Level.CONFIG) { result = 4; }
		else if (level == Level.INFO) { result = 5; }
		else if (level == Level.WARNING) { result = 6; }
		else if (level == Level.SEVERE) { result = 7; }
		return result;
	}

}
