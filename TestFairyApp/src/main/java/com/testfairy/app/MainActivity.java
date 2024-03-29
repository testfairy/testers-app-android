package com.testfairy.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.StrictMode;
import android.util.Log;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity {
	private static final String BASE_URL = "https://app.testfairy.com";

	private static final String MIME_TYPE_APK = "application/vnd.android.package-archive";
	private static final String USER_AGENT = "TestersApp/" + BuildConfig.VERSION_NAME + " android " + BuildConfig.FLAVOR;

	private static final String LOGIN_URL = BASE_URL + "/login/";
	private static final String GOOGLE_SIGNIN_URL = BASE_URL + "/login/with/google/";

	private static final String TEMP_DOWNLOAD_FILE = "testfairy-app-download.apk";

	private static final String GOOGLE_CLIENT_ID = BuildConfig.GOOGLE_CLIENT_ID;

	private static final int RC_GET_TOKEN = 9002;
	private static final int RC_INSTALL_PACKAGE = 10123;

	private File localFile;
	private WebView webView;
	private ProgressDialog dialog;
	private FileDownloader downloader;
	private ProgressBar progressBar;
	private long backPressedTime = 0;
	private String packageToInstall = null;

	private GoogleSignInClient googleSignInClient;

	private class MyWebViewClient extends WebViewClient {
		private boolean wasLastPageLogin = false;

		@Override
		public boolean shouldOverrideUrlLoading(WebView view, String url) {
			if (url.contains("/signup/google/")) {
				doGoogleLogin();
				return true;
			}

			if (url.contains("/download/")) {
				startDownload(url);
			}

			if (url.contains("/logout/")) {
				googleSignInClient.signOut();
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

			Log.d(Config.TAG, "onPageFinished: Current URL = " + webView.getUrl());
			if (isAtUrlPathExactly(url, "/login/")) {
				wasLastPageLogin = true;
				clearHistory(view);
			} else {
				if (wasLastPageLogin && isAtUrlPathExactly(url, "/my/")) {
					//if the last page was login delete the browser history, so the cant go back to this page
					clearHistory(view);
				}

				wasLastPageLogin = false;
			}
		}

		private void clearHistory(WebView view) {
			Log.d(Config.TAG, "clearingHistory");
			view.clearHistory();
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
		String url = webView.getUrl();
		if (isAtUrlPathExactly(url, "/login/") || isAtUrlPathExactly(url, "/my/")) {
			promptOrExit();
		} else {
			Log.d(Config.TAG, "canGoBack -> go back");
			webView.goBack();
		}
	}

	@Override
	public void onBackPressed() {
		Log.d(Config.TAG, "onBackPressed: Current URL = " + webView.getUrl());
		String url = webView.getUrl();
		if (isAtUrlPathExactly(url, "/login/") || isAtUrlPathExactly(url, "/my/")) {
			promptOrExit();
		} else {
			webView.loadUrl("javascript:if(window.MyController){MyController.onAndroidBackPressed();} else {Android.doBackPressed();}");
		}
	}

	private void promptOrExit() {
		if (backPressedTime + 2000 > System.currentTimeMillis()) {
			Log.d(Config.TAG, "exit the app -> exit");
			finish();
		} else {
			Log.d(Config.TAG, "exit the app -> start timer");
			Toast.makeText(this, "Press once again to exit!", Toast.LENGTH_SHORT).show();
			backPressedTime = System.currentTimeMillis();
		}
	}

	private class MyWebChromeClient extends WebChromeClient {
		public void onProgressChanged(WebView view, int progress) {
			setPageProgress(progress);
		}

		public void onConsoleMessage(String message, int lineNumber, String sourceID) {
			Log.d(Config.TAG, "console: " + message + " -- From line " + lineNumber + " of " + sourceID);
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

		progressBar = findViewById(R.id.progressBar);
		progressBar.setVisibility(View.GONE);

		webView = findViewById(R.id.webView);
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

		GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
				.requestIdToken(GOOGLE_CLIENT_ID)
				.requestEmail()
				.build();

		googleSignInClient = GoogleSignIn.getClient(this, gso);
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		if (requestCode == RC_GET_TOKEN) {
			Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
			handleSignInResult(task);
		}

		if (requestCode == RC_INSTALL_PACKAGE) {
			if (resultCode == RESULT_OK) {
				Toast.makeText(this, "App installed successfully", Toast.LENGTH_LONG).show();

				if (packageToInstall != null) {
					launchApp(packageToInstall);
				}
			} else {
				Toast.makeText(this, "Installation failed", Toast.LENGTH_LONG).show();

				Log.e(Config.TAG, "Error installing package: " + resultCode);
			}
		}

		packageToInstall = null;
	}

	public void launchApp(String packageName) {
		Intent intent = new Intent();
		intent.setPackage(packageName);

		PackageManager pm = getPackageManager();
		List<ResolveInfo> resolveInfos = pm.queryIntentActivities(intent, 0);

		Collections.sort(resolveInfos, new ResolveInfo.DisplayNameComparator(pm));

		if(resolveInfos.size() > 0) {
			ResolveInfo launchable = resolveInfos.get(0);
			ActivityInfo activity = launchable.activityInfo;
			ComponentName name=new ComponentName(activity.applicationInfo.packageName, activity.name);
			Intent i = new Intent(Intent.ACTION_MAIN);

			i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
			i.setComponent(name);

			startActivity(i);
		} else {
			Intent launchIntent = null;

			try{
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
					launchIntent = getPackageManager().getLeanbackLaunchIntentForPackage(packageName);
				}
			} catch (NoSuchMethodError e){
			}

			if (launchIntent == null) {
				launchIntent = getPackageManager().getLaunchIntentForPackage(packageName);
			}

			if (launchIntent != null)  {
				launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
				startActivity(launchIntent);
			}
		}
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
	private void alertWithMessage(String message) {
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

		localFile = new File(prepareAndGetDownloadDirectory(this), "/" + TEMP_DOWNLOAD_FILE);
		Log.v(Config.TAG, "Using " + localFile.getAbsolutePath() + " for storing apk locally");

		dialog = new ProgressDialog(this);
		dialog.setMessage("Downloading..");
		dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		dialog.setIndeterminate(true);
		dialog.setOnCancelListener(onDialogCancelled);
		dialog.setCancelable(true);
		dialog.setCanceledOnTouchOutside(false);
		dialog.setProgressNumberFormat("%1d KB/%2d KB");

		dialog.show();

		//buildUpgradeUrl = "http://app.testfairy.com/download/64VK8C1D68S2VP1RT7NQA30Z145VWJA5ACJNNZTF5TFAC/MakeMeBald_v1.1-testfairy.apk";
		Uri uri = Uri.parse(url);
		if (uri.getQueryParameterNames().contains("packageName")) {
			packageToInstall = uri.getQueryParameter("packageName");

			if (packageToInstall.length() == 0) {
				packageToInstall = null;
			}
		}

		downloader = new FileDownloader(url, localFile);
		downloader.setDownloadListener(downloadListener);

		// get cookies from web client
		String cookies = CookieManager.getInstance().getCookie(url);
		if (cookies != null) {
			CookieUtils utils = new CookieUtils();
			Map<String, String> map = utils.parseCookieString(cookies);
//				Log.v(Config.TAG, "COOKIE: " + map.get("u") + ", url=" + url);

			for (String key : map.keySet()) {
				Log.v(Config.TAG, "Copying cookie " + key + " = " + map.get(key) + " to file downloader");
				downloader.addCookie(key, map.get(key));
			}
		}

		downloader.start();
	}

	private File prepareAndGetDownloadDirectory(Context context) {
		File dir = context.getExternalCacheDir();

		if (dir == null) {
			dir = context.getCacheDir();
		}

		File autoUpdateRootFolder = new File(dir, "testfairy-auto-update");

		FileUtils.deleteEntirely(autoUpdateRootFolder);
		autoUpdateRootFolder.mkdirs();

		return autoUpdateRootFolder;
	}

	private void doGoogleLogin() {
		Intent signInIntent = googleSignInClient.getSignInIntent();
		startActivityForResult(signInIntent, RC_GET_TOKEN);
		progressBar.setProgress(0);
		progressBar.setVisibility(View.VISIBLE);
	}

	private static boolean isAtUrlPathExactly(String url, String path) {
		Uri uri = Uri.parse(url);
		return path.equals(uri.getPath());
	}

	private void handleSignInResult(@NonNull Task<GoogleSignInAccount> completedTask) {
		try {
			GoogleSignInAccount account = completedTask.getResult(ApiException.class);
			String idToken = account.getIdToken();
			String url = GOOGLE_SIGNIN_URL + "?idToken=" + idToken;
			webView.loadUrl(url);
		} catch (ApiException e) {
			Log.w(Config.TAG, "handleSignInResult:error", e);
		}
	}

	private FileDownloader.DownloadListener downloadListener = new FileDownloader.DownloadListener() {

		private long fileSize;
		private long lastPrintout;
		private long downloadStartedTimestamp;

		private static final long THRESHOLD = 256 * 1024;

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

			Log.v(Config.TAG, String.format("Done downloading %.2f KB", fileSize / 1000.0f));

			long diff = System.currentTimeMillis() - downloadStartedTimestamp;
			float secs = diff / 1000.0f;
			Log.v(Config.TAG, String.format("Downloaded completed in %.2f seconds", secs));

			StrictMode.VmPolicy vmPolicy = StrictMode.getVmPolicy();
			StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
			StrictMode.setVmPolicy(builder.build());

			Uri filePath = Uri.fromFile(localFile);

			if (Build.VERSION.SDK_INT >= 24) {
				try {
					filePath = FileProvider.getUriForFile(MainActivity.this, getPackageName() + ".provider",
							localFile);
				} catch (Throwable t) {
					Log.e(Config.TAG, "Error", t);
				}
			}

			Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
			intent.setDataAndType(filePath, MIME_TYPE_APK);
			intent.setFlags(
//					Intent.FLAG_ACTIVITY_CLEAR_TOP |
//							Intent.FLAG_ACTIVITY_NEW_TASK |
							Intent.FLAG_GRANT_READ_URI_PERMISSION
			);
			intent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true);
			intent.putExtra(Intent.EXTRA_RETURN_RESULT, true);
			intent.putExtra(Intent.EXTRA_ALLOW_REPLACE, true);
			intent.putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, getApplicationInfo().packageName);

			try {
				startActivityForResult(intent, RC_INSTALL_PACKAGE);
			} catch (ActivityNotFoundException e) {
				Log.e(Config.TAG, "Installation Failed", e);

				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						Toast.makeText(MainActivity.this, "Installation Failed", Toast.LENGTH_SHORT).show();
					}
				});
			}

			StrictMode.setVmPolicy(vmPolicy);
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
