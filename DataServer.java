import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DataServer implements Runnable {

	private ServerSocket serverSocket = null;
	private ExecutorService threadPool = Executors.newFixedThreadPool(10);
	private int serverPort;

	public TweetsMap ds_tweetsMap = new TweetsMap();

	public ThreadSafeLock lock = new ThreadSafeLock();
	private static final Logger logger = LogManager.getLogger("DataServer");

	public DataServer(int port) {
		this.serverPort = port;
	}

	public void run() {
		logger.info("DataServer Start #############################");
		createServerSocket();
		while (true) {
			Socket clientSocket = null;
			try {
				clientSocket = this.serverSocket.accept();
				logger.info("A new request received by DataServer");
			} catch (IOException e) {
				logger.debug(e.getMessage(), e);
			}
			this.threadPool.execute(new DataProcessor(clientSocket, ds_tweetsMap, lock));
		}
	}

	private void createServerSocket() {
		try {
			this.serverSocket = new ServerSocket(this.serverPort);
		} catch (IOException e) {
			logger.debug(e.getMessage(), e);
		}
	}

	public static void main(String[] args) {
		int PORT = 4003;
		DataServer hs = new DataServer(PORT);
		new Thread(hs).start();
	}
}