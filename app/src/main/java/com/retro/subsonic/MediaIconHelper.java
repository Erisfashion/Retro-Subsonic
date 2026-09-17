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

        RectF leftBar = new RectF(half - gap / 2f - barW, top, half - gap / 2f, bottom);
        canvas.drawRoundRect(leftBar, r, r, paint);

        RectF rightBar = new RectF(half + gap / 2f, top, half + gap / 2f + barW, bottom);
        canvas.drawRoundRect(rightBar, r, r, paint);

        return new BitmapDrawable(context.getResources(), bitmap);
    }

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

        RectF bar = new RectF(left, top, left + barW, bottom);
        canvas.drawRoundRect(bar, r, r, paint);

        Path path = new Path();
        float triLeft = left + barW + 1.5f * density;
        path.moveTo(right, top);
        path.lineTo(triLeft, half);
        path.lineTo(right, bottom);
        path.close();

        canvas.drawPath(path, paint);
        return new BitmapDrawable(context.getResources(), bitmap);
    }

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

        RectF bar = new RectF(right - barW, top, right, bottom);
        canvas.drawRoundRect(bar, r, r, paint);

        Path path = new Path();
        float triRight = right - barW - 1.5f * density;
        path.moveTo(left, top);
        path.lineTo(triRight, half);
        path.lineTo(left, bottom);
        path.close();

        canvas.drawPath(path, paint);
        return new BitmapDrawable(context.getResources(), bitmap);
    }

    public static Drawable createSearchIcon(Context context, int sizeDp, int color) {
        float density = context.getResources().getDisplayMetrics().density;
        int sizePx = (int) (sizeDp * density);
        Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(color);
        paint.setStrokeWidth(2.0f * density);
        paint.setStrokeCap(Paint.Cap.ROUND);

        float half = sizePx / 2f;
        float offset = 1.6f * density;
        float radius = sizePx * 0.22f;

        float cx = half - offset;
        float cy = half - offset;
        canvas.drawCircle(cx, cy, radius, paint);

        float angle = (float) Math.toRadians(45);
        float startX = cx + (float) (radius * Math.cos(angle));
        float startY = cy + (float) (radius * Math.sin(angle));
        float handleLen = sizePx * 0.22f;
        float endX = startX + (float) (handleLen * Math.cos(angle));
        float endY = startY + (float) (handleLen * Math.sin(angle));

        canvas.drawLine(startX, startY, endX, endY, paint);
        return new BitmapDrawable(context.getResources(), bitmap);
    }

    public static Drawable createPowerIcon(Context context, int sizeDp, int color) {
        float density = context.getResources().getDisplayMetrics().density;
        int sizePx = (int) (sizeDp * density);
        Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(color);
        paint.setStrokeWidth(2.0f * density);
        paint.setStrokeCap(Paint.Cap.ROUND);

        float half = sizePx / 2f;
        float r = sizePx * 0.28f;

        RectF oval = new RectF(half - r, half - r, half + r, half + r);
        canvas.drawArc(oval, -60, 300, false, paint);
        canvas.drawLine(half, half - r * 1.15f, half, half - 1.5f * density, paint);

        return new BitmapDrawable(context.getResources(), bitmap);
    }

    // 现代设置齿轮图标
    public static Drawable createSettingsIcon(Context context, int sizeDp, int color) {
        float density = context.getResources().getDisplayMetrics().density;
        int sizePx = (int) (sizeDp * density);
        Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setColor(color);
        stroke.setStrokeWidth(2.0f * density);
        stroke.setStrokeCap(Paint.Cap.ROUND);

        float cx = sizePx / 2f;
        float cy = sizePx / 2f;
        float rInner = sizePx * 0.16f;
        float rOuter = sizePx * 0.32f;

        canvas.drawCircle(cx, cy, rInner, stroke);

        for (int i = 0; i < 6; i++) {
            double angle = Math.toRadians(i * 60);
            float x1 = cx + (float) ((rInner + 2 * density) * Math.cos(angle));
            float y1 = cy + (float) ((rInner + 2 * density) * Math.sin(angle));
            float x2 = cx + (float) (rOuter * Math.cos(angle));
            float y2 = cy + (float) (rOuter * Math.sin(angle));
            canvas.drawLine(x1, y1, x2, y2, stroke);
        }
        return new BitmapDrawable(context.getResources(), bitmap);
    }

    // 主题切换图标（太阳 / 月亮）
    public static Drawable createThemeIcon(Context context, int sizeDp, int color, boolean isDark) {
        float density = context.getResources().getDisplayMetrics().density;
        int sizePx = (int) (sizeDp * density);
        Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);

        float cx = sizePx / 2f;
        float cy = sizePx / 2f;

        if (isDark) {
            // 月牙图标 (表示切到浅色/亮色)
            Path moon = new Path();
            float r = sizePx * 0.32f;
            moon.addCircle(cx, cy, r, Path.Direction.CW);
            Path cutout = new Path();
            cutout.addCircle(cx - 3.5f * density, cy - 3.5f * density, r, Path.Direction.CW);
            moon.op(cutout, Path.Op.DIFFERENCE);
            canvas.drawPath(moon, paint);
        } else {
            // 太阳图标 (表示切到深色)
            float r = sizePx * 0.20f;
            canvas.drawCircle(cx, cy, r, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.8f * density);
            for (int i = 0; i < 8; i++) {
                double a = Math.toRadians(i * 45);
                float x1 = cx + (float) ((r + 2.5f * density) * Math.cos(a));
                float y1 = cy + (float) ((r + 2.5f * density) * Math.sin(a));
                float x2 = cx + (float) ((r + 6.0f * density) * Math.cos(a));
                float y2 = cy + (float) ((r + 6.0f * density) * Math.sin(a));
                canvas.drawLine(x1, y1, x2, y2, paint);
            }
        }
        return new BitmapDrawable(context.getResources(), bitmap);
    }

    // 垃圾桶清空图标
    public static Drawable createTrashIcon(Context context, int sizeDp, int color) {
        float density = context.getResources().getDisplayMetrics().density;
        int sizePx = (int) (sizeDp * density);
        Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setColor(color);
        stroke.setStrokeWidth(1.8f * density);
        stroke.setStrokeCap(Paint.Cap.ROUND);

        float cx = sizePx / 2f;
        float top = sizePx * 0.25f;
        float bottom = sizePx * 0.75f;
        float w = sizePx * 0.35f;

        // 顶盖与把手
        canvas.drawLine(cx - w, top, cx + w, top, stroke);
        canvas.drawLine(cx - w * 0.4f, top - 3 * density, cx + w * 0.4f, top - 3 * density, stroke);

        // 桶身
        Path body = new Path();
        body.moveTo(cx - w * 0.85f, top);
        body.lineTo(cx - w * 0.70f, bottom);
        body.lineTo(cx + w * 0.70f, bottom);
        body.lineTo(cx + w * 0.85f, top);
        canvas.drawPath(body, stroke);

        // 桶内竖条纹
        canvas.drawLine(cx, top + 4 * density, cx, bottom - 3 * density, stroke);
        return new BitmapDrawable(context.getResources(), bitmap);
    }

    // 歌单栏竖三点菜单图标
    public static Drawable createMoreVertIcon(Context context, int sizeDp, int color) {
        float density = context.getResources().getDisplayMetrics().density;
        int sizePx = (int) (sizeDp * density);
        Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);

        float cx = sizePx / 2f;
        float cy = sizePx / 2f;
        float r = 2.2f * density;
        float offset = 6.5f * density;

        canvas.drawCircle(cx, cy - offset, r, paint);
        canvas.drawCircle(cx, cy, r, paint);
        canvas.drawCircle(cx, cy + offset, r, paint);

        return new BitmapDrawable(context.getResources(), bitmap);
    }

    public static Drawable createRepeatIcon(Context context, int sizeDp, int color) {
        float density = context.getResources().getDisplayMetrics().density;
        int sizePx = (int) (sizeDp * density);
        Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setColor(color);
        stroke.setStrokeWidth(1.8f * density);
        stroke.setStrokeCap(Paint.Cap.ROUND);

        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(color);

        float l = sizePx * 0.28f;
        float r = sizePx * 0.72f;
        float t = sizePx * 0.28f;
        float b = sizePx * 0.72f;
        float cr = sizePx * 0.12f;

        float ahW = sizePx * 0.12f;
        float ahH = sizePx * 0.12f;

        Path p1 = new Path();
        p1.moveTo(l, sizePx * 0.56f);
        p1.lineTo(l, t + cr);
        p1.quadTo(l, t, l + cr, t);
        p1.lineTo(r - sizePx * 0.04f, t);
        canvas.drawPath(p1, stroke);

        float tipX1 = r + sizePx * 0.04f;
        Path a1 = new Path();
        a1.moveTo(tipX1, t);
        a1.lineTo(tipX1 - ahW, t - ahH);
        a1.lineTo(tipX1 - ahW, t + ahH);
        a1.close();
        canvas.drawPath(a1, fill);

        Path p2 = new Path();
        p2.moveTo(r, sizePx * 0.44f);
        p2.lineTo(r, b - cr);
        p2.quadTo(r, b, r - cr, b);
        p2.lineTo(l + sizePx * 0.04f, b);
        canvas.drawPath(p2, stroke);

        float tipX2 = l - sizePx * 0.04f;
        Path a2 = new Path();
        a2.moveTo(tipX2, b);
        a2.lineTo(tipX2 + ahW, b - ahH);
        a2.lineTo(tipX2 + ahW, b + ahH);
        a2.close();
        canvas.drawPath(a2, fill);

        return new BitmapDrawable(context.getResources(), bitmap);
    }

    public static Drawable createRepeatOneIcon(Context context, int sizeDp, int color) {
        float density = context.getResources().getDisplayMetrics().density;
        int sizePx = (int) (sizeDp * density);
        Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Drawable base = createRepeatIcon(context, sizeDp, color);
        base.setBounds(0, 0, sizePx, sizePx);
        base.draw(canvas);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(color);
        paint.setStrokeWidth(1.6f * density);
        paint.setStrokeCap(Paint.Cap.ROUND);

        float cx = sizePx / 2f;
        float cy = sizePx / 2f;

        Path path1 = new Path();
        path1.moveTo(cx - 2.5f * density, cy - 2.0f * density);
        path1.lineTo(cx, cy - 4.5f * density);
        path1.lineTo(cx, cy + 4.5f * density);
        canvas.drawPath(path1, paint);

        canvas.drawLine(cx - 3.0f * density, cy + 4.5f * density, cx + 3.0f * density, cy + 4.5f * density, paint);

        return new BitmapDrawable(context.getResources(), bitmap);
    }

    public static Drawable createShuffleIcon(Context context, int sizeDp, int color) {
        float density = context.getResources().getDisplayMetrics().density;
        int sizePx = (int) (sizeDp * density);
        Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setColor(color);
        stroke.setStrokeWidth(1.8f * density);
        stroke.setStrokeCap(Paint.Cap.ROUND);

        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(color);

        float x1 = sizePx * 0.24f;
        float x2 = sizePx * 0.38f;
        float x3 = sizePx * 0.62f;
        float x4 = sizePx * 0.70f;

        float yt = sizePx * 0.36f;
        float yb = sizePx * 0.64f;

        float ahW = sizePx * 0.11f;
        float ahH = sizePx * 0.11f;

        canvas.drawLine(x1, yt, x2, yt, stroke);
        canvas.drawLine(x1, yb, x2, yb, stroke);

        Path p1 = new Path();
        p1.moveTo(x2, yt);
        p1.cubicTo(sizePx * 0.5f, yt, sizePx * 0.5f, yb, x3, yb);
        p1.lineTo(x4, yb);
        canvas.drawPath(p1, stroke);

        Path p2a = new Path();
        p2a.moveTo(x2, yb);
        p2a.cubicTo(sizePx * 0.42f, yb, sizePx * 0.46f, sizePx * 0.55f, sizePx * 0.46f, sizePx * 0.55f);
        canvas.drawPath(p2a, stroke);

        Path p2b = new Path();
        p2b.moveTo(sizePx * 0.54f, sizePx * 0.45f);
        p2b.cubicTo(sizePx * 0.54f, sizePx * 0.45f, sizePx * 0.58f, yt, x3, yt);
        p2b.lineTo(x4, yt);
        canvas.drawPath(p2b, stroke);

        float tipX1 = x4 + sizePx * 0.08f;
        Path a1 = new Path();
        a1.moveTo(tipX1, yt);
        a1.lineTo(tipX1 - ahW, yt - ahH);
        a1.lineTo(tipX1 - ahW, yt + ahH);
        a1.close();
        canvas.drawPath(a1, fill);

        float tipX2 = x4 + sizePx * 0.08f;
        Path a2 = new Path();
        a2.moveTo(tipX2, yb);
        a2.lineTo(tipX2 - ahW, yb - ahH);
        a2.lineTo(tipX2 - ahW, yb + ahH);
        a2.close();
        canvas.drawPath(a2, fill);

        return new BitmapDrawable(context.getResources(), bitmap);
    }

    public static Drawable createEqualizerIcon(Context context, int sizeDp, int color) {
        float density = context.getResources().getDisplayMetrics().density;
        int sizePx = (int) (sizeDp * density);
        Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setColor(color);
        stroke.setStrokeWidth(2.0f * density);
        stroke.setStrokeCap(Paint.Cap.ROUND);

        float y1 = sizePx * 0.35f;
        float y2 = sizePx * 0.65f;
        float xLeft = sizePx * 0.22f;
        float xRight = sizePx * 0.78f;

        float r = sizePx * 0.09f;
        float cx1 = sizePx * 0.61f;
        float cx2 = sizePx * 0.39f;

        canvas.drawLine(xLeft, y1, cx1 - r, y1, stroke);
        canvas.drawCircle(cx1, y1, r, stroke);
        canvas.drawLine(cx1 + r, y1, xRight, y1, stroke);

        canvas.drawLine(xLeft, y2, cx2 - r, y2, stroke);
        canvas.drawCircle(cx2, y2, r, stroke);
        canvas.drawLine(cx2 + r, y2, xRight, y2, stroke);

        return new BitmapDrawable(context.getResources(), bitmap);
    }
}
