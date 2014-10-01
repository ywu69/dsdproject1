import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class FrontEndServer implements Runnable {

	private ServerSocket serverSocket = null;
	private ExecutorService threadPool = Executors.newFixedThreadPool(10);
	private int serverPort;
	private String ds_addr;
	private int ds_port;
	private static final Logger logger = LogManager.getLogger("FrontEndServer");

	public TweetsMap fe_tweetsMap = new TweetsMap();

	public ThreadSafeLock lock = new ThreadSafeLock();

	public void run() {
		logger.info("FrontEndServer Start #############################");
		createServerSocket();
		while (true) {
			Socket clientSocket = null;
			try {
				clientSocket = this.serverSocket.accept();
				logger.info("A new request received by FrontEndServer");
			} catch (IOException e) {
				logger.debug(e.getMessage(), e);
			}
			this.threadPool.execute(new FrontEndProcessor(clientSocket, ds_addr, ds_port, fe_tweetsMap, lock));
		}
	}

	private void createServerSocket() {
		try {
			this.serverSocket = new ServerSocket(this.serverPort);
		} catch (IOException e) {
			logger.debug(e.getMessage(), e);
		}
	}
	
	/*
	 * Read the configuration file
	 */
	private void configurationRead(String filePath){
		JSONParser parser = new JSONParser();
		JSONObject jsonObject = null;
		try {
			jsonObject = (JSONObject) parser.parse(new FileReader(filePath));
		} catch (FileNotFoundException e) {
			logger.debug(e.getMessage(), e);
		} catch (IOException e) {
			logger.debug(e.getMessage(), e);
		} catch (ParseException e) {
			logger.debug(e.getMessage(), e);
		}

		String portsString = (String) jsonObject.get("FE_port");
		serverPort = Integer.valueOf(portsString);
		ds_addr = (String) jsonObject.get("BE_addr");
		String ds_portString = (String) jsonObject.get("BE_port");
		ds_port = Integer.valueOf(ds_portString);
	}
	
	public static void main(String[] args) {
		FrontEndServer fs = new FrontEndServer();
		fs.configurationRead(args[0]);
		new Thread(fs).start();
	}
}