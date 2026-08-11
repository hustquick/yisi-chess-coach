package com.yisi.chesscoach;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class ChessBoardView extends View {
    interface Listener { void onMove(String move); }
    static final class Piece { final char value; final int file,rank; Piece(char v,int f,int r){value=v;file=f;rank=r;} }
    private final Paint paint=new Paint(3); private final List<Piece> pieces=new ArrayList<>(); private final Set<String> legal=new HashSet<>();
    private String selected, lastMove; private boolean flipped; private Listener listener;
    ChessBoardView(Context context){super(context);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
    void setListener(Listener value){listener=value;} void setFlipped(boolean value){flipped=value;invalidate();}
    void setFen(String fen){pieces.clear();String[] rows=fen.split(" ")[0].split("/");for(int r=0;r<Math.min(8,rows.length);r++){int f=0;for(char c:rows[r].toCharArray()){if(Character.isDigit(c))f+=c-'0';else pieces.add(new Piece(c,f++,r));}}invalidate();}
    void setLegalMoves(String from,String moves){selected=from;legal.clear();for(String m:moves.split(" "))if(m.startsWith(from)&&m.length()>=4)legal.add(m.substring(2,4));invalidate();}
    void clearSelection(){selected=null;legal.clear();invalidate();} void setLastMove(String value){lastMove=value;invalidate();}
    @Override protected void onMeasure(int w,int h){int size=MeasureSpec.getSize(w);setMeasuredDimension(size,size);}
    @Override protected void onDraw(Canvas c){float cell=getWidth()/8f;paint.setTypeface(Typeface.create("sans",Typeface.NORMAL));for(int vr=0;vr<8;vr++)for(int vf=0;vf<8;vf++){int f=flipped?7-vf:vf,r=flipped?7-vr:vr;String sq=square(f,r);paint.setColor((vf+vr)%2==0?Color.rgb(234,220,181):Color.rgb(81,125,98));c.drawRect(vf*cell,vr*cell,(vf+1)*cell,(vr+1)*cell,paint);if(sq.equals(selected)){paint.setColor(0x88F3CD2E);c.drawRect(vf*cell,vr*cell,(vf+1)*cell,(vr+1)*cell,paint);}if(legal.contains(sq)){paint.setColor(0x883A8EDB);c.drawCircle((vf+.5f)*cell,(vr+.5f)*cell,cell*.14f,paint);}}
        if(lastMove!=null&&lastMove.length()>=4){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(3,cell*.05f));paint.setColor(0xffff9f1c);for(String sq:new String[]{lastMove.substring(0,2),lastMove.substring(2,4)}){int[] p=decode(sq);int vf=flipped?7-p[0]:p[0],vr=flipped?7-p[1]:p[1];c.drawRect(vf*cell+2,vr*cell+2,(vf+1)*cell-2,(vr+1)*cell-2,paint);}paint.setStyle(Paint.Style.FILL);}
        paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(cell*.78f);for(Piece p:pieces){int vf=flipped?7-p.file:p.file,vr=flipped?7-p.rank:p.rank;paint.setColor(Character.isUpperCase(p.value)?Color.WHITE:Color.rgb(18,18,18));paint.setShadowLayer(2,0,1,0x88000000);c.drawText(symbol(p.value),(vf+.5f)*cell,(vr+.78f)*cell,paint);}paint.clearShadowLayer();
        drawCoordinates(c,cell);
    }
    private void drawCoordinates(Canvas c,float cell){
        paint.setTypeface(Typeface.create("sans",Typeface.BOLD));paint.setTextSize(Math.max(10,cell*.19f));paint.setShadowLayer(1,0,1,0x44000000);
        for(int i=0;i<8;i++){
            int file=flipped?7-i:i,rank=flipped?i+1:8-i;
            paint.setTextAlign(Paint.Align.LEFT);paint.setColor(i%2==0?Color.rgb(234,220,181):Color.rgb(81,125,98));
            c.drawText(String.valueOf((char)('a'+file)),i*cell+cell*.08f,8*cell-cell*.08f,paint);
            paint.setColor(i%2==0?Color.rgb(81,125,98):Color.rgb(234,220,181));
            c.drawText(String.valueOf(rank),cell*.07f,i*cell+cell*.23f,paint);
        }
        paint.clearShadowLayer();
    }
    @Override public boolean onTouchEvent(MotionEvent event){if(event.getAction()!=MotionEvent.ACTION_UP)return true;float cell=getWidth()/8f;int vf=Math.min(7,(int)(event.getX()/cell)),vr=Math.min(7,(int)(event.getY()/cell)),f=flipped?7-vf:vf,r=flipped?7-vr:vr;String sq=square(f,r);if(selected!=null&&legal.contains(sq)){String promotion=(r==0||r==7)&&pieceAt(selected)!=null&&Character.toLowerCase(pieceAt(selected).value)=='p'?"q":"";String move=selected+sq+promotion;previewMove(move);clearSelection();if(listener!=null)listener.onMove(move);return true;}Piece p=pieceAt(sq);if(p!=null){if(listener instanceof MainActivity) ((MainActivity)listener).onSquareSelected(sq);}else clearSelection();return true;}
    private void previewMove(String move){Piece from=pieceAt(move.substring(0,2));if(from==null)return;int[] to=decode(move.substring(2,4));pieces.removeIf(p->(p.file==from.file&&p.rank==from.rank)||(p.file==to[0]&&p.rank==to[1]));char value=move.length()>4?(Character.isUpperCase(from.value)?'Q':'q'):from.value;pieces.add(new Piece(value,to[0],to[1]));lastMove=move;invalidate();}
    private Piece pieceAt(String sq){int[] p=decode(sq);for(Piece x:pieces)if(x.file==p[0]&&x.rank==p[1])return x;return null;}
    private static String square(int f,int r){return ""+(char)('a'+f)+(8-r);} private static int[] decode(String s){return new int[]{s.charAt(0)-'a',8-(s.charAt(1)-'0')};}
    private static String symbol(char p){switch(p){case'K':return"♔";case'Q':return"♕";case'R':return"♖";case'B':return"♗";case'N':return"♘";case'P':return"♙";case'k':return"♚";case'q':return"♛";case'r':return"♜";case'b':return"♝";case'n':return"♞";default:return"♟";}}
}
