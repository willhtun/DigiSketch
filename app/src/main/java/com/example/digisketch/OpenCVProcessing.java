package com.example.digisketch;

import android.graphics.Bitmap;
import android.util.Log;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.CLAHE;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

public class OpenCVProcessing {
    public Mat[] process(Mat img) {
        // Increase Contrast
        Mat img_temp = img.clone();
        Mat lab = new Mat();
        Mat l2 = new Mat();
        List<Mat> split_lab = new ArrayList<>();
        CLAHE clahe = Imgproc.createCLAHE(3.0, new Size(8, 8));
        Imgproc.cvtColor(img_temp, lab, Imgproc.COLOR_BGR2Lab);
        Core.split(lab, split_lab);
        clahe.apply(split_lab.get(0), l2);
        Core.merge(split_lab, lab);
        Imgproc.cvtColor(lab, img_temp, Imgproc.COLOR_Lab2BGR);

        // Remove some noise
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(3, 3));
        Imgproc.erode(img_temp, img_temp, kernel, new Point(-1,-1), 1);
        Imgproc.GaussianBlur(img_temp, img_temp, new Size(3,3), 0, 0, Core.BORDER_DEFAULT);

        // Canny (Big blobs approximated to subtract outliers)
        Mat img_canny_0 = new Mat();
        Mat img_canny_1 = new Mat();
        Mat img_canny_2 = new Mat();
        Mat img_canny_3 = new Mat();
        Mat img_canny_4 = new Mat();
        Mat[] img_canny_array = { img_canny_0, img_canny_1, img_canny_2, img_canny_3, img_canny_4 };
        for (int i = 0; i < 5; i++) {
            Imgproc.Canny(img_temp, img_canny_array[i], 25 + (i*15), 60 + (i*15));
            Imgproc.dilate(img_canny_array[i], img_canny_array[i], kernel, new Point(-1, -1), 25);
        }

        // Find Adaptive Threshold
        Mat img_at_0 = new Mat();
        Mat img_at_1 = new Mat();
        Mat img_at_2 = new Mat();
        Mat img_at_3 = new Mat();
        Mat img_at_4 = new Mat();
        Mat[] img_at_array = { img_at_0, img_at_1, img_at_2, img_at_3, img_at_4 };
        for (int i = 0; i < 5; i++) {
            Imgproc.cvtColor(img, img_at_array[i], Imgproc.COLOR_BGR2GRAY);
            Imgproc.adaptiveThreshold(img_at_array[i], img_at_array[i], 255, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY, 55, 5 + ((4 - i) * 7));
            Core.bitwise_not(img_at_array[i], img_at_array[i]);
        }


        // Image Subtraction To Remove Outlier Noises
        Mat img_sub_00 = new Mat();
        Mat img_sub_01 = new Mat();
        Mat img_sub_02 = new Mat();
        Mat img_sub_03 = new Mat();
        Mat img_sub_04 = new Mat();
        Mat img_sub_10 = new Mat();
        Mat img_sub_11 = new Mat();
        Mat img_sub_12 = new Mat();
        Mat img_sub_13 = new Mat();
        Mat img_sub_14 = new Mat();
        Mat img_sub_20 = new Mat();
        Mat img_sub_21 = new Mat();
        Mat img_sub_22 = new Mat();
        Mat img_sub_23 = new Mat();
        Mat img_sub_24 = new Mat();
        Mat img_sub_30 = new Mat();
        Mat img_sub_31 = new Mat();
        Mat img_sub_32 = new Mat();
        Mat img_sub_33 = new Mat();
        Mat img_sub_34 = new Mat();
        Mat img_sub_40 = new Mat();
        Mat img_sub_41 = new Mat();
        Mat img_sub_42 = new Mat();
        Mat img_sub_43 = new Mat();
        Mat img_sub_44 = new Mat();
        Mat[] img_sub_array = { img_sub_00, img_sub_01, img_sub_02, img_sub_03, img_sub_04,
                                img_sub_10, img_sub_11, img_sub_12, img_sub_13, img_sub_14,
                                img_sub_20, img_sub_21, img_sub_22, img_sub_23, img_sub_24,
                                img_sub_30, img_sub_31, img_sub_32, img_sub_33, img_sub_34,
                                img_sub_40, img_sub_41, img_sub_42, img_sub_43, img_sub_44,};
        for (int i = 0; i < 5; i++) { // at
            for (int j = 0; j < 5; j++) { //canny
                Core.bitwise_and(img_at_array[i], img_canny_array[j], img_sub_array[(i*5)+j]);
                // TODO remove if final image clean up is added
                Core.bitwise_not(img_sub_array[(i*5)+j], img_sub_array[(i*5)+j]);
            }
        }

        // Final Image Clean Up
        Mat output = new Mat();
        Mat stats = new Mat();
        Mat centroids = new Mat();
        int nb_components = Imgproc.connectedComponentsWithStats(img_sub_array[23], output, stats, centroids, 8);
        List<Integer> sizes = new ArrayList<>();
        byte buff[] = new byte[(int) stats.total() * stats.channels()];
        for (int i = 1; i < stats.rows(); i++) {
            sizes.add(stats.get(i, 4, buff));
        }
        int min_size = 50;
        Mat img_final = new Mat(img_sub_array[23].rows(), img_sub_array[23].height(), CvType.CV_8U, Scalar.all(0));
        for (int i = 1; i < nb_components; i++) {
            if (sizes.get(i) >= min_size) {
                Core.compare(output, new Scalar(i), img_final, Core.CMP_EQ);
            }
        }
        Core.bitwise_not(img_final, img_final);

        // Return
        return img_sub_array;
    }
}
