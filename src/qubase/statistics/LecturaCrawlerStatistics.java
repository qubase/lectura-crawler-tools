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
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
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
	
	private static long days;
	private static int compressedDays;
	
	private static class Crawler {
		public String name = null;
		public String status = null;
		public String upload = null;
		public String instance = null;
	}
	
	private static class Total {
		public int crawlerCnt = 0;
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
		public int company = 0;
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
			days = Long.parseLong(props.getProperty("history-days"));
			for (int i = 0; i < args.length; i++) {
				if (args[i].matches("history-days=[0-9]+")) {
					days = Long.parseLong(args[i].split("\\s*=\\s*")[1].trim());
				}
				
				if (args[i].matches("email-recipients=[-0-9a-zA-Z@\\.,]+")) {
					String[] r = (args[i].split("\\s*=\\s*")[1].trim().split(","));
					recipients = new String[r.length];
					recipients = r;
				}
			}
			
			emailBody = "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\"><title>Lectura Crawler Statistics</title></head>";
			emailBody += "<body>\n";
			emailBody += prepareFull();
			
			try {
				compressedDays = Integer.parseInt(props.getProperty("compressed-days"));
				if (compressedDays > 0) {
					emailBody += prepareCompressed();
				}
			} catch (NumberFormatException e) {
				//ignore
			}
			
			if (props.getProperty("listing-examples").equals("1")) {
				emailBody += prepareListings();
			}
			
			emailBody += "</body>\n";
			emailBody += "</html>";
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
	
	private static String prepareFull() {
		DBCollection coll = db.getCollection("listings.report");
		long daysToMillis = (days * 24L * 60L * 60L * 1000L);
		long nowToMillis = new Date().getTime();
		long historicalToMillis = nowToMillis - daysToMillis;
		Date historicalDate = new Date(historicalToMillis);
		DBCursor cursor = coll.find(new BasicDBObject("date", new BasicDBObject("$gte", historicalDate)));
		cursor.sort(new BasicDBObject("date", -1));
		
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
		tableHead += "<th " + headStyle + ">Curr</th>";
		tableHead += "<th " + headStyle + ">Year</th>";
		tableHead += "<th " + headStyle + ">Hrs/Km</th>";
		tableHead += "<th " + headStyle + ">Cat</th>";
		tableHead += "<th " + headStyle + ">Serial</th>";
		tableHead += "<th " + headStyle + ">Cntr</th>";
		tableHead += "<th " + headStyle + ">Reg</th>";
		tableHead += "<th " + headStyle + ">Zip</th>";
		tableHead += "<th " + headStyle + ">Cmpn</th>";
		tableHead += "<th " + headLeftStyle + ">A</th>";
		tableHead += "<th " + headStyle + ">R</th>";
		tableHead += "<th " + headStyle + ">D</th>";
		tableHead += "<th " + headStyle + ">Hrs</th>";
		tableHead += "<th " + headStyle + ">RHrs</th>";
		tableHead += "<th " + headStyle + ">1RSec</th>";
		tableHead += "</tr>\n";
		
		String colspan = "22";
		
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
		String text = "";
		while (cursor.hasNext()) {
			BasicDBObject doc = (BasicDBObject) cursor.next();
			Date reportTimestamp = doc.getDate("date");
			Calendar cal = Calendar.getInstance();
			cal.setTime(reportTimestamp);
			cal.add(Calendar.DATE, -1);
			reportTimestamp = cal.getTime();
			Total total = new Total();
			if (!reportTimestamp.equals(currDate)) {
				if (first) {
					first = false;
				} else {
					text += tableTail;
				}
				
				if (iter <= count) {
					text += "<table cellpadding=\"10\" cellspacing=\"0\" align=\"center\" width=\"100%\">\n";
					text += "<tr><th align=\"left\" colspan=\"" + colspan + "\" style=\"padding-top: 25px;\"><h2 style=\"color: #808080\">" + sdf.format(reportTimestamp) + "</h2></th></tr>";
				}
				
				iter++;
				text += tableHead;
				currDate = reportTimestamp;
			}
			
			BasicDBList list = (BasicDBList) doc.get("summary");
			
			for (Object portalReport : list) {
				total.crawlerCnt++;
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
				
				thisCellStyle = (report.getInt("company") == 0) ? (status.equals("1")) ? alarmCellStyle : cellStyle : cellStyle;
				tableRow += "<td " + thisCellStyle + ">" + report.getInt("company") + "</td>";
				total.company += report.getInt("company");
				
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
			tableTotal += "<td " + headStyle + ">" + total.crawlerCnt + "</td>";
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
			tableTotal += "<td " + headStyle + ">" + total.company + "</td>";
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
		return text;
	}
	
	private static String prepareCompressed() {
		String text = null;
		
		DBCollection coll = db.getCollection("listings.report");
		DBCursor cursor = coll.find()
				.sort(new BasicDBObject("date", -1))
				.limit(compressedDays);
		
		String headStyle = "style=\"border-bottom: 2px solid #404040; border-right: 1px dotted #a0a0a0;\"";
		String defaultStyle = "style=\"border-bottom: 1px solid #404040; background: #b2ffb2; color: #505050; border-right: 1px dotted #a0a0a0;\"";
		String offStyle = "style=\"border-bottom: 1px solid #404040; background: #efefef; color: #a0a0a0; border-right: 1px dotted #a0a0a0;\"";
		String alarmCellStyle = "style=\"border-bottom: 1px solid #404040; background: #ff6666; color: #505050; border-right: 1px dotted #a0a0a0;\"";
		
		int colspan = compressedDays + 1;
		
		text = "<table cellpadding=\"10\" cellspacing=\"0\" align=\"center\" width=\"100%\">\n";
		text += "<tr><th align=\"left\" colspan=\"" + colspan + "\" style=\"padding-top: 25px;\"><h2 style=\"color: #808080\">Compressed overview for " + new Integer(compressedDays).toString() + " days (listings per day)</h2></th></tr>";
		
		String tableHead = "<tr>";
		tableHead += "<th " + headStyle + ">Name</th>";
		
		SimpleDateFormat sdf = (SimpleDateFormat) DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.US);
		sdf.applyPattern("dd MMM");
		HashMap<Integer, String> lines = new HashMap<Integer, String>();
		ArrayList<Integer> ids = new ArrayList<Integer>();
		
		Iterator<Entry<Integer, Crawler>> it = crawlers.entrySet().iterator();
	    while (it.hasNext()) {
	        @SuppressWarnings("rawtypes")
			Map.Entry pairs = (Map.Entry)it.next();
			String name = ((Crawler) pairs.getValue()).name;
			Integer pid = (Integer) pairs.getKey();
			
			String def = ((Crawler) pairs.getValue()).status.equals("1") ? defaultStyle : offStyle;
			
	        lines.put(pid, "<tr><td " + def + " align=\"right\"><b>" + name + "</b></td>");
	        ids.add(pid);
	    }
	    
		while (cursor.hasNext()) {
			BasicDBObject doc = (BasicDBObject) cursor.next();
			Date reportTimestamp = doc.getDate("date");
			Calendar cal = Calendar.getInstance();
			cal.setTime(reportTimestamp);
			cal.add(Calendar.DATE, -1);
			reportTimestamp = cal.getTime();
			tableHead += "<th " + headStyle + ">" + sdf.format(reportTimestamp) + "</th>";
			
			BasicDBList list = (BasicDBList) doc.get("summary");
			
			HashMap<Integer, String> dList = new HashMap<Integer, String>();
			for (Object portalReport : list) {
				BasicDBObject pr = (BasicDBObject) portalReport;
				Integer portalId = pr.getInt("portalId");
				BasicDBObject report = (BasicDBObject) pr.get("report");
				int listings = report.getInt("listings");
				
				Crawler crawler = crawlers.get(portalId);
				boolean on = crawler.status.equals("1");
				String def = (on) ? defaultStyle : offStyle;
				String alarm = (on) ? alarmCellStyle : offStyle;
				
				String td = (listings > 0) ? "<td " + def + ">" + listings + "</td>" : "<td " + alarm + ">" + listings + "</td>";
				dList.put(portalId, td);
			}
			
			for (Integer id : ids) {
				if (lines.containsKey(id)) {
					if (dList.containsKey(id)) { 
						lines.put(id, lines.get(id) + dList.get(id));
					} else {
						lines.put(id, lines.get(id) + "<td " + offStyle + ">N/A</td>");
					}
				}
			}
		}
		
		tableHead += "</tr>\n";
		text += tableHead;
		Collections.sort(ids);
		for (Integer id : ids) {
			if (lines.containsKey(id)) {
				text += lines.get(id) + "</tr>\n";
			}
		}
		text += "</table>\n";
		
		return text;
	}
	
	private static String prepareListings() {
		String text = null;
		
		Iterator<Entry<Integer, Crawler>> it = crawlers.entrySet().iterator();
		ArrayList<Integer> ids = new ArrayList<Integer>();
		HashMap<Integer, BasicDBObject> listings = new HashMap<Integer, BasicDBObject>();
		
		Calendar cal = Calendar.getInstance();
		cal.setTime(new Date());
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
		cal.add(Calendar.DATE, -1);
		Date yesterday = cal.getTime();
		
	    while (it.hasNext()) {
	        @SuppressWarnings("rawtypes")
			Map.Entry pairs = (Map.Entry) it.next();
			Integer pid = (Integer) pairs.getKey();
			
			ArrayList<BasicDBObject> conditions = new ArrayList<BasicDBObject>();
			conditions.add(new BasicDBObject("portalId", pid));
			conditions.add(new BasicDBObject("createdAt", new BasicDBObject("$gte", yesterday)));
			
			DBCollection coll = db.getCollection("listings");
			DBCursor cursor = coll.find(new BasicDBObject("$and", conditions))
					.sort(new BasicDBObject("createdAt", -1))
					.limit(1);
			BasicDBObject listing = (cursor.hasNext()) ? (BasicDBObject) cursor.next() : null;
			
			listings.put(pid, listing);
			
	        ids.add(pid);
	    }
	    
	    String headStyle = "style=\"border-bottom: 2px solid #404040; border-right: 1px dotted #a0a0a0;\"";
		String defaultStyle = "style=\"border-bottom: 1px solid #404040; background: #b2ffb2; color: #505050; border-right: 1px dotted #a0a0a0;\"";
		String offStyle = "style=\"border-bottom: 1px solid #404040; background: #efefef; color: #a0a0a0; border-right: 1px dotted #a0a0a0;\"";
	    int colspan = 16;
	    text = "<table cellpadding=\"10\" cellspacing=\"0\" align=\"center\" width=\"100%\">\n";
		text += "<tr><th align=\"left\" colspan=\"" + colspan + "\" style=\"padding-top: 25px;\"><h2 style=\"color: #808080\">Sample listings</h2></th></tr>";
		
		text += "<tr>";
		text += "<th " + headStyle + ">Name</th>";
		text += "<th " + headStyle + ">Man</th>";
		text += "<th " + headStyle + ">Model</th>";
		text += "<th " + headStyle + ">Year</th>";
		text += "<th " + headStyle + ">Hrs/Km</th>";
		text += "<th " + headStyle + ">Cat</th>";
		text += "<th " + headStyle + ">Price</th>";
		text += "<th " + headStyle + ">Curr</th>";
		text += "<th " + headStyle + ">Cntry</th>";
		text += "<th " + headStyle + ">Reg</th>";
		text += "<th " + headStyle + ">Zip</th>";
		text += "<th " + headStyle + ">Serial</th>";
		text += "<th " + headStyle + ">Date</th>";
		text += "<th " + headStyle + ">Cmpn</th>";
		text += "<th " + headStyle + ">New</th>";
		text += "<th " + headStyle + ">Link</th>";
		text += "</tr>";
		
		Collections.sort(ids);
		SimpleDateFormat sdf = (SimpleDateFormat) DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.US);
		sdf.applyPattern("dd.MM.yyyy");
		String emptyCell = "<td " + offStyle + ">&nbsp;</td>";
		for (Integer id : ids) {
			BasicDBObject listing = listings.get(id);
			String def = crawlers.get(id).status.equals("1") ? defaultStyle : offStyle;
			if (listing != null) {
				text += "<tr>";
				text += "<td " + def + " align=\"right\"><b>" + crawlers.get(id).name + "</b></td>";
				text += (listings.get(id).getString("manName") != null) ? "<td " + def + ">" + listings.get(id).getString("manName") + "</td>" : emptyCell;
				text += (listings.get(id).getString("modelName") != null) ? "<td " + def + ">" + listings.get(id).getString("modelName") + "</td>" : emptyCell;
				text += (listings.get(id).getString("year") != null) ? "<td " + def + ">" + listings.get(id).getString("year") + "</td>" : emptyCell;
				text += (listings.get(id).getString("counter") != null) ? "<td " + def + ">" + listings.get(id).getString("counter") + "</td>" : emptyCell;
				text += (listings.get(id).getString("category") != null) ? "<td " + def + ">" + listings.get(id).getString("category") + "</td>" : emptyCell;
				text += (listings.get(id).getString("price") != null) ? "<td " + def + ">" + listings.get(id).getString("price") + "</td>" : emptyCell;
				text += (listings.get(id).getString("currency") != null) ? "<td " + def + ">" + listings.get(id).getString("currency") + "</td>" : emptyCell;
				text += (listings.get(id).getString("country") != null) ? "<td " + def + ">" + listings.get(id).getString("country") + "</td>" : emptyCell;
				text += (listings.get(id).getString("region") != null) ? "<td " + def + ">" + listings.get(id).getString("region") + "</td>" : emptyCell;
				text += (listings.get(id).getString("zip") != null) ? "<td " + def + ">" + listings.get(id).getString("zip") + "</td>" : emptyCell;
				text += (listings.get(id).getString("serial") != null) ? "<td " + def + ">" + listings.get(id).getString("serial") + "</td>" : emptyCell;
				text += (listings.get(id).getString("date") != null) ? "<td " + def + ">" + sdf.format(listings.get(id).getDate("date")) + "</td>" : emptyCell;
				text += (listings.get(id).getString("company") != null) ? "<td " + def + ">" + listings.get(id).getString("company") + "</td>" : emptyCell;
				text += (listings.get(id).getString("new") != null) ? "<td " + def + ">" + listings.get(id).getString("new") + "</td>" : emptyCell;
				text += (listings.get(id).getString("url") != null) ? "<td " + def + "><a href=\"" + listings.get(id).getString("url") + "\">&rarr;</a></td>" : emptyCell;
				text += "</tr>";
			} else {
				text += "<tr>";
				text += "<td " + def + " align=\"right\"><b>" + crawlers.get(id).name + "</b></td>";
				for (int i = 0; i < colspan - 1; i++) {
					text += emptyCell;
				}
				text += "</tr>";
			}
		}
		text += "</table>\n";
		
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
