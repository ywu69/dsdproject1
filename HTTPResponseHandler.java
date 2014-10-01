import java.io.IOException;
import java.io.OutputStream;

public class HTTPResponseHandler {

	private OutputStream responseOut;

	public HTTPResponseHandler(OutputStream responseOut) {
		this.responseOut = responseOut;
	}

	public void response(int responseType, String responseHeader,
			String responseBody) {
		String responsebody = responseBody + "\n";
		String responseheaders = "HTTP/1.1 " + responseType + " "
				+ responseHeader + "\n" + "Content-Length: "
				+ responsebody.getBytes().length + "\n\n";
		try {
			responseOut.write(responseheaders.getBytes());
			responseOut.write(responsebody.getBytes());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}