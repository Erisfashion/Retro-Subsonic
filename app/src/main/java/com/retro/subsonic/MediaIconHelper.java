package com.retro.subsonic;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

public class MediaIconHelper {

    private static int dpToPx(Context context, float dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    public static Drawable getPlayDrawable(Context context, int color, int sizeDp) {
        int size = dpToPx(context, sizeDp);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);

        Path path = new Path();
        float left = size * 0.32f;
        float right = size * 0.74f;
        float top = size * 0.22f;
        float bottom = size * 0.78f;
        float midY = size * 0.50f;

        path.moveTo(left, top);
        path.lineTo(right, midY);
        path.lineTo(left, bottom);
        path.close();

        canvas.drawPath(path, paint);
        return new BitmapDrawable(context.getResources(), bitmap);
    }

    public static Drawable getPauseDrawable(Context context, int color, int sizeDp) {
        int size = dpToPx(context, sizeDp);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);

        float barWidth = size * 0.16f;
        float barHeight = size * 0.52f;
        float top = (size - barHeight) / 2f;
        float bar1Left = size * 0.28f;
        float bar2Left = size * 0.56f;

        canvas.drawRect(bar1Left, top, bar1Left + barWidth, top + barHeight, paint);
        canvas.drawRect(bar2Left, top, bar2Left + barWidth, top + barHeight, paint);

        return new BitmapDrawable(context.getResources(), bitmap);
    }

    public static Drawable getNextDrawable(Context context, int color, int sizeDp) {
        int size = dpToPx(context, sizeDp);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);

        Path path = new Path();
        path.moveTo(size * 0.25f, size * 0.25f);
        path.lineTo(size * 0.65f, size * 0.50f);
        path.lineTo(size * 0.25f, size * 0.75f);
        path.close();
        canvas.drawPath(path, paint);

        float barWidth = size * 0.10f;
        canvas.drawRect(size * 0.68f, size * 0.25f, size * 0.68f + barWidth, size * 0.75f, paint);

        return new BitmapDrawable(context.getResources(), bitmap);
    }

    public static Drawable getPrevDrawable(Context context, int color, int sizeDp) {
        int size = dpToPx(context, sizeDp);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);

        Path path = new Path();
        path.moveTo(size * 0.75f, size * 0.25f);
        path.lineTo(size * 0.35f, size * 0.50f);
        path.lineTo(size * 0.75f, size * 0.75f);
        path.close();
        canvas.drawPath(path, paint);

        float barWidth = size * 0.10f;
        canvas.drawRect(size * 0.22f, size * 0.25f, size * 0.22f + barWidth, size * 0.75f, paint);

        return new BitmapDrawable(context.getResources(), bitmap);
    }

    public static Drawable getQueueDrawable(Context context, int color, int sizeDp) {
        int size = dpToPx(context, sizeDp);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dpToPx(context, 2));
        paint.setStrokeCap(Paint.Cap.ROUND);

        float left = size * 0.22f;
        float right = size * 0.78f;

        canvas.drawLine(left, size * 0.32f, right, size * 0.32f, paint);
        canvas.drawLine(left, size * 0.50f, right, size * 0.50f, paint);
        canvas.drawLine(left, size * 0.68f, right, size * 0.68f, paint);

        return new BitmapDrawable(context.getResources(), bitmap);
    }

    public static Drawable getBackDrawable(Context context, int color, int sizeDp) {
        int size = dpToPx(context, sizeDp);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dpToPx(context, 2.5f));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);

        Path path = new Path();
        path.moveTo(size * 0.60f, size * 0.25f);
        path.lineTo(size * 0.35f, size * 0.50f);
        path.lineTo(size * 0.60f, size * 0.75f);

        canvas.drawPath(path, paint);
        return new BitmapDrawable(context.getResources(), bitmap);
    }

    public static Drawable getFavoriteDrawable(Context context, boolean isFav, int sizeDp) {
        int size = dpToPx(context, sizeDp);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(isFav ? Color.parseColor("#E53935") : Color.parseColor("#B0B0B0"));
        paint.setStyle(isFav ? Paint.Style.FILL : Paint.Style.STROKE);
        paint.setStrokeWidth(dpToPx(context, 2));

        Path path = new Path();
        float width = size * 0.7f;
        float height = size * 0.7f;
        float startX = size * 0.5f;
        float startY = size * 0.35f;

        path.moveTo(startX, startY);
        path.cubicTo(startX, size * 0.20f, size * 0.20f, size * 0.20f, size * 0.20f, size * 0.45f);
        path.cubicTo(size * 0.20f, size * 0.65f, startX, size * 0.85f, startX, size * 0.85f);
        path.cubicTo(startX, size * 0.85f, size * 0.80f, size * 0.65f, size * 0.80f, size * 0.45f);
        path.cubicTo(size * 0.80f, size * 0.20f, startX, size * 0.20f, startX, startY);
        path.close();

        canvas.drawPath(path, paint);
        return new BitmapDrawable(context.getResources(), bitmap);
    }
}
