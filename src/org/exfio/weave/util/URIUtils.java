/*******************************************************************************
 * Copyright (c) 2014 Richard Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 * 
 * Contributors:
 *     Richard Hirner (bitfire web engineering) - initial API and implementation
 ******************************************************************************/
package org.exfio.weave.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class URIUtils {
	
	// handles invalid URLs/paths as good as possible
	public static String sanitize(String original) {
		if (original == null)
			return null;
		
		Pattern p = Pattern.compile("^((https?:)?//([^/]+))?(.*)", Pattern.CASE_INSENSITIVE);
		// $1: "http://hostname" or "https://hostname" or "//hostname" or empty (hostname may end with":port")
		// $2: "http:" or "https:" or empty
		// $3: hostname (may end with ":port") or empty
		// $4: path or empty

		Matcher m = p.matcher(original);
		if (m.matches()) {
			String	schema = m.group(2),
					host = m.group(3),
					path = m.group(4);
			
			if (host != null)
				// sanitize host name (don't replace "[", "]", ":" for IP address literals and port numbers)
				// 	"@" should be used for user name/password, but this case shouldn't appear in our URLs
				for (char c : new char[] { '@', ' ', '<', '>', '"', '#', '{', '}', '|', '\\', '^', '~', '`' })
					host = host.replace(String.valueOf(c), "%" + Integer.toHexString(c));
			
			if (path != null)
				// rewrite unsafe characters:
				//	" ", "<", ">", """, "#", "{", "}", "|", "\", "^", "~", "["], "]", "`"
				// do not rewrite "%" because we assume that URLs should be already encoded correctly
				for (char c : new char[] { ' ', '<', '>', '"', '#', '{', '}', '|', '\\', '^', '~', '[', ']', '`' })
					path = path.replace(String.valueOf(c), "%" + Integer.toHexString(c));
			
			String url = (schema != null) ? schema : "";
			if (host != null)
				url = url + "//" + host;
			if (path != null)
				url = url + path;
			
			if (!url.equals(original))
				Log.getInstance().warn("Trying to repair invalid URL: " + original + " -> " + url);
			return url;
			
		} else {
			
			Log.getInstance().warn("Couldn't sanitize URL: " + original);
			return original;
			
		}
	}
}
