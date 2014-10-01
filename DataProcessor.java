import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class DataProcessor implements Runnable {

	private Socket clientSocket = null;
	private TweetsMap ds_tweetsMap;
	private ThreadSafeLock lock;
	private static final Logger logger = LogManager.getLogger("DataServer");

	public DataProcessor(Socket clientSocket, TweetsMap ds_tweetsMap,
			ThreadSafeLock lock) {
		this.clientSocket = clientSocket;
		this.ds_tweetsMap = ds_tweetsMap;
		this.lock = lock;
	}

	public void run() {

		try {
			/*
			 * Read request and parse it by HTTPRequestLineParser
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
					String httpMethod = hl.getMethod().toString();
					if (httpMethod.equals("POST")) {
						/*
						 * Request from PostHttpServer, save the tweet into data
						 * server
						 */
						logger.info("Receive a post request from front end.................");
						lock.lockWrite();
						storeTweet(br, hrh);
						lock.unlockWrite();
					} else if (httpMethod.equals("GET")) {
						/*
						 * Request from SearchHttpServer, check the version
						 * number and respond to SearchHttpServer
						 */
						logger.info("Receive a search request from front end.................");
						lock.lockRead();
						searchTweet(hl, hrh);
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

	private void storeTweet(BufferedReader br, HTTPResponseHandler hrh) {
		try {
			/*
			 * If the post request contains body, read it in, Otherwise, return
			 * a 400 Bad Request
			 */
			if (br.ready()) {
				Thread.sleep(3 * 1000);		/*For test purpose*/
				char[] bodyChars = new char[1000];
				br.read(bodyChars);
				StringBuffer sb = new StringBuffer();
				sb.append(bodyChars);
				String postBody = sb.toString().trim();

				/*
				 * Convert the post request to JSONObject, Then get the hashtags
				 * and tweet
				 */
				JSONParser jp = new JSONParser();
				JSONObject requestBody = (JSONObject) jp.parse(postBody);

				if (requestBody.containsKey("tweet")
						&& requestBody.containsKey("hashtags")) {

					String tweet = requestBody.get("tweet").toString();

					JSONArray hashtags = (JSONArray) requestBody
							.get("hashtags");
					HashMap<String, TweetsData> tweetsHashMap = ds_tweetsMap
							.getTweetsHashMap();

					for (int i = 0; i < hashtags.size(); i++) {
						/*
						 * If the data server contains no this hashtag, create a
						 * new one in the data hashmap
						 */
						if (!tweetsHashMap.containsKey(hashtags.get(i))) {
							logger.info("Create a new hashtag in data server!");
							TweetsData tweetsData = new TweetsData();
							tweetsData.setVersionNum(0);
							tweetsData.addTweets(tweet);
							ds_tweetsMap.setTweetsHashMap(hashtags.get(i)
									.toString(), tweetsData);
						}
						/*
						 * If the data server contains this hashtag and does not
						 * have this tweet about this hashtag, save the tweet in
						 * and updata the version number
						 */
						else {
							logger.info("Update a hashtag in data server!");
							TweetsData tweetsData = tweetsHashMap.get(hashtags
									.get(i));
							JSONArray tweetsArray = tweetsData.getTweetsArray();
							if (!tweetsArray.contains(tweet)) {
								int newVersionNum = tweetsData.getVersionNum() + 1;
								tweetsData.setVersionNum(newVersionNum);
								tweetsData.addTweets(tweet);
							}
						}
					}

					/*
					 * Respond to Post Server
					 */
					hrh.response(201, "Created", "Created!");
					logger.info("Successfully save the tweet into data server!");

				} else {
					hrh.response(400, "Bad Request",
							"You do not post any tweet!");
					logger.info("No tweet or no hashtag in this post request!");
				}
			} else {
				hrh.response(400, "Bad Request",
						"Front End do not post anything!");
				logger.info("Post request body is empty!");
			}
		} catch (IOException e) {
			logger.debug(e.getMessage(), e);
		} catch (ParseException e) {
			logger.debug(e.getMessage(), e);
		} catch (InterruptedException e) {
			logger.debug(e.getMessage(), e);
		}
	}

	@SuppressWarnings("unchecked")
	private void searchTweet(HTTPRequestLine hl, HTTPResponseHandler hrh) {
		HashMap<String, String> termandnum = hl.getParameters();

		/*
		 * If the parameter map is empty, that means the request contains no q
		 */
		if (termandnum.isEmpty()) {
			hrh.response(400, "Bad Request",
					"This GET request does not have q parameter!");
			logger.info("No parameter in this GET request!");
		}
		/*
		 * If the request contains no q or v, return a 400 Bad Request
		 */
		else if (!termandnum.containsKey("q") || !termandnum.containsKey("v")) {
			hrh.response(400, "Bad Request",
					"Your request does not have q or v as parameters!");
			logger.info("Your request does not have q or v as parameters!");
		} else {
			/*
			 * Get the searchterm and the version number
			 */
			try {
				Thread.sleep(3 * 1000);	/*For test purpose*/
			} catch (InterruptedException e) {
				logger.debug(e.getMessage(), e);
			}		

			String search_term = termandnum.get("q");
			int search_versionNumber = Integer.valueOf(termandnum.get("v"));

			HashMap<String, TweetsData> tweetsHashMap = ds_tweetsMap
					.getTweetsHashMap();

			/*
			 * If data server does not have this searchterm, set the versionNum
			 * to -1 Then send a response with empty array to front end
			 */
			if (!tweetsHashMap.containsKey(search_term)) {
				logger.info("No tweet in data server about this term!");

				JSONObject responseObject = new JSONObject();
				responseObject.put("q", search_term);
				responseObject.put("v", -1);

				JSONArray emptyArray = new JSONArray();
				responseObject.put("tweets", emptyArray);

				hrh.response(200, "OK", responseObject.toString());
				
			} else {
				TweetsData tweetsData = tweetsHashMap.get(search_term);
				int ds_versionNumber = tweetsData.getVersionNum();
				JSONArray tweetsArray = tweetsData.getTweetsArray();

				/*
				 * If data server contains this searchterm, and the versin num
				 * in front end is up-to-date, Then send a up-to-date response
				 * to front end
				 */
				if (ds_versionNumber == search_versionNumber) {
					hrh.response(304, "Not Modified",
							"Your version is up-to-date!");
					logger.info("Front end cache is up-to-date about this term!");
				}
				/*
				 * If the versin num in front end is not up-to-date, Then send a
				 * response with latest version number and tweets about this
				 * searchterm to front end
				 */
				else if (ds_versionNumber > search_versionNumber) {
					JSONObject responseObject = new JSONObject();
					responseObject.put("q", search_term);
					responseObject.put("v", ds_versionNumber);
					responseObject.put("tweets", tweetsArray);

					hrh.response(200, "OK", responseObject.toString());
					logger.info("Front end cache need to update about this term!");
				}
			}
		}
	}
}