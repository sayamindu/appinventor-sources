// -*- mode: java; c-basic-offset: 2; -*-
// Copyright 2009-2011 Google, All Rights reserved
// Copyright 2011-2012 MIT, All rights reserved
// Released under the MIT License https://raw.github.com/mit-cml/app-inventor/master/mitlicense.txt

package com.google.appinventor.components.runtime;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;

import com.google.appinventor.components.runtime.util.OAuth2Helper;
import com.google.appinventor.components.runtime.util.OnInitializeListener;
import com.google.appinventor.components.runtime.util.SdkLevel;
import com.google.appinventor.components.runtime.util.SmsBroadcastReceiver;

import com.google.appinventor.components.annotations.DesignerComponent;
import com.google.appinventor.components.annotations.DesignerProperty;
import com.google.appinventor.components.annotations.PropertyCategory;
import com.google.appinventor.components.annotations.SimpleEvent;
import com.google.appinventor.components.annotations.SimpleFunction;
import com.google.appinventor.components.annotations.SimpleObject;
import com.google.appinventor.components.annotations.SimpleProperty;
import com.google.appinventor.components.annotations.UsesLibraries;
import com.google.appinventor.components.annotations.UsesPermissions;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.common.PropertyTypeConstants;
import com.google.appinventor.components.common.YaVersion;

import android.app.Activity;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.telephony.gsm.SmsManager;
import android.telephony.gsm.SmsMessage;
import android.util.Log;
import android.widget.Toast;

/**
 * A component capable of sending and receiving text messages via SMS.

 * @author markf@google.com (Mark Friedman)
 * @author ram8647@gmail.com (Ralph Morelli)
 */
@SuppressWarnings("deprecation")
@DesignerComponent(version = YaVersion.TEXTING_COMPONENT_VERSION,
    description = "<p>A component that will, when the <code>SendMessage</code> " +
    " method is called, send the text message " +
    "specified in the <code>Message</code> property to the phone number " +
    "specified in the <code>PhoneNumber</code> property. " +
    "<p>This component can also receive text messages unless the " +
    "<code>ReceivingEnabled</code> property is False.  When a message " +
    "arrives, the <code>MessageReceived</code> event is raised and provides " +
    "the sending number and message.</p>" +
    "<p>If the GoogleVoiceEnabled property is true, messages can be sent and received " +
    "over Wifi. This option requires that the user have a Google Voice account and " + 
    "that the mobile Voice app is installed on the phone. This option works only on " +
    "phones that support Android 2.0 (Eclair) or higher. </p>" +
    "<p>Often, this component is used with the <code>ContactPicker</code> " +
    "component, which lets the user select a contact from the ones stored " +
    "on the phone and sets the <code>PhoneNumber</code> property to the " +
    "contact's phone number.</p>" +
    "<p>To directly specify the phone number (e.g., 650-555-1212), set " +
    "the <code>PhoneNumber</code> property to a Text with the specified " +
    "digits (e.g., \"6505551212\").  Dashes, dots, and parentheses may be " +
    "included (e.g., \"(650)-555-1212\") but will be ignored; spaces may " +
    "not be included.</p>",
    category = ComponentCategory.SOCIAL,
    nonVisible = true,
    iconName = "images/texting.png")
    @SimpleObject
    @UsesPermissions(permissionNames = 
      "android.permission.RECEIVE_SMS, android.permission.SEND_SMS, " +
      "com.google.android.apps.googlevoice.permission.RECEIVE_SMS, " +
      "com.google.android.apps.googlevoice.permission.SEND_SMS, " +
      "android.permission.ACCOUNT_MANAGER, android.permission.MANAGE_ACCOUNTS, " + 
    "android.permission.GET_ACCOUNTS, android.permission.USE_CREDENTIALS")
    @UsesLibraries(libraries = 
    "google-api-client-beta.jar," +
    "google-api-client-android2-beta.jar," +
    "google-http-client-beta.jar," +
    "google-http-client-android2-beta.jar," +
    "google-http-client-android3-beta.jar," +
    "google-oauth-client-beta.jar," +
    "guava-11.0.1.jar")

  public class Texting extends AndroidNonvisibleComponent
    implements Component, OnResumeListener, OnPauseListener, OnInitializeListener {

  public static final String TAG = "Texting Component";

  public static final String SMS_RECEIVED = "android.provider.Telephony.SMS_RECEIVED";
  public static final String GV_SMS_RECEIVED = "com.google.android.apps.googlevoice.SMS_RECEIVED";
  public static final String PHONE_NUMBER_TAG = "com.google.android.apps.googlevoice.PHONE_NUMBER";
  public static final String MESSAGE_TAG = "com.google.android.apps.googlevoice.TEXT";
  public static final String TELEPHONY_INTENT_FILTER = "android.provider.Telephony.SMS_RECEIVED";
  public static final String GV_INTENT_FILTER = "com.google.android.apps.googlevoice.SMS_RECEIVED";
  public static final String GV_PACKAGE_NAME = "com.google.android.apps.googlevoice";
  public static final String GV_SMS_SEND_URL = "https://www.google.com/voice/b/0/sms/send/";
  public static final String GV_URL = "https://www.google.com/voice/b/0";

  // Meta data key and value that identify an app for handling incoming Sms
  // Used by Texting component
  public static final String META_DATA_SMS_KEY = "sms_handler_component";
  public static final String META_DATA_SMS_VALUE = "Texting";

  //  private static final String GV_PHONES_INFO_URL = "https://www.google.com/voice/b/0/settings/tab/phones";
  private static final String GV_SERVICE = "grandcentral";
  private static final String USER_AGENT = "Mozilla/5.0 (Windows; U; Windows NT 5.1; en-US) AppleWebKit/525.13 (KHTML, like Gecko) Chrome/0.A.B.C Safari/525.13";
  private static final int SERVER_TIMEOUT_MS = 30000;
  private static final String SENT = "SMS_SENT";
  private static final String UTF8 = "UTF-8";
  private static final String MESSAGE_DELIMITER = "\u0001";

  // Google Voice oauth helper
  private GoogleVoiceUtil gvHelper;
  private static Activity activity;
  private static Component component;

  // Indicates whether the component is receiving messages or not
  public static boolean receivingEnabled = true;
  private SmsManager smsManager;

  // The phone number to send the text message to.
  private String phoneNumber;
  // The message to send
  private String message;
  // Whether or not Google Voice is enabled.
  private boolean googleVoiceEnabled;

  // No messages can be received until Initialized
  private boolean isInitialized;
  
  // True when resumed and false when paused. 
  // Messages are cached when app is not running
  private static boolean isRunning;
 
  // Cache file for cached messages
  private static final String CACHE_FILE = "textingmsgcache";
  private static int messagesCached;
  private static Object cacheLock = new Object();
  
  /**
   * Creates a new TextMessage component.
   *
   * @param container  ignored (because this is a non-visible component)
   */
  public Texting(ComponentContainer container) {
    super(container.$form());
    Log.d(TAG, "Texting constructor");
    Texting.component = (Texting)this;
    activity = container.$context();

    smsManager = SmsManager.getDefault();
    PhoneNumber("");
    receivingEnabled = true;
    googleVoiceEnabled = false;
    
    isInitialized = false; // Set true when the form is initialized and can dispatch
    isRunning = false;     // This will be set true in onResume and false in onPause

    // Register this component for lifecycle callbacks 
    container.$form().registerForOnInitialize(this);
    container.$form().registerForOnResume(this);
    container.$form().registerForOnPause(this);  
  }

  /**
   * Callback from Form. No incoming messages can be processed through
   * MessageReceived until the Form is initialized. Messages are cached
   * until this method is called.
   */
  @Override
  public void onInitialize() {
    Log.i(TAG, "onInitialize()");
    isInitialized = true;
    isRunning = true;    // Added b/c REPL does not call onResume when starting Texting component
    processCachedMessages();
    NotificationManager nm = (NotificationManager) activity.getSystemService(Context.NOTIFICATION_SERVICE);
    nm.cancel(SmsBroadcastReceiver.NOTIFICATION_ID);
  }

  /**
   * Sets the phone number to send the text message to when the SendMessage function is called.
   *
   * @param phoneNumber a phone number to call
   */
  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_STRING, defaultValue = "")
  @SimpleProperty(category = PropertyCategory.BEHAVIOR)
  public void PhoneNumber(String phoneNumber) {
    this.phoneNumber = phoneNumber;
  }

  /**
   * Get the phone number that the message will be sent to when the SendMessage function is called.
   */
  @SimpleProperty
  public String PhoneNumber() {
    return phoneNumber;
  }

  /**
   * Sets the text message to send when the SendMessage function is called.
   *
   * @param message the message to send when the SendMessage function is called.
   */
  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_STRING, defaultValue = "")
  @SimpleProperty(category = PropertyCategory.BEHAVIOR)
  public void Message(String message) {
    this.message = message;
  }

  /**
   * Get the message that will be sent when the SendMessage function is called.
   */
  @SimpleProperty
  public String Message() {
    return message;
  }

  /**
   * Send a text message
   */
  @SimpleFunction
  public void SendMessage() {

    Log.i(TAG, "Sending message "  + message + " to " + phoneNumber);

    // Handles authenticating and sending messages
    new AsyncSendMessage().execute();
  }

  /**
   * Event that's raised when a new text message is received by the phone.
   * 
   * This method shouldn't be called until the app is initialized. If dispatch
   * fails, the message is cached. 
   * 
   * @param number the phone number that the text message was sent from.
   * @param messageText the text of the message.
   */
  @SimpleEvent
  public static void MessageReceived(String number, String messageText) {
    if (receivingEnabled) {
      Log.i(TAG, "MessageReceived from " + number + ":" + messageText);   
      if (EventDispatcher.dispatchEvent(component, "MessageReceived", number, messageText)) {
        Log.i(TAG, "Dispatch successful");
      } else {
        Log.i(TAG, "Dispatch failed, caching");
        synchronized (cacheLock) {
          addMessageToCache(activity, number, messageText);
        }
      }    
    }
  }

  /**
   * Gets whether you want to use Google Voice to send/receive messages.
   *
   * @return 'true' or 'false' depending on whether you want to
   * use Google Voice for sending/receiving messages.
   */
  @SimpleProperty(category = PropertyCategory.BEHAVIOR)
  public boolean GoogleVoiceEnabled() {
    return googleVoiceEnabled;
  }

  /**
   * Sets whether you want to use Google Voice to send/receive messages.
   *
   * @param enabled  Set to 'true' or 'false' depending on whether you want to
   *  use Google Voice to send/receive messages.
   */
  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_BOOLEAN, defaultValue = "False")
  @SimpleProperty()
  public void GoogleVoiceEnabled(boolean enabled) {
    if (SdkLevel.getLevel() >= SdkLevel.LEVEL_ECLAIR) {
      this.googleVoiceEnabled = enabled;
    } else {
      Toast.makeText(activity, "Sorry, your phone's system does not support this option.", Toast.LENGTH_LONG).show();
    }
  }

  /**
   * Gets whether you want the {@link #MessageReceived(String,String)} event to
   * get run when a new text message is received.
   *
   * @return 'true' or 'false' depending on whether you want the
   *          {@link #MessageReceived(String,String)} event to get run when a
   *          new text message is received.
   */
  @SimpleProperty(category = PropertyCategory.BEHAVIOR)
  public boolean ReceivingEnabled() {
    return receivingEnabled;
  }

  /**
   * Sets whether you want the {@link #MessageReceived(String,String)} event to
   * get run when a new text message is received.
   *
   * @param enabled  Set to 'true' or 'false' depending on whether you want the
   *                 {@link #MessageReceived(String,String)} event to get run
   *                 when a new text message is received.
   */
  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_BOOLEAN, defaultValue = "True")
  @SimpleProperty()
  public void ReceivingEnabled(boolean enabled) {
    Texting.receivingEnabled = enabled;
  }

  /**
   * Parse the messages out of the extra fields from the "android.permission.RECEIVE_SMS" broadcast
   * intent.
   *
   * Note: This code was copied from the Android android.provider.Telephony.Sms.Intents class.
   *
   * @param intent the intent to read from
   * @return an array of SmsMessages for the PDUs
   */
  public static SmsMessage[] getMessagesFromIntent(
                                                   Intent intent) {
    Object[] messages = (Object[]) intent.getSerializableExtra("pdus");
    byte[][] pduObjs = new byte[messages.length][];

    for (int i = 0; i < messages.length; i++) {
      pduObjs[i] = (byte[]) messages[i];
    }
    byte[][] pdus = new byte[pduObjs.length][];
    int pduCount = pdus.length;
    SmsMessage[] msgs = new SmsMessage[pduCount];
    for (int i = 0; i < pduCount; i++) {
      pdus[i] = pduObjs[i];
      msgs[i] = SmsMessage.createFromPdu(pdus[i]);
    }
    return msgs;
  }

  /**
   * Sends all the messages in the cache through MessageReceived and
   * clears the cache.
   */
  private void processCachedMessages() {
    String[] messagelist = null;
    synchronized (cacheLock) {
      messagelist =  retrieveCachedMessages();
    }
    if (messagelist == null) 
      return;
    Log.i(TAG, "processing " +  messagelist.length + " cached messages ");

    for (int k = 0; k < messagelist.length; k++) {
      String phoneAndMessage = messagelist[k];
      Log.i(TAG, "Message + " + k + " " + phoneAndMessage);

      int delim = phoneAndMessage.indexOf(":");
      
      // If receiving is not enabled, messages are not dispatched
      if (receivingEnabled && delim != -1) {
        MessageReceived(phoneAndMessage.substring(0,delim),
            phoneAndMessage.substring(delim+1));
      }
    }
  }
  
  /**
   * Retrieves cached messages from the cache file
   * and deletes the file. 
   * @return
   */
  private String[] retrieveCachedMessages() {
    Log.i(TAG, "Retrieving cached messages");
    String cache = "";
    try {
      FileInputStream fis = activity.openFileInput(CACHE_FILE);
      byte[] bytes = new byte[8192];
      if (fis == null) {
        Log.e(TAG, "Null file stream returned from openFileInput");
        return null;
      }
      int n = fis.read(bytes);
      Log.i(TAG, "Read " + n + " bytes from " + CACHE_FILE);
      cache = new String(bytes, 0, n);
      fis.close();
      activity.deleteFile(CACHE_FILE);
      messagesCached = 0;
      Log.i(TAG, "Retrieved cache " + cache);
    } catch (FileNotFoundException e) {
      Log.e(TAG, "File not found error reading from cache file");
      e.printStackTrace();
      return null;
    } catch (IOException e) {
      Log.e(TAG, "I/O Error reading from cache file");
      e.printStackTrace();
      return null;
    } 
    String messagelist[] = cache.split(MESSAGE_DELIMITER);
    return messagelist;
  }
  
  /**
   * Called by SmsBroadcastReceiver
   * @return
   */
  public static boolean isRunning() {
    return isRunning;
  }
  
  /**
   * Used to keep count in Notifications.
   * @return
   */
  public static int getCachedMsgCount() {
    return messagesCached;
  }
  
  /**
   * Processes cached messages if the app is initialized
   */
  @Override
  public void onResume() {
    Log.i(TAG, "onResume()");
    isRunning = true;
    if (isInitialized) {
      processCachedMessages();
      NotificationManager nm = (NotificationManager) activity.getSystemService(Context.NOTIFICATION_SERVICE);
      nm.cancel(SmsBroadcastReceiver.NOTIFICATION_ID);
    }
  }
  
  /**
   * Messages received while paused will be cached
   */
  @Override
  public void onPause() {
    Log.i(TAG, "onPause()");
    isRunning = false;
  }
  
  /**
   * This method is called by SmsBroadcastRecieiver when a message is received.
   * @param phone
   * @param msg
   */
  public static void handledReceivedMessage(Context context, String phone, String msg) {
    if (isRunning) {
      MessageReceived(phone, msg);
    } else {
      synchronized (cacheLock) {
        addMessageToCache(context, phone, msg);
      }
    }
  }
  
  /**
   * Messages a cached in a private file
   * @param context
   * @param phone
   * @param msg
   */
  private static void addMessageToCache(Context context, String phone, String msg) {
    try {
      String cachedMsg = phone + ":" + msg + MESSAGE_DELIMITER;
      Log.i(TAG, "Caching " + cachedMsg);
      FileOutputStream fos = context.openFileOutput(CACHE_FILE, Context.MODE_APPEND);
      fos.write(cachedMsg.getBytes());
      fos.close();      
      ++messagesCached;
      Log.i(TAG, "Cached " + cachedMsg);
    } catch (FileNotFoundException e) {
      Log.e(TAG, "File not found error writing to cache file");
      e.printStackTrace();
    } catch (IOException e) {
      Log.e(TAG, "I/O Error writing to cache file");
      e.printStackTrace();
    }
  }

  /**
   * Utility class built from Free Software (GPLv3 or later)
   * by cannibalizing parts of Voice.java of the free software
   * package, Google-Voice-Java:
   * 
   * @see http://code.google.com/p/google-voice-java/  
   * 
   */
  class GoogleVoiceUtil {
    private final int MAX_REDIRECTS = 5;

    String general; // Google's GV page
    String rnrSEE;  // Value that passed into SMS's
    String authToken;
    int redirectCounter;
    private boolean isInitialized;

    /**
     * The constructor sometimes fails to getGeneral
     * @param authToken
     */
    public GoogleVoiceUtil(String authToken) {
      Log.i(TAG, "Creating GV Util");
      this.authToken = authToken;
      try {
        this.general = getGeneral();
        Log.i(TAG, "general = " + this.general);
        setRNRSEE();  
        isInitialized = true;   // If we make it to here, we're good to go
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    public boolean isInitialized() {
      return isInitialized;
    }

    /**
     * Free software method copied and adapted from Voice.java
     * @see http://code.google.com/p/google-voice-java/
     * 
     */
    private String sendGvSms() {
      Log.i(TAG, "sendGvSms()");
      String response = "";
      try {
        String smsData = 
          URLEncoder.encode("phoneNumber", UTF8) + "=" + URLEncoder.encode(phoneNumber, UTF8) + 
          "&" + URLEncoder.encode("text", UTF8) + "=" + URLEncoder.encode(message, UTF8) + 
          "&" + URLEncoder.encode("_rnr_se", UTF8) + "=" + URLEncoder.encode(rnrSEE, UTF8);

        Log.i(TAG, "smsData = " + smsData);
        URL smsUrl = new URL(GV_SMS_SEND_URL);

        URLConnection smsConn = smsUrl.openConnection();
        smsConn.setRequestProperty( "Authorization", "GoogleLogin auth=" + authToken );
        smsConn.setRequestProperty("User-agent", USER_AGENT);
        smsConn.setDoOutput(true);
        smsConn.setConnectTimeout(SERVER_TIMEOUT_MS);

        Log.i(TAG, "sms request = " + smsConn);
        OutputStreamWriter callwr = new OutputStreamWriter(smsConn.getOutputStream());
        callwr.write(smsData);
        callwr.flush();

        BufferedReader callrd = new BufferedReader(new InputStreamReader(smsConn.getInputStream()));

        String line;
        while ((line = callrd.readLine()) != null) {
          response += line + "\n\r";
        }
        Log.i(TAG, "Sent SMS, response = " + response);

        callwr.close();
        callrd.close();

        if (response.equals("")) {
          throw new IOException("No Response Data Received.");
        } else 
          return response;
      } catch (IOException e) {
        Log.i(TAG, "IO Error on Send " + e.getMessage());
        e.printStackTrace();
        return "IO Error Message not sent";
      }
    }


    /**
     * Fetches the page Source Code for the Voice homepage. This file contains
     * most of the useful information for the Google Voice Account such as
     * attached PhoneOld info and Contacts.
     *
     * @return the general
     * @throws IOException
     *             Signals that an I/O exception has occurred.
     */
    public String getGeneral() throws IOException {
      Log.i(TAG, "getGeneral()");
      return get(GV_URL);
    }

    /**
     * Internal method which parses the Homepage source code to determine the
     * rnrsee variable, this variable is passed into most fuctions for placing
     * calls and sms.
     *
     * @throws IOException Signals that an I/O exception has occurred.
     */
    private void setRNRSEE() throws IOException {
      Log.i(TAG, "setRNRSEE()");
      if (general != null) {
        if(general.contains("'_rnr_se': '")) {
          String p1 = general.split("'_rnr_se': '", 2)[1];
          rnrSEE = p1.split("',", 2)[0];
          Log.i(TAG,"Successfully Received rnr_se.");
          p1 = null;
        } else {
          Log.i(TAG, "Answer did not contain rnr_se! "+ general);
          throw new IOException("Answer did not contain rnr_se! "+ general);
        }
      } else {
        Log.i(TAG,"setRNRSEE(): Answer was null!");
        throw new IOException("setRNRSEE(): Answer was null!");
      }
    }

    /**
     * HTTP GET request for a given URL String.
     *
     * @param urlString
     *            the url string
     * @return the string
     * @throws IOException
     *             Signals that an I/O exception has occurred.
     */
    String get(String urlString) throws IOException {
      URL url = new URL(urlString);
      HttpURLConnection conn = (HttpURLConnection) url.openConnection();
      int responseCode = 0;
      try {
        conn.setRequestProperty( "Authorization", "GoogleLogin auth="+authToken );
        conn.setRequestProperty("User-agent", USER_AGENT);
        conn.setInstanceFollowRedirects(false); // will follow redirects of same protocol http to http, but does not follow from http to https for example if set to true

        // Get the response
        conn.connect();
        responseCode = conn.getResponseCode();
        Log.i(TAG, urlString + " - " + conn.getResponseMessage());
      } catch (Exception e) {
        throw new IOException(urlString + " : " + conn.getResponseMessage() + "("+responseCode+") : IO Error."); 
      }

      InputStream is;
      if(responseCode==200) {
        is = conn.getInputStream();
      } else if(responseCode==HttpURLConnection.HTTP_MOVED_PERM || responseCode==HttpURLConnection.HTTP_MOVED_TEMP || responseCode==HttpURLConnection.HTTP_SEE_OTHER || responseCode==307) {
        redirectCounter++;
        if(redirectCounter > MAX_REDIRECTS) {
          redirectCounter = 0;
          throw new IOException(urlString + " : " + conn.getResponseMessage() + "("+responseCode+") : Too manny redirects. exiting.");
        }
        String location = conn.getHeaderField("Location");
        if(location!=null && !location.equals("")) {
          System.out.println(urlString + " - " + responseCode + " - new URL: " + location);
          return get(location);
        } else {
          throw new IOException(urlString + " : " + conn.getResponseMessage() + "("+responseCode+") : Received moved answer but no Location. exiting.");
        }
      } else {
        is = conn.getErrorStream();
      }
      redirectCounter = 0;

      if(is==null) {
        throw new IOException(urlString + " : " + conn.getResponseMessage() + "("+responseCode+") : InputStream was null : exiting.");
      }

      String result="";
      try {
        // Get the response
        BufferedReader rd = new BufferedReader(new InputStreamReader(is));

        StringBuffer sb = new StringBuffer();
        String line;
        while ((line = rd.readLine()) != null) {
          sb.append(line + "\n\r");
        }
        rd.close();
        result = sb.toString();
      } catch (Exception e) {
        throw new IOException(urlString + " - " + conn.getResponseMessage() + "("+responseCode+") - " +e.getLocalizedMessage());
      }
      return result;
    }
  }


  /**
   * Callback method to handle the result of attempting to send a message. 
   * Each message is assigned a Broadcast receiver that is notified by 
   * the phone's radio regarding the status of the sent message. The 
   * receivers call this method.  (See transmitMessage() method below.)
   * 
   * @param context
   *            The context in which the calling BroadcastReceiver is running.
   * @param receiver
   *            Currently unused. Intended as a special BroadcastReceiver to
   *            send results to. (For instance, if another plugin wanted to do
   *            its own handling.)
   * @param resultCode, the code sent back by the phone's Radio
   * @param seq, the message's sequence number
   * @param smsMsg, the message being processed
   */
  private synchronized void handleSentMessage(Context context,
                                              BroadcastReceiver receiver, int resultCode, String smsMsg) {
    switch (resultCode) {
      case Activity.RESULT_OK:
        Log.i(TAG, "Received OK, msg:" + smsMsg);
        Toast.makeText(activity, "Message sent", Toast.LENGTH_SHORT).show();
        break;
      case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
        Log.e(TAG, "Received generic failure, msg:" + smsMsg);
        Toast.makeText(activity, "Generic failure: message not sent", Toast.LENGTH_SHORT).show();
        break;
      case SmsManager.RESULT_ERROR_NO_SERVICE:
        Log.e(TAG, "Received no service error, msg:"  + smsMsg);
        Toast.makeText(activity, "No Sms service available. Message not sent.", Toast.LENGTH_SHORT).show();
        break;
      case SmsManager.RESULT_ERROR_NULL_PDU:
        Log.e(TAG, "Received null PDU error, msg:"  + smsMsg);
        Toast.makeText(activity, "Received null PDU error. Message not sent.", Toast.LENGTH_SHORT).show();
        break;
      case SmsManager.RESULT_ERROR_RADIO_OFF:
        Log.e(TAG, "Received radio off error, msg:" + smsMsg);
        Toast.makeText(activity, "Could not send SMS message: radio off.", Toast.LENGTH_LONG).show();
        break;
    }
  }


  /**
   * Asynchronously Sends a text message via Google Voice
   * 
   */
  class AsyncSendMessage extends AsyncTask<Void, Void, String> {

    /**
     * Handles sending SMS over Google Voice or built-in SMS
     */
    @Override
    protected String doInBackground(Void... arg0) {
      String response = "";
      Log.i(TAG, "Async sending message");

      try {

        if (googleVoiceEnabled) {
          Log.i(TAG, "Sending via GV");
          
          // Get the authtoken
          OAuth2Helper oauthHelper = new OAuth2Helper();
          String authToken = oauthHelper.getRefreshedAuthToken(activity, GV_SERVICE);
          Log.i(TAG, "authToken = " + authToken);
           
          if (gvHelper == null) {
            gvHelper = new GoogleVoiceUtil(authToken);
          }
          if (gvHelper.isInitialized()) {
            response = gvHelper.sendGvSms();
            Log.i(TAG, "Sent SMS, response = " + response);
          } else {
            return "IO Error: unable to create GvHelper";
          }
        } else {
          Log.i(TAG, "Sending via built-in Sms");

          PendingIntent pendingIntent = PendingIntent.getBroadcast(activity, 0, new Intent(SENT), 0);

          // Receiver for when the SMS is sent
          BroadcastReceiver sendReceiver = new BroadcastReceiver() {
            @Override
            public synchronized void onReceive(Context arg0, Intent arg1) {
              try {
                handleSentMessage(arg0, null, getResultCode(), message);
                activity.unregisterReceiver(this);
              } catch (Exception e) {
                Log.e("BroadcastReceiver",
                    "Error in onReceive for msgId "  + arg1.getAction());
                Log.e("BroadcastReceiver", e.getMessage());
                e.printStackTrace();
              }
            }
          };
          // This may result in an error -- a "sent" or "error" message will be displayed
          activity.registerReceiver(sendReceiver, new IntentFilter(SENT));
          smsManager.sendTextMessage(phoneNumber, null, message, pendingIntent, null);
        }    
      } catch (Exception e) {
        e.printStackTrace();
      }
      return response;
    }

    @Override
    protected void onPostExecute(String result) {
      super.onPostExecute(result);
      if (result.contains("ok"))
        Toast.makeText(activity, "Message sent", Toast.LENGTH_SHORT).show();
      else if (result.contains("IO Error")) 
        Toast.makeText(activity, result, Toast.LENGTH_SHORT).show();
      else {
        // Do nothing -- built-in SMS will display either "sent" or "error"
      }
    }
  }
}

