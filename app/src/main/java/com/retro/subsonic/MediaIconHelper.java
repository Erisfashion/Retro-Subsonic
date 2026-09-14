package com.retro.subsonic;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

public class MediaIconHelper {

    // 播放三角图标 (带视错觉微距校正)
    public static Drawable createPlayIcon(Context context, int sizeDp, int color) {
        float density = context.getResources().getDisplayMetrics().density;
        int sizePx = (int) (sizeDp * density);
        Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);

        float offset = 1.5f * density;
        float half = sizePx / 2f;
        float triW = sizePx * 0.44f;
        float triH = sizePx * 0.52f;

        float left = half - triW / 2f + offset;
        float right = half + triW / 2f + offset;
        float top = half - triH / 2f;
        float bottom = half + triH / 2f;

        Path path = new Path();
        path.moveTo(left, top);
        path.lineTo(right, half);
        path.lineTo(left, bottom);
        path.close();

        canvas.drawPath(path, paint);
        return new BitmapDrawable(context.getResources(), bitmap);
    }

    // 暂停双柱图标 (圆角光条)
    public static Drawable createPauseIcon(Context context, int sizeDp, int color) {
        float density = context.getResources().getDisplayMetrics().density;
        int sizePx = (int) (sizeDp * density);
        Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);

        float half = sizePx / 2f;
        float barW = 3.5f * density;
        float barH = sizePx * 0.48f;
        float gap = 4.5f * density;

        float top = half - barH / 2f;
        float bottom = half + barH / 2f;
        float r = 1.5f * density;

        // 左柱
        RectF leftBar = new RectF(half - gap / 2f - barW, top, half - gap / 2f, bottom);
        canvas.drawRoundRect(leftBar, r, r, paint);

        // 右柱
        RectF rightBar = new RectF(half + gap / 2f, top, half + gap / 2f + barW, bottom);
        canvas.drawRoundRect(rightBar, r, r, paint);

        return new BitmapDrawable(context.getResources(), bitmap);
    }

    // 上一首图标 (竖线 + 向左三角)
    public static Drawable createPreviousIcon(Context context, int sizeDp, int color) {
        float density = context.getResources().getDisplayMetrics().density;
        int sizePx = (int) (sizeDp * density);
        Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);

        float half = sizePx / 2f;
        float totalW = sizePx * 0.46f;
        float totalH = sizePx * 0.46f;

        float left = half - totalW / 2f;
        float right = half + totalW / 2f;
        float top = half - totalH / 2f;
        float bottom = half + totalH / 2f;

        float barW = 2.5f * density;
        float r = 1.0f * density;

        // 左端竖线
        RectF bar = new RectF(left, top, left + barW, bottom);
        canvas.drawRoundRect(bar, r, r, paint);

        // 向左三角
        Path path = new Path();
        float triLeft = left + barW + 1.5f * density;
        path.moveTo(right, top);
        path.lineTo(triLeft, half);
        path.lineTo(right, bottom);
        path.close();

        canvas.drawPath(path, paint);
        return new BitmapDrawable(context.getResources(), bitmap);
    }

    // 下一首图标 (向右三角 + 竖线)
    public static Drawable createNextIcon(Context context, int sizeDp, int color) {
        float density = context.getResources().getDisplayMetrics().density;
        int sizePx = (int) (sizeDp * density);
        Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);

        float half = sizePx / 2f;
        float totalW = sizePx * 0.46f;
        float totalH = sizePx * 0.46f;

        float left = half - totalW / 2f;
        float right = half + totalW / 2f;
        float top = half - totalH / 2f;
        float bottom = half + totalH / 2f;

        float barW = 2.5f * density;
        float r = 1.0f * density;

        // 右端竖线
        RectF bar = new RectF(right - barW, top, right, bottom);
        canvas.drawRoundRect(bar, r, r, paint);

        // 向右三角
        Path path = new Path();
        float triRight = right - barW - 1.5f * density;
        path.moveTo(left, top);
        path.lineTo(triRight, half);
        path.lineTo(left, bottom);
        path.close();

        canvas.drawPath(path, paint);
        return new BitmapDrawable(context.getResources(), bitmap);
    }
}
