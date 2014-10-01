import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class FrontEndProcessor implements Runnable {

	private Socket clientSocket = null;
	private String ds_addr;
	private int ds_port;
	private TweetsMap fe_tweetsMap;
	private ThreadSafeLock lock;
	private static final Logger logger = LogManager.getLogger("FrontEndServer");

	public FrontEndProcessor(Socket clientSocket, String ds_addr, int ds_port,
			TweetsMap fe_tweetsMap, ThreadSafeLock lock) {
		this.clientSocket = clientSocket;
		this.ds_addr = ds_addr;
		this.ds_port = ds_port;
		this.fe_tweetsMap = fe_tweetsMap;
		this.lock = lock;
	}

	public void run() {
		try {
			/*
			 * Read request from client and parse it by HTTPRequestLineParser
			 */
			BufferedReader br = new BufferedReader(new InputStreamReader(
					clientSocket.getInputStream()));
			String line;
			ArrayList<String> postHearder = new ArrayList<String>();

			while (!(line = br.readLine().trim()).equals("")) {
				postHearder.add(line);
			}
			String uri = null;
			uri = postHearder.get(0);

			OutputStream out = clientSocket.getOutputStream();
			HTTPRequestLine hl = HTTPRequestLineParser.parse(uri);
			HTTPResponseHandler hrh = new HTTPResponseHandler(out);

			/*
			 * Check whether the request is valid
			 */
			if (hl != null) {
				/*
				 * 404 Not found returned for bad endpoint
				 */
				if (!hl.getUripath().equals("tweets")) {
					hrh.response(404, "Not Found", "Not Found!");
					logger.info("This request has a bad endpoint");
				} else {
					/*
					 * If this is a post requst, call postProcessor, 
					 * If this is a get request, call searchProcessor,
					 * Otherwise, we do not provide this method
					 */
					String httpMethod = hl.getMethod().toString();
					if (httpMethod.equals("POST")) {
						logger.info("Post a tweet to data server.................");
						lock.lockWrite();
						postProcessor(br, hrh);
						lock.unlockWrite();
					} else if (httpMethod.equals("GET")) {
						logger.info("Search a serchterm in data server.................");
						lock.lockRead();
						searchProcessor(hl, hrh);
						lock.unlockRead();
					} else {
						hrh.response(405, "Method Not Allowed",
								"We do not provide this method!");
						logger.info("We do not provide this method");
					}
				}
			} else {
				hrh.response(400, "Bad Request", "This is a bad request!");
				logger.info("This is a bad request!");
			}
			out.flush();
			out.close();
		} catch (IOException e) {
			logger.debug(e.getMessage(), e);
		}
	}

	@SuppressWarnings("unchecked")
	private void postProcessor(BufferedReader br, HTTPResponseHandler hrh) {
		try {
			/*
			 * If the post request body is not empty, read it in,
			 * Otherwise, response a 400 Bad Request
			 */
			if (br.ready()) {
				/*
				 * Read the request body in
				 */
				char[] bodyChars = new char[1000];
				br.read(bodyChars);
				StringBuffer sb = new StringBuffer();
				sb.append(bodyChars);
				String postBody = sb.toString().trim();

				JSONParser jp = new JSONParser();
				JSONObject requestBody = (JSONObject) jp.parse(postBody);
				/*
				 * If the post request body contains "text", parse the tweet in
				 * Otherwise, response a 400 Bad Request
				 */
				if (requestBody.containsKey("text")) {
					String valueofRequestBody = requestBody.get("text")
							.toString();
					/*
					 * If the tweet contains no hashtag, response a 400 Bad Request
					 */
					if (!valueofRequestBody.contains("#")) {
						hrh.response(400, "Bad Request",
								"Your tweet does not have any hashtag!");
						logger.info("No hashtag in this tweet!");
					} else {
						String[] tweetStrings = null;
						JSONArray hashtags = new JSONArray();
						tweetStrings = valueofRequestBody.split(" ");
						for (int i = 0; i < tweetStrings.length; i++) {
							if (tweetStrings[i].contains("#")) {
								/*
								 * If the tweet contains empty hashtag, response a 400 Bad Request,
								 * Otherwise, get every hashtag and tweet, 
								 * Then post it to Data Server
								 */
								if (tweetStrings[i].substring(1).isEmpty()) {
									hrh.response(400, "Bad Request",
											"At least one hashtag is empty!");
									logger.info("At least one hashtag is empty!");
									return;
								} else {
									hashtags.add(tweetStrings[i].substring(1));
								}
							}
						}
						JSONObject addTweetBody = new JSONObject();
						addTweetBody.put("tweet", valueofRequestBody);
						addTweetBody.put("hashtags", hashtags);

						/* 
						 * Send post message to data server 
						 */
						Socket postSocket = new Socket(
								InetAddress.getByName(ds_addr), ds_port);
						BufferedWriter wr = new BufferedWriter(
								new OutputStreamWriter(
										postSocket.getOutputStream()));

						String path = "/tweets";
						wr.write("POST " + path + " HTTP/1.1\r\n");
						wr.write("Content-Length: "
								+ valueofRequestBody.length() + "\r\n");
						wr.write("Content-Type: application/json\r\n");
						wr.write("\r\n");
						wr.write(addTweetBody.toString());
						wr.flush();
						wr.close();

						/*
						 * Response a 201 Created to client
						 */
						hrh.response(201, "Created", "Created!");
						logger.info("Saving a tweet into data server!");

						postSocket.close();
					}
				} else {
					hrh.response(400, "Bad Request",
							"You do not post any tweet!");
					logger.info("No tweet in this post request!");
				}
			} else {
				hrh.response(400, "Bad Request", "You do not post anything!");
				logger.info("Post request body is empty!");
			}
		} catch (IOException e) {
			logger.debug(e.getMessage(), e);
		} catch (ParseException e) {
			logger.debug(e.getMessage(), e);
		}
	}

	@SuppressWarnings("unchecked")
	private void searchProcessor(HTTPRequestLine hl, HTTPResponseHandler hrh) {
		HashMap<String, String> searchtermHashMap = hl.getParameters();

		/*
		 * If the parameter map is empty, that means the request contains no q
		 */
		if (searchtermHashMap.isEmpty()) {
			hrh.response(400, "Bad Request",
					"This GET request does not have q parameter!");
			logger.info("No parameter in this GET request!");
		} else if (!searchtermHashMap.containsKey("q")) {
			/*
			 * If the request contains other parameters, response a 400 Bad Request
			 */
			hrh.response(400, "Bad Request", "We only accept q as parameter!");
			logger.info("We only accept q parameter in the GET request!");
		} else {
			String searchterm = searchtermHashMap.get("q");
			HashMap<String, TweetsData> cacheHashMap = fe_tweetsMap
					.getTweetsHashMap();

			try {
				/* 
				 * Send search message to data server 
				 */
				Socket connectToDsSocket = new Socket(
						InetAddress.getByName(ds_addr), ds_port);
				BufferedWriter wr = new BufferedWriter(new OutputStreamWriter(
						connectToDsSocket.getOutputStream()));

				int cache_versionNum = -1;
				
				/* 
				 * If the cache does not have this searchterm, set the versionNum to -1
				 * Then send the GET request to data server 
				 */
				logger.info("Sending a search request to data server...............");

				if (!cacheHashMap.containsKey(searchterm)) {
					wr.write("GET " + "/tweets?q=" + searchterm + "&" + "v="
							+ "-1" + " HTTP/1.1\r\n");
					//wr.write("Host: " + "localhost:4003" + "\r\n");
					wr.write("\r\n");
				} 
				/* 
				 * If the cache has this searchterm, set the versionNum to cache_versionNum
				 * Then send the GET request to data server
				 */
				else {
					TweetsData cache_tweetData = cacheHashMap.get(searchterm);
					cache_versionNum = cache_tweetData.getVersionNum();

					wr.write("GET " + "/tweets?q=" + searchterm + "&" + "v="
							+ cache_versionNum + " HTTP/1.1\r\n");
					//wr.write("Host: " + "localhost:4003" + "\r\n");
					wr.write("\r\n");
				}
				wr.flush();

				/* 
				 * Receive the response from data server 
				 */
				logger.info("Receiving a search response from data server...............");

				BufferedReader recerive_br = new BufferedReader(
						new InputStreamReader(
								connectToDsSocket.getInputStream()));
				String receive_line = null;
				ArrayList<String> receive_Hearder = new ArrayList<String>();

				while (!(receive_line = recerive_br.readLine().trim())
						.equals("")) {
					receive_Hearder.add(receive_line);
				}

				char[] bodyChars = new char[1000];
				recerive_br.read(bodyChars);
				StringBuffer sb = new StringBuffer();
				sb.append(bodyChars);
				String receive_body = sb.toString().trim();
				
				/* 
				 * If the response from data server is "Your version is up-to-date!",
				 * get the tweets of this serachterm from cache and send it to client
				 */
				if (receive_body.equals("Your version is up-to-date!")) {
					TweetsData final_tweetsData = cacheHashMap.get(searchterm);
					JSONArray final_tweetArray = final_tweetsData
							.getTweetsArray();
					JSONObject final_response = new JSONObject();
					final_response.put("q", searchterm);
					final_response.put("tweets", final_tweetArray);
					hrh.response(200, "OK", final_response.toString());
					logger.info("Response to client directly............");
				} 
				/* 
				 * If the response from data server is not "Your version is up-to-date!",
				 * get the tweets of this serachterm from data server and send it to client
				 */
				else {
					JSONParser jp = new JSONParser();
					JSONObject receiveBody = (JSONObject) jp
							.parse(receive_body);
					int ds_versionNum = Integer.valueOf(receiveBody.get("v")
							.toString());

					JSONObject final_response = new JSONObject();
					JSONArray final_tweetArray = (JSONArray) receiveBody
							.get("tweets");

					/* 
					 * If the versionNum in the response is -1,
					 * that means the data server also contains no this searchterm
					 * Then send an response with empty tweet array to client
					 */
					if (ds_versionNum == -1) {
						final_response.put("q", searchterm);
						final_response.put("tweets", final_tweetArray);
						hrh.response(200, "OK", final_response.toString());
						logger.info("No search result............");
					} 
					/* 
					 * If the versionNum in the response is not -1,
					 * that means the cache need to updata its version,
					 * Then send an response with the tweets about 
					 * this serchterm from data server to client,
					 * Then update the cache to latest version
					 */
					else {
						final_response.put("q", searchterm);
						final_response.put("tweets", final_tweetArray);
						hrh.response(200, "OK", final_response.toString());
						logger.info("Response to client and update the cache............");
						/* Update CacheHashMap */
						TweetsData final_tweetsData = new TweetsData();
						final_tweetsData.setVersionNum(ds_versionNum);
						final_tweetsData.setTweetsArray(final_tweetArray);
						fe_tweetsMap.setTweetsHashMap(searchterm,
								final_tweetsData);
					}
				}
				connectToDsSocket.close();
			} catch (IOException e) {
				logger.debug(e.getMessage(), e);
			} catch (ParseException e) {
				logger.debug(e.getMessage(), e);
			}
		}
	}
}