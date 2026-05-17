package com.winlator.cmod.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

public class FlowLayout extends ViewGroup {

    private boolean vertical = true;

    public FlowLayout(Context context) {
        super(context);
    }

    public FlowLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public FlowLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setVertical(boolean vertical) {
        if (this.vertical != vertical) {
            this.vertical = vertical;
            requestLayout();
        }
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

    @Override
    protected LayoutParams generateLayoutParams(LayoutParams p) {
        return new MarginLayoutParams(p);
    }

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new MarginLayoutParams(getContext(), attrs);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int paddingH = getPaddingLeft() + getPaddingRight();
        int paddingV = getPaddingTop() + getPaddingBottom();
        int specWidth = MeasureSpec.getSize(widthMeasureSpec);
        int specMode = MeasureSpec.getMode(widthMeasureSpec);
        int displayWidth = getContext().getResources().getDisplayMetrics().widthPixels;
        int maxWidth;
        if (vertical) {
            if ((specMode == MeasureSpec.EXACTLY || specMode == MeasureSpec.AT_MOST) && specWidth > 0) {
                maxWidth = specWidth - paddingH;
            } else {
                maxWidth = displayWidth - paddingH;
            }
        } else {
            // Flow mode: always wrap by full screen width so items don't break
            // prematurely when parent passes a stale/narrow spec.
            int basis = (specMode == MeasureSpec.EXACTLY && specWidth > 0)
                    ? Math.max(specWidth, displayWidth) : displayWidth;
            maxWidth = basis - paddingH;
        }

        int rowWidth = 0, rowHeight = 0, totalHeight = 0, maxRowWidth = 0;
        int prevRightMargin = 0;

        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
            MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
            int childW = child.getMeasuredWidth();
            int totalH = child.getMeasuredHeight() + lp.topMargin + lp.bottomMargin;

            if (vertical) {
                totalHeight += totalH;
                maxRowWidth = Math.max(maxRowWidth, childW + lp.leftMargin + lp.rightMargin);
            } else {
                int gap = (rowWidth == 0) ? lp.leftMargin : (prevRightMargin + lp.leftMargin);
                if (rowWidth > 0 && rowWidth + gap + childW > maxWidth) {
                    totalHeight += rowHeight;
                    maxRowWidth = Math.max(maxRowWidth, rowWidth);
                    rowWidth = lp.leftMargin + childW;
                    rowHeight = totalH;
                } else {
                    rowWidth += gap + childW;
                    rowHeight = Math.max(rowHeight, totalH);
                }
                maxRowWidth = Math.max(maxRowWidth, rowWidth);
                prevRightMargin = lp.rightMargin;
            }
        }
        totalHeight += rowHeight;

        int measuredWidth = maxRowWidth + paddingH;
        setMeasuredDimension(
                resolveSize(measuredWidth, widthMeasureSpec),
                resolveSize(totalHeight + paddingV, heightMeasureSpec));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int pl = getPaddingLeft();
        int pt = getPaddingTop();
        int displayWidth = getContext().getResources().getDisplayMetrics().widthPixels;
        int laidOutWidth = r - l - pl - getPaddingRight();
        int availableWidth = vertical
                ? laidOutWidth
                : Math.max(laidOutWidth, displayWidth - pl - getPaddingRight());
        int x = pl, y = pt, rowHeight = 0;
        int prevRightMargin = 0;
        boolean firstInRow = true;

        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
            int childW = child.getMeasuredWidth();
            int childH = child.getMeasuredHeight();
            int totalH = childH + lp.topMargin + lp.bottomMargin;

            if (vertical) {
                child.layout(pl + lp.leftMargin, y + lp.topMargin,
                        pl + lp.leftMargin + childW, y + lp.topMargin + childH);
                y += totalH;
            } else {
                int gap = firstInRow ? lp.leftMargin : (prevRightMargin + lp.leftMargin);
                int usedX = x - pl;
                if (!firstInRow && usedX + gap + childW > availableWidth) {
                    x = pl;
                    y += rowHeight;
                    rowHeight = 0;
                    firstInRow = true;
                    gap = lp.leftMargin;
                }
                child.layout(x + gap, y + lp.topMargin,
                        x + gap + childW, y + lp.topMargin + childH);
                x += gap + childW;
                rowHeight = Math.max(rowHeight, totalH);
                prevRightMargin = lp.rightMargin;
                firstInRow = false;
            }
        }
    }
}
