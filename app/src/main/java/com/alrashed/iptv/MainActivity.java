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
        buildUi();
        player=new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        player.addListener(new Player.Listener(){
            @Override public void onPlayerError(PlaybackException error){
                status.setText("تعذّر التشغيل: "+error.getErrorCodeName()+" — قد يكون الرابط منتهيًا أو الصيغة غير مدعومة");
            }
            @Override public void onPlaybackStateChanged(int state){
                if(state==Player.STATE_BUFFERING) status.setText("جارٍ تحميل البث…");
                else if(state==Player.STATE_READY) status.setText("البث جاهز");
            }
        });
    }

    private void buildUi(){
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(bg);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18),dp(14),dp(18),dp(22));
        scroll.addView(root);

        LinearLayout brand=new LinearLayout(this);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        brand.setOrientation(LinearLayout.VERTICAL);
        brand.setPadding(dp(2),dp(10),dp(2),dp(14));
        TextView title=text("آل راشد",30,gold,true);
        title.setGravity(Gravity.CENTER);
        brand.addView(title,match(dp(42)));
        TextView sub=text("IPTV  •  مشغّل التلفاز الذكي",14,Color.rgb(178,190,207),false);
        sub.setGravity(Gravity.CENTER);
        brand.addView(sub,match(dp(28)));
        root.addView(brand);

        // TV-first dashboard banner: deliberately different from the old plain form screen.
        LinearLayout hero=card();
        hero.setPadding(dp(18),dp(16),dp(18),dp(16));
        hero.setBackground(roundRect(Color.rgb(18,43,65),dp(20)));
        LinearLayout.LayoutParams heroParams=match(-2);
        heroParams.bottomMargin=dp(14);
        root.addView(hero,heroParams);
        LinearLayout liveLine=new LinearLayout(this);
        liveLine.setGravity(Gravity.CENTER_VERTICAL);
        TextView liveDot=text("●  مباشر",13,Color.rgb(82,220,154),true);
        liveLine.addView(liveDot,new LinearLayout.LayoutParams(0,dp(28),1));
        TextView versionTag=text("AL RASHED  •  TV",11,Color.rgb(190,205,222),true);
        liveLine.addView(versionTag);
        hero.addView(liveLine,match(dp(30)));
        TextView heroTitle=text("كل قنواتك في مكان واحد",23,Color.WHITE,true);
        heroTitle.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        hero.addView(heroTitle,match(dp(42)));
        TextView heroSubtitle=text("مشغّل IPTV للموبايل والتلفاز — بحث سريع، مفضلة، وتحكم بالتشغيل",13,Color.rgb(190,205,222),false);
        heroSubtitle.setMaxLines(2);
        hero.addView(heroSubtitle,match(dp(42)));
        LinearLayout metrics=new LinearLayout(this);
        metrics.setGravity(Gravity.CENTER_VERTICAL);
        metrics.setPadding(0,dp(8),0,0);
        TextView metricChannels=text("القنوات  •  جاهز",12,Color.WHITE,true);
        metrics.addView(metricChannels,new LinearLayout.LayoutParams(0,dp(32),1));
        TextView metricFav=text("★  "+favorites.size()+" مفضلة",12,gold,true);
        metrics.addView(metricFav);
        hero.addView(metrics,match(dp(38)));

        LinearLayout sourceCard=card();
        root.addView(sourceCard,match(-2));
        addSectionTitle(sourceCard,"مصدر البث","أضف اشتراكك أو قائمة القنوات");
        sourceSpinner=new Spinner(this);
        ArrayAdapter<String> sources=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,
            new String[]{"قائمة M3U / M3U8","Xtream Codes","Stalker Portal"});
        sources.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sourceSpinner.setAdapter(sources);
        styleSpinner(sourceSpinner);
        sourceCard.addView(sourceSpinner,match(dp(48)));
        addressField=field("رابط قائمة M3U أو عنوان السيرفر");
        sourceCard.addView(addressField,match(dp(48)));
        userField=field("اسم المستخدم");
        sourceCard.addView(userField,match(dp(46)));
        passField=field("كلمة المرور");
        passField.setInputType(129);
        sourceCard.addView(passField,match(dp(46)));
        macField=field("MAC المخصص لاشتراك Stalker");
        sourceCard.addView(macField,match(dp(46)));
        sourceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(AdapterView<?> p,View v,int pos,long id){
                userField.setVisibility(pos==1?View.VISIBLE:View.GONE);
                passField.setVisibility(pos==1?View.VISIBLE:View.GONE);
                macField.setVisibility(pos==2?View.VISIBLE:View.GONE);
                addressField.setHint(pos==0?"رابط قائمة M3U / M3U8":pos==1?"عنوان السيرفر مثل https://server:8080":"رابط Stalker Portal");
            }
            @Override public void onNothingSelected(AdapterView<?> p){}
        });
        Button load=button("＋   تحميل القنوات");
        load.setTextSize(16);
        sourceCard.addView(load,match(dp(50)));
        load.setOnClickListener(v->loadSource());

        LinearLayout library=card();
        LinearLayout.LayoutParams libParams=match(-2);
        libParams.topMargin=dp(12);
        root.addView(library,libParams);
        addSectionTitle(library,"مكتبتك","ابحث وصنّف القنوات بسرعة");
        searchField=field("ابحث عن قناة أو مجموعة…");
        library.addView(searchField,match(dp(46)));
        searchField.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int st,int c,int a){}
            public void onTextChanged(CharSequence s,int start,int before,int count){filterChannels();}
            public void afterTextChanged(Editable e){}
        });
        LinearLayout actions=new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button all=button("كل القنوات");
        Button fav=button("★  المفضلة");
        all.setTextSize(14);fav.setTextSize(14);
        LinearLayout.LayoutParams half=new LinearLayout.LayoutParams(0,dp(44),1);
        half.setMargins(0,dp(3),dp(5),dp(3));
        actions.addView(all,half);
        LinearLayout.LayoutParams half2=new LinearLayout.LayoutParams(0,dp(44),1);
        half2.setMargins(dp(5),dp(3),0,dp(3));
        actions.addView(fav,half2);
        library.addView(actions,match(dp(50)));
        all.setOnClickListener(v->{onlyFavorites=false;filterChannels();});
        fav.setOnClickListener(v->{onlyFavorites=true;filterChannels();});

        LinearLayout playerCard=card();
        LinearLayout.LayoutParams playerParams=match(-2);
        playerParams.topMargin=dp(12);
        root.addView(playerCard,playerParams);
        nowPlaying=text("جاهز للمشاهدة",17,Color.WHITE,true);
        playerCard.addView(nowPlaying,match(dp(36)));
        playerView=new PlayerView(this);
        playerView.setUseController(true);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS);
        playerView.setVisibility(View.GONE);
        playerCard.addView(playerView,new LinearLayout.LayoutParams(-1,dp(220)));
        TextView placeholder=text("اختر قناة من القائمة لبدء المشاهدة",14,Color.rgb(178,190,207),false);
        placeholder.setGravity(Gravity.CENTER);
        placeholder.setPadding(dp(8),dp(12),dp(8),dp(12));
        playerCard.addView(placeholder,match(dp(56)));
        status=text("أدخل بيانات مصدر IPTV تملك حق استخدامه، ثم اضغط تحميل القنوات.",13,Color.rgb(178,190,207),false);
        status.setGravity(Gravity.CENTER_VERTICAL);
        playerCard.addView(status,match(-2));

        LinearLayout channelsCard=card();
        LinearLayout.LayoutParams channelsParams=match(-2);
        channelsParams.topMargin=dp(12);
        root.addView(channelsCard,channelsParams);
        LinearLayout headingRow=new LinearLayout(this);
        headingRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading=text("القنوات",20,Color.WHITE,true);
        headingRow.addView(heading,new LinearLayout.LayoutParams(0,dp(42),1));
        empty=text("0 قناة",13,Color.rgb(178,190,207),false);
        headingRow.addView(empty);
        channelsCard.addView(headingRow,match(dp(42)));
        listView=new ListView(this);
        listView.setBackgroundColor(panel);
        listView.setDivider(new android.graphics.drawable.ColorDrawable(Color.rgb(38,52,72)));
        listView.setDividerHeight(dp(1));
        listView.setFocusable(true);
        listView.setNestedScrollingEnabled(false);
        listView.setAdapter(new ChannelAdapter());
        channelsCard.addView(listView,new LinearLayout.LayoutParams(-1,dp(300)));
        listView.setOnItemClickListener((p,v,pos,id)->play(visibleChannels.get(pos)));
        listView.setOnItemLongClickListener((p,v,pos,id)->{
            Channel c=visibleChannels.get(pos);
            if(!favorites.add(c.key()))favorites.remove(c.key());
            saveFavorites();
            ((BaseAdapter)listView.getAdapter()).notifyDataSetChanged();
            status.setText(favorites.contains(c.key())?"أضيفت إلى المفضلة":"أزيلت من المفضلة");
            return true;
        });
        TextView hint=text("اضغط مطولًا على القناة لإضافتها إلى المفضلة",12,Color.rgb(150,164,183),false);
        channelsCard.addView(hint,match(dp(30)));
        setContentView(scroll);
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
            row.setPadding(dp(12),dp(8),dp(12),dp(8));
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setBackgroundColor(panel);
            TextView badge=text(favorites.contains(c.key())?"★":"▶",17,gold,true);
            badge.setGravity(Gravity.CENTER);
            row.addView(badge,new LinearLayout.LayoutParams(dp(34),dp(42)));
            LinearLayout details=new LinearLayout(MainActivity.this);
            details.setOrientation(LinearLayout.VERTICAL);
            details.setPadding(dp(8),0,dp(8),0);
            TextView name=text(c.name,15,Color.WHITE,true);
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
    @Override protected void onDestroy(){if(player!=null)player.release();worker.shutdownNow();super.onDestroy();}
}