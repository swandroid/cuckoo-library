package interdroid.swan.cuckoo_sensors;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

import com.google.android.gcm.server.Constants;
import com.google.android.gcm.server.Message;
import com.google.android.gcm.server.Result;
import com.google.android.gcm.server.Sender;

public class RemoteMonitorThread extends Thread {

	private CuckooPoller sensor;
	private String valuePath;
	private Map<String, Object> configuration;
	private String apiKey;
	private String registrationId;

	public RemoteMonitorThread(String registrationId, String apiKey,
			CuckooPoller sensor, final String valuePath,
			final Map<String, Object> configuration) {
		this.registrationId = registrationId;
		this.apiKey = apiKey;
		this.sensor = sensor;
		this.valuePath = valuePath;
		this.configuration = configuration;
	}

	public void run() {
		Map<String, Object> previous = null;
		System.out.println("Starting to monitor: " + valuePath + ", "
				+ configuration + ", " + sensor);
		while (!interrupted()) {
			Map<String, Object> values = sensor.poll(valuePath, configuration);
			if (changed(previous, values)) {
				previous = new HashMap<String, Object>();
				previous.putAll(values);
				// push with GCM
				try {
					//push(registrationId, apiKey, values, true);
					pushFirebase(registrationId, apiKey, values);
				} catch (Exception e) {
					e.printStackTrace(System.out);
					// should not happen
				}
			}
			try {
				sleep(sensor.getInterval(configuration, true));
			} catch (InterruptedException e) {
				System.out.println(e);
				break;
			}
		}
	}

	private boolean changed(Map<String, Object> old, Map<String, Object> current) {
		if (current == null) {
			// new values are not valid
			return false;
		} else if (old == null) {
			// old values were invalid
			return true;
		} else {
			for (String key : old.keySet()) {
				if (!old.get(key).equals(current.get(key))) {
					// yes, we found a change
					return true;
				}
			}
		}
		return false;
	}

	public final void push(String registrationId, String apiKey,
			Map<String, Object> args, boolean delayWhileIdle)
			throws IOException {
		Sender sender = new Sender(apiKey);
		Message.Builder builder = new Message.Builder();
		builder.timeToLive(60 * 60).collapseKey("MAGIC_STRING")
				.delayWhileIdle(delayWhileIdle);
		for (String key : args.keySet()) {
			builder.addData(key, "" + args.get(key));
		}
		Message message = builder.build();
		Result result = sender.send(message, registrationId, 5);
		if (result.getMessageId() != null) {
			String canonicalRegId = result.getCanonicalRegistrationId();
			if (canonicalRegId != null) {
				// same device has more than on registration ID: update database
				System.out
						.println("same device has more than on registration ID: update database");
			} else {
				System.out.println("ok");
			}
		} else {
			String error = result.getErrorCodeName();
			if (error.equals(Constants.ERROR_NOT_REGISTERED)) {
				// application has been removed from device - unregister
				// database
				System.out
						.println("application has been removed from device - unregister database");
			}
			System.out.println("ok 2: " + error);
		}
	}
	
	public final void pushFirebase(String registrationId, String apiKey,
			Map<String, Object> args) {
		
		URL url;
		try {
			url = new URL("https://fcm.googleapis.com/fcm/send");
			HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
		    conn.setRequestMethod("POST");
		    conn.setRequestProperty("Content-Type", "application/json");
		    conn.setRequestProperty("Authorization", "key=" + apiKey);
		    conn.setDoOutput(true);

		    String input = "{\"to\" : \"" + registrationId +" \", \"data\": {";

		    for (String key : args.keySet()) {
				input += "\""+key+"\": \"" + args.get(key) + "\"";
			}
		    input += "}}";
		    
	        //String json = gson.toJson(data, type);
		    OutputStream os = conn.getOutputStream();
		    os.write(input.getBytes("UTF-8"));
		    os.flush();
		    os.close();
		    
		    int responseCode = conn.getResponseCode();
			System.out.println("\nSending 'POST' request to URL : " + url);
			System.out.println("Response Code : " + responseCode);

			BufferedReader in = new BufferedReader(
			        new InputStreamReader(conn.getInputStream()));
			String inputLine;
			StringBuffer response = new StringBuffer();

			while ((inputLine = in.readLine()) != null) {
				response.append(inputLine);
			}
			in.close();

			//print result
			System.out.println(response.toString());
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	    
	}

}
