package qubase.engine;


import java.util.Properties;
import javax.mail.*;
import javax.mail.internet.*;

/**
 * provides an API to send email messages
 * @author martin racko <mr@lectura.de>
 */
public class Email {
	
	
	/**
	 * sends the email message
	 * @param recipients an array of strings with receivers email addresses
	 * @param from email of the sender
	 * @param subject subject of the message
	 * @param text
	 * @param local_level what priority this message has
	 */
	public static void send(String[] recipients, String subject, String text) {
		
		String host = "smtp.gmail.com";
		String username = LecturaCrawlerEngine.getProperties().getProperty("email-user");
		String password = LecturaCrawlerEngine.getProperties().getProperty("email-pass");
 
		Properties props = System.getProperties();
		props.put("mail.smtp.starttls.enable", "true");
		props.put("mail.smtp.host", host);
		props.setProperty("mail.transport.protocol", "smtps");
		props.put("mail.smtp.user", username);
		props.put("mail.smtp.password", password);
		props.put("mail.smtp.port", "465");
		props.put("mail.smtps.auth", "true");
		Session session = Session.getDefaultInstance(props, null);
		
		Message message = new MimeMessage(session);
		
		try {
		    InternetAddress[] address_to = new InternetAddress[recipients.length]; 
		    for (int i = 0; i < recipients.length; i++) {
		        address_to[i] = new InternetAddress(recipients[i]);
		    }
		    message.setRecipients(Message.RecipientType.TO, address_to);
	
		    message.setSubject(subject);
		    message.setText(text);
		    
		    Transport transport = session.getTransport("smtps");
		    transport.connect(host, username, password);
		    transport.sendMessage(message, message.getAllRecipients());
		    transport.close(); 
		} catch (Exception e) {
			System.out.println(e.getMessage());
			e.printStackTrace();
		}
	}
}
