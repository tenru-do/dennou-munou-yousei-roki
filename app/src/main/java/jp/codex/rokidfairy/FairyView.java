package jp.codex.rokidfairy;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

public class FairyView extends View {
    public enum Action {
        ASCEND, DESCEND, LEFT, RIGHT, WAVE, DOWNWARD, KNEE_HUG, SLEEP, DANCE
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Bitmap[] sprites = new Bitmap[Action.values().length];

    private long actionStartedAt;
    private Action action = Action.WAVE;

    public FairyView(Context context) {
        super(context);
        setWillNotDraw(false);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
        sprites[Action.ASCEND.ordinal()] = BitmapFactory.decodeResource(getResources(), R.drawable.fairy_ascend);
        sprites[Action.DESCEND.ordinal()] = BitmapFactory.decodeResource(getResources(), R.drawable.fairy_descend);
        sprites[Action.LEFT.ordinal()] = BitmapFactory.decodeResource(getResources(), R.drawable.fairy_left);
        sprites[Action.RIGHT.ordinal()] = BitmapFactory.decodeResource(getResources(), R.drawable.fairy_right);
        sprites[Action.WAVE.ordinal()] = BitmapFactory.decodeResource(getResources(), R.drawable.fairy_wave);
        sprites[Action.DOWNWARD.ordinal()] = BitmapFactory.decodeResource(getResources(), R.drawable.fairy_downward);
        sprites[Action.KNEE_HUG.ordinal()] = BitmapFactory.decodeResource(getResources(), R.drawable.fairy_knee_hug);
        sprites[Action.SLEEP.ordinal()] = BitmapFactory.decodeResource(getResources(), R.drawable.fairy_sleep);
        sprites[Action.DANCE.ordinal()] = BitmapFactory.decodeResource(getResources(), R.drawable.fairy_dance);
    }

    public void setAction(Action action, long actionStartedAt) {
        this.action = action;
        this.actionStartedAt = actionStartedAt;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        Bitmap bitmap = sprites[action.ordinal()];
        if (bitmap == null) {
            return;
        }

        long now = android.os.SystemClock.uptimeMillis();
        float wingPulse = isMoving(action)
                ? (float) Math.sin((now - actionStartedAt) / 75.0) * 3.5f
                : 0f;
        float bob = isMoving(action)
                ? (float) Math.sin((now - actionStartedAt) / 420.0) * 2f
                : 0f;

        float maxWidth = getWidth() * 0.92f + wingPulse;
        float maxHeight = getHeight() * 0.92f + wingPulse;
        float scale = Math.min(maxWidth / bitmap.getWidth(), maxHeight / bitmap.getHeight());
        float drawWidth = bitmap.getWidth() * scale;
        float drawHeight = bitmap.getHeight() * scale;
        float left = (getWidth() - drawWidth) / 2f;
        float top = (getHeight() - drawHeight) / 2f + bob;

        paint.setAlpha(255);
        canvas.drawBitmap(bitmap, null, new RectF(left, top, left + drawWidth, top + drawHeight), paint);
    }

    private boolean isMoving(Action value) {
        return value == Action.ASCEND
                || value == Action.DESCEND
                || value == Action.LEFT
                || value == Action.RIGHT
                || value == Action.DOWNWARD
                || value == Action.DANCE;
    }
}
