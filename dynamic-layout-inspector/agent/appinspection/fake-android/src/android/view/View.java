/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.view;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.HardwareRenderer;
import android.graphics.Matrix;
import android.graphics.Picture;
import android.graphics.Point;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;

// Normally this class is abstract, but for tests, making it concrete allows us to set up
// layout trees more easily.
@SuppressWarnings({"MethodMayBeStatic", "FieldCanBeLocal", "unused"})
public class View {

    public static final int VISIBLE = 0x0;
    public static final int INVISIBLE = 0x4;
    public static final int GONE = 0x8;

    public static final class AttachInfo {
        @SuppressWarnings("unused") // Accessed by reflection
        private final Handler mHandler = new Handler(Looper.getMainLooper());

        private final ThreadedRenderer mThreadedRenderer = new ThreadedRenderer();

        @VisibleForTesting
        public void forcePictureCapture(Picture picture) {
            HardwareRenderer.PictureCapturedCallback callback =
                    mThreadedRenderer.getPictureCaptureCallback();

            if (callback != null) {
                callback.onPictureCaptured(picture);
            }
        }

        @VisibleForTesting
        public ThreadedRenderer getRenderer() {
            return mThreadedRenderer;
        }
    }

    public interface OnCreateContextMenuListener {
        void onCreateContextMenu(
                ContextMenu contextMenu, View view, ContextMenu.ContextMenuInfo contextMenuInfo);
    }

    private final Context mContext;
    private final int mId;

    private int mLeft = 0;
    private int mTop = 0;
    private int mWidth = 0;
    private int mHeight = 0;
    private int mScrollX = 0;
    private int mScrollY = 0;
    private ViewGroup.LayoutParams mLayoutParams = new ViewGroup.LayoutParams();
    @Nullable private ViewTreeObserver mViewTreeObserver = null;

    private ViewRootImpl mViewRootImpl;

    @VisibleForTesting public final Point locationInSurface = new Point();

    @VisibleForTesting public final Point locationOnScreen = new Point();

    @VisibleForTesting public Consumer<Canvas> drawHandler = canvas -> {};

    @VisibleForTesting Picture mPictureCapture;

    /** If set, used to fake what is normally more complex Matrix math */
    private float[] mTransformedPoints = null;

    @Nullable AttachInfo mAttachInfo = null;

    public View(Context context) {
        mContext = context;
        mId = mContext.generateViewId();
    }

    public Context getContext() {
        return mContext;
    }

    public int getId() {
        return mId;
    }

    public long getUniqueDrawingId() {
        // uniqueDrawingId is distinct from id in production, but for testing purposes, relating
        // them is fine. That said, we transform the value to make sure we don't allow them to be
        // used interchangeably by accident.
        return (long) mId * 2L;
    }

    public int getVisibility() {
        return VISIBLE;
    }

    public boolean isAttachedToWindow() {
        return true;
    }

    private boolean myHardwareAccelerated = true;

    public boolean isHardwareAccelerated() {
        return myHardwareAccelerated;
    }

    @VisibleForTesting
    public void setHardwareAccelerated(boolean on) {
        myHardwareAccelerated = on;
    }

    public float getZ() {
        return 0f;
    }

    public int[] getDrawableState() {
        return new int[0];
    }

    public Resources getResources() {
        return mContext.getResources();
    }

    public int getSourceLayoutResId() {
        return 0;
    }

    public Map<Integer, Integer> getAttributeSourceResourceMap() {
        return Collections.emptyMap();
    }

    public int getLeft() {
        return mLeft;
    }

    public int getTop() {
        return mTop;
    }

    public int getWidth() {
        return mWidth;
    }

    public int getHeight() {
        return mHeight;
    }

    public int getScrollX() {
        return mScrollX;
    }

    public int getScrollY() {
        return mScrollY;
    }

    public void setLeft(int left) {
        mLeft = left;
    }

    public void setTop(int top) {
        mTop = top;
    }

    public void setWidth(int width) {
        mWidth = width;
    }

    public void setHeight(int height) {
        mHeight = height;
    }

    public void setScrollX(int scrollX) {
        mScrollX = scrollX;
    }

    public void setScrollY(int scrollY) {
        mScrollY = scrollY;
    }

    public ViewGroup.LayoutParams getLayoutParams() {
        return mLayoutParams;
    }

    public void setLayoutParams(ViewGroup.LayoutParams params) {
        mLayoutParams = params;
    }

    @VisibleForTesting
    public void setTransformedPoints(float[] pts) {
        if (pts != null && pts.length != 8) {
            throw new IllegalArgumentException();
        }
        mTransformedPoints = pts;
    }

    @VisibleForTesting
    public void setAttachInfo(AttachInfo attachInfo) {
        mAttachInfo = attachInfo;
    }

    public void getLocationInSurface(int[] location) {
        location[0] = locationInSurface.x;
        location[1] = locationInSurface.y;
    }

    public void getLocationOnScreen(int[] location) {
        location[0] = locationOnScreen.x;
        location[1] = locationOnScreen.y;
    }

    public int[] getAttributeResolutionStack(int attributeId) {
        return new int[0];
    }

    private int mInvalidateCount = 0;

    public void invalidate() {
        mInvalidateCount++;
        if (mPictureCapture != null) {
            // If a capture picture is specified in a test,
            // notify the callback (delayed) on the UI thread.
            // This would allow a test to avoid calling forcePictureCapture explicitly.
            Handler handler = new Handler(Looper.getMainLooper());
            handler.post(() -> forcePictureCapture(mPictureCapture));
        }
    }

    public void postInvalidate() {
        invalidate();
    }

    @VisibleForTesting
    public int getInvalidateCount() {
        return mInvalidateCount;
    }

    public void draw(Canvas canvas) {
        drawHandler.accept(canvas);
    }

    public void transformMatrixToGlobal(Matrix matrix) {
        matrix.transformedPoints = mTransformedPoints;
    }

    public ViewTreeObserver getViewTreeObserver() {
        if (mViewTreeObserver == null) {
            mViewTreeObserver = new ViewTreeObserver();
        }
        return mViewTreeObserver;
    }

    @VisibleForTesting
    public void setViewRootImpl(ViewRootImpl viewRootImpl) {
        mViewRootImpl = viewRootImpl;
    }

    /**
     * Gets the view root associated with the View.
     *
     * @return The view root, or null if none.
     */
    @Nullable
    public ViewRootImpl getViewRootImpl() {
        return mViewRootImpl;
    }

    // Only works with views where setAttachInfo was called on them
    public void forcePictureCapture(Picture picture) {
        if (mAttachInfo != null) {
            mAttachInfo.forcePictureCapture(picture);
        }
        if (mViewTreeObserver != null) {
            mViewTreeObserver.fireFrameCommit();
        }
    }

    // In tests this can be used to specify the picture that will be intercepted with a
    // HardwareRenderer callback when the View root is invalidated.
    @VisibleForTesting
    public void setPictureCapture(Picture picture) {
        mPictureCapture = picture;
    }

    @VisibleForTesting
    public Picture getPictureCapture() {
        return mPictureCapture;
    }
}
