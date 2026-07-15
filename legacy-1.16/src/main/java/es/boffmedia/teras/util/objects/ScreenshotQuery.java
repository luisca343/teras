package es.boffmedia.teras.util.objects;

/**
 * Data class for screenshot query parameters
 */
public class ScreenshotQuery {
    private String query;
    private boolean includeUI;
    private String format;
    private int quality;

    public ScreenshotQuery() {
        this.includeUI = true;
        this.format = "png";
        this.quality = 90;
    }

    public ScreenshotQuery(String query, boolean includeUI, String format, int quality) {
        this.query = query;
        this.includeUI = includeUI;
        this.format = format;
        this.quality = quality;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public boolean isIncludeUI() {
        return includeUI;
    }

    public void setIncludeUI(boolean includeUI) {
        this.includeUI = includeUI;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public int getQuality() {
        return quality;
    }

    public void setQuality(int quality) {
        this.quality = quality;
    }
}
