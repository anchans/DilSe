package com.example.dilse;

import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.androidplot.Plot;
import com.androidplot.util.Redrawer;
import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.StepMode;
import com.androidplot.xy.XYGraphWidget;
import com.androidplot.xy.XYPlot;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class MainActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {

    private static final String TAG = "OCVSample::Activity";
    private Tutorial3View mOpenCvCameraView;

    private static final int HISTORY_SIZE = 100;
    private XYPlot aprLevelsPlot = null;
    private XYPlot aprHistoryPlot = null;
    private SimpleXYSeries azimuthHistorySeries = null;
    private Redrawer redrawer;
    private double temp = 0;
    static final float ALPHA = 0.75f;

    float lastTouchY=0;
    int cannyThreshold=50;
    private double prev = 0;
    private double mag;
    private double maxSum;
    private int i = 0;
    private int bpm;
    private double time_init;
    private double  time_fin;
    private double time;
    private int count = 0;
    TextView histvalue;


    private BaseLoaderCallback mLoaderCallBack = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch(status){
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");
                    mOpenCvCameraView.enableView();
                }break;
                default:
                {
                    super.onManagerConnected(status);
                }break;
            }
        }
    };



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.show_camera);

        // setup the APR History plot:
        aprHistoryPlot = (XYPlot) findViewById(R.id.plot);

        azimuthHistorySeries = new SimpleXYSeries("");
        azimuthHistorySeries.useImplicitXVals();

        aprHistoryPlot.setRangeBoundaries(-3, 4, BoundaryMode.FIXED);
        aprHistoryPlot.setDomainBoundaries(0, HISTORY_SIZE, BoundaryMode.FIXED);
        LineAndPointFormatter lineAndPointFormatter = new LineAndPointFormatter(Color.rgb(255, 0, 0), null, null, null);
        aprHistoryPlot.addSeries(azimuthHistorySeries,
                lineAndPointFormatter);
        Paint paint = lineAndPointFormatter.getLinePaint();
        paint.setStrokeWidth(3);
        lineAndPointFormatter.setLinePaint(paint);
        aprHistoryPlot.setDomainStepMode(StepMode.INCREMENT_BY_VAL);
        aprHistoryPlot.setDomainStepValue(HISTORY_SIZE/10);
        /*aprHistoryPlot.setLinesPerRangeLabel(3);
        aprHistoryPlot.getDomainTitle().pack();
        aprHistoryPlot.getRangeTitle().pack();*/

        aprHistoryPlot.getGraph().getLineLabelStyle(XYGraphWidget.Edge.LEFT).
                setFormat(new DecimalFormat("#"));

        aprHistoryPlot.getGraph().getLineLabelStyle(XYGraphWidget.Edge.BOTTOM).
                setFormat(new DecimalFormat("#"));

        Switch switch1 = (Switch) findViewById(R.id.switch1);

        mOpenCvCameraView = (Tutorial3View) findViewById(R.id.show_camera_activity_java_surface_view);

        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);

        mOpenCvCameraView.setCvCameraViewListener(this);

        histvalue = (TextView) findViewById(R.id.histvalue);

        switch1.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(!isChecked){
                    Toast.makeText(getApplicationContext(), "OFF",Toast.LENGTH_SHORT).show();
                    mOpenCvCameraView.setFlashOff();

                }
                else{
                    Toast.makeText(getApplicationContext(),"ON",Toast.LENGTH_SHORT).show();
                    mOpenCvCameraView.setFlashOn();
                }
            }

        });
        redrawer = new Redrawer(
                Arrays.asList(new Plot[]{aprHistoryPlot}),
                100, false);

    }


    @Override
    public void onCameraViewStarted(int width, int height) {


    }

    @Override
    public void onCameraViewStopped() {

    }



    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {

        Mat rgba = inputFrame.rgba();
        Mat check = rgba.clone();

        Imgproc.cvtColor(check, check, Imgproc.COLOR_RGBA2GRAY);
        Imgproc.Canny(check,check,cannyThreshold/3,cannyThreshold );

        List<Mat> graysplit = new ArrayList<Mat>(3);
        Core.split(check,graysplit);

        Mat gray = graysplit.get(0);

        double lcheck = Core.mean(gray).val[0];

        if(lcheck == 0)
        {
            redrawer.start();
            List<Mat> clist = new ArrayList<Mat>(3);
            Core.split(rgba, clist);
            Mat mR = clist.get(0);
            double red = Core.mean(mR).val[0];

            if (azimuthHistorySeries.size() > HISTORY_SIZE)
                azimuthHistorySeries.removeFirst();



            mag = (red - prev);
            prev = red;

            mag = temp + ALPHA*(mag - temp);
            temp = mag;


            azimuthHistorySeries.addLast(null, (-mag));


            if((-mag) > 0.6)
            {
                count++;
            }
            if(i == 0)
            {
                time_init = System.currentTimeMillis();
            }
            if(i == 150)
            {
                i = -1;
                time_fin = System.currentTimeMillis();
                time = (time_fin - time_init)/1000;
                bpm = (int) Math.floor(60*((count/2)/time));
                Log.d("bpm","the bpm is" + bpm);
                runOnUiThread(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        if(bpm>30)
                        {
                            histvalue.setText("" +bpm+ " bpm");
                        }
                        else
                            histvalue.setText("Finger not present");
                    }
                });
                count = 0;
            }
            i++;


        }
        else
        {
            redrawer.pause();
            bpm = 0;
            runOnUiThread(new Runnable()
            {
                @Override
                public void run()
                {
                    histvalue.setText("Finger not present");
                }
            });

        }

        return rgba;
    }


    @Override
    public boolean onTouchEvent(MotionEvent e) {
        // MotionEvent reports input details from the touch screen
        // and other input controls. In this case, you are only
        // interested in events where the touch position changed.
        float y = e.getY();
        if (e.getAction() == MotionEvent.ACTION_MOVE) {
            if (lastTouchY > y)
                cannyThreshold += 5;
            else
                cannyThreshold -= 5;
            lastTouchY = y;
        }

        if (e.getAction() == MotionEvent.ACTION_UP)
            lastTouchY = 0;
        return true;
    }

    @Override
    public void onPause(){
        super.onPause();
        redrawer.pause();
        if(mOpenCvCameraView!=null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume()
    {
        super.onResume();
        redrawer.start();
        if(!OpenCVLoader.initDebug()){
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialisation");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_1_0,this,mLoaderCallBack);
        }
        else{
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallBack.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        redrawer.finish();
        if(mOpenCvCameraView!=null)
            mOpenCvCameraView.disableView();
    }

}
