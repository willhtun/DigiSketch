package com.studio764.digisketch;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.StrictMode;
import android.text.method.DateTimeKeyListener;
import android.util.Log;
import android.util.TimingLogger;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.dropbox.core.DbxApiException;
import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.UploadErrorException;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.tasks.Task;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;

public class PostProcess extends AppCompatActivity {
    File picturesFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);

    private Bitmap[] final_bitmap_array;
    private Bitmap[] final_bitmap_array_transparent;
    private boolean transparent_mode = false;

    private int seekValue_at_preview = 1;
    private int seekValue_at = 1;
    private int mode = 0;

    private boolean cropped = false;
    private Rect croppedRect;

    private GoogleSignInAccount google_account;
    private String dropbox_authToken;

    private ImageView image_view;
    private ImageView crop_1;
    private ImageView crop_2;
    private ImageView crop_3;
    private ImageView crop_4;
    private ImageView crop_rect;
    private ImageView crop_mask;
    private int[] crop_1_rollback = new int[2];
    private int[] crop_2_rollback = new int[2];
    private int[] crop_3_rollback = new int[2];
    private int[] crop_4_rollback = new int[2];
    private ProgressBar progressBar;
    private TextView progressText;
    private ConstraintLayout seekbar_wrapper;
    private LinearLayout panel_options;
    private LinearLayout panel_adjust;
    private LinearLayout panel_crop;
    private LinearLayout panel_save;

    int jpeg_height;
    int jpeg_width;
    int starting_y;
    private float scalingFactor;
    private boolean flash;
    int cropx_size_px;
    int rotationDeg;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        TimingLogger timings = new TimingLogger("time_test", "start");

        OpenCVLoader.initDebug();
        timings.addSplit("set up 10");
        super.onCreate(savedInstanceState);
        timings.addSplit("set up 11");
        setContentView(R.layout.activity_post_process);
        timings.addSplit("set up 12");

        findViewById(R.id.camera_captureProcess).setVisibility(View.VISIBLE);

        String img_path = getIntent().getStringExtra("img_path");
        timings.addSplit("set up 2");

        image_view = findViewById(R.id.image_viewer);
        crop_1 = findViewById(R.id.crop_1);
        crop_2 = findViewById(R.id.crop_2);
        crop_3 = findViewById(R.id.crop_3);
        crop_4 = findViewById(R.id.crop_4);
        crop_rect = findViewById(R.id.image_cropRect);
        crop_mask = findViewById(R.id.image_cropMask);
        seekbar_wrapper = findViewById(R.id.process_seekbar_wrapper);
        panel_options = findViewById(R.id.panel_options);
        panel_adjust = findViewById(R.id.panel_adjust);
        panel_crop = findViewById(R.id.panel_crop);
        panel_save = findViewById(R.id.panel_save);
        progressBar = findViewById(R.id.camera_progressBar);
        progressText = findViewById(R.id.camera_progressText);
        timings.addSplit("set up 3");
        try {
            google_account = getIntent().getParcelableExtra("account");
            dropbox_authToken = getIntent().getStringExtra("dropboxAuthToken");

            jpeg_height = getIntent().getIntExtra("jpeg_height", 0);
            jpeg_width = getIntent().getIntExtra("jpeg_width", 0);
            starting_y = getIntent().getIntExtra("starting_y", 0);
            scalingFactor = getIntent().getFloatExtra("scaling_factor", 0f);
            flash = getIntent().getBooleanExtra("flash", false);
            rotationDeg = getIntent().getIntExtra("rotationDegree", 0);
            timings.addSplit("intents set up");

            Bitmap bitmap = BitmapFactory.decodeFile(img_path);
            bitmap = RotateBitmap(bitmap, rotationDeg);
            timings.addSplit("bit map set up");

            Bitmap[] params2 = {bitmap};
            image_view.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image_view.setImageBitmap(bitmap);
            timings.addSplit("params set up");
            timings.dumpToLog();

            new DownloadImageTask().execute(params2);

        } catch (RuntimeException e) {
            google_account = null;
            dropbox_authToken = null;
            e.printStackTrace();
        }

        // SET UPS
        setUpSeekBar();

        // previously under onResume
        cropx_size_px = dpToPx(crop_1.getWidth());

        panel_options.setVisibility(View.INVISIBLE);
        findViewById(R.id.btn_takepicture).setVisibility(View.VISIBLE);
        if (flash) {
            findViewById(R.id.btn_flash_on).setVisibility(View.VISIBLE);
            findViewById(R.id.btn_flash_off).setVisibility(View.INVISIBLE);
        } else {
            findViewById(R.id.btn_flash_on).setVisibility(View.INVISIBLE);
            findViewById(R.id.btn_flash_off).setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onBackPressed() {
        switch (mode) {
            case 0:
                finish(); break;
            case 1:
                cancelAdjust(findViewById(R.id.adjust_cancel)); break;
            case 2:
                cancelCrop(findViewById(R.id.crop_cancel)); break;
            case 3:
                cancelSave(findViewById(R.id.processBtn_saveCancel)); break;
            default:
                break;
        }
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
                if (transparent_mode)
                    image_view.setImageBitmap(final_bitmap_array_transparent[seekValue_at_preview]);
                else
                    image_view.setImageBitmap(final_bitmap_array[seekValue_at_preview]);
            }
        });
    }

    // Cropping ====================================================================================
    public void punchHole(float x, float y, float w, float h) {
        Bitmap mask = Bitmap.createBitmap(image_view.getWidth(), image_view.getHeight(), Bitmap.Config.ARGB_8888);
        mask.eraseColor(android.graphics.Color.argb(175, 0, 0, 0));
        Canvas canvas = new Canvas(mask);
        Paint paint = new Paint();
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        canvas.drawRect(x, y, w, h, paint);
        crop_mask.setImageBitmap(mask);
    }

    private void setUpCrop() {
        final int windowwidth = image_view.getWidth();
        final int windowheight = image_view.getHeight();
        final int windowLeftBorder = (Resources.getSystem().getDisplayMetrics().widthPixels - windowwidth) / 2;

        crop_1.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                ConstraintLayout.LayoutParams layoutParams1 = (ConstraintLayout.LayoutParams) crop_1.getLayoutParams();
                ConstraintLayout.LayoutParams layoutParams2 = (ConstraintLayout.LayoutParams) crop_2.getLayoutParams();
                ConstraintLayout.LayoutParams layoutParams4 = (ConstraintLayout.LayoutParams) crop_4.getLayoutParams();
                ConstraintLayout.LayoutParams rectParam = (ConstraintLayout.LayoutParams) crop_rect.getLayoutParams();

                switch (event.getAction()) {
                    case MotionEvent.ACTION_MOVE:
                        int x_cord = (int) event.getRawX();
                        int y_cord = (int) event.getRawY();

                        if (x_cord > crop_2.getLeft() - 180) x_cord = (int) crop_2.getLeft() - 180;
                        if (y_cord > crop_4.getTop() - 100) y_cord = (int) crop_4.getTop() - 100;
                        if (x_cord > windowwidth) x_cord = windowwidth;
                        if (x_cord < windowLeftBorder) x_cord = windowLeftBorder;
                        if (y_cord > windowheight) y_cord = windowheight;


                        layoutParams1.leftMargin = x_cord - 100;
                        layoutParams1.topMargin = y_cord - 180;

                        layoutParams2.topMargin = y_cord - 180;

                        layoutParams4.leftMargin = x_cord - 100;

                        crop_1.setLayoutParams(layoutParams1);
                        crop_2.setLayoutParams(layoutParams2);
                        crop_4.setLayoutParams(layoutParams4);

                        punchHole(x_cord - 100, y_cord - 180, crop_2.getRight(), crop_4.getBottom());
                        break;
                    case MotionEvent.ACTION_UP:
                        rectParam.leftMargin = crop_1.getLeft();
                        rectParam.topMargin = crop_1.getTop();
                        rectParam.width = crop_2.getRight() - crop_1.getLeft();
                        rectParam.height = crop_4.getBottom() - crop_1.getTop();

                        crop_rect.setLayoutParams(rectParam);
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
                ConstraintLayout.LayoutParams rectParam = (ConstraintLayout.LayoutParams) crop_rect.getLayoutParams();

                switch (event.getAction()) {
                    case MotionEvent.ACTION_MOVE:
                        int x_cord = (int) event.getRawX();
                        int y_cord = (int) event.getRawY();

                        if (x_cord < crop_1.getRight() + 200) x_cord = (int) crop_1.getRight() + 200;
                        if (y_cord > crop_3.getTop() - 100) y_cord = (int) crop_3.getTop() - 100;
                        if (x_cord > windowwidth) x_cord = windowwidth;
                        if (x_cord < windowLeftBorder) x_cord = windowLeftBorder;
                        if (y_cord > windowheight) y_cord = windowheight;

                        layoutParams2.rightMargin = windowwidth - x_cord - 25;
                        layoutParams2.topMargin = y_cord - 180;

                        layoutParams1.topMargin = y_cord - 180;

                        layoutParams3.rightMargin = windowwidth - x_cord - 25;

                        crop_2.setLayoutParams(layoutParams2);
                        crop_1.setLayoutParams(layoutParams1);
                        crop_3.setLayoutParams(layoutParams3);

                        punchHole(crop_1.getLeft(), y_cord - 180, x_cord + 25, crop_4.getBottom());
                        break;
                    case MotionEvent.ACTION_UP:
                        rectParam.leftMargin = crop_1.getLeft();
                        rectParam.topMargin = crop_1.getTop();
                        rectParam.width = crop_2.getRight() - crop_1.getLeft();
                        rectParam.height = crop_4.getBottom() - crop_1.getTop();

                        crop_rect.setLayoutParams(rectParam);
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
                ConstraintLayout.LayoutParams rectParam = (ConstraintLayout.LayoutParams) crop_rect.getLayoutParams();

                switch (event.getAction()) {
                    case MotionEvent.ACTION_MOVE:
                        int x_cord = (int) event.getRawX();
                        int y_cord = (int) event.getRawY();

                        if (x_cord < crop_4.getRight() + 200) x_cord = (int) crop_4.getRight() + 200;
                        if (y_cord < crop_2.getBottom() + 250) y_cord = (int) crop_2.getBottom() + 250;
                        if (x_cord > windowwidth) x_cord = windowwidth;
                        if (x_cord < windowLeftBorder) x_cord = windowLeftBorder;
                        if (y_cord > windowheight) y_cord = windowheight;

                        layoutParams3.rightMargin = windowwidth - x_cord - 25;
                        layoutParams3.bottomMargin = windowheight - y_cord;

                        layoutParams2.rightMargin = windowwidth - x_cord - 25;

                        layoutParams4.bottomMargin = windowheight - y_cord;

                        crop_3.setLayoutParams(layoutParams3);
                        crop_2.setLayoutParams(layoutParams2);
                        crop_4.setLayoutParams(layoutParams4);

                        punchHole(crop_1.getLeft(), crop_1.getTop(), x_cord + 25, y_cord);
                        break;
                    case MotionEvent.ACTION_UP:
                        rectParam.leftMargin = crop_1.getLeft();
                        rectParam.topMargin = crop_1.getTop();
                        rectParam.width = crop_2.getRight() - crop_1.getLeft();
                        rectParam.height = crop_4.getBottom() - crop_1.getTop();

                        crop_rect.setLayoutParams(rectParam);
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
                ConstraintLayout.LayoutParams rectParam = (ConstraintLayout.LayoutParams) crop_rect.getLayoutParams();

                switch (event.getAction()) {
                    case MotionEvent.ACTION_MOVE:
                        int x_cord = (int) event.getRawX();
                        int y_cord = (int) event.getRawY();

                        if (x_cord > crop_3.getLeft() - 200) x_cord = (int) crop_3.getLeft() - 200;
                        if (y_cord < crop_1.getBottom() + 250) y_cord = (int) crop_1.getBottom() + 250;
                        if (x_cord > windowwidth) x_cord = windowwidth;
                        if (x_cord < windowLeftBorder) x_cord = windowLeftBorder;
                        if (y_cord > windowheight) y_cord = windowheight;

                        layoutParams4.leftMargin = x_cord - 100;
                        layoutParams4.bottomMargin = windowheight - y_cord;

                        layoutParams1.leftMargin = x_cord - 100;

                        layoutParams3.bottomMargin = windowheight - y_cord;

                        crop_4.setLayoutParams(layoutParams4);
                        crop_1.setLayoutParams(layoutParams1);
                        crop_3.setLayoutParams(layoutParams3);

                        punchHole(x_cord - 100, crop_1.getTop(), crop_2.getRight(), y_cord);
                        break;
                    case MotionEvent.ACTION_UP:
                        rectParam.leftMargin = crop_1.getLeft();
                        rectParam.topMargin = crop_1.getTop();
                        rectParam.width = crop_2.getRight() - crop_1.getLeft();
                        rectParam.height = crop_4.getBottom() - crop_1.getTop();

                        crop_rect.setLayoutParams(rectParam);
                        break;
                    default:
                        break;
                }
                return true;
            }
        });
        crop_rect.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
            ConstraintLayout.LayoutParams layoutParams1 = (ConstraintLayout.LayoutParams) crop_1.getLayoutParams();
            ConstraintLayout.LayoutParams layoutParams2 = (ConstraintLayout.LayoutParams) crop_2.getLayoutParams();
            ConstraintLayout.LayoutParams layoutParams3 = (ConstraintLayout.LayoutParams) crop_3.getLayoutParams();
            ConstraintLayout.LayoutParams layoutParams4 = (ConstraintLayout.LayoutParams) crop_4.getLayoutParams();
            ConstraintLayout.LayoutParams rectParam = (ConstraintLayout.LayoutParams) crop_rect.getLayoutParams();

            switch (event.getAction()) {
                case MotionEvent.ACTION_MOVE:
                    int x_cord = (int) event.getRawX();
                    int y_cord = (int) event.getRawY();

                    if (x_cord - 50 + (rectParam.width / 2) > windowwidth) x_cord = windowwidth - (rectParam.width / 2) + 50;
                    if (y_cord - 150 + (rectParam.height / 2) > windowheight) y_cord = windowheight - (rectParam.height / 2) + 150;

                    rectParam.leftMargin = x_cord - 50 - (rectParam.width / 2);
                    rectParam.topMargin = y_cord - 150 - (rectParam.height / 2);
                    crop_rect.setLayoutParams(rectParam);

                    layoutParams1.leftMargin = crop_rect.getLeft();
                    layoutParams1.topMargin = crop_rect.getTop();
                    layoutParams2.rightMargin = image_view.getWidth() - crop_rect.getRight();
                    layoutParams2.topMargin = crop_rect.getTop();
                    layoutParams3.rightMargin = image_view.getWidth() - crop_rect.getRight();
                    layoutParams3.bottomMargin = image_view.getHeight() - crop_rect.getBottom();
                    layoutParams4.leftMargin = crop_rect.getLeft();
                    layoutParams4.bottomMargin = image_view.getHeight() - crop_rect.getBottom();
                    crop_1.setLayoutParams(layoutParams1);
                    crop_2.setLayoutParams(layoutParams2);
                    crop_3.setLayoutParams(layoutParams3);
                    crop_4.setLayoutParams(layoutParams4);

                    // TODOS
                    punchHole(crop_rect.getLeft(), crop_rect.getTop(), crop_rect.getRight(), crop_rect.getBottom());
                    break;
                default:
                    break;
            }
            return true;
            }
        });
    }

    // Button functions ============================================================================
    public void openAdjustment(View view) {
        panel_options.setVisibility(View.INVISIBLE);
        panel_adjust.setVisibility(View.VISIBLE);
        seekbar_wrapper.setVisibility(View.VISIBLE);

        mode = 1;
    }

    public void confirmAdjust(View view) {
        seekValue_at = seekValue_at_preview;

        if (transparent_mode)
            image_view.setImageBitmap(final_bitmap_array_transparent[seekValue_at]);
        else
            image_view.setImageBitmap(final_bitmap_array[seekValue_at]);

        panel_options.setVisibility(View.VISIBLE);
        panel_adjust.setVisibility(View.INVISIBLE);
        seekbar_wrapper.setVisibility(View.INVISIBLE);

        mode = 0;
    }

    public void cancelAdjust(View view) {
        if (transparent_mode)
            image_view.setImageBitmap(final_bitmap_array_transparent[seekValue_at]);
        else
            image_view.setImageBitmap(final_bitmap_array[seekValue_at]);

        ((SeekBar) findViewById(R.id.process_seekbar_at)).setProgress(seekValue_at);

        panel_options.setVisibility(View.VISIBLE);
        panel_adjust.setVisibility(View.INVISIBLE);
        seekbar_wrapper.setVisibility(View.INVISIBLE);

        mode = 0;
    }

    public void openCrop(View view) {
        final int windowwidth = image_view.getWidth();
        final int windowheight = image_view.getHeight();

        punchHole(crop_1.getLeft(), crop_1.getTop(), crop_2.getRight(), crop_4.getBottom());

        panel_options.setVisibility(View.INVISIBLE);
        panel_crop.setVisibility(View.VISIBLE);
        crop_mask.setVisibility(View.VISIBLE);
        crop_1.setVisibility(View.VISIBLE);
        crop_2.setVisibility(View.VISIBLE);
        crop_3.setVisibility(View.VISIBLE);
        crop_4.setVisibility(View.VISIBLE);
        crop_rect.setVisibility(View.VISIBLE);

        crop_1_rollback[0] = crop_1.getLeft();
        crop_1_rollback[1] = crop_1.getTop();
        crop_2_rollback[0] = windowwidth - crop_2.getRight();
        crop_2_rollback[1] = crop_2.getTop();
        crop_3_rollback[0] = windowwidth - crop_3.getRight();
        crop_3_rollback[1] = windowheight - crop_3.getBottom();
        crop_4_rollback[0] = crop_4.getLeft();
        crop_4_rollback[1] = windowheight - crop_4.getBottom();

        ConstraintLayout.LayoutParams rectParam = (ConstraintLayout.LayoutParams) crop_rect.getLayoutParams();
        rectParam.width = crop_2.getRight() - crop_1.getLeft();
        rectParam.height = crop_4.getBottom() - crop_1.getTop();
        crop_rect.setLayoutParams(rectParam);

        mode = 2;
    }

    public void confirmCrop(View view) {
        cropped = true;

        croppedRect = new Rect( crop_1.getLeft(),
                                crop_1.getTop(),
                                crop_2.getRight(),
                                crop_3.getBottom() );

        panel_options.setVisibility(View.VISIBLE);
        panel_crop.setVisibility(View.INVISIBLE);
        crop_1.setVisibility(View.INVISIBLE);
        crop_2.setVisibility(View.INVISIBLE);
        crop_3.setVisibility(View.INVISIBLE);
        crop_4.setVisibility(View.INVISIBLE);
        crop_rect.setVisibility(View.INVISIBLE);

        mode = 0;
    }

    public void cancelCrop(View view) {
        final int windowwidth = image_view.getWidth();
        final int windowheight = image_view.getHeight();

        ConstraintLayout.LayoutParams layoutParams1 = (ConstraintLayout.LayoutParams) crop_1.getLayoutParams();
        ConstraintLayout.LayoutParams layoutParams2 = (ConstraintLayout.LayoutParams) crop_2.getLayoutParams();
        ConstraintLayout.LayoutParams layoutParams3 = (ConstraintLayout.LayoutParams) crop_3.getLayoutParams();
        ConstraintLayout.LayoutParams layoutParams4 = (ConstraintLayout.LayoutParams) crop_4.getLayoutParams();
        layoutParams1.leftMargin = crop_1_rollback[0];
        layoutParams1.topMargin = crop_1_rollback[1];
        layoutParams2.rightMargin = crop_2_rollback[0];
        layoutParams2.topMargin = crop_2_rollback[1];
        layoutParams3.rightMargin = crop_3_rollback[0];
        layoutParams3.bottomMargin = crop_3_rollback[1];
        layoutParams4.leftMargin = crop_4_rollback[0];
        layoutParams4.bottomMargin = crop_4_rollback[1];
        crop_1.setLayoutParams(layoutParams1);
        crop_2.setLayoutParams(layoutParams2);
        crop_3.setLayoutParams(layoutParams3);
        crop_4.setLayoutParams(layoutParams4);

        ConstraintLayout.LayoutParams rectParam = (ConstraintLayout.LayoutParams) crop_rect.getLayoutParams();
        rectParam.leftMargin = crop_1_rollback[0];
        rectParam.topMargin = crop_1_rollback[1];
        rectParam.width = crop_1_rollback[0] - crop_2_rollback[0];
        rectParam.height = crop_1_rollback[1] - crop_4_rollback[1];
        crop_rect.setLayoutParams(rectParam);

        punchHole(crop_1_rollback[0], crop_1_rollback[1], windowwidth - crop_2_rollback[0], windowheight - crop_4_rollback[1]);

        if (cropped)
            crop_mask.setVisibility(View.VISIBLE);
        else
            crop_mask.setVisibility(View.INVISIBLE);

        panel_options.setVisibility(View.VISIBLE);
        panel_crop.setVisibility(View.INVISIBLE);
        crop_1.setVisibility(View.INVISIBLE);
        crop_2.setVisibility(View.INVISIBLE);
        crop_3.setVisibility(View.INVISIBLE);
        crop_4.setVisibility(View.INVISIBLE);
        crop_rect.setVisibility(View.INVISIBLE);

        mode = 0;
    }

    public void resetCrop(View view) {
        final int windowwidth = image_view.getWidth();
        final int windowheight = image_view.getHeight();

        cropped = false;

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

        ConstraintLayout.LayoutParams rectParam = (ConstraintLayout.LayoutParams) crop_rect.getLayoutParams();
        rectParam.leftMargin = dpToPx(32);
        rectParam.topMargin = dpToPx(32);
        rectParam.width = windowwidth - dpToPx(64);
        rectParam.height = windowheight - dpToPx(64);
        crop_rect.setLayoutParams(rectParam);

        crop_mask.setVisibility(View.INVISIBLE);
        panel_options.setVisibility(View.VISIBLE);
        panel_crop.setVisibility(View.INVISIBLE);
        crop_1.setVisibility(View.INVISIBLE);
        crop_2.setVisibility(View.INVISIBLE);
        crop_3.setVisibility(View.INVISIBLE);
        crop_4.setVisibility(View.INVISIBLE);
        crop_rect.setVisibility(View.INVISIBLE);

        mode = 0;
    }

    public void openRemoveBackground(View view) {
        if (!transparent_mode) {
            transparent_mode = true;
            image_view.setImageBitmap(final_bitmap_array_transparent[seekValue_at]);
        }
        else {
            transparent_mode = false;
            image_view.setImageBitmap(final_bitmap_array[seekValue_at]);
        }
    }

    public void openSave(View view) {
        panel_options.setVisibility(View.INVISIBLE);
        panel_save.setVisibility(View.VISIBLE);

        mode = 3;
    }

    public void cancelSave(View view) {
        panel_options.setVisibility(View.VISIBLE);
        panel_save.setVisibility(View.INVISIBLE);

        mode = 0;
    }

    public Bitmap getSaveBitmap() {
        if (transparent_mode) {
            Bitmap saveBmp; //0.8686
            if (cropped) {
                Log.d("cropped_test", "xxx:" + (croppedRect.width() * scalingFactor));
                Log.d("cropped_test2", final_bitmap_array_transparent[seekValue_at].getWidth() + "_" + findViewById(R.id.image_viewer).getWidth());
                saveBmp = Bitmap.createBitmap(final_bitmap_array_transparent[seekValue_at],
                        ((int)((croppedRect.left * scalingFactor) + (dpToPx(8)/2) + starting_y)),
                        (int) (croppedRect.top * scalingFactor),
                        (int) (croppedRect.width() * scalingFactor),
                        (int) (croppedRect.height() * scalingFactor));
            }
            else {
                Log.d("dp_test", "." + dpToPx(8));
                Log.d("sizesize_test", final_bitmap_array_transparent[seekValue_at].getWidth() + "..." + final_bitmap_array_transparent[seekValue_at].getHeight());
                Log.d("sizesize_test", "..." + jpeg_width + " " + jpeg_height + " " + starting_y + " " + scalingFactor);
                saveBmp = Bitmap.createBitmap(final_bitmap_array_transparent[seekValue_at],
                        starting_y + (dpToPx(8)/2),
                        0,
                        jpeg_width - starting_y - starting_y - dpToPx(8),
                        jpeg_height);
            }
            return saveBmp;
        }
        else {
            Bitmap saveBmp; //0.8686
            if (cropped) {
                Log.d("cropped_test", "xxx:" + (croppedRect.width() * scalingFactor));
                Log.d("cropped_test2", final_bitmap_array[seekValue_at].getWidth() + "_" + findViewById(R.id.image_viewer).getWidth());
                saveBmp = Bitmap.createBitmap(final_bitmap_array[seekValue_at],
                        ((int)((croppedRect.left * scalingFactor) + (dpToPx(8)/2) + starting_y)),
                        (int) (croppedRect.top * scalingFactor),
                        (int) (croppedRect.width() * scalingFactor),
                        (int) (croppedRect.height() * scalingFactor));
            }
            else {
                Log.d("dp_test", "." + dpToPx(8));
                Log.d("sizesize_test", final_bitmap_array[seekValue_at].getWidth() + "..." + final_bitmap_array[seekValue_at].getHeight());
                Log.d("sizesize_test", "..." + jpeg_width + " " + jpeg_height + " " + starting_y + " " + scalingFactor);
                Log.d("sizesize_test", starting_y + ":" + (dpToPx(8)/2));

                saveBmp = Bitmap.createBitmap(final_bitmap_array[seekValue_at],
                        starting_y + (dpToPx(8)/2),
                        0,
                        jpeg_width - starting_y - starting_y - dpToPx(8),
                        jpeg_height);
            }
            return saveBmp;
        }
    }

    public void save_local(View view) {
        //4032:3024 -> 4032:2367
        //1038:1768 -> 1768:1038
        //scaling factor = 2.2805...
        File digisketchFolder = new File(picturesFolder, "DigiSketch");
        if (!digisketchFolder.exists())
            digisketchFolder.mkdir();

        // TODO catch folder creation error
        File file = new File(digisketchFolder, "pic_"+ Calendar.getInstance().getTimeInMillis() + ".png");
        try (FileOutputStream out = new FileOutputStream(file)) {
            Bitmap saveBmp = getSaveBitmap();
            saveBmp.compress(Bitmap.CompressFormat.PNG, 100, out);
            Toast.makeText(this, "Saved to /Pictures/DigiSketch", Toast.LENGTH_SHORT).show();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void save_drive(View view) {
        if (google_account == null)
            Toast.makeText(this, "Not signed in to Drive", Toast.LENGTH_SHORT).show();
        else {
            Toast.makeText(this, "Saved to " + google_account.getEmail(), Toast.LENGTH_SHORT).show();

            // Prepare image
            ByteArrayOutputStream out =  new ByteArrayOutputStream();
            Bitmap saveBmp = getSaveBitmap();
            saveBmp.compress(Bitmap.CompressFormat.PNG, 100, out);
            byte[] save_bytearray = out.toByteArray();

            // Prepare drive
            GoogleAccountCredential credential =
                    GoogleAccountCredential.usingOAuth2(
                            this, Collections.singleton(DriveScopes.DRIVE_FILE));
            credential.setSelectedAccount(google_account.getAccount());
            Drive googleDriveService =
                    new Drive.Builder(
                            AndroidHttp.newCompatibleTransport(),
                            new GsonFactory(),
                            credential)
                            .setApplicationName("Drive API Migration")
                            .build();
            DriveServiceHelper mDriveServiceHelper = new DriveServiceHelper(googleDriveService);

            // Upload to drive
            try {
                mDriveServiceHelper.saveFile( "pic_"+ Calendar.getInstance().getTimeInMillis(), save_bytearray);
            } catch (Exception e) {
                // Drive fails
            }
        }
    }

    public void save_drop(View view) {
        if (dropbox_authToken == null)
            Toast.makeText(this, "Not signed in to Dropbox", Toast.LENGTH_SHORT).show();
        else {
            // Prepare image
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Bitmap saveBmp = getSaveBitmap();
            saveBmp.compress(Bitmap.CompressFormat.PNG, 100, out);
            byte[] save_bytearray = out.toByteArray();

            // Prepare dropbox
            int SDK_INT = android.os.Build.VERSION.SDK_INT;
            if (SDK_INT > 8) {
                DbxClientV2 dropbox_client = null;
                try {
                    StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
                            .permitAll().build();
                    StrictMode.setThreadPolicy(policy);
                    DbxRequestConfig config = DbxRequestConfig.newBuilder("yr0vsv7wjiujk5a").build();
                    dropbox_client = new DbxClientV2(config, dropbox_authToken);
                } catch (Exception e) {
                    Toast.makeText(this, "Not signed in to Dropbox", Toast.LENGTH_SHORT).show();
                }

                // Upload to dropbox
                try {
                    InputStream in = new ByteArrayInputStream(save_bytearray);
                    FileMetadata metadata = dropbox_client.files().uploadBuilder("/pic_" + Calendar.getInstance().getTimeInMillis() + ".png")
                            .uploadAndFinish(in);
                    Toast.makeText(this, "Saved to " + dropbox_client.users().getCurrentAccount().getEmail(), Toast.LENGTH_SHORT).show();
                } catch (UploadErrorException e) {
                    Toast.makeText(this, "Upload error", Toast.LENGTH_SHORT).show();
                } catch (DbxException e) {
                    // Dropbox fails
                } catch (IOException e) {
                    // Input stream fails
                }
            }
        }
    }

    private void turnOnAdjustmentPanel() {
        panel_options.setVisibility(View.VISIBLE);
        findViewById(R.id.btn_takepicture).setVisibility(View.INVISIBLE);
        findViewById(R.id.btn_flash_on).setVisibility(View.INVISIBLE);
        findViewById(R.id.btn_flash_off).setVisibility(View.INVISIBLE);
    }

    // Image loading async task ====================================================================
    private class DownloadImageTask extends AsyncTask<Bitmap, Integer, Bitmap[]> {
        @Override
        protected Bitmap[] doInBackground(Bitmap... bm) {
            TimingLogger timings = new TimingLogger("time_test", "start");

            Bitmap bitmap = bm[0];
            Mat img = new Mat();
            Bitmap bmp32 = bitmap.copy(Bitmap.Config.ARGB_8888, true);
            Utils.bitmapToMat(bmp32, img);
            publishProgress(35);
            timings.addSplit("cv prepare");

            Mat img_temp = img.clone();
            Mat img_canny;
            Mat[] img_finals;
            Mat[] img_at;
            timings.addSplit("cv 0");

            img_temp = OpenCVProcessing.process1_contrast(img_temp);
            publishProgress(40);
            timings.addSplit("cv 1");

            img_temp = OpenCVProcessing.process2_removeNoise(img_temp);
            publishProgress(45);
            timings.addSplit("cv 2");

            img_canny = OpenCVProcessing.process3_canny(img_temp);
            publishProgress(55);
            timings.addSplit("cv 3");

            img_at = OpenCVProcessing.process4_at(img);
            publishProgress(75);
            timings.addSplit("cv 4");

            img_finals = OpenCVProcessing.process5_subtraction(img_at, img_canny);
            publishProgress(90);
            timings.addSplit("cv 5");

            final_bitmap_array = new Bitmap[3];
            final_bitmap_array_transparent = new Bitmap[3];

            for (int i = 0; i < 3; i++) {
                Bitmap final_bitmap = Bitmap.createBitmap(img_finals[i].cols(), img_finals[i].rows(), Bitmap.Config.ARGB_8888);
                Utils.matToBitmap(img_finals[i], final_bitmap);
                final_bitmap_array[i] = final_bitmap;
                final_bitmap_array_transparent[i] = makeTransparent(final_bitmap);
                publishProgress(100);
            }
            timings.addSplit("cv done");
            timings.dumpToLog();

            return final_bitmap_array;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            progressBar.setProgress(values[0]);
        }

        protected void onPostExecute(Bitmap[] result) {
            image_view.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image_view.setImageBitmap(result[1]);

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
            turnOnAdjustmentPanel();
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

    public static Bitmap makeTransparent(Bitmap bit) {
        Bitmap myBitmap = bit.copy(bit.getConfig(), true);
        int [] allpixels = new int [myBitmap.getHeight() * myBitmap.getWidth()];

        myBitmap.getPixels(allpixels, 0, myBitmap.getWidth(), 0, 0, myBitmap.getWidth(), myBitmap.getHeight());

        for(int i = 0; i < allpixels.length; i++)
        {
            if(allpixels[i] == Color.WHITE)
            {
                allpixels[i] = Color.TRANSPARENT;
            }
        }

        myBitmap.setPixels(allpixels,0,myBitmap.getWidth(),0, 0, myBitmap.getWidth(),myBitmap.getHeight());
        return myBitmap;
    }
}

