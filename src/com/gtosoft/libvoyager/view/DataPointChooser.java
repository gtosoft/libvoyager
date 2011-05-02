/**
 * (C) 2011 libvoyager is licensed under a Creative Commons Attribution-NonCommercial 3.0 Unported License. 
 * Permissions beyond the scope of this license may be available at http://www.gtosoft.com. You can download, 
 * use, modify the code as long as you do not include it as part of commercial software.
 */

package com.gtosoft.libvoyager.view;


import android.app.ListActivity;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.widget.ListView;


public class DataPointChooser extends ListActivity {

    Cursor mCursor = null;

    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
    	
    	Intent intent1 =getIntent();
    	Bundle b = intent1.getExtras();
//    	ArrayList al = new ArrayList<String> ();
//    	al.add
//    	b.getstring
//    	
//    	String [] displayFields = {};
//    	int [] displayViews = {};
//    	
//    	setContentView(R.layout.datapointchooserlayout);
//    	
//    	mCursor = ddb.
//    	
//        setListAdapter(new SimpleCursorAdapter (
//                this,
//                R.layout.datapointchooseritem,
//                mCursor, 
//                displayFields, 
//                displayViews));

        ListView lv = this.getListView();
        
//        lv.setOnItemClickListener(new OnItemClickListener()  {
//                        public void onItemClick(AdapterView<?> arg0, View arg1, int arg2,
//                                        long arg3) {
//                                mCursor.requery();
//                        }       
//        });

    	
    	
    }
}
