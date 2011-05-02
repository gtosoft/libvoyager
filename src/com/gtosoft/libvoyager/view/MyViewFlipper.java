/**
 * (C) 2011 libvoyager is licensed under a Creative Commons Attribution-NonCommercial 3.0 Unported License. 
 * Permissions beyond the scope of this license may be available at http://www.gtosoft.com. You can download, 
 * use, modify the code as long as you do not include it as part of commercial software.
 */

package com.gtosoft.libvoyager.view;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.ViewFlipper;

public class MyViewFlipper extends ViewFlipper {

    public MyViewFlipper(Context context, AttributeSet attrs) {
    	super(context, attrs);
    }
    
    @Override
    protected void onDetachedFromWindow() {
    	try{
    		super.onDetachedFromWindow();
    	}catch(Exception e) {
    		Log.d("MyViewFlipper","Stopped a viewflipper crash");
    	}
    }
    
    
}
