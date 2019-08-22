package com.example.digisketch;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;

import java.io.ByteArrayOutputStream;

public class PostProcess extends AppCompatActivity {
    private Bitmap[] final_bitmap_array;
    private int seekValue_at = 2;
    private int seekValue_canny = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        OpenCVLoader.initDebug();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post_process);
        ((ProgressBar) findViewById(R.id.image_progressbar)).setVisibility(View.VISIBLE);

        String img_path = getIntent().getStringExtra("img_path");

        try {
            int cropped_height = getIntent().getIntExtra("jpeg_height", 0);
            int cropped_width = getIntent().getIntExtra("jpeg_width", 0);
            int starting_y = getIntent().getIntExtra("starting_y", 0);

            String[] params = {img_path,
                                Integer.toString(cropped_height),
                                Integer.toString(cropped_width),
                                Integer.toString(starting_y)};

            new DownloadImageTask().execute(params);

        } catch (RuntimeException e) {
            e.printStackTrace();
        }

        // SET UPS
        setUpSeekBar();
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
                seekValue_at = seekBar.getProgress();
                ((ImageView) findViewById(R.id.image_viewer)).setImageBitmap(final_bitmap_array[(seekValue_at*5)+seekValue_canny]);
            }
        });

        SeekBar seek_canny = (SeekBar) findViewById(R.id.process_seekbar_canny);
        seek_canny.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

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
                seekValue_canny = seekBar.getProgress();
                ((ImageView) findViewById(R.id.image_viewer)).setImageBitmap(final_bitmap_array[(seekValue_at*5)+seekValue_canny]);
            }
        });
    }

    private class DownloadImageTask extends AsyncTask<String, Integer, Bitmap[]> {
        @Override
        protected Bitmap[] doInBackground(String... objects) {
            Bitmap bitmap = BitmapFactory.decodeFile(objects[0]);
            publishProgress(10);

            int cropped_height = Integer.valueOf(objects[1]);
            int cropped_width = Integer.valueOf(objects[2]);
            int starting_y = Integer.valueOf(objects[3]);
            publishProgress(30);

            int[] pixels = new int[cropped_width * (cropped_height-starting_y)];
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bitmap.getPixels(pixels, 0, cropped_width, 0, starting_y, cropped_width, cropped_height - starting_y);
            publishProgress(55);

            bitmap = Bitmap.createBitmap(pixels, 0, cropped_width, cropped_width, cropped_height - starting_y - starting_y, Bitmap.Config.ARGB_8888);
            publishProgress(90);

            bitmap = RotateBitmap(bitmap, 90);

            Mat mat = new Mat();
            Bitmap bmp32 = bitmap.copy(Bitmap.Config.ARGB_8888, true);
            Utils.bitmapToMat(bmp32, mat);

            Mat[] final_mats = new OpenCVProcessing().process(mat);
            final_bitmap_array = new Bitmap[25];

            for (int i = 0; i < 25; i++) {
                Bitmap final_bitmap = Bitmap.createBitmap(final_mats[i].cols(), final_mats[i].rows(), Bitmap.Config.ARGB_8888);
                Utils.matToBitmap(final_mats[i], final_bitmap);
                final_bitmap_array[i] = final_bitmap;
            }

            return final_bitmap_array;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            ((ProgressBar) findViewById(R.id.image_progressbar)).setProgress(values[0]);
        }

        protected void onPostExecute(Bitmap[] result) {
            ((ProgressBar) findViewById(R.id.image_progressbar)).setVisibility(View.GONE);
            ((ImageView) findViewById(R.id.image_viewer)).setImageBitmap(result[2]);
        }

        private Bitmap RotateBitmap(Bitmap source, float angle)
        {
            Matrix matrix = new Matrix();
            matrix.postRotate(angle);
            publishProgress(100);

            return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
        }
    }
}

