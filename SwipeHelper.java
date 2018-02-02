package com.myapp.test.utilities;

/**
 * Created by manolo.dantonio
 * on 01/12/2017.
 */

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.support.design.widget.Snackbar;
import android.support.transition.TransitionManager;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.util.Log;
import android.view.Display;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import com.myapp.test.Application;
import com.myapp.test.adapters.AgendaHeaderAdapter;
import com.myapp.test.adapters.EmptyAdapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

import static com.myapp.test.model.ConstantsKt.DURATION_DELETE;

public abstract class SwipeHelper extends ItemTouchHelper.SimpleCallback {

    private static final String TEXT_CANCEL = "Annulla";
    private static final String TEXT_MESSAGE = "Elemento rimosso";
    private static ArrayList<UnderlayButton> buttons;
    private static int swipedPos = -1;
    public int BUTTON_WIDTH = 350;
    private RecyclerView recyclerView;
    private GestureDetector gestureDetector;
    private float swipeThreshold = 0.5f;
    private Map<Integer, ArrayList<UnderlayButton>> buttonsBuffer;
    private Queue<Integer> recoverQueue;
    private boolean canDelete = false;

    private GestureDetector.SimpleOnGestureListener gestureListener = new GestureDetector.SimpleOnGestureListener() {
        @Override
        public boolean onDown(MotionEvent e) {
            return super.onDown(e);
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            for (UnderlayButton button : buttons) {
                if (button.onClick(e.getX(), e.getY()))
                    break;
            }

            return true;
        }
    };

    private View.OnTouchListener onTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent e) {
            // this "view" equals the touched recyclerView

            RecyclerView.Adapter adapter = recyclerView.getAdapter();
            // This will STOP touch features on this conditions
            if (adapter instanceof EmptyAdapter) return true;
            //////////////////

            if (swipedPos < 0) return false;
//            if (adapter instanceof AgendaHeaderAdapter) {
//                if (((AgendaHeaderAdapter) adapter).getDataSetWithHeaders().get(swipedPos).isHeader()) {
//                    return true;
//                }
//            }

            RecyclerView.ViewHolder swipedViewHolder = recyclerView.findViewHolderForAdapterPosition(swipedPos);
            if (swipedViewHolder == null) return false;
//            if (swipedViewHolder instanceof AgendaHeaderAdapter.HeaderViewHolder) {
//                return true;
//            }

            Point point = new Point((int) e.getRawX(), (int) e.getRawY());

            View swipedItem = swipedViewHolder.itemView;
            Rect rect = new Rect();
            swipedItem.getGlobalVisibleRect(rect);

            if (e.getAction() == MotionEvent.ACTION_DOWN || e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_MOVE) {
                if (rect.top < point.y && rect.bottom > point.y)
                    gestureDetector.onTouchEvent(e);
                else {
                    recoverQueue.add(swipedPos);
                    swipedPos = -1;
                    recoverSwipedItem();
                }
            }
            return false;
        }
    };

//    public SwipeHelper(RecyclerView recyclerView) {
//        this(recyclerView, null);
//        dataset = null;
//    }

    public SwipeHelper(RecyclerView recyclerView) {
        super(0, ItemTouchHelper.LEFT);
        if (recyclerView == null) return;

        this.recyclerView = recyclerView;
        buttons = new ArrayList<>();
        this.gestureDetector = new GestureDetector(recyclerView.getContext(), gestureListener);


        this.recyclerView.setOnTouchListener(onTouchListener);


        buttonsBuffer = new HashMap<>();
        recoverQueue = new LinkedList<Integer>() {
            @Override
            public boolean add(Integer o) {
                if (contains(o))
                    return false;
                else
                    return super.add(o);
            }
        };

        // Button width based on display size
        Point size = new Point();
        final Display display = recyclerView.getDisplay();
        if (display != null)
            display.getSize(size);
        BUTTON_WIDTH = size.x / 4;

        attachSwipe();
    }

    public static <ITEM> void showDeleteSnack(int position, RecyclerView recyclerView, ArrayList<ITEM> dataset, SnackbarDeleteActionListener snackBarOnDeleteListener) {
        snackBarOnDeleteListener.onDeleteStart();
        if (recyclerView.getAdapter() == null || dataset == null || dataset.isEmpty()) {
            Log.e("static showDeleteSnack", "This method requires a non-empty adapter and dataset");
            return;
        }

        final boolean[] canDelete = {true};
        final RecyclerView.Adapter populatedAdapter = recyclerView.getAdapter();
        final ITEM selectedItem = dataset.get(position);

        // call
        new Handler().postDelayed(
                () -> {
                    if (!canDelete[0]) return;
                    canDelete[0] = false;
                    snackBarOnDeleteListener.onDeleteAction(position, selectedItem);
                },
                DURATION_DELETE
        );

        // list
        ViewGroup parent = recyclerView.getParent() instanceof ViewGroup ? (ViewGroup) recyclerView.getParent() : recyclerView;
        TransitionManager.beginDelayedTransition(parent);
        recyclerView.getRecycledViewPool().clear();
        dataset.remove(position);
        if (dataset.size() > 0) {
            recyclerView.getAdapter().notifyDataSetChanged();
        } else {
            snackBarOnDeleteListener.onEmptyDataset();
        }


        // message
        Snackbar.make(parent, TEXT_MESSAGE, DURATION_DELETE)
                .setAction(TEXT_CANCEL, view -> {
                    // action "Cancel"
                    if (!canDelete[0]) return;
                    canDelete[0] = false;
                    TransitionManager.beginDelayedTransition(parent);
                    if (dataset.size() == 0) {
                        // resume original adapter when at least 1 row
                        recyclerView.setAdapter(populatedAdapter);
                    }

                    dataset.add(selectedItem);
                    recyclerView.getAdapter().notifyDataSetChanged();
                    snackBarOnDeleteListener.onResumeItem(position);
                })
                .show();
    }

    @Override
    public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
        return false;


    }

    @Override
    public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
        if (viewHolder instanceof AgendaHeaderAdapter.HeaderViewHolder) return;

        int pos = viewHolder.getAdapterPosition();

        if (swipedPos != pos)
            recoverQueue.add(swipedPos);

        swipedPos = pos;

        if (buttonsBuffer.containsKey(swipedPos))
            buttons = buttonsBuffer.get(swipedPos);
        else
            buttons.clear();

        buttonsBuffer.clear();
        swipeThreshold = 0.5f * buttons.size() * BUTTON_WIDTH;
        recoverSwipedItem();
    }

    @Override
    public float getSwipeThreshold(RecyclerView.ViewHolder viewHolder) {
        return swipeThreshold;
    }

    @Override
    public float getSwipeEscapeVelocity(float defaultValue) {
        return 0.1f * defaultValue;
    }

    @Override
    public float getSwipeVelocityThreshold(float defaultValue) {
        return 5.0f * defaultValue;
    }

    @Override
    public void onChildDraw(Canvas c, RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {

        // This will STOP touch features on this conditions
        if (viewHolder instanceof AgendaHeaderAdapter.HeaderViewHolder ||
                viewHolder instanceof AgendaHeaderAdapter.SpinnerViewHolder) return;
        /////////////

        int pos = viewHolder.getAdapterPosition();
        float translationX = dX;
        View itemView = viewHolder.itemView;

//        swipedPos = pos;
        if (pos < 0) {
            return;
        }

        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
            if (dX < 0) {
                ArrayList<UnderlayButton> buffer = new ArrayList<>();

                if (!buttonsBuffer.containsKey(pos)) {
                    instantiateUnderlayButton(viewHolder, buffer);
                    buttonsBuffer.put(pos, buffer);
                } else {
                    buffer = buttonsBuffer.get(pos);
                }

                translationX = dX * buffer.size() * BUTTON_WIDTH / itemView.getWidth();
                drawButtons(c, itemView, buffer, pos, translationX);
            }
        }

        super.onChildDraw(c, recyclerView, viewHolder, translationX, dY, actionState, isCurrentlyActive);
    }

    private synchronized void recoverSwipedItem() {
        while (!recoverQueue.isEmpty()) {
            int pos = recoverQueue.poll();
            if (pos > -1) {
                recyclerView.getAdapter().notifyItemChanged(pos);
            }
        }
    }

    private void drawButtons(Canvas c, View itemView, ArrayList<UnderlayButton> buffer, int pos, float dX) {
        float right = itemView.getRight();
        float dButtonWidth = (-1) * dX / buffer.size();

        for (UnderlayButton button : buffer) {
            float left = right - dButtonWidth;
            button.onDraw(
                    c,
                    new RectF(
                            left,
                            itemView.getTop(),
                            right,
                            itemView.getBottom()
                    ),
                    pos
            );

            right = left;
        }
    }

    public void attachSwipe() {
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(this);
        itemTouchHelper.attachToRecyclerView(recyclerView);
    }

    public abstract void instantiateUnderlayButton(RecyclerView.ViewHolder viewHolder, ArrayList<UnderlayButton> underlayButtons);

    public interface UnderlayButtonClickListener {
        void onClick(int pos);
    }

    public interface SnackbarDeleteActionListener<ITEM> {
        void onDeleteStart();

        void onDeleteAction(int position, ITEM deletedItem);

        void onResumeItem(int position);

        void onEmptyDataset();
    }

    public class UnderlayButton {
        private String text;
        private int imageResId;
        private int color;
        private int pos;
        private RectF clickRegion;
        private UnderlayButtonClickListener clickListener;

        public UnderlayButton(String text, int imageResId, int color, UnderlayButtonClickListener clickListener) {
            this.text = text;
            this.imageResId = imageResId;
            this.color = color;
            this.clickListener = clickListener;
        }


        public boolean onClick(float x, float y) {
            if (clickRegion != null && clickRegion.contains(x, y)) {
                if (clickListener != null) {
                    clickListener.onClick(pos);
                    return true;
                }
            }

            return false;
        }

        public void onDraw(Canvas canvas, RectF rect, int pos) {
            Paint paint = new Paint();

            // Draw background
            paint.setColor(color);
            canvas.drawRect(rect, paint);

            if (text != null && !text.isEmpty()) {
                drawText(canvas, rect, paint);
            } else if (imageResId > 0) {
                drawImage(canvas, rect, paint);
            }


            clickRegion = rect;
            this.pos = pos;
        }

        private void drawImage(Canvas canvas, RectF rect, Paint paint) {
            Drawable imageDrawable = ContextCompat.getDrawable(Application.getContext(), imageResId);
            if (imageDrawable == null) return;

            int rectWidthCenter = canvas.getWidth() - (int) (rect.width() / 2f);
            int rectHeightCenter = (int) (rect.bottom - (int) (rect.height() / 2f));
            int imgWidth = imageDrawable.getIntrinsicWidth() / 3;
            int imgHeight = imageDrawable.getIntrinsicHeight() / 3;
            imageDrawable.setBounds(
                    rectWidthCenter - imgWidth,
                    rectHeightCenter - imgHeight,
                    rectWidthCenter + imgWidth,
                    rectHeightCenter + imgHeight
            );


            imageDrawable.draw(canvas);

        }

        private void drawText(Canvas canvas, RectF rect, Paint paint) {
            // Draw Text
            paint.setColor(Color.WHITE);
            paint.setTextSize(40f);

            Rect newRect = new Rect();
            float rectHeight = rect.height();
            float rectWidth = rect.width();
            paint.setTextAlign(Paint.Align.LEFT);
            paint.getTextBounds(text, 0, text.length(), newRect);
            float x = rectWidth / 2f - newRect.width() / 2f - newRect.left;
            float y = rectHeight / 2f + newRect.height() / 2f - newRect.bottom;
            canvas.drawText(text, rect.left + x, rect.top + y, paint);
        }
    }

}
