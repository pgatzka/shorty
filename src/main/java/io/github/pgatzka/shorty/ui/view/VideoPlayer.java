package io.github.pgatzka.shorty.ui.view;

import com.vaadin.flow.component.HtmlComponent;
import com.vaadin.flow.component.Tag;

/** A plain HTML5 video element. */
@Tag("video")
public class VideoPlayer extends HtmlComponent {

    private static final long serialVersionUID = 1L;

    public VideoPlayer() {
        getElement().setAttribute("controls", true);
        getElement().setAttribute("playsinline", true);
        getStyle().set("max-height", "70vh").set("aspect-ratio", "9 / 16").set("background", "black");
    }

    public void setSource(String url) {
        getElement().setAttribute("src", url);
    }

    public String getSource() {
        return getElement().getAttribute("src");
    }
}
