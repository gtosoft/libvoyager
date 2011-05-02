/**
 * (C) 2011 libvoyager is licensed under a Creative Commons Attribution-NonCommercial 3.0 Unported License. 
 * Permissions beyond the scope of this license may be available at http://www.gtosoft.com. You can download, 
 * use, modify the code as long as you do not include it as part of commercial software.
 */

package com.gtosoft.libvoyager.view;

import com.gtosoft.libvoyager.util.EasyTime;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;


public class XYGraphView extends View{
	final boolean DEBUG = false;
	
	long mTimeX = 0;
	
	EasyTime eTime = new EasyTime();

	String mGraphLabel = "";
	
	int mRequestedCanvasHeight = 0;
	int mRequestedCanvasWidth = 0;
	
	int mInputMin =   0;
	int mInputMax = 100;
	
	int mLastValue = 0;
	
	Context mParentContext;
	
	Handler mhMain;
	
	Paint mpntGraphPoints;
	Paint mpntGraphLines;
	Paint mpntGraphMeta;
	
	//HashMap <Long, Integer> mCoordinates = new HashMap<Long,Integer> ();

	// allocate the integer array which will contain coordinate pairs to be printed to the graph.
	long lastWriteTime = 0;
	int mCoordWriterOffset = 0;
	int mCoordinateArray[];

	
	public XYGraphView(Context context) {
		super(context);
		setParentContext (context);
	}

	public XYGraphView(Context context, AttributeSet attrs) {
		super(context, attrs);
		
		setParentContext(context);

		
		//// This block of code obtains the height specified in the xml layout.////////////// 
		String height = attrs.getAttributeValue("android", "layout_height");
		int iHeight = 0;
		try {iHeight = Integer.valueOf(height);
		} catch (Exception e) {	}
		if (iHeight > 0) {
			mRequestedCanvasHeight = iHeight;
		}

		// Get width from the xml layout. 
		String width = attrs.getAttributeValue("android", "layout_width");
		int iWidth = 0;
		try {iWidth = Integer.valueOf(width.split("[a-z]")[0]);
		} catch (Exception e) {	}
		if (iWidth > 0) {
			mRequestedCanvasWidth = iWidth;
		}

		// If we obtained two good numbers, then run init. 
		if (mRequestedCanvasHeight > 0 && mRequestedCanvasWidth > 0) {
			msg ("Got height and width from layout, initialize view");
			init();
		}
		
		
	}
 
	
	public XYGraphView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		msg ("Alternate constructor throwing out attributes and style");
		setParentContext(context);
	}


	private void setParentContext (Context c) {
		mParentContext = c;
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		

		// extract their requested height and widtdh. 
		int requestedWidth  = MeasureSpec.getSize(widthMeasureSpec);
		int requestedHeight = MeasureSpec.getSize(heightMeasureSpec);

// Perform any tasks here related to fitting the dimensions into something we like.  
//		if (requestedHeight != requestedWidth) {
//			
//			// overwrite the larger of the dimensions. 
//			if (requestedWidth > requestedHeight)
//				requestedWidth = requestedHeight;
//			else
//				requestedHeight = requestedWidth;
//		}
		
		// set the local member variables to the newly discovered desired dimensions. 
		mRequestedCanvasHeight = requestedHeight;
		mRequestedCanvasWidth  = requestedWidth; 
		
		// spit back the requested dimensions as our accepted dimensions. We'll do our best. 
		setMeasuredDimension(requestedWidth, requestedHeight);

		// this seems like a good place to initialize things. 
		//msg ("onMeasure, calling init()");
		init();
	}
		

	/**
	 * Initial initialization 
	 * @return - true unless a problem occurs. 
	 */
	private boolean init () {

		// It's possible init() was called during initialization earlier so only re-allocate the array if the new size is bigger.
		if (mCoordinateArray == null || mCoordinateArray.length < mRequestedCanvasWidth) {
			// Allocate twice as much width as we really need.
			if (mCoordinateArray == null) {
//				msg ("Coord array null, allocating for first time. ");
			} else {
				msg ("Coord array size is " + mCoordinateArray.length + " canvas is " + mRequestedCanvasWidth);
			}
			
			
			mCoordinateArray = new int[mRequestedCanvasWidth*2];
			coordArrayBackfill(0,mCoordinateArray.length);
		}
		
		
		mpntGraphPoints = new Paint();
		mpntGraphPoints.setColor(Color.GREEN);
		mpntGraphPoints.setStrokeWidth(1);
		
		mpntGraphLines = new Paint();
//		mpntGraphLines.setColor(Color.DKGRAY);
		mpntGraphLines.setColor(Color.GREEN);
		mpntGraphLines.setStrokeWidth(2);

		mpntGraphMeta = new Paint();
		mpntGraphMeta.setColor(Color.WHITE);
		mpntGraphMeta.setTextSize(25);
		mpntGraphMeta.setStrokeWidth(3);
		mpntGraphMeta.setAlpha(70);
		

		// quick hack to prevent divide by zero during startup. (modulo math doesn't like X % 0).
		if (mRequestedCanvasWidth == 0)
			mRequestedCanvasWidth = 1;

		if (mhMain == null) 
			mhMain = new Handler();
		
		return true;
	}
	
	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		
		drawGraphFrame(canvas);
		drawGraphPoints(canvas);
	}
	
	

	private boolean drawGraphFrame (Canvas canvas) {
		// Draw a square: TODO: migrate this into a function to which we just pass a canvas and corners. 
		float[] points = {0,0,0,mRequestedCanvasHeight,
						  0,0,mRequestedCanvasWidth,0,
						  mRequestedCanvasWidth,mRequestedCanvasHeight,mRequestedCanvasWidth,0,
						  mRequestedCanvasWidth,mRequestedCanvasHeight,0,mRequestedCanvasHeight
						  };
		
		canvas.drawLines(points, 0, 16, mpntGraphMeta);
		
		canvas.drawText("" + mRequestedCanvasWidth + "s, res=1s, inMin=" + mInputMin + " inMax=" + mInputMax, 10,25,mpntGraphMeta);
		
		canvas.drawText("Label: " + mGraphLabel, 10, 25 + mpntGraphMeta.getTextSize() + 5, mpntGraphMeta);

		canvas.drawText("" + mLastValue, 10,25 + (mpntGraphMeta.getTextSize() + 5) * 2,mpntGraphMeta);
		
		return true;
	}
	
	private void msg (String message) {
		Log.d("XYGraphView",message);
	}

	/**
	 * Set the min/max values of the data which you plan on setting with addPoint(). Default is 0, 100 if not specified here
	 * @param newMin
	 * @param newMax
	 */
	public void setMinMax (int newMin, int newMax) {
		// Sanity Checks
		
		if (newMin >= newMax) {
			msg ("Invalid min/max specified: min:" + newMin + " max: " + newMax);
			return;
		}
		
		// Accept the new values. 
		
		mInputMin = newMin;
		mInputMax = newMax;
	}
	
	
	
	private long getTimeForX () {
		long ret = (eTime.getUptimeMillis() / 100);
		
		return ret;
	}

	/**
	 * Returns the index where we'll be writing. 
	 * @return
	 */
	private long getWriterPosition () {
		//if (mRequestedCanvasWidth == 0) return 0;
		
		int width = (mRequestedCanvasWidth==0)?500:mRequestedCanvasWidth;
		
//		return (getTimeX() % mRequestedCanvasWidth);
		if (mTimeX == 0) 
			return 0;
		
		return (mTimeX % width);
	}
	
	/**
	 * Updates the global variable associated with the current time used for X coordinate stuff. 
	 * Only the addPoint method should update this variable just before writing. 
	 */
	private void updateGlobalX() {
		mTimeX = getTimeForX();
	}
	
	public void addPoint (int newPointValue) {
		long unboundY = newPointValue;
		
		//int canvasWidth = (mRequestedCanvasWidth==0)?500:mRequestedCanvasWidth;
		int canvasHeight = (mRequestedCanvasHeight==0)?500:mRequestedCanvasHeight;
		
		
		updateGlobalX();
		long writerOffset = getWriterPosition();
		
		if (DEBUG==true) msg ("Graph: Adding point: " + writerOffset + "," + unboundY);

		// initialize the last-write time variable. 
		if (lastWriteTime == 0)
			lastWriteTime = mTimeX;

//		msg ("Before backfill of " + (mTimeX - lastWriteTime) + " bytes");
//		printBufferDebug();
		coordArrayBackfill(writerOffset,mTimeX - lastWriteTime);
		
		
		int y; int canvasY;
		float fY;

			
		// f(x)=(x-min)/(max-min) as derived by B&S Jan 17 2010.
		//    Where X is the variable input not the x coordinate. 
		fY = canvasHeight * unboundY - canvasHeight * mInputMin;
		fY = fY / (mInputMax - mInputMin);
		y  = (int)fY;

//msg ("DEBUG: newpt=" + newPointValue + " fy=" + fY + " y=" + y + " offset=" + writerOffset + " height=" + mRequestedCanvasHeight);		
 
		
		// Flip Y. 
		canvasY = canvasHeight - y;

		
		coordArraySet(0, writerOffset, canvasY);
//		printBufferDebug();
		
//		msg ("After backfill AND WRITE: ");
//		printBufferDebug();

		mLastValue = newPointValue;
		
		// request that the graph be re-drawn. 
		postInvalidate();
		
		lastWriteTime = mTimeX;
	}

	/**
	 * temp: prints a bit of the buffer before and after current position along with a pointer showing where we're at. 
	 */
	private void printBufferDebug() {
		long writerOffset = getWriterPosition();
		
		String bufferBytes = "";

		int numpointstoshow = 20;
		int center = numpointstoshow / 2;
		
		bufferBytes = "<offset=" + writerOffset + "/c=" + center  + ">";

		for (int i=0;i<numpointstoshow;i++) {
			bufferBytes = bufferBytes + " " ;
			if (i == center) bufferBytes = bufferBytes + "[";
			bufferBytes = bufferBytes + coordArrayGet(i - center, writerOffset);
			if (i == center) bufferBytes = bufferBytes + "]";
			bufferBytes = bufferBytes + " ";
		}
		
		msg ("DEBUG: BUFFER: " + bufferBytes + " (Writer offset " + writerOffset + ")");
		
	}
	
	private void coordArraySet (long index, long writerOffset, int newValue) {
		int width = (mRequestedCanvasWidth==0)?500:mRequestedCanvasWidth;
		
		if (mCoordinateArray == null) {
			msg ("ERR: coordinate array null");
			return;
		}

		int idx = (int) (writerOffset + index) % width;
		
		// If we're wrapping around the 0-index, then wrap the idx around too.  
		if (idx < 0) {
//			msg ("Special Case(set): Fixing " + idx + " to be " + (idx + width));
			idx = width + idx;
		}

//msg ("DEBUG: setting coord at idx " + idx + "=" + newValue);
		try {mCoordinateArray[idx] = newValue;
		} catch (Exception e) {
			msg ("ERROR setting coordinate at index " + idx + " size=" + mCoordinateArray.length + " newvalue=" + newValue);
			return;
		}
		
	}
	
	/**
	 * Pulls an array element out of the circular array buffer. 
	 * @param index - after we flatten the array this is the index of the data you want, 0 always being the first item. 
	 * @param offset - current write position offset 
	 * @return - returns the integer at the corrected index. 
	 */
	private int coordArrayGet (long index, long writerOffset) {
		int width = (mRequestedCanvasWidth==0)?500:mRequestedCanvasWidth;

		int ret = 0;
		
		
//		int idx = (int) (writerOffset - 1 + index) % width;
		int idx = (int) (writerOffset + index) % width;

		// this wraps us around the origin of the index.  
		if (idx < 0) {
//			msg ("Special Case(get): Fixing " + idx + " to be " + (idx + width));
			idx = width + idx;
		}
		
		// prevents out of bounds situation. 
		if (mCoordinateArray == null || idx < 0 || idx >= mCoordinateArray.length)
			return -1;

		
		try {ret = mCoordinateArray[idx];
		} catch (Exception e) {
			msg ("Error during get. IDX=" + idx + " index=" + index + " writeroffset=" + writerOffset + " " + 
					" len=" + mCoordinateArray.length + "Err=" + e.getMessage());
		}
		
//		msg ("Get: writer at " + writerOffset + " index=" + index + " =" + ret);

		return ret;
	}
	
	/**
	 * Backfills the given number of items assuming it's been that long since the array was updated so we're carrying forward the last known data before that. 
	 * @param writerOffset
	 * @param numItemsToBackfill
	 */
	private void coordArrayBackfill (long writerOffset, long numItemsToBackfill) {

		// often the case?
		if (numItemsToBackfill == 0)
			return;
		
		// Get the value of the item that we want to carry forward. 
//		int valueToCarryForward = coordArrayGet(0 - numItemsToBackfill - 1,writerOffset);
		
		if (numItemsToBackfill >= mRequestedCanvasWidth) {
			// special case - fill the whole array. 
			for (int i=0;i<mRequestedCanvasWidth;i++) {
				coordArraySet(i, 0, -1);
			}
			
			// All done. 
			return;
		}
		
//msg ("Backfilling " + numItemsToBackfill + " items ending at " + writerOffset);
		
		// Only need to backfill "some" of the array. 
		for (int i=0;i<numItemsToBackfill;i++) {
			// Basically we're backfilling starting at "now" and going backwards to the writer index. 
			//coordArraySet(i, numItemsToBackfill + i, valueToCarryForward);
			//coordArraySet(0 - i + 1, writerOffset, valueToCarryForward);
			// use -1 in the array to tell the writer to skip that index. 
			coordArraySet(0 - i	, writerOffset, -1);
		}
		
		
		
	}
	
	// TODO: Draw the graph at a set interval instead of when new data arrives?
	// TODO: Use a canvas drawPoints() and let the canvas to the lifting. s
	/**
	 * Draw all points onto the graph. 
	 * @return - true on success, false otherwise. 
	 */
	private boolean drawGraphPoints (Canvas canvas) {
		
		if (mhMain == null) return false;
		
		int y = 0;
		int lastx = -1;
		int lasty = -1;
		
		// grab the current writeroffset. The writer offset may change while we're working so that's why we get it and save it. 
		long writerOffset = getWriterPosition();
		
		int x = mRequestedCanvasWidth-1;
		//msg ("Plotting the graph...");
		while (x >= 0) {
			y = coordArrayGet(x,writerOffset);
			if (y < 0) {
				x--;
				continue;
			}
			//msg ("Draw circle at " + x + "," + y + " writeroffset=" + writerOffset);
			canvas.drawCircle(x,y, 3, mpntGraphPoints);
			
			if (lasty >=0 && lastx >=0 && x > 0) {
				canvas.drawLine(lastx, lasty, x,y,mpntGraphLines);
//				msg ("DEBUG: drawing line. lastx=" + lastx + " lasty=" + lasty + " to " + x + "," + y);
			} else {
//				msg ("DEBUG: Not drawing line. lastx=" + lastx + " lasty=" + lasty);
			}
//canvas.drawLine(50,50,x,y,mpntGraphLines);			
			lastx = x;
			lasty = y;
			x--;
		}
		

		// TODO: Draw a line from one point to the next. 
		return true;
	}
	

	public void setGraphLabel (String newLabel) {
		mGraphLabel = newLabel;
	}
	


}
