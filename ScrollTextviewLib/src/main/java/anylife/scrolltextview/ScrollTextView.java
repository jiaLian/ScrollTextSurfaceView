package anylife.scrolltextview;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.FontMetrics;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff.Mode;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.FloatRange;
import androidx.annotation.IntRange;

import com.goodjia.ScrollListener;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Android auto Scroll Text,like TV News,AD devices
 * <p>
 * NEWEST LOG :
 * 1.setText() immediately take effect (v1.3.6)
 * 2.support scroll forever            (v1.3.7)
 * 3.support scroll text size         （v1.5.0)
 * <p>
 * <p>
 * Basic knowledge：https://www.jianshu.com/p/918fec73a24d
 *
 * @author anylife.zlb@gmail.com  2013/09/02
 */
public class ScrollTextView extends SurfaceView implements SurfaceHolder.Callback {
    private static final int MIN_SPEED = 1;
    private static final int MAX_SPEED = 30;
    private static final float MIN_TEXT_SIZE = 0.1f;
    private static final float MAX_TEXT_SIZE = Float.MAX_VALUE;
    private final String TAG = ScrollTextView.class.getSimpleName();
    // surface Handle onto a raw buffer that is being managed by the screen compositor.
    private SurfaceHolder surfaceHolder;   //providing access and control over this SurfaceView's underlying surface.

    private Paint paint = null;
    private boolean stopScroll = false;     // stop scroll
    private boolean pauseScroll = false;    // pause scroll

    //Default value
    private boolean clickEnable = false;    // click to stop/start
    public boolean isHorizontal = true;     // horizontal｜V
    @IntRange(from = MIN_SPEED, to = MAX_SPEED)
    private int speedDp = 3;                  // scroll-speed
    private int speedPx = dip2px(getContext(), speedDp);                  // scroll-speed
    private String text = "";               // scroll text
    private float letterSpacing = 0.2f;
    private float textPadding = dip2px(getContext(), 5);
    @FloatRange(from = MIN_TEXT_SIZE, to = MAX_TEXT_SIZE)
    private float textSize = sp2px(getContext(), 20f);           // default text size
    private int textColor;
    private int textBackColor = 0x00000000;

    protected int needScrollTimes = Integer.MAX_VALUE;        //scroll times
    private int scrollCount = 0;
    protected int scrollTimePeriod = Integer.MIN_VALUE;             //scroll period;unit:second
    private int viewWidth = 0;
    private int viewHeight = 0;
    private float textWidth = 0f;
    private float textX = 0f;
    private float textY = 0f;
    private float viewWidth_plus_textLength = 0.0f;

    private ScheduledExecutorService scheduledExecutorService;
    private ScheduledExecutorService scrollPeriodExecutorService;
    protected ScheduledFuture scrollPeriodScheduledFuture;
    private Set<ScrollListener> listeners = new HashSet();
    boolean isSetNewText = false;
    boolean isScrollForever = true;

    /**
     * constructs 1
     *
     * @param context you should know
     */
    public ScrollTextView(Context context) {
        super(context);
    }

    /**
     * constructs 2
     *
     * @param context CONTEXT
     * @param attrs   ATTRS
     */
    public ScrollTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        surfaceHolder = this.getHolder();  //get The surface holder
        surfaceHolder.addCallback(this);
        paint = new Paint();
        TypedArray arr = getContext().obtainStyledAttributes(attrs, R.styleable.ScrollTextView);
        clickEnable = arr.getBoolean(R.styleable.ScrollTextView_clickEnable, clickEnable);
        isHorizontal = arr.getBoolean(R.styleable.ScrollTextView_isHorizontal, isHorizontal);
        setSpeedDp(arr.getInteger(R.styleable.ScrollTextView_speed, speedDp));
        text = arr.getString(R.styleable.ScrollTextView_text);
        textColor = arr.getColor(R.styleable.ScrollTextView_text_color, Color.BLACK);
        textSize = arr.getDimension(R.styleable.ScrollTextView_text_size, textSize);
        needScrollTimes = arr.getInteger(R.styleable.ScrollTextView_times, Integer.MAX_VALUE);
        letterSpacing = arr.getFloat(R.styleable.ScrollTextView_letterSpacing, letterSpacing);
        textPadding = arr.getDimension(R.styleable.ScrollTextView_textPadding, textPadding);
        isScrollForever = arr.getBoolean(R.styleable.ScrollTextView_isScrollForever, true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            paint.setLetterSpacing(letterSpacing);
        }
        paint.setColor(textColor);
        paint.setTextSize(textSize);

        setZOrderOnTop(true);  //Control whether the surface view's surface is placed on top of its window.
        getHolder().setFormat(PixelFormat.TRANSLUCENT);

        setFocusable(true);
        arr.recycle();
    }

    /**
     * measure text height width
     *
     * @param widthMeasureSpec  widthMeasureSpec
     * @param heightMeasureSpec heightMeasureSpec
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // TODO Auto-generated method stub
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        viewWidth = MeasureSpec.getSize(widthMeasureSpec);
        viewHeight = getFontHeight(textSize);      //实际的视图高
        setMeasuredDimension(viewWidth, viewHeight);
    }


    /**
     * surfaceChanged
     *
     * @param arg0 arg0
     * @param arg1 arg1
     * @param arg2 arg1
     * @param arg3 arg1
     */
    @Override
    public void surfaceChanged(SurfaceHolder arg0, int arg1, int arg2, int arg3) {
    }

    /**
     * surfaceCreated,init a new scroll thread.
     * lockCanvas
     * Draw something
     * unlockCanvasAndPost
     *
     * @param holder holder
     */
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        stopScroll = false;
        scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        scheduledExecutorService.scheduleAtFixedRate(new ScrollTextThread(), 100, 100, TimeUnit.MILLISECONDS);
    }

    /**
     * surfaceDestroyed
     *
     * @param arg0 SurfaceHolder
     */
    @Override
    public void surfaceDestroyed(SurfaceHolder arg0) {
        stopScroll = true;
        scheduledExecutorService.shutdownNow();
        if (scrollPeriodExecutorService != null) {
            scrollPeriodExecutorService.shutdownNow();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        scheduledExecutorService.shutdownNow();
        if (scrollPeriodExecutorService != null) {
            scrollPeriodExecutorService.shutdownNow();
        }
        super.onDetachedFromWindow();
    }

    /**
     * text height
     *
     * @param fontSize fontSize
     * @return fontSize`s height
     */
    private int getFontHeight(float fontSize) {
        Paint paint = new Paint();
        paint.setTextSize(fontSize);
        FontMetrics fm = paint.getFontMetrics();
        return (int) (Math.ceil(fm.descent - fm.ascent) + textPadding * 2);
    }

    /**
     * get Background color
     *
     * @return textBackColor
     */
    public int getBackgroundColor() {
        return textBackColor;
    }


    /**
     * set background color
     *
     * @param color textBackColor
     */
    public void setScrollTextBackgroundColor(int color) {
        this.setBackgroundColor(color);
        this.textBackColor = color;
    }

    public void addScrollListener(ScrollListener listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    public void removeScrollListener(ScrollListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    /**
     * get speed
     *
     * @return speed
     */
    public int getSpeedDp() {
        return speedDp;
    }

    /**
     * get Text
     *
     * @return
     */
    public String getText() {
        return text;
    }

    /**
     * get text size
     *
     * @return sp
     */
    public float getTextSize() {
        return px2sp(getContext(), textSize);
    }


    /**
     * get text color
     *
     * @return textColor
     */
    public int getTextColor() {
        return textColor;
    }

    public float getLetterSpacing() {
        return letterSpacing;
    }

    public void setLetterSpacing(float letterSpacing) {
        this.letterSpacing = letterSpacing;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            paint.setLetterSpacing(letterSpacing);
            measureVarious();
            isSetNewText = true;
        }
    }

    /**
     * set scroll times
     *
     * @param times scroll times
     */
    public void setTimes(int times) {
        if (times <= 0) {
            throw new IllegalArgumentException("times was invalid integer, it must between > 0");
        } else {
            if (scrollPeriodScheduledFuture != null) {
                scrollPeriodScheduledFuture.cancel(true);
            }
            textX = 0;
            scrollCount = 0;
            scrollTimePeriod = Integer.MIN_VALUE;
            needScrollTimes = times;
            isScrollForever = false;
        }
    }

    public int getScrollTimePeriod() {
        return scrollTimePeriod;
    }

    public void setScrollTimePeriod(int scrollTimePeriod) {
        this.scrollTimePeriod = scrollTimePeriod;
        scheduleScrollPeriod();
    }

    public void scheduleScrollPeriod() {
        if (scrollPeriodScheduledFuture != null) {
            scrollPeriodScheduledFuture.cancel(true);
        }
        if (scrollPeriodExecutorService == null || scrollPeriodExecutorService.isShutdown()) {
            scrollPeriodExecutorService = Executors.newSingleThreadScheduledExecutor();
        }
        if (scrollTimePeriod > 0) {
            textX = 0;
            needScrollTimes = Integer.MAX_VALUE;
            scrollCount = 0;
            stopScroll = false;
            scrollPeriodScheduledFuture = scrollPeriodExecutorService.schedule(() -> {
                stopScroll = true;
                synchronized (listeners) {
                    for (ScrollListener listener : listeners) {
                        listener.onFinished();
                    }
                }
            }, scrollTimePeriod, TimeUnit.SECONDS);
        }
    }

    /**
     * set scroll text size sp
     *
     * @param textSizeSp
     */
    public void setTextSize(@FloatRange(from = MIN_TEXT_SIZE, to = MAX_TEXT_SIZE) float textSizeSp) {
        textSize = sp2px(getContext(), textSizeSp);
        //重新设置Size
        paint.setTextSize(textSize);

        //实际的视图高,thanks to WG
        viewHeight = getFontHeight(textSize);
        android.view.ViewGroup.LayoutParams lp = this.getLayoutParams();
        lp.width = viewWidth;
        lp.height = viewHeight;
        this.setLayoutParams(lp);

        //试图区域也要改变
        measureVarious();

        isSetNewText = true;
    }

    /**
     * dp to px
     *
     * @param context c
     * @param dpValue dp
     * @return
     */
    private int dip2px(Context context, float dpValue) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dpValue * scale + 0.5f);
    }

    /**
     * sp to px
     *
     * @param context c
     * @param spValue sp
     * @return
     */
    private int sp2px(Context context, float spValue) {
        float fontScale = context.getResources().getDisplayMetrics().scaledDensity;
        return (int) (spValue * fontScale + 0.5f);
    }

    public int px2sp(Context context, float pxValue) {
        float fontScale = context.getResources().getDisplayMetrics().scaledDensity;
        return (int) (pxValue / fontScale + 0.5f);
    }


    /**
     * isHorizontal or vertical
     *
     * @param horizontal isHorizontal or vertical
     */
    public void setHorizontal(boolean horizontal) {
        isHorizontal = horizontal;
    }

    /**
     * set scroll text
     *
     * @param newText scroll text
     */
    public void setText(String newText) {
        scrollCount = 0;
        isSetNewText = true;
        stopScroll = false;
        this.text = newText;
        measureVarious();
    }

    /**
     * Set the text color
     *
     * @param color A color value in the form 0xAARRGGBB.
     */
    public void setTextColor(@ColorInt int color) {
        textColor = color;
        paint.setColor(textColor);
    }

    /**
     * set scroll speed
     *
     * @param speedDp SCROLL SPEED
     */
    public void setSpeedDp(@IntRange(from = MIN_SPEED, to = MAX_SPEED) int speedDp) {
        this.speedDp = speedDp;
        speedPx = dip2px(getContext(), speedDp);
    }


    /**
     * scroll text forever
     *
     * @param scrollForever scroll forever or not
     */
    public void setScrollForever(boolean scrollForever) {
        isScrollForever = scrollForever;
    }


    /**
     * scroll text vertical
     */
    private void drawVerticalScroll() {
        if (text == null) return;
        List<String> strings = new ArrayList<>();
        int start = 0, end = 0;
        while (end < text.length()) {
            while (paint.measureText(text.substring(start, end)) < viewWidth && end < text.length()) {
                end++;
            }
            if (end == text.length()) {
                strings.add(text.substring(start, end));
                break;
            } else {
                end--;
                strings.add(text.substring(start, end));
                start = end;
            }
        }

        float fontHeight = paint.getFontMetrics().bottom - paint.getFontMetrics().top;

        FontMetrics fontMetrics = paint.getFontMetrics();
        float distance = (fontMetrics.bottom - fontMetrics.top) / 2 - fontMetrics.bottom;
        float baseLine = viewHeight / 2 + distance;

        for (int n = 0; n < strings.size(); n++) {
            for (float i = viewHeight + fontHeight; i > -fontHeight; i = i - 3) {
                if (stopScroll || isSetNewText) {
                    return;
                }

                if (pauseScroll) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Log.e(TAG, e.toString());
                    }
                    continue;
                }

                Canvas canvas = surfaceHolder.lockCanvas();
                canvas.drawColor(Color.TRANSPARENT, Mode.CLEAR);
                canvas.drawText(strings.get(n), 0, i, paint);
                surfaceHolder.unlockCanvasAndPost(canvas);

                if (i - baseLine < 4 && i - baseLine > 0) {
                    if (stopScroll) {
                        return;
                    }
                    try {
                        Thread.sleep(speedDp * 1000);
                    } catch (InterruptedException e) {
                        Log.e(TAG, e.toString());
                    }
                }
            }
        }
    }

    /**
     * Draw text
     *
     * @param X X
     * @param Y Y
     */
    private synchronized void draw(float X, float Y) {
        if (text == null) return;
        Canvas canvas = surfaceHolder.lockCanvas();
        canvas.drawColor(Color.TRANSPARENT, Mode.CLEAR);
        canvas.drawText(text, X, Y, paint);
        surfaceHolder.unlockCanvasAndPost(canvas);
    }


    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        this.setVisibility(visibility);
    }

    /**
     * measure text
     */
    private void measureVarious() {
        if (text == null) return;
        textWidth = paint.measureText(text);
        viewWidth_plus_textLength = viewWidth + textWidth;
        textX = 0;

        //baseline measure !
        FontMetrics fontMetrics = paint.getFontMetrics();
        float distance = (fontMetrics.bottom - fontMetrics.top) / 2 - fontMetrics.bottom;
        textY = viewHeight / 2 + distance;
    }


    /**
     * Scroll thread
     */
    class ScrollTextThread implements Runnable {
        @Override
        public void run() {
            measureVarious();
            while (!stopScroll && (text != null || !text.isEmpty())) {
                if (isHorizontal) {
                    if (pauseScroll) {
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            Log.e(TAG, e.toString());
                        }
                        continue;
                    }
                    draw(viewWidth - textX, textY);
                    textX += speedPx;
                    if (textX > viewWidth_plus_textLength) {
                        textX = 0;
                        --needScrollTimes;
                        scrollCount++;
                        synchronized (listeners) {
                            for (ScrollListener listener : listeners) {
                                listener.onLoopCompletion(scrollCount);
                            }
                        }
                    }
                } else {
                    drawVerticalScroll();
                    isSetNewText = false;
                    --needScrollTimes;
                    scrollCount++;
                    synchronized (listeners) {
                        for (ScrollListener listener : listeners) {
                            listener.onLoopCompletion(scrollCount);
                        }
                    }
                }
                if (needScrollTimes <= 0 && !isScrollForever) {
                    stopScroll = true;
                    synchronized (listeners) {
                        for (ScrollListener listener : listeners) {
                            listener.onFinished();
                        }
                    }
                }

            }
        }
    }

}
