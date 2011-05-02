/**
 * (C) 2011 libvoyager is licensed under a Creative Commons Attribution-NonCommercial 3.0 Unported License. 
 * Permissions beyond the scope of this license may be available at http://www.gtosoft.com. You can download, 
 * use, modify the code as long as you do not include it as part of commercial software.
 */

package com.gtosoft.libvoyager.gui;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;


/**
 * This class will help abstract the gory details of displaying a notification in the 
 * notification bar in Android so the calling class can focus on other things.  
 *  
 * @author Brad Hein / GTOSoft. 
 */

public class NotificationHelper {
    private static final int VOYAGER_NOTIFY_ID_CHECKENG = 2345;

    NotificationManager mNotificationManager = null;
    Notification mNotificationCheckEngine = null;

    Context mCallerContext;
    Handler mHandler;

    
    public NotificationHelper(Context yourActivityContext, Handler yourUIHandler) {
    	
    	mCallerContext 	= yourActivityContext;
    	mHandler 		= yourUIHandler;
    	
	}

    /**
     * Put up a notification that there are DTC's available. 
     * @param clsReviewDTCs - if the user clicks the DTC notification, we will encarnate this activity class
     * @param checkEngineDrawableResourceID - the check engine icon, resource ID from R.
     * @return
     */
    public boolean notifyCheckEngine (String infoText, Class clsReviewDTCs, int checkEngineDrawableResourceID) {
            
            // initial config stuff. 
            if (mNotificationManager == null) {
                    String ns = Context.NOTIFICATION_SERVICE;
                    mNotificationManager = (NotificationManager) mCallerContext.getSystemService(ns);
            }
            
            if (mNotificationCheckEngine == null) {
                    CharSequence tickerText = "Check Engine (click for details)";
                    
                    long when = System.currentTimeMillis();
                    mNotificationCheckEngine = new Notification (checkEngineDrawableResourceID,tickerText, when);
                    mNotificationCheckEngine.flags |= Notification.FLAG_ONLY_ALERT_ONCE;
            }
            
           
            Intent i = new Intent (mCallerContext,clsReviewDTCs);
            PendingIntent pi = PendingIntent.getActivity (mCallerContext,0,i,0);

            mNotificationCheckEngine.setLatestEventInfo(mCallerContext,"Check Engine", infoText, pi);
            mNotificationManager.notify (VOYAGER_NOTIFY_ID_CHECKENG,mNotificationCheckEngine);

            
            return true;
    }



    public void shutdown () {
        // get rid of the notification bar items. 
        try {
            mNotificationManager.cancel(VOYAGER_NOTIFY_ID_CHECKENG);
            mNotificationManager.cancelAll();
            mNotificationCheckEngine = null;
        } catch (Exception e) {
        	msg ("Exception during shutdown: " + e.getMessage());
        }

    }

    
    private void msg (String messg) {
    	Log.d("NotificationHelper",messg);
    }
    
} // end of NotificationHelper class. 
