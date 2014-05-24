package org.exfio.weave.net;

import lombok.Getter;

public class HttpException extends ch.boye.httpclientandroidlib.HttpException {
	private static final long serialVersionUID = -4805778240079377401L;
	
	@Getter private int code;
	
	public HttpException(int code, String message) {
		super(message);
		this.code = code;
	}
	
	public boolean isClientError() {
		return code/100 == 4;
	}

}