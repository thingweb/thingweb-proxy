package de.thingweb.proxy;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;

import de.thingweb.binding.BindingTools;
import de.thingweb.client.Callback;
import de.thingweb.client.Client;
import de.thingweb.client.ClientFactory;
import de.thingweb.client.UnsupportedException;
import de.thingweb.desc.ThingDescriptionParser;
import de.thingweb.discovery.TDRepository;
import de.thingweb.proxy.visualization.VISLauncher;
import de.thingweb.security.TokenRequirements;
import de.thingweb.servient.ServientBuilder;
import de.thingweb.servient.ThingInterface;
import de.thingweb.servient.ThingServer;
import de.thingweb.thing.Action;
import de.thingweb.thing.Content;
import de.thingweb.thing.Property;
import de.thingweb.thing.Thing;

public class ProxyLauncher implements ProxyState {

	private static final Logger log = LoggerFactory.getLogger(ProxyLauncher.class);

	private int nextPort = 9000;

	public static final String PROXY_PREFIX = "$Proxy$";

	TDRepository tdRepo = new TDRepository(TDRepository.ETH_URI);

	Map<String, String> uriForward = new HashMap<>();
	
	List<RestEvent> restEvents = new ArrayList<>();
	
	List<TDContainer> proxiedTDs = new ArrayList<>();
	
	
	class TDContainer {
		final ThingInterface tiLocal;
		final String tdOriginalRemote;
		public TDContainer(final ThingInterface tiLocal, String tdOriginalRemote) {
			this.tiLocal = tiLocal;
			this.tdOriginalRemote = tdOriginalRemote;
		}
	}
	
//	Map<String, Function<Object, Object>> propertyFunctions = new HashMap<>();

	public ProxyLauncher() throws Exception {
		this(-1);
	}

	public ProxyLauncher(int limitTDs) throws Exception {
		ServientBuilder.initialize();

		final TokenRequirements tokenRequirements = null; // NicePlugFestTokenReqFactory.createTokenRequirements();
		ThingServer server = ServientBuilder.newThingServer(tokenRequirements);

		// fetch all existing "things" in repo and create proxy for it
		JSONObject jo = tdRepo.nameOfThings(); // all things
//		StringBuilder sbNodes = new StringBuilder();

		Iterator<String> keys = jo.keys();
		while (keys.hasNext()) {

			if (limitTDs > 0 && proxiedTDs.size() >= limitTDs) {
				// abort
				System.out.println("Limit reached after " + limitTDs + " TDs");
				return;
			}

			String key = keys.next();
			try {

				JSONObject td = jo.getJSONObject(key);
				String tdOriginal = td.toString();

				String tdName = td.getString("name");
				if (tdName.startsWith(PROXY_PREFIX)) {
					// do not proxy proxies
				} else {
					// replace name "voter" to "$Proxy$voter"
					td.put("name", PROXY_PREFIX + tdName);

					// replace URI IPs
					if (td.has("uris")) {
						Object u = td.get("uris");
						// System.out.println(u.getClass());
						if (u instanceof String || u instanceof JSONString) {
							String su = td.getString("uris");
							String uriProxy = checkUri(su);
							td.put("uris", uriProxy);
						} else if (u instanceof org.json.JSONArray) {
							JSONArray au = td.getJSONArray("uris");
							JSONArray auNew = new JSONArray();
							for (int i = 0; i < au.length(); i++) {
								String aui = au.getString(i);
								String auiProxy = checkUri(aui);
								auNew.put(auiProxy);
							}
							td.put("uris", auNew);
						} else {
							// mhh, not really as expected
						}
					}

					String std = td.toString();
					System.out.println(std.substring(0, Math.min(std.length(), 80)) + " ...");

					Thing thingDesc = ThingDescriptionParser.fromBytes(td.toString().getBytes());
					ThingInterface ti = server.addThing(thingDesc);
					// proxiedTDs.add(new TDContainer(new ThingInterfaceProxy(ti, restEvents), tdOriginal));
					proxiedTDs.add(new TDContainer(ti, tdOriginal));

					// attach handlers
					attachHandlers(ti, tdOriginal);
				}

			} catch (Exception e) {
				// any issue
				log.error("Could not create proxy for " + key + ". " + e.getMessage());
			}
		}
	}

	// wait for async callback to return
	class SyncCallback implements Callback {

		volatile boolean isDone = false;

		boolean isInError = false;
		String isInErrorMessage;

		Content response;

		public void onGet(String propertyName, Content response) {
			this.response = response;
			isDone = true;
		}

		public void onGetError(String propertyName) {
			isInError = true;
			isDone = true;
		}

		public void onPut(String propertyName, Content response) {
			this.response = response;
			isDone = true;
		}

		public void onPutError(String propertyName, String message) {
			isInError = true;
			isInErrorMessage = message;
			isDone = true;
		}

		public void onObserve(String propertyName, Content response) {
			this.response = response;
			isDone = true;
		}

		public void onObserveError(String propertyName) {
			isInError = true;
			isDone = true;
		}

		public void onAction(String actionName, Content response) {
			isDone = true;
		}

		public void onActionError(String actionName) {
			isInError = true;
			isDone = true;
		}
	}

	void attachHandlers(final ThingInterface tiLocal, String tdOriginalRemote)
			throws JsonParseException, IOException, UnsupportedException, URISyntaxException {

		ClientFactory cf = new ClientFactory();
		Thing tRemote = ThingDescriptionParser.fromBytes(tdOriginalRemote.toString().getBytes());
		Client client4Remote = cf.getClientFromTD(tRemote);

		Map<String, Object> thingProps = new HashMap<String, Object>();
		
		
		
		/*
		 * GET
		 */
		Consumer<Object> callbackGET = (input) -> {
			if (input instanceof Property) {

				Property property = (de.thingweb.thing.Property) input;
				
				Object result = null;
				
				boolean successGET = false;
				
				try {
					SyncCallback callbackGETX = new SyncCallback();
				
					client4Remote.get(property.getName(), callbackGETX);
	
					// wait till callback is done
					while (!callbackGETX.isDone) {
						// loop
					}
					
					// done now
					if (callbackGETX.isInError) {
						String msg = "Callback for " + property.getName() + " is in error!";
						log.error(msg);
					} else {
						result =  callbackGETX.response;
						
						if(result instanceof Content) {
							Object value = de.thingweb.util.encoding.ContentHelper.getValueFromJson((Content) result);
							log.info("Content GET value is " + value);	
						}
						
						// need to update value?
						thingProps.put(property.getName(), result);
						tiLocal.setProperty(property, result); // TODO not really sure why this is needed as well
						
						successGET = true;
					}
				} catch (Exception e) {
				}
				
				// TODO hack, but how to remove previous PUT that seems to be necessary?
				if(restEvents.size()> 0 ) {
					int lastIndex = restEvents.size()-1;
					RestEvent re = restEvents.get(lastIndex);
					// 1000000 ns == 1 ms
					if((System.nanoTime() - re.timestampNS ) < 5000000 ) { // 5 millisecs
						// previous update was due to  GET --> remove it from log
						this.restEvents.remove(lastIndex);
					}
				}
				
				this.restEvents.add(new RestEvent(property.getName(), RestMethod.GET, VISLauncher.ANY_CLIENT, client4Remote.getThing().getName(), successGET));
			}
		};
		tiLocal.onPropertyRead(callbackGET);
		
		
		/*
		 * PUT
		 */
		List<Property> properties = tiLocal.getThingModel().getProperties();
		for (Property property : properties) {
			// Initialize property value
			property.getValueType();
			// Object initValue = new Integer(0);
			// ti.setProperty(property, initValue);

			// register onPropertyUpdate
			tiLocal.onPropertyUpdate(property.getName(), (input) -> {
				log.info("try to set " + property.getName() + " value to " + input);
				
				boolean successPUT = false;

				try {
					Content cont;
					if(input instanceof Content) {
						cont = (Content) input;
						Object value = de.thingweb.util.encoding.ContentHelper.getValueFromJson(cont);
						log.info("Content PUT value is " + value);
						
					} else {
						cont = de.thingweb.util.encoding.ContentHelper.makeJsonValue(input); // , de.thingweb.thing.MediaType.APPLICATION_JSON);
					}
					
					SyncCallback callbackPUT = new SyncCallback();

					client4Remote.put(property.getName(), cont, callbackPUT);

					// wait till callback is done
					while (!callbackPUT.isDone) {
						// loop
					}

					// done now
					if (callbackPUT.isInError) {
						String msg = "Callback for " + property.getName() + " is in error!";
						log.error(msg);
					} else {
						thingProps.put(property.getName(), input);
						// tiLocal.setProperty(property, input);
						log.info("Setting " + property.getName() + " value to " + input + " was succesful");
						successPUT = true;
					}
				} catch (Exception e) {
					log.error(e.getMessage());
				}
				
				this.restEvents.add(new RestEvent(property.getName(), RestMethod.PUT, VISLauncher.ANY_CLIENT, client4Remote.getThing().getName(), successPUT));
			});
		}


		/*
		 * POST
		 */
		List<Action> actions= tiLocal.getThingModel().getActions();
		for (Action action : actions) {
			
			Function<Object, Object> funcPOST = (input) -> {
				boolean successPOST = false;
				Object result = null;
				
				try {
					Content cont;
					if(input instanceof Content) {
						cont = (Content) input;
						Object value = de.thingweb.util.encoding.ContentHelper.getValueFromJson(cont);
						log.info("Action value is " + value);
					} else {
						cont = de.thingweb.util.encoding.ContentHelper.makeJsonValue(input);
					}
					
					SyncCallback callbackPOST = new SyncCallback();
					client4Remote.action(action.getName(), cont, callbackPOST);

					// wait till callback is done
					while (!callbackPOST.isDone) {
						// loop
					}

					// done now
					if (callbackPOST.isInError) {
						String msg = "Callback for " + action.getName() + " is in error!";
						log.error(msg);
					} else {
						result = callbackPOST.response;
						successPOST = true;
					}

				} catch (Exception e) {
				}
				
				this.restEvents.add(new RestEvent(action.getName(), RestMethod.POST, VISLauncher.ANY_CLIENT, client4Remote.getThing().getName(), successPOST));
				return result;
			};
			
			tiLocal.onActionInvoke(action.getName(), funcPOST);
		}
		
	}

	public void start() throws Exception {
		ServientBuilder.start();

		// start visualizer
		VISLauncher vis = new VISLauncher(this);
//		vis.setPrefixName(PROXY_PREFIX);
		vis.start();
	}

	private boolean isPortInUse(String host, int port) {
		// Assume no connection is possible.
		boolean result = false;

		try {
			(new Socket(host, port)).close();
			result = true;
		} catch (IOException e) {
			// Could not connect.
		}

		return result;
	}

	String checkUri(String uriOriginal) throws Exception {
		URI u = new URI(uriOriginal);
		u.getScheme();

		if ("coap".equals(u.getScheme()) || "http".equals(u.getScheme())) {
			// replace host
			String host = BindingTools.getIpAddress(); // u.getHost();

			// update IP
			while (isPortInUse(host, nextPort)) {
				nextPort++;
			}
			int port = nextPort++; // u.getPort();

			URI newUri = new URI(u.getScheme(), u.getUserInfo(), host, port, u.getPath(), u.getQuery(),
					u.getFragment());
			String sNewUri = newUri.toString();

			// store uri mappings
			this.uriForward.put(sNewUri, uriOriginal);
			String msg = "Replace uri " + uriOriginal + " with " + sNewUri;
			log.debug(msg);
			System.out.println(msg);

			return sNewUri;
		} else {
			// we do not support other formats yet --> keep it as
			// e.g., WebSocket
			throw new Exception("No support for scheme:" + u.getScheme() + "");
		}
	}

	public static void main(String[] args) throws Exception {
		int limitTDs = 7; // by default -1 --> unlimited
		ProxyLauncher pl = new ProxyLauncher(limitTDs);
		pl.start();
	}

	@Override
	public List<Thing> getProxiedThings() {
		List<Thing> ts = new ArrayList<>();
		for(TDContainer tdc : this.proxiedTDs) {
			ts.add(tdc.tiLocal.getThingModel());
		}
		return ts;
	}

	@Override
	public List<RestEvent> getRestEvents() {
		// TODO maybe just report "some"
		return this.restEvents;
	}
	
	@Override
	public String getProxyPrefix() {
		return PROXY_PREFIX;
	}

}
