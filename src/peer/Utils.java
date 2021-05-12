package peer;

public class Utils {
    public static String progressBar(long current, long total) {
        int size = 40;

        int percentage = (int) ((current / (double) total) * size);

        String bar = "|" + "=".repeat(Math.max(0, percentage)) +
                " ".repeat(Math.max(0, size - percentage)) +
                "|";
        return bar;
    }

    public static String prettySize(double bytes) {
        String type = "B";
        if (bytes / 1024 > 1) {
            bytes /= 1024.0;
            type = "KB";
        }
        if (bytes / 1024 > 1) {
            bytes /= 1024.0;
            type = "MB";
        }
        if (bytes / 1024 > 1) {
            bytes /= 1024.0;
            type = "GB";
        }

        return String.format("%.2f %s", bytes, type);
    }

    public static String rate(long start, long current, long bytes) {
        double delta = (current - start) / 1000.0;
        double rate = (double) bytes / delta;

        return prettySize(rate) + "/s";
    }
}
