package com.yisi.chesscoach;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.android.controller.ActivityController;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

/** UI/state tests use a deliberately tiny engine fixture, NOT a Stockfish strength test. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=34, qualifiers="w393dp-h800dp-port-mdpi", shadows=ChessUiTest.EngineFixture.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ChessUiTest {
    static final String AFTER_E4="rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1";
    static final String PROMOTION="7k/P7/8/8/8/8/8/7K w - - 0 1";
    @Implements(value=StockfishNative.class,isInAndroidSdk=false)
    public static class EngineFixture {
        @Implementation protected static void __staticInitializer__() {}
        @Implementation protected static String initialize(String f,int t,int h){return "ready";}
        @Implementation protected static void stop(){}
        @Implementation protected static String legalMoves(String f){return f.equals(MainActivity.START)?"e2e4 d2d4":f.equals(PROMOTION)?"a7a8q a7a8r a7a8b a7a8n":"";}
        @Implementation protected static String gameStatus(String f){return "ongoing";}
        @Implementation protected static String applyMove(String f,String m){if(f.equals(MainActivity.START)&&m.equals("e2e4"))return AFTER_E4;if(f.equals(PROMOTION)&&m.startsWith("a7a8"))return Character.toUpperCase(m.charAt(4))+"6k/8/8/8/8/8/8/7K b - - 0 1";return "error:fixture move not supported";}
        @Implementation protected static String san(String f,String m){return m.equals("e2e4")?"e4":m;}
        @Implementation protected static String analyze(String f,int d,int count,String restriction){if(!f.equals(MainActivity.START))return "{\"lines\":[]}";return "{\"lines\":[{\"pv\":\"e2e4\",\"depth\":"+d+",\"score\":\"cp 25\"}]}";}
    }
    private ActivityController<MainActivity> start()throws Exception{
        RuntimeEnvironment.getApplication().getSharedPreferences("chess_records",Context.MODE_PRIVATE).edit().clear().commit();
        ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup().visible();flush(c.get());return c;
    }
    private void flush(MainActivity activity)throws Exception{
        ExecutorService queue=(ExecutorService)field(activity,"engine");
        for(int i=0;i<4;i++){queue.submit(()->{}).get(10,TimeUnit.SECONDS);Shadows.shadowOf(Looper.getMainLooper()).idle();}
    }
    private Object field(Object object,String name)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private void call(MainActivity a,String method,Class<?> type,Object argument)throws Exception{Method m=MainActivity.class.getDeclaredMethod(method,type);m.setAccessible(true);m.invoke(a,argument);flush(a);}
    private TextView find(View root,String label){
        if(root instanceof TextView&&((TextView)root).getText().toString().equals(label))return(TextView)root;
        if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){TextView result=find(((ViewGroup)root).getChildAt(i),label);if(result!=null)return result;}
        return null;
    }
    @Test public void saveLoadNavigateAndRestoreWholeTimeline()throws Exception{
        ActivityController<MainActivity> c=start();MainActivity a=c.get();a.onMove("e2e4");flush(a);assertEquals(AFTER_E4,field(a,"fen"));
        call(a,"goToPly",int.class,0);assertEquals(MainActivity.START,field(a,"fen"));
        Method snapshot=MainActivity.class.getDeclaredMethod("snapshot");snapshot.setAccessible(true);JSONObject saved=(JSONObject)snapshot.invoke(a);
        assertEquals(1,saved.getJSONArray("moves").length());assertEquals(0,saved.getInt("activePly"));
        call(a,"loadRecord",JSONObject.class,saved);assertEquals(MainActivity.START,field(a,"fen"));
        Button forward=(Button)field(a,"redoButton");assertTrue(forward.isEnabled());forward.performClick();flush(a);assertEquals(AFTER_E4,field(a,"fen"));
        c.pause().stop().destroy();
        ActivityController<MainActivity> restored=Robolectric.buildActivity(MainActivity.class).setup();flush(restored.get());
        assertEquals(AFTER_E4,field(restored.get(),"fen"));restored.pause().stop().destroy();
    }
    @Test public void saveButtonWritesAndLoadDialogListsRecord()throws Exception{
        ActivityController<MainActivity> c=start();MainActivity a=c.get();a.onMove("e2e4");flush(a);
        TextView module=find(a.getWindow().getDecorView(),"▾ 棋谱与存档");assertNotNull(module);module.performClick();
        TextView save=find(a.getWindow().getDecorView(),"保存");assertNotNull(save);save.performClick();
        AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();flush(a);
        JSONArray records=new JSONArray(a.getSharedPreferences("chess_records",Context.MODE_PRIVATE).getString("saved","[]"));assertEquals(1,records.length());
        find(a.getWindow().getDecorView(),"载入").performClick();assertNotNull(ShadowAlertDialog.getLatestAlertDialog());c.pause().stop().destroy();
    }
    @Test public void invalidFenAndIllegalRecordKeepCurrentPosition()throws Exception{
        ActivityController<MainActivity> c=start();MainActivity a=c.get();a.onMove("e2e4");flush(a);
        call(a,"loadRecord",JSONObject.class,new JSONObject().put("initialFEN",MainActivity.START).put("moves",new JSONArray(Arrays.asList("e2e5"))));
        assertEquals(AFTER_E4,field(a,"fen"));
        call(a,"loadRecord",JSONObject.class,new JSONObject().put("initialFEN","8/8/8/8/8/8/8/8 w - - 0 1").put("moves",new JSONArray()));
        assertEquals(AFTER_E4,field(a,"fen"));c.pause().stop().destroy();
    }
    @Test public void promotionDialogOffersFourChoicesAndCanUnderpromote()throws Exception{
        ActivityController<MainActivity> c=start();MainActivity a=c.get();call(a,"loadPosition",String.class,PROMOTION);a.onPromotion("a7a8");
        AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();assertEquals(4,dialog.getListView().getAdapter().getCount());
        dialog.getListView().performItemClick(null,3,3);flush(a);assertTrue(field(a,"fen").toString().startsWith("N6k"));c.pause().stop().destroy();
    }
    @Test public void firstViewportContainsBoardAndArrowsActuallyRender()throws Exception{
        ActivityController<MainActivity> c=start();MainActivity a=c.get();View root=a.getWindow().getDecorView();
        View page=((ViewGroup)a.findViewById(android.R.id.content)).getChildAt(0);
        page.dispatchApplyWindowInsets(new android.view.WindowInsets.Builder().setInsets(android.view.WindowInsets.Type.systemBars(),android.graphics.Insets.of(0,24,0,24)).build());
        assertTrue(page.getPaddingTop()>=24);
        root.measure(View.MeasureSpec.makeMeasureSpec(393,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY));root.layout(0,0,393,800);
        ChessBoardView board=(ChessBoardView)field(a,"board");int[] coordinates=new int[2];board.getLocationInWindow(coordinates);
        assertTrue("Board clipped below viewport",coordinates[1]+board.getHeight()<=800);
        assertEquals(board.getWidth(),board.getHeight());
        Bitmap enabled=Bitmap.createBitmap(board.getWidth(),board.getHeight(),Bitmap.Config.ARGB_8888);board.draw(new Canvas(enabled));
        ((Button)field(a,"bestButton")).performClick();
        Bitmap disabled=Bitmap.createBitmap(board.getWidth(),board.getHeight(),Bitmap.Config.ARGB_8888);board.draw(new Canvas(disabled));
        assertFalse("Arrow toggle changed no pixels",enabled.sameAs(disabled));
        ((Button)field(a,"bestButton")).performClick();
        Bitmap image=Bitmap.createBitmap(393,800,Bitmap.Config.ARGB_8888);root.draw(new Canvas(image));
        File directory=new File("build/ui-previews");assertTrue(directory.isDirectory()||directory.mkdirs());
        try(FileOutputStream out=new FileOutputStream(new File(directory,"chess-portrait.png"))){assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,out));}
        c.pause().stop().destroy();
    }
    @Test @Config(qualifiers="w800dp-h393dp-land-mdpi") public void landscapeBoardFitsAndRenders()throws Exception{
        ActivityController<MainActivity> c=start();MainActivity a=c.get();View root=a.getWindow().getDecorView();
        root.measure(View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(393,View.MeasureSpec.EXACTLY));root.layout(0,0,800,393);
        ChessBoardView board=(ChessBoardView)field(a,"board");int[] location=new int[2];board.getLocationInWindow(location);
        assertTrue(location[1]+board.getHeight()<=393);
        Bitmap image=Bitmap.createBitmap(800,393,Bitmap.Config.ARGB_8888);root.draw(new Canvas(image));
        File directory=new File("build/ui-previews");assertTrue(directory.isDirectory()||directory.mkdirs());
        try(FileOutputStream out=new FileOutputStream(new File(directory,"chess-landscape.png"))){assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,out));}
        c.pause().stop().destroy();
    }
    @Test public void selectedPieceAnalysisAndEditorDoNotChangeLiveGame()throws Exception{
        ActivityController<MainActivity> c=start();MainActivity a=c.get();a.onSquareSelected("e2");flush(a);
        assertEquals("e2",field(a,"selectedSquare"));assertNotNull(find(a.getWindow().getDecorView(),"返回全局"));
        a.onSelectionCleared();flush(a);assertNull(field(a,"selectedSquare"));
        ChessBoardView board=(ChessBoardView)field(a,"board");board.setEditor(true);board.editSquare("a2",(char)0);
        assertFalse(board.editorFen(true).equals(MainActivity.START));assertEquals(MainActivity.START,field(a,"fen"));
        board.setEditor(false);board.setFen(MainActivity.START);assertEquals(MainActivity.START,field(a,"fen"));c.pause().stop().destroy();
    }
}
