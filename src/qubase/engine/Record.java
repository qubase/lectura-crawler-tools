package qubase.engine;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.WriteResult;

public class Record {
	
	private BasicDBObject json;
	private int ttl = 0;
	private static final String DATE_FORMAT = "yyyy-MM-dd";
	
	private boolean hasModelName = false;
	private boolean hasManName = false;
	private boolean hasDate = false;
	private boolean isDuplicate = false;
	
	public void save() throws Exception {
		DB db = LecturaCrawlerEngine.getDB();
		DBCollection collection = db.getCollection("listings");
		
		if (!hasModelName || !hasManName) {
			throw new Exception("Mandatory fields missing: " + json.toString());
		}
		
		Date now = new Date();
		json.append("createdAt", now);
		
		if (!hasDate) {
			json.append("date", now);
		}
		
		if (ttl != 0) {
			json.append("ttl", addTtl(now));
		}
		json.append("todo", 1);
		
		WriteResult res = collection.insert(json);
		if (!res.getLastError().ok()) {
			throw new Exception(res.getError());
		}
	}
	
	public boolean exists() throws Exception {
		String url = (String) json.get("url");
		if (url == null) throw new Exception("URL not defined, can not check if exists.");
		BasicDBObject query = new BasicDBObject("url", url);
		
		DB db = LecturaCrawlerEngine.getDB();
		DBCollection collection = db.getCollection("listings");
		DBCursor cursor = collection.find(query);
		int cnt = cursor.count();
		cursor.close();
		return  cnt > 0;
	}
	
	private Date addTtl(Date date) {
		Calendar cal = Calendar.getInstance();  
	    cal.setTime(date);  
	    cal.add(Calendar.DATE, ttl);
		return cal.getTime();
	}

	public void addProperty(String key, String value) throws ParseException {
		if (json == null) {
			json = new BasicDBObject();
		}
		
		if (key.equals("modelName") && value != null && !value.isEmpty()) {
			hasModelName = true;
		}
		
		if (key.equals("manName") && value != null && !value.isEmpty()) {
			hasManName = true;
		}
		
		if (value.matches("[0-9]+") && !key.equals("serial")) {
			//this is a number
			try {
				json.append(key, Integer.parseInt(value));
			} catch (Exception e) {
				try {
					json.append(key, Long.parseLong(value));
				} catch (Exception ex) {
					json.append(key, value);
				}
			}
		} else if (value.matches("[0-9]{4}-[0-9]{1,2}-[0-9]{1,2}")) {
			//this is a date
			hasDate = true;
			SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT);
			Date date = sdf.parse(value);
			json.append(key, date);
		} else {
			json.append(key, value);
		}
	}
	
	public void setTtl(int value) {
		ttl = value;
	}
	
	public String toString() {
		return json.toString();
	}

	public boolean isDuplicate() {
		return isDuplicate;
	}

	public void setDuplicate(boolean isDuplicate) {
		this.isDuplicate = isDuplicate;
	}
	
	
}
