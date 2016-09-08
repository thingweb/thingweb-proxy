package de.thingweb.proxy;

import java.util.List;

import de.thingweb.thing.Thing;

public interface ProxyState {
	
	public List<Thing> getProxiedThings();
	
	public List<RestEvent> getRestEvents();
	
	public String getProxyPrefix();

}
