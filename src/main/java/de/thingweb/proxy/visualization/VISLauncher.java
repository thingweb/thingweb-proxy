package de.thingweb.proxy.visualization;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.thingweb.discovery.TDRepository;
import de.thingweb.proxy.ProxyLauncher;
import de.thingweb.proxy.ProxyState;
import de.thingweb.proxy.RestEvent;
import de.thingweb.thing.Metadata;
import de.thingweb.thing.Thing;
import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response.Status;

public class VISLauncher extends NanoHTTPD {
	
	public static final String ANY_CLIENT = "ANY_CLIENT";
	
	private long HOW_LONG_TO_SHOW_EVENTS_IN_MS = 120000; // 1min == 60000 ms
	private long HOW_LONG_TO_SHOW_EVENTS_IN_NS = TimeUnit.MILLISECONDS.toNanos(HOW_LONG_TO_SHOW_EVENTS_IN_MS); //  
	
	private static final Logger log = LoggerFactory.getLogger(VISLauncher.class);
	
	static final String MIME_JSON = "application/json";

	public static final String WEBSITE = "/index.html";
	public static final String NODES = "/nodes";
	public static final String EDGES = "/edges";
	
	
	private static final String NODES_BEGIN = "/*NODES BEGIN*/";
	private static final String NODES_END = "/*NODES END*/";
	
	private static final String EDGES_BEGIN = "/*EDGES BEGIN*/";
	private static final String EDGES_END = "/*EDGES END*/";

	public static int port = 8888;
	
	private boolean _stop = false;
	
	final ProxyState proxyState;
	
	public static final ObjectMapper mapper = new ObjectMapper();

	public VISLauncher(ProxyState proxyState) throws IOException {
		super(port);
		this.proxyState = proxyState;
	}
	
	public void start() throws IOException {
		super.start();
		// start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
		System.out.println("\nTD Visualizer started at http://localhost:" + port + "\n");
		
		while(!_stop) {
			/* run*/
		}
	}
	
	public void stop() {
		// in older version there is no start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
		// https://github.com/NanoHttpd/nanohttpd/issues/266
	}
	
	public void _stop() {
		_stop = true;
		super.stop();
	}
	

	public static StringBuilder convertStreamToString(InputStream is) throws IOException {
		int k;
		StringBuilder sb = new StringBuilder();
		while ((k = is.read()) != -1) {
			sb.append((char) k);
		}
		return sb;
	}
	
	
	StringBuilder replaceNodes(StringBuilder sb) throws Exception {
		int b = sb.indexOf(NODES_BEGIN);
		int e = sb.indexOf(NODES_END) + NODES_END.length();
		
		if(b<0 || e < 0 || b>=e) {
			throw new Exception("Internal Error for replacing nodes");
		}
		
		StringBuilder sbNodes = new StringBuilder();
		List<Thing> things =  this.proxyState.getProxiedThings();
		
		// ANY
		sbNodes.append('{');
		
		sbNodes.append("id: ");
		sbNodes.append("'" + ANY_CLIENT  + "'");
		
		sbNodes.append(',');
		sbNodes.append(" label: ");
		sbNodes.append("'" + ANY_CLIENT + "'");
		
		sbNodes.append(", physics:false ");
		
		sbNodes.append('}');
		if(things.size()> 0) {
			sbNodes.append(',');	
		}
		
		sbNodes.append("\n\r");
		
		//  {id: '1', label: 'Node 1', title: 'ddd'},
		for(int i=0;i<things.size(); i++) {
			Thing t = things.get(i);

			sbNodes.append('{');
			
			sbNodes.append("id: ");
			String n = t.getName();
			n = n.replace(this.proxyState.getProxyPrefix(), "");
			sbNodes.append("'" + n + "'");
			
			sbNodes.append(',');
			sbNodes.append(" label: ");
			sbNodes.append("'" + n + "'");
			
			// avoid dynamic nodes 
			sbNodes.append(", physics:false ");
			
			
			sbNodes.append(',');
			sbNodes.append(" title: ");
			String title = "'" + t.getName();
			
			Metadata md = t.getMetadata();
			if(md.contains("uris")) {
				title += "<br/>uris: " + md.get("uris");	
			}
			if(!t.getProperties().isEmpty()) {
				title += "<br/>properties: " + t.getProperties();	
			} 
			if(!t.getActions().isEmpty()) {
				title += "<br/>actions: " + t.getActions();
			}
			if(!t.getEvents().isEmpty()) {
				title += "<br/>events: " + t.getEvents();	
			} 
			title += "'";
			sbNodes.append(title);
			
			sbNodes.append('}');
			
			// is there another entry
			if(i<(things.size()-1)) {
				sbNodes.append(',');	
			}
			
			sbNodes.append("\n\r");
		}
		
		sb = sb.replace(b, e, sbNodes.toString());
		
		return sb;
	}
	
	StringBuilder replaceEdges(StringBuilder sb) throws Exception {
		int b = sb.indexOf(EDGES_BEGIN);
		int e = sb.indexOf(EDGES_END) + EDGES_END.length();
		
		if(b<0 || e < 0 || b>=e) {
			throw new Exception("Internal Error for replacing edges");
		}
		
		StringBuilder sbEdges = new StringBuilder();
		
		List<RestEvent> events =  this.proxyState.getRestEvents();
		
		//  {id: '1', from: '1', to: '2', title: 'a title <br /> new line', arrows: 'to', label: "a label"},
		for(int i=0;i<events.size(); i++) {
			RestEvent t = events.get(i);
			
			long tms = t.timestampNS; // .getTime();
			if(( System.nanoTime() - tms ) > this.HOW_LONG_TO_SHOW_EVENTS_IN_NS ) {
				continue;
			}

			sbEdges.append('{');
			
			sbEdges.append("id: ");
			sbEdges.append("'" + tms  + "'");
			
			sbEdges.append(',');
			sbEdges.append(" from: ");
			if(t.from == null) {
				sbEdges.append("'" + ANY_CLIENT + "'");
			} else {
				sbEdges.append("'" + t.from + "'");
			}
			
			sbEdges.append(',');
			sbEdges.append(" to: ");
			sbEdges.append("'" + t.to + "'");
			
			sbEdges.append(',');
			sbEdges.append(" label: ");
			sbEdges.append("'" + t.restMethod + " " + t.name + "'");
			
			sbEdges.append(",  title: 'Success=" + t.success +"<br />Timestamp=" + t.timestamp  + "'");
			
//			// avoid dynamic edges 
//			sbEdges.append(", physics:true ");
			
			sbEdges.append(", arrows:'to'");
			
			sbEdges.append('}');
			
			// is there another entry
			if(i<(events.size()-1)) {
				sbEdges.append(',');	
			}
			
			sbEdges.append("\n\r");
		}
		
		sb = sb.replace(b, e, sbEdges.toString());
		
		return sb;
	}

	
	public static void replaceAll(StringBuilder sb, String toReplace, String replacement) {
	    int index = -1;
	    while ((index = sb.lastIndexOf(toReplace)) != -1) {
	        sb.replace(index, index + toReplace.length(), replacement);
	    }
	}
	
    public static String getIpAddress(boolean preferIPv4) throws UnknownHostException, SocketException {
        if(!InetAddress.getLocalHost().isLoopbackAddress()) {
        	log.info("IP_ address: " + InetAddress.getLocalHost().getHostAddress());
            return InetAddress.getLocalHost().getHostAddress();
        } else {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress()) {
                    	// avoid IPV6 ?
                    	String s = addr.getHostAddress();
                    	log.info("IPv address: " + s);
                    	if(preferIPv4 && s.length() > 15) {
                    		// IPV6
                    		log.info("Found IPv6 address? Try to find IPv4");
                    	} else {
                    		return s;
                    	}

                    }
                }
            }
        }

        // well - we tried. but it seems there is only loopback
        return InetAddress.getLocalHost().getHostAddress();
    }
	
	
	@Override
	public Response serve(IHTTPSession session) {
		try {
			String uri = session.getUri();

			if ("/".equals(uri) || "/index.html".equals(uri)) {
				InputStream is = getClass().getResourceAsStream(VISLauncher.WEBSITE);
				if(is == null) {
					throw new Exception("HTML resource not found");
				}
				// return newChunkedResponse(Response.Status.OK, MIME_HTML, is);
				StringBuilder sb = convertStreamToString(is);
				is.close();
				
				replaceAll(sb, "localhost:8080", getIpAddress(true) + ":" + this.getListeningPort());
				
				sb = replaceNodes(sb);
				sb = replaceEdges(sb);
				
				log.info(sb.toString());
				// return newFixedLengthResponse(Status.OK, MIME_HTML, sb.toString());
				return new Response(Response.Status.OK, MIME_HTML, sb.toString());
				
			} else if (NODES.equals(uri)) {
				List<Thing> things = this.proxyState.getProxiedThings();
				
				JSONArray ja = new JSONArray();
				for(Thing t : things) {
					ja.put(t.getName());
				}
				return new Response(Response.Status.OK, MIME_JSON, ja.toString());
			} else if (EDGES.equals(uri)) {
				List<RestEvent> events = this.proxyState.getRestEvents();
				
				int ms = -1;
				try {
					String sms = session.getParms().get("ms");
					ms = Integer.parseInt(sms);
				} catch (Exception e) {
				}
				
				JSONArray ja = new JSONArray();
				for(RestEvent re : events) {
					
					if(ms < 0 || (System.currentTimeMillis()- re.timestamp.getTime()) <= ms ) {
						JSONObject jo = new JSONObject();
						jo.put("timestampNS", re.timestampNS);
						jo.put("timestamp", re.timestamp);
						jo.put("name", re.name);
						jo.put("method", re.restMethod);
						jo.put("from", re.from);
						jo.put("to", re.to);
						jo.put("success", re.success);
						
						ja.put(jo);
					}
					

				}
				return new Response(Response.Status.OK, MIME_JSON, ja.toString());
			} else {
				String msg = "<html><body><h1>Not Found!</h1>\n";
				msg += "</body></html>\n";
				log.info(msg);
				// return newFixedLengthResponse(Status.NOT_FOUND, MIME_HTML, msg);
				return new Response(Response.Status.NOT_FOUND, MIME_HTML, msg);
			}
		} catch (Exception e) {
			String msg = "<html><body><h1>ERROR</h1>\n<p>";
			msg += e.getMessage();
			msg += "</p></body></html>\n";
			log.error(msg);
			// return newFixedLengthResponse(Status.INTERNAL_ERROR, MIME_HTML, msg);
			return new Response(Response.Status.INTERNAL_ERROR, MIME_HTML, msg);
		}

		// return newFixedLengthResponse(msg);
		// return new Response(Response.Status.OK, MIME_HTML, msg);

	}

	public static void main(String[] args) {
		try {
			int limitTDs = 7; // by default -1 --> unlimited
			ProxyLauncher pl = new ProxyLauncher(limitTDs);
			pl.start();
			
			VISLauncher vis = new VISLauncher(pl);
//			vis.setPrefixName(ProxyLauncher.PROXY_PREFIX);
			vis.start();
		} catch (Exception ioe) {
			System.err.println("Couldn't start server:\n" + ioe);
		}
	}

}
