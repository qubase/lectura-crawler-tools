package qubase.suite;

import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.pool.PoolStats;

public class ConnectionMonitor extends Thread {
	private PoolingHttpClientConnectionManager connectionManager;
	private int interval = 60000;
	private int idle = 1800;
	private Logger logger = Logger.getLogger(this.getClass().getName());
	
	public void run() {
		boolean ok = true;
		while (ok) {
			try {
				Thread.sleep(interval);
			} catch (InterruptedException e) {
				logger.severe("Failed to wait: " + e.getMessage());
				ok = false;
			}
			
			PoolStats stats = connectionManager.getTotalStats();
			String text = "Max: " + stats.getMax() 
					+ ", Available: " + stats.getAvailable()
					+ ", Pending: " + stats.getPending()
					+ ", Leased: " + stats.getLeased();
			logger.info("Purging connections: " + text);
			
			connectionManager.closeExpiredConnections();
			connectionManager.closeIdleConnections(idle, TimeUnit.SECONDS);
		}
	}
	
	public ConnectionMonitor(PoolingHttpClientConnectionManager connectionManager, int interval, int idle) {
		this.connectionManager = connectionManager;
		this.interval = interval;
		this.idle = idle;
	}
}
