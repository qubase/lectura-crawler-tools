package qubase.suite;

import java.util.HashMap;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;

public class Dialing {
	private static HashMap<String, String> codes = new HashMap<String, String>();
	private static boolean init = false;
	
	
	public static String getCountry(String dialingCode) {
		if (!init) {
			initDialing();
			init = true;
		}
		
		return codes.get(dialingCode);
	}
	
	private static void initDialing() {
		DB db = LecturaCrawlerSuite.getDB();
		DBCollection dial = db.getCollection("dial");
		DBCursor cursor = dial.find();
		
		while (cursor.hasNext()) {
			BasicDBObject doc = (BasicDBObject) cursor.next();
			codes.put(doc.getString("code"), doc.getString("country"))	;
		}
	}
}
