/**
 * (C) 2011 libvoyager is licensed under a Creative Commons Attribution-NonCommercial 3.0 Unported License. 
 * Permissions beyond the scope of this license may be available at http://www.gtosoft.com. You can download, 
 * use, modify the code as long as you do not include it as part of commercial software.
 */

package com.gtosoft.libvoyager.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;


public class GaugeView extends View {
	int mRequestedCanvasHeight = 0;
	int mRequestedCanvasWidth  = 0;
	
	// resource ID for the arrow. 
	int RNeedle		= 0;
	int RBackground = 0;
	
	Handler mUIHandler = new Handler();
	
	// regardless of canvas height and width, we'll draw (translated) asif its this big, and scale as necessary. 
	int mDrawHeight = 100;
	int mDrawWidth  = 100;
	
	// Origin of mdrawheight/drawwidth canvas
	int mPx = 50;
	int mPy = 50;

	// we'll store the parent's context which we obtain during constructor. 
	Context mParentContext;

	// min/max values which the gauge can display
	double mMinValue = 0;
	double mMaxValue = 100;

	// current gauge value. 
	double mCurrentValue = 0;
	
	// the label shown beneath the gauge. 
	String mLabel = "";

	// Painting
	Paint mDefaultPaint = null; 
	Paint mPaintTickmarks = null;
	

	BitmapDrawable bBackground = null;
	BitmapDrawable bNeedle = null;

	float posLabelX;
	float posLabelY;
	Paint mpntLabel;

	float posCurrentValX;
	float posCurrentValY;
	Paint mpntCurrentVal;
	
	float mscalLabelX;
	float mscalLabelY;

	float mscalCurrentValX;
	float mscalCurrentValY;
	
	float mscalBackgroundX;
	float mscalBackgroundY;
	
	float mscalNeedleX;
	float mscalNeedleY;
	
	float posTickNumY;
	
	////////////////////////////////////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////////////////
	

	@Override
	protected void onDraw(Canvas canvas) {
		// TODO Auto-generated method stub
		super.onDraw(canvas);
		
		canvas.save(); // +1 item on stack
		// Draw background.
		canvas.save(); // +2 items on stack
		if (bBackground != null)  {
			canvas.scale(mscalBackgroundX, mscalBackgroundY);
			canvas.drawBitmap(bBackground.getBitmap(), 0, 0, mDefaultPaint);
		}
		canvas.restore(); // 2-1=1 item on stack

		// Draw tick marks and labels. 
		drawTicks (canvas);
		
		// Draw Needle
		// rotate the needle into position. 
		canvas.rotate(calculateRotationFromValue(mCurrentValue),mRequestedCanvasWidth/2, mRequestedCanvasHeight/2);
		// scale the matrix so when we draw the needle, it exactly fills up the available view. 
		canvas.scale(mscalNeedleX, mscalNeedleY);
		// draw it. 
		canvas.drawBitmap(bNeedle.getBitmap(),0,0,mDefaultPaint);

		// restore canvas to unrotated, unscaled form. 
		canvas.restore(); // 1-1 = 0 items on the stack

	// Draw label and value text on the gauge. 
		canvas.save();
		
		canvas.scale(mscalLabelX, mscalLabelY);
		// draw description label
		canvas.drawText(mLabel,posLabelX,posLabelY,mpntLabel);
		// draw current value in the box (the readout)
		canvas.drawText("" + Math.round(mCurrentValue),posCurrentValX,posCurrentValY,mpntCurrentVal);

		// restore the canvas to unresized state. 
		canvas.restore();
		
	}

	/**
	 * Perform initial initialization stuff. 
	 * @return
	 */
	private boolean init () {
		
		// we use this height and width as a basis for calculations to tell how much bigger or smaller the canvas is. 
		float modelHeight = 300;
		float modelWidth = 300;
		
		mDefaultPaint = new Paint();
		
		mDefaultPaint.setColor(Color.RED);
		mDefaultPaint.setAntiAlias(true);
		mDefaultPaint.setTextSize(30);
		
		// Tick marks AND numbers on the gauge. 
		mPaintTickmarks = new Paint();
		mPaintTickmarks.setColor(Color.WHITE);
		mPaintTickmarks.setStrokeWidth(3);
		mPaintTickmarks.setAntiAlias(true);

		mpntCurrentVal = new Paint();
		mpntCurrentVal.setTypeface(Typeface.MONOSPACE);
		mpntCurrentVal.setStrokeWidth(8f);
		mpntCurrentVal.setColor(Color.RED);
		mpntCurrentVal.setTextSize(50);

		mpntLabel = new Paint();
		mpntLabel.setTypeface(Typeface.MONOSPACE);
		mpntLabel.setStrokeWidth(8f);
		mpntLabel.setColor(Color.WHITE);
		mpntLabel.setTextSize(40);
		
		// define background image
//		bBackground = (BitmapDrawable) getResources().getDrawable(R.drawable.gaugedigital4);
		bBackground = null;

		// define needle image
		if (RNeedle     != 0) bNeedle = (BitmapDrawable) getResources().getDrawable(RNeedle);
		if (RBackground != 0) bNeedle = (BitmapDrawable) getResources().getDrawable(RBackground);

		// Calculate X & Y Positions of everything. 
		// calculate scaling based on model dimensions. When we draw the text we'll scale the canvas then draw. 
		posLabelX = 0.28f * modelWidth;
		posLabelY = 0.93f * modelHeight;
		// Calculate scaling necessary for gauge description label 
		mscalLabelX = mRequestedCanvasWidth / modelWidth;
		mscalLabelY = mRequestedCanvasHeight/ modelHeight;
		
		//Log.d("gauge","mscalLabelX=" + mscalLabelX);

		// calculate scaling based on model dimensions. When we draw the text we'll scale the canvas then draw. 
		posCurrentValX = (0.25f) * modelWidth;
		posCurrentValY = (0.77f) * modelHeight;
		// Calculate scaling necessary for current value label. 
		mscalCurrentValX = mRequestedCanvasWidth /modelWidth;
		mscalCurrentValY = mRequestedCanvasHeight/modelHeight;
		
		// Calculate scaling for background image
		if (bBackground != null) {
			mscalBackgroundX  = (float)mRequestedCanvasHeight / bBackground.getIntrinsicHeight();
			mscalBackgroundY  = (float)mRequestedCanvasWidth  / bBackground.getIntrinsicWidth();
		} else {
			mscalBackgroundX = 1;
			mscalBackgroundY = 1;
		}

		// calculate scaling for needle bitmap
		mscalNeedleX = (float)mRequestedCanvasHeight / bNeedle.getIntrinsicHeight() ;
		mscalNeedleY = (float)mRequestedCanvasWidth  / bNeedle.getIntrinsicWidth() ;

		// tick mark number considerations based on scale.  
		mPaintTickmarks.setTextSize(mscalLabelY * 30f);
		posTickNumY = mscalLabelY * 30f + 10f ;
		
		return true;
	}
	
	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);

		// TODO: Do all of our measuring here instead of in the code that executes every time we re-draw. 
		
		// extract their requested height and widtdh. 
		int requestedWidth  = MeasureSpec.getSize(widthMeasureSpec);
		int requestedHeight = MeasureSpec.getSize(heightMeasureSpec);

		// make sure we're a square by re-writing the requested dimensions, favoring the smaller of the two as the new h & w of square. 
		if (requestedHeight != requestedWidth) {
			
			// overwrite the larger of the dimensions. 
			if (requestedWidth > requestedHeight)
				requestedWidth = requestedHeight;
			else
				requestedHeight = requestedWidth;
		}
		
		// set the local member variables to the newly discovered desired dimensions. 
		mRequestedCanvasHeight = requestedHeight;
		mRequestedCanvasWidth  = requestedWidth; 
		
		// calculate the origin. 
		mPx = mRequestedCanvasHeight / 2;
		mPy = mRequestedCanvasWidth  / 2;

		// spit back the requested dimensions as our accepted dimensions. We'll do our best. 
		setMeasuredDimension(requestedWidth, requestedHeight);

		// this seems like a good place to initialize things. 
		init();
	}
	
	
	public GaugeView(Context context) {
		super(context);
		
		setParentContext(context);
	}

	public GaugeView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		setParentContext(context);
	}

	public GaugeView(Context context, AttributeSet attrs) {
		super(context, attrs);
		setParentContext(context);
	}

	
	public void setParentContext (Context ctx) {
		mParentContext = ctx;
	}
	
	public void setLabel(String newLabel) {
		mLabel = newLabel;
		this.invalidate();
	}

	public void setMinMax(double newMin, double newMax) {
		mMinValue = newMin;
		mMaxValue = newMax;
		this.invalidate();
	}

	private void setValue_UI(double newValue) {
		if (mCurrentValue == newValue) {
			return;
		}
			
		mCurrentValue = newValue;
		this.invalidate();
	}
	
	public void setValue (double newValue) {
		final double nv = newValue;
//msg ("THREAD: GAUGE: was " + mCurrentValue + " new " + newValue);

		mUIHandler.post (new Runnable () {
			public void run () {
				setValue_UI(nv);
			}// end of run
		});// end of runnable class
	}// end of post.
	
	
	
	/**
	 * Given a value (which will always be the current value of this gauge) we'll determine what angle to rotate the image so we're pointed in the right spot on the gauge.  
	 * @param v
	 * @return
	 */
	private float calculateRotationFromValue(double v) {
		
		// Using: f(x)=(x-min)/(max-min)
		// where x falls betweeen min and max inclusive. 
		// and f(x) returns a number between 0 and 1. 

		// use float 'cause we'll be working with decimal numbers. 
		float ret = 0;

		// get ret into range of 0-1. Using math.round to cast to a suitable type. 
		ret = Math.round(v - mMinValue);
		ret = ret / Math.round(mMaxValue - mMinValue);

		// now get the number into a range suitable for display on a gauge. 
		
		// first convert it to 0-180
		ret = ret * 180;
		
		// now back it up a bit so it falls within -90 and 90 (degrees). 
		ret = ret - 90;
		
		return (int) ret;
	}

	private boolean drawTicks (Canvas canvas) {

		canvas.save();

//		final int textheight = 40; calculate this separately during onMeasure in posTickNumY
		final int textleft = 10;
		final int tickspacing = 30; // degrees of separation between each tick. MUST BE evenly multiplied to 90

		canvas.rotate(-90,mPx,mPy);
		
		for (int i=-90;i<=90;i+= tickspacing) {
			
				// draw tick mark (a verticle line)
				canvas.drawLine(mPx,3,mPx,15, mPaintTickmarks);

				if (i == -90)
					canvas.drawText("" + Math.round(mMinValue), mPx-textleft, posTickNumY, mPaintTickmarks);
				
				if (i==0)
					canvas.drawText("" + Math.round(mMaxValue - mMinValue) / 2, mPx-textleft, posTickNumY, mPaintTickmarks);
				
				if (i == 90)
					canvas.drawText("" + Math.round(mMaxValue), mPx- textleft, posTickNumY, mPaintTickmarks);

				// rotate the text a bit for the next one.
				canvas.rotate(tickspacing,mPx,mPy);
				
		}

		canvas.restore();
		
		return true;
	}// end of drawTicks()

	private void msg (String message) {
		Log.d("GTOGauge",message);
	}
}
