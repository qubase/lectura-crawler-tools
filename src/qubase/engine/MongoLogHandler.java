package qubase.engine;

import java.util.Calendar;
import java.util.Date;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;

public class MongoLogHandler extends Handler {

	@Override
	public void close() throws SecurityException {
	}

	@Override
	public void flush() {
	}

	@Override
	public void publish(LogRecord record) {
		DB db = LecturaCrawlerEngine.getDB();
		DBCollection collection = db.getCollection("log");
		Date now = new Date();
		BasicDBObject json = new BasicDBObject("ttl", addTtl(now, Integer.parseInt(LecturaCrawlerEngine.getProperties().getProperty("log-ttl-hours"))))
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
