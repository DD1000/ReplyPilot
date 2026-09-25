package com.contentfoundry.replypilot;

/** Keeps a rejected touch from becoming a click when its overlay disappears. */
final class TouchProtection {
    private boolean blocked;
    private boolean deliveredDown;

    record Decision(boolean block,boolean cancelDeliveredTouch,boolean dismissSelection) {}

    Decision next(boolean down,boolean terminal,boolean obscured,boolean selectionActive){
        if(down){blocked=false;deliveredDown=false;}
        boolean firstBlock=obscured&&!blocked;
        // Only a fresh outside tap dismisses a toolbar. Its appearance at the
        // end of the long press that created it is not a dismissal request.
        Decision result=new Decision(blocked||obscured,firstBlock&&deliveredDown,firstBlock&&down&&selectionActive);
        if(obscured)blocked=true;
        if(!result.block()&&down)deliveredDown=true;
        if(terminal){blocked=false;deliveredDown=false;}
        return result;
    }

    void reset(){blocked=false;deliveredDown=false;}
}
