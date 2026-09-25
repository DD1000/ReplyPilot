package com.contentfoundry.replypilot;

import android.app.AlertDialog;
import android.content.Context;
import android.view.MotionEvent;

/** The send confirmation must remain visible when the user touches its controls. */
public final class ApprovalDialog extends AlertDialog {
    public ApprovalDialog(Context context){super(context);}

    @Override public boolean dispatchTouchEvent(MotionEvent event){
        int obscured=MotionEvent.FLAG_WINDOW_IS_OBSCURED|MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED;
        if((event.getFlags()&obscured)!=0)return true;
        return super.dispatchTouchEvent(event);
    }
}
