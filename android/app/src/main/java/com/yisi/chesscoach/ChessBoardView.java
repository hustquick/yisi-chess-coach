package com.yisi.chesscoach;

import android.content.Context;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.View;
import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class ChessBoardView extends View {
    interface Listener { void onMove(String move); void onSquareSelected(String square); void onSelectionCleared(); void onPromotion(String move); void onEditorSquare(String square); }
    static final class Piece { final char value; final int file,rank; Piece(char v,int f,int r){value=v;file=f;rank=r;} }
    static final class VariationFrame { final String beforeFen,afterFen,move,san,step;VariationFrame(String b,String a,String m,String n,String s){beforeFen=b;afterFen=a;move=m;san=n;step=s;} }
    private final Paint paint=new Paint(3); private final List<Piece> pieces=new ArrayList<>(); private final Set<String> legal=new HashSet<>();
    private final Handler handler=new Handler(Looper.getMainLooper());private String selected,lastMove,actualFen,previewSan,previewStep;private boolean flipped,previewing;private Listener listener;private List<VariationFrame> previewFrames;private int previewIndex;private float previewProgress;private char previewPiece;private int[] previewFrom,previewTo;private ValueAnimator previewAnimator;
    private boolean arrowsVisible,editor;
    private final List<String> candidateMoves=new ArrayList<>();
    ChessBoardView(Context context){super(context);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
    void setListener(Listener value){listener=value;} void setFlipped(boolean value){flipped=value;invalidate();}
    void setFen(String fen){cancelPreview(false);actualFen=fen;selected=null;legal.clear();candidateMoves.clear();loadFen(fen);invalidate();}
    void setCandidateMoves(List<String> moves,boolean visible){candidateMoves.clear();candidateMoves.addAll(moves);arrowsVisible=visible;invalidate();}
    void setEditor(boolean value){editor=value;clearSelection();stopVariationPreview();}
    void editSquare(String square,char value){int[] p=decode(square);pieces.removeIf(piece->piece.file==p[0]&&piece.rank==p[1]);if(value!=0)pieces.add(new Piece(value,p[0],p[1]));invalidate();}
    String editorFen(boolean white){StringBuilder output=new StringBuilder();for(int r=0;r<8;r++){if(r>0)output.append('/');int empty=0;for(int f=0;f<8;f++){Piece piece=pieceAt(square(f,r));if(piece==null)empty++;else{if(empty>0){output.append(empty);empty=0;}output.append(piece.value);}}if(empty>0)output.append(empty);}return output+(white?" w":" b")+" - - 0 1";}
    private void loadFen(String fen){pieces.clear();String[] rows=fen.split(" ")[0].split("/");for(int r=0;r<Math.min(8,rows.length);r++){int f=0;for(char c:rows[r].toCharArray()){if(Character.isDigit(c))f+=c-'0';else pieces.add(new Piece(c,f++,r));}}}
    void setLegalMoves(String from,String moves){selected=from;legal.clear();for(String m:moves.split(" "))if(m.startsWith(from)&&m.length()>=4)legal.add(m.substring(2,4));invalidate();}
    void clearSelection(){selected=null;legal.clear();invalidate();} void setLastMove(String value){lastMove=value;invalidate();}
    @Override protected void onMeasure(int w,int h){int cap=Math.max((int)(160*getResources().getDisplayMetrics().density),getResources().getDisplayMetrics().heightPixels-(int)(180*getResources().getDisplayMetrics().density));int size=Math.min(MeasureSpec.getSize(w),cap);if(MeasureSpec.getMode(h)!=MeasureSpec.UNSPECIFIED)size=Math.min(size,MeasureSpec.getSize(h));setMeasuredDimension(size,size);}
    @Override protected void onDraw(Canvas c){float cell=getWidth()/8f;paint.setTypeface(Typeface.create("sans",Typeface.NORMAL));for(int vr=0;vr<8;vr++)for(int vf=0;vf<8;vf++){int f=flipped?7-vf:vf,r=flipped?7-vr:vr;String sq=square(f,r);paint.setColor((vf+vr)%2==0?Color.rgb(234,220,181):Color.rgb(81,125,98));c.drawRect(vf*cell,vr*cell,(vf+1)*cell,(vr+1)*cell,paint);if(sq.equals(selected)){paint.setColor(0x88F3CD2E);c.drawRect(vf*cell,vr*cell,(vf+1)*cell,(vr+1)*cell,paint);}if(legal.contains(sq)){paint.setColor(0x883A8EDB);c.drawCircle((vf+.5f)*cell,(vr+.5f)*cell,cell*.14f,paint);}}
        if(lastMove!=null&&lastMove.length()>=4){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(3,cell*.05f));paint.setColor(0xffff9f1c);for(String sq:new String[]{lastMove.substring(0,2),lastMove.substring(2,4)}){int[] p=decode(sq);int vf=flipped?7-p[0]:p[0],vr=flipped?7-p[1]:p[1];c.drawRect(vf*cell+2,vr*cell+2,(vf+1)*cell-2,(vr+1)*cell-2,paint);}paint.setStyle(Paint.Style.FILL);}
        paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(cell*.78f);for(Piece p:pieces){drawPiece(c,p.value,p.file,p.rank,cell);}paint.clearShadowLayer();
        if(arrowsVisible&&!previewing&&!editor)drawCandidateArrows(c,cell);
        if(previewing&&previewFrom!=null&&previewTo!=null){float ff=flipped?7-previewFrom[0]:previewFrom[0],fr=flipped?7-previewFrom[1]:previewFrom[1],tf=flipped?7-previewTo[0]:previewTo[0],tr=flipped?7-previewTo[1]:previewTo[1],x1=(ff+.5f)*cell,y1=(fr+.5f)*cell,x2=(tf+.5f)*cell,y2=(tr+.5f)*cell;paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(3,cell*.045f));paint.setColor(0xaa2ba66d);c.drawLine(x1,y1,x1+(x2-x1)*previewProgress,y1+(y2-y1)*previewProgress,paint);paint.setStrokeWidth(Math.max(3,cell*.055f));paint.setColor(0xdd2ba66d);c.drawCircle(x2,y2,cell*(.34f+.08f*previewProgress),paint);paint.setStyle(Paint.Style.FILL);drawPieceAt(c,previewPiece,x1+(x2-x1)*previewProgress,y1+(y2-y1)*previewProgress,cell);paint.setColor(0xdd173d2c);c.drawRoundRect(cell*.25f,cell*.15f,getWidth()-cell*.25f,cell*.78f,cell*.25f,cell*.25f,paint);paint.setColor(Color.WHITE);paint.setTextSize(cell*.23f);paint.setTextAlign(Paint.Align.CENTER);c.drawText("▶ 变化演示  "+previewStep+"  "+previewSan,getWidth()/2f,cell*.56f,paint);}
        drawCoordinates(c,cell);
    }
    private void drawPiece(Canvas c,char value,int file,int rank,float cell){int vf=flipped?7-file:file,vr=flipped?7-rank:rank;drawPieceAt(c,value,(vf+.5f)*cell,(vr+.5f)*cell,cell);}
    private void drawPieceAt(Canvas canvas,char value,float x,float y,float cell){
        // Canvas silhouettes avoid platform-dependent Unicode glyphs and missing fonts.
        canvas.save();canvas.translate(x-cell*.5f,y-cell*.5f);canvas.scale(cell,cell);
        boolean white=Character.isUpperCase(value);int fill=white?Color.rgb(255,249,231):Color.rgb(34,43,37),stroke=white?Color.rgb(50,65,52):Color.rgb(210,215,190);
        paint.clearShadowLayer();paint.setStrokeWidth(.025f);paint.setStrokeJoin(Paint.Join.ROUND);paint.setStrokeCap(Paint.Cap.ROUND);
        Path body=new Path();char type=Character.toLowerCase(value);
        if(type=='p'){body.moveTo(.32f,.73f);body.lineTo(.40f,.44f);body.lineTo(.60f,.44f);body.lineTo(.68f,.73f);body.close();}
        else if(type=='r'){body.moveTo(.27f,.22f);body.lineTo(.38f,.22f);body.lineTo(.38f,.32f);body.lineTo(.46f,.32f);body.lineTo(.46f,.22f);body.lineTo(.54f,.22f);body.lineTo(.54f,.32f);body.lineTo(.62f,.32f);body.lineTo(.62f,.22f);body.lineTo(.73f,.22f);body.lineTo(.73f,.40f);body.lineTo(.62f,.47f);body.lineTo(.65f,.73f);body.lineTo(.35f,.73f);body.lineTo(.38f,.47f);body.lineTo(.27f,.40f);body.close();}
        else if(type=='n'){body.moveTo(.27f,.73f);body.cubicTo(.25f,.60f,.49f,.54f,.52f,.43f);body.lineTo(.32f,.48f);body.lineTo(.23f,.39f);body.lineTo(.44f,.20f);body.lineTo(.43f,.12f);body.lineTo(.57f,.20f);body.cubicTo(.78f,.28f,.75f,.55f,.71f,.73f);body.close();}
        else if(type=='b'){body.moveTo(.30f,.73f);body.lineTo(.43f,.46f);body.cubicTo(.20f,.35f,.46f,.19f,.50f,.14f);body.cubicTo(.55f,.20f,.80f,.35f,.57f,.46f);body.lineTo(.70f,.73f);body.close();}
        else if(type=='q'){body.moveTo(.31f,.73f);body.lineTo(.26f,.29f);body.lineTo(.40f,.44f);body.lineTo(.50f,.22f);body.lineTo(.60f,.44f);body.lineTo(.74f,.29f);body.lineTo(.69f,.73f);body.close();}
        else {body.moveTo(.31f,.73f);body.lineTo(.37f,.49f);body.cubicTo(.20f,.30f,.41f,.25f,.50f,.37f);body.cubicTo(.59f,.25f,.80f,.30f,.63f,.49f);body.lineTo(.69f,.73f);body.close();}
        shape(canvas,body,fill,stroke);
        if(type=='p'){Path head=new Path();head.addCircle(.50f,.31f,.13f,Path.Direction.CW);shape(canvas,head,fill,stroke);}
        if(type=='k'){paint.setColor(stroke);paint.setStrokeWidth(.085f);canvas.drawLine(.50f,.12f,.50f,.29f,paint);canvas.drawLine(.42f,.19f,.58f,.19f,paint);paint.setStrokeWidth(.035f);paint.setColor(fill);canvas.drawLine(.50f,.12f,.50f,.29f,paint);canvas.drawLine(.42f,.19f,.58f,.19f,paint);}
        if(type=='q'){for(float crown:new float[]{.25f,.50f,.75f}){Path bead=new Path();bead.addCircle(crown,crown==.5f?.18f:.25f,.042f,Path.Direction.CW);shape(canvas,bead,fill,stroke);}}
        if(type=='b'){paint.setColor(stroke);paint.setStrokeWidth(.035f);canvas.drawLine(.52f,.25f,.44f,.36f,paint);}
        if(type=='n'){paint.setColor(stroke);canvas.drawCircle(.48f,.31f,.024f,paint);}
        Path foot=new Path();foot.addRoundRect(.24f,.73f,.76f,.84f,.025f,.025f,Path.Direction.CW);shape(canvas,foot,fill,stroke);
        canvas.restore();paint.setStyle(Paint.Style.FILL);paint.setStrokeWidth(1);paint.setStrokeCap(Paint.Cap.BUTT);
    }
    private void shape(Canvas canvas,Path path,int fill,int stroke){paint.setStyle(Paint.Style.FILL);paint.setColor(fill);canvas.drawPath(path,paint);paint.setStyle(Paint.Style.STROKE);paint.setColor(stroke);paint.setStrokeWidth(.025f);canvas.drawPath(path,paint);paint.setStyle(Paint.Style.FILL);}
    private void drawCandidateArrows(Canvas canvas,float cell){
        int[] colors={0xd927985f,0xb83480ba,0xbabd7e29,0xb89361af,0xb85b8881};
        for(int index=Math.min(5,candidateMoves.size())-1;index>=0;index--){
            String move=candidateMoves.get(index);if(!move.matches("[a-h][1-8][a-h][1-8][qrbn]?"))continue;
            int[] from=decode(move.substring(0,2)),to=decode(move.substring(2,4));
            float x1=((flipped?7-from[0]:from[0])+.5f)*cell,y1=((flipped?7-from[1]:from[1])+.5f)*cell;
            float x2=((flipped?7-to[0]:to[0])+.5f)*cell,y2=((flipped?7-to[1]:to[1])+.5f)*cell;
            double angle=Math.atan2(y2-y1,x2-x1);float ux=(float)Math.cos(angle),uy=(float)Math.sin(angle);
            paint.setColor(colors[index]);paint.setStrokeWidth(cell*(index==0?.105f:.065f));paint.setStrokeCap(Paint.Cap.ROUND);
            canvas.drawLine(x1+ux*cell*.16f,y1+uy*cell*.16f,x2-ux*cell*.22f,y2-uy*cell*.22f,paint);
            Path head=new Path();head.moveTo(x2,y2);head.lineTo(x2-ux*cell*.31f-uy*cell*.16f,y2-uy*cell*.31f+ux*cell*.16f);head.lineTo(x2-ux*cell*.31f+uy*cell*.16f,y2-uy*cell*.31f-ux*cell*.16f);head.close();canvas.drawPath(head,paint);
        }paint.setStrokeCap(Paint.Cap.BUTT);
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
    @Override public boolean onTouchEvent(MotionEvent event){
        if(previewing)return true;if(event.getAction()!=MotionEvent.ACTION_UP)return true;
        performClick();if(event.getX()<0||event.getY()<0||event.getX()>=getWidth()||event.getY()>=getHeight())return true;
        float cell=getWidth()/8f;int vf=(int)(event.getX()/cell),vr=(int)(event.getY()/cell),f=flipped?7-vf:vf,r=flipped?7-vr:vr;String sq=square(f,r);
        if(listener==null)return true;
        if(editor){listener.onEditorSquare(sq);return true;}
        if(selected!=null&&legal.contains(sq)){
            String move=selected+sq;Piece source=pieceAt(selected);
            if((r==0||r==7)&&source!=null&&Character.toLowerCase(source.value)=='p')listener.onPromotion(move);
            else listener.onMove(move);
            return true;
        }
        Piece piece=pieceAt(sq);boolean whiteTurn=actualFen!=null&&actualFen.split(" ")[1].equals("w");
        if(piece!=null&&Character.isUpperCase(piece.value)==whiteTurn){if(sq.equals(selected)){clearSelection();listener.onSelectionCleared();}else listener.onSquareSelected(sq);}
        else{clearSelection();listener.onSelectionCleared();}
        return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
    void startVariation(List<VariationFrame> frames,String currentFen){stopVariationPreview();if(frames==null||frames.isEmpty())return;actualFen=currentFen;previewFrames=new ArrayList<>(frames);previewing=true;playPreviewFrame(0);}
    void stopVariationPreview(){cancelPreview(true);}
    private void cancelPreview(boolean restore){handler.removeCallbacksAndMessages(null);if(previewAnimator!=null){previewAnimator.cancel();previewAnimator=null;}previewing=false;previewFrames=null;previewFrom=null;previewTo=null;if(restore&&actualFen!=null)loadFen(actualFen);invalidate();}
    private void playPreviewFrame(int index){if(!previewing||previewFrames==null||index>=previewFrames.size()){handler.postDelayed(()->stopVariationPreview(),950);return;}previewIndex=index;VariationFrame frame=previewFrames.get(index);previewFrom=decode(frame.move.substring(0,2));previewTo=decode(frame.move.substring(2,4));List<Piece> before=parsePieces(frame.beforeFen);Piece moving=null;for(Piece p:before)if(p.file==previewFrom[0]&&p.rank==previewFrom[1]){moving=p;break;}if(moving==null){stopVariationPreview();return;}previewPiece=moving.value;if(frame.move.length()>4)previewPiece=Character.isUpperCase(previewPiece)?Character.toUpperCase(frame.move.charAt(4)):frame.move.charAt(4);previewSan=frame.san;previewStep=frame.step;loadFen(frame.afterFen);pieces.removeIf(p->p.file==previewTo[0]&&p.rank==previewTo[1]);previewProgress=0;previewAnimator=ValueAnimator.ofFloat(0f,1f);previewAnimator.setDuration(620);previewAnimator.addUpdateListener(a->{previewProgress=(float)a.getAnimatedValue();invalidate();});previewAnimator.start();handler.postDelayed(()->{if(!previewing)return;loadFen(frame.afterFen);previewFrom=null;previewTo=null;invalidate();handler.postDelayed(()->playPreviewFrame(index+1),260);},700);}
    private List<Piece> parsePieces(String fen){List<Piece> saved=new ArrayList<>(pieces);loadFen(fen);List<Piece> result=new ArrayList<>(pieces);pieces.clear();pieces.addAll(saved);return result;}
    private Piece pieceAt(String sq){int[] p=decode(sq);for(Piece x:pieces)if(x.file==p[0]&&x.rank==p[1])return x;return null;}
    private static String square(int f,int r){return ""+(char)('a'+f)+(8-r);} private static int[] decode(String s){return new int[]{s.charAt(0)-'a',8-(s.charAt(1)-'0')};}
}
