package qubase.statistics;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.UnknownHostException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Properties;

import qubase.engine.Email;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.MongoClient;

public class LecturaCrawlerStatistics {

	private static DB db;
	private static Properties props = new Properties();
	private static HashMap<Integer, Crawler> crawlers = new HashMap<Integer, Crawler>();
	
	private static class Crawler {
		public String name = null;
		public String status = null;
		public String upload = null;
		public String instance = null;
	}
	
	private static class Total {
		public int listingsAllTime = 0;
		public int listings24hrs = 0;
		public int price = 0;
		public int curr = 0;
		public int year = 0;
		public int counter = 0;
		public int category = 0;
		public int serial = 0;
		public int country = 0;
		public int region = 0;
		public int zip = 0;
		public int attempts = 0;
		public int requests = 0;
		public int duplicates = 0;
		public int seconds = 0;
		public int requestSeconds = 0;
	}
	
	public static void main(String[] args) {
		String emailBody = null;
		try {
			initProps();
			if (!initMongo()) {
				String message = "Not able to authenticate to MongoDB database: " + props.getProperty("mongodb-db");
				throw new Exception(message);
			}
			loadCrawlers();
			String[] recipients = props.getProperty("email-recipients").split("\\s*,\\s*");
			emailBody = prepareEmail();
			Email.send(recipients, "Lectura Crawler Statistics", emailBody, props.getProperty("email-user"), props.getProperty("email-pass"), true);
			
		} catch (Exception e) {
			StringWriter stackTrace = new StringWriter();
			e.printStackTrace(new PrintWriter(stackTrace));
			emailBody = stackTrace.toString();
			String[] recipients = {"info@lectura.de"};
			try {
				recipients = props.getProperty("email-recipients").split("\\s*,\\s*");
			} catch (Exception ignore) {
				//
			}
			Email.send(recipients, "Lectura Crawler Statistics - Fail", emailBody, props.getProperty("email-user"), props.getProperty("email-pass"), false);
		} finally {
			System.exit(0);
		}
	}
	
	private static String prepareEmail() {
		int days = Integer.parseInt(props.getProperty("history-days"));
		
		DBCollection coll = db.getCollection("listings.report");
		Date historicalDate = new Date((new Date()).getTime() - days * 24 * 60 * 60 * 1000);
		DBCursor cursor = coll.find(new BasicDBObject("date", new BasicDBObject("$gte", historicalDate)));
		cursor.sort(new BasicDBObject("date", -1));
		
		String text = "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\"><title>Lectura Crawler Statistics</title></head>";
		text += "<body>\n";
		
		String headStyle = "style=\"border-bottom: 2px solid #404040;\"";
		String headLeftStyle = "style=\"border-bottom: 2px solid #404040; border-left: 1px dotted #808080;\"";
		String tableHead = "<tr>";
		tableHead += "<th " + headStyle + " width=\"24\">&nbsp;</th>";
		tableHead += "<th " + headStyle + ">ID</th>";
		tableHead += "<th " + headStyle + ">Name [Instance]</th>";
		tableHead += "<th " + headStyle + ">St.</th>";
		tableHead += "<th " + headStyle + ">All time</th>";
		tableHead += "<th " + headStyle + ">24 hrs</th>";
		tableHead += "<th " + headStyle + ">Price</th>";
		tableHead += "<th " + headStyle + ">Curr.</th>";
		tableHead += "<th " + headStyle + ">Year</th>";
		tableHead += "<th " + headStyle + ">Hrs/Km</th>";
		tableHead += "<th " + headStyle + ">Cat.</th>";
		tableHead += "<th " + headStyle + ">Serial</th>";
		tableHead += "<th " + headStyle + ">Country</th>";
		tableHead += "<th " + headStyle + ">Region</th>";
		tableHead += "<th " + headStyle + ">Zip</th>";
		tableHead += "<th " + headLeftStyle + ">A</th>";
		tableHead += "<th " + headStyle + ">R</th>";
		tableHead += "<th " + headStyle + ">D</th>";
		tableHead += "<th " + headStyle + ">Hrs</th>";
		tableHead += "<th " + headStyle + ">RHrs</th>";
		tableHead += "<th " + headStyle + ">1RSec</th>";
		tableHead += "</tr>\n";
		
		String colspan = "21";
		
		String titleOK = "Seems to be OK";
		String titleOFF = "This one's OFF";
		String titleFuckedUp = "There might be a problem";
		String titleUploadOFF = "Testing. No upload to Lectura";
		String imgOK = "<img src=\"https://dl.dropboxusercontent.com/u/6521559/117-todo.png\" title=\"" + titleOK + "\"/>";
		String imgOFF = "<img src=\"https://dl.dropboxusercontent.com/u/6521559/21-skull.png\" title=\"" + titleOFF + "\"/>";
		String imgFuckedUp = "<img src=\"https://dl.dropboxusercontent.com/u/6521559/25-weather.png\" title=\"" + titleFuckedUp + "\"/>";
		String imgUploadOFF = "<img src=\"https://dl.dropboxusercontent.com/u/6521559/54-lock.png\" title=\"" + titleUploadOFF + "\"/>";
		
		String tableTail = "<tr><td colspan=\"" + colspan + "\">"
				+ imgOK + "&nbsp;&nbsp;&nbsp;" + titleOK + "&nbsp;&nbsp;|&nbsp;&nbsp;"
				+ imgOFF + "&nbsp;&nbsp;&nbsp;" + titleOFF + "&nbsp;&nbsp;|&nbsp;&nbsp;"
				+ imgFuckedUp + "&nbsp;&nbsp;&nbsp;" + titleFuckedUp + "&nbsp;&nbsp;|&nbsp;&nbsp;"
				+ imgUploadOFF + "&nbsp;&nbsp;&nbsp;" + titleUploadOFF + "<br/>"
				+ "<b>A</b> - All attempts&nbsp;&nbsp;|&nbsp;&nbsp;<b>R</b> - Requests&nbsp;&nbsp;|&nbsp;&nbsp;<b>D</b> - Duplicates&nbsp;&nbsp;|&nbsp;&nbsp;<b>Hrs</b> - Hours spent with this crawler&nbsp;&nbsp;|&nbsp;&nbsp;<b>RHrs</b> - Hours spent with requests&nbsp;&nbsp;|&nbsp;&nbsp;<b>1RSec</b> - Avg seconds per request"
				+ "</td></tr>";
		tableTail += "</table>\n";
		
		Date currDate = new Date();
		boolean first = true;
		int count = cursor.count();
		int iter = 1;
		
		SimpleDateFormat sdf = (SimpleDateFormat) DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.US);
		sdf.applyPattern("dd MMM yyyy");
		
		while (cursor.hasNext()) {
			BasicDBObject doc = (BasicDBObject) cursor.next();
			Date reportTimestamp = doc.getDate("date");
			Total total = new Total();
			if (!reportTimestamp.equals(currDate)) {
				if (first) {
					first = false;
				} else {
					text += tableTail;
				}
				
				if (iter <= count) {
					text += "<table cellpadding=\"10\" cellspacing=\"0\" align=\"center\">\n";
					text += "<tr><th align=\"left\" colspan=\"" + colspan + "\" style=\"padding-top: 25px;\"><h2 style=\"color: #808080\">" + sdf.format(reportTimestamp) + "</h2></th></tr>";
				}
				
				iter++;
				text += tableHead;
				currDate = reportTimestamp;
			}
			
			BasicDBList list = (BasicDBList) doc.get("summary");
			
			for (Object portalReport : list) {
				BasicDBObject pr = (BasicDBObject) portalReport;
				
				Integer portalId = pr.getInt("portalId");
				BasicDBObject report = (BasicDBObject) pr.get("report");
				
				String name = "N/A";
				String status = "N/A";
				String upload = "N/A";
				String instance = "";
				if (crawlers.containsKey(portalId)) {
					name = crawlers.get(portalId).name;
					status = crawlers.get(portalId).status;
					upload = crawlers.get(portalId).upload;
					instance = " [" + crawlers.get(portalId).instance + "]";
				}
				
				
				String defaultStyle = "style=\"border-bottom: 1px solid #404040; background: #b2ffb2; color: #505050\"";
				String offStyle = "style=\"border-bottom: 1px solid #404040; background: #efefef; color: #a0a0a0\"";
				String alarmStyle = "style=\"border-bottom: 1px solid #404040; background: #ffb0b0; color: #505050\"";
				String uploadOffStyle = "style=\"border-bottom: 1px solid #404040; background: #fff5b2; color: #505050\"";
				String statsLeftStyle = "style=\"border-bottom: 1px solid #404040; border-left: 1px solid #808080; border-right: 1px dotted #808080; background: #ffffff; color: #505050\"";
				String statsStyle = "style=\"border-bottom: 1px solid #404040; border-right: 1px dotted #808080; background: #ffffff; color: #505050\"";
				
				boolean alarm = report.getInt("listings") == 0
						|| report.getInt("price") == 0
						|| report.getInt("currency") == 0
						|| report.getInt("counter") == 0
						|| report.getInt("year") == 0
						|| report.getInt("category") == 0
						|| report.getInt("country") == 0;
				
				String cellStyle = (!status.equals("1")) 
						? offStyle 
						: (alarm)
							? alarmStyle
							: (upload.equals("0"))
								? uploadOffStyle
								: defaultStyle;
				
				String img = (!status.equals("1")) 
						? imgOFF 
						: (alarm)
							? imgFuckedUp
							: (upload.equals("0"))
								? imgUploadOFF
								: imgOK;
				
				String alarmCellStyle = "style=\"border-bottom: 1px solid #404040; background: #ff6666; color: #505050\"";
				String tableRow = "<tr>";
				tableRow += "<td " + cellStyle + " width=\"24\">" + img + "</td>";
				tableRow += "<td " + cellStyle + ">" + portalId + "</td>";
				tableRow += "<td " + cellStyle + "><b>" + name + "</b>" + instance + "</td>";
				tableRow += "<td " + cellStyle + ">" + ((status.equals("1")) ? "ON" : "OFF") + "</td>";
				tableRow += "<td " + cellStyle + ">" + report.getInt("listingsAllTime") + "</td>";
				total.listingsAllTime += report.getInt("listingsAllTime");
				
				String thisCellStyle = (report.getInt("listings") == 0) ? (status.equals("1")) ? alarmCellStyle : cellStyle : cellStyle;
				tableRow += "<td " + thisCellStyle + "><b>" + report.getInt("listings") + "</b></td>";
				total.listings24hrs += report.getInt("listings");
				
				thisCellStyle = (report.getInt("price") == 0) ? (status.equals("1")) ? alarmCellStyle : cellStyle : cellStyle;
				tableRow += "<td " + thisCellStyle + ">" + report.getInt("price") + "</td>";
				total.price += report.getInt("price");
				
				thisCellStyle = (report.getInt("currency") == 0) ? (status.equals("1")) ? alarmCellStyle : cellStyle : cellStyle;
				tableRow += "<td " + thisCellStyle + ">" + report.getInt("currency") + "</td>";
				total.curr += report.getInt("currency");
				
				thisCellStyle = (report.getInt("year") == 0) ? (status.equals("1")) ? alarmCellStyle : cellStyle : cellStyle;
				tableRow += "<td " + thisCellStyle + ">" + report.getInt("year") + "</td>";
				total.year += report.getInt("year");
				
				thisCellStyle = (report.getInt("counter") == 0) ? (status.equals("1")) ? alarmCellStyle : cellStyle : cellStyle;
				tableRow += "<td " + thisCellStyle + ">" + report.getInt("counter") + "</td>";
				total.counter += report.getInt("counter");
				
				thisCellStyle = (report.getInt("category") == 0) ? (status.equals("1")) ? alarmCellStyle : cellStyle : cellStyle;
				tableRow += "<td " + thisCellStyle + ">" + report.getInt("category") + "</td>";
				total.category += report.getInt("category");
				
				thisCellStyle = (report.getInt("serial") == 0) ? (status.equals("1")) ? alarmCellStyle : cellStyle : cellStyle;
				tableRow += "<td " + thisCellStyle + ">" + report.getInt("serial") + "</td>";
				total.serial += report.getInt("serial");
				
				boolean tooFewCountriesCollected = false;
				if (report.getInt("listings") != 0) {
					tooFewCountriesCollected = (report.getInt("listings") / 2) > report.getInt("country");
				}
				thisCellStyle = (report.getInt("country") == 0 || tooFewCountriesCollected) ? (status.equals("1")) ? alarmCellStyle : cellStyle : cellStyle;
				tableRow += "<td " + thisCellStyle + ">" + report.getInt("country") + "</td>";
				total.country += report.getInt("country");
				
				thisCellStyle = (report.getInt("region") == 0) ? (status.equals("1")) ? alarmCellStyle : cellStyle : cellStyle;
				tableRow += "<td " + thisCellStyle + ">" + report.getInt("region") + "</td>";
				total.region += report.getInt("region");
				
				thisCellStyle = (report.getInt("zip") == 0) ? (status.equals("1")) ? alarmCellStyle : cellStyle : cellStyle;
				tableRow += "<td " + thisCellStyle + ">" + report.getInt("zip") + "</td>";
				total.zip += report.getInt("zip");
				
				//stats
				int attempts = (report.get("attempts") != null) ?  report.getInt("attempts") : 0;
				int requests = (report.get("requests") != null) ?  report.getInt("requests") : 0;
				int duplicates = (report.get("duplicates") != null) ?  report.getInt("duplicates") : 0;
				long seconds = (report.get("seconds") != null) ?  report.getLong("seconds") : 0;
				long requestSeconds = (report.get("requestSeconds") != null) ?  report.getLong("requestSeconds") : 0;
				
				tableRow += "<td " + statsLeftStyle + ">" + attempts + "</td>";
				tableRow += "<td " + statsStyle + "><b>" + requests + "</b></td>";
				tableRow += "<td " + statsStyle + ">" + duplicates + "</td>";
				tableRow += "<td " + statsStyle + ">" + round(((double) seconds) / (60 * 60), 2) + "</td>";
				tableRow += "<td " + statsStyle + ">" + round(((double) requestSeconds) / (60 * 60), 2) + "</td>";
				tableRow += "<td " + statsStyle + ">" + ((requests > 0) ? round( (double) requestSeconds / (double) requests, 2) : "-") + "</td>";
				tableRow += "</tr>\n";
				
				total.attempts += attempts;
				total.requests += requests;
				total.duplicates += duplicates;
				total.seconds += seconds;
				total.requestSeconds += requestSeconds;
				
				text += tableRow;
			}
			
			String tableTotal = "<tr>";
			tableTotal += "<td " + headStyle + " width=\"24\">&nbsp;</td>";
			tableTotal += "<td " + headStyle + ">&nbsp;</td>";
			tableTotal += "<td " + headStyle + "><b>TOTAL</b></td>";
			tableTotal += "<td " + headStyle + ">&nbsp;</td>";
			tableTotal += "<td " + headStyle + ">" + total.listingsAllTime + "</td>";
			tableTotal += "<td " + headStyle + "><b>" + total.listings24hrs + "</b></td>";
			tableTotal += "<td " + headStyle + ">" + total.price + "</td>";
			tableTotal += "<td " + headStyle + ">" + total.curr + "</td>";
			tableTotal += "<td " + headStyle + ">" + total.year + "</td>";
			tableTotal += "<td " + headStyle + ">" + total.counter + "</td>";
			tableTotal += "<td " + headStyle + ">" + total.category + "</td>";
			tableTotal += "<td " + headStyle + ">" + total.serial + "</td>";
			tableTotal += "<td " + headStyle + ">" + total.country + "</td>";
			tableTotal += "<td " + headStyle + ">" + total.region + "</td>";
			tableTotal += "<td " + headStyle + ">" + total.zip + "</td>";
			tableTotal += "<td " + headLeftStyle + ">" + total.attempts + "</td>";
			tableTotal += "<td " + headStyle + "><b>" + total.requests + "</b></td>";
			tableTotal += "<td " + headStyle + ">" + total.duplicates + "</td>";
			tableTotal += "<td " + headStyle + ">" + round(((double) total.seconds) / (60 * 60), 2) + "</td>";
			tableTotal += "<td " + headStyle + ">" + round(((double) total.requestSeconds) / (60 * 60), 2) + "</td>";
			tableTotal += "<td " + headStyle + ">" + ((total.requests > 0) ? round( (double) total.requestSeconds / (double) total.requests, 2) : "-") + "</td>";
			tableTotal += "</tr>\n";
			
			text += tableTotal;
		}
		
		text += tableTail;
		text += "</table>\n";
		text += "</body>\n";
		text += "</html>";
		return text;
	}
	
	private static double round(double value, int places) {
	    if (places < 0) throw new IllegalArgumentException();

	    BigDecimal bd = new BigDecimal(value);
	    bd = bd.setScale(places, RoundingMode.HALF_UP);
	    return bd.doubleValue();
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
			crawler.instance = c.getString("instance");
			crawlers.put(id, crawler);
		}
	}

	public static void initProps() throws FileNotFoundException, IOException {
		props.load(new FileInputStream("config.properties"));
	}

	private static boolean initMongo() throws UnknownHostException {
		MongoClient mongoClient = new MongoClient(props.getProperty("mongodb-host"), Integer.parseInt(props.getProperty("mongodb-port")));
		db = mongoClient.getDB(props.getProperty("mongodb-db"));
		return db.authenticate(props.getProperty("mongodb-user"), props.getProperty("mongodb-pass").toCharArray());
	}
	
}
