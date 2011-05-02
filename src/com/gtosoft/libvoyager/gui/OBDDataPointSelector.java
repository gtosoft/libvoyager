/**
 * (C) 2011 libvoyager is licensed under a Creative Commons Attribution-NonCommercial 3.0 Unported License. 
 * Permissions beyond the scope of this license may be available at http://www.gtosoft.com. You can download, 
 * use, modify the code as long as you do not include it as part of commercial software.
 */

package com.gtosoft.libvoyager.gui;

import java.util.HashSet;

import com.gtosoft.libvoyager.R;
import com.gtosoft.libvoyager.db.DashDB;

import android.app.ListActivity;
import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ListView;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;


/**
 * This listactivity will display a list of all available OBD data points. 
 * The user may select one, and we will return it to the calling activity. 
 */


public class OBDDataPointSelector extends ListActivity {
    Cursor mCursor = null;
    
    // instantiate an instance of dashDB. 
    DashDB ddb = new DashDB(OBDDataPointSelector.this);
    
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
//        String displayFields[] = {"DPN","description"};
//        int displayViews[] = {R.id.ODLISTtvRequest,R.id.ODLISTtvDescription};
        setContentView(R.layout.obddatalist);

        mCursor = ddb.getAllPassiveDPNs();
        MyAdapter s;
        s = new MyAdapter(OBDDataPointSelector.this, mCursor);
        
        
        setListAdapter(s);
      
        ListView lv = this.getListView();
        lv.setOnItemClickListener(new OnItemClickListener()  {
	        public void onItemClick(AdapterView<?> arg0, View arg1, int arg2,
	                        long arg3) {
	        	// TODO: Set result and propagate back to calling activity. 
	        	// setResult(resultCode, data)
	        	msg ("Click!");
	                mCursor.requery();
	        }       
        });

	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		ddb.shutdown();
	}

	private void msg (String message) {
		Log.d("OBDDataPointSelector",message);
	}


	/**
	 * @author brad
	 */
    private class MyAdapter extends ResourceCursorAdapter {
    	
    	// This set will contain all the DPNs that are selected.
    	HashSet<String> mDPNs = new HashSet<String>();
    	
        public MyAdapter(Context context, Cursor cur) {
            super(context, R.layout.obddatalist, cur);
        }

        @Override
        public View newView(Context context, Cursor cur, ViewGroup parent) {
            LayoutInflater li = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View view = li.inflate(R.layout.obddatalistitem, parent, false);
            
        	CheckBox cb = (CheckBox) view.findViewById(R.id.OBDLISTcbSelected);
            
	        // Define what action to take when a checkbox is clicked. 
	        OnCheckedChangeListener listener = new OnCheckedChangeListener() {
				
				@Override
				public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
					if (isChecked == true) {
						// Add DPN to the set. 
						 
						
						// Get a handle of the view which contains the checkbox...
						View v = (View) buttonView.getParent();
						// get a handle on the DPN textview for the given record.
						TextView tvDPN = (TextView) v.findViewById(R.id.ODLISTtvRequest);
						// add it to the set
						if (tvDPN.length()>0)
							mDPNs.add("" + tvDPN.getText());
					} else {
						// remove the DPN from the set.
						
						// Get a handle of the view which contains the checkbox...
						View v = (View) buttonView.getParent();
						// get a handle on the DPN textview for the given record.
						TextView tvDPN = (TextView) v.findViewById(R.id.ODLISTtvRequest);
						// add it to the set
						if (tvDPN.length()>0)
							mDPNs.remove("" + tvDPN.getText());
					}
				}
			};
	        
			// assign the click listener. 
	        cb.setOnCheckedChangeListener(listener);
	        
            return view;
        }

        @Override
        public void bindView(View view, Context context, Cursor cur) {
        	// Obtain references to the onscreen views. 
        	CheckBox cb = (CheckBox) view.findViewById(R.id.OBDLISTcbSelected);
        	TextView tvDPN = (TextView) view.findViewById(R.id.ODLISTtvRequest);
        	TextView tvDescription = (TextView) view.findViewById(R.id.ODLISTtvDescription);

        	String DPN;
        	DPN = cur.getString(cur.getColumnIndex("DPN"));

        	// Put the text on the screen
        	tvDPN.setText(DPN);
        	tvDescription.setText(cur.getString(cur.getColumnIndex("description")));

        	// set the checkbox based on whether the DPN is in the set. 
        	if (mDPNs.contains(DPN)) {
        		cb.setChecked(true);
        	} else {
        		cb.setChecked(false);
        	}
        	

        }
    }

	
}
