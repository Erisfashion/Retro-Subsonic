package com.retro.subsonic;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

public class TonearmView extends View {

    private Paint paint;
    private Path path;

    public TonearmView(Context context) {
        super(context);
        init();
    }

    public TonearmView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TonearmView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        path = new Path();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float density = getResources().getDisplayMetrics().density;

        // 1. 顶部白色基座轴心 (小圆轮盘)
        float pivotX = w * 0.50f;
        float pivotY = 16f * density;

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFD8D8DC);
        canvas.drawCircle(pivotX, pivotY, 8f * density, paint);

        paint.setColor(0xFFFFFFFF);
        canvas.drawCircle(pivotX, pivotY, 5.5f * density, paint);

        paint.setColor(0xFF26282E);
        canvas.drawCircle(pivotX, pivotY, 2.5f * density, paint);

        // 2. 弯曲流畅的白色金属唱臂 (从轴心向下向右延伸，优美弧度弯向黑胶音轨)
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(0xFFFFFFFF);
        paint.setStrokeWidth(3.0f * density);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);

        path.reset();
        path.moveTo(pivotX, pivotY);

        float elbowX = w * 0.68f;
        float elbowY = h * 0.35f;

        float targetX = w * 0.72f;
        float targetY = h * 0.46f;

        path.cubicTo(pivotX + 2f * density, pivotY + 26f * density,
                     elbowX + 4f * density, elbowY - 16f * density,
                     elbowX, elbowY);

        path.lineTo(targetX, targetY);
        canvas.drawPath(path, paint);

        // 3. 唱针拾音唱头 (斜搭在黑胶唱片音轨上的白色精致方块)
        canvas.save();
        canvas.translate(targetX, targetY);
        canvas.rotate(-32f);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFFFFFFF);
        canvas.drawRect(-4f * density, -2.5f * density, 13f * density, 4f * density, paint);

        // 唱头中间黑色细纹细节
        paint.setColor(0xFF1E2026);
        canvas.drawRect(2f * density, -2.5f * density, 4.5f * density, 4f * density, paint);

        canvas.restore();
    }
}
