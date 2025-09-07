package com.example.core.experemental.intersections;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyzeChartIntersectionsService {

    static {
        nu.pattern.OpenCV.loadShared();
        System.out.println("OpenCV loaded: " + Core.VERSION);
    }

    public Map<String, Object> analyze(String path, String mode, int distance) {
        Map<String, Object> response = new HashMap<>();

        Mat src = Imgcodecs.imread(path);
        if (src.empty()) {
            response.put("error", "Не удалось загрузить картинку: " + path);
            return response;
        }

        // --- HSV ---
        Mat hsv = new Mat();
        Imgproc.cvtColor(src, hsv, Imgproc.COLOR_BGR2HSV);

        // --- Маски ---
        Mat redMask1 = new Mat();
        Mat redMask2 = new Mat();
        Mat greenMask = new Mat();

        Core.inRange(hsv, new Scalar(0, 100, 50), new Scalar(10, 255, 255), redMask1);
        Core.inRange(hsv, new Scalar(160, 100, 50), new Scalar(179, 255, 255), redMask2);
        Mat redMask = new Mat();
        Core.addWeighted(redMask1, 1.0, redMask2, 1.0, 0.0, redMask);

        Core.inRange(hsv, new Scalar(40, 50, 50), new Scalar(90, 255, 255), greenMask);

        List<Point> intersections = new ArrayList<>();
        Mat result = src.clone();

        if ("mask".equalsIgnoreCase(mode)) {
            Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(3, 3));
            Imgproc.dilate(redMask, redMask, kernel);
            Imgproc.dilate(greenMask, greenMask, kernel);

            Mat intersectionMask = new Mat();
            Core.bitwise_and(redMask, greenMask, intersectionMask);
            intersections = getLinePoints(intersectionMask);

            for (Point p : intersections) {
                Imgproc.circle(result, p, 3, new Scalar(255, 0, 0), -1);
            }

        } else {
            List<Point> redPoints = getLinePoints(redMask);
            List<Point> greenPoints = getLinePoints(greenMask);

            if ("pixel".equalsIgnoreCase(mode)) {
                for (Point rp : redPoints) {
                    for (Point gp : greenPoints) {
                        if (Math.abs(rp.x - gp.x) <= 1 && Math.abs(rp.y - gp.y) <= 1) {
                            intersections.add(new Point((rp.x + gp.x) / 2, (rp.y + gp.y) / 2));
                        }
                    }
                }
            } else if ("distance".equalsIgnoreCase(mode)) {
                for (Point rp : redPoints) {
                    for (Point gp : greenPoints) {
                        double dx = rp.x - gp.x;
                        double dy = rp.y - gp.y;
                        if (Math.sqrt(dx * dx + dy * dy) <= distance) {
                            intersections.add(new Point((rp.x + gp.x) / 2, (rp.y + gp.y) / 2));
                        }
                    }
                }
            }

            for (Point p : redPoints) result.put((int) p.y, (int) p.x, new double[]{0, 0, 255});
            for (Point p : greenPoints) result.put((int) p.y, (int) p.x, new double[]{0, 255, 0});
            for (Point p : intersections) Imgproc.circle(result, p, 3, new Scalar(255, 0, 0), -1);
        }

        // --- Текст с количеством пересечений ---
        String text = "Intersections: " + intersections.size();
        int font = Imgproc.FONT_HERSHEY_SIMPLEX;
        double fontScale = 2.0; // очень крупный текст
        int thickness = 5;
        Imgproc.putText(
                result,
                text,
                new Point(50, 200), // координаты (слева сверху)
                font,
                fontScale,
                new Scalar(0, 165, 255), // ярко-жёлтый цвет
                thickness
        );

        String outPath = path.replace(".png", "_" + mode + "_out.png");
        Imgcodecs.imwrite(outPath, result);

        response.put("outPath", outPath);
        response.put("mode", mode);
        response.put("intersectionsCount", intersections.size());
        return response;
    }

    private List<Point> getLinePoints(Mat mask) {
        List<Point> points = new ArrayList<>();
        for (int y = 0; y < mask.rows(); y++) {
            for (int x = 0; x < mask.cols(); x++) {
                double[] val = mask.get(y, x);
                if (val != null && val[0] > 0) points.add(new Point(x, y));
            }
        }
        return points;
    }
}
