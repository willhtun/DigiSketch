package com.studio764.digisketch;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class PostProcess extends AppCompatActivity {
    private Bitmap[] final_bitmap_array;

    private int seekValue_at_preview = 1;
    private int seekValue_at = 1;

    private boolean cropped = false;
    private Rect croppedRect;

    private ImageView crop_1;
    private ImageView crop_2;
    private ImageView crop_3;
    private ImageView crop_4;

    int cropped_height;
    int cropped_width;
    int starting_y;
    private float scalingFactor;

    private ProgressBar progressBar;
    private TextView progressText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        OpenCVLoader.initDebug();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post_process);

        findViewById(R.id.camera_captureProcess).setVisibility(View.VISIBLE);

        String img_path = getIntent().getStringExtra("img_path");

        try {
            cropped_height = getIntent().getIntExtra("jpeg_height", 0);
            cropped_width = getIntent().getIntExtra("jpeg_width", 0);
            starting_y = getIntent().getIntExtra("starting_y", 0);
            scalingFactor = getIntent().getFloatExtra("scaling_factor", 0f);

            String[] params = {img_path,
                                Integer.toString(cropped_height),
                                Integer.toString(cropped_width),
                                Integer.toString(starting_y)};

            Bitmap bitmap = BitmapFactory.decodeFile(img_path);
            bitmap = RotateBitmap(bitmap, 90);

            Bitmap[] params2 = {bitmap};
            ((ImageView) findViewById(R.id.image_viewer)).setScaleType(ImageView.ScaleType.CENTER_CROP);
            ((ImageView) findViewById(R.id.image_viewer)).setImageBitmap(bitmap);

            new DownloadImageTask().execute(params2);

        } catch (RuntimeException e) {
            e.printStackTrace();
        }

        // SET UPS
        setUpSeekBar();

        crop_1 = findViewById(R.id.crop_1);
        crop_2 = findViewById(R.id.crop_2);
        crop_3 = findViewById(R.id.crop_3);
        crop_4 = findViewById(R.id.crop_4);
    }

    @Override
    protected void onResume() {
        super.onResume();
        progressBar = findViewById(R.id.camera_progressBar);
        progressText = findViewById(R.id.camera_progressText);
    }

    private void setUpSeekBar() {
        SeekBar seek_at = (SeekBar) findViewById(R.id.process_seekbar_at);
        seek_at.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // TODO Auto-generated method stub
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // TODO Auto-generated method stub
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                seekValue_at_preview = seekBar.getProgress();
                ((ImageView) findViewById(R.id.image_viewer)).setImageBitmap(final_bitmap_array[seekValue_at_preview]);
            }
        });
    }

    // Cropping ====================================================================================
    public void punchHole(float x, float y, float w, float h) {
        Bitmap mask = Bitmap.createBitmap(findViewById(R.id.image_viewer).getWidth(), findViewById(R.id.image_viewer).getHeight(), Bitmap.Config.ARGB_8888);
        mask.eraseColor(android.graphics.Color.argb(175, 0, 0, 0));
        Canvas canvas = new Canvas(mask);
        Paint paint = new Paint();
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        canvas.drawRect(x, y, w, h, paint);
        ((ImageView) findViewById(R.id.image_cropMask)).setImageBitmap(mask);
    }

    private void setUpCrop() {
        final int windowwidth = findViewById(R.id.image_viewer).getWidth();
        final int windowheight = findViewById(R.id.image_viewer).getHeight();
        final int windowLeftBorder = (Resources.getSystem().getDisplayMetrics().widthPixels - windowwidth) / 2;

        final ImageView crop_1 = findViewById(R.id.crop_1);
        final ImageView crop_2 = findViewById(R.id.crop_2);
        final ImageView crop_3 = findViewById(R.id.crop_3);
        final ImageView crop_4 = findViewById(R.id.crop_4);
        crop_1.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                ConstraintLayout.LayoutParams layoutParams1 = (ConstraintLayout.LayoutParams) crop_1.getLayoutParams();
                ConstraintLayout.LayoutParams layoutParams2 = (ConstraintLayout.LayoutParams) crop_2.getLayoutParams();
                ConstraintLayout.LayoutParams layoutParams4 = (ConstraintLayout.LayoutParams) crop_4.getLayoutParams();

                switch (event.getAction()) {
                    case MotionEvent.ACTION_MOVE:
                        int x_cord = (int) event.getRawX();
                        int y_cord = (int) event.getRawY();
                        
                        if (x_cord > windowwidth) x_cord = windowwidth;
                        if (x_cord < windowLeftBorder) x_cord = windowLeftBorder;
                        if (y_cord > windowheight) y_cord = windowheight;


                        layoutParams1.leftMargin = x_cord - 50;
                        layoutParams1.topMargin = y_cord - 150;

                        layoutParams2.topMargin = y_cord - 150;

                        layoutParams4.leftMargin = x_cord - 50;

                        crop_1.setLayoutParams(layoutParams1);
                        crop_2.setLayoutParams(layoutParams2);
                        crop_4.setLayoutParams(layoutParams4);

                        punchHole(crop_1.getLeft(), crop_1.getTop(), crop_2.getRight(), crop_4.getBottom());
                        break;
                    default:
                        break;
                }
                return true;
            }
        });
        crop_2.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                ConstraintLayout.LayoutParams layoutParams1 = (ConstraintLayout.LayoutParams) crop_1.getLayoutParams();
                ConstraintLayout.LayoutParams layoutParams2 = (ConstraintLayout.LayoutParams) crop_2.getLayoutParams();
                ConstraintLayout.LayoutParams layoutParams3 = (ConstraintLayout.LayoutParams) crop_3.getLayoutParams();

                switch (event.getAction()) {
                    case MotionEvent.ACTION_MOVE:
                        int x_cord = (int) event.getRawX();
                        int y_cord = (int) event.getRawY();

                        if (x_cord > windowwidth) x_cord = windowwidth;
                        if (x_cord < windowLeftBorder) x_cord = windowLeftBorder;
                        if (y_cord > windowheight) y_cord = windowheight;

                        layoutParams2.rightMargin = windowwidth - x_cord - 50;
                        layoutParams2.topMargin = y_cord - 150;

                        layoutParams1.topMargin = y_cord - 150;

                        layoutParams3.rightMargin = windowwidth - x_cord - 50;

                        crop_2.setLayoutParams(layoutParams2);
                        crop_1.setLayoutParams(layoutParams1);
                        crop_3.setLayoutParams(layoutParams3);

                        punchHole(crop_1.getLeft(), crop_1.getTop(), crop_2.getRight(), crop_4.getBottom());
                        break;
                    default:
                        break;
                }
                return true;
            }
        });
        crop_3.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                ConstraintLayout.LayoutParams layoutParams2 = (ConstraintLayout.LayoutParams) crop_2.getLayoutParams();
                ConstraintLayout.LayoutParams layoutParams3 = (ConstraintLayout.LayoutParams) crop_3.getLayoutParams();
                ConstraintLayout.LayoutParams layoutParams4 = (ConstraintLayout.LayoutParams) crop_4.getLayoutParams();

                switch (event.getAction()) {
                    case MotionEvent.ACTION_MOVE:
                        int x_cord = (int) event.getRawX();
                        int y_cord = (int) event.getRawY();

                        if (x_cord > windowwidth) x_cord = windowwidth;
                        if (x_cord < windowLeftBorder) x_cord = windowLeftBorder;
                        if (y_cord > windowheight) y_cord = windowheight;

                        layoutParams3.rightMargin = windowwidth - x_cord - 50;
                        layoutParams3.bottomMargin = windowheight - y_cord;

                        layoutParams2.rightMargin = windowwidth - x_cord - 50;

                        layoutParams4.bottomMargin = windowheight - y_cord;

                        crop_3.setLayoutParams(layoutParams3);
                        crop_2.setLayoutParams(layoutParams2);
                        crop_4.setLayoutParams(layoutParams4);

                        punchHole(crop_1.getLeft(), crop_1.getTop(), crop_2.getRight(), crop_4.getBottom());
                        break;
                    default:
                        break;
                }
                return true;
            }
        });
        crop_4.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                ConstraintLayout.LayoutParams layoutParams1 = (ConstraintLayout.LayoutParams) crop_1.getLayoutParams();
                ConstraintLayout.LayoutParams layoutParams3 = (ConstraintLayout.LayoutParams) crop_3.getLayoutParams();
                ConstraintLayout.LayoutParams layoutParams4 = (ConstraintLayout.LayoutParams) crop_4.getLayoutParams();

                switch (event.getAction()) {
                    case MotionEvent.ACTION_MOVE:
                        int x_cord = (int) event.getRawX();
                        int y_cord = (int) event.getRawY();

                        if (x_cord > windowwidth) x_cord = windowwidth;
                        if (x_cord < windowLeftBorder) x_cord = windowLeftBorder;
                        if (y_cord > windowheight) y_cord = windowheight;

                        layoutParams4.leftMargin = x_cord - 50;
                        layoutParams4.bottomMargin = windowheight - y_cord;

                        layoutParams1.leftMargin = x_cord - 50;

                        layoutParams3.bottomMargin = windowheight - y_cord;

                        crop_4.setLayoutParams(layoutParams4);
                        crop_1.setLayoutParams(layoutParams1);
                        crop_3.setLayoutParams(layoutParams3);

                        punchHole(crop_1.getLeft(), crop_1.getTop(), crop_2.getRight(), crop_4.getBottom());
                        break;
                    default:
                        break;
                }
                return true;
            }
        });
    }

    // Button functions ============================================================================
    public void openRetake(View view) {
        finish();
    }

    public void setUpAdjustmentButton(View view) {
        findViewById(R.id.panel_options).setVisibility(View.INVISIBLE);
        findViewById(R.id.panel_adjust).setVisibility(View.VISIBLE);
        findViewById(R.id.process_seekbar_wrapper).setVisibility(View.VISIBLE);
    }

    public void confirmAdjust(View view) {
        seekValue_at = seekValue_at_preview;
        ((ImageView) findViewById(R.id.image_viewer)).setImageBitmap(final_bitmap_array[seekValue_at]);

        findViewById(R.id.panel_options).setVisibility(View.VISIBLE);
        findViewById(R.id.panel_adjust).setVisibility(View.INVISIBLE);
        findViewById(R.id.process_seekbar_wrapper).setVisibility(View.INVISIBLE);
    }

    public void cancelAdjust(View view) {
        ((ImageView) findViewById(R.id.image_viewer)).setImageBitmap(final_bitmap_array[seekValue_at]);
        ((SeekBar) findViewById(R.id.process_seekbar_at)).setProgress(seekValue_at);

        findViewById(R.id.panel_options).setVisibility(View.VISIBLE);
        findViewById(R.id.panel_adjust).setVisibility(View.INVISIBLE);
        findViewById(R.id.process_seekbar_wrapper).setVisibility(View.INVISIBLE);
    }

    public void setUpCropButton(View view) {
        punchHole(crop_1.getLeft(), crop_1.getTop(), crop_2.getRight(), crop_4.getBottom());

        findViewById(R.id.panel_options).setVisibility(View.INVISIBLE);
        findViewById(R.id.panel_crop).setVisibility(View.VISIBLE);
        findViewById(R.id.image_cropMask).setVisibility(View.VISIBLE);
        findViewById(R.id.crop_1).setVisibility(View.VISIBLE);
        findViewById(R.id.crop_2).setVisibility(View.VISIBLE);
        findViewById(R.id.crop_3).setVisibility(View.VISIBLE);
        findViewById(R.id.crop_4).setVisibility(View.VISIBLE);
        findViewById(R.id.processBtn_retake).setVisibility(View.INVISIBLE);


    }

    public void confirmCrop(View view) {
        cropped = true;

        croppedRect = new Rect( findViewById(R.id.crop_1).getLeft(),
                                findViewById(R.id.crop_1).getTop(),
                                findViewById(R.id.crop_2).getRight(),
                                findViewById(R.id.crop_3).getBottom() );

        findViewById(R.id.panel_options).setVisibility(View.VISIBLE);
        findViewById(R.id.panel_crop).setVisibility(View.INVISIBLE);
        findViewById(R.id.crop_1).setVisibility(View.INVISIBLE);
        findViewById(R.id.crop_2).setVisibility(View.INVISIBLE);
        findViewById(R.id.crop_3).setVisibility(View.INVISIBLE);
        findViewById(R.id.crop_4).setVisibility(View.INVISIBLE);
    }

    public void resetCrop(View view) {
        cropped = false;

        final ImageView crop_1 = findViewById(R.id.crop_1);
        final ImageView crop_2 = findViewById(R.id.crop_2);
        final ImageView crop_3 = findViewById(R.id.crop_3);
        final ImageView crop_4 = findViewById(R.id.crop_4);
        ConstraintLayout.LayoutParams layoutParams1 = (ConstraintLayout.LayoutParams) crop_1.getLayoutParams();
        ConstraintLayout.LayoutParams layoutParams2 = (ConstraintLayout.LayoutParams) crop_2.getLayoutParams();
        ConstraintLayout.LayoutParams layoutParams3 = (ConstraintLayout.LayoutParams) crop_3.getLayoutParams();
        ConstraintLayout.LayoutParams layoutParams4 = (ConstraintLayout.LayoutParams) crop_4.getLayoutParams();
        layoutParams1.leftMargin = dpToPx(32);
        layoutParams1.topMargin = dpToPx(32);
        layoutParams2.rightMargin = dpToPx(32);
        layoutParams2.topMargin = dpToPx(32);
        layoutParams3.rightMargin = dpToPx(32);
        layoutParams3.bottomMargin = dpToPx(32);
        layoutParams4.leftMargin = dpToPx(32);
        layoutParams4.bottomMargin = dpToPx(32);
        crop_1.setLayoutParams(layoutParams1);
        crop_2.setLayoutParams(layoutParams2);
        crop_3.setLayoutParams(layoutParams3);
        crop_4.setLayoutParams(layoutParams4);

        findViewById(R.id.image_cropMask).setVisibility(View.INVISIBLE);
        findViewById(R.id.panel_options).setVisibility(View.VISIBLE);
        findViewById(R.id.panel_crop).setVisibility(View.INVISIBLE);
        findViewById(R.id.crop_1).setVisibility(View.INVISIBLE);
        findViewById(R.id.crop_2).setVisibility(View.INVISIBLE);
        findViewById(R.id.crop_3).setVisibility(View.INVISIBLE);
        findViewById(R.id.crop_4).setVisibility(View.INVISIBLE);
    }

    public void save(View view) {
        //4032:3024 -> 4032:2367
        //1038:1768 -> 1768:1038
        //scaling factor = 2.2805...
        File file = new File(Environment.getExternalStorageDirectory()+"/pic.jpg");
        try (FileOutputStream out = new FileOutputStream(file)) {
            Bitmap saveBmp;
            if (cropped)
                saveBmp = Bitmap.createBitmap(final_bitmap_array[seekValue_at],
                                                (int) (croppedRect.left * scalingFactor) + starting_y,
                                                (int) (croppedRect.top * scalingFactor),
                                                (int) (croppedRect.width() * scalingFactor),
                                                (int) (croppedRect.height() * scalingFactor));

            else {
                int[] pixels = new int[cropped_width * (cropped_height-starting_y)];
                final_bitmap_array[seekValue_at].getPixels(pixels, 0, cropped_height - starting_y, starting_y, 0, cropped_height - starting_y - starting_y, cropped_width);
                saveBmp = Bitmap.createBitmap(pixels,
                                                0,
                                                cropped_height - starting_y,
                                                cropped_height - starting_y - starting_y,
                                                cropped_width,
                                                Bitmap.Config.ARGB_8888);
            }

            saveBmp.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Image loading async task ====================================================================
    private class DownloadImageTask extends AsyncTask<Bitmap, Integer, Bitmap[]> {
        @Override
        protected Bitmap[] doInBackground(Bitmap... bm) {
            Bitmap bitmap = bm[0];
            Mat img = new Mat();
            Bitmap bmp32 = bitmap.copy(Bitmap.Config.ARGB_8888, true);
            Utils.bitmapToMat(bmp32, img);
            publishProgress(35);

            Mat img_temp = img.clone();
            Mat img_canny;
            Mat[] img_finals;
            Mat[] img_at;

            img_temp = OpenCVProcessing.process1_contrast(img_temp);
            publishProgress(40);

            img_temp = OpenCVProcessing.process2_removeNoise(img_temp);
            publishProgress(45);

            img_canny = OpenCVProcessing.process3_canny(img_temp);
            publishProgress(55);

            img_at = OpenCVProcessing.process4_at(img);
            publishProgress(75);

            img_finals = OpenCVProcessing.process5_subtraction(img_at, img_canny);
            publishProgress(90);


            final_bitmap_array = new Bitmap[3];

            for (int i = 0; i < 3; i++) {
                Bitmap final_bitmap = Bitmap.createBitmap(img_finals[i].cols(), img_finals[i].rows(), Bitmap.Config.ARGB_8888);
                Utils.matToBitmap(img_finals[i], final_bitmap);
                final_bitmap_array[i] = final_bitmap;
                publishProgress(100);
            }

            return final_bitmap_array;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            progressBar.setProgress(values[0]);
        }

        protected void onPostExecute(Bitmap[] result) {
            ((ImageView) findViewById(R.id.image_viewer)).setScaleType(ImageView.ScaleType.CENTER_CROP);
            ((ImageView) findViewById(R.id.image_viewer)).setImageBitmap(result[1]);

            ImageView process = findViewById(R.id.camera_captureProcess);
            process.setVisibility(View.VISIBLE);
            process.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .setListener(null);
            progressBar.setVisibility(View.VISIBLE);
            progressBar.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .setListener(null);
            progressText.setVisibility(View.VISIBLE);
            progressText.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .setListener(null);

            setUpCrop();
        }
    }

    private Bitmap RotateBitmap(Bitmap source, float angle)
    {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }

    private int dpToPx(float dp) {
        Resources r = getResources();
        float px = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                r.getDisplayMetrics()
        );
        return (int) px;
    }
}

