package com.cesidiodibenedetto.filechooser;

import java.util.HashMap;
import java.util.Map;
import java.io.File;

import org.apache.cordova.CordovaActivity;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaResourceApi;
import org.apache.cordova.PluginResult;

import com.ipaulpro.afilechooser.utils.FileUtils;

/**
 * FileChooser is a PhoneGap plugin that acts as polyfill for Android KitKat and web
 * applications that need support for <input type="file">
 *
 */
public class FileChooser extends CordovaPlugin {

    private CallbackContext callbackContext = null;
    private static final String TAG = "FileChooser";
    private static final int REQUEST_CODE = 6666; // onActivityResult request code

    private void showFileChooser(String mime) {
        // Use the GET_CONTENT intent from the utility class
        Intent target = FileUtils.createGetContentIntent(mime);
        // Create the chooser Intent

        Context context = this.cordova.getActivity().getApplicationContext();
        String packageName = context.getPackageName();
        int chooserTitleStringId = context.getResources().getIdentifier("chooser_title", "string", packageName);
        Intent intent = Intent.createChooser(target, context.getResources().getString(chooserTitleStringId));

        try {
            this.cordova.startActivityForResult((CordovaPlugin) this, intent, REQUEST_CODE);
        } catch (ActivityNotFoundException e) {
            // The reason for the existence of aFileChooser
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if( requestCode == REQUEST_CODE) {
                if (resultCode == Activity.RESULT_OK) {
                    if (data != null) {
                        // Get the URI of the selected file
                        final Uri uri = data.getData();
                        Log.i(TAG, "URI: " + uri.toString());
                        JSONObject obj = new JSONObject();
                        try {
                            File f = FileUtils.getFile(this.cordova.getActivity(), uri);
                            if (f != null) {
                                final String url = f.toURI().toURL().toString();
                                Log.i(TAG, "URL: " + url);
                                obj.put("url", url);
                                obj.put("name", f.getName());
                            } else {
                                // Android 10+ scoped storage 不允许查询 _data 列，直接返回 content URI
                                // JS 层的 window.resolveLocalFileSystemURL 可以处理 content:// URI
                                Log.i(TAG, "Fallback to content URI: " + uri);
                                obj.put("url", uri.toString());
                                obj.put("name", getDisplayName(uri));
                            }
                            this.callbackContext.success(obj);
                        } catch (Exception e) {
                            // 路径解析失败时同样 fallback 到 content URI
                            Log.e(TAG, "File select error, fallback to content URI", e);
                            try {
                                obj.put("url", uri.toString());
                                obj.put("name", getDisplayName(uri));
                                this.callbackContext.success(obj);
                            } catch (Exception je) {
                                this.callbackContext.error(je.getMessage());
                            }
                        }
                    }
                } else {
                    this.callbackContext.error("No file selected");
                }
        }
    }

    /** 通过 ContentResolver 查询文件显示名，供 Android 10+ content URI 使用 */
    private String getDisplayName(Uri uri) {
        Cursor cursor = null;
        try {
            cursor = this.cordova.getActivity().getContentResolver().query(
                uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(0);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error querying display name", e);
        } finally {
            if (cursor != null) cursor.close();
        }
        return uri.getLastPathSegment();
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) {
            this.callbackContext = callbackContext;
            if (action.equals("open")) {
                try {
                    JSONObject options = args.getJSONObject(0);
                    final String mime = options.optString("mime", "*/*");
                    showFileChooser(mime);
                } catch (Exception e) {
                    callbackContext.error(e.getMessage());
                }
                return true;
            }
            else {
                return false;
            }
    }

}
