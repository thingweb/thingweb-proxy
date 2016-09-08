package de.thingweb.proxy;

import java.util.Date;

import de.thingweb.thing.Thing;

public class RestEvent {
	public final Date timestamp;
	public final String name;
	public final RestMethod restMethod;
	public final Thing from;
	public final Thing to;
	public final boolean success;
	public RestEvent(String name, RestMethod restMethod, Thing from, Thing to, boolean success) {
		timestamp = new Date();
		this.name = name;
		this.restMethod = restMethod;
		this.from = from;
		this.to = to;
		this.success = success;
	}
}
