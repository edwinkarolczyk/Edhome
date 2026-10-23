package com.edwinkarolczyk.edhome;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Podgląd dwóch zdjęć czynności. Zdjęcia pochodzą z istniejącej bazy lokalnej;
 * ten ekran nie zmienia stanu czynności ani pomiaru Start/Stop.
 */
final class ProjectTaskPhotoDialog {
    interface Actions {
        void replace(int slot);
        void remove(int slot);
    }

    private ProjectTaskPhotoDialog() { }

    static void show(Activity activity, Bitmap first, Bitmap second,
            int selectedSlot, Actions actions) {
        if(first==null && second==null)return;
        final Bitmap[] photos={first,second};
        final int[] index={selectedSlot==2?1:0};
        if(photos[index[0]]==null)index[0]=photos[0]!=null?0:1;

        Dialog dialog=new Dialog(activity,android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        LinearLayout root=new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);

        TextView heading=new TextView(activity);
        heading.setTextColor(Color.WHITE);
        heading.setTextSize(17);
        heading.setGravity(Gravity.CENTER);
        heading.setPadding(12,20,12,12);
        root.addView(heading,new LinearLayout.LayoutParams(-1,-2));

        PhotoSurface surface=new PhotoSurface(activity);
        root.addView(surface,new LinearLayout.LayoutParams(-1,0,1f));

        LinearLayout bar=new LinearLayout(activity);
        bar.setGravity(Gravity.CENTER);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        Button previous=button(activity,"‹");
        Button next=button(activity,"›");
        Button replace=button(activity,"Zmień");
        Button remove=button(activity,"Usuń");
        Button close=button(activity,"Zamknij");
        bar.addView(previous);
        bar.addView(next);
        bar.addView(replace);
        bar.addView(remove);
        bar.addView(close);
        root.addView(bar,new LinearLayout.LayoutParams(-1,-2));

        final Runnable[] update=new Runnable[1];
        update[0]=()->{
            heading.setText("Zdjęcie "+(index[0]+1)+" z 2");
            surface.setPhoto(photos[index[0]]);
            replace.setEnabled(photos[index[0]]!=null);
            remove.setEnabled(photos[index[0]]!=null);
        };
        final java.util.function.IntConsumer switchTo=target->{
            if(target<0||target>1||photos[target]==null)return;
            index[0]=target;
            update[0].run();
        };
        surface.setSwitcher(delta->switchTo.accept(index[0]+delta));
        previous.setOnClickListener(v->switchTo.accept(index[0]-1));
        next.setOnClickListener(v->switchTo.accept(index[0]+1));
        replace.setOnClickListener(v->{
            int slot=index[0]+1;
            dialog.dismiss();
            actions.replace(slot);
        });
        remove.setOnClickListener(v->{
            int slot=index[0]+1;
            dialog.dismiss();
            actions.remove(slot);
        });
        close.setOnClickListener(v->dialog.dismiss());
        update[0].run();
        dialog.setContentView(root);
        dialog.show();
        Window window=dialog.getWindow();
        if(window!=null)window.setLayout(-1,-1);
    }

    private static Button button(Activity activity,String title) {
        Button button=new Button(activity);
        button.setText(title);
        button.setTextSize(12);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(4,0,4,0);
        button.setLayoutParams(new LinearLayout.LayoutParams(0,52,1f));
        return button;
    }

    private static final class PhotoSurface extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        private final ScaleGestureDetector pinch;
        private Bitmap photo;
        private java.util.function.IntConsumer switcher;
        private float zoom=1f;
        private float offsetX,offsetY;
        private float lastX,lastY,downX,downY;
        private boolean pinched;

        PhotoSurface(Activity activity) {
            super(activity);
            setBackgroundColor(Color.BLACK);
            pinch=new ScaleGestureDetector(activity,
                new ScaleGestureDetector.SimpleOnScaleGestureListener(){
                    @Override public boolean onScale(ScaleGestureDetector detector){
                        float before=zoom;
                        zoom=Math.max(1f,Math.min(5f,zoom*detector.getScaleFactor()));
                        float ratio=zoom/before;
                        offsetX=offsetX*ratio+(detector.getFocusX()-getWidth()/2f)*(1f-ratio);
                        offsetY=offsetY*ratio+(detector.getFocusY()-getHeight()/2f)*(1f-ratio);
                        clamp();
                        invalidate();
                        return true;
                    }
                });
        }

        void setSwitcher(java.util.function.IntConsumer next){switcher=next;}
        void setPhoto(Bitmap bitmap){
            photo=bitmap;
            zoom=1f;
            offsetX=offsetY=0;
            invalidate();
        }

        private void clamp(){
            if(photo==null||getWidth()<=0||getHeight()<=0)return;
            float base=Math.min(getWidth()/(float)photo.getWidth(),
                getHeight()/(float)photo.getHeight());
            float width=photo.getWidth()*base*zoom;
            float height=photo.getHeight()*base*zoom;
            float maxX=Math.max(0f,(width-getWidth())/2f);
            float maxY=Math.max(0f,(height-getHeight())/2f);
            offsetX=Math.max(-maxX,Math.min(maxX,offsetX));
            offsetY=Math.max(-maxY,Math.min(maxY,offsetY));
        }

        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            if(photo==null)return;
            clamp();
            float base=Math.min(getWidth()/(float)photo.getWidth(),
                getHeight()/(float)photo.getHeight());
            float width=photo.getWidth()*base*zoom;
            float height=photo.getHeight()*base*zoom;
            float cx=getWidth()/2f+offsetX;
            float cy=getHeight()/2f+offsetY;
            canvas.drawBitmap(photo,null,new RectF(cx-width/2f,cy-height/2f,
                cx+width/2f,cy+height/2f),paint);
        }

        @Override public boolean onTouchEvent(MotionEvent event){
            pinch.onTouchEvent(event);
            switch(event.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    downX=lastX=event.getX();
                    downY=lastY=event.getY();
                    pinched=false;
                    return true;
                case MotionEvent.ACTION_POINTER_DOWN:
                    pinched=true;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if(event.getPointerCount()==1&&!pinch.isInProgress()&&zoom>1f){
                        offsetX+=event.getX()-lastX;
                        offsetY+=event.getY()-lastY;
                        clamp();
                        invalidate();
                    }
                    lastX=event.getX();
                    lastY=event.getY();
                    return true;
                case MotionEvent.ACTION_UP:
                    float dx=event.getX()-downX;
                    float dy=event.getY()-downY;
                    float threshold=60*getResources().getDisplayMetrics().density;
                    if(!pinched&&zoom<=1.01f&&Math.abs(dx)>threshold
                        &&Math.abs(dx)>Math.abs(dy)*1.25f&&switcher!=null)
                        switcher.accept(dx<0?1:-1);
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    return true;
                default:return true;
            }
        }
    }
}
