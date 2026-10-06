package com.yulinlin.data.core.session;

/** Display-level highlighting settings shared by search-capable sessions. */
public class HighlightProperties {
    private String startTag = "__HL_START__";
    private String endTag = "__HL_END__";
    private int maxFragments = 2;
    private String fragmentDelimiter = "...";

    public String getStartTag() {
        return startTag;
    }

    public void setStartTag(String startTag) {
        this.startTag = validText(startTag, "startTag", false);
    }

    public String getEndTag() {
        return endTag;
    }

    public void setEndTag(String endTag) {
        this.endTag = validText(endTag, "endTag", false);
    }

    public int getMaxFragments() {
        return maxFragments;
    }

    public void setMaxFragments(int maxFragments) {
        if (maxFragments < 1) throw new IllegalArgumentException("highlight.maxFragments must be positive");
        this.maxFragments = maxFragments;
    }

    public String getFragmentDelimiter() {
        return fragmentDelimiter;
    }

    public void setFragmentDelimiter(String fragmentDelimiter) {
        this.fragmentDelimiter = validText(fragmentDelimiter, "fragmentDelimiter", true);
    }

    private static String validText(String value, String name, boolean allowEmpty) {
        if (value == null || (!allowEmpty && value.isEmpty()) || value.length() > 256
                || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("highlight." + name + " must be "
                    + (allowEmpty ? "a" : "a non-empty")
                    + " text value up to 256 characters without control characters");
        }
        return value;
    }
}
