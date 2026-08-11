package com.yisi.chesscoach;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public final class MainActivity extends Activity implements ChessBoardView.Listener {
    private static final String START="rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    private final ExecutorService engine=Executors.newSingleThreadExecutor();private final Handler main=new Handler(Looper.getMainLooper());private final AtomicInteger generation=new AtomicInteger();
    private ChessBoardView board;private TextView status,turn,candidates,moves;private String fen=START;private final List<String> history=new ArrayList<>(),fens=new ArrayList<>();private int depth=14;private boolean flipped;
    @Override protected void onCreate(Bundle state){super.onCreate(state);fens.add(fen);engine.execute(()->StockfishNative.initialize("",Math.max(1,Math.min(4,Runtime.getRuntime().availableProcessors()-1)),128));setContentView(build());board.setFen(fen);analyze();}
    private View build(){LinearLayout page=column();page.setPadding(dp(14),dp(12),dp(14),dp(24));page.addView(header());LinearLayout content=new LinearLayout(this);boolean landscape=getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE;content.setOrientation(landscape?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL);LinearLayout left=column();left.addView(controls());board=new ChessBoardView(this);board.setListener(this);left.addView(board,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,landscape?0:ViewGroup.LayoutParams.WRAP_CONTENT,landscape?1:0));LinearLayout right=column();candidates=text("等待 Stockfish…",15,Color.DKGRAY);right.addView(module("教练分析",candidates,true));moves=text("尚未行棋",14,Color.DKGRAY);right.addView(module("棋谱",moves,false));right.addView(module("对弈与分析设置",settings(),false));if(landscape){content.addView(left,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.MATCH_PARENT,.62f));ScrollView scroll=new ScrollView(this);scroll.addView(right);content.addView(scroll,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.MATCH_PARENT,.38f));page.addView(content,new LinearLayout.LayoutParams(-1,0,1));return page;}content.addView(left,new LinearLayout.LayoutParams(-1,-2));content.addView(right,new LinearLayout.LayoutParams(-1,-2));ScrollView scroll=new ScrollView(this);scroll.addView(content);page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));return page;}
    private View header(){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);ImageView logo=new ImageView(this);logo.setImageDrawable(getApplicationInfo().loadIcon(getPackageManager()));logo.setScaleType(ImageView.ScaleType.FIT_CENTER);row.addView(logo,new LinearLayout.LayoutParams(dp(58),dp(58)));LinearLayout title=column();title.setPadding(dp(10),0,0,0);TextView t=text("弈思",22,Color.rgb(24,31,26));t.setTypeface(null,Typeface.BOLD);title.addView(t);title.addView(text("国际象棋教练",14,Color.GRAY));row.addView(title,new LinearLayout.LayoutParams(0,-2,1));status=text("Stockfish",12,Color.rgb(27,88,65));status.setSingleLine(true);row.addView(status);return row;}
    private View controls(){LinearLayout box=column();turn=text("白方走棋",17,Color.rgb(27,88,65));LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.addView(turn,new LinearLayout.LayoutParams(0,dp(48),1));row.addView(button("⇅",v->{flipped=!flipped;board.setFlipped(flipped);}));row.addView(button("悔棋",v->undo()));row.addView(button("重开",v->reset()));box.addView(row);return box;}
    private View settings(){LinearLayout box=column();TextView value=text("分析深度 14",14,Color.DKGRAY);SeekBar seek=new SeekBar(this);seek.setMax(16);seek.setProgress(6);seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean u){depth=8+p;value.setText("分析深度 "+depth);}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){analyze();}});box.addView(value);box.addView(seek);box.addView(text("分析时仍可行棋；局面变化后会立即中断旧任务并分析新局面。",12,Color.GRAY));return box;}
    private View module(String title,View content,boolean open){LinearLayout box=column();TextView h=text((open?"▴ ":"▾ ")+title,16,Color.rgb(27,88,65));h.setTypeface(null,Typeface.BOLD);h.setPadding(dp(12),dp(12),dp(12),dp(12));content.setVisibility(open?View.VISIBLE:View.GONE);h.setOnClickListener(v->{boolean show=content.getVisibility()!=View.VISIBLE;content.setVisibility(show?View.VISIBLE:View.GONE);h.setText((show?"▴ ":"▾ ")+title);});box.addView(h);content.setPadding(dp(12),dp(4),dp(12),dp(14));box.addView(content);return box;}
    void onSquareSelected(String square){
        // Legal targets are part of the touch interaction, so they must not sit
        // behind a full-depth MultiPV request on the serial engine queue.
        final int g=generation.incrementAndGet();
        final String requested=fen;
        StockfishNative.stop();
        engine.execute(()->{
            if(g!=generation.get()||!requested.equals(fen))return;
            String legal=StockfishNative.legalMoves(requested);
            main.post(()->{if(g==generation.get()&&requested.equals(fen))board.setLegalMoves(square,legal);});
        });
    }
    @Override public void onMove(String move){int g=generation.incrementAndGet();StockfishNative.stop();status.setText("更新局面…");engine.execute(()->{String next=StockfishNative.applyMove(fen,move);if(g!=generation.get()||next.startsWith("error:"))return;main.post(()->{fen=next;history.add(move);fens.add(next);board.setFen(next);board.setLastMove(move);update();analyze();});});}
    private void analyze(){
        final int g=generation.incrementAndGet();
        final String requested=fen;
        final int requestedDepth=depth;
        StockfishNative.stop();
        status.setText("Stockfish 计算中");
        engine.execute(()->{
            try{
                int previewDepth=Math.min(8,requestedDepth);
                String preview=formatAnalysis(StockfishNative.analyze(requested,previewDepth,5,""));
                if(isStale(g,requested))return;
                publishAnalysis(g,requested,preview,"Stockfish · 深度 "+previewDepth+" · 继续分析");

                if(previewDepth<requestedDepth){
                    if(isStale(g,requested))return;
                    String complete=formatAnalysis(StockfishNative.analyze(requested,requestedDepth,5,""));
                    if(isStale(g,requested))return;
                    publishAnalysis(g,requested,complete,"Stockfish · 深度 "+requestedDepth);
                }else{
                    publishAnalysis(g,requested,preview,"Stockfish · 已完成");
                }
            }catch(Exception e){
                main.post(()->{if(!isStale(g,requested))status.setText(e.getMessage());});
            }
        });
    }
    private boolean isStale(int g,String requested){return g!=generation.get()||!requested.equals(fen);}
    private String formatAnalysis(String json)throws Exception{
        JSONArray lines=new JSONObject(json).getJSONArray("lines");
        StringBuilder out=new StringBuilder();
        for(int i=0;i<lines.length();i++){
            JSONObject line=lines.getJSONObject(i);
            String pv=line.getString("pv");
            out.append(i+1).append("   ").append(pv.split(" ")[0]).append("   ")
                    .append(line.getString("score")).append("   d").append(line.getInt("depth")).append('\n');
        }
        return out.toString();
    }
    private void publishAnalysis(int g,String requested,String output,String state){
        main.post(()->{
            if(isStale(g,requested))return;
            candidates.setText(output);
            status.setText(state);
        });
    }
    private void update(){turn.setText(fen.split(" ")[1].equals("w")?"白方走棋":"黑方走棋");StringBuilder b=new StringBuilder();for(int i=0;i<history.size();i++){if(i%2==0)b.append(i/2+1).append(". ");b.append(history.get(i)).append(i%2==0?"   ":"\n");}moves.setText(b.length()==0?"尚未行棋":b);}
    private void undo(){if(history.isEmpty())return;generation.incrementAndGet();StockfishNative.stop();history.remove(history.size()-1);fens.remove(fens.size()-1);fen=fens.get(fens.size()-1);board.setFen(fen);update();analyze();}private void reset(){generation.incrementAndGet();StockfishNative.stop();fen=START;history.clear();fens.clear();fens.add(fen);board.setFen(fen);update();analyze();}
    @Override protected void onDestroy(){generation.incrementAndGet();StockfishNative.stop();engine.shutdownNow();super.onDestroy();}
    private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}private TextView text(String s,int z,int c){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setTextColor(c);return v;}private Button button(String s,View.OnClickListener l){Button b=new Button(this);b.setText(s);b.setOnClickListener(l);return b;}private int dp(int x){return(int)(x*getResources().getDisplayMetrics().density+.5f);}
}
