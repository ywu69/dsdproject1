import java.util.HashMap;


public class TweetsMap {

	private HashMap<String, TweetsData> tweetsHashMap = new HashMap<String, TweetsData>();

	public void setTweetsHashMap(String key, TweetsData tweetsData) {
		tweetsHashMap.put(key, tweetsData);
	}

	public HashMap<String, TweetsData> getTweetsHashMap() {
		return tweetsHashMap;
	}
}