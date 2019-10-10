package com.studio764.digisketch;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.CLAHE;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

import static org.opencv.core.CvType.CV_8U;

public class OpenCVProcessing {
    public static Mat process1_contrast(Mat img_temp) {
        // Increase Contrast
        Mat lab = new Mat();
        Mat l2 = new Mat();
        List<Mat> split_lab = new ArrayList<>();
        CLAHE clahe = Imgproc.createCLAHE(3.0, new Size(8, 8));
        Imgproc.cvtColor(img_temp, lab, Imgproc.COLOR_BGR2Lab);
        Core.split(lab, split_lab);
        clahe.apply(split_lab.get(0), l2);
        Core.merge(split_lab, lab);
        Imgproc.cvtColor(lab, img_temp, Imgproc.COLOR_Lab2BGR);
        return img_temp;
    }

    public static Mat process2_removeNoise(Mat img_temp) {
        // Remove some noise
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(3, 3));
        Imgproc.erode(img_temp, img_temp, kernel, new Point(-1,-1), 1);
        Imgproc.GaussianBlur(img_temp, img_temp, new Size(3,3), 0, 0, Core.BORDER_DEFAULT);
        return img_temp;
    }

    public static Mat process3_canny(Mat img_temp) {
        // Canny (Big blobs approximated to subtract outliers)
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(3, 3));
        Mat img_canny = new Mat();
        Imgproc.Canny(img_temp, img_canny, 30, 80);
        Imgproc.dilate(img_canny, img_canny, kernel, new Point(-1, -1), 25);
        return img_canny;
    }

    public static Mat[] process4_at(Mat orig_img) {
        // Find Adaptive Threshold
        Mat img_at_0 = new Mat();
        Mat img_at_1 = new Mat();
        Mat img_at_2 = new Mat();
        Mat[] img_at_array = { img_at_0, img_at_1, img_at_2 };
        for (int i = 0; i < 3; i++) {
            Imgproc.cvtColor(orig_img, img_at_array[i], Imgproc.COLOR_BGR2GRAY); // 33, 26, 19, 12, 5
            Imgproc.adaptiveThreshold(img_at_array[i], img_at_array[i], 255, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY, 55, 5 + ((2 - i) * 7));
            Core.bitwise_not(img_at_array[i], img_at_array[i]);
        }
        return img_at_array;
    }

    public static Mat[] process5_subtraction(Mat[] img_at_array, Mat img_canny) {
        // Image Subtraction To Remove Outlier Noises
        Mat img_sub_00 = new Mat();
        Mat img_sub_01 = new Mat();
        Mat img_sub_02 = new Mat();
        Mat[] img_sub_array = { img_sub_00, img_sub_01, img_sub_02 };
        for (int i = 0; i < 3; i++) { // at
            Core.bitwise_and(img_at_array[i], img_canny, img_sub_array[i]);
            // TODO remove if final image clean up is added
            Core.bitwise_not(img_sub_array[i], img_sub_array[i]);
        }

        return img_sub_array;
    }

    public static Mat[] process6_postprocess(Mat[] img_sub_array) {
        for (int ii = 0; ii < 3; ii++) {
            Mat output = new Mat();
            Mat stats = new Mat();
            Mat centroids = new Mat();
            int nb_components = Imgproc.connectedComponentsWithStats(img_sub_array[ii], output, stats, centroids, 4);
            List<Integer> sizes = new ArrayList<>();
            int buff[] = new int[(int) stats.total() * stats.channels()];
            for (int i = 1; i < stats.rows(); i++) {
                sizes.add((int) (stats.get(i,4)[0]));
            }
            int min_size = 50;

            for (int i = 1; i < nb_components; i++) {
                if (stats.get(i,4)[0] < min_size) {
                    Imgproc.rectangle(img_sub_array[ii],
                                        new Rect(((int) stats.get(i,0)[0]),
                                                ((int) stats.get(i,1)[0]),
                                                ((int) stats.get(i,2)[0]),
                                                ((int) stats.get(i,3)[0])),
                                        new Scalar(150),
                                        100);
                }
            }

        }
        return img_sub_array;
    }

    public static Mat[] process(Mat img) {
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
        Mat img_canny = new Mat();
        Imgproc.Canny(img_temp, img_canny, 30, 80);
        Imgproc.dilate(img_canny, img_canny, kernel, new Point(-1, -1), 25);

        // Find Adaptive Threshold
        Mat img_at_0 = new Mat();
        Mat img_at_1 = new Mat();
        Mat img_at_2 = new Mat();
        Mat[] img_at_array = { img_at_0, img_at_1, img_at_2 };
        for (int i = 0; i < 3; i++) {
            Imgproc.cvtColor(img, img_at_array[i], Imgproc.COLOR_BGR2GRAY); // 33, 26, 19, 12, 5
            Imgproc.adaptiveThreshold(img_at_array[i], img_at_array[i], 255, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY, 55, 5 + ((2 - i) * 7));
            Core.bitwise_not(img_at_array[i], img_at_array[i]);
        }

        // Image Subtraction To Remove Outlier Noises
        Mat img_sub_00 = new Mat();
        Mat img_sub_01 = new Mat();
        Mat img_sub_02 = new Mat();
        Mat[] img_sub_array = { img_sub_00, img_sub_01, img_sub_02 };
        for (int i = 0; i < 3; i++) { // at
            Core.bitwise_and(img_at_array[i], img_canny, img_sub_array[i]);
            // TODO remove if final image clean up is added
            Core.bitwise_not(img_sub_array[i], img_sub_array[i]);
        }

        // Final Image Clean Up

        Mat[] final_img_array = new Mat[3];
        for (int ii = 0; ii < 3; ii++) {
            Mat output = new Mat();
            Mat stats = new Mat();
            Mat centroids = new Mat();
            int nb_components = Imgproc.connectedComponentsWithStats(img_sub_array[ii], output, stats, centroids, 8);
            List<Integer> sizes = new ArrayList<>();
            int buff[] = new int[(int) stats.total() * stats.channels()];
            for (int i = 2; i < stats.rows(); i++) {
                sizes.add((int) (stats.get(i,2)[0] * stats.get(i,3)[0]));
            }
            int min_size = 50;
            Mat img_final = new Mat(img_sub_array[ii].rows(), img_sub_array[ii].height(), CV_8U, Scalar.all(0));
            /*
            for (int i = 0; i < nb_components - 2; i++) {
                if (sizes.get(i) >= min_size) {
                    Core.compare(output, new Scalar(i + 2), img_final, Core.CMP_EQ);
                }
            }
            */

            Core.bitwise_not(img_final, img_final);
            final_img_array[ii] = img_final;
        }


        // Return
        return final_img_array;
    }
}
