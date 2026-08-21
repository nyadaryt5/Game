package com.sectmaster.game;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Native, dependency-free game surface. All visuals and input use Android Canvas/View APIs. */
public final class GameView extends View {
    public interface Host { void requestReset(); }
    private static final float VW = 1280f, VH = 720f;
    private static final int BG = Color.rgb(12, 24, 20), PANEL = Color.rgb(22, 42, 34);
    private static final int PANEL_2 = Color.rgb(29, 54, 44), GOLD = Color.rgb(223, 190, 112);
    private static final int JADE = Color.rgb(83, 199, 156), TEXT = Color.rgb(235, 239, 226), MUTED = Color.rgb(161, 177, 162);
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final List<Hit> hits = new ArrayList<>();
    private final Host host;
    private GameState state;
    private int tab, selectedBuilding = -1, selectedDisciple;
    private GameState.Building selectedPlaced;
    private float scale = 1, offsetX, offsetY;
    private long previousFrame;
    private String message;
    private long messageUntil;

    private static final class Hit {
        RectF rect; Runnable action; String label;
        Hit(float l,float t,float r,float b,String label,Runnable action){rect=new RectF(l,t,r,b);this.label=label;this.action=action;}
    }

    public GameView(Context context, GameState state, Host host) {
        super(context); this.state = state; this.host = host;
        p.setTypeface(Typeface.create("sans", Typeface.NORMAL));
        setBackgroundColor(BG); setFocusable(true); setContentDescription("Sect Master game board");
        previousFrame = SystemClock.elapsedRealtime();
    }

    public GameState getState() { return state; }
    public void replaceState(GameState value) { state=value; tab=0; selectedBuilding=-1; selectedPlaced=null; invalidate(); }
    public void showMessage(String text) { message=text; messageUntil=SystemClock.elapsedRealtime()+3200; invalidate(); }

    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh) {
        scale=Math.min(w/VW,h/VH); offsetX=(w-VW*scale)/2; offsetY=(h-VH*scale)/2;
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long now=SystemClock.elapsedRealtime();
        float dt=Math.min(0.1f,(now-previousFrame)/1000f); previousFrame=now;
        if(state.update(dt)) state.save(getContext());
        canvas.save(); canvas.translate(offsetX,offsetY); canvas.scale(scale,scale);
        hits.clear(); drawBackground(canvas); drawTop(canvas); drawTabs(canvas);
        switch(tab){case 0:drawSect(canvas);break;case 1:drawDisciples(canvas);break;case 2:drawMissions(canvas);break;case 3:drawResearch(canvas);break;case 4:drawInventory(canvas);break;default:drawSettings(canvas);}
        if(message!=null && now<messageUntil) drawToast(canvas,message); else if(now>=messageUntil) message=null;
        canvas.restore();
        postInvalidateOnAnimation();
    }

    private void drawBackground(Canvas c) {
        c.drawColor(BG); p.setColor(Color.rgb(15,31,25));
        for(int i=0;i<12;i++) c.drawCircle(70+i*121,120+(i%3)*190,70,p);
    }

    private void drawTop(Canvas c) {
        fill(c,0,0,VW,82,Color.rgb(9,19,16));
        text(c,"SECT MASTER",30,34,25,GOLD,true); text(c,"Evergreen Sect",30,64,18,TEXT,false);
        resource(c,260,"SPIRIT STONES",state.spiritStones); resource(c,455,"HERBS",state.herbs);
        resource(c,610,"ORE",state.ore); resource(c,745,"PILLS",state.pills);
        text(c,"POWER",900,28,13,MUTED,true); text(c,String.valueOf(state.power()),900,59,25,JADE,true);
        text(c,"DAY "+state.day,1015,31,15,MUTED,true); text(c,"RENOWN "+state.reputation,1015,59,17,TEXT,true);
        button(c,1160,17,1250,66,state.paused?"RESUME":"PAUSE",()->state.paused=!state.paused);
    }

    private void resource(Canvas c,float x,String label,float value){text(c,label,x,28,12,MUTED,true);text(c,format(value),x,59,23,TEXT,true);}
    private String format(float v){if(v>=10000)return String.format(Locale.US,"%.1fK",v/1000);return String.format(Locale.US,v<100?"%.1f":"%.0f",v);}

    private void drawTabs(Canvas c) {
        String[] names={"SECT","DISCIPLES","EXPEDITIONS","RESEARCH","STORES","SETTINGS"};
        float w=VW/names.length;
        for(int i=0;i<names.length;i++){ final int n=i; if(i==tab)fill(c,i*w,82,(i+1)*w,133,PANEL_2); textCenter(c,names[i],i*w,(i+1)*w,114,15,i==tab?GOLD:MUTED,true); hits.add(new Hit(i*w,82,(i+1)*w,133,names[i],()->{tab=n;selectedPlaced=null;})); }
    }

    private void drawSect(Canvas c) {
        panel(c,18,150,918,700); text(c,"MOUNTAIN SANCTUARY",40,182,18,GOLD,true);
        float gx=48,gy=205,tw=98,th=84;
        for(int y=0;y<5;y++)for(int x=0;x<8;x++){float l=gx+x*tw,t=gy+y*th;fillStroke(c,l,t,l+86,t+72,Color.rgb(20,48,38),Color.rgb(43,78,62),2);final int fx=x,fy=y;hits.add(new Hit(l,t,l+86,t+72,"Build tile",()->tileTapped(fx,fy)));}
        for(GameState.Building b:state.buildings) drawBuilding(c,b,gx+b.x*tw,gy+b.y*th);
        panel(c,938,150,1262,700); text(c,"BUILDINGS",960,183,18,GOLD,true);
        for(int i=0;i<GameState.BUILDING_NAMES.length;i++){final int type=i;float y=202+i*61;boolean sel=selectedBuilding==i;fillStroke(c,956,y,1245,y+50,sel?Color.rgb(48,83,65):PANEL_2,sel?GOLD:Color.rgb(48,75,62),2);text(c,GameState.BUILDING_NAMES[i],970,y+22,15,TEXT,true);text(c,GameState.BUILDING_COSTS[i]+" stones",970,y+41,12,MUTED,false);hits.add(new Hit(956,y,1245,y+50,"Select "+GameState.BUILDING_NAMES[i],()->{selectedBuilding=type;selectedPlaced=null;}));}
        if(selectedPlaced!=null){text(c,"Selected: "+buildingName(selectedPlaced),960,586,14,TEXT,true);button(c,956,604,1245,652,"UPGRADE TO LEVEL "+(selectedPlaced.level+1),()->notifyResult(state.upgrade(selectedPlaced)));}
        else { text(c,selectedBuilding<0?"Select a building, then tap a tile.":"Tap an empty tile to construct.",960,592,14,MUTED,false); }
    }

    private void tileTapped(int x,int y){
        for(GameState.Building b:state.buildings)if(b.x==x&&b.y==y){selectedPlaced=b;selectedBuilding=-1;return;}
        String result=state.build(selectedBuilding,x,y);notifyResult(result);if(result==null)selectedBuilding=-1;
    }

    private String buildingName(GameState.Building b){return b.type<0?"Main Hall":GameState.BUILDING_NAMES[b.type];}
    private void drawBuilding(Canvas c,GameState.Building b,float x,float y){
        int color=b.type<0?GOLD:new int[]{Color.rgb(98,159,142),Color.rgb(79,139,85),Color.rgb(109,122,136),Color.rgb(159,91,72),Color.rgb(137,112,80),Color.rgb(91,121,153)}[b.type];
        fill(c,x+6,y+22,x+80,y+65,Color.rgb(31,37,31));p.setColor(color);path.reset();path.moveTo(x+3,y+27);path.lineTo(x+43,y+5);path.lineTo(x+83,y+27);path.close();c.drawPath(path,p);fill(c,x+16,y+28,x+70,y+61,color);textCenter(c,b.type<0?"HALL":shortName(b.type),x,x+86,y+49,11,Color.rgb(11,24,19),true);text(c,"L"+b.level,x+63,y+17,11,TEXT,true);
    }
    private String shortName(int t){return new String[]{"MEDITATE","GARDEN","MINE","ALCHEMY","TRAIN","ARRAY"}[t];}

    private void drawDisciples(Canvas c){
        panel(c,18,150,850,700);text(c,"DISCIPLE ROSTER",40,184,19,GOLD,true);
        int cols=3;for(int i=0;i<state.disciples.size();i++){final int ix=i;float x=40+(i%cols)*260,y=205+(i/cols)*105;GameState.Disciple d=state.disciples.get(i);boolean sel=i==selectedDisciple;fillStroke(c,x,y,x+238,y+88,sel?Color.rgb(42,75,59):PANEL_2,sel?GOLD:Color.rgb(45,72,59),2);text(c,d.name,x+18,y+29,18,TEXT,true);text(c,"Cultivation level "+d.level,x+18,y+53,13,MUTED,false);float need=50+d.level*20;progress(c,x+18,y+65,200,8,d.xp/need);hits.add(new Hit(x,y,x+238,y+88,d.name,()->selectedDisciple=ix));}
        panel(c,870,150,1262,700);text(c,"TRAINING",894,184,19,GOLD,true);GameState.Disciple d=state.disciples.get(Math.min(selectedDisciple,state.disciples.size()-1));text(c,d.name,894,235,28,TEXT,true);text(c,"Power  "+d.power(),894,270,16,JADE,true);text(c,"Level  "+d.level,894,298,15,MUTED,false);button(c,894,330,1238,386,"TRAIN  ·  "+(20+d.level*12)+" STONES",()->notifyResult(state.train(d)));int recruit=100+state.disciples.size()*35;button(c,894,410,1238,466,"RECRUIT  ·  "+recruit+" STONES",()->notifyResult(state.recruit()));text(c,"Training is immediate. Meditation Halls",894,510,13,MUTED,false);text(c,"also grant experience over time.",894,532,13,MUTED,false);
    }

    private void drawMissions(Canvas c){
        panel(c,18,150,1244,700);text(c,"EXPEDITIONS",42,188,21,GOLD,true);text(c,"Send the entire available roster. Outcomes use your total sect power.",42,216,14,MUTED,false);
        String[] title={"Gathering Trail","Bandit Stronghold","Ancient Rift"};String[] desc={"Recommended power 25 · Safe supply run","Recommended power 65 · Moderate danger","Recommended power 125 · Severe danger"};String[] reward={"65 stones + materials","145 stones + materials","300 stones + rare materials"};
        for(int i=0;i<3;i++){final int difficulty=i;float x=42+i*394;panel(c,x,250,x+368,565);text(c,title[i],x+22,291,22,TEXT,true);text(c,desc[i],x+22,326,13,MUTED,false);text(c,"REWARD",x+22,385,12,GOLD,true);text(c,reward[i],x+22,414,16,TEXT,false);text(c,"Estimated chance",x+22,467,12,MUTED,false);int req=new int[]{25,65,125}[i];int chance=Math.max(15,Math.min(95,55+(state.power()-req)/2));progress(c,x+22,486,260,13,chance/100f);text(c,chance+"%",x+296,498,14,JADE,true);button(c,x+22,520,x+346,574,"BEGIN EXPEDITION",()->showMessage(state.mission(difficulty)));}
        text(c,"Completed: "+state.missionsDone+"     Victories: "+state.battlesWon,42,640,17,TEXT,true);
    }

    private void drawResearch(Canvas c){
        panel(c,18,150,1244,700);text(c,"SCRIPTURE LIBRARY",42,188,21,GOLD,true);text(c,"Permanent teachings improve every disciple and unlock future strength.",42,218,14,MUTED,false);
        float cx=350,cy=385;for(int i=0;i<8;i++){double a=-Math.PI/2+i*Math.PI/4;float x=cx+(float)Math.cos(a)*190,y=cy+(float)Math.sin(a)*190;boolean done=i<state.researchLevel;p.setColor(done?JADE:Color.rgb(55,76,66));p.setStrokeWidth(5);c.drawLine(cx,cy,x,y,p);c.drawCircle(x,y,38,p);textCenter(c,""+(i+1),x-38,x+38,y+6,16,done?Color.rgb(10,30,23):MUTED,true);}
        panel(c,660,245,1195,570);text(c,"Path of Ascension",695,291,25,TEXT,true);text(c,"Research stage "+state.researchLevel+" of 8",695,330,16,JADE,true);text(c,"Each stage grants +3 sect power and",695,372,14,MUTED,false);text(c,"improves the results of focused training.",695,396,14,MUTED,false);int cost=120+state.researchLevel*90;button(c,695,450,1160,512,state.researchLevel>=8?"RESEARCH COMPLETE":"STUDY  ·  "+cost+" STONES",()->notifyResult(state.research()));
    }

    private void drawInventory(Canvas c){
        panel(c,18,150,1244,700);text(c,"SECT STORES",42,188,21,GOLD,true);
        storeCard(c,42,225,"Spirit Stones",state.spiritStones,"Primary construction currency",GOLD);storeCard(c,440,225,"Medicinal Herbs",state.herbs,"Grown in Herb Gardens",JADE);storeCard(c,838,225,"Spirit Ore",state.ore,"Extracted by Spirit Mines",Color.rgb(157,171,181));storeCard(c,42,410,"Cultivation Pills",state.pills,"Brewed in the Alchemy Hall",Color.rgb(194,117,104));
        panel(c,440,410,1206,592);text(c,"ALCHEMY",466,450,17,GOLD,true);text(c,"Convert 10 herbs into cultivation pills. Higher-level Alchemy Halls",466,482,14,MUTED,false);text(c,"increase every batch.",466,506,14,MUTED,false);button(c,466,532,1178,580,"BREW PILLS  ·  10 HERBS",()->notifyResult(state.brew()));
    }
    private void storeCard(Canvas c,float x,float y,String name,float amount,String note,int color){panel(c,x,y,x+368,y+150);p.setColor(color);c.drawCircle(x+38,y+42,18,p);text(c,name,x+70,y+44,18,TEXT,true);text(c,format(amount),x+24,y+96,31,color,true);text(c,note,x+24,y+128,13,MUTED,false);}

    private void drawSettings(Canvas c){
        panel(c,18,150,1244,700);text(c,"SETTINGS & SAVE",42,190,21,GOLD,true);panel(c,42,225,610,560);text(c,"Native Android edition",70,270,23,TEXT,true);text(c,"Your progress is stored privately on this device.",70,308,14,MUTED,false);text(c,"The game auto-saves every 20 seconds and whenever",70,333,14,MUTED,false);text(c,"the app moves to the background.",70,358,14,MUTED,false);button(c,70,405,580,462,"SAVE NOW",()->{state.save(getContext());showMessage("Progress saved.");});
        panel(c,640,225,1206,560);text(c,"New beginning",670,270,23,TEXT,true);text(c,"Erase all buildings, disciples, and progress.",670,308,14,MUTED,false);text(c,"You will be asked to confirm this action.",670,333,14,MUTED,false);dangerButton(c,670,405,1176,462,"RESET ALL PROGRESS",host::requestReset);text(c,"Sect Master 2.0 · Android",42,650,13,MUTED,false);
    }

    private void notifyResult(String value){if(value==null)showMessage("Done.");else showMessage(value);}
    private void drawToast(Canvas c,String value){float w=Math.min(850,Math.max(300,value.length()*9+70));fillStroke(c,(VW-w)/2,632,(VW+w)/2,690,Color.rgb(35,63,51),GOLD,2);textCenter(c,value,(VW-w)/2,(VW+w)/2,668,16,TEXT,true);}
    private void panel(Canvas c,float l,float t,float r,float b){fillStroke(c,l,t,r,b,PANEL,Color.rgb(40,67,55),1);}
    private void fill(Canvas c,float l,float t,float r,float b,int color){p.setStyle(Paint.Style.FILL);p.setColor(color);c.drawRect(l,t,r,b,p);}
    private void fillStroke(Canvas c,float l,float t,float r,float b,int fill,int stroke,float sw){p.setStyle(Paint.Style.FILL);p.setColor(fill);c.drawRoundRect(new RectF(l,t,r,b),8,8,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(sw);p.setColor(stroke);c.drawRoundRect(new RectF(l,t,r,b),8,8,p);p.setStyle(Paint.Style.FILL);}
    private void text(Canvas c,String s,float x,float y,float size,int color,boolean bold){p.setTextSize(size);p.setColor(color);p.setTypeface(Typeface.create("sans",bold?Typeface.BOLD:Typeface.NORMAL));p.setTextAlign(Paint.Align.LEFT);c.drawText(s,x,y,p);}
    private void textCenter(Canvas c,String s,float l,float r,float y,float size,int color,boolean bold){p.setTextSize(size);p.setColor(color);p.setTypeface(Typeface.create("sans",bold?Typeface.BOLD:Typeface.NORMAL));p.setTextAlign(Paint.Align.CENTER);c.drawText(s,(l+r)/2,y,p);p.setTextAlign(Paint.Align.LEFT);}
    private void progress(Canvas c,float x,float y,float w,float h,float ratio){fill(c,x,y,x+w,y+h,Color.rgb(12,25,20));fill(c,x,y,x+w*Math.max(0,Math.min(1,ratio)),y+h,JADE);}
    private void button(Canvas c,float l,float t,float r,float b,String label,Runnable action){fillStroke(c,l,t,r,b,Color.rgb(39,91,70),JADE,2);textCenter(c,label,l,r,(t+b)/2+6,14,TEXT,true);hits.add(new Hit(l,t,r,b,label,action));}
    private void dangerButton(Canvas c,float l,float t,float r,float b,String label,Runnable action){fillStroke(c,l,t,r,b,Color.rgb(95,47,43),Color.rgb(205,108,94),2);textCenter(c,label,l,r,(t+b)/2+6,14,TEXT,true);hits.add(new Hit(l,t,r,b,label,action));}

    @Override public boolean onTouchEvent(MotionEvent e){
        if(e.getAction()!=MotionEvent.ACTION_UP)return true;
        float x=(e.getX()-offsetX)/scale,y=(e.getY()-offsetY)/scale;
        for(int i=hits.size()-1;i>=0;i--){Hit h=hits.get(i);if(h.rect.contains(x,y)){h.action.run();performClick();invalidate();return true;}}
        return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
}
