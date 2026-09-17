// 扁平垃圾桶图标 (桶身 + 桶盖与提手 + 内部竖向凹槽)
    public static Drawable createTrashIcon(Context context, int sizeDp, int color) {
        float density = context.getResources().getDisplayMetrics().density;
        int sizePx = (int) (sizeDp * density);
        Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(color);
        paint.setStrokeWidth(1.8f * density);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);

        float left = sizePx * 0.28f;
        float right = sizePx * 0.72f;
        float topLid = sizePx * 0.28f;
        float bottomBody = sizePx * 0.78f;
        float inset = sizePx * 0.05f;

        // 桶盖横线与顶端提手
        canvas.drawLine(left - 2 * density, topLid, right + 2 * density, topLid, paint);
        float handleW = sizePx * 0.22f;
        float half = sizePx / 2f;
        Path handle = new Path();
        handle.moveTo(half - handleW / 2f, topLid);
        handle.lineTo(half - handleW / 2f, topLid - 3.5f * density);
        handle.lineTo(half + handleW / 2f, topLid - 3.5f * density);
        handle.lineTo(half + handleW / 2f, topLid);
        canvas.drawPath(handle, paint);

        // 桶身外框 (微收窄梯形)
        Path body = new Path();
        body.moveTo(left, topLid + 2 * density);
        body.lineTo(left + inset, bottomBody);
        body.lineTo(right - inset, bottomBody);
        body.lineTo(right, topLid + 2 * density);
        canvas.drawPath(body, paint);

        // 桶身内壁 2 条竖向凹槽
        float innerOffset = sizePx * 0.08f;
        canvas.drawLine(half - innerOffset, topLid + 6 * density, half - innerOffset + inset * 0.4f, bottomBody - 4 * density, paint);
        canvas.drawLine(half + innerOffset, topLid + 6 * density, half + innerOffset - inset * 0.4f, bottomBody - 4 * density, paint);

        return new BitmapDrawable(context.getResources(), bitmap);
    }
