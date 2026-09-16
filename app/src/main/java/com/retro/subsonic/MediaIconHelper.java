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
        path.lineTo(right, bottom);
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
        paint.setStrokeWidth(2.2f * density);
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
        paint.setStrokeWidth(2.2f * density);
        paint.setStrokeCap(Paint.Cap.ROUND);

        float half = sizePx / 2f;
        float r = sizePx * 0.28f;

        RectF oval = new RectF(half - r, half - r, half + r, half + r);
        canvas.drawArc(oval, -60, 300, false, paint);
        canvas.drawLine(half, half - r * 1.15f, half, half - 1.5f * density, paint);

        return new BitmapDrawable(context.getResources(), bitmap);
    }

    // 列表循环图标 (双循环轨道箭头)
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

        float l = sizePx * 0.26f;
        float r = sizePx * 0.74f;
        float t = sizePx * 0.32f;
        float b = sizePx * 0.68f;
        float cr = sizePx * 0.12f;

        // 上半轨道 (从左下拐角向上、向右)
        Path p1 = new Path();
        p1.moveTo(l, (t + b) / 2f);
        p1.lineTo(l, t + cr);
        p1.quadTo(l, t, l + cr, t);
        p1.lineTo(r - 2 * density, t);
        canvas.drawPath(p1, stroke);

        // 上半右侧向右箭头
        Path a1 = new Path();
        a1.moveTo(r + 3 * density, t);
        a1.lineTo(r - 3 * density, t - 3.5f * density);
        a1.lineTo(r - 3 * density, t + 3.5f * density);
        a1.close();
        canvas.drawPath(a1, fill);

        // 下半轨道 (从右上拐角向下、向左)
        Path p2 = new Path();
        p2.moveTo(r, (t + b) / 2f);
        p2.lineTo(r, b - cr);
        p2.quadTo(r, b, r - cr, b);
        p2.lineTo(l + 2 * density, b);
        canvas.drawPath(p2, stroke);

        // 下半左侧向左箭头
        Path a2 = new Path();
        a2.moveTo(l - 3 * density, b);
        a2.lineTo(l + 3 * density, b - 3.5f * density);
        a2.lineTo(l + 3 * density, b + 3.5f * density);
        a2.close();
        canvas.drawPath(a2, fill);

        return new BitmapDrawable(context.getResources(), bitmap);
    }

    // 单曲循环图标 (双循环轨道 + 中心数字 1)
    public static Drawable createRepeatOneIcon(Context context, int sizeDp, int color) {
        float density = context.getResources().getDisplayMetrics().density;
        int sizePx = (int) (sizeDp * density);
        Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        // 绘制基础双循环轨道
        Drawable base = createRepeatIcon(context, sizeDp, color);
        base.setBounds(0, 0, sizePx, sizePx);
        base.draw(canvas);

        // 中心精细绘制数字 1
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(color);
        paint.setStrokeWidth(1.6f * density);
        paint.setStrokeCap(Paint.Cap.ROUND);

        float cx = sizePx / 2f;
        float cy = sizePx / 2f;

        Path path1 = new Path();
        path1.moveTo(cx - 2.5f * density, cy - 1.5f * density);
        path1.lineTo(cx, cy - 4.5f * density);
        path1.lineTo(cx, cy + 4.5f * density);
        canvas.drawPath(path1, paint);

        canvas.drawLine(cx - 3.0f * density, cy + 4.5f * density, cx + 3.0f * density, cy + 4.5f * density, paint);

        return new BitmapDrawable(context.getResources(), bitmap);
    }

    // 随机播放图标 (交叉重叠箭头)
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

        float x1 = sizePx * 0.22f;
        float x2 = sizePx * 0.38f;
        float x3 = sizePx * 0.62f;
        float x4 = sizePx * 0.74f;

        float yTop = sizePx * 0.36f;
        float yBottom = sizePx * 0.64f;

        // 路径 1: 从左上平滑穿插至右下
        Path p1 = new Path();
        p1.moveTo(x1, yTop);
        p1.lineTo(x2, yTop);
        p1.cubicTo(sizePx * 0.5f, yTop, sizePx * 0.5f, yBottom, x3, yBottom);
        p1.lineTo(x4, yBottom);
        canvas.drawPath(p1, stroke);

        // 箭头 1: 右下箭头
        Path a1 = new Path();
        a1.moveTo(x4 + 4 * density, yBottom);
        a1.lineTo(x4 - 2 * density, yBottom - 3.5f * density);
        a1.lineTo(x4 - 2 * density, yBottom + 3.5f * density);
        a1.close();
        canvas.drawPath(a1, fill);

        // 路径 2: 从左下至中间留白后至右上
        Path p2Left = new Path();
        p2Left.moveTo(x1, yBottom);
        p2Left.lineTo(x2, yBottom);
        p2Left.lineTo(sizePx * 0.44f, sizePx * 0.58f);
        canvas.drawPath(p2Left, stroke);

        Path p2Right = new Path();
        p2Right.moveTo(sizePx * 0.56f, sizePx * 0.42f);
        p2Right.lineTo(x3, yTop);
        p2Right.lineTo(x4, yTop);
        canvas.drawPath(p2Right, stroke);

        // 箭头 2: 右上箭头
        Path a2 = new Path();
        a2.moveTo(x4 + 4 * density, yTop);
        a2.lineTo(x4 - 2 * density, yTop - 3.5f * density);
        a2.lineTo(x4 - 2 * density, yTop + 3.5f * density);
        a2.close();
        canvas.drawPath(a2, fill);

        return new BitmapDrawable(context.getResources(), bitmap);
    }
}
