package com.yisi.chesscoach;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.Toast;
import android.content.SharedPreferences;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public final class MainActivity extends Activity implements ChessBoardView.Listener {
    static final String START="rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    private final ExecutorService engine=Executors.newSingleThreadExecutor();private final Handler main=new Handler(Looper.getMainLooper());private final AtomicInteger generation=new AtomicInteger();
    private static final int[] ELOS={1320,1500,1700,1900,2100,2300,2500,2700,2900,3100};
    private static final String[] LEVELS={"业余一级","业余三级","业余五级","业余七级","业余九级","专业一级","专业三级","专业五级","专业七级","专业九级"};
    private ChessBoardView board;private EvaluationChartView chart;private TextView status,turn,moves;private LinearLayout candidates;private Button modeButton,sideButton,levelButton,undoButton,redoButton;private String fen=START,gameStatus="ongoing";private final List<String> history=new ArrayList<>(),notations=new ArrayList<>(),fens=new ArrayList<>(),redoHistory=new ArrayList<>(),redoNotations=new ArrayList<>(),redoFens=new ArrayList<>();private int depth=14,eloIndex=4;private boolean flipped,computerMode,humanWhite=true,showBest=true;
    private static final class CandidateData{final int rank,depth;final String move,san,score,continuation;final List<ChessBoardView.VariationFrame> frames;CandidateData(int r,int d,String m,String n,String s,String c,List<ChessBoardView.VariationFrame> f){rank=r;depth=d;move=m;san=n;score=s;continuation=c;frames=f;}}
    private SharedPreferences preferences;
    private volatile boolean engineReady;
    private boolean destroyed, editing;
    private String selectedSquare, recordName="新对局";
    private List<CandidateData> visibleCandidates=new ArrayList<>();
    private LinearLayout recordRows;
    private Button bestButton;
    private final Map<String,Double> evaluations=new LinkedHashMap<>();
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);preferences=getSharedPreferences("chess_records",MODE_PRIVATE);
        fens.add(fen);setContentView(build());((LinearLayout.LayoutParams)board.getLayoutParams()).gravity=Gravity.CENTER_HORIZONTAL;board.setFen(fen);update();
        getWindow().setStatusBarColor(Color.rgb(248,245,234));getWindow().setNavigationBarColor(Color.rgb(248,245,234));
        if(Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(false);
        else getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        installInsets();getWindow().getDecorView().requestApplyInsets();
        engine.execute(()->{try{
            String result=StockfishNative.initialize("",Math.max(1,Math.min(3,Runtime.getRuntime().availableProcessors()-2)),64);
            if(!"ready".equals(result))throw new IllegalStateException(result);engineReady=true;
            main.post(()->{if(destroyed)return;String saved=preferences.getString("session","");
                if(saved.isEmpty())analyze();else try{loadRecord(new JSONObject(saved));}catch(Exception e){notice("上次对局无法恢复，原存档未删除。");analyze();}});
        }catch(Exception|LinkageError e){reportError("引擎无法加载",e);}});
    }
    private View build(){LinearLayout page=column();page.setBackgroundColor(Color.rgb(248,245,234));page.setPadding(dp(14),dp(8),dp(14),dp(10));page.addView(header());LinearLayout content=new LinearLayout(this);boolean landscape=getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE;content.setOrientation(landscape?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL);LinearLayout left=column();left.addView(controls());board=new ChessBoardView(this);board.setListener(this);left.addView(board,new LinearLayout.LayoutParams(-1,-2));LinearLayout right=column();candidates=column();candidates.addView(text("等待 Stockfish…",15,Color.DKGRAY));right.addView(module("教练分析",candidates,true));right.addView(chartModule());right.addView(module("对弈与分析设置",settings(),false));right.addView(module("棋谱与存档",recordControls(),false));right.addView(text("Stockfish · GPL-3.0 开源引擎。存档仅保存在本机。",11,Color.GRAY));if(landscape){ScrollView boardScroll=new ScrollView(this);boardScroll.addView(left);content.addView(boardScroll,new LinearLayout.LayoutParams(0,-1,.60f));ScrollView scroll=new ScrollView(this);scroll.addView(right);content.addView(scroll,new LinearLayout.LayoutParams(0,-1,.40f));page.addView(content,new LinearLayout.LayoutParams(-1,0,1));return page;}content.addView(left,new LinearLayout.LayoutParams(-1,-2));content.addView(right,new LinearLayout.LayoutParams(-1,-2));ScrollView scroll=new ScrollView(this);scroll.addView(content);page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));return page;}
    private View header(){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);ImageView logo=new ImageView(this);logo.setImageDrawable(getApplicationInfo().loadIcon(getPackageManager()));logo.setScaleType(ImageView.ScaleType.FIT_CENTER);row.addView(logo,new LinearLayout.LayoutParams(dp(42),dp(42)));LinearLayout title=column();title.setPadding(dp(8),0,0,0);TextView t=text("弈思",20,Color.rgb(24,31,26));t.setTypeface(null,Typeface.BOLD);title.addView(t);title.addView(text("国际象棋思考教练",11,Color.GRAY));row.addView(title,new LinearLayout.LayoutParams(0,-2,1));status=text("引擎准备中",11,Color.rgb(27,88,65));status.setMaxLines(2);row.addView(status,new LinearLayout.LayoutParams(dp(105),-2));return row;}
    private View controls(){LinearLayout box=column();turn=text("白方走棋",13,Color.rgb(27,88,65));turn.setGravity(Gravity.CENTER);LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(6),0,dp(6));undoButton=button("↶",v->undo());undoButton.setContentDescription("悔棋");redoButton=button("↷",v->redo());redoButton.setContentDescription("前进");bestButton=button("优",v->{showBest=!showBest;refreshArrows();});Button flip=button("⇅",v->{flipped=!flipped;board.setFlipped(flipped);});flip.setContentDescription("翻转棋盘");Button restart=button("↻",v->new AlertDialog.Builder(this).setTitle("重新开局？").setMessage("当前对局将重置，已保存的棋谱不受影响。").setPositiveButton("重开",(d,w)->reset()).setNegativeButton("取消",null).show());restart.setContentDescription("重开");for(Button b:new Button[]{undoButton,redoButton,bestButton,flip,restart}){b.setTextSize(19);row.addView(b,new LinearLayout.LayoutParams(dp(40),dp(42)));}row.addView(turn,new LinearLayout.LayoutParams(0,dp(42),1));box.addView(row);return box;}
    private View settings(){
        LinearLayout box=column();
        modeButton=button("模式：双人对弈",v->new AlertDialog.Builder(this).setTitle("对弈模式").setItems(new String[]{"双人对弈","人机对战","摆棋"},(d,w)->{
            if(!engineReady){notice("请等待引擎准备完成。");return;}
            generation.incrementAndGet();StockfishNative.stop();selectedSquare=null;board.clearSelection();board.stopVariationPreview();
            computerMode=w==1;editing=w==2;board.setEditor(editing);board.setFen(fen);
            modeButton.setText("模式："+(editing?"摆棋":computerMode?"人机对战":"双人对弈"));
            sideButton.setVisibility(computerMode?View.VISIBLE:View.GONE);levelButton.setVisibility(computerMode?View.VISIBLE:View.GONE);
            if(editing){status.setText("摆棋 · 点击格子选择棋子");board.setCandidateMoves(new ArrayList<>(),false);notice("点击格子放置或删除棋子，完成后点击「完成摆棋」。");}
            else if(computerMode&&!isHumanTurn())playComputerMove();else analyze();
        }).show());
        sideButton=button("我方：白方",v->{humanWhite=!humanWhite;sideButton.setText(humanWhite?"我方：白方":"我方：黑方");generation.incrementAndGet();if(engineReady)StockfishNative.stop();if(!isHumanTurn())playComputerMove();else analyze();});sideButton.setVisibility(View.GONE);
        levelButton=button("电脑："+LEVELS[eloIndex]+" · Elo "+ELOS[eloIndex],v->new AlertDialog.Builder(this).setTitle("电脑等级（参考 Elo）").setItems(LEVELS,(d,w)->{eloIndex=w;levelButton.setText("电脑："+LEVELS[w]+" · Elo "+ELOS[w]);if(computerMode&&!isHumanTurn()){generation.incrementAndGet();if(engineReady)StockfishNative.stop();playComputerMove();}}).show());levelButton.setVisibility(View.GONE);
        box.addView(modeButton);box.addView(sideButton);box.addView(levelButton);
        TextView value=text("分析深度 14",14,Color.DKGRAY);SeekBar seek=new SeekBar(this);seek.setMax(16);seek.setProgress(6);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean u){depth=8+p;value.setText("分析深度 "+depth);}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){analyze();}});
        box.addView(value);box.addView(seek);
        box.addView(button("完成摆棋",v->{if(!editing){notice("请先切换到摆棋模式。");return;}new AlertDialog.Builder(this).setTitle("选择行棋方").setItems(new String[]{"白方","黑方"},(d,w)->{try{loadPosition(FenRules.validate(board.editorFen(w==0)));}catch(Exception e){notice(e.getMessage());}}).show();}));
        box.addView(button("取消摆棋",v->{if(!editing)return;editing=false;computerMode=false;board.setEditor(false);board.setFen(fen);board.clearSelection();modeButton.setText("模式：双人对弈");analyze();}));
        box.addView(text("选中棋子查看该棋子的候选；点击空格返回全局。电脑等级为参考 Elo，并非平台官方段级。摆棋完成后默认取消王车易位权，可通过 FEN 精确设置。",12,Color.GRAY));return box;
    }
    private View module(String title,View content,boolean open){LinearLayout box=column();LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.topMargin=dp(10);box.setLayoutParams(params);box.setBackground(surface(Color.rgb(253,252,247),12));TextView h=text((open?"▴ ":"▾ ")+title,16,Color.rgb(27,88,65));h.setTypeface(null,Typeface.BOLD);h.setPadding(dp(12),dp(12),dp(12),dp(12));content.setVisibility(open?View.VISIBLE:View.GONE);h.setOnClickListener(v->{boolean show=content.getVisibility()!=View.VISIBLE;content.setVisibility(show?View.VISIBLE:View.GONE);h.setText((show?"▴ ":"▾ ")+title);});box.addView(h);content.setPadding(dp(12),dp(4),dp(12),dp(14));box.addView(content);return box;}
    public void onSquareSelected(String square){
        if(!engineReady||editing)return;
        if(!"ongoing".equals(gameStatus)||!isHumanTurn())return;
        // Legal targets are part of the touch interaction, so they must not sit
        // behind a full-depth MultiPV request on the serial engine queue.
        final int g=generation.incrementAndGet();
        final String requested=fen;
        selectedSquare=square;
        StockfishNative.stop();
        engine.execute(()->{
            if(g!=generation.get()||!requested.equals(fen))return;
            try{String legal=StockfishNative.legalMoves(requested);
                main.post(()->{if(g==generation.get()&&requested.equals(fen)&&!destroyed){board.setLegalMoves(square,legal);analyze();}});
            }catch(Exception|LinkageError e){reportError("选子分析失败",e);}
        });
    }
    @Override public void onMove(String move){
        if(!engineReady||editing||!"ongoing".equals(gameStatus)||!isHumanTurn())return;
        final String requested=fen;final int g=generation.incrementAndGet();StockfishNative.stop();selectedSquare=null;board.stopVariationPreview();status.setText("更新局面…");
        engine.execute(()->{try{
            if(isStale(g,requested))return;
            if(!Arrays.asList(StockfishNative.legalMoves(requested).split(" ")).contains(move))throw new IllegalArgumentException("这步棋不合法。");
            String notation=StockfishNative.san(requested,move),next=StockfishNative.applyMove(requested,move);
            if(isStale(g,requested)||next.startsWith("error:"))return;String nextStatus=StockfishNative.gameStatus(next);
            main.post(()->{if(isStale(g,requested))return;
                fen=next;gameStatus=nextStatus;redoHistory.clear();redoNotations.clear();redoFens.clear();visibleCandidates.clear();board.clearSelection();
                history.add(move);notations.add(notation.startsWith("error:")?move:notation);fens.add(next);board.setFen(next);board.setLastMove(move);update();
                if("ongoing".equals(gameStatus)){if(computerMode&&!isHumanTurn())playComputerMove();else analyze();}else showGameOutcome();
            });
        }catch(Exception|LinkageError e){reportError("行棋失败",e);}});
    }
    private boolean isHumanTurn(){return !computerMode||(fen.split(" ")[1].equals("w")==humanWhite);}
    private void playComputerMove(){if(!engineReady||editing||!computerMode||isHumanTurn()||!"ongoing".equals(gameStatus))return;final int g=generation.incrementAndGet();final String requested=fen;final int requestedElo=ELOS[eloIndex];final int playDepth=Math.min(depth,10);StockfishNative.stop();status.setText("Stockfish · "+LEVELS[eloIndex]+" 正在思考");engine.execute(()->{try{if(isStale(g,requested))return;String move=StockfishNative.bestMove(requested,playDepth,requestedElo);if(isStale(g,requested)||move.startsWith("error:"))return;String notation=StockfishNative.san(requested,move),next=StockfishNative.applyMove(requested,move);if(isStale(g,requested)||next.startsWith("error:"))return;String nextStatus=StockfishNative.gameStatus(next);main.post(()->{if(isStale(g,requested))return;fen=next;gameStatus=nextStatus;selectedSquare=null;visibleCandidates.clear();redoHistory.clear();redoNotations.clear();redoFens.clear();board.clearSelection();history.add(move);notations.add(notation.startsWith("error:")?move:notation);fens.add(next);board.setFen(next);board.setLastMove(move);update();if("ongoing".equals(gameStatus))analyze();else showGameOutcome();});}catch(Exception|LinkageError e){reportError("电脑行棋失败",e);}});}
    private void analyze(){
        if(!engineReady||editing||destroyed||!"ongoing".equals(gameStatus))return;
        final int g=generation.incrementAndGet();
        final String requested=fen;
        final int requestedDepth=depth;
        final String square=selectedSquare;
        StockfishNative.stop();
        status.setText("Stockfish 计算中");
        engine.execute(()->{
            try{
                if(isStale(g,requested))return;
                String restriction="";
                if(square!=null){
                    StringBuilder targets=new StringBuilder();
                    for(String move:StockfishNative.legalMoves(requested).split(" "))if(move.startsWith(square)){if(targets.length()>0)targets.append(' ');targets.append(move);}
                    restriction=targets.toString();
                    if(restriction.isEmpty()){publishAnalysis(g,requested,new ArrayList<>(),"该棋子暂无合法着法");return;}
                }
                int previewDepth=Math.min(8,requestedDepth);
                List<CandidateData> preview=formatAnalysis(StockfishNative.analyze(requested,previewDepth,5,restriction),requested);
                if(isStale(g,requested))return;
                publishAnalysis(g,requested,preview,"Stockfish · 深度 "+previewDepth+" · 继续分析");

                if(previewDepth<requestedDepth){
                    if(isStale(g,requested))return;
                    List<CandidateData> complete=formatAnalysis(StockfishNative.analyze(requested,requestedDepth,5,restriction),requested);
                    if(isStale(g,requested))return;
                    publishAnalysis(g,requested,complete,"Stockfish · 深度 "+requestedDepth);
                }else{
                    publishAnalysis(g,requested,preview,"Stockfish · 已完成");
                }
            }catch(Exception|LinkageError e){
                android.util.Log.e("YisiChess","分析失败",e);
                main.post(()->{if(!isStale(g,requested))status.setText("分析失败："+e.getMessage());});
            }
        });
    }
    private boolean isStale(int g,String requested){return destroyed||g!=generation.get()||!requested.equals(fen);}
    private List<CandidateData> formatAnalysis(String json,String positionFen)throws Exception{
        JSONArray lines=new JSONObject(json).getJSONArray("lines");
        List<CandidateData> result=new ArrayList<>();
        for(int i=0;i<lines.length();i++){
            JSONObject line=lines.getJSONObject(i);
            String pv=line.getString("pv");
            String[] variation=pv.trim().split("\\s+");String current=positionFen;StringBuilder sanLine=new StringBuilder();List<ChessBoardView.VariationFrame> frames=new ArrayList<>();String first=variation.length>0?variation[0]:"",firstSan=first;
            for(int j=0;j<Math.min(18,variation.length);j++){String uci=variation[j],san=StockfishNative.san(current,uci),next=StockfishNative.applyMove(current,uci);if(next.startsWith("error:"))break;if(san.startsWith("error:"))san=uci;if(j==0)firstSan=san;if(j<5){if(sanLine.length()>0)sanLine.append("  ");sanLine.append(san);}frames.add(new ChessBoardView.VariationFrame(current,next,uci,san,(j/2+1)+(j%2==0?"a":"b")));current=next;}
            result.add(new CandidateData(i+1,line.getInt("depth"),first,firstSan,whiteScoreText(line.getString("score"),positionFen),sanLine.toString(),frames));
        }
        return result;
    }
    private String whiteScoreText(String score,String requestedFen){String[] parts=score.trim().split("\\s+");if(parts.length<2)return score;try{int value=Integer.parseInt(parts[1]);if(requestedFen.split(" ")[1].equals("b"))value=-value;if("mate".equals(parts[0]))return value>=0?"白方将杀":"黑方将杀";return String.format(java.util.Locale.US,"%+.2f",value/100.0);}catch(NumberFormatException e){return score;}}
    private void publishAnalysis(int g,String requested,List<CandidateData> output,String state){
        main.post(()->{
            if(isStale(g,requested))return;
            visibleCandidates=output;
            if(selectedSquare==null&&!output.isEmpty())try{evaluations.put(requested,Double.parseDouble(output.get(0).score));updateChart();}catch(NumberFormatException ignored){}
            showCandidates(output);
            refreshArrows();
            status.setText(state);
        });
    }
    private void showCandidates(List<CandidateData> output){
        candidates.removeAllViews();
        String review=history.isEmpty()?"开局建议：先看候选，再选择计划。":"上一步："+notations.get(notations.size()-1);
        if(!history.isEmpty()&&evaluations.containsKey(fen)&&evaluations.containsKey(fens.get(fens.size()-2))){
            String before=fens.get(fens.size()-2);double loss=(evaluations.get(before)-evaluations.get(fen))*(before.split(" ")[1].equals("w")?1:-1);
            review+=" · "+(loss>1.5?"失误":loss>.6?"欠佳":loss>.2?"可改进":"稳健")+"（评分参考）";
        }
        candidates.addView(text(review,13,Color.DKGRAY));
        TextView hint=text(selectedSquare==null?"全局候选着法":selectedSquare.toUpperCase(Locale.ROOT)+" 棋子的候选着法",13,Color.rgb(27,88,65));
        hint.setPadding(0,dp(6),0,dp(6));candidates.addView(hint);
        if(selectedSquare!=null)candidates.addView(button("返回全局",v->onSelectionCleared()));
        if(output.isEmpty())candidates.addView(text("暂无合法候选",14,Color.GRAY));
        for(CandidateData item:output){
            LinearLayout card=column();card.setPadding(dp(10),dp(8),dp(10),dp(8));card.setBackground(surface(Color.rgb(237,242,235),10));
            TextView title=text(item.rank+"  "+item.san+"   "+item.score+"  d"+item.depth,15,Color.rgb(25,45,34));title.setTypeface(null,Typeface.BOLD);card.addView(title);
            TextView continuation=text(item.continuation,12,Color.GRAY);continuation.setMaxLines(2);card.addView(continuation);
            LinearLayout actions=new LinearLayout(this);
            Button preview=button("演示",v->board.startVariation(item.frames,fen));preview.setEnabled(!item.frames.isEmpty()&&!editing);
            Button play=button(item.rank==1?"走最佳":"走这步",v->{board.stopVariationPreview();onMove(item.move);});play.setEnabled(isHumanTurn()&&!editing);
            actions.addView(preview,new LinearLayout.LayoutParams(0,dp(40),1));actions.addView(play,new LinearLayout.LayoutParams(0,dp(40),1));card.addView(actions);
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(7);candidates.addView(card,p);
        }
        candidates.addView(button("停止演示",v->board.stopVariationPreview()));
    }
    @Override public void onSelectionCleared(){selectedSquare=null;board.clearSelection();analyze();}
    @Override public void onPromotion(String move){final String requested=fen;new AlertDialog.Builder(this).setTitle("兵升变").setItems(new String[]{"后","车","象","马"},(d,w)->{if(requested.equals(fen))onMove(move+"qrbn".charAt(w));}).setNegativeButton("取消",null).show();}
    @Override public void onEditorSquare(String square){if(editing)showEditorPieceDialog(square);}
    private void showEditorPieceDialog(String square){new AlertDialog.Builder(this).setTitle("摆棋 · "+square.toUpperCase(Locale.ROOT)).setItems(new String[]{"删除","白王","白后","白车","白象","白马","白兵","黑王","黑后","黑车","黑象","黑马","黑兵"},(d,w)->board.editSquare(square,w==0?(char)0:"KQRBNPkqrbnp".charAt(w-1))).setNegativeButton("取消",null).show();}
    private void updateChart(){List<Double> values=new ArrayList<>();for(String position:fens){Double value=evaluations.get(position);if(value!=null)values.add(value);}chart.setValues(values);}
    private void refreshArrows(){List<String> arrows=new ArrayList<>();for(CandidateData c:visibleCandidates)arrows.add(c.move);board.setCandidateMoves(arrows,showBest);
        bestButton.setBackground(surface(showBest?Color.rgb(27,88,65):Color.rgb(228,237,226),20));bestButton.setTextColor(showBest?Color.WHITE:Color.rgb(27,88,65));
        bestButton.setContentDescription(showBest?"隐藏候选箭头":"显示候选箭头");}
    private View recordControls(){
        LinearLayout box=column();moves=text("尚未行棋",13,Color.DKGRAY);box.addView(moves);
        LinearLayout row=new LinearLayout(this);row.addView(button("开局",v->goToPly(0)),new LinearLayout.LayoutParams(0,dp(44),1));row.addView(button("载入",v->showLoadDialog()),new LinearLayout.LayoutParams(0,dp(44),1));row.addView(button("保存",v->saveGame()),new LinearLayout.LayoutParams(0,dp(44),1));box.addView(row);
        box.addView(button("复制当前 FEN",v->{if(editing){notice("请先完成摆棋。");return;}((ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("FEN",fen));notice("FEN 已复制。");}));
        recordRows=column();box.addView(recordRows);return box;
    }
    private JSONObject snapshot()throws Exception{List<String> timeline=new ArrayList<>(history);timeline.addAll(redoHistory);return new JSONObject().put("initialFEN",fens.get(0)).put("moves",new JSONArray(timeline)).put("activePly",history.size()).put("title",recordName);}
    private void persistSession(){if(editing||preferences==null)return;try{preferences.edit().putString("session",snapshot().toString()).apply();}catch(Exception e){notice("自动保存失败。");}}
    private void saveGame(){
        if(editing){notice("请先完成摆棋。");return;}
        EditText title=new EditText(this);title.setSingleLine(true);title.setText(recordName.equals("新对局")?"棋局 "+java.text.DateFormat.getDateTimeInstance().format(new java.util.Date()):recordName);
        new AlertDialog.Builder(this).setTitle("保存到本机").setView(title).setPositiveButton("保存",(d,w)->{try{
            JSONArray old=new JSONArray(preferences.getString("saved","[]"));if(old.length()>=100){notice("已有 100 个存档，请先删除不需要的存档。");return;}
            String name=title.getText().toString().trim();recordName=name.isEmpty()?"未命名棋局":name;
            JSONArray all=new JSONArray();all.put(snapshot().put("savedAt",System.currentTimeMillis()));for(int i=0;i<old.length();i++)all.put(old.get(i));
            if(!preferences.edit().putString("saved",all.toString()).commit())throw new IllegalStateException("本机写入失败");
            update();notice("已保存："+recordName);
        }catch(Exception e){notice("保存失败："+e.getMessage());}}).setNegativeButton("取消",null).show();
    }
    private void showLoadDialog(){
        if(editing){notice("请先完成或取消摆棋。");return;}
        LinearLayout box=column();box.setPadding(dp(16),dp(8),dp(16),dp(8));
        box.addView(text("粘贴 FEN 局面",15,Color.DKGRAY));EditText input=new EditText(this);input.setText(fen);input.setMinLines(3);input.setTextSize(13);box.addView(input);
        ScrollView scroll=new ScrollView(this);scroll.addView(box);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("载入棋谱或局面").setView(scroll).setNegativeButton("完成",null).create();
        box.addView(button("载入此局面",v->{try{loadPosition(FenRules.validate(input.getText().toString()));dialog.dismiss();}catch(Exception e){input.setError(e.getMessage());}}));
        box.addView(text("本机存档（长按删除）",14,Color.rgb(27,88,65)));
        try{JSONArray records=new JSONArray(preferences.getString("saved","[]"));
            if(records.length()==0)box.addView(text("还没有保存的棋局。",13,Color.GRAY));
            for(int i=0;i<records.length();i++){
                JSONObject record=records.getJSONObject(i);final int index=i;
                Button entry=button(record.optString("title","棋局")+" · "+record.getJSONArray("moves").length()+" 半回合\n"+java.text.DateFormat.getDateTimeInstance().format(new java.util.Date(record.optLong("savedAt"))),v->{loadRecord(record);dialog.dismiss();});
                entry.setOnLongClickListener(v->{new AlertDialog.Builder(this).setTitle("删除这份存档？").setMessage(record.optString("title")).setPositiveButton("删除",(d,w)->{try{
                    JSONArray remaining=new JSONArray();for(int j=0;j<records.length();j++)if(j!=index)remaining.put(records.get(j));
                    if(!preferences.edit().putString("saved",remaining.toString()).commit())throw new IllegalStateException("本机写入失败");
                    dialog.dismiss();showLoadDialog();
                }catch(Exception e){notice(e.getMessage());}}).setNegativeButton("取消",null).show();return true;});box.addView(entry);
            }
        }catch(Exception e){box.addView(text("读取失败，原存档未删除。",13,Color.RED));}
        dialog.show();
    }
    private void loadPosition(String position)throws Exception{loadRecord(new JSONObject().put("initialFEN",FenRules.validate(position)).put("moves",new JSONArray()).put("title",position.equals(START)?"新对局":"载入局面"));}
    private void loadRecord(JSONObject record){
        if(!engineReady){notice("请等待引擎准备完成。");return;}
        final String start;final List<String> requestedMoves=new ArrayList<>();
        try{start=FenRules.validate(record.getString("initialFEN"));JSONArray saved=record.getJSONArray("moves");
            if(saved.length()>2000)throw new IllegalArgumentException("棋谱过长。");
            for(int i=0;i<saved.length();i++){String move=saved.getString(i);if(!move.matches("[a-h][1-8][a-h][1-8][qrbn]?"))throw new IllegalArgumentException("棋谱格式不正确。");requestedMoves.add(move);}
        }catch(Exception e){notice("无法载入："+e.getMessage());return;}
        final int g=generation.incrementAndGet();StockfishNative.stop();board.stopVariationPreview();status.setText("检查棋谱…");
        engine.execute(()->{try{
            List<String> positions=new ArrayList<>(),names=new ArrayList<>();String current=start;positions.add(current);
            for(String move:requestedMoves){if(g!=generation.get()||destroyed)return;
                if(!Arrays.asList(StockfishNative.legalMoves(current).split(" ")).contains(move))throw new IllegalArgumentException("非法着法："+move);
                names.add(StockfishNative.san(current,move));current=StockfishNative.applyMove(current,move);if(current.startsWith("error:"))throw new IllegalArgumentException(current);positions.add(current);
            }
            final int active=record.optInt("activePly",requestedMoves.size());if(active<0||active>requestedMoves.size())throw new IllegalArgumentException("棋谱浏览位置不正确。");
            final String finalFen=positions.get(active),finalStatus=StockfishNative.gameStatus(finalFen);
            main.post(()->{if(g!=generation.get()||destroyed)return;
                editing=false;computerMode=false;board.setEditor(false);modeButton.setText("模式：双人对弈");sideButton.setVisibility(View.GONE);levelButton.setVisibility(View.GONE);
                history.clear();history.addAll(requestedMoves.subList(0,active));notations.clear();notations.addAll(names.subList(0,active));fens.clear();fens.addAll(positions.subList(0,active+1));
                redoHistory.clear();redoHistory.addAll(requestedMoves.subList(active,requestedMoves.size()));redoNotations.clear();redoNotations.addAll(names.subList(active,names.size()));redoFens.clear();redoFens.addAll(positions.subList(active+1,positions.size()));selectedSquare=null;visibleCandidates.clear();evaluations.clear();chart.clear();
                fen=finalFen;gameStatus=finalStatus;recordName=record.optString("title","载入棋局");board.setFen(fen);board.clearSelection();
                board.setLastMove(history.isEmpty()?null:history.get(history.size()-1));update();refreshArrows();status.setText("棋谱已载入");analyze();
            });
        }catch(Exception|LinkageError e){reportError("载入失败，当前棋局未更改",e);}});
    }
    private void goToPly(int ply){if(editing||ply<0||ply>history.size()+redoHistory.size())return;generation.incrementAndGet();if(engineReady)StockfishNative.stop();while(history.size()>ply){redoHistory.add(0,history.remove(history.size()-1));redoNotations.add(0,notations.remove(notations.size()-1));redoFens.add(0,fens.remove(fens.size()-1));}while(history.size()<ply){history.add(redoHistory.remove(0));notations.add(redoNotations.remove(0));fens.add(redoFens.remove(0));}fen=fens.get(fens.size()-1);finishNavigation();}
    private void reportError(String title,Throwable e){android.util.Log.e("YisiChess",title,e);main.post(()->{if(!destroyed){status.setText(title);notice(title+"："+e.getMessage());}});}
    private void installInsets(){
        View page=((ViewGroup)findViewById(android.R.id.content)).getChildAt(0);
        int left=page.getPaddingLeft(),top=page.getPaddingTop(),right=page.getPaddingRight(),bottom=page.getPaddingBottom();
        page.setOnApplyWindowInsetsListener((view,insets)->{
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets safe=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());view.setPadding(left+safe.left,top+safe.top,right+safe.right,bottom+safe.bottom);}
            else view.setPadding(left+insets.getSystemWindowInsetLeft(),top+insets.getSystemWindowInsetTop(),right+insets.getSystemWindowInsetRight(),bottom+insets.getSystemWindowInsetBottom());
            return insets;
        });
    }
    private void notice(String text){Toast.makeText(this,text==null?"操作失败":text,Toast.LENGTH_LONG).show();}
    private void update(){
        turn.setText(editing?"摆棋中":!"ongoing".equals(gameStatus)?(gameStatus.equals("checkmate")?"对局结束":"和棋"):(fen.split(" ")[1].equals("w")?"白方走棋":"黑方走棋"));
        undoButton.setEnabled(!editing&&!history.isEmpty());redoButton.setEnabled(!editing&&!redoHistory.isEmpty());
        moves.setText(recordName+" · "+history.size()+" / "+(history.size()+redoHistory.size())+" 半回合");recordRows.removeAllViews();
        List<String> timeline=new ArrayList<>(notations);timeline.addAll(redoNotations);
        if(timeline.isEmpty())recordRows.addView(text("尚未行棋。可载入 FEN 或本机存档。",13,Color.GRAY));
        for(int i=0;i<timeline.size();i+=2){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(text((i/2+1)+".",13,Color.GRAY),new LinearLayout.LayoutParams(dp(28),-2));
            for(int j=i;j<Math.min(i+2,timeline.size());j++){final int ply=j+1;Button move=button(timeline.get(j),v->goToPly(ply));if(ply==history.size()){move.setTextColor(Color.WHITE);move.setBackground(surface(Color.rgb(27,88,65),10));}row.addView(move,new LinearLayout.LayoutParams(0,dp(42),1));}
            recordRows.addView(row);
        }
        refreshArrows();updateChart();if(engineReady)persistSession();
    }
    private void showGameOutcome(){boolean mate="checkmate".equals(gameStatus);String winner=fen.split(" ")[1].equals("w")?"黑方":"白方";new AlertDialog.Builder(this).setTitle(mate?winner+"获胜":"和棋").setMessage(mate?"将死。"+winner+"赢得本局。":"逼和：行棋方没有合法着法，但王未被将军。").setPositiveButton("再来一局",(dialog,which)->reset()).setNegativeButton("查看棋局",null).show();}
    private void undo(){if(editing||history.isEmpty())return;goToPly(history.size()-1);}
    private void redo(){if(editing||redoHistory.isEmpty())return;generation.incrementAndGet();if(engineReady)StockfishNative.stop();String move=redoHistory.remove(0),notation=redoNotations.remove(0);fen=redoFens.remove(0);history.add(move);notations.add(notation);fens.add(fen);finishNavigation();}
    private void finishNavigation(){
        selectedSquare=null;visibleCandidates.clear();board.setFen(fen);board.clearSelection();board.setLastMove(history.isEmpty()?null:history.get(history.size()-1));
        final int g=generation.get();final String requested=fen;
        if(!engineReady){update();return;}
        engine.execute(()->{try{String result=StockfishNative.gameStatus(requested);main.post(()->{if(isStale(g,requested))return;gameStatus=result;update();analyze();});}
            catch(Exception|LinkageError e){reportError("棋谱导航失败",e);}});
    }
    private void reset(){if(!engineReady)return;try{loadPosition(START);}catch(Exception e){notice(e.getMessage());}}
    private View chartModule(){chart=new EvaluationChartView(this);return module("局势图 · 白方视角",chart,false);}
    @Override protected void onDestroy(){destroyed=true;generation.incrementAndGet();if(board!=null)board.stopVariationPreview();if(engineReady)StockfishNative.stop();engine.shutdownNow();super.onDestroy();}
    private GradientDrawable surface(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}private TextView text(String s,int z,int c){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setTextColor(c);return v;}
    private Button button(String s,View.OnClickListener l){Button b=new Button(this);b.setText(s);b.setTextSize(13);b.setAllCaps(false);b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(0);b.setMinimumHeight(0);b.setPadding(dp(5),dp(4),dp(5),dp(4));b.setTextColor(Color.rgb(27,88,65));b.setBackground(surface(Color.rgb(228,237,226),10));b.setOnClickListener(l);return b;}
    private int dp(int x){return(int)(x*getResources().getDisplayMetrics().density+.5f);}
}
