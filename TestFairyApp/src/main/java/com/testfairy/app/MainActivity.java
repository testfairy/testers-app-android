package com.testfairy.app;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.content.FileProvider;
import android.util.Log;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.MimeTypeMap;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.testfairy.TestFairy;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;

import java.io.File;
import java.util.Map;

public class MainActivity extends Activity {

	private static final String MIME_TYPE_APK = "application/vnd.android.package-archive";
	private static final String USER_AGENT = "TestersApp/" + Config.VERSION + " android mobile";

	private static final String LOGIN_URL = "https://app.testfairy.com/login/";
	private static final String TEMP_DOWNLOAD_FILE = "testfairy-app-download.apk";

	private static final String ACCOUNT_TYPE = "com.testfairy.app";

	private File localFile;
	private WebView webView;
	private ProgressDialog dialog;
	private FileDownloader downloader;
	private ProgressBar progressBar;

	private boolean isLoginPage = false;
	private long backPressedTime = 0;

	private class UpdateTestFairyAccount implements Runnable {

		private String url;

		public UpdateTestFairyAccount(String url) {
			this.url = url;
		}
			
		@Override
		public void run() {
			try {
				String cookies = CookieManager.getInstance().getCookie(url);

				CookieUtils utils = new CookieUtils();

				HttpClient client = new DefaultHttpClient();
				HttpContext context = new BasicHttpContext();
				utils.setCookies(context, cookies);
				HttpGet get = new HttpGet("https://app.testfairy.com/login/me/");
				HttpResponse response = client.execute(get, context);
				HttpEntity entity = response.getEntity();
				String result = EntityUtils.toString(entity);
				JSONObject json = new JSONObject(result);
				String email = json.getString("email");

				AccountManager manager = AccountManager.get(MainActivity.this);
				Account newAccount = new Account(email, ACCOUNT_TYPE);
				Account[] accounts = manager.getAccounts();
				boolean found = false;
				for (Account account: accounts) {
					if (account.equals(newAccount)) {
						found = true;
						break;
					}
				}

				if (!found) {
					Log.d(Config.TAG, "Adding email " + newAccount.name + " to TestFairy account manager");
					manager.addAccountExplicitly(newAccount, "", null);
				}

			} catch (Throwable e) {
				// not logged in probably, or network error
			}
		}
	}

	private class MyWebViewClient extends WebViewClient {
		@Override
		public boolean shouldOverrideUrlLoading(WebView view, String url) {
			if (url.contains("/download/")) {
				startDownload(url);
			}
			return false;
		}

		@Override
		public void onPageStarted(WebView view, String url, Bitmap favicon) {
			super.onPageStarted(view, url, favicon);
		}

		@Override
		public void onPageFinished(WebView view, String url) {
			super.onPageFinished(view, url);

			setPageProgress(100);

			if (url.contains("/login")) {
				isLoginPage = true;
			} else {
				if (isLoginPage) {
					//if the last page was login delete the browser history, so the cant go back to this page
					Log.d("console", "clearHistory");
					view.clearHistory();
				}

				isLoginPage = false;
			}

			// check cookies, see if a user has changed
			Thread t = new Thread(new UpdateTestFairyAccount(url));
			t.start();
		}
	}

	public static class WebAppInterface {
		MainActivity activity;

		WebAppInterface(MainActivity activity) {
			this.activity = activity;
		}

		@JavascriptInterface
		public void doBackPressed() {
			activity.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					activity.goBack();
				}
			});
		}
	}

	private void goBack() {
		if (webView.canGoBack()) {
			Log.d("console WebAppInterface", "canGoBack -> go back");
			webView.goBack();
		} else {
			//exit the app
			if (backPressedTime + 2000 > System.currentTimeMillis()) {
				Log.d("console WebAppInterface", "exit the app -> exit");
				MainActivity.this.finish();
			} else {
				Log.d("console WebAppInterface", "exit the app -> start timer");
				Toast.makeText(MainActivity.this, "Press once again to exit!", Toast.LENGTH_SHORT).show();
				backPressedTime = System.currentTimeMillis();
			}
		}
	}

	@Override
	public void onBackPressed() {
		if (isLoginPage) {
			if (backPressedTime + 2000 > System.currentTimeMillis()) {
				Log.d("onBackPressed LoginPage", "exit the app -> exit");
				finish();
			} else {
				Log.d("onBackPressed LoginPage", "exit the app -> start timer");
				Toast.makeText(this, "Press once again to exit!", Toast.LENGTH_SHORT).show();
				backPressedTime = System.currentTimeMillis();
			}
		} else {
			webView.loadUrl("javascript:MyController.onAndroidBackPressed()");
		}
	}

	private class MyWebChromeClient extends WebChromeClient {
		public void onProgressChanged(WebView view, int progress) {
			setPageProgress(progress);
		}

		public void onConsoleMessage(String message, int lineNumber, String sourceID) {
			Log.d("console ", message + " -- From line " + lineNumber + " of " + sourceID);
		}
	}

	private void setPageProgress(int progress) {
		progressBar.setProgress(progress);
		progressBar.setVisibility(progress < 100 ? View.VISIBLE : View.INVISIBLE);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {

//		this.requestWindowFeature(Window.FEATURE_NO_TITLE);
//		this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		TestFairy.begin(this, "b5f48b5b410537bbb8781507af019e07307f2eac");

		progressBar = (ProgressBar) findViewById(R.id.progressBar);
		progressBar.setVisibility(View.GONE);

		webView = (WebView) findViewById(R.id.webView);
		webView.setWebViewClient(new MyWebViewClient());
		webView.setWebChromeClient(new MyWebChromeClient());

		webView.addJavascriptInterface(new WebAppInterface(this), "Android");

		// enable javascript. must be called before addJavascriptInterface
		WebSettings webSettings = webView.getSettings();
		webSettings.setJavaScriptEnabled(true);
		webSettings.setSavePassword(false);


		webSettings.setUserAgentString(USER_AGENT);
//		webView.addJavascriptInterface(new MyJSObject(), "TFAPP");

//		SharedPreferences prefs = getPreferences(MODE_PRIVATE);
//		String uCookie = prefs.getString("u", null);
//		if (uCookie != null) {
//			editor.putBoolean(Strings.TERMS_APPROVED_PREF, true);
//			editor.commit();
//			CookieManager.getInstance().setCookie("u=" + uCookie);
//		}

//		SharedPreferences.Editor editor = prefs.edit();

		webView.loadUrl(LOGIN_URL);
	}

	private DialogInterface.OnCancelListener onDialogCancelled = new DialogInterface.OnCancelListener() {
		@Override
		public void onCancel(DialogInterface dialog) {
			if (downloader != null) {
				Log.v(Config.TAG, "User pressed on cancel");
				downloader.abort();
				downloader = null;
			}
		}
	};

	/**
	 * Display an AlertDialog on screen
	 *
	 * @param message
	 * @return
	 */
	private void alertWithMessage(String message)
	{
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage(message);
		builder.setCancelable(true);
		builder.setIcon(android.R.drawable.ic_dialog_alert);
		builder.show();
	}

	/**
	 * Start downloading APK from url
	 *
	 * @param url
	 * @return
	 */
	private void startDownload(String url) {

		// find where to keep files locally
		File storageDir = getExternalFilesDir(null);
		if (storageDir == null) {
			Log.i(Config.TAG, "getExternalFilesDir() returned null");
			storageDir = getFilesDir();
			if (storageDir == null) {
				Log.e(Config.TAG, "getFilesDir() also returned null!");
				alertWithMessage("Could not download app to device");
				return;
			}
		}

		localFile = new File(storageDir.getAbsolutePath() + "/" + TEMP_DOWNLOAD_FILE);
		Log.v(Config.TAG, "Using " + localFile.getAbsolutePath() + " for storing apk locally");

		dialog = new ProgressDialog(this);
//		dialog.setMax(0);
//		dialog.setTitle("Please Wait");
		dialog.setMessage("Downloading..");
		dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		dialog.setIndeterminate(true);
		dialog.setOnCancelListener(onDialogCancelled);
		dialog.setCancelable(true);
		dialog.setCanceledOnTouchOutside(false);
		dialog.setProgressNumberFormat("%1d KB/%2d KB");

		dialog.show();

		//buildUpgradeUrl = "http://app.testfairy.com/download/64VK8C1D68S2VP1RT7NQA30Z145VWJA5ACJNNZTF5TFAC/MakeMeBald_v1.1-testfairy.apk";
		downloader = new FileDownloader(url, localFile);
		downloader.setDownloadListener(downloadListener);

		// get cookies from web client
		String cookies = CookieManager.getInstance().getCookie(url);
		if (cookies != null) {
			CookieUtils utils = new CookieUtils();
			Map<String, String> map = utils.parseCookieString(cookies);
//				Log.v(Config.TAG, "COOKIE: " + map.get("u") + ", url=" + url);

			for (String key: map.keySet()) {
				Log.v(Config.TAG, "Copying cookie " + key + " = " + map.get(key) + " to file downloader");
				downloader.addCookie(key, map.get(key));
			}
		}

		downloader.start();
	}

	private FileDownloader.DownloadListener downloadListener = new FileDownloader.DownloadListener() {

		private long fileSize;
		private long lastPrintout;
		private long downloadStartedTimestamp;

		private static final long THRESHOLD = 256*1024;

		@Override
		public void onDownloadStarted() {
			fileSize = 0;
			lastPrintout = 0;
			downloadStartedTimestamp = System.currentTimeMillis();
		}

		@Override
		public void onDownloadFailed() {
		}

		@Override
		public void onDownloadCompleted() {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					dialog.setIndeterminate(true);
					dialog.dismiss();
				}
			});

			Log.v(Config.TAG, String.format("Done downloading %.2f KB", fileSize/1000.0f));

			long diff = System.currentTimeMillis() - downloadStartedTimestamp;
			float secs = diff/1000.0f;
			Log.v(Config.TAG, String.format("Downloaded completed in %.2f seconds", secs));

			Intent intent = new Intent(Intent.ACTION_VIEW);
			Uri uri = Uri.fromFile(localFile);
			if (Build.VERSION.SDK_INT >= 24) {
				uri = FileProvider.getUriForFile(
					MainActivity.this,
					getApplicationContext().getPackageName() + ".provider",
					localFile
				);
				intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
			}

			intent.setDataAndType(uri, MIME_TYPE_APK);
			startActivity(intent);
		}

		@Override
		public void onDownloadProgress(final int offset, final int total) {

			MainActivity.this.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					dialog.setIndeterminate(false);
					dialog.setMax(total >> 10);
					dialog.setProgress(offset >> 10);

					if ((offset - lastPrintout) >= THRESHOLD) {
						lastPrintout = offset;
						Log.d(Config.TAG, "Download progress: " + offset + " / " + total);
					}

					fileSize = total;
				}
			});
		}
	};
}
