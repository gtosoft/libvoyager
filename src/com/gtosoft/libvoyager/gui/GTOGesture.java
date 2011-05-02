/**
 * (C) 2011 libvoyager is licensed under a Creative Commons Attribution-NonCommercial 3.0 Unported License. 
 * Permissions beyond the scope of this license may be available at http://www.gtosoft.com. You can download, 
 * use, modify the code as long as you do not include it as part of commercial software.
 */

package com.gtosoft.libvoyager.gui;

import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.GestureDetector.SimpleOnGestureListener;

/**
 * This class will embody the logic necessary to handle gestures such as a fling.  
 */


public class GTOGesture extends SimpleOnGestureListener {
    private static final int SWIPE_MIN_DISTANCE = 120;
    private static final int SWIPE_MAX_OFF_PATH = 250;
    private static final int SWIPE_THRESHOLD_VELOCITY = 200;
    View.OnTouchListener gestureListener;

    
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        try {
            if (Math.abs(e1.getY() - e2.getY()) > SWIPE_MAX_OFF_PATH) {
            	msg("Gesture failed: SWIPE OFF PATH " + Math.abs(e1.getY() - e2.getY()) + " > " + SWIPE_MAX_OFF_PATH);
                return false;
            }
            
            //msg ("Swipe: Velocity=" + (Math.abs(velocityX)) + " Distance=" + (e1.getX() - e2.getX() ));

            // right to left swipe
            if(e1.getX() - e2.getX() > SWIPE_MIN_DISTANCE && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
                onSwipeLeft();
            }  else if (e2.getX() - e1.getX() > SWIPE_MIN_DISTANCE && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
                onSwipeRight();
            }
            
            return true;
        } catch (Exception e) {
        	msg ("Exception during fling");
            // nothing
        }
        return false;
    }
	
    public void onSwipeLeft () {
    	//msg ("Left Swipe");
    }
    
    public void onSwipeRight () {
    	//msg ("Right Swipe");
    }

    private void msg (String message) {
    	Log.d("GTOGesture",message);
    }
    
    
}
