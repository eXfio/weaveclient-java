package org.exfio.weave.client;

import org.exfio.weave.client.WeaveClient.StorageVersion;

public class WeaveStorageV5Params implements WeaveClientParams {
	public String baseURL;
	public String user;
	public String password;
	public String syncKey;
	
	public StorageVersion getStorageVersion() {
		return StorageVersion.v5;
	}
}
