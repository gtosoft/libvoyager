/**
 * (C) 2011 libvoyager is licensed under a Creative Commons Attribution-NonCommercial 3.0 Unported License. 
 * Permissions beyond the scope of this license may be available at http://www.gtosoft.com. You can download, 
 * use, modify the code as long as you do not include it as part of commercial software.
 */

package com.gtosoft.libvoyager.view;

import com.gtosoft.libvoyager.util.EasyTime;

import android.content.Context;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.ViewFlipper;

public class MyViewFlipper extends ViewFlipper {
	int mCurrentPage = 0;
	int mPageFlipOrder [] = {0,1,2};
	Context mParentContext;

    public MyViewFlipper(Context context, AttributeSet attrs) {
    	super(context, attrs);
    	
    	mParentContext = context;
    }
    
    @Override
    protected void onDetachedFromWindow() {
    	try{
    		super.onDetachedFromWindow();
    	}catch(Exception e) {
    		Log.d("MyViewFlipper","Stopped a viewflipper crash");
    	}
    }
    
    /**
	 * Advance viewflipper to the next layout. 
	 */
	public void flipNext() {
	//       int currentChild = vFlipper.getDisplayedChild();
	//       setvFlipperPage(currentChild + 1);
	         int currentChild = mCurrentPage;
	         setvFlipperPage(currentChild + 1);
	 }
 
	/**
	 * Flip to the previous screen (screen to the left). 
	 * If we're already at the leftmost viewflipper screen then we can't flip any more, so do nothing. 
	 */
	public void flipPrevious() {
	//       int currentChild = vFlipper.getDisplayedChild();
	        int currentChild = mCurrentPage;
	        // if we're not on the last child view then flip to the prior one in the set. 
	        setvFlipperPage(currentChild - 1);
	}

 
 
/**
* Sets the currently displayed viewflipper page. Also Performs sanity checks and fires off our flip-events. 
* @param newPage
*/
public synchronized void setvFlipperPage (final int newPage) {
       final int translatedOldPage = mPageFlipOrder[mCurrentPage];
       final int translatedNewPage = mPageFlipOrder[newPage];

       // newPage < vFlipper.getChildCount() && 
       if (newPage >= 0 && newPage < mPageFlipOrder.length ) {
    	   	
    	   // set flipper animations
    	   setFlipAnimation();
    	   
           // Switch views. 
           mCurrentPage = newPage;
           this.setDisplayedChild(translatedNewPage);
           
           // fire off an event so we can take action if necessary. Do it in the background so it is smooth to the user.   
           new Thread () {
                   public void run () {
                           // added the following two looper commands in order to accomodate the cursor adapter which threw this: 
                           //      java.lang.RuntimeException: Can't create handler inside thread that has not called Looper.prepare()
                           Looper.prepare();
                           long tStart = EasyTime.getUnixTime();
                           flipEvent(translatedOldPage, translatedNewPage);
                           long tStop = EasyTime.getUnixTime();
                           
                           // did the flip event take too long? 
                           if (tStop - tStart > 2) {
//                                       msg ("WARNING: Async Flip event (translated page " + translatedOldPage + " to translated page" + newPage + ") took " + (tStop - tStart) + " Seconds to complete.");
                           }
                           Looper.loop();
                   }
           }.start();
               
       }// end of if there was a flip event.
	}

	private void setFlipAnimation() {
		// TODO: These two seem to be in contention - put some thought into what would be more appropriate.
		this.setInAnimation(mParentContext, android.R.anim.slide_in_left);
		this.setOutAnimation(mParentContext, android.R.anim.slide_out_right);	
	}

   /**
    * Override this to see flip events. 
    */
   private void flipEvent (int translatedOldPage, int translatedNewPagea) {
	   return;
   }


 
    
}
