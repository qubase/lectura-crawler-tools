package qubase.uploader;

import java.io.FileInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import qubase.engine.Email;

import com.connectionpool.ConnectionPool;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;

public class LecturaUploader {
	
	private static ConnectionPool connectionPool = null;
	private static DB db;
	private static Properties props = new Properties();
	
	private static int recordsTransferedCP = 0;
	private static int recordsToTransferCP = 0;
	private static int recordsTransferedCPTotal = 0;
	private static int recordsToTransferCPTotal = 0;
	
	private static int insertLength = 0;
	
	private static List<?> portals;
	
	private static String message = null;
	
	private static HashMap<Integer, Crawler> crawlers = new HashMap<Integer, Crawler>();
	
	private static class Crawler {
		public String status = null;
		public String upload = null;
		public String name = null;
	}

	public static void main(String[] args) throws Exception {
		props.load(new FileInputStream("config.properties"));
		
		insertLength = Integer.parseInt(props.getProperty("insert-length"));
		
		Class.forName("com.mysql.jdbc.Driver");
		String url = "jdbc:mysql://" + props.getProperty("mysql-host") + "/" + props.getProperty("mysql-db");
		connectionPool = new ConnectionPool(url, props.getProperty("mysql-user"), props.getProperty("mysql-pass"));
		
		MongoClient mongoClient = new MongoClient(props.getProperty("mongodb-host"), Integer.parseInt(props.getProperty("mongodb-port")));
		db = mongoClient.getDB(props.getProperty("mongodb-db"));
		if (!db.authenticate(props.getProperty("mongodb-user"), props.getProperty("mongodb-pass").toCharArray())) {
			throw new Exception("Connection to MongoDB failed.");
		}
		
		message = "";
		String subject = null;
		String[] recipients = props.getProperty("email-recipients").split("\\s*,\\s*");
		try {
			loadCrawlers();
			if (loadPortals()) {
				int size = portals.size();
				if (size > 0) {
					
					for (int i = 0; i < size; i++) {
						boolean upload = 
								(crawlers.containsKey((Integer) portals.get(i))) &&
								crawlers.get((Integer) portals.get(i)).upload.equals("1") &&
								crawlers.get((Integer) portals.get(i)).status.equals("1");
						
						if (upload) {
							System.out.println("Portal: " + portals.get(i));
							transferDataForPortal((Integer) portals.get(i), crawlers.get((Integer) portals.get(i)));
						} else {
							System.out.println("Skipping portal: " + portals.get(i));
						}
						
						recordsToTransferCP = 0;
						recordsTransferedCP = 0;
					}
					
					subject = "Lectura Uploader - Success";
					message += "TOTAL: " + recordsToTransferCPTotal + "/" + recordsTransferedCPTotal; 
				} else {
					message = "No portals to process loaded.";
					subject = "Lectura Uploader - Warning";
				}
			} else {
				message = "Couldn't load portals";
				subject = "Lectura Uploader - Failed";
			}
		} catch (Exception e) {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			e.printStackTrace(pw);
			
			message = sw.toString();
			subject = "Lectura Uploader - Failed";
		} finally {
			System.out.println(subject);
			System.out.println(message);
			Email.send(recipients, subject, message, props.getProperty("email-user"), props.getProperty("email-pass"), false);
		}
		
		System.exit(0);
	}
	
	private static boolean loadPortals() {
		if (db == null) return false;
		
		DBCollection coll = db.getCollection("listings");
		//TODO remove the portalId condition
		portals = coll.distinct("portalId", new BasicDBObject("todo", 1)/*.append("portalId", 675)*/);
		
		return portals != null;
	}

	private static void transferDataForPortal(Integer portalId, Crawler crawler) throws Exception {
		if (db == null) throw new Exception("MongoDB not initialized.");
		
		DBCollection coll = db.getCollection("listings");
		
		DBCursor cursor = coll.find(new BasicDBObject("portalId", portalId).append("todo", 1));
		cursor.sort(new BasicDBObject("createdAt", 1));
		recordsToTransferCP = cursor.count();
		
		String insert = "insert into base_uploaded_data (portal_id, import_id, original_name, original_manufacturer, original_category, original_category_language, url, original_price, currency, operating_hours, year_of_manufacture, country, serial_number, zip_code, region, found_on, worksheet_operator, upload_operator, uploaded_at) values ";
		String values = "";
		
		int prepared = 0;
		
		Thread.sleep(1000);
		long importId = System.currentTimeMillis() / 1000;
		
		while (cursor.hasNext()) {
			DBObject doc = cursor.next();
			values += createValues(doc, portalId, importId) + ",";
			prepared++;
			
			doc.removeField("todo");
			coll.save(doc);
			
			if (prepared == insertLength) {
				try {
					recordsTransferedCP += insert((insert + values).replaceAll(",$", ""));
					System.out.println(recordsTransferedCP + "/" + recordsToTransferCP);
				} catch (Exception e) {
					StringWriter sw = new StringWriter();
					PrintWriter pw = new PrintWriter(sw);
					e.printStackTrace(pw);
					
					message += sw.toString() + "\n\n";
				} finally {
					prepared = 0;
					values = "";
				}
			}
		}
		
		cursor.close();
		
		//flush the final rows
		if (prepared != 0) {
			try {
				recordsTransferedCP += insert((insert + values).replaceAll(",$", ""));
				System.out.println(recordsTransferedCP + "/" + recordsToTransferCP);
			} catch (Exception e) {
				StringWriter sw = new StringWriter();
				PrintWriter pw = new PrintWriter(sw);
				e.printStackTrace(pw);
				
				message += sw.toString() + "\n\n";
			}
		}
		
		recordsToTransferCPTotal += recordsToTransferCP;
		recordsTransferedCPTotal += recordsTransferedCP;
		
		message += "Success ratio: [" + portalId + "] " + crawler.name + " => " + recordsTransferedCP + "/" + recordsToTransferCP + "\n";
		
		try {
			insertImport(importId, portalId);
		} catch (Exception e) {
			message += "Couldn't save import [" + importId + "]: " + e.getMessage() + "\n\n";
		}
	}

	private static int insert(String sql) throws Exception {
		Connection connection = null;
		Statement statement = null;
		try {
			connection = connectionPool.getConnection();
			statement = connection.createStatement();
			return statement.executeUpdate(sql);
		} catch (Exception e) {
			throw e;
		} finally {
			if (statement != null) {
				statement.close();
			} 
			if (connection != null) {
				connection.close();
			}
		}
	}
	
	private static void insertImport(long importId, Integer portalId) throws Exception {
		Connection connection = null;
		Statement statement = null;
		try {
			connection = connectionPool.getConnection();
			statement = connection.createStatement();
			
			int success = (recordsToTransferCP > recordsTransferedCP) ? recordsToTransferCP : recordsTransferedCP;
			int failed = (recordsToTransferCP > recordsTransferedCP) ? recordsToTransferCP - recordsTransferedCP : 0;
			
			String sql = "insert into base_human_import (id, imported_by, portal_id, success_file_lines, failed_file_lines, imported_at, created_at, status) values (" 
					+ importId + "," 
					+ "'qubase'," 
					+ portalId + ","
					+ success + ","
					+ failed + ","
					+ "now(), now(), 'processing')";
			
			statement.executeUpdate(sql);
		} catch (Exception e) {
			throw e;
		} finally {
			if (statement != null) {
				statement.close();
			} 
			if (connection != null) {
				connection.close();
			}
		}
	}

	private static String createValues(DBObject doc, Integer portalId, long importId) {
		
		String result = portalId + ",";
		result += importId + ",";
		result += "'" + doc.get("modelName").toString().replaceAll("('|\\\\)", "\\\\$1") + "',";
		result += "'" + doc.get("manName").toString().replaceAll("('|\\\\)", "\\\\$1") + "',";
		result += (doc.get("category") != null) ? "'" + doc.get("category").toString().replaceAll("('|\\\\)", "\\\\$1") + "'," : "NULL,";
		result += (doc.get("catLang") != null) ? "'" + doc.get("catLang").toString().replaceAll("('|\\\\)", "\\\\$1") + "'," : "NULL,";
		result += (doc.get("url") != null) ? "'" + doc.get("url").toString().replaceAll("('|\\\\)", "\\\\$1") + "'," : "NULL,";
		result += (doc.get("price") != null) ? "'" + doc.get("price").toString().replaceAll("('|\\\\)", "\\\\$1") + "'," : "NULL,";
		result += (doc.get("currency") != null) ? "'" + doc.get("currency").toString().replaceAll("('|\\\\)", "\\\\$1") + "'," : "NULL,";
		String counter = (doc.get("counter") == null) 
				? "NULL," 
				: (doc.get("counter").toString().replaceAll("[^0-9]", "").isEmpty()) 
					? "NULL," 
					: "'" + doc.get("counter").toString().replaceAll("[^0-9]", "") + "',"; 
		result += counter;
		String year = (doc.get("year") == null) 
				? "NULL," 
				: (doc.get("year").toString().replaceAll("[^0-9]", "").isEmpty()) 
					? "NULL," 
					: "'" + doc.get("year").toString().replaceAll("[^0-9]", "") + "',"; 
		result += year;
		result += (doc.get("country") != null) ? "'" + doc.get("country").toString().replaceAll("('|\\\\)", "\\\\$1") + "'," : "NULL,";
		result += (doc.get("serial") != null) ? "'" + doc.get("serial").toString().replaceAll("('|\\\\)", "\\\\$1") + "'," : "NULL,";
		result += (doc.get("zip") != null) ? "'" + doc.get("zip").toString().replaceAll("('|\\\\)", "\\\\$1") + "'," : "NULL,";
		result += (doc.get("region") != null) ? "'" + doc.get("region").toString().replaceAll("('|\\\\)", "\\\\$1") + "'," : "NULL,";
		result += (doc.get("date") != null) ? "'" + processDate((Date) doc.get("date")) + "'," : "NULL,";
		result += "'qubase',";
		result += "'qubase',";
		result += "now()";
		
		return "(" + result + ")";
	}

	private static String processDate(Date date) {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
		return sdf.format(date);
	}
	
	public static Properties getProperties() {
		return props;
	}
	
	private static void loadCrawlers() throws Exception {
		DBCursor cursor = db.getCollection("crawler.config").find().sort(new BasicDBObject("_id", 1));
		
		while (cursor.hasNext()) {
			BasicDBObject c = (BasicDBObject) cursor.next();
			Integer id = c.getInt("_id");
			Crawler crawler = new Crawler();
			crawler.name = c.getString("name");
			crawler.status = c.getString("status");
			crawler.upload = c.getString("upload");
			crawlers.put(id, crawler);
		}
	}
}
