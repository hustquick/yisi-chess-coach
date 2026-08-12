package com.yisi.chesscoach;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class EvaluationChartView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Double> values=new ArrayList<>();
    EvaluationChartView(Context context){super(context);setContentDescription("白方视角局势评分曲线，正分表示白方占优");}
    void addValue(double value){values.add(value);invalidate();}
    void clear(){values.clear();invalidate();}
    @Override protected void onMeasure(int w,int h){setMeasuredDimension(MeasureSpec.getSize(w),resolveSize((int)(170*getResources().getDisplayMetrics().density),h));}
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);float d=getResources().getDisplayMetrics().density,left=38*d,right=getWidth()-8*d,top=10*d,bottom=getHeight()-18*d,mid=(top+bottom)/2;double largest=0;for(double value:values)if(Double.isFinite(value)&&Math.abs(value)<100)largest=Math.max(largest,Math.abs(value));double range=Math.max(3,Math.ceil(largest));canvas.drawColor(Color.rgb(250,248,243));paint.setTextSize(10*d);paint.setTextAlign(Paint.Align.RIGHT);for(double value:new double[]{range,range/2,0,-range/2,-range}){float y=(float)(mid-value/range*(mid-top));paint.setColor(value==0?0x66383f3b:0x22383f3b);paint.setStrokeWidth(d);canvas.drawLine(left,y,right,y,paint);paint.setColor(Color.GRAY);canvas.drawText(axisLabel(value),left-6*d,y+3*d,paint);}if(values.size()>1){paint.setColor(Color.rgb(27,110,76));paint.setStrokeWidth(3*d);paint.setStyle(Paint.Style.STROKE);Path path=new Path();for(int i=0;i<values.size();i++){float x=left+i*(right-left)/(values.size()-1),y=(float)(mid-Math.max(-range,Math.min(range,values.get(i)))/range*(mid-top));if(i==0)path.moveTo(x,y);else path.lineTo(x,y);}canvas.drawPath(path,paint);paint.setStyle(Paint.Style.FILL);}paint.setTextAlign(Paint.Align.LEFT);paint.setColor(Color.GRAY);canvas.drawText("白方优势",left,top+9*d,paint);canvas.drawText("黑方优势",left,bottom,paint);}
    private String axisLabel(double value){if(value==0)return "0";String number=Math.rint(value)==value?String.format(Locale.CHINA,"%.0f",Math.abs(value)):String.format(Locale.CHINA,"%.1f",Math.abs(value));return (value>0?"+":"-")+number;}
}
