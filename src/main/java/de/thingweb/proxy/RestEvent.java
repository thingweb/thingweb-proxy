package de.thingweb.proxy;

import java.util.Date;

import de.thingweb.thing.Thing;

public class RestEvent {
	public final Date timestamp;
	public final long timestampNS;
	public final String name;
	public final RestMethod restMethod;
//	public final Thing from;
	public final String from;
//	public final Thing to;
	public final String to;
	public final boolean success;
	public RestEvent(String name, RestMethod restMethod, String from, String to, boolean success) {
		timestampNS = System.nanoTime(); // new Date();
		timestamp = new Date();
		this.name = name;
		this.restMethod = restMethod;
		this.from = from;
		this.to = to;
		this.success = success;
	}
}
