package qubase.statistics;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.UnknownHostException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Properties;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

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
	
	public static void main(String[] args) {
		String emailBody = null;
		try {
			initProps();
			if (!initMongo()) {
				String message = "Not able to authenticate to MongoDB database: " + props.getProperty("mongodb-db");
				throw new Exception(message);
			}
			loadCrawlers();
			String[] recipients = props.getProperty("email-recipients").split("//s*,//s*");
			emailBody = prepareEmail();
			Email.send(recipients, "Lectura Crawler Statistics", emailBody, props.getProperty("email-user"), props.getProperty("email-pass"), true);
			
		} catch (Exception e) {
			StringWriter stackTrace = new StringWriter();
			e.printStackTrace(new PrintWriter(stackTrace));
			emailBody = stackTrace.toString();
			String[] recipients = {"info@lectura.de"};
			try {
				recipients = props.getProperty("email-recipients").split("//s*,//s*");
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
		String tableHead = "<tr>";
		tableHead += "<th " + headStyle + " width=\"24\">&nbsp;</th>";
		tableHead += "<th " + headStyle + ">ID</th>";
		tableHead += "<th " + headStyle + ">Name</th>";
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
		tableHead += "</tr>\n";
		
		String colspan = "15";
		
		String titleOK = "Seems to be OK";
		String titleOFF = "This one's OFF";
		String titleFuckedUp = "There's a problem";
		String imgOK = "<img src=\"https://dl.dropboxusercontent.com/u/6521559/117-todo.png\" title=\"" + titleOK + "\"/>";
		String imgOFF = "<img src=\"https://dl.dropboxusercontent.com/u/6521559/21-skull.png\" title=\"" + titleOFF + "\"/>";
		String imgFuckedUp = "<img src=\"https://dl.dropboxusercontent.com/u/6521559/25-weather.png\" title=\"" + titleFuckedUp + "\"/>";
		
		String tableTail = "<tr><td colspan=\"" + colspan + "\">"
				+ imgOK + "&nbsp;&nbsp;&nbsp;" + titleOK + "&nbsp;&nbsp;|&nbsp;&nbsp;"
				+ imgOFF + "&nbsp;&nbsp;&nbsp;" + titleOFF + "&nbsp;&nbsp;|&nbsp;&nbsp;"
				+ imgFuckedUp + "&nbsp;&nbsp;&nbsp;" + titleFuckedUp
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
				if (crawlers.containsKey(portalId)) {
					name = crawlers.get(portalId).name;
					status = crawlers.get(portalId).status;
				}
				
				
				String defaultStyle = "style=\"border-bottom: 1px solid #404040; background: #b2ffb2; color: #505050\"";
				String offStyle = "style=\"border-bottom: 1px solid #404040; background: #efefef; color: #a0a0a0\"";
				String alarmStyle = "style=\"border-bottom: 1px solid #404040; background: #ffb0b0; color: #505050\"";
				
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
							: defaultStyle;
				
				String img = (!status.equals("1")) 
						? imgOFF 
						: (alarm)
							? imgFuckedUp
							: imgOK;
				
				String alarmCellStyle = "style=\"border-bottom: 1px solid #404040; background: #ff6666; color: #505050\"";
				String tableRow = "<tr>";
				tableRow += "<td " + cellStyle + " width=\"24\">" + img + "</td>";
				tableRow += "<td " + cellStyle + ">" + portalId + "</td>";
				tableRow += "<td " + cellStyle + "><b>" + name + "</b></td>";
				tableRow += "<td " + cellStyle + ">" + ((status.equals("1")) ? "ON" : "OFF") + "</td>";
				tableRow += "<td " + cellStyle + ">" + report.getInt("listingsAllTime") + "</td>";
				String thisCellStyle = (report.getInt("listings") == 0) ? (status.equals("1")) ? alarmCellStyle : cellStyle : cellStyle;
				tableRow += "<td " + thisCellStyle + ">" + report.getInt("listings") + "</td>";
				thisCellStyle = (report.getInt("price") == 0) ? (status.equals("1")) ? alarmCellStyle : cellStyle : cellStyle;
				tableRow += "<td " + thisCellStyle + ">" + report.getInt("price") + "</td>";
				thisCellStyle = (report.getInt("currency") == 0) ? (status.equals("1")) ? alarmCellStyle : cellStyle : cellStyle;
				tableRow += "<td " + thisCellStyle + ">" + report.getInt("currency") + "</td>";
				thisCellStyle = (report.getInt("year") == 0) ? (status.equals("1")) ? alarmCellStyle : cellStyle : cellStyle;
				tableRow += "<td " + thisCellStyle + ">" + report.getInt("year") + "</td>";
				thisCellStyle = (report.getInt("counter") == 0) ? (status.equals("1")) ? alarmCellStyle : cellStyle : cellStyle;
				tableRow += "<td " + thisCellStyle + ">" + report.getInt("counter") + "</td>";
				thisCellStyle = (report.getInt("category") == 0) ? (status.equals("1")) ? alarmCellStyle : cellStyle : cellStyle;
				tableRow += "<td " + thisCellStyle + ">" + report.getInt("category") + "</td>";
				thisCellStyle = (report.getInt("serial") == 0) ? (status.equals("1")) ? alarmCellStyle : cellStyle : cellStyle;
				tableRow += "<td " + thisCellStyle + ">" + report.getInt("serial") + "</td>";
				thisCellStyle = (report.getInt("country") == 0) ? (status.equals("1")) ? alarmCellStyle : cellStyle : cellStyle;
				tableRow += "<td " + thisCellStyle + ">" + report.getInt("country") + "</td>";
				thisCellStyle = (report.getInt("region") == 0) ? (status.equals("1")) ? alarmCellStyle : cellStyle : cellStyle;
				tableRow += "<td " + thisCellStyle + ">" + report.getInt("region") + "</td>";
				thisCellStyle = (report.getInt("zip") == 0) ? (status.equals("1")) ? alarmCellStyle : cellStyle : cellStyle;
				tableRow += "<td " + thisCellStyle + ">" + report.getInt("zip") + "</td>";
				tableRow += "</tr>\n";
				
				text += tableRow;
			}
		}
		
		text += tableTail;
		text += "</table>\n";
		text += "</body>\n";
		text += "</html>";
		return text;
	}
	
	private static void loadCrawlers() throws Exception {
		//read the XML configuration file
		File crawlerFile = new File(props.getProperty("crawler-config"));
		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
		Document doc = dBuilder.parse(crawlerFile);
		doc.getDocumentElement().normalize();
		
		XPath xPath =  XPathFactory.newInstance().newXPath();
		NodeList _crawlers = (NodeList) xPath.compile("/crawler-config/crawler").evaluate(doc, XPathConstants.NODESET);
		
		int size = _crawlers.getLength();
		for (int i = 0; i < size; i++) {
			Element c = (Element) _crawlers.item(i);
			Integer id = Integer.parseInt(c.getAttribute("id"));
			String status = c.getAttribute("status");
			String name = c.getElementsByTagName("name").item(0).getTextContent();
			Crawler crawler = new Crawler();
			crawler.name = name;
			crawler.status = status;
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
