package com.barcode.util;
import static java.lang.Math.max;
import static java.lang.Math.min;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

public class GraphicOverlay extends View {
    private final Object lock = new Object();
    private final List<Graphic> graphics = new ArrayList<>();
    // Matrix for transforming from image coordinates to overlay view coordinates.
    private final Matrix transformationMatrix = new Matrix();
  
    private int imageWidth;
    private int imageHeight;
    // The factor of overlay View size to image size. Anything in the image coordinates need to be
    // scaled by this amount to fit with the area of overlay View.
    private float scaleFactor = 1.0f;
    // The number of horizontal pixels needed to be cropped on each side to fit the image with the
    // area of overlay View after scaling.
    private float postScaleWidthOffset;
    // The number of vertical pixels needed to be cropped on each side to fit the image with the
    // area of overlay View after scaling.
    private float postScaleHeightOffset;
    private boolean isImageFlipped;
    private boolean needUpdateTransformation = true;


    public abstract static class Graphic {
        private GraphicOverlay overlay;
    
        public Graphic(GraphicOverlay overlay) {
          this.overlay = overlay;
        }
        public abstract void draw(Canvas canvas);
        protected void drawRect(
            Canvas canvas, float left, float top, float right, float bottom, Paint paint) {
            canvas.drawRect(left, top, right, bottom, paint);
        }
        protected void drawText(Canvas canvas, String text, float x, float y, Paint paint) {
            canvas.drawText(text, x, y, paint);
        }
        public float scale(float imagePixel) {
            return imagePixel * overlay.scaleFactor;
        }
        public Context getApplicationContext() {
            return overlay.getContext().getApplicationContext();
        }
        public boolean isImageFlipped() {
            return overlay.isImageFlipped;
        }
        public float translateX(float x) {
            if (overlay.isImageFlipped) {
              return overlay.getWidth() - (scale(x) - overlay.postScaleWidthOffset);
            } else {
              return scale(x) - overlay.postScaleWidthOffset;
            }
        }
        public float translateY(float y) {
            return scale(y) - overlay.postScaleHeightOffset;
        }

        public Matrix getTransformationMatrix() {
            return overlay.transformationMatrix;
        }

        public void postInvalidate() {
            overlay.postInvalidate();
        }
        public void updatePaintColorByZValue(
            Paint paint,
            Canvas canvas,
            boolean visualizeZ,
            boolean rescaleZForVisualization,
            float zInImagePixel,
            float zMin,
            float zMax) {
          if (!visualizeZ) {
            return;
          }
    
          // When visualizeZ is true, sets up the paint to different colors based on z values.
          // Gets the range of z value.
          float zLowerBoundInScreenPixel;
          float zUpperBoundInScreenPixel;
    
          if (rescaleZForVisualization) {
            zLowerBoundInScreenPixel = min(-0.001f, scale(zMin));
            zUpperBoundInScreenPixel = max(0.001f, scale(zMax));
          } else {
            // By default, assume the range of z value in screen pixel is [-canvasWidth, canvasWidth].
            float defaultRangeFactor = 1f;
            zLowerBoundInScreenPixel = -defaultRangeFactor * canvas.getWidth();
            zUpperBoundInScreenPixel = defaultRangeFactor * canvas.getWidth();
          }
    
          float zInScreenPixel = scale(zInImagePixel);
    
          if (zInScreenPixel < 0) {
            // Sets up the paint to be red if the item is in front of the z origin.
            // Maps values within [zLowerBoundInScreenPixel, 0) to [255, 0) and use it to control the
            // color. The larger the value is, the more red it will be.
            int v = (int) (zInScreenPixel / zLowerBoundInScreenPixel * 255);
            v = Ints.constrainToRange(v, 0, 255);
            paint.setARGB(255, 255, 255 - v, 255 - v);
          } else {
            // Sets up the paint to be blue if the item is behind the z origin.
            // Maps values within [0, zUpperBoundInScreenPixel] to [0, 255] and use it to control the
            // color. The larger the value is, the more blue it will be.
            int v = (int) (zInScreenPixel / zUpperBoundInScreenPixel * 255);
            v = Ints.constrainToRange(v, 0, 255);
            paint.setARGB(255, 255 - v, 255 - v, 255);
          }
        }
    }
    public GraphicOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
        addOnLayoutChangeListener(
            (view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                needUpdateTransformation = true);
    }

    public void clear() {
        synchronized (lock) {
          graphics.clear();
        }
        postInvalidate();
    }

    public void add(Graphic graphic) {
        synchronized (lock) {
          graphics.add(graphic);
        }
    }

    public void remove(Graphic graphic) {
        synchronized (lock) {
          graphics.remove(graphic);
        }
        postInvalidate();
    }
    public void setImageSourceInfo(int imageWidth, int imageHeight, boolean isFlipped) {
        synchronized (lock) {
          this.imageWidth = imageWidth;
          this.imageHeight = imageHeight;
          this.isImageFlipped = isFlipped;
          needUpdateTransformation = true;
        }
        postInvalidate();
    }

    public int getImageWidth() {
        return imageWidth;
    }
    
    public int getImageHeight() {
        return imageHeight;
    }

    private void updateTransformationIfNeeded() {
        if (!needUpdateTransformation || imageWidth <= 0 || imageHeight <= 0) {
          return;
        }
        float viewAspectRatio = (float) getWidth() / getHeight();
        float imageAspectRatio = (float) imageWidth / imageHeight;
        postScaleWidthOffset = 0;
        postScaleHeightOffset = 0;
        if (viewAspectRatio > imageAspectRatio) {
          // The image needs to be vertically cropped to be displayed in this view.
          scaleFactor = (float) getWidth() / imageWidth;
          postScaleHeightOffset = ((float) getWidth() / imageAspectRatio - getHeight()) / 2;
        } else {
          // The image needs to be horizontally cropped to be displayed in this view.
          scaleFactor = (float) getHeight() / imageHeight;
          postScaleWidthOffset = ((float) getHeight() * imageAspectRatio - getWidth()) / 2;
        }
    
        transformationMatrix.reset();
        transformationMatrix.setScale(scaleFactor, scaleFactor);
        transformationMatrix.postTranslate(-postScaleWidthOffset, -postScaleHeightOffset);
    
        if (isImageFlipped) {
          transformationMatrix.postScale(-1f, 1f, getWidth() / 2f, getHeight() / 2f);
        }
    
        needUpdateTransformation = false;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        synchronized (lock) {
            updateTransformationIfNeeded();
            for (Graphic graphic : graphics) {
                graphic.draw(canvas);
            }
        }
    }
}