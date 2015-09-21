package com.testfairy.app;

import org.apache.http.cookie.Cookie;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.http.*;
import org.apache.http.client.*;
import org.apache.http.protocol.*;
import org.apache.http.client.protocol.*;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.cookie.BasicClientCookie;

public class CookieUtils {

	private Pattern cookiePattern = Pattern.compile("([^=]+)=([^\\;]*);?\\s?");

	/**
	 * Parse a cookie string and create a Map object with key/value.
	 *
	 * @param cookies
	 * @return Map
	 */
	public Map<String, String> parseCookieString(String cookies) {

		HashMap<String, String> out = new HashMap<String, String>();

		Matcher matcher = cookiePattern.matcher(cookies);
		while (matcher.find()) {
			String key = matcher.group(1);
			String value = matcher.group(2);
			out.put(key, value);
		}

		return out;
	}

	/**
	 * Parse a cookie-string and update an HttpContext with values
	 *
	 * @param context
	 * @param cookies
	 */
	public void setCookies(HttpContext context, String cookies) {
		CookieStore cookieStore = new BasicCookieStore();
		Map<String, String> map = parseCookieString(cookies);
		for (String key : map.keySet()) {
			BasicClientCookie cookie = new BasicClientCookie(key, map.get(key));
			cookie.setDomain(".testfairy.com");
			cookieStore.addCookie(cookie);
		}
		context.setAttribute(ClientContext.COOKIE_STORE, cookieStore);
	}
}
