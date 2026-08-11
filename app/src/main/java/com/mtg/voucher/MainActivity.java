package com.mtg.voucher;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebView;
import android.widget.*;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private EditText ip,user,pass,count,prefix;
    private Spinner profiles;
    private TextView status;
    private LinearLayout voucherContainer, connectionPanel;
    private SharedPreferences prefs;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final SecureRandom random = new SecureRandom();
    private final ArrayList<Voucher> vouchers = new ArrayList<>();

    static class Voucher {
        String username,password,profile;
        Voucher(String u,String p,String pr){username=u;password=p;profile=pr;}
    }

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        setContentView(R.layout.activity_main);

        ip=findViewById(R.id.routerIp); user=findViewById(R.id.routerUser); pass=findViewById(R.id.routerPass);
        count=findViewById(R.id.countInput); prefix=findViewById(R.id.prefixInput); profiles=findViewById(R.id.profileSpinner);
        status=findViewById(R.id.status); voucherContainer=findViewById(R.id.voucherContainer);
        connectionPanel=findViewById(R.id.connectionPanel);

        prefs=getSharedPreferences("mtg",MODE_PRIVATE);
        ip.setText(prefs.getString("ip","192.168.88.1"));
        user.setText(prefs.getString("user",""));
        pass.setText(prefs.getString("pass",""));

        findViewById(R.id.settingsBtn).setOnClickListener(v -> {
            connectionPanel.setVisibility(connectionPanel.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE);
        });
        findViewById(R.id.testBtn).setOnClickListener(v -> testAndLoad());
        findViewById(R.id.generateBtn).setOnClickListener(v -> generate());
        findViewById(R.id.printBtn).setOnClickListener(v -> printAll());

        if(!user.getText().toString().isEmpty()) testAndLoad();
    }

    private String base(){return "http://"+ip.getText().toString().trim()+"/rest";}
    private String auth(){
        String s=user.getText().toString()+":"+pass.getText().toString();
        return "Basic "+Base64.encodeToString(s.getBytes(StandardCharsets.UTF_8),Base64.NO_WRAP);
    }

    private String req(String method,String path,JSONObject body)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(base()+path).openConnection();
        c.setConnectTimeout(8000); c.setReadTimeout(12000); c.setRequestMethod(method);
        c.setRequestProperty("Authorization",auth()); c.setRequestProperty("Accept","application/json");
        if(body!=null){
            c.setDoOutput(true); c.setRequestProperty("Content-Type","application/json");
            try(OutputStream os=c.getOutputStream()){os.write(body.toString().getBytes(StandardCharsets.UTF_8));}
        }
        int code=c.getResponseCode();
        InputStream is=(code>=200&&code<300)?c.getInputStream():c.getErrorStream();
        StringBuilder sb=new StringBuilder();
        if(is!=null)try(BufferedReader br=new BufferedReader(new InputStreamReader(is,StandardCharsets.UTF_8))){
            String line; while((line=br.readLine())!=null)sb.append(line);
        }
        if(code<200||code>=300)throw new Exception("HTTP "+code+" "+sb);
        return sb.toString();
    }

    private void testAndLoad(){
        status.setText("Connecting...");
        executor.execute(() -> {
            try{
                req("GET","/system/resource",null);
                JSONArray a=new JSONArray(req("GET","/user-manager/profile",null));
                ArrayList<String> names=new ArrayList<>();
                for(int i=0;i<a.length();i++){
                    String n=a.getJSONObject(i).optString("name","");
                    if(!n.isEmpty())names.add(n);
                }
                prefs.edit().putString("ip",ip.getText().toString().trim())
                        .putString("user",user.getText().toString())
                        .putString("pass",pass.getText().toString()).apply();
                runOnUiThread(() -> {
                    profiles.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));
                    status.setText("Connected ✓ — "+names.size()+" profiles");
                    connectionPanel.setVisibility(View.GONE);
                });
            }catch(Exception e){runOnUiThread(() -> status.setText("Connection failed: "+friendly(e)));}
        });
    }

    private void generate(){
        if(profiles.getSelectedItem()==null){toast("Open Settings and test connection first");return;}
        int n;
        try{n=Integer.parseInt(count.getText().toString().trim());}catch(Exception e){toast("Invalid count");return;}
        if(n<1||n>100){toast("Choose 1 to 100");return;}
        String prof=profiles.getSelectedItem().toString();
        String pre=prefix.getText().toString().trim();

        vouchers.clear(); voucherContainer.removeAllViews(); status.setText("Generating...");
        final int total=n;
        executor.execute(() -> {
            for(int i=0;i<total;i++){
                String un=pre+randomText(6,"abcdefghjkmnpqrstuvwxyz23456789");
                String pw=randomText(8,"ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789");
                try{
                    JSONObject u=new JSONObject(); u.put("name",un); u.put("password",pw);
                    req("PUT","/user-manager/user",u);
                    JSONObject p=new JSONObject(); p.put("user",un); p.put("profile",prof);
                    req("PUT","/user-manager/user-profile",p);
                    Voucher v=new Voucher(un,pw,prof); vouchers.add(v);
                    int done=vouchers.size();
                    runOnUiThread(() -> {addCard(v);status.setText("Generated "+done+" / "+total);});
                }catch(Exception e){runOnUiThread(() -> status.setText("Stopped: "+friendly(e)));return;}
            }
            runOnUiThread(() -> status.setText("Generated "+vouchers.size()+" vouchers ✓"));
        });
    }

    private String randomText(int len,String chars){
        StringBuilder s=new StringBuilder();
        for(int i=0;i<len;i++)s.append(chars.charAt(random.nextInt(chars.length())));
        return s.toString();
    }

    private void addCard(Voucher v){
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(26,22,26,22); box.setBackgroundResource(R.drawable.rounded_panel);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,0,0,14); box.setLayoutParams(lp);
        box.addView(t("MTG",26,true)); box.addView(t("WiFi ACCESS VOUCHER",13,true));
        box.addView(t("USERNAME",10,false)); box.addView(t(v.username,22,true));
        box.addView(t("PASSWORD",10,false)); box.addView(t(v.password,22,true));
        box.addView(t("PROFILE: "+v.profile,12,false));
        Button share=new Button(this); share.setText("SHARE WHATSAPP / APPS");
        share.setOnClickListener(x -> share(v)); box.addView(share);
        voucherContainer.addView(box);
    }

    private TextView t(String s,int sz,boolean bold){
        TextView t=new TextView(this); t.setText(s); t.setTextSize(sz); t.setGravity(Gravity.CENTER_HORIZONTAL);
        t.setPadding(4,4,4,4); if(bold)t.setTypeface(null,android.graphics.Typeface.BOLD); return t;
    }

    private void share(Voucher v){
        String txt="MTG WiFi ACCESS VOUCHER\nUsername: "+v.username+"\nPassword: "+v.password+"\nProfile: "+v.profile;
        Intent i=new Intent(Intent.ACTION_SEND); i.setType("text/plain"); i.putExtra(Intent.EXTRA_TEXT,txt);
        startActivity(Intent.createChooser(i,"Share voucher"));
    }

    private void printAll(){
        if(vouchers.isEmpty()){toast("Generate vouchers first");return;}
        StringBuilder h=new StringBuilder("<html><head><style>@page{size:A4;margin:8mm}body{font-family:Arial}.g{display:grid;grid-template-columns:repeat(3,1fr);gap:7px}.v{border:1px solid #222;padding:10px;text-align:center;break-inside:avoid}.b{font-size:20px;font-weight:bold}.l{font-size:10px;color:#555}</style></head><body><div class='g'>");
        for(Voucher v:vouchers)h.append("<div class='v'><b>MTG</b><br>WiFi ACCESS VOUCHER<div class='l'>USERNAME</div><div class='b'>")
                .append(v.username).append("</div><div class='l'>PASSWORD</div><div class='b'>").append(v.password)
                .append("</div><div>").append(v.profile).append("</div></div>");
        h.append("</div></body></html>");
        WebView w=new WebView(this); w.loadDataWithBaseURL(null,h.toString(),"text/html","UTF-8",null);
        w.postDelayed(() -> {
            PrintManager pm=(PrintManager)getSystemService(PRINT_SERVICE);
            pm.print("MTG Vouchers",w.createPrintDocumentAdapter("MTG Vouchers"),new PrintAttributes.Builder().build());
        },700);
    }

    private String friendly(Exception e){
        String m=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();
        if(m.contains("401"))return "Wrong router username/password";
        if(m.contains("404"))return "REST or User Manager endpoint not found";
        if(m.toLowerCase().contains("timeout")||m.toLowerCase().contains("failed to connect"))return "Router unreachable or IP > Services > www is disabled";
        return m;
    }

    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
}
