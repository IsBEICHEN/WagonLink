package com.example.livelink;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

final class UiIcons {
    private UiIcons() {
    }

    static Drawable plus(int size, int color) {
        return new PlusIconDrawable(size, color);
    }

    static Drawable close(int size, int color) {
        return new CloseIconDrawable(size, color);
    }

    static Drawable fullscreen(boolean exit, int size, int color) {
        return new FullscreenIconDrawable(exit, size, color);
    }

    static Drawable lock(boolean locked, int size, int color) {
        return new LockIconDrawable(locked, size, color);
    }

    private abstract static class StrokeIconDrawable extends Drawable {
        final int size;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        StrokeIconDrawable(int size, int color, float strokeScale, Paint.Cap cap, Paint.Join join) {
            this.size = size;
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(cap);
            paint.setStrokeJoin(join);
            paint.setStrokeWidth(Math.max(2f, size * strokeScale));
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(android.graphics.ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        @Override
        public int getIntrinsicWidth() {
            return size;
        }

        @Override
        public int getIntrinsicHeight() {
            return size;
        }
    }

    private static final class PlusIconDrawable extends StrokeIconDrawable {
        PlusIconDrawable(int size, int color) {
            super(size, color, 0.12f, Paint.Cap.ROUND, Paint.Join.ROUND);
        }

        @Override
        public void draw(Canvas canvas) {
            RectF bounds = new RectF(getBounds());
            float unit = Math.min(bounds.width(), bounds.height());
            float centerX = bounds.centerX();
            float centerY = bounds.centerY();
            float arm = unit * 0.26f;
            canvas.drawLine(centerX - arm, centerY, centerX + arm, centerY, paint);
            canvas.drawLine(centerX, centerY - arm, centerX, centerY + arm, paint);
        }
    }

    private static final class CloseIconDrawable extends StrokeIconDrawable {
        CloseIconDrawable(int size, int color) {
            super(size, color, 0.11f, Paint.Cap.ROUND, Paint.Join.ROUND);
        }

        @Override
        public void draw(Canvas canvas) {
            RectF bounds = new RectF(getBounds());
            float unit = Math.min(bounds.width(), bounds.height());
            float left = bounds.left + (bounds.width() - unit) / 2f;
            float top = bounds.top + (bounds.height() - unit) / 2f;
            canvas.drawLine(left + unit * 0.28f, top + unit * 0.28f,
                    left + unit * 0.72f, top + unit * 0.72f, paint);
            canvas.drawLine(left + unit * 0.72f, top + unit * 0.28f,
                    left + unit * 0.28f, top + unit * 0.72f, paint);
        }
    }

    private static final class FullscreenIconDrawable extends StrokeIconDrawable {
        private final boolean exit;

        FullscreenIconDrawable(boolean exit, int size, int color) {
            super(size, color, 0.09f, Paint.Cap.SQUARE, Paint.Join.MITER);
            this.exit = exit;
        }

        @Override
        public void draw(Canvas canvas) {
            RectF bounds = new RectF(getBounds());
            float unit = Math.min(bounds.width(), bounds.height());
            float left = bounds.left + (bounds.width() - unit) / 2f;
            float top = bounds.top + (bounds.height() - unit) / 2f;

            if (exit) {
                drawArrow(canvas, left + unit * 0.18f, top + unit * 0.18f,
                        left + unit * 0.43f, top + unit * 0.43f,
                        left + unit * 0.43f, top + unit * 0.28f,
                        left + unit * 0.28f, top + unit * 0.43f);
                drawArrow(canvas, left + unit * 0.82f, top + unit * 0.82f,
                        left + unit * 0.57f, top + unit * 0.57f,
                        left + unit * 0.57f, top + unit * 0.72f,
                        left + unit * 0.72f, top + unit * 0.57f);
            } else {
                drawArrow(canvas, left + unit * 0.43f, top + unit * 0.43f,
                        left + unit * 0.18f, top + unit * 0.18f,
                        left + unit * 0.18f, top + unit * 0.34f,
                        left + unit * 0.34f, top + unit * 0.18f);
                drawArrow(canvas, left + unit * 0.57f, top + unit * 0.57f,
                        left + unit * 0.82f, top + unit * 0.82f,
                        left + unit * 0.82f, top + unit * 0.66f,
                        left + unit * 0.66f, top + unit * 0.82f);
            }
        }

        private void drawArrow(Canvas canvas, float startX, float startY, float endX, float endY,
                               float headX1, float headY1, float headX2, float headY2) {
            canvas.drawLine(startX, startY, endX, endY, paint);
            canvas.drawLine(endX, endY, headX1, headY1, paint);
            canvas.drawLine(endX, endY, headX2, headY2, paint);
        }
    }

    private static final class LockIconDrawable extends StrokeIconDrawable {
        private final boolean locked;
        private final Path shacklePath = new Path();
        private final RectF body = new RectF();

        LockIconDrawable(boolean locked, int size, int color) {
            super(size, color, 0.075f, Paint.Cap.ROUND, Paint.Join.ROUND);
            this.locked = locked;
        }

        @Override
        public void draw(Canvas canvas) {
            RectF bounds = new RectF(getBounds());
            float unit = Math.min(bounds.width(), bounds.height());
            float centerX = bounds.centerX();
            float centerY = bounds.centerY();

            body.set(centerX - unit * 0.25f, centerY - unit * 0.02f,
                    centerX + unit * 0.25f, centerY + unit * 0.30f);
            canvas.drawRoundRect(body, unit * 0.055f, unit * 0.055f, paint);

            shacklePath.reset();
            if (locked) {
                shacklePath.moveTo(centerX - unit * 0.18f, centerY - unit * 0.02f);
                shacklePath.lineTo(centerX - unit * 0.18f, centerY - unit * 0.20f);
                shacklePath.quadTo(centerX - unit * 0.18f, centerY - unit * 0.40f, centerX, centerY - unit * 0.40f);
                shacklePath.quadTo(centerX + unit * 0.18f, centerY - unit * 0.40f, centerX + unit * 0.18f, centerY - unit * 0.20f);
                shacklePath.lineTo(centerX + unit * 0.18f, centerY - unit * 0.02f);
            } else {
                shacklePath.moveTo(centerX - unit * 0.18f, centerY - unit * 0.02f);
                shacklePath.lineTo(centerX - unit * 0.18f, centerY - unit * 0.20f);
                shacklePath.quadTo(centerX - unit * 0.15f, centerY - unit * 0.39f, centerX + unit * 0.07f, centerY - unit * 0.37f);
                shacklePath.lineTo(centerX + unit * 0.22f, centerY - unit * 0.27f);
            }
            canvas.drawPath(shacklePath, paint);
            canvas.drawLine(centerX, centerY + unit * 0.10f, centerX, centerY + unit * 0.20f, paint);
        }
    }
}
