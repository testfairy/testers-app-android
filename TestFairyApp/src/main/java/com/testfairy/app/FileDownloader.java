package com.testfairy.app;

import android.util.Log;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;

public class FileDownloader {

	private String url;
	private File localFile;
	private DownloadListener listener;
	volatile private boolean aborted;

	private HashMap<String, String> cookies = new HashMap<String, String>();

	public interface DownloadListener {
		void onDownloadStarted();
		void onDownloadFailed();
		void onDownloadCompleted();
		void onDownloadProgress(int offset, int total);
	}

	public FileDownloader(String url, File localFile) {
		this.url = url;
		this.localFile = localFile;
	}

	public void abort() {
		// abort download
		aborted = true;
	}

	public void setDownloadListener(DownloadListener listener) {
		this.listener = listener;
	}

	public void start() {
		aborted = false;

		Thread t = new Thread(downloadThread);
		t.start();
	}

	public void addCookie(String key, String value) {
		cookies.put(key, value);
	}

	private boolean isAborted() {
		return aborted;
	}

	private Runnable downloadThread = new Runnable() {
		@Override
		public void run() {
			try {
				Log.v(Config.TAG, "Downloading " + url);

				CookieStore cookieStore = new BasicCookieStore();
				for (String key: cookies.keySet()) {
//					Log.v(Config.TAG, "Copying " + key + "=" + cookies.get(key) + " into cookie store");
					BasicClientCookie cookie = new BasicClientCookie(key, cookies.get(key));
					cookie.setDomain(".testfairy.com");
					cookieStore.addCookie(cookie);
				}

				HttpClient client = new DefaultHttpClient();
				HttpContext context = new BasicHttpContext();
				context.setAttribute(ClientContext.COOKIE_STORE, cookieStore);
				HttpGet get = new HttpGet(url);
				HttpResponse response = client.execute(get, context);
				HttpEntity entity = response.getEntity();
				InputStream input = entity.getContent();

				listener.onDownloadStarted();

				int fileLength = (int) entity.getContentLength();
				listener.onDownloadProgress(0, fileLength);

				OutputStream output = new FileOutputStream(localFile);

				byte data[] = new byte[65536];
				int total = 0;
				int count;
				while (!isAborted() && ((count = input.read(data)) != -1)) {
					output.write(data, 0, count);

					total += count;
					listener.onDownloadProgress(total, fileLength);
				}

				output.flush();
				output.close();

				if (isAborted()) {
					Log.v(Config.TAG, "Download aborted by request");

					// abort http get request
					get.abort();

					// download aborted prematurely
					listener.onDownloadFailed();

					// remove this half-baked file
					localFile.delete();
					return;
				}

				// close file handle normally
				input.close();

				// just to be sure!
				listener.onDownloadProgress(fileLength, fileLength);
				listener.onDownloadCompleted();

				Log.v(Config.TAG, "Download completed successfully");

			} catch (Exception e) {
				Log.e("TestApp", "Failed downloading " + url, e);
				listener.onDownloadFailed();
			}
		}
	};
}
