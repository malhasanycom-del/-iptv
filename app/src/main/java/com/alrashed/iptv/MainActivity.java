package com.alrashed.iptv;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.*;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private final int bg=Color.rgb(10,17,30), panel=Color.rgb(24,36,54), gold=Color.rgb(255,190,55);
    private EditText addressField, userField, passField, macField, searchField;
    private Spinner sourceSpinner;
    private TextView status, nowPlaying, empty;
    private ListView listView;
    private PlayerView playerView;
    private ExoPlayer player;
    private final ArrayList<Channel> allChannels=new ArrayList<>(), visibleChannels=new ArrayList<>();
    private final Set<String> favorites=new HashSet<>();
    private boolean onlyFavorites=false;
    private SharedPreferences prefs;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();

    static class Channel {
        String name,url,group,logo,id;
        Channel(String n,String u,String g,String l,String i){name=n;url=u;group=g;logo=l;id=i;}
        String key(){return (id==null||id.isEmpty()?url:id)+"|"+name;}
        @Override public String toString(){return name+(group==null||group.isEmpty()?"":"  ·  "+group);}
    }

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(bg); getWindow().setNavigationBarColor(bg);
        prefs=getSharedPreferences("alrashed",MODE_PRIVATE);
        favorites.addAll(prefs.getStringSet("favorites",new HashSet<>()));
        // Create the player before building the dashboard. The home page does not
        // create PlayerView, so attaching to playerView here caused a startup crash.
        player=new ExoPlayer.Builder(this).build();
        player.addListener(new Player.Listener(){
            @Override public void onPlayerError(PlaybackException error){
                if(status!=null)status.setText("تعذّر التشغيل: "+error.getErrorCodeName()+" — قد يكون الرابط منتهيًا أو الصيغة غير مدعومة");
            }
            @Override public void onPlaybackStateChanged(int state){
                if(status!=null&&prefs.getBoolean("show_status",true)){ if(state==Player.STATE_BUFFERING) status.setText("جارٍ تحميل البث…"); else if(state==Player.STATE_READY) status.setText("البث جاهز"); }
            }
        });
        buildUi();
    }

    @Override protected void onDestroy(){
        if(playerView!=null)playerView.setPlayer(null);
        if(player!=null){player.release();player=null;}
        worker.shutdownNow();
        super.onDestroy();
    }

    private LinearLayout pageContainer;
    private String currentPage="home";

    private void buildUi(){
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        LinearLayout shell=new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(bg);

        LinearLayout top=new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(18),dp(8),dp(18),dp(8));
        top.setBackgroundColor(Color.rgb(12,22,37));
        LinearLayout brand=new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        TextView brandTitle=text("آل راشد IPTV",22,gold,true);
        brand.addView(brandTitle);
        TextView brandSub=text("مشغّل التلفاز الذكي",11,Color.rgb(170,187,207),false);
        brand.addView(brandSub);
        top.addView(brand,new LinearLayout.LayoutParams(0,dp(56),1));
        TextView statusChip=text("●  جاهز",12,Color.rgb(88,220,159),true);
        top.addView(statusChip);
        shell.addView(top,match(dp(70)));

        HorizontalScrollView navScroll=new HorizontalScrollView(this);
        navScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout nav=new LinearLayout(this);
        nav.setPadding(dp(10),dp(6),dp(10),dp(6));
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.addView(navButton("الرئيسية","home"));
        nav.addView(navButton("البث المباشر","live"));
        nav.addView(navButton("المصادر","sources"));
        nav.addView(navButton("المفضلة","favorites"));
        nav.addView(navButton("الإعدادات","settings"));
        navScroll.addView(nav);
        navScroll.setBackgroundColor(Color.rgb(15,27,43));
        shell.addView(navScroll,match(dp(56)));

        pageContainer=new LinearLayout(this);
        pageContainer.setOrientation(LinearLayout.VERTICAL);
        pageContainer.setPadding(dp(14),dp(10),dp(14),dp(16));
        ScrollView pageScroll=new ScrollView(this);
        pageScroll.setFillViewport(true);
        pageScroll.addView(pageContainer);
        shell.addView(pageScroll,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(shell);
        showPage("home");
    }

    private Button navButton(String label,String page){
        Button b=new Button(this);
        b.setText(label);b.setTextSize(13);b.setAllCaps(false);
        b.setTextColor(page.equals(currentPage)?gold:Color.WHITE);
        b.setBackground(roundRect(Color.rgb(24,39,59),dp(12)));
        b.setPadding(dp(12),0,dp(12),0);
        b.setOnClickListener(v->showPage(page));
        return b;
    }

    private void showPage(String page){
        currentPage=page;
        if(pageContainer==null)return;
        pageContainer.removeAllViews();
        if("home".equals(page))buildHomePage();
        else if("sources".equals(page))buildSourcesPage();
        else if("live".equals(page)||"favorites".equals(page))buildLivePage("favorites".equals(page));
        else if("settings".equals(page))buildSettingsPage();
    }

    private void buildHomePage(){
        LinearLayout hero=card();
        hero.setPadding(dp(18),dp(18),dp(18),dp(18));
        hero.setBackground(roundRect(Color.rgb(20,48,72),dp(22)));
        TextView eyebrow=text("AL RASHED  /  IPTV",12,gold,true);
        hero.addView(eyebrow,match(dp(28)));
        TextView title=text("عالم المشاهدة،\nبأسلوبك أنت",28,Color.WHITE,true);
        title.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        hero.addView(title,match(dp(88)));
        TextView desc=text("قنواتك المباشرة في واجهة واحدة. أضف مصدرك، خصّص المشغّل، وابدأ المشاهدة.",14,Color.rgb(199,213,229),false);
        desc.setMaxLines(3);
        hero.addView(desc,match(dp(58)));
        Button go=button("▶   ابدأ المشاهدة");
        hero.addView(go,match(dp(50)));
        go.setOnClickListener(v->showPage(allChannels.isEmpty()?"sources":"live"));
        LinearLayout.LayoutParams hp=match(-2);hp.bottomMargin=dp(14);
        pageContainer.addView(hero,hp);

        LinearLayout stats=card();
        TextView statsTitle=text("مكتبتك",19,Color.WHITE,true);
        stats.addView(statsTitle,match(dp(34)));
        TextView count=text(allChannels.size()+" قناة محمّلة",14,Color.rgb(180,195,213),false);
        stats.addView(count,match(dp(30)));
        TextView favCount=text("★  "+favorites.size()+" قناة في المفضلة",14,gold,true);
        stats.addView(favCount,match(dp(30)));
        pageContainer.addView(stats,match(-2));

        TextView quickTitle=text("الوصول السريع",19,Color.WHITE,true);
        LinearLayout.LayoutParams qt=match(dp(42));qt.topMargin=dp(8);
        pageContainer.addView(quickTitle,qt);
        pageContainer.addView(homeAction("▦","إضافة مصدر","M3U / Xtream Codes / Stalker Portal",()->showPage("sources")),match(-2));
        pageContainer.addView(homeAction("▶","القنوات المباشرة","تصفح القنوات وابحث ضمن مجموعاتك",()->showPage("live")),match(-2));
        pageContainer.addView(homeAction("⚙","تخصيص التطبيق","التحكم بالصورة والتشغيل وسلوك المشغّل",()->showPage("settings")),match(-2));
    }

    private LinearLayout homeAction(String symbol,String title,String subtitle,Runnable action){
        LinearLayout row=card();row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);
        TextView ico=text(symbol,24,gold,true);ico.setGravity(Gravity.CENTER);
        row.addView(ico,new LinearLayout.LayoutParams(dp(48),dp(56)));
        LinearLayout words=new LinearLayout(this);words.setOrientation(LinearLayout.VERTICAL);words.setPadding(dp(10),0,dp(8),0);
        words.addView(text(title,16,Color.WHITE,true));
        words.addView(text(subtitle,12,Color.rgb(164,181,202),false));
        row.addView(words,new LinearLayout.LayoutParams(0,-2,1));
        row.addView(text("‹",24,gold,true));
        row.setOnClickListener(v->action.run());
        LinearLayout.LayoutParams p=match(-2);p.bottomMargin=dp(8);
        row.setFocusable(true);row.setClickable(true);
        return row;
    }

    private void buildSourcesPage(){
        TextView heading=text("مصادر البث",25,Color.WHITE,true);pageContainer.addView(heading,match(dp(44)));
        pageContainer.addView(text("أدخل بيانات اشتراك تملك حق استخدامه. يمكنك تبديل نوع المصدر من القائمة.",13,Color.rgb(171,188,207),false),match(dp(44)));
        LinearLayout sourceCard=card();pageContainer.addView(sourceCard,match(-2));
        sourceSpinner=new Spinner(this);
        ArrayAdapter<String> sources=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,new String[]{"قائمة M3U / M3U8","Xtream Codes","Stalker Portal"});
        sources.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);sourceSpinner.setAdapter(sources);styleSpinner(sourceSpinner);
        sourceCard.addView(text("نوع المصدر",13,Color.rgb(170,188,207),true),match(dp(28)));
        sourceCard.addView(sourceSpinner,match(dp(48)));
        addressField=field("رابط قائمة M3U أو عنوان السيرفر");sourceCard.addView(addressField,match(dp(50)));
        userField=field("اسم المستخدم");sourceCard.addView(userField,match(dp(48)));
        passField=field("كلمة المرور");passField.setInputType(129);sourceCard.addView(passField,match(dp(48)));
        macField=field("MAC المخصص لاشتراك Stalker");sourceCard.addView(macField,match(dp(48)));
        sourceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(AdapterView<?> p,View v,int pos,long id){
                userField.setVisibility(pos==1?View.VISIBLE:View.GONE);passField.setVisibility(pos==1?View.VISIBLE:View.GONE);
                macField.setVisibility(pos==2?View.VISIBLE:View.GONE);
                addressField.setHint(pos==0?"رابط قائمة M3U / M3U8":pos==1?"عنوان السيرفر مثل https://server:8080":"رابط Stalker Portal");
            }
            @Override public void onNothingSelected(AdapterView<?> p){}
        });
        Button load=button("＋   حفظ المصدر وتحميل القنوات");sourceCard.addView(load,match(dp(52)));load.setOnClickListener(v->loadSource());
        Button back=secondaryButton("العودة للرئيسية");sourceCard.addView(back,match(dp(46)));back.setOnClickListener(v->showPage("home"));
        status=text("لم يتم تحميل مصدر بعد.",13,Color.rgb(176,193,211),false);sourceCard.addView(status,match(-2));
    }

    private void buildLivePage(boolean favOnly){
        TextView heading=text(favOnly?"قنواتي المفضلة":"البث المباشر",25,Color.WHITE,true);
        pageContainer.addView(heading,match(dp(44)));
        LinearLayout playerCard=card();pageContainer.addView(playerCard,match(-2));
        nowPlaying=text("اختر قناة لبدء المشاهدة",16,Color.WHITE,true);playerCard.addView(nowPlaying,match(dp(38)));
        playerView=new PlayerView(this);playerView.setPlayer(player);playerView.setUseController(true);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS);
        playerView.setResizeMode(getResizeMode());
        playerView.setVisibility(View.VISIBLE);
        playerCard.addView(playerView,new LinearLayout.LayoutParams(-1,dp(230)));
        status=text("جاهز. حمّل مصدرًا من صفحة المصادر إذا كانت القائمة فارغة.",13,Color.rgb(174,191,210),false);
        playerCard.addView(status,match(-2));
        searchField=field("ابحث باسم القناة أو المجموعة…");pageContainer.addView(searchField,match(dp(50)));
        searchField.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int st,int c,int a){}
            public void onTextChanged(CharSequence s,int start,int before,int count){filterChannels();}
            public void afterTextChanged(Editable e){}
        });
        LinearLayout controls=new LinearLayout(this);controls.setGravity(Gravity.CENTER_VERTICAL);
        Button all=secondaryButton("كل القنوات");Button fav=secondaryButton("★ المفضلة");
        controls.addView(all,new LinearLayout.LayoutParams(0,dp(46),1));
        controls.addView(fav,new LinearLayout.LayoutParams(0,dp(46),1));
        pageContainer.addView(controls,match(dp(50)));
        all.setOnClickListener(v->{onlyFavorites=false;filterChannels();});
        fav.setOnClickListener(v->{onlyFavorites=true;filterChannels();});
        LinearLayout channels=card();pageContainer.addView(channels,match(-2));
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(text("قائمة القنوات",18,Color.WHITE,true),new LinearLayout.LayoutParams(0,dp(40),1));
        empty=text("0 قناة",13,gold,true);head.addView(empty);channels.addView(head,match(dp(42)));
        listView=new ListView(this);listView.setBackgroundColor(panel);
        listView.setDivider(new android.graphics.drawable.ColorDrawable(Color.rgb(38,52,72)));listView.setDividerHeight(dp(1));
        listView.setNestedScrollingEnabled(false);listView.setAdapter(new ChannelAdapter());
        channels.addView(listView,new LinearLayout.LayoutParams(-1,dp(360)));
        listView.setOnItemClickListener((p,v,pos,id)->play(visibleChannels.get(pos)));
        listView.setOnItemLongClickListener((p,v,pos,id)->{
            Channel ch=visibleChannels.get(pos);
            if(!favorites.add(ch.key()))favorites.remove(ch.key());
            saveFavorites();((BaseAdapter)listView.getAdapter()).notifyDataSetChanged();
            status.setText(favorites.contains(ch.key())?"أضيفت إلى المفضلة":"أزيلت من المفضلة");return true;
        });
        pageContainer.addView(text("اضغط مطولًا على أي قناة لتبديل حالة المفضلة.",12,Color.rgb(145,163,185),false),match(dp(30)));
        onlyFavorites=favOnly;filterChannels();
    }

    private void buildSettingsPage(){
        TextView heading=text("الإعدادات والتحكم",25,Color.WHITE,true);pageContainer.addView(heading,match(dp(48)));
        pageContainer.addView(text("خصص سلوك التطبيق حسب هاتفك أو جهاز التلفاز.",13,Color.rgb(174,191,210),false),match(dp(34)));
        LinearLayout playback=card();pageContainer.addView(playback,match(-2));
        playback.addView(text("المشغّل والصورة",18,Color.WHITE,true),match(dp(38)));
        playback.addView(text("ملاءمة أبعاد الفيديو",13,Color.rgb(174,191,210),false),match(dp(28)));
        Spinner resize=new Spinner(this);
        ArrayAdapter<String> resizeAdapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,new String[]{"احتواء كامل (FIT)","ملء الشاشة (FILL)","تكبير لقص الحواف (ZOOM)"});
        resizeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);resize.setAdapter(resizeAdapter);styleSpinner(resize);
        resize.setSelection(prefs.getInt("resize_mode",0));playback.addView(resize,match(dp(48)));
        resize.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(AdapterView<?> p,View v,int pos,long id){
                prefs.edit().putInt("resize_mode",pos).apply();
                if(playerView!=null)playerView.setResizeMode(pos==1?androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL:pos==2?androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM:androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT);
            }
            @Override public void onNothingSelected(AdapterView<?> p){}
        });
        playback.addView(text("سرعة التشغيل",13,Color.rgb(174,191,210),false),match(dp(28)));
        Spinner speed=new Spinner(this);
        ArrayAdapter<String> speedAdapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,new String[]{"0.75×","1.0×","1.25×","1.5×","2.0×"});
        speedAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);speed.setAdapter(speedAdapter);styleSpinner(speed);
        int savedSpeed=prefs.getInt("playback_speed",1);speed.setSelection(savedSpeed);playback.addView(speed,match(dp(48)));
        speed.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(AdapterView<?> p,View v,int pos,long id){
                prefs.edit().putInt("playback_speed",pos).apply();
                if(player!=null){float[] rates={0.75f,1f,1.25f,1.5f,2f};player.setPlaybackSpeed(rates[pos]);}
            }
            @Override public void onNothingSelected(AdapterView<?> p){}
        });

        LinearLayout behavior=card();LinearLayout.LayoutParams bp=match(-2);bp.topMargin=dp(12);pageContainer.addView(behavior,bp);
        behavior.addView(text("سلوك التطبيق",18,Color.WHITE,true),match(dp(38)));
        addToggle(behavior,"إبقاء الشاشة مضاءة أثناء المشاهدة","keep_screen_on",false,(checked)->{
            if(checked)getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            else getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        });
        addToggle(behavior,"تشغيل أول قناة تلقائيًا بعد التحميل","auto_play_first",false,(checked)->{});
        addToggle(behavior,"إظهار رسائل حالة التشغيل","show_status",true,(checked)->{});
        addToggle(behavior,"استعادة آخر مصدر أدخلته على الجهاز","remember_source",true,(checked)->{});
        LinearLayout appearance=card();LinearLayout.LayoutParams ap=match(-2);ap.topMargin=dp(12);pageContainer.addView(appearance,ap);
        appearance.addView(text("المظهر والتنظيم",18,Color.WHITE,true),match(dp(38)));
        addToggle(appearance,"قائمة قنوات أكثر كثافة","compact_channels",false,(checked)->{if("live".equals(currentPage)||"favorites".equals(currentPage))showPage(currentPage);});
        appearance.addView(text("اللون الحالي: كحلي وذهبي",13,Color.rgb(174,191,210),false),match(dp(34)));
        Button reset=secondaryButton("إعادة ضبط إعدادات المشغّل");
        LinearLayout.LayoutParams rp=match(dp(48));rp.topMargin=dp(12);pageContainer.addView(reset,rp);
        reset.setOnClickListener(v->{
            prefs.edit().remove("resize_mode").remove("playback_speed").remove("keep_screen_on").remove("auto_play_first").remove("show_status").remove("remember_source").remove("compact_channels").apply();
            if(player!=null)player.setPlaybackSpeed(1f);
            getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            toast("أُعيد ضبط الإعدادات");showPage("settings");
        });
    }

    private interface ToggleAction{void changed(boolean checked);}
    private void addToggle(LinearLayout parent,String label,String key,boolean defaultValue,ToggleAction action){
        Switch sw=new Switch(this);sw.setText(label);sw.setTextColor(Color.WHITE);sw.setTextSize(14);
        sw.setPadding(0,dp(8),0,dp(8));sw.setChecked(prefs.getBoolean(key,defaultValue));
        parent.addView(sw,match(dp(54)));
        if("keep_screen_on".equals(key)&&sw.isChecked())getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        sw.setOnCheckedChangeListener((button,checked)->{prefs.edit().putBoolean(key,checked).apply();action.changed(checked);});
    }

    private int getResizeMode(){
        int mode=prefs.getInt("resize_mode",0);
        return mode==1?androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL:mode==2?androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM:androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT;
    }
    private Button secondaryButton(String label){
        Button b=new Button(this);b.setText(label);b.setTextSize(13);b.setTextColor(Color.WHITE);b.setAllCaps(false);
        b.setBackground(roundRect(Color.rgb(34,50,72),dp(12)));return b;
    }

    private LinearLayout card(){
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14),dp(12),dp(14),dp(12));
        box.setBackground(roundRect(panel,dp(18)));
        return box;
    }
    private void addSectionTitle(LinearLayout parent,String titleText,String subtitleText){
        TextView title=text(titleText,18,Color.WHITE,true);
        parent.addView(title,match(dp(30)));
        TextView subtitle=text(subtitleText,12,Color.rgb(165,180,199),false);
        parent.addView(subtitle,match(dp(25)));
    }
    private android.graphics.drawable.GradientDrawable roundRect(int color,int radius){
        android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable();
        d.setColor(color);d.setCornerRadius(radius);
        d.setStroke(dp(1),Color.rgb(37,51,70));
        return d;
    }

    private class ChannelAdapter extends BaseAdapter {
        public int getCount(){return visibleChannels.size();}
        public Object getItem(int p){return visibleChannels.get(p);}
        public long getItemId(int p){return p;}
        public View getView(int p,View convert,ViewGroup parent){
            Channel c=visibleChannels.get(p);
            LinearLayout row=new LinearLayout(MainActivity.this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12),dp(prefs.getBoolean("compact_channels",false)?3:8),dp(12),dp(prefs.getBoolean("compact_channels",false)?3:8));
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setBackgroundColor(panel);
            TextView badge=text(favorites.contains(c.key())?"★":"▶",17,gold,true);
            badge.setGravity(Gravity.CENTER);
            row.addView(badge,new LinearLayout.LayoutParams(dp(34),dp(42)));
            LinearLayout details=new LinearLayout(MainActivity.this);
            details.setOrientation(LinearLayout.VERTICAL);
            details.setPadding(dp(8),0,dp(8),0);
            TextView name=text(c.name,prefs.getBoolean("compact_channels",false)?13:15,Color.WHITE,true);
            name.setMaxLines(1);name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            details.addView(name);
            String group=(c.group==null||c.group.isEmpty())?"بث مباشر":c.group;
            TextView meta=text(group,12,Color.rgb(165,180,199),false);
            meta.setMaxLines(1);meta.setEllipsize(android.text.TextUtils.TruncateAt.END);
            details.addView(meta);
            row.addView(details,new LinearLayout.LayoutParams(0,-2,1));
            TextView play=text("›",25,Color.rgb(165,180,199),false);
            play.setGravity(Gravity.CENTER);
            row.addView(play,new LinearLayout.LayoutParams(dp(24),dp(42)));
            return row;
        }
    }

    private void loadSource(){
        final int mode=sourceSpinner.getSelectedItemPosition();
        final String address=addressField.getText().toString().trim();
        final String user=userField.getText().toString().trim(), pass=passField.getText().toString().trim(), mac=macField.getText().toString().trim();
        if(address.isEmpty()){toast("أدخل الرابط أولًا");return;}
        if(mode==1&&(user.isEmpty()||pass.isEmpty())){toast("أدخل اسم المستخدم وكلمة المرور");return;}
        if(mode==2&&mac.isEmpty()){toast("أدخل MAC المخصص لاشتراكك");return;}
        status.setText("جارٍ الاتصال وتحميل القنوات…");
        worker.execute(()->{
            try{
                ArrayList<Channel> result;
                if(mode==0) result=loadM3u(address);
                else if(mode==1) result=loadXtream(address,user,pass);
                else result=loadStalker(address,mac);
                runOnUiThread(()->{
                    allChannels.clear();allChannels.addAll(result);onlyFavorites=false;filterChannels();
                    status.setText("اكتمل التحميل: "+result.size()+" قناة");
                    if(result.isEmpty())toast("لم نجد قنوات. تحقق من الرابط وبيانات الاشتراك.");
                });
            }catch(Exception e){runOnUiThread(()->status.setText("فشل التحميل: "+safeMessage(e)+" — تحقق من بيانات المصدر والإنترنت"));}
        });
    }

    private ArrayList<Channel> loadM3u(String address)throws Exception{
        String body=request(address,null,null,20000);
        ArrayList<Channel> out=new ArrayList<>(); String name="قناة",group="",logo="",id="";
        for(String raw:body.split("\\r?\\n")){
            String line=raw.trim(); if(line.isEmpty())continue;
            if(line.startsWith("#EXTINF")){
                int comma=line.lastIndexOf(','); name=comma>=0?line.substring(comma+1).trim():attr(line,"tvg-name");
                if(name.isEmpty())name="قناة";
                group=attr(line,"group-title");logo=attr(line,"tvg-logo");id=attr(line,"tvg-id");
            }else if(line.startsWith("#EXTGRP:")){group=line.substring(8).trim();}
            else if(!line.startsWith("#")&&(line.startsWith("http://")||line.startsWith("https://"))){
                out.add(new Channel(name,line,group,logo,id));name="قناة";group="";logo="";id="";
            }
        }
        return out;
    }

    private ArrayList<Channel> loadXtream(String base,String user,String pass)throws Exception{
        base=base.replaceAll("/+$","");
        String api=base+"/player_api.php?username="+enc(user)+"&password="+enc(pass);
        JSONObject auth=new JSONObject(request(api,null,null,18000));
        if(auth.has("user_info")&&!auth.getJSONObject("user_info").optString("auth","1").equals("1"))throw new Exception("بيانات Xtream غير مقبولة");
        HashMap<String,String> groups=new HashMap<>();
        try{
            JSONArray cats=new JSONArray(request(api+"&action=get_live_categories",null,null,18000));
            for(int i=0;i<cats.length();i++){JSONObject o=cats.getJSONObject(i);groups.put(o.optString("category_id"),o.optString("category_name"));}
        }catch(Exception ignored){}
        JSONArray streams=new JSONArray(request(api+"&action=get_live_streams",null,null,25000));
        ArrayList<Channel> out=new ArrayList<>();
        for(int i=0;i<streams.length();i++){
            JSONObject o=streams.getJSONObject(i);String sid=o.optString("stream_id");
            String ext=o.optString("container_extension","ts");if(ext.isEmpty())ext="ts";
            String url=base+"/live/"+enc(user)+"/"+enc(pass)+"/"+sid+"."+ext;
            String cat=o.optString("category_id");
            out.add(new Channel(o.optString("name","قناة"),url,groups.getOrDefault(cat,""),o.optString("stream_icon",""),sid));
        }
        return out;
    }

    private ArrayList<Channel> loadStalker(String portal,String mac)throws Exception{
        portal=portal.trim(); if(!portal.endsWith("/"))portal+="/";
        String endpoint=portal+(portal.endsWith("server/load.php")?"":"server/load.php");
        String cookie="mac="+mac+"; stb_lang=en; timezone=UTC";
        String headers=""; 
        JSONObject handshake=new JSONObject(request(endpoint+"?type=stb&action=handshake&token=&JsHttpRequest=1-xml",cookie,null,18000));
        String token=handshake.optJSONObject("js")!=null?handshake.getJSONObject("js").optString("token",""):"";
        if(token.isEmpty())throw new Exception("لم ينجح تسجيل الدخول إلى Stalker Portal");
        String authCookie=cookie+";"; 
        JSONObject profile=new JSONObject(request(endpoint+"?type=stb&action=get_profile&hd=1&ver=ImageDescription%3A%200.2.18-r23-pub-250%3B%20ImageSDK%3A%200&num_banks=2&sn=&stb_type=MAG250&client_type=STB&image_version=218&video_out=hdmi& JsHttpRequest=1-xml",authCookie,token,18000));
        JSONArray channelsJson=null;
        String[] tries={
          endpoint+"?type=itv&action=get_all_channels&force_ch_link_check=&JsHttpRequest=1-xml",
          endpoint+"?type=itv&action=get_ordered_list&genre=*&force_ch_link_check=&fav=0&sortby=number&hd=0&not_ended=0&p=0&JsHttpRequest=1-xml"
        };
        Exception last=null;
        for(String u:tries){try{JSONObject j=new JSONObject(request(u,authCookie,token,25000));JSONObject js=j.optJSONObject("js");if(js!=null){channelsJson=js.optJSONArray("data");if(channelsJson==null)channelsJson=js.optJSONArray("channels");}if(channelsJson!=null)break;}catch(Exception e){last=e;}}
        if(channelsJson==null)throw new Exception(last==null?"Portal لم يُرجع قائمة قنوات":safeMessage(last));
        ArrayList<Channel> out=new ArrayList<>();
        for(int i=0;i<channelsJson.length();i++){
            JSONObject o=channelsJson.optJSONObject(i);if(o==null)continue;
            String cmd=o.optString("cmd","");String id=o.optString("id",o.optString("ch_id"));
            if(cmd.isEmpty())continue;
            String stream=cmd.replace("ffmpeg ","").replace("auto ","").trim();
            if(!stream.startsWith("http://")&&!stream.startsWith("https://"))stream=endpoint+"?type=itv&action=create_link&cmd="+enc(cmd)+"&JsHttpRequest=1-xml";
            out.add(new Channel(o.optString("name","قناة"),stream,o.optString("tv_genre_title",""),o.optString("logo",""),id));
        }
        return out;
    }

    private void play(Channel c){
        try{
            playerView.setVisibility(View.VISIBLE);
            String url=c.url;
            if(url.contains("action=create_link")){
                status.setText("هذا النوع من روابط Stalker يحتاج إلى طلب جلسة تشغيل خاص؛ قد لا يعمل مباشرة على بعض البوابات.");
            }
            nowPlaying.setText("يُشغّل الآن: "+c.name);
            player.setMediaItem(MediaItem.fromUri(Uri.parse(url)));player.prepare();player.play();
            status.setText("جارٍ بدء البث…");
        }catch(Exception e){status.setText("تعذّر تشغيل القناة: "+safeMessage(e));}
    }

    private void filterChannels(){
        String q=searchField==null?"":searchField.getText().toString().trim().toLowerCase(Locale.ROOT);
        visibleChannels.clear();
        for(Channel c:allChannels){
            if(onlyFavorites&&!favorites.contains(c.key()))continue;
            if(!q.isEmpty()&&!c.name.toLowerCase(Locale.ROOT).contains(q)&&!(c.group!=null&&c.group.toLowerCase(Locale.ROOT).contains(q)))continue;
            visibleChannels.add(c);
        }
        if(empty!=null)empty.setText(visibleChannels.size()+" قناة");
        if(listView!=null)((BaseAdapter)listView.getAdapter()).notifyDataSetChanged();
    }
    private void saveFavorites(){prefs.edit().putStringSet("favorites",new HashSet<>(favorites)).apply();}
    private String attr(String line,String key){
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?:^|\\s)"+java.util.regex.Pattern.quote(key)+"=\"([^\"]*)\"").matcher(line);
        return m.find()?m.group(1):"";
    }
    private String enc(String s){try{return URLEncoder.encode(s,"UTF-8");}catch(Exception e){return s;}}
    private String request(String url,String cookie,String token,int timeout)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(url.replace(" ","%20")).openConnection();
        c.setConnectTimeout(timeout);c.setReadTimeout(timeout);c.setRequestProperty("User-Agent","Mozilla/5.0 (Linux; Android 12) AlRashedIPTV/2.0");
        c.setRequestProperty("Accept","*/*");
        if(cookie!=null)c.setRequestProperty("Cookie",cookie);
        if(token!=null&&!token.isEmpty())c.setRequestProperty("Authorization","Bearer "+token);
        try(InputStream in=(c.getResponseCode()<400?c.getInputStream():c.getErrorStream());ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);
            String body=out.toString("UTF-8");
            if(c.getResponseCode()>=400)throw new Exception("HTTP "+c.getResponseCode());
            return body;
        }finally{c.disconnect();}
    }
    private String safeMessage(Exception e){String m=e.getMessage();return m==null?"خطأ غير معروف":m.length()>140?m.substring(0,140):m;}
    private TextView text(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);t.setGravity(Gravity.CENTER_VERTICAL);return t;}
    private EditText field(String hint){EditText e=new EditText(this);e.setSingleLine(true);e.setTextSize(14);e.setTextColor(Color.WHITE);e.setHintTextColor(Color.rgb(143,157,176));e.setHint(hint);e.setPadding(dp(12),0,dp(12),0);e.setBackground(roundRect(Color.rgb(14,23,38),dp(10)));e.setSelectAllOnFocus(false);return e;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setTextColor(Color.rgb(15,22,34));b.setTextSize(14);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setAllCaps(false);b.setBackground(roundRect(gold,dp(12)));return b;}
    private void styleSpinner(Spinner s){s.setBackgroundTintList(android.content.res.ColorStateList.valueOf(gold));}
    private LinearLayout.LayoutParams match(int h){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,h);p.bottomMargin=dp(5);return p;}
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    @Override protected void onStop(){super.onStop();if(player!=null)player.pause();}
}