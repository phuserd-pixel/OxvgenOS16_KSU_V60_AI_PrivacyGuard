package com.oxguard.offlineplayer;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.VideoView;

public class AdaptiveVideoView extends VideoView {
    public static final int MODE_FIT = 0;
    public static final int MODE_CROP = 1;
    public static final int MODE_STRETCH = 2;
    public static final int MODE_ORIGINAL = 3;

    private int videoWidth;
    private int videoHeight;
    private int resizeMode = MODE_FIT;

    public AdaptiveVideoView(Context context) {
        super(context);
    }

    public AdaptiveVideoView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AdaptiveVideoView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setVideoSize(int width, int height) {
        if (width > 0 && height > 0) {
            videoWidth = width;
            videoHeight = height;
            requestLayout();
        }
    }

    public void setResizeMode(int mode) {
        resizeMode = mode;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int parentWidth = MeasureSpec.getSize(widthMeasureSpec);
        int parentHeight = MeasureSpec.getSize(heightMeasureSpec);

        if (parentWidth <= 0 || parentHeight <= 0 || videoWidth <= 0 || videoHeight <= 0) {
            setMeasuredDimension(parentWidth, parentHeight);
            return;
        }

        if (resizeMode == MODE_STRETCH) {
            setMeasuredDimension(parentWidth, parentHeight);
            return;
        }

        float videoAspect = videoWidth / (float) videoHeight;
        float parentAspect = parentWidth / (float) parentHeight;
        int measuredWidth = parentWidth;
        int measuredHeight = parentHeight;

        if (resizeMode == MODE_CROP) {
            if (parentAspect > videoAspect) {
                measuredHeight = Math.round(parentWidth / videoAspect);
            } else {
                measuredWidth = Math.round(parentHeight * videoAspect);
            }
        } else if (resizeMode == MODE_ORIGINAL) {
            float scale = Math.min(1.0f, Math.min(parentWidth / (float) videoWidth, parentHeight / (float) videoHeight));
            measuredWidth = Math.max(1, Math.round(videoWidth * scale));
            measuredHeight = Math.max(1, Math.round(videoHeight * scale));
        } else {
            if (parentAspect > videoAspect) {
                measuredWidth = Math.round(parentHeight * videoAspect);
            } else {
                measuredHeight = Math.round(parentWidth / videoAspect);
            }
        }

        setMeasuredDimension(measuredWidth, measuredHeight);
    }
}
